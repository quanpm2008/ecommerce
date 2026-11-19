-- Payment schema
-- Enums are stored as VARCHAR + CHECK constraints to cleanly map to Java enums.

CREATE SCHEMA IF NOT EXISTS payment_service;

-- 3.4.1 Payments: một Payment logic trên một CheckoutSession
CREATE TABLE payments (
    payment_id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    checkout_session_id     UUID         NOT NULL,
    customer_id             UUID         NOT NULL,
    amount_vnd              BIGINT       NOT NULL,
    currency                VARCHAR(3)      NOT NULL DEFAULT 'VND',
    provider                VARCHAR(50)  NOT NULL DEFAULT 'SIMULATOR',
    environment             VARCHAR(50)  NOT NULL DEFAULT 'DEV',
    idempotency_key         VARCHAR(128) NOT NULL,
    status                  VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    provider_transaction_id VARCHAR(255),
    error_code              VARCHAR(100),
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at            TIMESTAMPTZ,

    CONSTRAINT uq_payments_checkout_session_id UNIQUE (checkout_session_id),
    CONSTRAINT uq_payments_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_payments_amount_vnd CHECK (amount_vnd > 0),
    CONSTRAINT ck_payments_currency CHECK (currency = 'VND'),
    CONSTRAINT ck_payments_status CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED')),
    CONSTRAINT ck_payments_completed CHECK ((status IN ('SUCCESS', 'FAILED')) = (completed_at IS NOT NULL))
);

CREATE UNIQUE INDEX uq_payments_provider_transaction_id
    ON payments (provider_transaction_id)
    WHERE provider_transaction_id IS NOT NULL;

CREATE INDEX ix_payments_customer_id ON payments (customer_id);
CREATE INDEX ix_payments_status ON payments (status);

-- 3.4.2 Refunds: hoàn tiền theo Order hoặc toàn bộ checkout khi thanh toán muộn
CREATE TABLE refunds (
    refund_id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id           UUID         NOT NULL REFERENCES payments (payment_id) ON DELETE RESTRICT,
    order_id             UUID,
    scope                VARCHAR(20)  NOT NULL,
    amount_vnd           BIGINT       NOT NULL,
    reason               VARCHAR(50)  NOT NULL,
    status               VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    idempotency_key      VARCHAR(128) NOT NULL,
    provider_refund_id   VARCHAR(255),
    retry_count          INTEGER      NOT NULL DEFAULT 0,
    needs_manual_review  BOOLEAN      NOT NULL DEFAULT FALSE,
    manual_note          TEXT,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at         TIMESTAMPTZ,

    CONSTRAINT uq_refunds_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_refunds_scope CHECK (scope IN ('ORDER', 'FULL_CHECKOUT')),
    CONSTRAINT ck_refunds_scope_order CHECK (
        (scope = 'ORDER' AND order_id IS NOT NULL)
        OR (scope = 'FULL_CHECKOUT' AND order_id IS NULL)
    ),
    CONSTRAINT ck_refunds_amount_vnd CHECK (amount_vnd > 0),
    CONSTRAINT ck_refunds_reason CHECK (reason IN (
        'CUSTOMER_CANCEL', 'SELLER_REJECT', 'SELLER_CANCEL', 'SELLER_TIMEOUT', 'LATE_PAYMENT'
    )),
    CONSTRAINT ck_refunds_status CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED')),
    CONSTRAINT ck_refunds_retry_count CHECK (retry_count >= 0),
    CONSTRAINT ck_refunds_completed CHECK ((status IN ('SUCCESS', 'FAILED')) = (completed_at IS NOT NULL))
);

-- Tối đa một nghĩa vụ hoàn tiền trên mỗi Order bị hủy
CREATE UNIQUE INDEX uq_refunds_order_id
    ON refunds (order_id)
    WHERE scope = 'ORDER';

CREATE UNIQUE INDEX uq_refunds_provider_refund_id
    ON refunds (provider_refund_id)
    WHERE provider_refund_id IS NOT NULL;

CREATE INDEX ix_refunds_payment_id ON refunds (payment_id);
CREATE INDEX ix_refunds_status ON refunds (status);
CREATE INDEX ix_refunds_manual_review ON refunds (needs_manual_review) WHERE needs_manual_review = TRUE;

-- Transactional Outbox pattern cho payment-service
CREATE TABLE outbox_events (
    event_id     UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type   VARCHAR(100) NOT NULL,
    aggregate_id UUID         NOT NULL,
    payload      JSONB        NOT NULL,
    status       VARCHAR(10)  NOT NULL DEFAULT 'PENDING',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ,

    CONSTRAINT ck_outbox_events_status CHECK (status IN ('PENDING', 'PUBLISHED')),
    CONSTRAINT ck_outbox_events_published CHECK ((status = 'PUBLISHED') = (published_at IS NOT NULL))
);

CREATE INDEX ix_outbox_events_pending ON outbox_events (created_at) WHERE status = 'PENDING';
