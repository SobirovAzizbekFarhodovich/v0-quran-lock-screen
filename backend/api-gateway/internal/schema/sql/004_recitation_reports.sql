-- User-flagged recitation results with the raw phone recording, collected for evaluation.
-- The audio lives on disk as RECITATION_REPORTS_DIR/<id>.pcm; audio_path points to it.
-- Idempotent: also applied by the api-gateway at startup (internal/schema).
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS recitation_reports (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  surah INT NOT NULL,
  ayah INT NOT NULL,
  expected_text TEXT NOT NULL DEFAULT '',
  transcript TEXT NOT NULL DEFAULT '',
  alignment TEXT NOT NULL DEFAULT '',
  app_version TEXT NOT NULL DEFAULT '',
  note TEXT NOT NULL DEFAULT '',
  audio_path TEXT NOT NULL,
  audio_bytes BIGINT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Per-user daily rate limit check.
CREATE INDEX IF NOT EXISTS idx_recitation_reports_user_created
  ON recitation_reports (user_id, created_at);
-- Chronological export for evaluation.
CREATE INDEX IF NOT EXISTS idx_recitation_reports_created
  ON recitation_reports (created_at);
