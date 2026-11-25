CREATE TABLE payment_refund_retry_request (
    customer_id uuid NOT NULL,
    idempotency_key uuid NOT NULL,
    refund_id uuid NOT NULL REFERENCES refunds(refund_id),
    expected_retry_cycle integer NOT NULL CHECK (expected_retry_cycle >= 0),
    response jsonb,
    PRIMARY KEY (customer_id, idempotency_key)
);
