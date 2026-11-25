ALTER TABLE payment_refund_work
    ALTER COLUMN next_run_at DROP NOT NULL,
    ADD COLUMN lease_id UUID,
    ADD COLUMN lease_until TIMESTAMPTZ,
    ADD CONSTRAINT ck_refund_work_lease CHECK ((lease_id IS NULL) = (lease_until IS NULL));

CREATE INDEX ix_refund_work_due ON payment_refund_work(next_run_at) WHERE next_run_at IS NOT NULL;

CREATE TABLE payment_refund_attempts (
    attempt_id UUID PRIMARY KEY,
    refund_id UUID NOT NULL REFERENCES refunds(refund_id),
    retry_cycle INTEGER NOT NULL CHECK (retry_cycle >= 0),
    attempt_number INTEGER NOT NULL CHECK (attempt_number BETWEEN 0 AND 3),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED')),
    provider_refund_id VARCHAR(255),
    completed_at TIMESTAMPTZ,
    error_code VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_refund_attempt_number UNIQUE (refund_id, retry_cycle, attempt_number),
    CONSTRAINT uq_refund_attempt_provider_ref UNIQUE (provider_refund_id),
    CONSTRAINT ck_refund_attempt_completed CHECK (
        (status = 'PENDING' AND completed_at IS NULL AND error_code IS NULL)
        OR (status = 'SUCCESS' AND completed_at IS NOT NULL AND provider_refund_id IS NOT NULL AND error_code IS NULL)
        OR (status = 'FAILED' AND completed_at IS NOT NULL AND provider_refund_id IS NOT NULL AND error_code IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_refund_attempt_active ON payment_refund_attempts(refund_id) WHERE status = 'PENDING';
CREATE UNIQUE INDEX uq_refund_attempt_success ON payment_refund_attempts(refund_id) WHERE status = 'SUCCESS';

CREATE TABLE payment_refund_attempt_queries (
    query_id UUID PRIMARY KEY,
    attempt_id UUID NOT NULL REFERENCES payment_refund_attempts(attempt_id),
    checked_at TIMESTAMPTZ NOT NULL,
    outcome VARCHAR(20) NOT NULL CHECK (outcome IN ('UNKNOWN', 'PENDING', 'SUCCESS', 'FAILED', 'CONFLICT'))
);

CREATE INDEX ix_refund_attempt_queries ON payment_refund_attempt_queries(attempt_id, checked_at);

CREATE INDEX ix_payment_outbox_aggregate_version ON outbox_events(aggregate_id, version) WHERE status = 'PENDING';
