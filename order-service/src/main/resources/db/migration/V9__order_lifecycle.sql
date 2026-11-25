ALTER TABLE orders.purchase_order ADD COLUMN lifecycle_version bigint NOT NULL DEFAULT 0
    CHECK (lifecycle_version >= 0);

ALTER TABLE orders.checkout_event_outbox DROP CONSTRAINT checkout_event_outbox_checkout_session_id_key;
ALTER TABLE orders.checkout_event_outbox ADD COLUMN order_id uuid REFERENCES orders.purchase_order;
ALTER TABLE orders.checkout_event_outbox ADD COLUMN order_version bigint;
ALTER TABLE orders.checkout_event_outbox ADD CONSTRAINT checkout_event_outbox_order_version_check
    CHECK ((order_id IS NULL AND order_version IS NULL) OR
           (order_id IS NOT NULL AND order_version IS NOT NULL AND order_version > 0));

CREATE UNIQUE INDEX checkout_event_outbox_one_checkout
    ON orders.checkout_event_outbox (checkout_session_id) WHERE order_id IS NULL;
CREATE UNIQUE INDEX checkout_event_outbox_order_version
    ON orders.checkout_event_outbox (order_id, order_version) WHERE order_id IS NOT NULL;
CREATE INDEX purchase_order_seller_due ON orders.purchase_order (seller_action_due_at, order_id)
    WHERE status IN ('PAID', 'PACKING');
CREATE INDEX purchase_order_customer_due ON orders.purchase_order (customer_confirm_due_at, order_id)
    WHERE status = 'DELIVERED';
