CREATE TABLE notifications (
    notification_id UUID PRIMARY KEY,
    dedupe_key TEXT NOT NULL UNIQUE,
    payload_hash VARCHAR(64) NOT NULL,
    recipient_user_id UUID NOT NULL,
    audience_context VARCHAR(16) NOT NULL
        CHECK (audience_context IN ('CUSTOMER', 'SELLER', 'ACCOUNT')),
    notification_type VARCHAR(70) NOT NULL,
    channel VARCHAR(16) NOT NULL DEFAULT 'IN_APP' CHECK (channel = 'IN_APP'),
    title VARCHAR(255),
    content TEXT,
    reference_type VARCHAR(70),
    reference_id UUID,
    reference_code VARCHAR(160),
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT notification_live_content CHECK (
        deleted_at IS NOT NULL OR (title IS NOT NULL AND content IS NOT NULL)
    ),
    CONSTRAINT notification_reference CHECK (
        (reference_type IS NULL AND reference_id IS NULL AND reference_code IS NULL)
        OR (reference_type IS NOT NULL AND reference_id IS NOT NULL
            AND (reference_type = 'WITHDRAWAL' OR reference_code IS NULL))
    ),
    CONSTRAINT notification_deleted_read CHECK (deleted_at IS NULL OR is_read)
);

CREATE INDEX notifications_visible_owner_idx
    ON notifications (recipient_user_id, created_at DESC, notification_id DESC)
    WHERE channel = 'IN_APP';

-- No raw event body or rendered/template payload is kept in the inbox.
CREATE TABLE notification_inbox (
    event_id TEXT PRIMARY KEY,
    payload_hash VARCHAR(64) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    state VARCHAR(20) NOT NULL CHECK (state IN ('APPLIED', 'FAILED_REVIEW')),
    last_error_code VARCHAR(70),
    received_at TIMESTAMPTZ NOT NULL
);
