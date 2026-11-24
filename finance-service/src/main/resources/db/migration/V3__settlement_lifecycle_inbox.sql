ALTER TABLE settlement_inbox DROP CONSTRAINT settlement_inbox_event_type_check;
ALTER TABLE settlement_inbox ADD CONSTRAINT settlement_inbox_event_type_check
    CHECK (event_type IN ('checkout.paid', 'seller.approved', 'order.cancelled', 'order.confirmed', 'refund.succeeded'));

CREATE TABLE funds_available_outbox (
    event_id uuid PRIMARY KEY,
    order_id uuid NOT NULL UNIQUE REFERENCES settlement (order_id),
    seller_id uuid NOT NULL,
    seller_amount_vnd bigint NOT NULL CHECK (seller_amount_vnd >= 0),
    occurred_at timestamptz NOT NULL,
    published_at timestamptz
);

CREATE INDEX funds_available_outbox_pending_idx ON funds_available_outbox (occurred_at, event_id)
    WHERE published_at IS NULL;
