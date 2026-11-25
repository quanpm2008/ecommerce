CREATE TABLE orders.checkout_sale_evidence (
    checkout_session_id uuid PRIMARY KEY REFERENCES orders.checkout_session,
    payment_id uuid NOT NULL,
    completed_at text NOT NULL,
    next_attempt_at timestamptz DEFAULT statement_timestamp(),
    lease_token uuid,
    lease_until timestamptz,
    last_error varchar(80),
    CHECK ((lease_token IS NULL) = (lease_until IS NULL)),
    CHECK (next_attempt_at IS NOT NULL OR lease_token IS NULL)
);

CREATE INDEX checkout_sale_evidence_due
    ON orders.checkout_sale_evidence (next_attempt_at, checkout_session_id)
    WHERE next_attempt_at IS NOT NULL;

INSERT INTO orders.checkout_sale_evidence (checkout_session_id, payment_id, completed_at)
SELECT DISTINCT ON (i.checkout_session_id) i.checkout_session_id, c.payment_id,
    i.raw_envelope::jsonb #>> '{payload,completedAt}'
FROM orders.checkout_inbox i
JOIN orders.checkout_session c ON c.checkout_session_id = i.checkout_session_id
WHERE i.state = 'WAITING_DEPENDENCY' AND c.status = 'AWAITING_PAYMENT' AND c.payment_id IS NOT NULL
ORDER BY i.checkout_session_id, i.received_at, i.event_id;
