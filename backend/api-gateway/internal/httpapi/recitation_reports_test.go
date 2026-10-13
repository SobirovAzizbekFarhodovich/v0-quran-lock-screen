package httpapi

import (
	"bytes"
	"encoding/json"
	"errors"
	"io"
	"mime/multipart"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/gin-gonic/gin"
)

func TestReportMetaValidate(t *testing.T) {
	valid := func() recitationReportMeta {
		return recitationReportMeta{
			Surah: 1, Ayah: 2,
			ExpectedText: "الحمد لله رب العالمين",
			Transcript:   "الحمد لله رب",
			Alignment:    "ok ok ok missed",
			AppVersion:   "1.4.0 (42)",
			Note:         "oxirgi so'z to'g'ri o'qilgan edi",
		}
	}
	tests := []struct {
		name    string
		mutate  func(*recitationReportMeta)
		wantErr string
	}{
		{"valid", func(*recitationReportMeta) {}, ""},
		{"ayah 0 means several/auto", func(m *recitationReportMeta) { m.Ayah = 0 }, ""},
		{"optional fields empty", func(m *recitationReportMeta) {
			m.ExpectedText, m.Transcript, m.Alignment, m.AppVersion, m.Note = "", "", "", "", ""
		}, ""},
		{"note of 500 cyrillic characters", func(m *recitationReportMeta) { m.Note = strings.Repeat("ж", 500) }, ""},
		{"surah 0", func(m *recitationReportMeta) { m.Surah = 0 }, "surah"},
		{"surah 115", func(m *recitationReportMeta) { m.Surah = 115 }, "surah"},
		{"negative ayah", func(m *recitationReportMeta) { m.Ayah = -1 }, "ayah"},
		{"ayah 287", func(m *recitationReportMeta) { m.Ayah = 287 }, "ayah"},
		{"note too long", func(m *recitationReportMeta) { m.Note = strings.Repeat("a", 501) }, "note"},
		{"app version too long", func(m *recitationReportMeta) { m.AppVersion = strings.Repeat("1", 65) }, "app_version"},
		{"transcript too long", func(m *recitationReportMeta) { m.Transcript = strings.Repeat("a", maxReportTextBytes+1) }, "transcript"},
		{"NUL in note", func(m *recitationReportMeta) { m.Note = "a\x00b" }, "NUL"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			m := valid()
			tt.mutate(&m)
			err := m.validate()
			if tt.wantErr == "" {
				if err != nil {
					t.Fatalf("validate() = %v", err)
				}
				return
			}
			if err == nil || !strings.Contains(err.Error(), tt.wantErr) {
				t.Fatalf("validate() = %v, want error containing %q", err, tt.wantErr)
			}
		})
	}
}

func TestParseReportMetaRejectsNonObject(t *testing.T) {
	for _, raw := range []string{"", "[]", `"x"`, `{"surah": "one"}`} {
		if _, err := parseReportMeta([]byte(raw)); err == nil {
			t.Fatalf("parseReportMeta(%q) accepted", raw)
		}
	}
}

type reportsEnv struct {
	store  *fakeRecitationStore
	dir    string
	router *gin.Engine
	free   uint64
}

func newReportsEnv(t *testing.T) *reportsEnv {
	t.Helper()
	gin.SetMode(gin.TestMode)
	env := &reportsEnv{store: &fakeRecitationStore{}, dir: filepath.Join(t.TempDir(), "reports"), free: 100 << 30}
	h := &RecitationReportsHandler{
		store:     env.store,
		cfg:       RecitationReportsConfig{Dir: env.dir, DailyLimit: 20, MinFreeMB: 1024},
		now:       func() time.Time { return testNow },
		freeBytes: func(string) (uint64, bool) { return env.free, true },
	}
	env.router = gin.New()
	env.router.POST(RecitationReportsPath, withUser, h.Create)
	return env
}

const validMetaJSON = `{"surah":1,"ayah":0,"expected_text":"بسم الله","transcript":"بسم","alignment":"ok missed","app_version":"1.4.0","note":"test"}`

