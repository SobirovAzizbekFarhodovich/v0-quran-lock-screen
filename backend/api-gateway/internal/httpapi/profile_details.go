package httpapi

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"image"
	"image/jpeg"
	_ "image/png" // decode PNG uploads
	"io"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"
	"unicode/utf8"

	"github.com/gin-gonic/gin"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"islamic-ai/backend/api-gateway/internal/auth"
)

// profileDTO is everything the app's profile page shows. It is a superset of userDTO,
// so older app builds reading GET /profile keep working.
type profileDTO struct {
	ID         string  `json:"id"`
	Name       string  `json:"name"`
	Email      string  `json:"email"`
	Language   string  `json:"language"`
	BirthYear  *int    `json:"birth_year"`
	Gender     *string `json:"gender"`
	QuranLevel *string `json:"quran_level"`
	Goal       *string `json:"goal"`
	// Path of the photo (auth required), with a version so the app knows when it changed.
	AvatarURL *string `json:"avatar_url"`
}

const (
	maxAvatarBytes = 3 << 20
	maxAvatarSide  = 2048
	minAvatarSide  = 64
)

var (
	allowedGender     = map[string]bool{"male": true, "female": true}
	allowedQuranLevel = map[string]bool{"beginner": true, "fluent": true, "hafiz": true}
	allowedGoal       = map[string]bool{"memorize": true, "understand": true, "learn": true}
	allowedLanguage   = map[string]bool{"uz_latin": true, "uz_cyrillic": true, "ru": true, "en": true}
)

// ProfileDetailsHandler serves the profile page: details and the photo.
type ProfileDetailsHandler struct {
	db         *pgxpool.Pool
	avatarsDir string
	now        func() time.Time
}

func NewProfileDetailsHandler(db *pgxpool.Pool, avatarsDir string) *ProfileDetailsHandler {
	return &ProfileDetailsHandler{db: db, avatarsDir: avatarsDir, now: time.Now}
}

func loadProfile(ctx context.Context, db *pgxpool.Pool, userID string) (profileDTO, error) {
	var p profileDTO
	var avatarAt *time.Time
	err := db.QueryRow(ctx,
		`SELECT id::text, name, email, language, birth_year, gender, quran_level, goal, avatar_updated_at
		 FROM users WHERE id = $1`, userID,
	).Scan(&p.ID, &p.Name, &p.Email, &p.Language, &p.BirthYear, &p.Gender, &p.QuranLevel, &p.Goal, &avatarAt)
	if err != nil {
		return profileDTO{}, err
	}
	if avatarAt != nil {
		url := fmt.Sprintf("/api/v1/profile/avatar?v=%d", avatarAt.Unix())
		p.AvatarURL = &url
	}
	return p, nil
}

// Get handles GET /profile.
func (h *ProfileDetailsHandler) Get(c *gin.Context) {
	p, err := loadProfile(c.Request.Context(), h.db, c.GetString(auth.UserIDKey))
	if err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "user not found"})
		return
	}
	c.JSON(http.StatusOK, p)
}

// Patch handles PATCH /profile: only the fields present in the body change; a field sent
// as null is cleared (name and language cannot be cleared).
func (h *ProfileDetailsHandler) Patch(c *gin.Context) {
	var body map[string]json.RawMessage
	if err := json.NewDecoder(io.LimitReader(c.Request.Body, 16<<10)).Decode(&body); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid json"})
		return
	}
	sets := []string{}
	args := []any{}
	add := func(column string, value any) {
		args = append(args, value)
		sets = append(sets, fmt.Sprintf("%s = $%d", column, len(args)))
	}
	isNull := func(raw json.RawMessage) bool { return strings.TrimSpace(string(raw)) == "null" }
	text := func(key string, raw json.RawMessage) (string, error) {
		var s string
		if err := json.Unmarshal(raw, &s); err != nil {
			return "", fmt.Errorf("%s must be a string", key)
		}
		return strings.TrimSpace(s), nil
	}
	enum := func(key string, allowed map[string]bool) error {
		raw := body[key]
		if isNull(raw) {
			add(key, nil)
			return nil
		}
		s, err := text(key, raw)
		if err != nil || !allowed[s] {
			return fmt.Errorf("invalid %s", key)
		}
		add(key, s)
		return nil
	}
	for key, raw := range body {
		var err error
		switch key {
		case "name":
			var s string
			if s, err = text(key, raw); err == nil {
				if s == "" || utf8.RuneCountInString(s) > 60 {
					err = errors.New("name must be 1-60 characters")
				} else {
					add("name", s)
				}
			}
		case "language":
			var s string
			if s, err = text(key, raw); err == nil {
				if !allowedLanguage[s] {
					err = errors.New("invalid language")
				} else {
					add("language", s)
				}
			}
		case "birth_year":
			if isNull(raw) {
				add("birth_year", nil)
				break
			}
			var y int
			if json.Unmarshal(raw, &y) != nil || y < 1900 || y > h.now().Year() {
				err = errors.New("invalid birth_year")
			} else {
				add("birth_year", y)
			}
		case "gender":
			err = enum(key, allowedGender)
		case "quran_level":
			err = enum(key, allowedQuranLevel)
		case "goal":
			err = enum(key, allowedGoal)
		default:
			err = fmt.Errorf("unknown field %s", key)
		}
		if err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
			return
		}
	}
	userID := c.GetString(auth.UserIDKey)
	if len(sets) > 0 {
		args = append(args, userID)
		query := fmt.Sprintf("UPDATE users SET %s, updated_at = now() WHERE id = $%d", strings.Join(sets, ", "), len(args))
		tag, err := h.db.Exec(c.Request.Context(), query, args...)
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "update failed"})
			return
		}
		if tag.RowsAffected() == 0 {
			c.JSON(http.StatusNotFound, gin.H{"error": "user not found"})
			return
		}
	}
	h.Get(c)
}

