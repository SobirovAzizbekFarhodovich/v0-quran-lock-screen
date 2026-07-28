package ratelimit

import (
	"fmt"
	"net/http"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/redis/go-redis/v9"
)

// RedisTokenBucket implements IP-based rate limiting using Redis.
// Returns 429 with Retry-After header when limit is exceeded.
func RedisTokenBucket(client *redis.Client, limit int64, window time.Duration) gin.HandlerFunc {
	return func(c *gin.Context) {
		key := "rate:" + c.ClientIP() + ":" + c.FullPath()
		count, err := client.Incr(c.Request.Context(), key).Result()
		if err == nil && count == 1 {
			_ = client.Expire(c.Request.Context(), key, window).Err()
		}
		if err == nil && count > limit {
			ttl, _ := client.TTL(c.Request.Context(), key).Result()
			retryAfter := int(ttl.Seconds())
			if retryAfter <= 0 {
				retryAfter = int(window.Seconds())
			}
			c.Header("Retry-After", fmt.Sprintf("%d", retryAfter))
			c.AbortWithStatusJSON(http.StatusTooManyRequests, gin.H{
				"error":       "rate limit exceeded",
				"retry_after": retryAfter,
			})
			return
		}
		c.Next()
	}
}
