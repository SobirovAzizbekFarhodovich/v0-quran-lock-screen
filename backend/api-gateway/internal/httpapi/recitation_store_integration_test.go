package httpapi

import (
	"context"
	"fmt"
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"

	"islamic-ai/backend/api-gateway/internal/schema"
)

// TestPGRecitationStore runs the real SQL against Postgres. It is skipped unless
// TEST_DATABASE_URL points at a disposable database, e.g.
//
//	docker run -d --rm -p 55432:5432 -e POSTGRES_PASSWORD=t postgres:16-alpine
//	TEST_DATABASE_URL=postgres://postgres:t@localhost:55432/postgres?sslmode=disable go test ./...
func TestPGRecitationStore(t *testing.T) {
	url := os.Getenv("TEST_DATABASE_URL")
	if url == "" {
		t.Skip("TEST_DATABASE_URL not set")
	}
	ctx := context.Background()
	db, err := pgxpool.New(ctx, url)
	if err != nil {
		t.Fatal(err)
	}
	defer db.Close()

	// Base schema (users) as a fresh volume would get it, then the gateway's startup step twice
	// to prove it is idempotent.
	initSQL, err := os.ReadFile(filepath.Join("..", "..", "..", "..", "migrations", "001_init.sql"))
	if err != nil {
		t.Fatal(err)
	}
	if _, err := db.Exec(ctx, string(initSQL)); err != nil {
		t.Fatalf("001_init: %v", err)
	}
	for i := 0; i < 2; i++ {
		if err := schema.Apply(ctx, db); err != nil {
			t.Fatalf("schema.Apply #%d: %v", i+1, err)
		}
	}

	var userID, otherID string
	suffix := time.Now().UnixNano()
	for _, dst := range []*string{&userID, &otherID} {
		if err := db.QueryRow(ctx,
			`INSERT INTO users (name, email, password_hash) VALUES ('t', $1, 'x') RETURNING id::text`,
			fmt.Sprintf("it-%d-%p@example.com", suffix, dst),
		).Scan(dst); err != nil {
			t.Fatal(err)
		}
	}
	defer db.Exec(ctx, `DELETE FROM users WHERE id = ANY($1::uuid[])`, []string{userID, otherID})

	store := newPGRecitationStore(db)
	base := time.Date(2026, 10, 1, 8, 0, 0, 123_000_000, time.UTC).UnixMilli()
	mk := func(id string, at int64) ayahResult {
		return ayahResult{ClientID: id, Surah: 2, Ayah: 1, WordCount: 3, CorrectCount: 2, WrongCount: 1,
			Mode: "check", CreatedAt: at, LocalDay: at / 86_400_000}
	}
	// A full-surah check: five ayahs with one timestamp, then two later single checks.
	rows := []ayahResult{mk("a1", base), mk("a2", base), mk("a3", base), mk("a4", base), mk("a5", base),
		mk("b", base+1), mk("c", base+60_000)}
	n, err := store.InsertAyahResults(ctx, userID, rows)
	if err != nil || n != 7 {
		t.Fatalf("insert = %d, %v", n, err)
	}
	// Re-upload plus an in-request duplicate: nothing new.
	n, err = store.InsertAyahResults(ctx, userID, append(rows[:2:2], mk("a1", base)))
	if err != nil || n != 0 {
		t.Fatalf("re-insert = %d, %v", n, err)
	}
	// Same client_id for another user is a different row.
	if n, err = store.InsertAyahResults(ctx, otherID, rows[:1]); err != nil || n != 1 {
		t.Fatalf("other user insert = %d, %v", n, err)
	}

	// limit 2 lands inside the same-millisecond run: the page must finish it.
	page, more, err := store.ListAyahResults(ctx, userID, time.UnixMilli(0), 2)
	if err != nil {
		t.Fatal(err)
	}
	if len(page) != 5 || !more || page[0].ClientID != "a1" || page[4].ClientID != "a5" || page[4].CreatedAt != base {
		t.Fatalf("page 1 = %+v more=%v", page, more)
	}
	page, more, err = store.ListAyahResults(ctx, userID, time.UnixMilli(page[4].CreatedAt), 2)
	if err != nil || len(page) != 2 || more || page[0].ClientID != "b" || page[1].ClientID != "c" {
		t.Fatalf("page 2 = %+v more=%v err=%v", page, more, err)
	}
	got := page[1]
	if got.Surah != 2 || got.Ayah != 1 || got.WordCount != 3 || got.CorrectCount != 2 || got.WrongCount != 1 ||
		got.MissedCount != 0 || got.Mode != "check" || got.CreatedAt != base+60_000 || got.LocalDay != (base+60_000)/86_400_000 {
		t.Fatalf("round trip mismatch: %+v", got)
	}
	page, more, err = store.ListAyahResults(ctx, userID, time.UnixMilli(base+60_000), 500)
	if err != nil || len(page) != 0 || more {
		t.Fatalf("page 3 = %+v more=%v err=%v", page, more, err)
	}

	deleted, err := store.DeleteAyahResults(ctx, userID)
	if err != nil || deleted != 7 {
		t.Fatalf("delete = %d, %v", deleted, err)
	}
	if page, _, _ := store.ListAyahResults(ctx, otherID, time.UnixMilli(0), 10); len(page) != 1 {
		t.Fatalf("other user's history was touched: %+v", page)
	}

	// Reports.
	report := recitationReport{
		ID: "0b5e9a8e-4a43-4f0e-9d2a-7c3b1f6e2a10", UserID: userID,
		Meta:      recitationReportMeta{Surah: 1, Ayah: 0, ExpectedText: "بسم", Note: "x"},
		AudioPath: "/data/recitation-reports/0b5e9a8e-4a43-4f0e-9d2a-7c3b1f6e2a10.pcm", AudioBytes: 32000,
	}
	if err := store.InsertReport(ctx, report); err != nil {
		t.Fatal(err)
	}
	count, err := store.CountReportsSince(ctx, userID, time.Now().Add(-24*time.Hour))
	if err != nil || count != 1 {
		t.Fatalf("count = %d, %v", count, err)
	}
	if count, _ = store.CountReportsSince(ctx, userID, time.Now().Add(time.Hour)); count != 0 {
		t.Fatalf("count in the future window = %d", count)
	}
}
