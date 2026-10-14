package httpapi

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"net/http"
	"strconv"
	"strings"
	"time"
	"unicode/utf8"

	"github.com/gin-gonic/gin"
	"github.com/redis/go-redis/v9"

	"islamic-ai/backend/api-gateway/internal/metrics"
	"islamic-ai/backend/api-gateway/internal/rag"
)

// PublicAskHandler serves POST /api/v1/public/ask: the noorain.uz website lets visitors
// try the AI assistant without an account. Every answer costs an external LLM call, so
// it is narrow on purpose: one question with no history, a short length cap, a pause
// between questions and a small daily budget per IP. Identical questions are answered
// from a cache (the site's suggested questions cost nothing) and do not use the budget.
type PublicAskHandler struct {
	client   ragQuerier
	store    askStore
	daily    int64
	gap      time.Duration
	cacheTTL time.Duration
	now      func() time.Time
}

type ragQuerier interface {
	Query(ctx context.Context, req rag.QueryRequest) (rag.QueryResponse, error)
}

// askStore is the slice of Redis the handler needs (a fake in tests).
type askStore interface {
	Get(ctx context.Context, key string) (string, error)
	Set(ctx context.Context, key, value string, ttl time.Duration) error
	SetNX(ctx context.Context, key string, ttl time.Duration) (bool, error)
	TTL(ctx context.Context, key string) (time.Duration, error)
	Incr(ctx context.Context, key string, ttl time.Duration) (int64, error)
	Decr(ctx context.Context, key string) error
}

const (
	publicAskMaxRunes = 300
	errNotFound       = "not found"
)

var publicAskLanguages = map[string]string{
	"uz": "uz_latin", "uz_latin": "uz_latin",
	"uzc": "uz_cyrillic", "uz_cyrillic": "uz_cyrillic",
	"ru": "ru", "en": "en",
}

func NewPublicAskHandler(client *rag.Client, redisClient *redis.Client, daily int64, gap time.Duration) *PublicAskHandler {
	return &PublicAskHandler{
		client:   client,
		store:    redisAskStore{redisClient},
		daily:    daily,
		gap:      gap,
		cacheTTL: 24 * time.Hour,
		now:      time.Now,
	}
}

type publicAskRequest struct {
	Question string `json:"question" binding:"required,max=2000"`
	Language string `json:"language" binding:"max=16"`
}

type publicAskAnswer struct {
	Answer  string        `json:"answer"`
	Sources []interface{} `json:"sources"`
}

