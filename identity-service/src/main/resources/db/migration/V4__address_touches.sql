-- Order marks an address as used after a checkout commits (API §7.1 POST .../addresses/{id}/touch). Retries with the
-- same checkout session must have no extra effect, so each (address, checkout session) pair is recorded once.
CREATE TABLE address_touches (
    address_id          UUID         NOT NULL REFERENCES customer_addresses (address_id) ON DELETE CASCADE,
    checkout_session_id VARCHAR(100) NOT NULL,
    touched_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (address_id, checkout_session_id),
    CONSTRAINT ck_address_touches_checkout_session_id CHECK (btrim(checkout_session_id) <> '')
);
