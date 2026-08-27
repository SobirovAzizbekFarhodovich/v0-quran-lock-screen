package httpapi

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"fmt"
	"net/http"
	"net/url"
	"strings"

	"github.com/gin-gonic/gin"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/redis/go-redis/v9"
	"golang.org/x/crypto/bcrypt"

	"islamic-ai/backend/api-gateway/internal/auth"
)

type AuthHandler struct {
	db               *pgxpool.Pool
	tokens           *auth.TokenService
	redis            *redis.Client
	email            EmailSender
	passwordResetURL string
	googleClientIDs  []string
	appleClientID    string
}

func NewAuthHandler(db *pgxpool.Pool, tokens *auth.TokenService, redisClient *redis.Client, email EmailSender, passwordResetURL string, googleClientIDs []string, appleClientID string) *AuthHandler {
	return &AuthHandler{db: db, tokens: tokens, redis: redisClient, email: email, passwordResetURL: strings.TrimRight(passwordResetURL, "/"), googleClientIDs: googleClientIDs, appleClientID: appleClientID}
}

type authRequest struct {
	Name     string `json:"name"`
	Email    string `json:"email" binding:"required,email"`
	Password string `json:"password" binding:"required,min=8"`
	Language string `json:"language"`
}

// RequestPasswordReset deliberately gives the same response for every email so
// an attacker cannot enumerate registered accounts.
func (h *AuthHandler) RequestPasswordReset(c *gin.Context) {
	var req struct {
		Email string `json:"email" binding:"required,email"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "valid email is required"})
		return
	}
	var userID, email string
	err := h.db.QueryRow(c.Request.Context(), `SELECT id::text, email FROM users WHERE email = lower($1)`, req.Email).Scan(&userID, &email)
	if err == nil && h.email != nil {
		raw, tokenErr := newResetToken()
		if tokenErr == nil {
			_, tokenErr = h.db.Exec(c.Request.Context(), `UPDATE password_reset_tokens SET used_at = now() WHERE user_id = $1 AND used_at IS NULL`, userID)
		}
		if tokenErr == nil {
			_, tokenErr = h.db.Exec(c.Request.Context(), `INSERT INTO password_reset_tokens (user_id, token_hash, expires_at) VALUES ($1, $2, now() + interval '1 hour')`, userID, auth.HashToken(raw))
		}
		if tokenErr == nil {
			link := h.passwordResetURL + "?token=" + url.QueryEscape(raw)
			_ = h.email.Send(c.Request.Context(), email, "Reset your Islamic AI password", fmt.Sprintf("<p>Use this link within one hour:</p><p><a href=\"%s\">Reset password</a></p>", link))
		}
	}
	c.JSON(http.StatusAccepted, gin.H{"message": "If an account exists, a reset link has been sent."})
}

func (h *AuthHandler) ConfirmPasswordReset(c *gin.Context) {
	var req struct {
		Token    string `json:"token" binding:"required,len=64"`
		Password string `json:"password" binding:"required,min=8,max=128"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "a valid token and password of at least 8 characters are required"})
		return
	}
	tx, err := h.db.Begin(c.Request.Context())
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "password reset unavailable"})
		return
	}
	defer tx.Rollback(c.Request.Context())
	var userID string
	err = tx.QueryRow(c.Request.Context(), `SELECT user_id::text FROM password_reset_tokens WHERE token_hash = $1 AND used_at IS NULL AND expires_at > now() FOR UPDATE`, auth.HashToken(req.Token)).Scan(&userID)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "reset link is invalid or expired"})
		return
	}
	hash, err := bcrypt.GenerateFromPassword([]byte(req.Password), 12)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "password hash failed"})
		return
	}
	if _, err = tx.Exec(c.Request.Context(), `UPDATE users SET password_hash = $1, updated_at = now() WHERE id = $2`, string(hash), userID); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "password update failed"})
		return
	}
	if _, err = tx.Exec(c.Request.Context(), `UPDATE password_reset_tokens SET used_at = now() WHERE user_id = $1 AND used_at IS NULL`, userID); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "password reset failed"})
		return
	}
	if _, err = tx.Exec(c.Request.Context(), `UPDATE refresh_tokens SET revoked_at = now() WHERE user_id = $1 AND revoked_at IS NULL`, userID); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "session revoke failed"})
		return
	}
	if err = tx.Commit(c.Request.Context()); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "password reset failed"})
		return
	}
	c.JSON(http.StatusOK, gin.H{"message": "password reset completed"})
}

func newResetToken() (string, error) {
	data := make([]byte, 32)
	if _, err := rand.Read(data); err != nil {
		return "", err
	}
	return hex.EncodeToString(data), nil
}

