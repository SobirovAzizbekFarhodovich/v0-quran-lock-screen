package httpapi

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/gin-gonic/gin"

	"islamic-ai/backend/api-gateway/internal/auth"
)

const testUserID = "11111111-2222-3333-4444-555555555555"

var testNow = time.Date(2026, 10, 13, 12, 0, 0, 0, time.UTC)

// fakeRecitationStore records calls instead of talking to Postgres.
type fakeRecitationStore struct {
	userID       string
	inserted     []ayahResult
	insertResult int64
	err          error

	listSince time.Time
	listLimit int
	listRows  []ayahResult
	listMore  bool

	deleted int64

	reportCount int
	reports     []recitationReport
	reportErr   error
}

func (f *fakeRecitationStore) InsertAyahResults(_ context.Context, userID string, rows []ayahResult) (int64, error) {
	f.userID, f.inserted = userID, rows
	return f.insertResult, f.err
}

func (f *fakeRecitationStore) ListAyahResults(_ context.Context, userID string, since time.Time, limit int) ([]ayahResult, bool, error) {
	f.userID, f.listSince, f.listLimit = userID, since, limit
	return f.listRows, f.listMore, f.err
}

func (f *fakeRecitationStore) DeleteAyahResults(_ context.Context, userID string) (int64, error) {
	f.userID = userID
	return f.deleted, f.err
}

func (f *fakeRecitationStore) CountReportsSince(_ context.Context, userID string, _ time.Time) (int, error) {
	f.userID = userID
	return f.reportCount, f.err
}

func (f *fakeRecitationStore) InsertReport(_ context.Context, r recitationReport) error {
	if f.reportErr != nil {
		return f.reportErr
	}
	f.reports = append(f.reports, r)
	return nil
}

// withUser stands in for auth.Middleware.
func withUser(c *gin.Context) {
	c.Set(auth.UserIDKey, testUserID)
	c.Next()
}

func newResultsRouter(store *fakeRecitationStore) *gin.Engine {
	gin.SetMode(gin.TestMode)
	h := &RecitationResultsHandler{store: store, now: func() time.Time { return testNow }}
	r := gin.New()
	g := r.Group("/api/v1", withUser)
	g.POST("/recitation/results", h.Upload)
	g.GET("/recitation/results", h.List)
	g.DELETE("/recitation/results", h.Delete)
	return r
}

func validResult() ayahResult {
	created := testNow.Add(-time.Hour).UnixMilli()
	return ayahResult{
		ClientID:     "6f1c2a3e-8b8d-4c55-9a43-2f0e1d9b7c10",
		Surah:        2,
		Ayah:         255,
		WordCount:    50,
		CorrectCount: 45,
		WrongCount:   3,
		MissedCount:  2,
		Mode:         "check",
		CreatedAt:    created,
		LocalDay:     created / 86_400_000,
	}
}

func TestAyahResultValidate(t *testing.T) {
	tests := []struct {
		name    string
		mutate  func(*ayahResult)
		wantErr string
	}{
		{"valid", func(*ayahResult) {}, ""},
		{"memorize mode", func(r *ayahResult) { r.Mode = "memorize" }, ""},
		{"all counts zero (nothing recognised yet)", func(r *ayahResult) { r.CorrectCount, r.WrongCount, r.MissedCount = 0, 0, 0 }, ""},
		{"counts sum exactly word_count", func(r *ayahResult) { r.CorrectCount, r.WrongCount, r.MissedCount = 50, 0, 0 }, ""},
		{"local day one ahead (UTC+14)", func(r *ayahResult) { r.LocalDay++ }, ""},
		{"local day one behind (UTC-12)", func(r *ayahResult) { r.LocalDay-- }, ""},
		{"surah 114 ayah 1", func(r *ayahResult) { r.Surah, r.Ayah = 114, 1 }, ""},
		{"ayah 286", func(r *ayahResult) { r.Ayah = 286 }, ""},
		{"short non-uuid client id", func(r *ayahResult) { r.ClientID = "local-42" }, ""},

		{"missing client id", func(r *ayahResult) { r.ClientID = "" }, "client_id is required"},
		{"client id too long", func(r *ayahResult) { r.ClientID = strings.Repeat("a", 65) }, "client_id longer"},
		{"client id with space", func(r *ayahResult) { r.ClientID = "a b" }, "printable ASCII"},
		{"client id non-ascii", func(r *ayahResult) { r.ClientID = "ayah-٢" }, "printable ASCII"},
		{"surah 0", func(r *ayahResult) { r.Surah = 0 }, "surah"},
		{"surah 115", func(r *ayahResult) { r.Surah = 115 }, "surah"},
		{"ayah 0", func(r *ayahResult) { r.Ayah = 0 }, "ayah"},
		{"ayah 287", func(r *ayahResult) { r.Ayah = 287 }, "ayah"},
		{"word count 0", func(r *ayahResult) { r.WordCount = 0; r.CorrectCount, r.WrongCount, r.MissedCount = 0, 0, 0 }, "word_count"},
		{"word count absurd", func(r *ayahResult) { r.WordCount = 5000 }, "word_count"},
		{"negative correct", func(r *ayahResult) { r.CorrectCount = -1 }, "negative"},
		{"negative wrong", func(r *ayahResult) { r.WrongCount = -1 }, "negative"},
		{"negative missed", func(r *ayahResult) { r.MissedCount = -1 }, "negative"},
		{"counts exceed word count", func(r *ayahResult) { r.CorrectCount = 46 }, "exceeds word_count"},
		{"unknown mode", func(r *ayahResult) { r.Mode = "listen" }, "mode"},
		{"empty mode", func(r *ayahResult) { r.Mode = "" }, "mode"},
		{"created_at in seconds", func(r *ayahResult) { r.CreatedAt /= 1000; r.LocalDay = r.CreatedAt / 86_400_000 }, "created_at"},
		{"created_at zero", func(r *ayahResult) { r.CreatedAt, r.LocalDay = 0, 0 }, "created_at"},
		{"created_at far future", func(r *ayahResult) {
			r.CreatedAt = testNow.Add(30 * 24 * time.Hour).UnixMilli()
			r.LocalDay = r.CreatedAt / 86_400_000
		}, "created_at"},
		{"local day far off", func(r *ayahResult) { r.LocalDay += 3 }, "local_day"},
		{"local day in ms by mistake", func(r *ayahResult) { r.LocalDay = r.CreatedAt }, "local_day"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			r := validResult()
			tt.mutate(&r)
			err := r.validate(testNow)
			if tt.wantErr == "" {
				if err != nil {
					t.Fatalf("validate() = %v, want nil", err)
				}
				return
			}
			if err == nil || !strings.Contains(err.Error(), tt.wantErr) {
				t.Fatalf("validate() = %v, want error containing %q", err, tt.wantErr)
			}
		})
	}
}

