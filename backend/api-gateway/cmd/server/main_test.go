package main

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/gin-gonic/gin"
)

func TestTrustedProxyClientIP(t *testing.T) {
	gin.SetMode(gin.TestMode)
	router := gin.New()
	if err := router.SetTrustedProxies(trustedProxyCIDRs); err != nil {
		t.Fatal(err)
	}
	router.GET("/ip", func(c *gin.Context) { c.String(http.StatusOK, c.ClientIP()) })

	get := func(remote, xff string) string {
		req := httptest.NewRequest(http.MethodGet, "/ip", nil)
		req.RemoteAddr = remote
		if xff != "" {
			req.Header.Set("X-Forwarded-For", xff)
		}
		rec := httptest.NewRecorder()
		router.ServeHTTP(rec, req)
		return rec.Body.String()
	}

	// nginx on the Docker network: the real client IP comes from XFF, and a
	// client-supplied leftmost value is ignored.
	if got := get("172.18.0.5:5000", "9.9.9.9, 203.0.113.7"); got != "203.0.113.7" {
		t.Fatalf("via nginx: ClientIP = %q, want 203.0.113.7", got)
	}
	// Direct (untrusted) peer cannot spoof its IP with XFF.
	if got := get("198.51.100.4:5000", "1.2.3.4"); got != "198.51.100.4" {
		t.Fatalf("direct: ClientIP = %q, want 198.51.100.4", got)
	}
}
