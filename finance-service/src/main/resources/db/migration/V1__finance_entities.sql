CREATE TABLE settlement (
    settlement_id uuid PRIMARY KEY,
    order_id uuid NOT NULL UNIQUE,
    payment_id uuid NOT NULL,
    seller_id uuid NOT NULL,
    gross_amount_vnd bigint NOT NULL CHECK (gross_amount_vnd > 0),
    commission_bps_snapshot integer NOT NULL CHECK (commission_bps_snapshot BETWEEN 0 AND 10000),
    commission_amount_vnd bigint NOT NULL CHECK (commission_amount_vnd >= 0),
    seller_amount_vnd bigint NOT NULL CHECK (seller_amount_vnd >= 0),
    status varchar(20) NOT NULL CHECK (status IN ('PENDING', 'PENDING_REFUND', 'SETTLED', 'REFUNDED')),
    created_at timestamptz NOT NULL,
    settled_at timestamptz,
    CHECK (commission_amount_vnd = floor((gross_amount_vnd::numeric * commission_bps_snapshot + 5000) / 10000)),
    CHECK (seller_amount_vnd = gross_amount_vnd - commission_amount_vnd),
    CHECK ((status = 'SETTLED') = (settled_at IS NOT NULL))
);

CREATE TABLE wallet (
    wallet_id uuid PRIMARY KEY,
    owner_type varchar(12) NOT NULL CHECK (owner_type IN ('SELLER', 'PLATFORM')),
    owner_id uuid,
    currency char(3) NOT NULL CHECK (currency = 'VND'),
    pending_balance_vnd bigint NOT NULL CHECK (pending_balance_vnd >= 0),
    available_balance_vnd bigint NOT NULL CHECK (available_balance_vnd >= 0),
    total_withdrawn_vnd bigint NOT NULL CHECK (total_withdrawn_vnd >= 0),
    row_version bigint NOT NULL CHECK (row_version >= 0),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CHECK ((owner_type = 'SELLER' AND owner_id IS NOT NULL)
        OR (owner_type = 'PLATFORM' AND owner_id IS NULL AND total_withdrawn_vnd = 0))
);

CREATE UNIQUE INDEX wallet_seller_currency_uq ON wallet (owner_id, currency) WHERE owner_type = 'SELLER';
CREATE UNIQUE INDEX wallet_platform_currency_uq ON wallet (currency) WHERE owner_type = 'PLATFORM';

INSERT INTO wallet (wallet_id, owner_type, currency, pending_balance_vnd, available_balance_vnd,
    total_withdrawn_vnd, row_version, created_at, updated_at)
VALUES (gen_random_uuid(), 'PLATFORM', 'VND', 0, 0, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

CREATE TABLE withdrawal (
    withdrawal_id uuid PRIMARY KEY,
    withdrawal_code varchar(32) NOT NULL UNIQUE,
    wallet_id uuid NOT NULL REFERENCES wallet (wallet_id),
    seller_id uuid NOT NULL,
    amount_vnd bigint NOT NULL CHECK (amount_vnd > 0),
    available_before_vnd bigint NOT NULL CHECK (available_before_vnd >= 0),
    available_after_vnd bigint NOT NULL CHECK (available_after_vnd >= 0),
    destination varchar(100) NOT NULL,
    idempotency_key varchar(128) NOT NULL,
    request_hash varchar(128) NOT NULL,
    withdrawn_at timestamptz NOT NULL,
    UNIQUE (seller_id, idempotency_key),
    CHECK (available_after_vnd = available_before_vnd - amount_vnd)
);

CREATE TABLE wallet_transaction (
    wallet_transaction_id uuid PRIMARY KEY,
    wallet_id uuid NOT NULL REFERENCES wallet (wallet_id),
    settlement_id uuid REFERENCES settlement (settlement_id),
    withdrawal_id uuid REFERENCES withdrawal (withdrawal_id),
    reference_type varchar(12) NOT NULL CHECK (reference_type IN ('PAYMENT', 'ORDER', 'REFUND', 'WITHDRAWAL')),
    reference_id uuid NOT NULL,
    transaction_type varchar(24) NOT NULL CHECK (transaction_type IN
        ('PAYMENT_RECEIVED', 'SETTLEMENT_RELEASED', 'REFUND_DEBIT', 'WITHDRAWAL_COMPLETED')),
    pending_delta_vnd bigint NOT NULL,
    available_delta_vnd bigint NOT NULL,
    withdrawn_delta_vnd bigint NOT NULL,
    business_event_key varchar(160) NOT NULL,
    created_at timestamptz NOT NULL,
    UNIQUE (wallet_id, business_event_key),
    CHECK ((transaction_type = 'WITHDRAWAL_COMPLETED' AND settlement_id IS NULL
                AND withdrawal_id IS NOT NULL AND withdrawn_delta_vnd > 0)
        OR (transaction_type <> 'WITHDRAWAL_COMPLETED' AND settlement_id IS NOT NULL
                AND withdrawal_id IS NULL AND withdrawn_delta_vnd = 0))
);
