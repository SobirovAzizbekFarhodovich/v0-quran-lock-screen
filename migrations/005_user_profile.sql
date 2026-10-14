-- 005: the profile page — age (as birth year), gender, Quran reading level, goal, and
-- whether the user has a photo (the file lives in the gateway's avatars volume).
ALTER TABLE users ADD COLUMN IF NOT EXISTS birth_year INT CHECK (birth_year BETWEEN 1900 AND 2100);
ALTER TABLE users ADD COLUMN IF NOT EXISTS gender TEXT CHECK (gender IN ('male', 'female'));
ALTER TABLE users ADD COLUMN IF NOT EXISTS quran_level TEXT CHECK (quran_level IN ('beginner', 'fluent', 'hafiz'));
ALTER TABLE users ADD COLUMN IF NOT EXISTS goal TEXT CHECK (goal IN ('memorize', 'understand', 'learn'));
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_updated_at TIMESTAMPTZ;
