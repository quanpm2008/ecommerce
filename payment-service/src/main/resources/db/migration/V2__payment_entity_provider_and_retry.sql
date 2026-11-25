ALTER TABLE payments
    ALTER COLUMN provider SET DEFAULT 'DEMO_PROVIDER',
    ALTER COLUMN currency TYPE char(3),
    ADD COLUMN expires_at timestamptz,
    ADD COLUMN payment_url text;

ALTER TABLE refunds
    ADD COLUMN retry_cycle integer NOT NULL DEFAULT 0 CHECK (retry_cycle >= 0),
    ADD COLUMN customer_retry_required boolean NOT NULL DEFAULT false,
    ADD CONSTRAINT ck_refunds_retry_limit CHECK (retry_count <= 3);

UPDATE refunds SET customer_retry_required = true WHERE status = 'FAILED' AND retry_count = 3;

CREATE UNIQUE INDEX uq_refunds_full_checkout ON refunds (payment_id) WHERE scope = 'FULL_CHECKOUT';