func (h *AuthHandler) Register(c *gin.Context) {
	var req authRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}
	hash, err := bcrypt.GenerateFromPassword([]byte(req.Password), 12)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "password hash failed"})
		return
	}
	var user userDTO
	err = h.db.QueryRow(c.Request.Context(),
		`INSERT INTO users (name, email, password_hash, language)
		 VALUES ($1, lower($2), $3, $4)
		 RETURNING id::text, name, email, language`,
		req.Name, req.Email, string(hash), req.Language,
	).Scan(&user.ID, &user.Name, &user.Email, &user.Language)
	if err != nil {
		c.JSON(http.StatusConflict, gin.H{"error": "email already registered"})
		return
	}
	h.respondWithTokens(c, user)
}

func (h *AuthHandler) Login(c *gin.Context) {
	var req authRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}
	var user userDTO
	var hash string
	err := h.db.QueryRow(c.Request.Context(),
		`SELECT id::text, name, email, language, password_hash FROM users WHERE email = lower($1)`,
		req.Email,
	).Scan(&user.ID, &user.Name, &user.Email, &user.Language, &hash)
	if err != nil || bcrypt.CompareHashAndPassword([]byte(hash), []byte(req.Password)) != nil {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "invalid credentials"})
		return
	}
	h.respondWithTokens(c, user)
}

func (h *AuthHandler) Refresh(c *gin.Context) {
	var req struct {
		RefreshToken string `json:"refresh_token" binding:"required"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}
	claims, err := h.tokens.Parse(req.RefreshToken)
	if err != nil || claims.Type != "refresh" {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "invalid refresh token"})
		return
	}
	var user userDTO
	err = h.db.QueryRow(c.Request.Context(),
		`SELECT u.id::text, u.name, u.email, u.language
		 FROM refresh_tokens rt
		 JOIN users u ON u.id = rt.user_id
		 WHERE rt.token_hash = $1 AND rt.revoked_at IS NULL AND rt.expires_at > now()`,
		auth.HashToken(req.RefreshToken),
	).Scan(&user.ID, &user.Name, &user.Email, &user.Language)
	if err != nil {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "refresh token expired"})
		return
	}
	_, _ = h.db.Exec(c.Request.Context(), `UPDATE refresh_tokens SET revoked_at = now() WHERE token_hash = $1`, auth.HashToken(req.RefreshToken))
	h.respondWithTokens(c, user)
}

func (h *AuthHandler) Logout(c *gin.Context) {
	userID := c.GetString(auth.UserIDKey)

	// Revoke all refresh tokens for this user
	_, _ = h.db.Exec(c.Request.Context(),
		`UPDATE refresh_tokens SET revoked_at = now() WHERE user_id = $1 AND revoked_at IS NULL`,
		userID,
	)

	// Blacklist the current access token in Redis (expires when the token would)
	tokenHeader := c.GetHeader("Authorization")
	if len(tokenHeader) > 7 {
		rawToken := tokenHeader[7:] // Strip "Bearer "
		claims, err := h.tokens.Parse(rawToken)
		if err == nil && claims.ExpiresAt != nil {
			ttl := claims.ExpiresAt.Time.Sub(claims.IssuedAt.Time)
			if ttl > 0 {
				_ = h.redis.Set(c.Request.Context(), "blacklist:"+claims.ID, "1", ttl).Err()
			}
		}
	}

	c.JSON(http.StatusOK, gin.H{"message": "logged out"})
}

func (h *AuthHandler) respondWithTokens(c *gin.Context, user userDTO) {
	pair, err := h.tokens.Issue(user.ID, user.Email)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "token issue failed"})
		return
	}
	if err := storeRefreshToken(c.Request.Context(), h.db, user.ID, pair); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "refresh token store failed"})
		return
	}
	c.JSON(http.StatusOK, gin.H{
		"access_token":  pair.AccessToken,
		"refresh_token": pair.RefreshToken,
		"user":          user,
	})
}

func storeRefreshToken(ctx context.Context, db *pgxpool.Pool, userID string, pair auth.TokenPair) error {
	_, err := db.Exec(ctx,
		`INSERT INTO refresh_tokens (user_id, token_hash, expires_at) VALUES ($1, $2, $3)`,
		userID, pair.RefreshHash, pair.RefreshExp,
	)
	return err
}

func scanUserByID(ctx context.Context, db *pgxpool.Pool, userID string) (userDTO, error) {
	var user userDTO
	err := db.QueryRow(ctx,
		`SELECT id::text, name, email, language FROM users WHERE id = $1`,
		userID,
	).Scan(&user.ID, &user.Name, &user.Email, &user.Language)
	if err == pgx.ErrNoRows {
		return userDTO{}, err
	}
	return user, err
}
