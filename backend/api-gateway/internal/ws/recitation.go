package ws

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"errors"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
	"github.com/gorilla/websocket"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/redis/go-redis/v9"

	"islamic-ai/backend/api-gateway/internal/auth"
	"islamic-ai/backend/api-gateway/internal/metrics"
)

const (
	audioStreamName       = "recitation:audio"
	resultKeyPrefix       = "recitation:result:"
	resultChannelPrefix   = "recitation:results:"
	writeTimeout          = 10 * time.Second
	readTimeout           = 70 * time.Second
	pingInterval          = 25 * time.Second
	resultPollInterval    = 750 * time.Millisecond
	maxAudioChunkBytes    = 512 * 1024
	recitationResultReady = "result"
)

type Handler struct {
	redis    *redis.Client
	db       *pgxpool.Pool
	upgrader websocket.Upgrader
}

func NewHandler(redisClient *redis.Client, db *pgxpool.Pool, allowedOrigins []string) *Handler {
	return &Handler{
		redis: redisClient,
		db:    db,
		upgrader: websocket.Upgrader{
			ReadBufferSize:  32 * 1024,
			WriteBufferSize: 32 * 1024,
			CheckOrigin:     checkOrigin(allowedOrigins),
		},
	}
}

func (h *Handler) Recitation(c *gin.Context) {
	conn, err := h.upgrader.Upgrade(c.Writer, c.Request, nil)
	if err != nil {
		return
	}

	ctx, cancel := context.WithCancel(c.Request.Context())
	defer func() {
		cancel()
		_ = conn.Close()
	}()

	metrics.ActiveWebSockets.Inc()
	defer metrics.ActiveWebSockets.Dec()

	conn.SetReadLimit(maxAudioChunkBytes)
	_ = conn.SetReadDeadline(time.Now().Add(readTimeout))
	conn.SetPongHandler(func(string) error {
		return conn.SetReadDeadline(time.Now().Add(readTimeout))
	})

	var writeMu sync.Mutex
	writeJSON := func(value interface{}) error {
		writeMu.Lock()
		defer writeMu.Unlock()
		_ = conn.SetWriteDeadline(time.Now().Add(writeTimeout))
		return conn.WriteJSON(value)
	}

	sessionID := uuid.NewString()
	userID := c.GetString(auth.UserIDKey)
	surah, ayah := parseVerseSelection(c)
	startedAt := time.Now()
	if err := h.createSession(ctx, sessionID, userID, surah, ayah); err != nil {
		_ = writeJSON(gin.H{"type": "error", "message": "session create failed"})
		return
	}

	if err := writeJSON(gin.H{
		"type":       "session_started",
		"session_id": sessionID,
		"surah":      surah,
		"ayah":       ayah,
	}); err != nil {
		return
	}

	resultDone := make(chan struct{})
	go h.forwardResults(ctx, sessionID, startedAt, writeJSON, resultDone)
	defer func() {
		cancel()
		<-resultDone
	}()

	for {
		messageType, payload, err := conn.ReadMessage()
		if err != nil {
			_ = h.markSessionDisconnected(context.Background(), sessionID)
			return
		}
		_ = conn.SetReadDeadline(time.Now().Add(readTimeout))
		if messageType != websocket.BinaryMessage {
			if shouldCloseFromText(messageType, payload) {
				if err := h.enqueueStop(ctx, sessionID, userID, surah, ayah); err != nil {
					_ = writeJSON(gin.H{"type": "error", "session_id": sessionID, "message": "stop enqueue failed"})
					continue
				}
				_ = writeJSON(gin.H{"type": "recording_stopped", "session_id": sessionID})
			}
			continue
		}
		streamID, err := h.redis.XAdd(c.Request.Context(), &redis.XAddArgs{
			Stream: audioStreamName,
			Values: map[string]interface{}{
				"session_id": sessionID,
				"user_id":    userID,
				"surah":      strconv.Itoa(surah),
				"ayah":       strconv.Itoa(ayah),
				"audio_b64":  base64.StdEncoding.EncodeToString(payload),
				"created_at": time.Now().UTC().Format(time.RFC3339Nano),
			},
		}).Result()
		if err != nil {
			_ = writeJSON(gin.H{"type": "error", "session_id": sessionID, "message": "audio enqueue failed"})
			continue
		}
		metrics.RecitationChunksTotal.Inc()
		if err := writeJSON(gin.H{"type": "chunk_ack", "session_id": sessionID, "stream_id": streamID}); err != nil {
			_ = h.markSessionDisconnected(context.Background(), sessionID)
			return
		}
	}
}

