// Package health implements the gateway's deep health check: unlike the
// cheap /healthz liveness probe it asks every dependency whether it can do
// real work (Postgres, Redis, the RAG service and the recitation model).
package health

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log"
	"net/http"
	"sync"
	"time"

	"github.com/gin-gonic/gin"
)

const (
	StatusOK       = "ok"
	StatusDegraded = "degraded"
)

// Result is the outcome of one component check. Error is a short, public
// reason; the full error is only logged, so internal addresses never leak
// through the public endpoint.
type Result struct {
	Status    string         `json:"status"`
	LatencyMS int64          `json:"latency_ms"`
	Error     string         `json:"error,omitempty"`
	Detail    map[string]any `json:"detail,omitempty"`
}

// Check probes one dependency. It must honour ctx's deadline.
type Check struct {
	Name    string
	Timeout time.Duration
	Run     func(ctx context.Context) (detail map[string]any, err error)
}

// Report is the body of the deep health response.
type Report struct {
	Status     string            `json:"status"`
	CheckedAt  time.Time         `json:"checked_at"`
	Components map[string]Result `json:"components"`
}

// publicError marks an error whose message is safe to return to clients.
type publicError struct{ msg string }

func (e publicError) Error() string { return e.msg }

// Degradedf returns an error whose message is shown in the response as is.
func Degradedf(format string, args ...any) error {
	return publicError{msg: fmt.Sprintf(format, args...)}
}

// Checker runs all checks concurrently and caches the report briefly so a
// burst of requests cannot hammer the dependencies.
type Checker struct {
	checks   []Check
	cacheTTL time.Duration
	now      func() time.Time

	mu       sync.Mutex
	cached   *Report
	cachedAt time.Time
}

func NewChecker(cacheTTL time.Duration, checks ...Check) *Checker {
	return &Checker{checks: checks, cacheTTL: cacheTTL, now: time.Now}
}

// Run executes every check (or returns a fresh-enough cached report).
func (c *Checker) Run(ctx context.Context) Report {
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.cached != nil && c.cacheTTL > 0 && c.now().Sub(c.cachedAt) < c.cacheTTL {
		return *c.cached
	}

	results := make(map[string]Result, len(c.checks))
	var resultsMu sync.Mutex
	var wg sync.WaitGroup
	for _, check := range c.checks {
		wg.Add(1)
		go func(check Check) {
			defer wg.Done()
			res := runOne(ctx, check)
			resultsMu.Lock()
			results[check.Name] = res
			resultsMu.Unlock()
		}(check)
	}
	wg.Wait()

	report := Report{Status: StatusOK, CheckedAt: c.now().UTC(), Components: results}
	for _, res := range results {
		if res.Status != StatusOK {
			report.Status = StatusDegraded
		}
	}
	c.cached = &report
	c.cachedAt = c.now()
	return report
}

func runOne(parent context.Context, check Check) (res Result) {
	timeout := check.Timeout
	if timeout <= 0 {
		timeout = 3 * time.Second
	}
	ctx, cancel := context.WithTimeout(parent, timeout)
	defer cancel()

	start := time.Now()
	defer func() {
		if r := recover(); r != nil {
			log.Printf("health: %s check panicked: %v", check.Name, r)
			res = Result{Status: StatusDegraded, Error: "check failed"}
		}
		res.LatencyMS = time.Since(start).Milliseconds()
	}()

	type outcome struct {
		detail map[string]any
		err    error
	}
	done := make(chan outcome, 1)
	go func() {
		detail, err := check.Run(ctx)
		done <- outcome{detail, err}
	}()

	var out outcome
	select {
	case out = <-done:
	case <-ctx.Done():
		// A client that ignores ctx must not stall the whole report.
		out = outcome{err: ctx.Err()}
	}
	if out.err == nil {
		return Result{Status: StatusOK, Detail: out.detail}
	}
	log.Printf("health: %s degraded: %v", check.Name, out.err)
	return Result{Status: StatusDegraded, Error: publicReason(out.err), Detail: out.detail}
}

