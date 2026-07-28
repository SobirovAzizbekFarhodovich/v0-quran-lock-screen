package httpapi

import (
	"testing"
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
