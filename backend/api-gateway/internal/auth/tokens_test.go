package auth

import (
	"testing"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

func TestIssueCreatesTypedTokenPair(t *testing.T) {
	service := NewTokenService("test-secret", time.Minute, time.Hour)
	pair, err := service.Issue("user-1", "user@example.com")
	if err != nil {
		t.Fatalf("Issue() error = %v", err)
	}
	access, err := service.Parse(pair.AccessToken)
	if err != nil || access.Type != "access" || access.UserID != "user-1" {
		t.Fatalf("access claims = %#v, %v", access, err)
	}
	refresh, err := service.Parse(pair.RefreshToken)
	if err != nil || refresh.Type != "refresh" || pair.RefreshHash != HashToken(pair.RefreshToken) {
		t.Fatalf("refresh claims = %#v, %v", refresh, err)
	}
}

func TestParseRejectsNonHMACToken(t *testing.T) {
	service := NewTokenService("test-secret", time.Minute, time.Hour)
	unsigned := jwt.NewWithClaims(jwt.SigningMethodNone, &Claims{})
	token, err := unsigned.SignedString(jwt.UnsafeAllowNoneSignatureType)
	if err != nil {
		t.Fatalf("SignedString() error = %v", err)
	}
	if _, err := service.Parse(token); err == nil {
		t.Fatal("Parse() accepted alg=none token")
	}
}
