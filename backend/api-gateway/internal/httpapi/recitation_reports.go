package httpapi

import (
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"
	"unicode/utf8"

	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgxpool"

	"islamic-ai/backend/api-gateway/internal/auth"
)

const (
	// RecitationReportsPath is the route of the report upload; the global request body
	// limit exempts it because it carries up to MaxRecitationReportBodyBytes.
	RecitationReportsPath = "/api/v1/recitation/reports"

	// MaxRecitationReportAudioBytes is 15 minutes of 16 kHz mono s16le PCM.
	MaxRecitationReportAudioBytes = 15 * 60 * 16000 * 2 // 28,800,000
	maxRecitationReportMetaBytes  = 1 << 20
	// MaxRecitationReportBodyBytes leaves room for the meta part and multipart framing.
	MaxRecitationReportBodyBytes = MaxRecitationReportAudioBytes + maxRecitationReportMetaBytes + 64<<10

	maxReportTextBytes       = 200_000 // a full Al-Baqarah expected text is well below this
	maxReportAppVersionBytes = 64
	maxReportNoteRunes       = 500
)

// RecitationReportsConfig controls where report audio goes and how much a user may send.
type RecitationReportsConfig struct {
	Dir        string // RECITATION_REPORTS_DIR
	DailyLimit int    // reports per user per rolling 24 hours
	MinFreeMB  int64  // refuse uploads when the reports filesystem has less free space
}

type recitationReportMeta struct {
	Surah        int    `json:"surah"`
	Ayah         int    `json:"ayah"` // 0 = several ayahs / auto-detected
	ExpectedText string `json:"expected_text"`
	Transcript   string `json:"transcript"`
	Alignment    string `json:"alignment"`
	AppVersion   string `json:"app_version"`
	Note         string `json:"note"`
}

func (m recitationReportMeta) validate() error {
	if m.Surah < 1 || m.Surah > 114 {
		return errors.New("surah must be between 1 and 114")
	}
	if m.Ayah < 0 || m.Ayah > 286 {
		return errors.New("ayah must be between 0 and 286")
	}
	for name, value := range map[string]string{
		"expected_text": m.ExpectedText,
		"transcript":    m.Transcript,
		"alignment":     m.Alignment,
	} {
		if len(value) > maxReportTextBytes {
			return fmt.Errorf("%s longer than %d bytes", name, maxReportTextBytes)
		}
	}
	if len(m.AppVersion) > maxReportAppVersionBytes {
		return fmt.Errorf("app_version longer than %d bytes", maxReportAppVersionBytes)
	}
	if utf8.RuneCountInString(m.Note) > maxReportNoteRunes {
		return fmt.Errorf("note longer than %d characters", maxReportNoteRunes)
	}
	for _, value := range []string{m.ExpectedText, m.Transcript, m.Alignment, m.AppVersion, m.Note} {
		if strings.ContainsRune(value, 0) {
			return errors.New("text fields must not contain NUL characters")
		}
	}
	return nil
}

func parseReportMeta(raw []byte) (recitationReportMeta, error) {
	var meta recitationReportMeta
	if err := json.Unmarshal(raw, &meta); err != nil {
		return meta, errors.New("meta must be a JSON object")
	}
	return meta, meta.validate()
}

type recitationReport struct {
	ID         string
	UserID     string
	Meta       recitationReportMeta
	AudioPath  string
	AudioBytes int64
}

type RecitationReportsHandler struct {
	store     recitationStore
	cfg       RecitationReportsConfig
	now       func() time.Time
	freeBytes func(dir string) (uint64, bool)
}

func NewRecitationReportsHandler(db *pgxpool.Pool, cfg RecitationReportsConfig) *RecitationReportsHandler {
	return &RecitationReportsHandler{store: newPGRecitationStore(db), cfg: cfg, now: time.Now, freeBytes: diskFreeBytes}
}

type uploadError struct {
	status int
	msg    string
}

func (e *uploadError) Error() string { return e.msg }

func tooLarge(msg string) *uploadError { return &uploadError{http.StatusRequestEntityTooLarge, msg} }
func badUpload(msg string) *uploadError {
	return &uploadError{http.StatusBadRequest, msg}
}

