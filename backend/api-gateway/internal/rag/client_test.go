package rag

import (
	"context"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"
	"time"
)

func TestCircuitReopensAfterHalfOpenFailure(t *testing.T) {
	var hits int32
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		atomic.AddInt32(&hits, 1)
		w.WriteHeader(http.StatusInternalServerError)
	}))
	defer srv.Close()

	c := NewClient(srv.URL, 5*time.Second)
	for i := 0; i < failureThreshold; i++ {
		if _, err := c.Query(context.Background(), QueryRequest{Message: "x"}); err == nil {
			t.Fatal("expected error")
		}
	}
	if _, err := c.Query(context.Background(), QueryRequest{Message: "x"}); !errors.Is(err, ErrCircuitOpen) {
		t.Fatalf("expected circuit open, got %v", err)
	}

	// Simulate the open window elapsing: the half-open probe fails and the
	// circuit must open again (previously fails==6 never re-armed it).
	c.mu.Lock()
	c.opened = time.Now().Add(-2 * openDuration)
	c.mu.Unlock()
	if _, err := c.Query(context.Background(), QueryRequest{Message: "x"}); err == nil || errors.Is(err, ErrCircuitOpen) {
		t.Fatalf("expected a real probe failure, got %v", err)
	}
	if _, err := c.Query(context.Background(), QueryRequest{Message: "x"}); !errors.Is(err, ErrCircuitOpen) {
		t.Fatalf("expected circuit to re-open, got %v", err)
	}
	if got := atomic.LoadInt32(&hits); got != int32(failureThreshold+1) {
		t.Fatalf("upstream hits = %d, want %d", got, failureThreshold+1)
	}
}

func TestCanceledContextDoesNotTripBreaker(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// Drain the body so net/http starts watching for client disconnects.
		_, _ = io.Copy(io.Discard, r.Body)
		select {
		case <-r.Context().Done():
		case <-time.After(2 * time.Second):
		}
	}))
	defer srv.Close()

	c := NewClient(srv.URL, 5*time.Second)
	for i := 0; i < failureThreshold+2; i++ {
		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Millisecond)
		_, _ = c.Query(ctx, QueryRequest{Message: "x"})
		cancel()
	}
	c.mu.Lock()
	fails := c.fails
	c.mu.Unlock()
	if fails != 0 {
		t.Fatalf("fails = %d, want 0 for caller cancellations", fails)
	}
}

func TestClientErrorsDoNotTripBreaker(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusUnprocessableEntity)
	}))
	defer srv.Close()

	c := NewClient(srv.URL, 5*time.Second)
	for i := 0; i < failureThreshold+2; i++ {
		_, err := c.Query(context.Background(), QueryRequest{Message: "x"})
		var statusErr *StatusError
		if !errors.As(err, &statusErr) || !statusErr.IsClientError() {
			t.Fatalf("expected 4xx StatusError, got %v", err)
		}
	}
}