// multipartBody builds a request body; audio == nil omits the audio part, meta == ""
// omits the meta part.
func multipartBody(t *testing.T, meta string, audio []byte) (*bytes.Buffer, string) {
	t.Helper()
	var buf bytes.Buffer
	mw := multipart.NewWriter(&buf)
	if meta != "" {
		if err := mw.WriteField("meta", meta); err != nil {
			t.Fatal(err)
		}
	}
	if audio != nil {
		fw, err := mw.CreateFormFile("audio", "recording.pcm")
		if err != nil {
			t.Fatal(err)
		}
		fw.Write(audio)
	}
	mw.Close()
	return &buf, mw.FormDataContentType()
}

func (e *reportsEnv) post(body io.Reader, contentType string) *httptest.ResponseRecorder {
	req := httptest.NewRequest(http.MethodPost, RecitationReportsPath, body)
	req.Header.Set("Content-Type", contentType)
	w := httptest.NewRecorder()
	e.router.ServeHTTP(w, req)
	return w
}

// dirEntries lists files in the reports dir (including leftover temp files).
func (e *reportsEnv) dirEntries(t *testing.T) []string {
	t.Helper()
	entries, err := os.ReadDir(e.dir)
	if errors.Is(err, os.ErrNotExist) {
		return nil
	}
	if err != nil {
		t.Fatal(err)
	}
	names := make([]string, 0, len(entries))
	for _, entry := range entries {
		names = append(names, entry.Name())
	}
	return names
}

func TestCreateReportStoresAudioAndRow(t *testing.T) {
	env := newReportsEnv(t)
	audio := bytes.Repeat([]byte{0x01, 0x02}, 16000) // 1 s of PCM
	// Audio first, meta last: the handler must cope with either part order.
	var body bytes.Buffer
	mw := multipart.NewWriter(&body)
	fw, _ := mw.CreateFormFile("audio", "a.pcm")
	fw.Write(audio)
	mw.WriteField("meta", validMetaJSON)
	mw.Close()

	w := env.post(&body, mw.FormDataContentType())
	if w.Code != http.StatusCreated {
		t.Fatalf("status = %d, body %s", w.Code, w.Body.String())
	}
	var out struct{ ID string }
	json.Unmarshal(w.Body.Bytes(), &out)
	if len(env.store.reports) != 1 {
		t.Fatalf("reports stored = %d", len(env.store.reports))
	}
	rep := env.store.reports[0]
	if rep.ID != out.ID || rep.UserID != testUserID || rep.AudioBytes != int64(len(audio)) {
		t.Fatalf("report = %+v, response id %q", rep, out.ID)
	}
	if rep.Meta.Surah != 1 || rep.Meta.Ayah != 0 || rep.Meta.ExpectedText != "بسم الله" || rep.Meta.Note != "test" {
		t.Fatalf("meta = %+v", rep.Meta)
	}
	if rep.AudioPath != filepath.Join(env.dir, out.ID+".pcm") {
		t.Fatalf("audio path = %q", rep.AudioPath)
	}
	saved, err := os.ReadFile(rep.AudioPath)
	if err != nil || !bytes.Equal(saved, audio) {
		t.Fatalf("saved audio mismatch (err %v, %d bytes)", err, len(saved))
	}
	if names := env.dirEntries(t); len(names) != 1 {
		t.Fatalf("reports dir holds %v, want only the .pcm file", names)
	}
}

func TestCreateReportClientErrors(t *testing.T) {
	pcm := []byte{0, 0, 1, 1}
	tests := []struct {
		name  string
		meta  string
		audio []byte
		want  int
	}{
		{"missing audio", validMetaJSON, nil, http.StatusBadRequest},
		{"missing meta", "", pcm, http.StatusBadRequest},
		{"empty audio", validMetaJSON, []byte{}, http.StatusBadRequest},
		{"odd byte count", validMetaJSON, []byte{1, 2, 3}, http.StatusBadRequest},
		{"meta not json", "surah=1", pcm, http.StatusBadRequest},
		{"meta invalid surah", `{"surah":0}`, pcm, http.StatusBadRequest},
		{"meta note too long", `{"surah":1,"note":"` + strings.Repeat("x", 501) + `"}`, pcm, http.StatusBadRequest},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			env := newReportsEnv(t)
			body, ct := multipartBody(t, tt.meta, tt.audio)
			w := env.post(body, ct)
			if w.Code != tt.want {
				t.Fatalf("status = %d, want %d (%s)", w.Code, tt.want, w.Body.String())
			}
			if len(env.store.reports) != 0 {
				t.Fatal("no report row may be stored")
			}
			if names := env.dirEntries(t); len(names) != 0 {
				t.Fatalf("leftover files %v", names)
			}
		})
	}
}

