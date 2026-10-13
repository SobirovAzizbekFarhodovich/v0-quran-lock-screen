package httpapi

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/gin-gonic/gin"

	"islamic-ai/backend/api-gateway/internal/rag"
)

type fakeRAG struct {
	calls int
	err   error
	resp  rag.QueryResponse
	last  rag.QueryRequest
}

func (f *fakeRAG) Query(_ context.Context, req rag.QueryRequest) (rag.QueryResponse, error) {
	f.calls++
	f.last = req
	return f.resp, f.err
}

type fakeStore struct {
	values map[string]string
	ints   map[string]int64
	gaps   map[string]bool
}

func newFakeStore() *fakeStore {
	return &fakeStore{values: map[string]string{}, ints: map[string]int64{}, gaps: map[string]bool{}}
}

func (s *fakeStore) Get(_ context.Context, key string) (string, error) {
	if v, ok := s.values[key]; ok {
		return v, nil
	}
	if n, ok := s.ints[key]; ok {
		return itoa(n), nil
	}
	return "", errors.New(errNotFound)
}
func (s *fakeStore) Set(_ context.Context, key, value string, _ time.Duration) error {
	s.values[key] = value
	return nil
}
func (s *fakeStore) SetNX(_ context.Context, key string, _ time.Duration) (bool, error) {
	if s.gaps[key] {
		return false, nil
	}
	s.gaps[key] = true
	return true, nil
}
func (s *fakeStore) TTL(context.Context, string) (time.Duration, error) { return 9 * time.Second, nil }
func (s *fakeStore) Incr(_ context.Context, key string, _ time.Duration) (int64, error) {
	s.ints[key]++
	return s.ints[key], nil
}
func (s *fakeStore) Decr(_ context.Context, key string) error {
	s.ints[key]--
	return nil
}

func itoa(n int64) string { b, _ := json.Marshal(n); return string(b) }

func newAskRouter(h *PublicAskHandler) *gin.Engine {
	gin.SetMode(gin.TestMode)
	r := gin.New()
	r.POST("/ask", h.Ask)
	return r
}

func ask(r *gin.Engine, body string) (int, map[string]any) {
	w := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodPost, "/ask", strings.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.RemoteAddr = "203.0.113.7:1234"
	r.ServeHTTP(w, req)
	var out map[string]any
	_ = json.Unmarshal(w.Body.Bytes(), &out)
	return w.Code, out
}

func testHandler(f *fakeRAG, s *fakeStore) *PublicAskHandler {
	return &PublicAskHandler{client: f, store: s, daily: 3, gap: 15 * time.Second, cacheTTL: time.Hour, now: time.Now}
}

func TestPublicAskAnswersAndCountsBudget(t *testing.T) {
	f := &fakeRAG{resp: rag.QueryResponse{Answer: " Sabr haqida… ", Sources: []interface{}{map[string]any{"title": "Ahqof 35"}}}}
	s := newFakeStore()
	r := newAskRouter(testHandler(f, s))
	code, out := ask(r, `{"question":"  Sabr   haqida? ","language":"uzc"}`)
	if code != http.StatusOK || out["answer"] != "Sabr haqida…" || out["remaining"].(float64) != 2 {
		t.Fatalf("got %d %v", code, out)
	}
	if f.last.Message != "Sabr haqida?" || f.last.Language != "uz_cyrillic" || f.last.UserID != "" {
		t.Fatalf("rag request %+v", f.last)
	}
}

func TestPublicAskCacheIsFreeAndSkipsLimits(t *testing.T) {
	f := &fakeRAG{resp: rag.QueryResponse{Answer: "A", Sources: []interface{}{"s"}}}
	s := newFakeStore()
	r := newAskRouter(testHandler(f, s))
	ask(r, `{"question":"Sabr haqida?","language":"uz"}`)
	// Same question again right away: served from cache, no gap error, no new LLM call.
	code, out := ask(r, `{"question":"sabr  HAQIDA?","language":"uz"}`)
	if code != http.StatusOK || out["cached"] != true || f.calls != 1 || out["remaining"].(float64) != 2 {
		t.Fatalf("got %d %v calls=%d", code, out, f.calls)
	}
}

func TestPublicAskGapAndDailyLimit(t *testing.T) {
	f := &fakeRAG{resp: rag.QueryResponse{Answer: "A", Sources: []interface{}{"s"}}}
	s := newFakeStore()
	h := testHandler(f, s)
	r := newAskRouter(h)
	ask(r, `{"question":"q1"}`)
	if code, out := ask(r, `{"question":"q2"}`); code != http.StatusTooManyRequests || out["error"] != "too_fast" {
		t.Fatalf("expected too_fast, got %d %v", code, out)
	}
	for _, q := range []string{"q2", "q3"} {
		s.gaps = map[string]bool{}
		if code, _ := ask(r, `{"question":"`+q+`"}`); code != http.StatusOK {
			t.Fatalf("%s: %d", q, code)
		}
	}
	s.gaps = map[string]bool{}
	if code, out := ask(r, `{"question":"q4"}`); code != http.StatusTooManyRequests || out["error"] != "daily_limit" {
		t.Fatalf("expected daily_limit, got %d %v", code, out)
	}
	if f.calls != 3 {
		t.Fatalf("llm calls = %d", f.calls)
	}
}

func TestPublicAskFailureRefundsBudgetAndRejectsLongQuestions(t *testing.T) {
	f := &fakeRAG{err: errors.New("down")}
	s := newFakeStore()
	r := newAskRouter(testHandler(f, s))
	if code, _ := ask(r, `{"question":"q"}`); code != http.StatusServiceUnavailable {
		t.Fatalf("code %d", code)
	}
	for k, v := range s.ints {
		if v != 0 {
			t.Fatalf("budget %s not refunded: %d", k, v)
		}
	}
	long := strings.Repeat("я", publicAskMaxRunes+1)
	if code, out := ask(r, `{"question":"`+long+`"}`); code != http.StatusBadRequest || out["error"] != "question_length" {
		t.Fatalf("got %d %v", code, out)
	}
}
