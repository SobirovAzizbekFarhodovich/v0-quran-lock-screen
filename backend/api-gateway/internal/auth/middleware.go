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

		// Check Redis blacklist (token revoked on logout)
		if redisClient, exists := c.Get("redis"); exists {
			if rc, ok := redisClient.(*redis.Client); ok {
				val, _ := rc.Get(c.Request.Context(), "blacklist:"+claims.ID).Result()
				if val != "" {
					c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{"error": "token revoked"})
					return
				}
			}
		}

		c.Set(UserIDKey, claims.UserID)
		c.Next()
	}
}

func WebSocketMiddleware(tokens *TokenService) gin.HandlerFunc {
	return func(c *gin.Context) {
		tokenValue := c.Query("token")
		if tokenValue == "" {
			header := c.GetHeader("Authorization")
			tokenValue = strings.TrimPrefix(header, "Bearer ")
		}
		claims, err := tokens.Parse(tokenValue)
		if err != nil || claims.Type != "access" {
			c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{"error": "invalid token"})
			return
		}
		if redisClient, exists := c.Get("redis"); exists {
			if rc, ok := redisClient.(*redis.Client); ok {
				if revoked, _ := rc.Exists(c.Request.Context(), "blacklist:"+claims.ID).Result(); revoked > 0 {
					c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{"error": "token revoked"})
					return
				}
			}
		}
		c.Set(UserIDKey, claims.UserID)
		c.Next()
	}
}