func TestCreateReportRejectsNonMultipart(t *testing.T) {
	env := newReportsEnv(t)
	w := env.post(strings.NewReader(validMetaJSON), "application/json")
	if w.Code != http.StatusBadRequest {
		t.Fatalf("status = %d", w.Code)
	}
}

func TestCreateReportRejectsOversizedAudio(t *testing.T) {
	env := newReportsEnv(t)
	// Stream just over 15 minutes of PCM without holding it in memory, with no
	// Content-Length, so the handler's streaming limit (not the header check) trips.
	pr, pw := io.Pipe()
	mw := multipart.NewWriter(pw)
	go func() {
		mw.WriteField("meta", validMetaJSON)
		fw, _ := mw.CreateFormFile("audio", "long.pcm")
		chunk := make([]byte, 64<<10)
		var written int64
		for written <= MaxRecitationReportAudioBytes {
			n, err := fw.Write(chunk)
			if err != nil {
				pw.CloseWithError(err)
				return
			}
			written += int64(n)
		}
		mw.Close()
		pw.Close()
	}()
	w := env.post(pr, mw.FormDataContentType())
	pr.CloseWithError(errors.New("handler finished")) // unblock the writer goroutine
	if w.Code != http.StatusRequestEntityTooLarge {
		t.Fatalf("status = %d, body %s", w.Code, w.Body.String())
	}
	if names := env.dirEntries(t); len(names) != 0 {
		t.Fatalf("leftover files %v", names)
	}
}

func TestCreateReportRejectsLargeContentLength(t *testing.T) {
	env := newReportsEnv(t)
	req := httptest.NewRequest(http.MethodPost, RecitationReportsPath, strings.NewReader("x"))
	req.Header.Set("Content-Type", "multipart/form-data; boundary=x")
	req.ContentLength = MaxRecitationReportBodyBytes + 1
	w := httptest.NewRecorder()
	env.router.ServeHTTP(w, req)
	if w.Code != http.StatusRequestEntityTooLarge {
		t.Fatalf("status = %d", w.Code)
	}
}

func TestCreateReportDailyLimit(t *testing.T) {
	env := newReportsEnv(t)
	env.store.reportCount = 20
	body, ct := multipartBody(t, validMetaJSON, []byte{0, 0})
	w := env.post(body, ct)
	if w.Code != http.StatusTooManyRequests {
		t.Fatalf("status = %d", w.Code)
	}
	if env.store.userID != testUserID {
		t.Fatalf("limit checked for user %q", env.store.userID)
	}
}

func TestCreateReportLowDisk(t *testing.T) {
	env := newReportsEnv(t)
	env.free = 10 << 20
	body, ct := multipartBody(t, validMetaJSON, []byte{0, 0})
	if w := env.post(body, ct); w.Code != http.StatusInsufficientStorage {
		t.Fatalf("status = %d", w.Code)
	}
}

func TestCreateReportStoreFailureRemovesAudio(t *testing.T) {
	env := newReportsEnv(t)
	env.store.reportErr = errors.New("db down")
	body, ct := multipartBody(t, validMetaJSON, []byte{0, 0, 0, 0})
	if w := env.post(body, ct); w.Code != http.StatusInternalServerError {
		t.Fatalf("status = %d", w.Code)
	}
	if names := env.dirEntries(t); len(names) != 0 {
		t.Fatalf("leftover files %v", names)
	}
}
