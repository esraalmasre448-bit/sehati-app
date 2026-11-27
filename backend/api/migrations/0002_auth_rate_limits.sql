-- Hashed fixed-window buckets for auth endpoint abuse controls.
-- Keys are peppered hashes; raw IP addresses and email addresses are not stored.
CREATE TABLE IF NOT EXISTS auth_rate_limits (
  bucket_key TEXT PRIMARY KEY,
  window_started_at TIMESTAMPTZ NOT NULL,
  request_count INTEGER NOT NULL CHECK (request_count >= 0)
);
CREATE INDEX IF NOT EXISTS auth_rate_limits_window_idx ON auth_rate_limits (window_started_at);
