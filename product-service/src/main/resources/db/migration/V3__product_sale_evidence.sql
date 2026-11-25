CREATE TABLE product_sale_evidence (
    evidence_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id uuid NOT NULL,
    seller_id uuid NOT NULL,
    effective_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    status varchar(16) NOT NULL
);

CREATE INDEX ix_product_sale_evidence_lookup
    ON product_sale_evidence (product_id, effective_at DESC, evidence_id DESC);

-- An existing catalog is known starting at migration time. Earlier history remains unknown.
DO $$
BEGIN
    IF to_regclass('products') IS NOT NULL THEN
        INSERT INTO product_sale_evidence (product_id, seller_id, status)
        SELECT product_id, seller_id, status FROM products;
    END IF;
END
$$;
