package rag

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"sync"
	"time"
)

var ErrCircuitOpen = errors.New("rag circuit open")

// StatusError is a non-2xx answer from the RAG service.
type StatusError struct {
	Code int
}

func (e *StatusError) Error() string { return fmt.Sprintf("rag bad status %d", e.Code) }

// IsClientError reports a 4xx rejection of the request itself (e.g. the
// message exceeds the RAG service's length limit).
func (e *StatusError) IsClientError() bool { return e.Code >= 400 && e.Code < 500 }

const (
	failureThreshold = 5
	openDuration     = 30 * time.Second
)

type QueryRequest struct {
	UserID   string `json:"user_id,omitempty"`
	UserName string `json:"user_name,omitempty" binding:"max=64"`
	// From the profile: lets the assistant use masculine/feminine forms of address.
	UserGender string `json:"user_gender,omitempty" binding:"omitempty,oneof=male female"`
	Message  string `json:"message" binding:"required,max=2000"`
	Language string `json:"language" binding:"max=16"`
}

type QueryResponse struct {
	Answer  string        `json:"answer"`
	Sources []interface{} `json:"sources"`
	Cached  bool          `json:"cached"`
}

type Client struct {
	baseURL string
	http    *http.Client
	mu      sync.Mutex
	fails   int
	opened  time.Time
}

func NewClient(baseURL string, timeout time.Duration) *Client {
	return &Client{baseURL: baseURL, http: &http.Client{Timeout: timeout}}
}

func (c *Client) Query(ctx context.Context, req QueryRequest) (QueryResponse, error) {
	c.mu.Lock()
	if c.fails >= failureThreshold && time.Since(c.opened) < openDuration {
		c.mu.Unlock()
		return QueryResponse{}, ErrCircuitOpen
	}
	c.mu.Unlock()

	body, _ := json.Marshal(req)
	httpReq, err := http.NewRequestWithContext(ctx, http.MethodPost, c.baseURL+"/query", bytes.NewReader(body))
	if err != nil {
		return QueryResponse{}, err
	}
	httpReq.Header.Set("Content-Type", "application/json")
	resp, err := c.http.Do(httpReq)
	if err != nil {
		// A caller that gave up (client disconnected / its own deadline) says
		// nothing about the RAG service's health; don't trip the breaker.
		if ctx.Err() == nil {
			c.recordFailure()
		}
		return QueryResponse{}, err
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		statusErr := &StatusError{Code: resp.StatusCode}
		// A 4xx is a bad request from one user (too long, blank...), not an
		// unhealthy service: it must not open the breaker for everyone.
		if !statusErr.IsClientError() {
			c.recordFailure()
		}
		return QueryResponse{}, statusErr
	}
	c.recordSuccess()

	var result QueryResponse
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return QueryResponse{}, err
	}
	return result, nil
}

func (c *Client) recordFailure() {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.fails++
	// >= rather than ==: after the open window elapses a half-open probe that
	// fails again must re-open the circuit (fails is already past 5 by then).
	if c.fails >= failureThreshold {
		c.opened = time.Now()
	}
}

func (c *Client) recordSuccess() {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.fails = 0
	c.opened = time.Time{}
}
