package health

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync/atomic"
	"testing"
	"time"

	"github.com/gin-gonic/gin"
)

func jsonServer(t *testing.T, code int, body string) *httptest.Server {
	t.Helper()
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(code)
		_, _ = w.Write([]byte(body))
	}))
	t.Cleanup(srv.Close)
	return srv
}

func okPing(context.Context) error { return nil }

func serve(t *testing.T, checker *Checker) (int, Report) {
	t.Helper()
	gin.SetMode(gin.TestMode)
	router := gin.New()
	router.GET("/healthz/deep", checker.Handler)
	rec := httptest.NewRecorder()
	router.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/healthz/deep", nil))
	var report Report
	if err := json.Unmarshal(rec.Body.Bytes(), &report); err != nil {
		t.Fatalf("decode %q: %v", rec.Body.String(), err)
	}
	return rec.Code, report
}

func TestAllHealthyReturns200(t *testing.T) {
	rag := jsonServer(t, 200, `{"status":"ok","documents_in_db":89666,"collections":{"a":1}}`)
	rec := jsonServer(t, 200, `{"status":"ok","model_loaded":true,"model_name":"base"}`)
	checker := NewChecker(0,
		PingCheck("postgres", time.Second, okPing),
		PingCheck("redis", time.Second, okPing),
		HTTPCheck("ai_rag", rag.URL, time.Second, nil, StatusOKValidator("documents_in_db")),
		HTTPCheck("ai_recitation", rec.URL, time.Second, nil, RecitationValidator),
	)

	code, report := serve(t, checker)
	if code != http.StatusOK || report.Status != StatusOK {
		t.Fatalf("got %d %q, want 200 ok: %+v", code, report.Status, report)
	}
	if len(report.Components) != 4 {
		t.Fatalf("components = %v", report.Components)
	}
	if got := report.Components["ai_recitation"].Detail["model_name"]; got != "base" {
		t.Errorf("recitation detail model_name = %v", got)
	}
	if _, leaked := report.Components["ai_rag"].Detail["collections"]; leaked {
		t.Errorf("rag detail should only carry whitelisted keys: %v", report.Components["ai_rag"].Detail)
	}
}

// The incident this endpoint exists for: the recitation service answered
// 200 "ok"-ish while the model was missing and every result came back empty.
func TestRecitationModelNotLoadedIsDegraded(t *testing.T) {
	for name, body := range map[string]string{
		"status ok but flag false": `{"status":"ok","model_loaded":false}`,
		"flag missing":             `{"status":"ok"}`,
		"degraded":                 `{"status":"degraded","model_loaded":false}`,
	} {
		t.Run(name, func(t *testing.T) {
			srv := jsonServer(t, 200, body)
			checker := NewChecker(0,
				PingCheck("postgres", time.Second, okPing),
				HTTPCheck("ai_recitation", srv.URL, time.Second, nil, RecitationValidator),
			)
			code, report := serve(t, checker)
			if code != http.StatusServiceUnavailable || report.Status != StatusDegraded {
				t.Fatalf("got %d %q, want 503 degraded", code, report.Status)
			}
			got := report.Components["ai_recitation"]
			if got.Status != StatusDegraded || got.Error != "model not loaded" {
				t.Errorf("recitation = %+v", got)
			}
			if report.Components["postgres"].Status != StatusOK {
				t.Errorf("postgres should stay ok: %+v", report.Components["postgres"])
			}
		})
	}
}

func TestPingFailureHidesInternalError(t *testing.T) {
	checker := NewChecker(0, PingCheck("redis", time.Second, func(context.Context) error {
		return errors.New("dial tcp 172.18.0.3:6379: connect: connection refused")
	}))
	code, report := serve(t, checker)
	if code != http.StatusServiceUnavailable {
		t.Fatalf("code = %d", code)
	}
	got := report.Components["redis"]
	if got.Error != "unreachable" || strings.Contains(got.Error, "172.18") {
		t.Errorf("redis error = %q", got.Error)
	}
}

func TestSlowCheckTimesOut(t *testing.T) {
	block := make(chan struct{})
	t.Cleanup(func() { close(block) })
	// Ignores ctx on purpose: the checker must still give up on time.
	stubborn := PingCheck("postgres", 50*time.Millisecond, func(context.Context) error {
		<-block
		return nil
	})
	start := time.Now()
	_, report := serve(t, NewChecker(0, stubborn))
	if elapsed := time.Since(start); elapsed > time.Second {
		t.Fatalf("took %v, timeout not enforced", elapsed)
	}
	if got := report.Components["postgres"]; got.Status != StatusDegraded || got.Error != "timeout" {
		t.Errorf("postgres = %+v", got)
	}
}

func TestHTTPErrorsAreDegraded(t *testing.T) {
	cases := map[string]struct {
		code int
		body string
		want string
	}{
		"5xx without json": {502, `bad gateway`, "http 502"},
		"5xx with reason":  {503, `{"status":"degraded"}`, "status degraded"},
		"200 not json":     {200, `<html>`, "invalid json"},
		"200 degraded":     {200, `{"status":"degraded"}`, "status degraded"},
	}
	for name, tc := range cases {
		t.Run(name, func(t *testing.T) {
			srv := jsonServer(t, tc.code, tc.body)
			_, report := serve(t, NewChecker(0, HTTPCheck("ai_rag", srv.URL, time.Second, nil, StatusOKValidator())))
			if got := report.Components["ai_rag"]; got.Status != StatusDegraded || got.Error != tc.want {
				t.Errorf("ai_rag = %+v, want error %q", got, tc.want)
			}
		})
	}

	t.Run("connection refused", func(t *testing.T) {
		srv := jsonServer(t, 200, `{}`)
		url := srv.URL
		srv.Close()
		_, report := serve(t, NewChecker(0, HTTPCheck("ai_rag", url, time.Second, nil, StatusOKValidator())))
		if got := report.Components["ai_rag"]; got.Error != "unreachable" {
			t.Errorf("ai_rag = %+v", got)
		}
	})
}

func TestReportIsCachedBriefly(t *testing.T) {
	var calls atomic.Int32
	checker := NewChecker(time.Minute, PingCheck("redis", time.Second, func(context.Context) error {
		calls.Add(1)
		return nil
	}))
	now := time.Unix(1000, 0)
	checker.now = func() time.Time { return now }

	checker.Run(context.Background())
	checker.Run(context.Background())
	if calls.Load() != 1 {
		t.Fatalf("calls = %d, want cached second run", calls.Load())
	}
	now = now.Add(2 * time.Minute)
	checker.Run(context.Background())
	if calls.Load() != 2 {
		t.Fatalf("calls = %d, want refresh after TTL", calls.Load())
	}
}
