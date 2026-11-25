ALTER TABLE orders.inventory ADD COLUMN seller_id uuid NOT NULL;

CREATE TABLE orders.stock_movement (
    stock_movement_id uuid PRIMARY KEY,
    product_id uuid NOT NULL REFERENCES orders.inventory (product_id),
    movement_type varchar(20) NOT NULL CHECK (movement_type IN ('IMPORT', 'MANUAL_ADJUST', 'SOLD', 'RESTOCK')),
    quantity_delta integer NOT NULL CHECK (quantity_delta <> 0),
    order_item_id uuid,
    actor_id uuid,
    business_event_key varchar(128) NOT NULL UNIQUE,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actor_type varchar(10) NOT NULL CHECK (actor_type IN ('USER', 'SYSTEM')),
    note text,
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    CHECK ((actor_type = 'USER' AND actor_id IS NOT NULL) OR (actor_type = 'SYSTEM' AND actor_id IS NULL))
);
