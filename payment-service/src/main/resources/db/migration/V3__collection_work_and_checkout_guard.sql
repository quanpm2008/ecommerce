CREATE TABLE payment_checkout_guard (
    checkout_session_id UUID PRIMARY KEY,
    closed_at TIMESTAMPTZ
);

CREATE TABLE payment_collection_work (
    payment_id UUID PRIMARY KEY REFERENCES payments(payment_id),
    next_run_at TIMESTAMPTZ,
    query_attempts INTEGER NOT NULL DEFAULT 0 CHECK (query_attempts >= 0),
    dispatch_started BOOLEAN NOT NULL DEFAULT false,
    lease_id UUID,
    lease_until TIMESTAMPTZ,
    CONSTRAINT ck_payment_collection_lease CHECK ((lease_id IS NULL) = (lease_until IS NULL))
);

CREATE INDEX ix_payment_collection_due ON payment_collection_work (next_run_at) WHERE next_run_at IS NOT NULL;

CREATE TABLE payment_collection_observation (
    payment_id UUID PRIMARY KEY REFERENCES payments(payment_id),
    provider_transaction_id VARCHAR(255) NOT NULL,
    amount_vnd BIGINT NOT NULL CHECK (amount_vnd > 0),
    currency CHAR(3) NOT NULL CHECK (currency = 'VND'),
    status VARCHAR(20) NOT NULL CHECK (status IN ('SUCCESS', 'FAILED')),
    completed_at TIMESTAMPTZ NOT NULL,
    error_code VARCHAR(100),
    observed_at TIMESTAMPTZ NOT NULL,
    applied_at TIMESTAMPTZ,
    CONSTRAINT ck_payment_observation_error CHECK (status <> 'FAILED' OR error_code IS NOT NULL)
);