func (h *ProfileDetailsHandler) avatarPath(userID string) (string, error) {
	// User ids are UUIDs; refuse anything that could leave the directory.
	if userID == "" || strings.ContainsAny(userID, `/\.`) {
		return "", errors.New("bad user id")
	}
	return filepath.Join(h.avatarsDir, userID+".jpg"), nil
}

// UploadAvatar handles POST /profile/avatar (multipart field "file", JPEG or PNG). The
// image is decoded and re-encoded as JPEG, which also drops any metadata (e.g. GPS).
func (h *ProfileDetailsHandler) UploadAvatar(c *gin.Context) {
	userID := c.GetString(auth.UserIDKey)
	path, err := h.avatarPath(userID)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid user"})
		return
	}
	c.Request.Body = http.MaxBytesReader(c.Writer, c.Request.Body, maxAvatarBytes+64<<10)
	file, _, err := c.Request.FormFile("file")
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "file is required (max 3 MB)"})
		return
	}
	defer file.Close()
	data, err := io.ReadAll(io.LimitReader(file, maxAvatarBytes+1))
	if err != nil || len(data) > maxAvatarBytes {
		c.JSON(http.StatusRequestEntityTooLarge, gin.H{"error": "image too large (max 3 MB)"})
		return
	}
	cfg, format, err := image.DecodeConfig(bytes.NewReader(data))
	if err != nil || (format != "jpeg" && format != "png") {
		c.JSON(http.StatusBadRequest, gin.H{"error": "only JPEG or PNG images"})
		return
	}
	if cfg.Width < minAvatarSide || cfg.Height < minAvatarSide || cfg.Width > maxAvatarSide || cfg.Height > maxAvatarSide {
		c.JSON(http.StatusBadRequest, gin.H{"error": "image must be 64-2048 px per side"})
		return
	}
	img, _, err := image.Decode(bytes.NewReader(data))
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "unreadable image"})
		return
	}
	if err := os.MkdirAll(h.avatarsDir, 0o750); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "storage unavailable"})
		return
	}
	tmp, err := os.CreateTemp(h.avatarsDir, ".upload-*")
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "storage unavailable"})
		return
	}
	encErr := jpeg.Encode(tmp, img, &jpeg.Options{Quality: 88})
	closeErr := tmp.Close()
	if encErr != nil || closeErr != nil || os.Rename(tmp.Name(), path) != nil {
		_ = os.Remove(tmp.Name())
		c.JSON(http.StatusInternalServerError, gin.H{"error": "could not save image"})
		return
	}
	if _, err := h.db.Exec(c.Request.Context(), `UPDATE users SET avatar_updated_at = $1, updated_at = now() WHERE id = $2`, h.now(), userID); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "update failed"})
		return
	}
	h.Get(c)
}

// Avatar handles GET /profile/avatar: the caller's own photo.
func (h *ProfileDetailsHandler) Avatar(c *gin.Context) {
	path, err := h.avatarPath(c.GetString(auth.UserIDKey))
	if err != nil {
		c.Status(http.StatusNotFound)
		return
	}
	if _, err := os.Stat(path); err != nil {
		c.Status(http.StatusNotFound)
		return
	}
	c.Header("Cache-Control", "private, max-age=86400")
	c.File(path)
}

// DeleteAvatar handles DELETE /profile/avatar.
func (h *ProfileDetailsHandler) DeleteAvatar(c *gin.Context) {
	userID := c.GetString(auth.UserIDKey)
	if path, err := h.avatarPath(userID); err == nil {
		_ = os.Remove(path)
	}
	if _, err := h.db.Exec(c.Request.Context(), `UPDATE users SET avatar_updated_at = NULL, updated_at = now() WHERE id = $1`, userID); err != nil && !errors.Is(err, pgx.ErrNoRows) {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "update failed"})
		return
	}
	h.Get(c)
}
