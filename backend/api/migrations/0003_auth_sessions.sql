-- Opaque access/refresh sessions. Only token hashes are stored.
CREATE TABLE IF NOT EXISTS auth_sessions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  access_token_hash TEXT NOT NULL UNIQUE,
  refresh_token_hash TEXT NOT NULL UNIQUE,
  access_expires_at TIMESTAMPTZ NOT NULL,
  refresh_expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ,
  replaced_by_session_id UUID REFERENCES auth_sessions(id) ON DELETE SET NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (refresh_expires_at > access_expires_at)
);
CREATE INDEX IF NOT EXISTS auth_sessions_user_active_idx
  ON auth_sessions (user_id, refresh_expires_at) WHERE revoked_at IS NULL;
CREATE INDEX IF NOT EXISTS auth_sessions_expiry_idx
  ON auth_sessions (refresh_expires_at);