func (h *PublicAskHandler) Ask(c *gin.Context) {
	start := time.Now()
	observe := func(status string) {
		metrics.RAGQueryDuration.WithLabelValues("public_" + status).Observe(time.Since(start).Seconds())
	}
	var req publicAskRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		observe("bad_request")
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid_request"})
		return
	}
	question := strings.Join(strings.Fields(req.Question), " ")
	if question == "" || utf8.RuneCountInString(question) > publicAskMaxRunes {
		observe("bad_request")
		c.JSON(http.StatusBadRequest, gin.H{"error": "question_length", "max": publicAskMaxRunes})
		return
	}
	lang, ok := publicAskLanguages[req.Language]
	if !ok {
		lang = "uz_latin"
	}

	ctx := c.Request.Context()
	ip := c.ClientIP()
	dayKey := "pubask:day:" + h.now().UTC().Format("2006-01-02") + ":" + ip
	sum := sha256.Sum256([]byte(lang + "\x00" + strings.ToLower(question)))
	cacheKey := "pubask:cache:" + hex.EncodeToString(sum[:])

	if cached, err := h.store.Get(ctx, cacheKey); err == nil {
		var answer publicAskAnswer
		if json.Unmarshal([]byte(cached), &answer) == nil {
			used, _ := h.store.Get(ctx, dayKey)
			n, _ := strconv.ParseInt(used, 10, 64)
			observe("cached")
			c.JSON(http.StatusOK, gin.H{"answer": answer.Answer, "sources": answer.Sources, "remaining": max(0, h.daily-n), "cached": true})
			return
		}
	}

	// A pause between questions, then the daily budget. Redis errors fail open, like
	// the gateway's other limiters; the global per-IP limiter still applies.
	if fresh, err := h.store.SetNX(ctx, "pubask:gap:"+ip, h.gap); err == nil && !fresh {
		ttl, _ := h.store.TTL(ctx, "pubask:gap:"+ip)
		retry := int(ttl.Round(time.Second).Seconds())
		if retry <= 0 {
			retry = int(h.gap.Seconds())
		}
		c.Header("Retry-After", strconv.Itoa(retry))
		observe("too_fast")
		c.JSON(http.StatusTooManyRequests, gin.H{"error": "too_fast", "retry_after": retry})
		return
	}
	n, incrErr := h.store.Incr(ctx, dayKey, 26*time.Hour)
	if incrErr == nil && n > h.daily {
		observe("daily_limit")
		c.JSON(http.StatusTooManyRequests, gin.H{"error": "daily_limit", "limit": h.daily})
		return
	}

	// The question runs to the end even if the visitor disconnects: the LLM is
	// paid for either way, the grounded answer still lands in the cache, and
	// dropping the connection no longer hands the question back (which made
	// the daily budget unlimited).
	queryCtx, cancelQuery := context.WithTimeout(context.WithoutCancel(ctx), publicAskQueryTimeout)
	defer cancelQuery()
	result, err := h.client.Query(queryCtx, rag.QueryRequest{Message: question, Language: lang})
	if err != nil {
		// The assistant failed to answer: give the question back to their budget.
		_ = h.store.Decr(context.WithoutCancel(ctx), dayKey)
		var statusErr *rag.StatusError
		switch {
		case errors.As(err, &statusErr) && statusErr.IsClientError():
			observe("bad_request")
			c.JSON(http.StatusBadRequest, gin.H{"error": "invalid_request"})
		default:
			observe("error")
			c.JSON(http.StatusServiceUnavailable, gin.H{"error": "unavailable"})
		}
		return
	}

	answer := publicAskAnswer{Answer: strings.TrimSpace(result.Answer), Sources: result.Sources}
	if answer.Sources == nil {
		answer.Sources = []interface{}{}
	}
	// Only grounded answers are cached; greetings and refusals are cheap and personal.
	if answer.Answer != "" && len(answer.Sources) > 0 {
		if payload, err := json.Marshal(answer); err == nil {
			_ = h.store.Set(context.WithoutCancel(ctx), cacheKey, string(payload), h.cacheTTL)
		}
	}
	remaining := max(0, h.daily-n)
	observe("ok")
	c.JSON(http.StatusOK, gin.H{"answer": answer.Answer, "sources": answer.Sources, "remaining": remaining, "cached": false})
}

// publicAskQueryTimeout bounds a website question that keeps running after
// the visitor has gone.
const publicAskQueryTimeout = 90 * time.Second

type redisAskStore struct{ r *redis.Client }

func (s redisAskStore) Get(ctx context.Context, key string) (string, error) {
	v, err := s.r.Get(ctx, key).Result()
	if errors.Is(err, redis.Nil) {
		return "", errors.New(errNotFound)
	}
	return v, err
}

func (s redisAskStore) Set(ctx context.Context, key, value string, ttl time.Duration) error {
	return s.r.Set(ctx, key, value, ttl).Err()
}

func (s redisAskStore) SetNX(ctx context.Context, key string, ttl time.Duration) (bool, error) {
	return s.r.SetNX(ctx, key, 1, ttl).Result()
}

func (s redisAskStore) TTL(ctx context.Context, key string) (time.Duration, error) {
	return s.r.TTL(ctx, key).Result()
}

func (s redisAskStore) Incr(ctx context.Context, key string, ttl time.Duration) (int64, error) {
	n, err := s.r.Incr(ctx, key).Result()
	if err == nil && n == 1 {
		_ = s.r.Expire(ctx, key, ttl).Err()
	}
	return n, err
}

func (s redisAskStore) Decr(ctx context.Context, key string) error {
	return s.r.Decr(ctx, key).Err()
}
