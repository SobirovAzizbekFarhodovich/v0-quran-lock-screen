package httpapi

import (
	"context"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
)

// recitationStore is the persistence the recitation history and report handlers need.
// Handlers depend on this interface so they can be tested without a database.
type recitationStore interface {
	// InsertAyahResults stores rows for the user, skipping client_ids already stored,
	// and returns how many rows were newly inserted.
	InsertAyahResults(ctx context.Context, userID string, rows []ayahResult) (int64, error)
	// ListAyahResults returns up to limit rows with created_at > since, ordered by
	// (created_at, client_id). It never ends a page in the middle of a millisecond: rows
	// sharing the last row's created_at are all included, so the page may hold more than
	// limit rows. hasMore reports whether rows newer than the page exist.
	ListAyahResults(ctx context.Context, userID string, since time.Time, limit int) (rows []ayahResult, hasMore bool, err error)
	DeleteAyahResults(ctx context.Context, userID string) (int64, error)

	CountReportsSince(ctx context.Context, userID string, since time.Time) (int, error)
	InsertReport(ctx context.Context, r recitationReport) error
}

// maxSameMillisecondRows caps how far a page is extended to finish a run of rows that
// share one created_at. A full-surah check writes every ayah with the same timestamp
// (at most 286), so this only bites a client that deliberately reuses one timestamp.
const maxSameMillisecondRows = 5000

type pgRecitationStore struct {
	db *pgxpool.Pool
}

func newPGRecitationStore(db *pgxpool.Pool) *pgRecitationStore {
	return &pgRecitationStore{db: db}
}

func (s *pgRecitationStore) InsertAyahResults(ctx context.Context, userID string, rows []ayahResult) (int64, error) {
	if len(rows) == 0 {
		return 0, nil
	}
	n := len(rows)
	clientIDs := make([]string, n)
	surahs := make([]int32, n)
	ayahs := make([]int32, n)
	words := make([]int32, n)
	correct := make([]int32, n)
	wrong := make([]int32, n)
	missed := make([]int32, n)
	modes := make([]string, n)
	days := make([]int64, n)
	created := make([]time.Time, n)
	for i, r := range rows {
		clientIDs[i] = r.ClientID
		surahs[i] = int32(r.Surah)
		ayahs[i] = int32(r.Ayah)
		words[i] = int32(r.WordCount)
		correct[i] = int32(r.CorrectCount)
		wrong[i] = int32(r.WrongCount)
		missed[i] = int32(r.MissedCount)
		modes[i] = r.Mode
		days[i] = r.LocalDay
		created[i] = time.UnixMilli(r.CreatedAt).UTC()
	}
	tag, err := s.db.Exec(ctx,
		`INSERT INTO recitation_ayah_results
		   (user_id, client_id, surah, ayah, word_count, correct_count, wrong_count,
		    missed_count, mode, local_day, created_at)
		 SELECT $1::uuid, t.client_id, t.surah, t.ayah, t.word_count, t.correct_count,
		        t.wrong_count, t.missed_count, t.mode, t.local_day, t.created_at
		 FROM unnest($2::text[], $3::int[], $4::int[], $5::int[], $6::int[], $7::int[],
		             $8::int[], $9::text[], $10::bigint[], $11::timestamptz[])
		   AS t(client_id, surah, ayah, word_count, correct_count, wrong_count,
		        missed_count, mode, local_day, created_at)
		 ON CONFLICT (user_id, client_id) DO NOTHING`,
		userID, clientIDs, surahs, ayahs, words, correct, wrong, missed, modes, days, created,
	)
	if err != nil {
		return 0, err
	}
	return tag.RowsAffected(), nil
}

const ayahResultColumns = `client_id, surah, ayah, word_count, correct_count, wrong_count,
	missed_count, mode, local_day, created_at`

func (s *pgRecitationStore) queryAyahResults(ctx context.Context, sql string, args ...any) ([]ayahResult, error) {
	rows, err := s.db.Query(ctx, sql, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := make([]ayahResult, 0)
	for rows.Next() {
		var r ayahResult
		var createdAt time.Time
		if err := rows.Scan(&r.ClientID, &r.Surah, &r.Ayah, &r.WordCount, &r.CorrectCount,
			&r.WrongCount, &r.MissedCount, &r.Mode, &r.LocalDay, &createdAt); err != nil {
			return nil, err
		}
		r.CreatedAt = createdAt.UnixMilli()
		out = append(out, r)
	}
	return out, rows.Err()
}

func (s *pgRecitationStore) ListAyahResults(ctx context.Context, userID string, since time.Time, limit int) ([]ayahResult, bool, error) {
	page, err := s.queryAyahResults(ctx,
		`SELECT `+ayahResultColumns+` FROM recitation_ayah_results
		 WHERE user_id = $1 AND created_at > $2
		 ORDER BY created_at, client_id
		 LIMIT $3`,
		userID, since, limit+1,
	)
	if err != nil {
		return nil, false, err
	}
	if len(page) <= limit {
		return page, false, nil
	}
	page = page[:limit]
	last := page[limit-1]
	lastAt := time.UnixMilli(last.CreatedAt).UTC()

	rest, err := s.queryAyahResults(ctx,
		`SELECT `+ayahResultColumns+` FROM recitation_ayah_results
		 WHERE user_id = $1 AND created_at = $2 AND client_id > $3
		 ORDER BY client_id
		 LIMIT $4`,
		userID, lastAt, last.ClientID, maxSameMillisecondRows,
	)
	if err != nil {
		return nil, false, err
	}
	page = append(page, rest...)

	var hasMore bool
	err = s.db.QueryRow(ctx,
		`SELECT EXISTS (SELECT 1 FROM recitation_ayah_results WHERE user_id = $1 AND created_at > $2)`,
		userID, lastAt,
	).Scan(&hasMore)
	if err != nil {
		return nil, false, err
	}
	return page, hasMore, nil
}

func (s *pgRecitationStore) DeleteAyahResults(ctx context.Context, userID string) (int64, error) {
	tag, err := s.db.Exec(ctx, `DELETE FROM recitation_ayah_results WHERE user_id = $1`, userID)
	if err != nil {
		return 0, err
	}
	return tag.RowsAffected(), nil
}

func (s *pgRecitationStore) CountReportsSince(ctx context.Context, userID string, since time.Time) (int, error) {
	var n int
	err := s.db.QueryRow(ctx,
		`SELECT count(*) FROM recitation_reports WHERE user_id = $1 AND created_at > $2`,
		userID, since,
	).Scan(&n)
	return n, err
}

func (s *pgRecitationStore) InsertReport(ctx context.Context, r recitationReport) error {
	_, err := s.db.Exec(ctx,
		`INSERT INTO recitation_reports
		   (id, user_id, surah, ayah, expected_text, transcript, alignment, app_version,
		    note, audio_path, audio_bytes)
		 VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11)`,
		r.ID, r.UserID, r.Meta.Surah, r.Meta.Ayah, r.Meta.ExpectedText, r.Meta.Transcript,
		r.Meta.Alignment, r.Meta.AppVersion, r.Meta.Note, r.AudioPath, r.AudioBytes,
	)
	return err
}
