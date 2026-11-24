CREATE TABLE address_deletion_receipts (
    address_id UUID PRIMARY KEY,
    customer_id UUID NOT NULL
);

INSERT INTO address_deletion_receipts (address_id, customer_id)
SELECT address_id, customer_id FROM customer_addresses WHERE deleted_at IS NOT NULL;
