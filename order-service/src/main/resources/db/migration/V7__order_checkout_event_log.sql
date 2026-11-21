CREATE TABLE orders.checkout_inbox (
    event_id uuid PRIMARY KEY,
    checkout_session_id uuid,
    raw_envelope text NOT NULL,
    state varchar(24) NOT NULL CHECK (state IN ('RECEIVED', 'WAITING_DEPENDENCY', 'APPLIED', 'FAILED_REVIEW')),
    received_at timestamptz NOT NULL DEFAULT statement_timestamp(),
    resolved_at timestamptz,
    last_error varchar(80),
    conflict_envelope text
);

CREATE INDEX checkout_inbox_waiting
    ON orders.checkout_inbox (checkout_session_id)
    WHERE state = 'WAITING_DEPENDENCY';

CREATE TABLE orders.checkout_event_outbox (
    event_id uuid PRIMARY KEY,
    checkout_session_id uuid NOT NULL UNIQUE REFERENCES orders.checkout_session,
    envelope text NOT NULL,
    created_at timestamptz NOT NULL,
    next_attempt_at timestamptz NOT NULL DEFAULT statement_timestamp(),
    sent_at timestamptz,
    lease_token uuid,
    lease_until timestamptz,
    CHECK ((lease_token IS NULL) = (lease_until IS NULL)),
    CHECK (sent_at IS NULL OR lease_token IS NULL)
);

CREATE INDEX checkout_event_outbox_ready
    ON orders.checkout_event_outbox (next_attempt_at, created_at, event_id)
    WHERE sent_at IS NULL;
