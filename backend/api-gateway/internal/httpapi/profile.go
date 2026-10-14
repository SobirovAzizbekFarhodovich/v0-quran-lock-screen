package httpapi

import (
	"net/http"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/jackc/pgx/v5/pgxpool"

	"islamic-ai/backend/api-gateway/internal/auth"
)

type userDTO struct {
	ID       string `json:"id"`
	Name     string `json:"name"`
	Email    string `json:"email"`
	Language string `json:"language"`
}

type ProfileHandler struct {
	db *pgxpool.Pool
}

func NewProfileHandler(db *pgxpool.Pool) *ProfileHandler {
	return &ProfileHandler{db: db}
}

func (h *ProfileHandler) Get(c *gin.Context) {
	user, err := scanUserByID(c.Request.Context(), h.db, c.GetString(auth.UserIDKey))
	if err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "user not found"})
		return
	}
	c.JSON(http.StatusOK, user)
}

func (h *ProfileHandler) Update(c *gin.Context) {
	var req struct {
		Name     string `json:"name" binding:"required"`
		Language string `json:"language" binding:"required"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}
	var user userDTO
	err := h.db.QueryRow(c.Request.Context(),
		`UPDATE users SET name = $1, language = $2, updated_at = now()
		 WHERE id = $3 RETURNING id::text, name, email, language`,
		cleanName(req.Name), normalizeLanguage(req.Language), c.GetString(auth.UserIDKey),
	).Scan(&user.ID, &user.Name, &user.Email, &user.Language)
	if err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "user not found"})
		return
	}
	c.JSON(http.StatusOK, user)
}

func (h *ProfileHandler) Sessions(c *gin.Context) {
	rows, err := h.db.Query(c.Request.Context(),
		`SELECT id::text, status, COALESCE(surah, 0), COALESCE(ayah, 0),
		        COALESCE(score, 0), COALESCE(transcript, ''), created_at
		 FROM recitation_sessions
		 WHERE user_id = $1
		 ORDER BY created_at DESC
		 LIMIT 50`,
		c.GetString(auth.UserIDKey),
	)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "sessions query failed"})
		return
	}
	defer rows.Close()

	sessions := make([]gin.H, 0)
	for rows.Next() {
		var id, status, transcript string
		var createdAt time.Time
		var score float64
		var surah, ayah int
		if err := rows.Scan(&id, &status, &surah, &ayah, &score, &transcript, &createdAt); err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "sessions scan failed"})
			return
		}
		sessions = append(sessions, gin.H{
			"id":         id,
			"status":     status,
			"surah":      surah,
			"ayah":       ayah,
			"score":      score,
			"transcript": transcript,
			"created_at": createdAt.Format(time.RFC3339),
		})
	}
	c.JSON(http.StatusOK, gin.H{"sessions": sessions})
}

func (h *ProfileHandler) CreateSession(c *gin.Context) {
	var req struct {
		Surah  int    `json:"surah"`
		Ayah   int    `json:"ayah"`
		Status string `json:"status"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}
	if req.Status == "" {
		req.Status = "active"
	}
	if req.Status != "active" && req.Status != "completed" && req.Status != "stopped" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid session status"})
		return
	}
	var surah interface{}
	var ayah interface{}
	if req.Surah > 0 {
		surah = req.Surah
	}
	if req.Ayah > 0 {
		ayah = req.Ayah
	}

	var id string
	var createdAt time.Time
	err := h.db.QueryRow(c.Request.Context(),
		`INSERT INTO recitation_sessions (user_id, surah, ayah, status)
		 VALUES ($1, $2, $3, $4)
		 RETURNING id::text, created_at`,
		c.GetString(auth.UserIDKey), surah, ayah, req.Status,
	).Scan(&id, &createdAt)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "session create failed"})
		return
	}

	c.JSON(http.StatusCreated, gin.H{
		"id":         id,
		"status":     req.Status,
		"surah":      req.Surah,
		"ayah":       req.Ayah,
		"created_at": createdAt.Format(time.RFC3339),
	})
}
