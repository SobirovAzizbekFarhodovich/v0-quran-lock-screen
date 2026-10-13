package httpapi

import (
	"errors"
	"fmt"
	"log"
	"net/http"
	"strconv"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/jackc/pgx/v5/pgxpool"

	"islamic-ai/backend/api-gateway/internal/auth"
)

const (
	maxResultsPerUpload    = 500
	defaultResultsPageSize = 500
	maxResultsPageSize     = 1000
	maxClientIDLength      = 64
	maxAyahWordCount       = 1000 // the longest ayah (2:282) has ~128 words
)

// earliestResultTime rejects timestamps from a reset device clock; the app did not exist
// before this date.
var earliestResultTime = time.Date(2020, 1, 1, 0, 0, 0, 0, time.UTC)

// ayahResult is one ayah of one recitation check, as the Android app stores it in Room.
type ayahResult struct {
	ClientID     string `json:"client_id"`
	Surah        int    `json:"surah"`
	Ayah         int    `json:"ayah"`
	WordCount    int    `json:"word_count"`
	CorrectCount int    `json:"correct_count"`
	WrongCount   int    `json:"wrong_count"`
	MissedCount  int    `json:"missed_count"`
	Mode         string `json:"mode"`
	CreatedAt    int64  `json:"created_at"` // epoch milliseconds
	LocalDay     int64  `json:"local_day"`  // device-local epoch day
}

// validate rejects only values that cannot come from a real recitation check.
func (r ayahResult) validate(now time.Time) error {
	if r.ClientID == "" {
		return errors.New("client_id is required")
	}
	if len(r.ClientID) > maxClientIDLength {
		return fmt.Errorf("client_id longer than %d characters", maxClientIDLength)
	}
	for _, ch := range r.ClientID {
		if ch <= ' ' || ch > '~' {
			return errors.New("client_id must be printable ASCII without spaces")
		}
	}
	if r.Surah < 1 || r.Surah > 114 {
		return errors.New("surah must be between 1 and 114")
	}
	if r.Ayah < 1 || r.Ayah > 286 {
		return errors.New("ayah must be between 1 and 286")
	}
	if r.WordCount < 1 || r.WordCount > maxAyahWordCount {
		return fmt.Errorf("word_count must be between 1 and %d", maxAyahWordCount)
	}
	if r.CorrectCount < 0 || r.WrongCount < 0 || r.MissedCount < 0 {
		return errors.New("counts must not be negative")
	}
	if r.CorrectCount+r.WrongCount+r.MissedCount > r.WordCount {
		return errors.New("correct_count + wrong_count + missed_count exceeds word_count")
	}
	if r.Mode != "check" && r.Mode != "memorize" {
		return errors.New(`mode must be "check" or "memorize"`)
	}
	created := time.UnixMilli(r.CreatedAt)
	if created.Before(earliestResultTime) || created.After(now.Add(7*24*time.Hour)) {
		return errors.New("created_at is not a plausible epoch-millisecond timestamp")
	}
	// local_day is the device's calendar day for created_at; UTC offsets span -12h..+14h.
	utcDay := r.CreatedAt / int64(24*time.Hour/time.Millisecond)
	if diff := r.LocalDay - utcDay; diff < -1 || diff > 1 {
		return errors.New("local_day does not match created_at")
	}
	return nil
}

type rejectedResult struct {
	Index    int    `json:"index"`
	ClientID string `json:"client_id"`
	Error    string `json:"error"`
}

// partitionResults splits an upload into rows to store and rows rejected as invalid.
func partitionResults(items []ayahResult, now time.Time) ([]ayahResult, []rejectedResult) {
	valid := make([]ayahResult, 0, len(items))
	rejected := make([]rejectedResult, 0)
	for i, item := range items {
		if err := item.validate(now); err != nil {
			rejected = append(rejected, rejectedResult{Index: i, ClientID: item.ClientID, Error: err.Error()})
			continue
		}
		valid = append(valid, item)
	}
	return valid, rejected
}

// parseResultsQuery reads ?since=<epoch ms>&limit=<n> for GET /recitation/results.
func parseResultsQuery(sinceRaw, limitRaw string) (since int64, limit int, err error) {
	limit = defaultResultsPageSize
	if sinceRaw != "" {
		since, err = strconv.ParseInt(sinceRaw, 10, 64)
		if err != nil || since < 0 {
			return 0, 0, errors.New("since must be a non-negative epoch-millisecond integer")
		}
	}
	if limitRaw != "" {
		limit, err = strconv.Atoi(limitRaw)
		if err != nil || limit < 1 || limit > maxResultsPageSize {
			return 0, 0, fmt.Errorf("limit must be between 1 and %d", maxResultsPageSize)
		}
	}
	return since, limit, nil
}

// RecitationResultsHandler serves the recitation history backup/restore API.
type RecitationResultsHandler struct {
	store recitationStore
	now   func() time.Time
}

func NewRecitationResultsHandler(db *pgxpool.Pool) *RecitationResultsHandler {
	return &RecitationResultsHandler{store: newPGRecitationStore(db), now: time.Now}
}

// Upload handles POST /recitation/results.
func (h *RecitationResultsHandler) Upload(c *gin.Context) {
	var req struct {
		Results []ayahResult `json:"results"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid JSON body"})
		return
	}
	if len(req.Results) == 0 {
		c.JSON(http.StatusBadRequest, gin.H{"error": "results must not be empty"})
		return
	}
	if len(req.Results) > maxResultsPerUpload {
		c.JSON(http.StatusRequestEntityTooLarge, gin.H{"error": fmt.Sprintf("at most %d results per request", maxResultsPerUpload)})
		return
	}

	valid, rejected := partitionResults(req.Results, h.now())
	inserted, err := h.store.InsertAyahResults(c.Request.Context(), c.GetString(auth.UserIDKey), valid)
	if err != nil {
		log.Printf("recitation results insert: %v", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "saving results failed"})
		return
	}
	c.JSON(http.StatusOK, gin.H{
		"accepted":   len(valid),
		"inserted":   inserted,
		"duplicates": int64(len(valid)) - inserted,
		"rejected":   rejected,
	})
}

// List handles GET /recitation/results.
func (h *RecitationResultsHandler) List(c *gin.Context) {
	since, limit, err := parseResultsQuery(c.Query("since"), c.Query("limit"))
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}
	rows, hasMore, err := h.store.ListAyahResults(c.Request.Context(), c.GetString(auth.UserIDKey), time.UnixMilli(since).UTC(), limit)
	if err != nil {
		log.Printf("recitation results list: %v", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "loading results failed"})
		return
	}
	if rows == nil {
		rows = []ayahResult{} // always a JSON array
	}
	nextSince := since
	if len(rows) > 0 {
		nextSince = rows[len(rows)-1].CreatedAt
	}
	c.JSON(http.StatusOK, gin.H{
		"results":    rows,
		"next_since": nextSince,
		"has_more":   hasMore,
	})
}

// Delete handles DELETE /recitation/results: wipes the user's synced history.
func (h *RecitationResultsHandler) Delete(c *gin.Context) {
	deleted, err := h.store.DeleteAyahResults(c.Request.Context(), c.GetString(auth.UserIDKey))
	if err != nil {
		log.Printf("recitation results delete: %v", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "deleting results failed"})
		return
	}
	c.JSON(http.StatusOK, gin.H{"deleted": deleted})
}