// Create handles POST /recitation/reports (multipart/form-data with "audio" and "meta").
func (h *RecitationReportsHandler) Create(c *gin.Context) {
	ctx := c.Request.Context()
	userID := c.GetString(auth.UserIDKey)

	if c.Request.ContentLength > MaxRecitationReportBodyBytes {
		c.JSON(http.StatusRequestEntityTooLarge, gin.H{"error": "audio longer than 15 minutes"})
		return
	}

	count, err := h.store.CountReportsSince(ctx, userID, h.now().Add(-24*time.Hour))
	if err != nil {
		log.Printf("recitation reports count: %v", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "report failed"})
		return
	}
	if count >= h.cfg.DailyLimit {
		c.Header("Retry-After", "3600")
		c.JSON(http.StatusTooManyRequests, gin.H{"error": "daily report limit reached"})
		return
	}

	if err := os.MkdirAll(h.cfg.Dir, 0o750); err != nil {
		log.Printf("recitation reports dir %s: %v", h.cfg.Dir, err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "report storage unavailable"})
		return
	}
	if free, ok := h.freeBytes(h.cfg.Dir); ok && free < uint64(h.cfg.MinFreeMB)<<20 {
		log.Printf("recitation reports: only %d MB free in %s, refusing upload", free>>20, h.cfg.Dir)
		c.JSON(http.StatusInsufficientStorage, gin.H{"error": "report storage full, try again later"})
		return
	}

	c.Request.Body = http.MaxBytesReader(c.Writer, c.Request.Body, MaxRecitationReportBodyBytes)
	meta, tmpPath, audioBytes, err := h.readUpload(c.Request)
	if tmpPath != "" {
		defer os.Remove(tmpPath) // no-op once renamed into place
	}
	if err != nil {
		var ue *uploadError
		if errors.As(err, &ue) {
			c.JSON(ue.status, gin.H{"error": ue.msg})
			return
		}
		log.Printf("recitation reports upload: %v", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "report failed"})
		return
	}

	id := uuid.NewString()
	finalPath := filepath.Join(h.cfg.Dir, id+".pcm")
	if err := os.Rename(tmpPath, finalPath); err != nil {
		log.Printf("recitation reports rename: %v", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "report failed"})
		return
	}
	report := recitationReport{ID: id, UserID: userID, Meta: meta, AudioPath: finalPath, AudioBytes: audioBytes}
	if err := h.store.InsertReport(ctx, report); err != nil {
		os.Remove(finalPath)
		log.Printf("recitation reports insert: %v", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "report failed"})
		return
	}
	c.JSON(http.StatusCreated, gin.H{"id": id})
}

// readUpload streams the multipart body: the audio part goes straight to a temp file in
// the reports dir (never fully into memory), the meta part is parsed and validated.
// The returned tmpPath, when non-empty, must be removed or renamed by the caller.
func (h *RecitationReportsHandler) readUpload(r *http.Request) (meta recitationReportMeta, tmpPath string, audioBytes int64, err error) {
	reader, err := r.MultipartReader()
	if err != nil {
		return meta, "", 0, badUpload("body must be multipart/form-data")
	}
	var rawMeta []byte
	haveAudio := false
	for {
		part, perr := reader.NextPart()
		if perr == io.EOF {
			break
		}
		if perr != nil {
			return meta, tmpPath, 0, classifyBodyError(perr)
		}
		switch part.FormName() {
		case "meta":
			if rawMeta != nil {
				return meta, tmpPath, 0, badUpload("duplicate meta field")
			}
			rawMeta, err = io.ReadAll(io.LimitReader(part, maxRecitationReportMetaBytes+1))
			if err != nil {
				return meta, tmpPath, 0, classifyBodyError(err)
			}
			if len(rawMeta) > maxRecitationReportMetaBytes {
				return meta, tmpPath, 0, tooLarge("meta larger than 1 MB")
			}
		case "audio":
			if haveAudio {
				return meta, tmpPath, 0, badUpload("duplicate audio field")
			}
			haveAudio = true
			tmpPath, audioBytes, err = h.saveAudio(part)
			if err != nil {
				return meta, tmpPath, 0, err
			}
		default:
			if _, err := io.Copy(io.Discard, part); err != nil {
				return meta, tmpPath, 0, classifyBodyError(err)
			}
		}
	}
	if !haveAudio {
		return meta, tmpPath, 0, badUpload("audio field is required")
	}
	if audioBytes == 0 || audioBytes%2 != 0 {
		return meta, tmpPath, 0, badUpload("audio must be non-empty 16-bit PCM")
	}
	if rawMeta == nil {
		return meta, tmpPath, 0, badUpload("meta field is required")
	}
	meta, err = parseReportMeta(rawMeta)
	if err != nil {
		return meta, tmpPath, 0, badUpload(err.Error())
	}
	return meta, tmpPath, audioBytes, nil
}

func (h *RecitationReportsHandler) saveAudio(part io.Reader) (string, int64, error) {
	f, err := os.CreateTemp(h.cfg.Dir, ".upload-*.tmp")
	if err != nil {
		return "", 0, err
	}
	n, copyErr := io.Copy(f, io.LimitReader(part, MaxRecitationReportAudioBytes+1))
	closeErr := f.Close()
	if copyErr != nil {
		return f.Name(), 0, classifyBodyError(copyErr)
	}
	if closeErr != nil {
		return f.Name(), 0, closeErr
	}
	if n > MaxRecitationReportAudioBytes {
		return f.Name(), 0, tooLarge("audio longer than 15 minutes")
	}
	return f.Name(), n, nil
}

// classifyBodyError maps a failure while reading the request body to a client error.
func classifyBodyError(err error) error {
	var maxErr *http.MaxBytesError
	if errors.As(err, &maxErr) {
		return tooLarge("request body too large")
	}
	var pathErr *os.PathError
	if errors.As(err, &pathErr) {
		return err // disk write failure: server side
	}
	return badUpload("malformed multipart body")
}
