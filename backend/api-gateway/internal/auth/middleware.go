package auth

import (
	"net/http"
	"strings"

	"github.com/gin-gonic/gin"
	"github.com/redis/go-redis/v9"
)

const UserIDKey = "user_id"

// Middleware authenticates requests using Bearer JWT tokens.
// It also checks Redis blacklist for logged-out tokens.
func Middleware(tokens *TokenService) gin.HandlerFunc {
	return func(c *gin.Context) {
		header := c.GetHeader("Authorization")
		if !strings.HasPrefix(header, "Bearer ") {
			c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{"error": "missing bearer token"})
			return
		}
		claims, err := tokens.Parse(strings.TrimPrefix(header, "Bearer "))
		if err != nil || claims.Type != "access" {
			c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{"error": "invalid token"})
			return
		}

		if revokedOrUnknown(c, claims.ID) {
			return
		}

		c.Set(UserIDKey, claims.UserID)
		c.Next()
	}
}

// revokedOrUnknown checks the logout blacklist and aborts the request when the
// token was revoked. It fails closed: if Redis cannot answer, a logged-out
// token must not start working again, so the request gets a 503 instead.
func revokedOrUnknown(c *gin.Context, tokenID string) bool {
	redisClient, exists := c.Get("redis")
	if !exists {
		return false
	}
	rc, ok := redisClient.(*redis.Client)
	if !ok {
		return false
	}
	revoked, err := rc.Exists(c.Request.Context(), "blacklist:"+tokenID).Result()
	if err != nil {
		c.AbortWithStatusJSON(http.StatusServiceUnavailable, gin.H{"error": "try again shortly"})
		return true
	}
	if revoked > 0 {
		c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{"error": "token revoked"})
		return true
	}
	return false
}

func WebSocketMiddleware(tokens *TokenService) gin.HandlerFunc {
	return func(c *gin.Context) {
		// Header only: a ?token= query parameter ends up in nginx and gin
		// access logs. The Android app sends the Authorization header.
		header := c.GetHeader("Authorization")
		if !strings.HasPrefix(header, "Bearer ") {
			c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{"error": "missing bearer token"})
			return
		}
		claims, err := tokens.Parse(strings.TrimPrefix(header, "Bearer "))
		if err != nil || claims.Type != "access" {
			c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{"error": "invalid token"})
			return
		}
		if revokedOrUnknown(c, claims.ID) {
			return
		}
		c.Set(UserIDKey, claims.UserID)
		c.Next()
	}
}
