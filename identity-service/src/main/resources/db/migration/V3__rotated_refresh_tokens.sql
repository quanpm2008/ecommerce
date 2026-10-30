-- Refresh tokens already rotated away from a session (API §6.1 refresh). auth_sessions keeps only the current hash;
-- presenting one of these again means the token was copied, so the whole session (the token chain) is revoked.
CREATE TABLE rotated_refresh_tokens (
    token_hash VARCHAR(128) PRIMARY KEY,
    session_id UUID         NOT NULL REFERENCES auth_sessions (session_id) ON DELETE CASCADE,
    rotated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_rotated_refresh_tokens_session ON rotated_refresh_tokens (session_id);
