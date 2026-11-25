ALTER TABLE orders.checkout_outbox
    ADD COLUMN next_attempt_at timestamptz NOT NULL DEFAULT statement_timestamp(),
    ADD COLUMN lease_until timestamptz,
    ADD COLUMN lease_token uuid,
    ADD COLUMN delivered_at timestamptz,
    ADD CHECK ((lease_token IS NULL) = (lease_until IS NULL)),
    ADD CHECK (delivered_at IS NULL OR lease_token IS NULL);

CREATE INDEX checkout_outbox_due_delivery
    ON orders.checkout_outbox (next_attempt_at, created_at, command_id)
    WHERE delivered_at IS NULL;
