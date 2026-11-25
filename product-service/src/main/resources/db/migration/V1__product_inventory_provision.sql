CREATE TABLE product_inventory_provision (
    product_id uuid PRIMARY KEY,
    seller_id uuid NOT NULL,
    request_key varchar(128) NOT NULL,
    request_hash char(64) NOT NULL,
    quantity integer NOT NULL CHECK (quantity >= 0),
    target_status varchar(16) NOT NULL CHECK (target_status IN ('SELLING', 'HIDDEN')),
    next_attempt_at timestamptz,
    lease_id uuid,
    lease_until timestamptz,
    UNIQUE (seller_id, request_key),
    CHECK ((lease_id IS NULL) = (lease_until IS NULL))
);

CREATE INDEX product_inventory_provision_due ON product_inventory_provision (next_attempt_at)
WHERE next_attempt_at IS NOT NULL;
