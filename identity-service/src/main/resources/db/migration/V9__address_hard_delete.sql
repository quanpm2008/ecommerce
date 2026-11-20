-- P-09 / G-13 (DB v5.1 §3.1.4, API v1.1 §6.1): deleting an address removes it; an order keeps its own snapshot, so
-- nothing depends on the row. Addresses hidden by the old soft delete go now (their touches cascade), and the
-- book holds at most 3 addresses (configuration identity.address.max-per-customer).
DELETE FROM customer_addresses WHERE deleted_at IS NOT NULL;
DROP INDEX ix_customer_addresses_customer;
ALTER TABLE customer_addresses DROP COLUMN deleted_at;
CREATE INDEX ix_customer_addresses_customer ON customer_addresses (customer_id);
