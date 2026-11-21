ALTER TABLE outbox_events
    ADD COLUMN lease_id UUID,
    ADD COLUMN lease_until TIMESTAMPTZ,
    ADD CONSTRAINT ck_payment_outbox_lease CHECK ((lease_id IS NULL) = (lease_until IS NULL));
