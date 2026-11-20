-- Cart service schema (Database §3.3, SAD §3.3 and A.5.3). Cart owns only Cart/CartItem and its own inbox;
-- product_id and customer_id are references to other services, never foreign keys across services.

CREATE TABLE carts (
    cart_id     UUID        PRIMARY KEY,
    -- Customer.customer_id = User.user_id (JWT sub). One cart per customer.
    customer_id UUID        NOT NULL,
    -- Bumped on every change to the cart or its lines (lost-update protection across tabs).
    row_version BIGINT      NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_carts_customer UNIQUE (customer_id)
);

CREATE TABLE cart_items (
    -- A removed-then-re-added product gets a new id, so a checkout snapshot never decrements the new line.
    cart_item_id UUID        PRIMARY KEY,
    cart_id      UUID        NOT NULL REFERENCES carts (cart_id) ON DELETE CASCADE,
    product_id   UUID        NOT NULL,
    -- P-10: 1-99 units per line. "Out of stock" is never stored as quantity 0.
    quantity     INTEGER     NOT NULL,
    -- Line version for the checkout snapshot (Database §3.6.1 cart_snapshot.row_version, SAD A.5.3).
    row_version  BIGINT      NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_cart_items_cart_product UNIQUE (cart_id, product_id),
    CONSTRAINT ck_cart_items_quantity CHECK (quantity BETWEEN 1 AND 99)
);

-- Inbox for consumed events (SAD A.5.2). event_id stops a redelivered event; business_key stops a different event
-- carrying the same obligation (AT-21), e.g. checkout.paid:<checkoutSessionId>.
CREATE TABLE inbox_events (
    event_id     VARCHAR(100) PRIMARY KEY,
    event_type   VARCHAR(100) NOT NULL,
    business_key VARCHAR(200) NOT NULL,
    payload_hash VARCHAR(64)  NOT NULL,
    state        VARCHAR(20)  NOT NULL,
    last_error   VARCHAR(1000),
    received_at  TIMESTAMPTZ  NOT NULL,
    applied_at   TIMESTAMPTZ,
    CONSTRAINT ck_inbox_events_state CHECK (state IN ('APPLIED', 'SKIPPED', 'FAILED_REVIEW'))
);

CREATE UNIQUE INDEX uq_inbox_events_business_key_applied ON inbox_events (business_key) WHERE state = 'APPLIED';
