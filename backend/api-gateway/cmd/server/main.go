package main

import (
	"context"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/gin-contrib/cors"
	"github.com/gin-gonic/gin"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/prometheus/client_golang/prometheus/promhttp"
	"github.com/redis/go-redis/v9"

	"islamic-ai/backend/api-gateway/internal/auth"
	"islamic-ai/backend/api-gateway/internal/config"
	"islamic-ai/backend/api-gateway/internal/httpapi"
	"islamic-ai/backend/api-gateway/internal/rag"
	"islamic-ai/backend/api-gateway/internal/ratelimit"
	"islamic-ai/backend/api-gateway/internal/schema"
	"islamic-ai/backend/api-gateway/internal/ws"
)

// trustedProxyCIDRs are the private ranges Docker networks are allocated
// from; nginx reaches the gateway from one of them.
var trustedProxyCIDRs = []string{"10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16"}

func main() {
	cfg := config.Load()
	if err := cfg.Validate(); err != nil {
		log.Fatalf("config validation: %v", err)
	}
	ctx := context.Background()

	dbConfig, err := pgxpool.ParseConfig(cfg.DatabaseURL)
	if err != nil {
		log.Fatalf("postgres config: %v", err)
	}
	dbConfig.MaxConns = int32(cfg.DBMaxConns)
	dbConfig.MinConns = int32(cfg.DBMinConns)
	db, err := pgxpool.NewWithConfig(ctx, dbConfig)
	if err != nil {
		log.Fatalf("postgres connect: %v", err)
	}
	defer db.Close()

	if err := db.Ping(ctx); err != nil {
		log.Fatalf("postgres ping: %v", err)
	}

	// migrations/ only run on a fresh postgres volume; apply the idempotent ones that
	// back the recitation sync/report APIs to existing databases too.
	if err := schema.Apply(ctx, db); err != nil {
		log.Printf("WARNING: schema apply failed, recitation sync/report endpoints will fail: %v", err)
	}

	redisClient := redis.NewClient(&redis.Options{
		Addr:     cfg.RedisAddr,
		Password: cfg.RedisPassword,
		DB:       cfg.RedisDB,
	})
	if err := redisClient.Ping(ctx).Err(); err != nil {
		log.Fatalf("redis ping: %v", err)
	}

	tokenService := auth.NewTokenService(cfg.JWTSecret, cfg.AccessTTL, cfg.RefreshTTL)
	authHandler := httpapi.NewAuthHandler(db, tokenService, redisClient, httpapi.NewResendEmailSender(cfg.ResendAPIKey, cfg.EmailFrom), cfg.PasswordResetURL, cfg.GoogleClientIDs, cfg.AppleClientID)
	profileHandler := httpapi.NewProfileHandler(db)
	// Matches the Android client's readTimeout: classify_intent + answer
	// generation each chain a primary LLM call with a fallback, so a slow
	// round trip can legitimately take tens of seconds.
	ragClient := rag.NewClient(cfg.AIRAGURL, 90*time.Second)
	ragHandler := httpapi.NewRAGHandler(ragClient)
	legacyChatHandler := httpapi.NewLegacyChatHandler(ragClient)
	recitationResultsHandler := httpapi.NewRecitationResultsHandler(db)
	recitationReportsHandler := httpapi.NewRecitationReportsHandler(db, httpapi.RecitationReportsConfig{
		Dir:        cfg.RecitationReportsDir,
		DailyLimit: cfg.RecitationReportsDailyLimit,
		MinFreeMB:  int64(cfg.RecitationReportsMinFreeMB),
	})
	wsHandler := ws.NewHandler(redisClient, db, cfg.CORSOrigins)

	router := gin.New()

	// Only trust X-Forwarded-For / X-Real-IP from the nginx container on the
	// Docker network; otherwise any client could spoof its IP and bypass the
	// per-IP rate limiter.
	if err := router.SetTrustedProxies(trustedProxyCIDRs); err != nil {
		log.Fatalf("trusted proxies: %v", err)
	}

	// --- Middleware Stack ---

	// CORS
	router.Use(cors.New(cors.Config{
		AllowOrigins:     cfg.CORSOrigins,
		AllowMethods:     []string{"GET", "POST", "PUT", "DELETE", "OPTIONS"},
		AllowHeaders:     []string{"Origin", "Content-Type", "Authorization", "Accept"},
		ExposeHeaders:    []string{"Content-Length", "Retry-After"},
		AllowCredentials: true,
		MaxAge:           12 * time.Hour,
	}))

	// Logger + Recovery
	router.Use(gin.Logger(), gin.Recovery())

	// Security headers
	router.Use(func(c *gin.Context) {
		c.Header("X-Content-Type-Options", "nosniff")
		c.Header("X-Frame-Options", "DENY")
		c.Header("X-XSS-Protection", "1; mode=block")
		c.Header("Referrer-Policy", "strict-origin-when-cross-origin")
		c.Next()
	})

	// Request body limit (10 MB). Recitation reports carry up to 15 minutes of PCM
	// audio and enforce their own, larger limit in the handler.
	router.Use(func(c *gin.Context) {
		if c.Request.URL.Path != httpapi.RecitationReportsPath {
			c.Request.Body = http.MaxBytesReader(c.Writer, c.Request.Body, 10<<20)
		}
		c.Next()
	})

	// Inject Redis into context for blacklist checks
	router.Use(func(c *gin.Context) {
		c.Set("redis", redisClient)
		c.Next()
	})

	// Global IP-based rate limit
	router.Use(ratelimit.RedisTokenBucket(redisClient, 120, time.Minute))

	// --- Public endpoints ---
	healthHandler := func(c *gin.Context) { c.JSON(http.StatusOK, gin.H{"status": "ok"}) }
	router.GET("/health", healthHandler)
	router.GET("/healthz", healthHandler)
	router.GET("/metrics", gin.WrapH(promhttp.Handler()))

	v1 := router.Group("/api/v1")
	v1.POST("/auth/register", authHandler.Register)
	// Brute-force sensitive endpoints get a stricter per-IP budget on top of
	// the global limiter (the key includes the route path, so each endpoint
	// has its own bucket).
	credentialLimit := ratelimit.RedisTokenBucket(redisClient, 10, time.Minute)
	v1.POST("/auth/login", credentialLimit, authHandler.Login)
	v1.POST("/auth/refresh", authHandler.Refresh)
	v1.POST("/auth/password-reset/request", credentialLimit, authHandler.RequestPasswordReset)
	v1.POST("/auth/password-reset/confirm", credentialLimit, authHandler.ConfirmPasswordReset)
	v1.POST("/auth/oauth/google", authHandler.GoogleOAuth)
	v1.POST("/auth/oauth/apple", authHandler.AppleOAuth)
	// Temporary backwards-compatible route for the Kotlin Tadabbur screen.
	// Keep it strictly rate-limited because it can invoke an external LLM.
	v1.POST("/chat/completions",
		ratelimit.RedisTokenBucket(redisClient, 2, 30*time.Second),
		legacyChatHandler.Complete,
	)

	// --- Protected endpoints ---
	protected := v1.Group("")
	protected.Use(auth.Middleware(tokenService))
	protected.POST("/auth/logout", authHandler.Logout)
	protected.GET("/profile", profileHandler.Get)
	protected.PUT("/profile", profileHandler.Update)
	protected.GET("/sessions", profileHandler.Sessions)
	protected.POST("/sessions", profileHandler.CreateSession)
	protected.POST("/spiritual/query",
		ratelimit.RedisTokenBucket(redisClient, 20, time.Minute),
		ragHandler.Query,
	)
	protected.POST("/recitation/results", recitationResultsHandler.Upload)
	protected.GET("/recitation/results", recitationResultsHandler.List)
	protected.DELETE("/recitation/results", recitationResultsHandler.Delete)
	protected.POST("/recitation/reports", recitationReportsHandler.Create)

	// --- WebSocket ---
	router.GET("/ws/recitation", auth.WebSocketMiddleware(tokenService), wsHandler.Recitation)

	server := &http.Server{
		Addr:              ":" + cfg.Port,
		Handler:           router,
		ReadHeaderTimeout: 5 * time.Second,
	}

	go func() {
		log.Printf("api-gateway listening on :%s", cfg.Port)
		if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatalf("listen: %s\n", err)
		}
	}()

	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit
	log.Println("Shutting down api-gateway...")

	ctxShutdown, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	if err := server.Shutdown(ctxShutdown); err != nil {
		log.Fatal("Server forced to shutdown: ", err)
	}

	log.Println("Server exiting")
}