func (h *Handler) forwardResults(ctx context.Context, sessionID string, startedAt time.Time, writeJSON func(interface{}) error, done chan<- struct{}) {
	defer close(done)

	pubsub := h.redis.Subscribe(ctx, resultChannelPrefix+sessionID)
	defer pubsub.Close()

	resultTicker := time.NewTicker(resultPollInterval)
	defer resultTicker.Stop()
	pingTicker := time.NewTicker(pingInterval)
	defer pingTicker.Stop()

	ch := pubsub.Channel()
	var lastPayload string
	firstResult := true

	for {
		select {
		case <-ctx.Done():
			return
		case msg, ok := <-ch:
			if !ok {
				ch = nil
				continue
			}
			if msg == nil || msg.Payload == "" || msg.Payload == lastPayload {
				continue
			}
			lastPayload = msg.Payload
			if h.sendResult(ctx, sessionID, msg.Payload, startedAt, &firstResult, writeJSON) != nil {
				return
			}
		case <-resultTicker.C:
			payload, err := h.redis.Get(ctx, resultKeyPrefix+sessionID).Result()
			if err != nil || payload == "" || payload == lastPayload {
				continue
			}
			lastPayload = payload
			if h.sendResult(ctx, sessionID, payload, startedAt, &firstResult, writeJSON) != nil {
				return
			}
		case <-pingTicker.C:
			if err := writeJSON(gin.H{"type": "ping", "session_id": sessionID}); err != nil {
				return
			}
		}
	}
}

func (h *Handler) sendResult(ctx context.Context, sessionID, payload string, startedAt time.Time, firstResult *bool, writeJSON func(interface{}) error) error {
	out, score, transcript, err := normalizeResultPayload(sessionID, payload)
	if err != nil {
		metrics.RecitationResultsTotal.WithLabelValues("invalid").Inc()
		return writeJSON(gin.H{"type": "error", "session_id": sessionID, "message": "invalid recitation result"})
	}

	if err := h.updateSessionResult(ctx, sessionID, score, transcript, payload); err != nil {
		out["warning"] = "result saved to client but database update failed"
	}

	if *firstResult {
		metrics.RecitationResultLatency.Observe(time.Since(startedAt).Seconds())
		*firstResult = false
	}
	metrics.RecitationResultsTotal.WithLabelValues("delivered").Inc()
	return writeJSON(out)
}

func normalizeResultPayload(sessionID, payload string) (gin.H, float64, string, error) {
	var raw map[string]interface{}
	if err := json.Unmarshal([]byte(payload), &raw); err != nil {
		return nil, 0, "", err
	}
	if raw == nil {
		return nil, 0, "", errors.New("empty payload")
	}

	raw["type"] = recitationResultReady
	raw["status"] = "completed"
	raw["session_id"] = stringValue(raw["session_id"], sessionID)

	transcript := stringValue(raw["transcript"], "")
	if transcript == "" {
		transcript = stringValue(raw["transcription"], "")
	}
	raw["transcript"] = transcript
	raw["transcription"] = transcript

	score := floatValue(raw["score"])
	if score == 0 {
		if tajweed, ok := raw["tajweed"].(map[string]interface{}); ok {
			score = floatValue(tajweed["score"])
		}
	}
	raw["score"] = score

	return gin.H(raw), score, transcript, nil
}

