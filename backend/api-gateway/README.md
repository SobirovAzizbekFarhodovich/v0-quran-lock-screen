# api-gateway

Go (gin) gateway in front of Postgres, Redis and the AI services. Authenticated
routes live under `/api/v1` and need `Authorization: Bearer <access token>`.

## Database migrations

`migrations/*.sql` (repo root) is mounted into postgres as
`docker-entrypoint-initdb.d`, which **only runs when the data volume is empty**.
Newer migrations that every environment needs are therefore also embedded in the
gateway (`internal/schema/sql/`, kept byte-identical to `migrations/` by a test) and
applied at every start under an advisory lock. They are all `CREATE ... IF NOT
EXISTS`, so re-running them is harmless. Currently: `003_recitation_ayah_results.sql`,
`004_recitation_reports.sql`.

To add one: write `migrations/00N_name.sql` idempotently, copy it to
`internal/schema/sql/`, rebuild the gateway. To apply by hand instead:

```bash
docker compose exec -T postgres psql -U islamic_ai -d islamic_ai < migrations/003_recitation_ayah_results.sql
```

## Recitation history sync

Backs up and restores the app's on-device recitation history (Room
`recitation_results`, one row per ayah per check). Each row carries a stable,
client-generated `client_id` (UUID string); uploads are idempotent on
`(user, client_id)`.

### Row shape

| field | type | notes |
|---|---|---|
| `client_id` | string | 1–64 printable ASCII chars, no spaces (a UUID) |
| `surah` | int | 1–114 |
| `ayah` | int | 1–286 |
| `word_count` | int | 1–1000 |
| `correct_count`, `wrong_count`, `missed_count` | int | ≥ 0, sum ≤ `word_count` |
| `mode` | string | `"check"` or `"memorize"` |
| `created_at` | int | epoch **milliseconds** (2020-01-01 … now + 7 days) |
| `local_day` | int | device-local epoch day of `created_at` (± 1 day of the UTC day) |

### `POST /api/v1/recitation/results`

Up to 500 rows per request.

```http
POST /api/v1/recitation/results
Authorization: Bearer eyJ...
Content-Type: application/json

{"results": [
  {"client_id": "6f1c2a3e-8b8d-4c55-9a43-2f0e1d9b7c10", "surah": 1, "ayah": 2,
   "word_count": 4, "correct_count": 3, "wrong_count": 1, "missed_count": 0,
   "mode": "check", "created_at": 1760340000123, "local_day": 20374}
]}
```

```json
{"accepted": 1, "inserted": 1, "duplicates": 0, "rejected": []}
```

- `accepted`: valid rows the server now holds (newly `inserted` + `duplicates`
  already stored earlier). Mark every accepted row as synced.
- `rejected`: invalid rows, e.g.
  `{"index": 3, "client_id": "…", "error": "surah must be between 1 and 114"}`.
  They will never be accepted, so mark them done too (don't retry forever).
- `400` malformed JSON / empty `results`; `413` more than 500 rows; `401` bad token.

### `GET /api/v1/recitation/results?since=<epoch ms>&limit=<n>`

Rows with `created_at > since`, oldest first. `since` defaults to 0, `limit` to 500
(max 1000).

```json
{
  "results": [{"client_id": "…", "surah": 1, "ayah": 2, "word_count": 4,
               "correct_count": 3, "wrong_count": 1, "missed_count": 0,
               "mode": "check", "created_at": 1760340000123, "local_day": 20374}],
  "next_since": 1760340000123,
  "has_more": false
}
```

Restore loop: start with `since=0`, insert the rows locally (skip `client_id`s you
already have), then call again with `since=next_since` while `has_more` is true.
A page never ends in the middle of one millisecond — a full-surah check writes all
its ayahs with the same `created_at`, so a page may hold a little more than `limit`
rows. With no rows, `next_since` echoes `since`.

### `DELETE /api/v1/recitation/results`

Deletes all of the user's synced history: `{"deleted": 42}`.

## Recitation reports

### `POST /api/v1/recitation/reports`

The user flags a recitation result as wrong; the phone recording is kept for
evaluation. `multipart/form-data` with two fields (any order):

- `audio`: raw 16 kHz mono s16le PCM, non-empty, even byte count, at most
  15 minutes = 28,800,000 bytes.
- `meta`: JSON (≤ 1 MB):

```json
{"surah": 2, "ayah": 0, "expected_text": "الم ذلك الكتاب ...",
 "transcript": "الم ذلك الكتب ...", "alignment": "ok ok wrong(الكتب) ...",
 "app_version": "1.4.0 (42)", "note": "optional, max 500 characters"}
```

`surah` 1–114 is required; `ayah` 0 means several/auto-detected (0–286);
`expected_text`, `transcript`, `alignment` up to 200 KB each; `app_version` up to 64 bytes.

```bash
curl -X POST http://HOST/api/v1/recitation/reports \
  -H "Authorization: Bearer $TOKEN" \
  -F 'meta={"surah":1,"ayah":0,"expected_text":"...","transcript":"...","alignment":"...","app_version":"1.4.0"};type=application/json' \
  -F 'audio=@recording.pcm;type=application/octet-stream'
```

`201 {"id": "0b5e9a8e-…"}`. Errors: `400` bad multipart/meta/audio, `413` audio
over 15 minutes, `429` more than `RECITATION_REPORTS_DAILY_LIMIT` (default 20)
reports in the last 24 h, `507` reports disk below `RECITATION_REPORTS_MIN_FREE_MB`
(default 1024).

Audio is written to `RECITATION_REPORTS_DIR` (default `/data/recitation-reports`) as
`<id>.pcm`; the `recitation_reports` row stores its path and size. Deleting a user
removes their rows (cascade) but not the audio files.

Deployment needs: a persistent volume on `RECITATION_REPORTS_DIR` for the
`api-gateway` service, and nginx `client_max_body_size` ≥ 30m for this path (the
global value is 20m).
