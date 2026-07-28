package rag

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"sync"
	"time"
)

var ErrCircuitOpen = errors.New("rag circuit open")

type QueryRequest struct {
	UserID   string `json:"user_id,omitempty"`
	Message  string `json:"message" binding:"required"`
	Language string `json:"language"`
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
	if c.fails >= 5 && time.Since(c.opened) < 30*time.Second {
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
		c.recordFailure()
		return QueryResponse{}, err
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		c.recordFailure()
		return QueryResponse{}, errors.New("rag bad status")
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
	if c.fails == 5 {
		c.opened = time.Now()
	}
}

func (c *Client) recordSuccess() {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.fails = 0
	c.opened = time.Time{}
}