func (h *Handler) createSession(ctx context.Context, sessionID, userID string, surah, ayah int) error {
	if h.db == nil {
		return nil
	}
	var surahValue interface{}
	var ayahValue interface{}
	if surah > 0 {
		surahValue = surah
	}
	if ayah > 0 {
		ayahValue = ayah
	}
	_, err := h.db.Exec(ctx,
		`INSERT INTO recitation_sessions (id, user_id, surah, ayah, status)
		 VALUES ($1, $2, $3, $4, 'active')
		 ON CONFLICT (id) DO NOTHING`,
		sessionID, userID, surahValue, ayahValue,
	)
	return err
}

func (h *Handler) enqueueStop(ctx context.Context, sessionID, userID string, surah, ayah int) error {
	_, err := h.redis.XAdd(ctx, &redis.XAddArgs{
		Stream: audioStreamName,
		Values: map[string]interface{}{
			"event":      "stop",
			"session_id": sessionID,
			"user_id":    userID,
			"surah":      strconv.Itoa(surah),
			"ayah":       strconv.Itoa(ayah),
			"created_at": time.Now().UTC().Format(time.RFC3339Nano),
		},
	}).Result()
	return err
}

func (h *Handler) updateSessionResult(ctx context.Context, sessionID string, score float64, transcript, payload string) error {
	if h.db == nil {
		return nil
	}
	_, err := h.db.Exec(ctx,
		`UPDATE recitation_sessions
		 SET status = 'completed', score = $2, transcript = $3, feedback = $4::jsonb, updated_at = now()
		 WHERE id = $1`,
		sessionID, score, transcript, payload,
	)
	return err
}

func (h *Handler) markSessionCompleted(ctx context.Context, sessionID string) error {
	if h.db == nil {
		return nil
	}
	_, err := h.db.Exec(ctx,
		`UPDATE recitation_sessions
		 SET status = CASE WHEN status = 'completed' THEN status ELSE 'stopped' END, updated_at = now()
		 WHERE id = $1`,
		sessionID,
	)
	return err
}

func (h *Handler) markSessionDisconnected(ctx context.Context, sessionID string) error {
	if h.db == nil {
		return nil
	}
	_, err := h.db.Exec(ctx,
		`UPDATE recitation_sessions
		 SET status = CASE WHEN status = 'completed' THEN status ELSE 'disconnected' END, updated_at = now()
		 WHERE id = $1`,
		sessionID,
	)
	return err
}

func parseVerseSelection(c *gin.Context) (int, int) {
	surah, _ := strconv.Atoi(c.Query("surah"))
	ayah, _ := strconv.Atoi(c.Query("ayah"))
	if surah < 0 {
		surah = 0
	}
	if ayah < 0 {
		ayah = 0
	}
	return surah, ayah
}

func shouldCloseFromText(messageType int, payload []byte) bool {
	if messageType != websocket.TextMessage {
		return false
	}
	var msg struct {
		Type string `json:"type"`
	}
	if err := json.Unmarshal(payload, &msg); err != nil {
		return false
	}
	return msg.Type == "stop" || msg.Type == "close"
}

func stringValue(value interface{}, fallback string) string {
	if text, ok := value.(string); ok && text != "" {
		return text
	}
	return fallback
}

func floatValue(value interface{}) float64 {
	switch typed := value.(type) {
	case float64:
		return typed
	case float32:
		return float64(typed)
	case int:
		return float64(typed)
	case int64:
		return float64(typed)
	case json.Number:
		value, _ := typed.Float64()
		return value
	default:
		return 0
	}
}

func checkOrigin(allowedOrigins []string) func(*http.Request) bool {
	allowed := make(map[string]struct{}, len(allowedOrigins))
	allowAny := false
	for _, origin := range allowedOrigins {
		origin = strings.TrimSpace(origin)
		if origin == "" {
			continue
		}
		if origin == "*" {
			allowAny = true
			continue
		}
		allowed[origin] = struct{}{}
	}

	return func(r *http.Request) bool {
		origin := r.Header.Get("Origin")
		if origin == "" {
			return true
		}
		if allowAny {
			return true
		}
		if _, ok := allowed[origin]; ok {
			return true
		}
		parsed, err := url.Parse(origin)
		if err != nil {
			return false
		}
		requestHost := r.Host
		if strings.EqualFold(parsed.Host, requestHost) {
			return true
		}
		return false
	}
}
