package ratelimit

import (
	"fmt"
	"net/http"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/redis/go-redis/v9"

	"islamic-ai/backend/api-gateway/internal/auth"
)

// RedisTokenBucket implements IP-based fixed-window rate limiting using Redis.
// Returns 429 with Retry-After header when limit is exceeded.
//
// name keeps limiters apart: the global limiter and a route's own limiter see
// the same IP and path, and with a shared key every request counted twice and
// the global 60 s window replaced the route's (an "hourly" limit reset every
// minute).
func RedisTokenBucket(client *redis.Client, name string, limit int64, window time.Duration) gin.HandlerFunc {
	return func(c *gin.Context) {
		key := "rate:" + name + ":" + c.ClientIP() + ":" + c.FullPath()
		count, ttl, err := Hit(c, client, key, window)
		if err == nil && count > limit {
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

// Hit counts one request against key and returns the count so far in the
// current window and the time left in it. INCR and the expiry go in one
// MULTI, so a failed EXPIRE can no longer leave a key that never resets.
func Hit(c *gin.Context, client *redis.Client, key string, window time.Duration) (int64, time.Duration, error) {
	ctx := c.Request.Context()
	var incr *redis.IntCmd
	var ttl *redis.DurationCmd
	_, err := client.TxPipelined(ctx, func(pipe redis.Pipeliner) error {
		incr = pipe.Incr(ctx, key)
		pipe.ExpireNX(ctx, key, window)
		ttl = pipe.TTL(ctx, key)
		return nil
	})
	if err != nil {
		return 0, 0, err
	}
	return incr.Val(), ttl.Val(), nil
}

// RedisUserQuota caps how often one signed-in user may call the routes it is
// attached to (it must run after auth.Middleware). Routes that share a name
// share the budget, which is how the LLM-backed routes get one daily quota.
func RedisUserQuota(client *redis.Client, name string, limit int64, window time.Duration) gin.HandlerFunc {
	return func(c *gin.Context) {
		userID := c.GetString(auth.UserIDKey)
		if userID == "" {
			c.Next()
			return
		}
		count, ttl, err := Hit(c, client, "quota:"+name+":"+userID, window)
		if err == nil && count > limit {
			retryAfter := int(ttl.Seconds())
			if retryAfter <= 0 {
				retryAfter = int(window.Seconds())
			}
			c.Header("Retry-After", fmt.Sprintf("%d", retryAfter))
			c.AbortWithStatusJSON(http.StatusTooManyRequests, gin.H{
				"error":       "daily limit reached",
				"retry_after": retryAfter,
			})
			return
		}
		c.Next()
	}
}
