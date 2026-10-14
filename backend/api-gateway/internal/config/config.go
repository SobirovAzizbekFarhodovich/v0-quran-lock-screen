package config

import (
	"errors"
	"os"
	"strconv"
	"strings"
	"time"
)

type Config struct {
	AppEnv           string
	Port             string
	DatabaseURL      string
	DBMaxConns       int
	DBMinConns       int
	RedisAddr        string
	RedisPassword    string
	RedisDB          int
	JWTSecret        string
	AccessTTL        time.Duration
	RefreshTTL       time.Duration
	AIRAGURL         string
	AIRecitationURL  string
	CORSOrigins      []string
	SentryDSN        string
	PublicBaseURL    string
	PasswordResetURL string
	ResendAPIKey     string
	EmailFrom        string
	GoogleClientIDs  []string
	AppleClientID    string

	RecitationReportsDir        string
	AvatarsDir                  string
	RecitationReportsDailyLimit int
	RecitationReportsMinFreeMB  int
}

func Load() Config {
	return Config{
		AppEnv:           env("APP_ENV", "local"),
		Port:             env("API_PORT", "8080"),
		DatabaseURL:      env("DATABASE_URL", "postgres://islamic_ai:change-me@localhost:5432/islamic_ai?sslmode=disable"),
		DBMaxConns:       envInt("DB_MAX_CONNS", 20),
		DBMinConns:       envInt("DB_MIN_CONNS", 2),
		RedisAddr:        env("REDIS_ADDR", "localhost:6379"),
		RedisPassword:    env("REDIS_PASSWORD", ""),
		RedisDB:          envInt("REDIS_DB", 0),
		JWTSecret:        env("JWT_SECRET", "dev-secret-change-me"),
		AccessTTL:        envDuration("JWT_ACCESS_TTL", 15*time.Minute),
		RefreshTTL:       envDuration("JWT_REFRESH_TTL", 30*24*time.Hour),
		AIRAGURL:         env("AI_RAG_URL", "http://localhost:8002"),
		AIRecitationURL:  strings.TrimRight(env("AI_RECITATION_URL", "http://localhost:8001"), "/"),
		CORSOrigins:      envSlice("CORS_ORIGINS", []string{"*"}),
		SentryDSN:        env("SENTRY_DSN", ""),
		PublicBaseURL:    strings.TrimRight(env("PUBLIC_BASE_URL", "http://localhost:8080"), "/"),
		PasswordResetURL: strings.TrimRight(env("PASSWORD_RESET_URL", "islamicai:///reset-password"), "/"),
		ResendAPIKey:     env("RESEND_API_KEY", ""),
		EmailFrom:        env("EMAIL_FROM", ""),
		GoogleClientIDs:  envSlice("GOOGLE_CLIENT_IDS", nil),
		AppleClientID:    env("APPLE_CLIENT_ID", ""),

		RecitationReportsDir:        env("RECITATION_REPORTS_DIR", "/data/recitation-reports"),
		AvatarsDir:                  env("AVATARS_DIR", "/data/avatars"),
		RecitationReportsDailyLimit: envInt("RECITATION_REPORTS_DAILY_LIMIT", 20),
		RecitationReportsMinFreeMB:  envInt("RECITATION_REPORTS_MIN_FREE_MB", 1024),
	}
}

func (c Config) Validate() error {
	if c.JWTSecret == "" {
		return errors.New("JWT_SECRET is required")
	}
	if c.DBMaxConns < 1 {
		return errors.New("DB_MAX_CONNS must be greater than zero")
	}
	if c.DBMinConns < 0 || c.DBMinConns > c.DBMaxConns {
		return errors.New("DB_MIN_CONNS must be between 0 and DB_MAX_CONNS")
	}
	if c.AppEnv == "production" {
		if len(c.JWTSecret) < 32 || c.JWTSecret == "dev-secret-change-me" {
			return errors.New("production JWT_SECRET must be at least 32 characters and not use the development fallback")
		}
		if !strings.HasPrefix(c.PublicBaseURL, "https://") {
			return errors.New("production PUBLIC_BASE_URL must use https")
		}
		if !strings.HasPrefix(c.PasswordResetURL, "https://") && !strings.HasPrefix(c.PasswordResetURL, "islamicai://") {
			return errors.New("PASSWORD_RESET_URL must be an https universal link or islamicai deep link")
		}
		if c.ResendAPIKey == "" || c.EmailFrom == "" {
			return errors.New("production RESEND_API_KEY and EMAIL_FROM are required for password reset")
		}
		for _, origin := range c.CORSOrigins {
			if strings.TrimSpace(origin) == "*" {
				return errors.New("production CORS_ORIGINS must not contain *")
			}
		}
	}
	return nil
}

func env(key, fallback string) string {
	value := os.Getenv(key)
	if value == "" {
		return fallback
	}
	return value
}

func envInt(key string, fallback int) int {
	value, err := strconv.Atoi(os.Getenv(key))
	if err != nil {
		return fallback
	}
	return value
}

func envDuration(key string, fallback time.Duration) time.Duration {
	value := os.Getenv(key)
	if value == "" {
		return fallback
	}
	parsed, err := time.ParseDuration(value)
	if err != nil {
		return fallback
	}
	return parsed
}

func envSlice(key string, fallback []string) []string {
	value := os.Getenv(key)
	if value == "" {
		return fallback
	}
	return strings.Split(value, ",")
}
