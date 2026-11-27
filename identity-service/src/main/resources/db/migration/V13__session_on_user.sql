-- Decision 0003 / DB v5.1 §3.1.1, §6 (GAP-IDN-005): one refresh session per User, stored on users. Cutover: every
-- old session is dropped, so everyone signs in again; no old device is picked to keep its session.
ALTER TABLE users
    ADD COLUMN refresh_token_hash       TEXT,
    ADD COLUMN refresh_token_expires_at TIMESTAMPTZ,
    ADD COLUMN session_id               UUID,
    ADD CONSTRAINT uq_users_refresh_token_hash UNIQUE (refresh_token_hash),
    ADD CONSTRAINT uq_users_session_id UNIQUE (session_id),
    ADD CONSTRAINT ck_users_session_all_or_none CHECK (
        (refresh_token_hash IS NULL AND refresh_token_expires_at IS NULL AND session_id IS NULL)
        OR (refresh_token_hash IS NOT NULL AND refresh_token_expires_at IS NOT NULL AND session_id IS NOT NULL));

-- Marks of refresh tokens already rotated away, to detect reuse. Not a second store of live sessions: only the mark of
-- the user's current session_id counts. The old marks belong to dropped sessions.
DROP TABLE rotated_refresh_tokens;
CREATE TABLE rotated_refresh_tokens (
    token_hash TEXT        PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
    session_id UUID        NOT NULL,
    rotated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_rotated_refresh_tokens_user ON rotated_refresh_tokens (user_id, session_id);

DROP TABLE auth_sessions;
