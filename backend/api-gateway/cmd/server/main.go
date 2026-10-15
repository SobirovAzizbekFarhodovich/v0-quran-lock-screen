package main

import (
	"context"
	"log"
	"net/http"
	"os"
	"os/signal"
	"strings"
	"syscall"
	"time"

	"github.com/gin-contrib/cors"
	"github.com/gin-gonic/gin"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/prometheus/client_golang/prometheus/promhttp"
	"github.com/redis/go-redis/v9"

	"islamic-ai/backend/api-gateway/internal/auth"
	"islamic-ai/backend/api-gateway/internal/config"
	"islamic-ai/backend/api-gateway/internal/health"
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

	if cfg.AppEnv != "local" {
		gin.SetMode(gin.ReleaseMode)
	}
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
	router.Use(ratelimit.RedisTokenBucket(redisClient, "global", 120, time.Minute))

	// --- Public endpoints ---
	healthHandler := func(c *gin.Context) { c.JSON(http.StatusOK, gin.H{"status": "ok"}) }
	router.GET("/health", healthHandler)
	router.GET("/healthz", healthHandler)
	// Deep check (monitoring, post-deploy): 503 unless every dependency can
	// do real work. /healthz stays a cheap liveness probe.
	deepHealth := health.NewChecker(5*time.Second,
		health.PingCheck("postgres", 2*time.Second, db.Ping),
		health.PingCheck("redis", 2*time.Second, func(ctx context.Context) error { return redisClient.Ping(ctx).Err() }),
		health.HTTPCheck("ai_rag", strings.TrimRight(cfg.AIRAGURL, "/")+"/healthz", 5*time.Second, nil,
			health.StatusOKValidator("documents_in_db", "total_documents", "missing_collections")),
		health.HTTPCheck("ai_recitation", cfg.AIRecitationURL+"/healthz", 3*time.Second, nil, health.RecitationValidator),
	)
	router.GET("/healthz/deep", deepHealth.Handler)
	router.GET("/metrics", gin.WrapH(promhttp.Handler()))

	v1 := router.Group("/api/v1")
	// Each sign-up costs a bcrypt hash and creates an account that multiplies
	// every per-user budget, so sign-ups get their own per-IP limits.
	v1.POST("/auth/register",
		ratelimit.RedisTokenBucket(redisClient, "register", 5, time.Minute),
		ratelimit.RedisTokenBucket(redisClient, "register-day", 20, 24*time.Hour),
		authHandler.Register,
	)
	// Brute-force sensitive endpoints get a stricter per-IP budget on top of
	// the global limiter (the key includes the route path, so each endpoint
	// has its own bucket). Login also has a per-account limit in the handler.
	credentialLimit := ratelimit.RedisTokenBucket(redisClient, "credentials", 10, time.Minute)
	v1.POST("/auth/login", credentialLimit, authHandler.Login)
	v1.POST("/auth/refresh", authHandler.Refresh)
	v1.POST("/auth/password-reset/request", credentialLimit, authHandler.RequestPasswordReset)
	v1.POST("/auth/password-reset/confirm", credentialLimit, authHandler.ConfirmPasswordReset)
	v1.POST("/auth/oauth/google", credentialLimit, authHandler.GoogleOAuth)
	v1.POST("/auth/oauth/apple", credentialLimit, authHandler.AppleOAuth)

	// The noorain.uz website lets visitors try the AI assistant without an account:
	// 3 questions a day per IP, 15 s apart; repeated questions come from a cache.
	publicAskHandler := httpapi.NewPublicAskHandler(ragClient, redisClient, 3, 15*time.Second)
	v1.POST("/public/ask", publicAskHandler.Ask)

	// Backwards-compatible route for the installed Kotlin app's on-device Tadabbur
	// fallback (older builds call it without a token). Kept unauthenticated so those
	// builds keep working, but tightly rate-limited and length-capped; it routes
	// through the same source-grounded RAG, not a raw LLM.
	v1.POST("/chat/completions",
		ratelimit.RedisTokenBucket(redisClient, "legacy-chat", 3, 20*time.Second),
		legacyChatHandler.Complete,
	)

	// --- Protected endpoints ---
	protected := v1.Group("")
	protected.Use(auth.Middleware(tokenService))
	protected.POST("/auth/logout", authHandler.Logout)
	// The profile page: details (age, gender, Quran level, goal) and the photo.
	profileDetails := httpapi.NewProfileDetailsHandler(db, cfg.AvatarsDir)
	protected.GET("/profile", profileDetails.Get)
	protected.PUT("/profile", profileHandler.Update)
	protected.PATCH("/profile", profileDetails.Patch)
	protected.POST("/profile/avatar", ratelimit.RedisTokenBucket(redisClient, "avatar", 10, time.Hour), profileDetails.UploadAvatar)
	protected.GET("/profile/avatar", profileDetails.Avatar)
	protected.DELETE("/profile/avatar", profileDetails.DeleteAvatar)
	protected.GET("/sessions", profileHandler.Sessions)
	protected.POST("/sessions", profileHandler.CreateSession)
	// Both routes below call the paid LLM: one daily budget per user on top
	// of the per-IP limits.
	llmQuota := ratelimit.RedisUserQuota(redisClient, "llm-day", 60, 24*time.Hour)
	protected.POST("/spiritual/query",
		ratelimit.RedisTokenBucket(redisClient, "spiritual", 20, time.Minute),
		llmQuota,
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
		IdleTimeout:       120 * time.Second,
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
