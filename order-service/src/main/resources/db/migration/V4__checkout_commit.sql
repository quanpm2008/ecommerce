CREATE TABLE orders.purchase_order (
    order_id uuid PRIMARY KEY,
    checkout_session_id uuid NOT NULL REFERENCES orders.checkout_session,
    seller_id uuid NOT NULL,
    seller_name_snapshot varchar(150) NOT NULL,
    customer_id uuid NOT NULL,
    total_amount_vnd bigint NOT NULL CHECK (total_amount_vnd > 0),
    currency char(3) NOT NULL CHECK (currency = 'VND'),
    commission_bps_snapshot integer NOT NULL CHECK (commission_bps_snapshot BETWEEN 0 AND 10000),
    recipient_name varchar(150) NOT NULL,
    recipient_phone varchar(20) NOT NULL,
    province_code varchar(32) NOT NULL,
    province_name varchar(150) NOT NULL,
    ward_code varchar(32) NOT NULL,
    ward_name varchar(150) NOT NULL,
    address_detail varchar(300) NOT NULL,
    status varchar(24) NOT NULL CHECK (status IN ('AWAITING_PAYMENT', 'PAYMENT_FAILED', 'EXPIRED', 'PAID',
        'PACKING', 'DELIVERED', 'CONFIRMED', 'CANCELLED')),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    UNIQUE (checkout_session_id, seller_id)
);

CREATE TABLE orders.order_item (
    order_item_id uuid PRIMARY KEY,
    order_id uuid NOT NULL REFERENCES orders.purchase_order,
    product_id uuid NOT NULL REFERENCES orders.inventory,
    product_name_snapshot varchar(255) NOT NULL,
    cover_media_snapshot text NOT NULL,
    unit_price_vnd_snapshot bigint NOT NULL CHECK (unit_price_vnd_snapshot > 0),
    quantity integer NOT NULL CHECK (quantity > 0),
    line_total_vnd bigint GENERATED ALWAYS AS (unit_price_vnd_snapshot * quantity) STORED,
    source_cart_item_id uuid
);

CREATE TABLE orders.reservation (
    reservation_id uuid PRIMARY KEY,
    order_item_id uuid NOT NULL UNIQUE REFERENCES orders.order_item,
    product_id uuid NOT NULL REFERENCES orders.inventory,
    quantity integer NOT NULL CHECK (quantity > 0),
    status varchar(16) NOT NULL CHECK (status IN ('HELD', 'COMMITTED', 'RELEASED')),
    expires_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    resolved_at timestamptz,
    CHECK ((status = 'HELD') = (resolved_at IS NULL))
);

ALTER TABLE orders.stock_movement ADD FOREIGN KEY (order_item_id) REFERENCES orders.order_item;

CREATE TABLE orders.idempotency_record (
    actor_user_id uuid NOT NULL,
    operation_context varchar(20) NOT NULL,
    endpoint varchar(100) NOT NULL,
    idempotency_key varchar(128) NOT NULL,
    request_hash varchar(128) NOT NULL,
    result_ref uuid NOT NULL REFERENCES orders.checkout_session,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (actor_user_id, operation_context, endpoint, idempotency_key)
);

CREATE TABLE orders.checkout_outbox (
    command_id uuid PRIMARY KEY,
    checkout_session_id uuid NOT NULL REFERENCES orders.checkout_session,
    command_type varchar(40) NOT NULL CHECK (command_type IN ('CreateProviderSession', 'ExpireCheckout')),
    expires_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    UNIQUE (checkout_session_id, command_type)
);
