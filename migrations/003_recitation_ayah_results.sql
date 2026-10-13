-- Per-ayah recitation history synced from the Android app (one row per ayah per check).
-- Idempotent: also applied by the api-gateway at startup (internal/schema), because
-- docker-entrypoint-initdb.d only runs on a fresh database volume.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS recitation_ayah_results (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  client_id TEXT NOT NULL,
  surah INT NOT NULL,
  ayah INT NOT NULL,
  word_count INT NOT NULL,
  correct_count INT NOT NULL,
  wrong_count INT NOT NULL,
  missed_count INT NOT NULL,
  mode TEXT NOT NULL CHECK (mode IN ('check', 'memorize')),
  local_day BIGINT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (user_id, client_id)
);

-- Serves GET /api/v1/recitation/results paging:
-- WHERE user_id = ? AND created_at > ? ORDER BY created_at, client_id.
CREATE INDEX IF NOT EXISTS idx_recitation_ayah_results_user_created
  ON recitation_ayah_results (user_id, created_at, client_id);
