ALTER TABLE finance.wallet ADD COLUMN frozen BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE finance.withdrawal_outbox (
    event_id UUID PRIMARY KEY,
    withdrawal_id UUID NOT NULL UNIQUE REFERENCES finance.withdrawal(withdrawal_id),
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ
);

CREATE INDEX withdrawal_outbox_pending_idx
    ON finance.withdrawal_outbox (occurred_at, event_id) WHERE published_at IS NULL;
