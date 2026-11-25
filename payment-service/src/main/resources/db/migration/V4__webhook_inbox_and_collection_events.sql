CREATE TABLE provider_webhook_inbox (
    provider VARCHAR(30) NOT NULL,
    event_id VARCHAR(255) NOT NULL,
    payload_digest BYTEA NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (provider, event_id)
);

CREATE TABLE payment_checkout_inbox (
    event_id UUID PRIMARY KEY,
    checkout_session_id UUID NOT NULL,
    version BIGINT NOT NULL CHECK (version > 0),
    received_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE payment_refund_work (
    refund_id UUID PRIMARY KEY REFERENCES refunds(refund_id),
    next_run_at TIMESTAMPTZ NOT NULL
);

ALTER TABLE outbox_events ADD COLUMN version BIGINT NOT NULL DEFAULT 1 CHECK (version > 0);
