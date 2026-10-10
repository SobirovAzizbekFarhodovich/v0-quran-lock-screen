package httpapi

import (
	"errors"
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/gin-gonic/gin"
	"github.com/jackc/pgx/v5/pgconn"
)

func TestNewResetToken(t *testing.T) {
	first, err := newResetToken()
	if err != nil {
		t.Fatalf("newResetToken() error = %v", err)
	}
	second, err := newResetToken()
	if err != nil || len(first) != 64 || first == second {
		t.Fatalf("unexpected reset tokens: %q, %q, error=%v", first, second, err)
	}
}

func TestNormalizeLanguage(t *testing.T) {
	cases := map[string]string{"": "uz", "   ": "uz", "ru": "ru", " en ": "en"}
	for in, want := range cases {
		if got := normalizeLanguage(in); got != want {
			t.Errorf("normalizeLanguage(%q) = %q, want %q", in, got, want)
		}
	}
}

func TestIsUniqueViolation(t *testing.T) {
	if !isUniqueViolation(fmt.Errorf("wrapped: %w", &pgconn.PgError{Code: "23505"})) {
		t.Error("23505 should be a unique violation")
	}
	if isUniqueViolation(&pgconn.PgError{Code: "23502"}) {
		t.Error("not-null violation must not map to 409")
	}
	if isUniqueViolation(errors.New("connection refused")) {
		t.Error("plain errors must not map to 409")
	}
}

func TestAuthRequestPasswordMax72(t *testing.T) {
	gin.SetMode(gin.TestMode)
	bind := func(pw string) error {
		body := `{"email":"a@b.co","password":"` + pw + `"}`
		c, _ := gin.CreateTestContext(httptest.NewRecorder())
		c.Request = httptest.NewRequest(http.MethodPost, "/", strings.NewReader(body))
		c.Request.Header.Set("Content-Type", "application/json")
		var req authRequest
		return c.ShouldBindJSON(&req)
	}
	if err := bind(strings.Repeat("a", 72)); err != nil {
		t.Fatalf("72-byte password rejected: %v", err)
	}
	if err := bind(strings.Repeat("a", 73)); err == nil {
		t.Fatal("73-byte password accepted")
	}
}

func TestPasswordTooLongCountsBytes(t *testing.T) {
	if passwordTooLong(strings.Repeat("a", 72)) {
		t.Error("72 ASCII bytes should be allowed")
	}
	// 40 Cyrillic runes = 80 bytes: passes the rune-based validator, fails bcrypt.
	if !passwordTooLong(strings.Repeat("ж", 40)) {
		t.Error("80-byte password should be rejected")
	}
}