func publicReason(err error) string {
	var pub publicError
	switch {
	case errors.As(err, &pub):
		return pub.msg
	case errors.Is(err, context.DeadlineExceeded):
		return "timeout"
	default:
		return "unreachable"
	}
}

// Handler serves the report: 200 when every component is ok, 503 otherwise.
func (c *Checker) Handler(ctx *gin.Context) {
	// A client hanging up must not cache a "timeout" report for everyone.
	report := c.Run(context.WithoutCancel(ctx.Request.Context()))
	code := http.StatusOK
	if report.Status != StatusOK {
		code = http.StatusServiceUnavailable
	}
	ctx.Header("Cache-Control", "no-store")
	ctx.JSON(code, report)
}

// PingCheck wraps a Ping-style function (pgxpool.Pool.Ping, redis Ping).
func PingCheck(name string, timeout time.Duration, ping func(ctx context.Context) error) Check {
	return Check{Name: name, Timeout: timeout, Run: func(ctx context.Context) (map[string]any, error) {
		return nil, ping(ctx)
	}}
}

// JSONValidator inspects a decoded health body and returns public detail
// fields plus an error (use Degradedf) when the service is not usable.
type JSONValidator func(body map[string]any) (map[string]any, error)

// HTTPCheck GETs url and requires a 2xx JSON answer accepted by validate.
func HTTPCheck(name, url string, timeout time.Duration, client *http.Client, validate JSONValidator) Check {
	if client == nil {
		client = &http.Client{}
	}
	return Check{Name: name, Timeout: timeout, Run: func(ctx context.Context) (map[string]any, error) {
		req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
		if err != nil {
			return nil, err
		}
		resp, err := client.Do(req)
		if err != nil {
			return nil, err
		}
		defer resp.Body.Close()
		raw, err := io.ReadAll(io.LimitReader(resp.Body, 1<<20))
		if err != nil {
			return nil, err
		}
		var body map[string]any
		jsonErr := json.Unmarshal(raw, &body)
		var detail map[string]any
		var verr error
		if jsonErr == nil && validate != nil {
			detail, verr = validate(body)
		}
		if resp.StatusCode < 200 || resp.StatusCode > 299 {
			// Prefer the service's own explanation (e.g. "model not loaded").
			if verr != nil {
				return detail, verr
			}
			return detail, Degradedf("http %d", resp.StatusCode)
		}
		if jsonErr != nil {
			return nil, Degradedf("invalid json")
		}
		return detail, verr
	}}
}

// StatusOKValidator accepts bodies whose "status" field is "ok" and copies
// the listed keys (when present) into the public detail.
func StatusOKValidator(detailKeys ...string) JSONValidator {
	return func(body map[string]any) (map[string]any, error) {
		detail := pick(body, detailKeys)
		if status, _ := body["status"].(string); status != StatusOK {
			if status == "" {
				status = "missing"
			}
			return detail, Degradedf("status %s", status)
		}
		return detail, nil
	}
}

// RecitationValidator requires the speech model to be loaded: without it
// every recitation comes back empty even though the service answers.
func RecitationValidator(body map[string]any) (map[string]any, error) {
	detail := pick(body, []string{"model_loaded", "model_name"})
	if loaded, _ := body["model_loaded"].(bool); !loaded {
		return detail, Degradedf("model not loaded")
	}
	if status, _ := body["status"].(string); status != StatusOK {
		return detail, Degradedf("status %s", status)
	}
	return detail, nil
}

func pick(body map[string]any, keys []string) map[string]any {
	var out map[string]any
	for _, key := range keys {
		if v, ok := body[key]; ok {
			if out == nil {
				out = make(map[string]any, len(keys))
			}
			out[key] = v
		}
	}
	return out
}