func TestParseResultsQuery(t *testing.T) {
	tests := []struct {
		since, limit string
		wantSince    int64
		wantLimit    int
		wantErr      bool
	}{
		{"", "", 0, 500, false},
		{"1760000000000", "", 1760000000000, 500, false},
		{"0", "1", 0, 1, false},
		{"5", "1000", 5, 1000, false},
		{"-1", "", 0, 0, true},
		{"abc", "", 0, 0, true},
		{"1.5", "", 0, 0, true},
		{"", "0", 0, 0, true},
		{"", "1001", 0, 0, true},
		{"", "ten", 0, 0, true},
	}
	for _, tt := range tests {
		since, limit, err := parseResultsQuery(tt.since, tt.limit)
		if (err != nil) != tt.wantErr {
			t.Fatalf("parseResultsQuery(%q, %q) error = %v, wantErr %v", tt.since, tt.limit, err, tt.wantErr)
		}
		if err == nil && (since != tt.wantSince || limit != tt.wantLimit) {
			t.Fatalf("parseResultsQuery(%q, %q) = %d, %d; want %d, %d", tt.since, tt.limit, since, limit, tt.wantSince, tt.wantLimit)
		}
	}
}

func doJSON(t *testing.T, r http.Handler, method, url string, body any) (*httptest.ResponseRecorder, map[string]any) {
	t.Helper()
	var reader *bytes.Reader
	switch b := body.(type) {
	case nil:
		reader = bytes.NewReader(nil)
	case string:
		reader = bytes.NewReader([]byte(b))
	default:
		raw, err := json.Marshal(b)
		if err != nil {
			t.Fatal(err)
		}
		reader = bytes.NewReader(raw)
	}
	req := httptest.NewRequest(method, url, reader)
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)
	var out map[string]any
	if w.Body.Len() > 0 {
		if err := json.Unmarshal(w.Body.Bytes(), &out); err != nil {
			t.Fatalf("response is not JSON: %s", w.Body.String())
		}
	}
	return w, out
}

func TestUploadStoresValidAndReportsRejected(t *testing.T) {
	store := &fakeRecitationStore{insertResult: 1}
	r := newResultsRouter(store)

	good1 := validResult()
	good2 := validResult()
	good2.ClientID = "second"
	bad := validResult()
	bad.ClientID = "bad"
	bad.Surah = 200

	w, out := doJSON(t, r, http.MethodPost, "/api/v1/recitation/results",
		map[string]any{"results": []ayahResult{good1, bad, good2}})
	if w.Code != http.StatusOK {
		t.Fatalf("status = %d, body %s", w.Code, w.Body.String())
	}
	if store.userID != testUserID {
		t.Fatalf("store got user %q", store.userID)
	}
	if len(store.inserted) != 2 || store.inserted[0].ClientID != good1.ClientID || store.inserted[1].ClientID != "second" {
		t.Fatalf("inserted = %+v", store.inserted)
	}
	if out["accepted"] != float64(2) || out["inserted"] != float64(1) || out["duplicates"] != float64(1) {
		t.Fatalf("response = %v", out)
	}
	rejected := out["rejected"].([]any)
	if len(rejected) != 1 {
		t.Fatalf("rejected = %v", rejected)
	}
	rej := rejected[0].(map[string]any)
	if rej["index"] != float64(1) || rej["client_id"] != "bad" || !strings.Contains(rej["error"].(string), "surah") {
		t.Fatalf("rejected[0] = %v", rej)
	}
}

