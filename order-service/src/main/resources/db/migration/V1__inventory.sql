CREATE TABLE orders.inventory (
    product_id uuid PRIMARY KEY,
    on_hand_quantity integer NOT NULL,
    reserved_quantity integer NOT NULL DEFAULT 0,
    row_version bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT inventory_quantities_valid
        CHECK (on_hand_quantity >= reserved_quantity AND reserved_quantity >= 0)
);