func TestUploadRequestErrors(t *testing.T) {
	tooMany := make([]ayahResult, maxResultsPerUpload+1)
	for i := range tooMany {
		tooMany[i] = validResult()
	}
	tests := []struct {
		name string
		body any
		want int
	}{
		{"malformed json", `{"results": [`, http.StatusBadRequest},
		{"wrong type", `{"results": {"surah": 1}}`, http.StatusBadRequest},
		{"string where int expected", `{"results": [{"client_id":"a","surah":"2"}]}`, http.StatusBadRequest},
		{"missing results", `{}`, http.StatusBadRequest},
		{"empty results", map[string]any{"results": []ayahResult{}}, http.StatusBadRequest},
		{"too many results", map[string]any{"results": tooMany}, http.StatusRequestEntityTooLarge},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			store := &fakeRecitationStore{}
			w, _ := doJSON(t, newResultsRouter(store), http.MethodPost, "/api/v1/recitation/results", tt.body)
			if w.Code != tt.want {
				t.Fatalf("status = %d, want %d (%s)", w.Code, tt.want, w.Body.String())
			}
			if store.inserted != nil {
				t.Fatal("store must not be called for a rejected request")
			}
		})
	}
}

func TestUploadAllInvalidStillSucceeds(t *testing.T) {
	store := &fakeRecitationStore{}
	bad := validResult()
	bad.Mode = "nope"
	w, out := doJSON(t, newResultsRouter(store), http.MethodPost, "/api/v1/recitation/results",
		map[string]any{"results": []ayahResult{bad}})
	if w.Code != http.StatusOK || out["accepted"] != float64(0) || len(out["rejected"].([]any)) != 1 {
		t.Fatalf("status %d, body %v", w.Code, out)
	}
}

func TestUploadStoreFailure(t *testing.T) {
	store := &fakeRecitationStore{err: errors.New("db down")}
	w, _ := doJSON(t, newResultsRouter(store), http.MethodPost, "/api/v1/recitation/results",
		map[string]any{"results": []ayahResult{validResult()}})
	if w.Code != http.StatusInternalServerError {
		t.Fatalf("status = %d", w.Code)
	}
}

func TestListPagesBySince(t *testing.T) {
	row1, row2 := validResult(), validResult()
	row2.ClientID, row2.CreatedAt = "later", row1.CreatedAt+5
	store := &fakeRecitationStore{listRows: []ayahResult{row1, row2}, listMore: true}

	w, out := doJSON(t, newResultsRouter(store), http.MethodGet, "/api/v1/recitation/results?since=1760000000000&limit=2", nil)
	if w.Code != http.StatusOK {
		t.Fatalf("status = %d, body %s", w.Code, w.Body.String())
	}
	if store.userID != testUserID || store.listLimit != 2 || store.listSince.UnixMilli() != 1760000000000 {
		t.Fatalf("store called with user=%q since=%v limit=%d", store.userID, store.listSince, store.listLimit)
	}
	if out["next_since"] != float64(row2.CreatedAt) || out["has_more"] != true {
		t.Fatalf("response = %v", out)
	}
	results := out["results"].([]any)
	first := results[0].(map[string]any)
	for _, key := range []string{"client_id", "surah", "ayah", "word_count", "correct_count", "wrong_count", "missed_count", "mode", "created_at", "local_day"} {
		if _, ok := first[key]; !ok {
			t.Fatalf("result is missing %q: %v", key, first)
		}
	}
}

func TestListEmptyKeepsSince(t *testing.T) {
	store := &fakeRecitationStore{}
	w, out := doJSON(t, newResultsRouter(store), http.MethodGet, "/api/v1/recitation/results?since=42", nil)
	if w.Code != http.StatusOK {
		t.Fatalf("status = %d", w.Code)
	}
	if store.listLimit != defaultResultsPageSize {
		t.Fatalf("default limit = %d", store.listLimit)
	}
	if out["next_since"] != float64(42) || out["has_more"] != false {
		t.Fatalf("response = %v", out)
	}
	if results, ok := out["results"].([]any); !ok || len(results) != 0 {
		t.Fatalf("results must be an empty array, got %v", out["results"])
	}
}

func TestListRejectsBadQuery(t *testing.T) {
	for _, q := range []string{"since=-5", "limit=0", "limit=5000", "since=yesterday"} {
		w, _ := doJSON(t, newResultsRouter(&fakeRecitationStore{}), http.MethodGet, "/api/v1/recitation/results?"+q, nil)
		if w.Code != http.StatusBadRequest {
			t.Fatalf("%s: status = %d", q, w.Code)
		}
	}
}

func TestDeleteWipesUserHistory(t *testing.T) {
	store := &fakeRecitationStore{deleted: 7}
	w, out := doJSON(t, newResultsRouter(store), http.MethodDelete, "/api/v1/recitation/results", nil)
	if w.Code != http.StatusOK || out["deleted"] != float64(7) || store.userID != testUserID {
		t.Fatalf("status %d, body %v, user %q", w.Code, out, store.userID)
	}
}
