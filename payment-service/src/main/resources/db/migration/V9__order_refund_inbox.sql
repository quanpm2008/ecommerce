CREATE TABLE payment_paid_checkout (
    checkout_session_id uuid PRIMARY KEY REFERENCES payment_checkout_guard(checkout_session_id),
    event_id uuid NOT NULL UNIQUE,
    payment_id uuid NOT NULL UNIQUE REFERENCES payments(payment_id),
    customer_id uuid NOT NULL,
    orders jsonb NOT NULL CHECK (jsonb_typeof(orders) = 'object' AND orders <> '{}'::jsonb)
);

CREATE TABLE payment_order_refund_inbox (
    event_id uuid PRIMARY KEY,
    checkout_session_id uuid NOT NULL REFERENCES payment_checkout_guard(checkout_session_id),
    order_id uuid NOT NULL,
    customer_id uuid NOT NULL,
    seller_id uuid NOT NULL,
    amount_vnd bigint NOT NULL CHECK (amount_vnd > 0),
    reason varchar(50) NOT NULL CHECK (reason IN ('CUSTOMER_CANCEL', 'SELLER_REJECT', 'SELLER_CANCEL', 'SELLER_TIMEOUT')),
    status varchar(24) NOT NULL CHECK (status IN ('WAITING_DEPENDENCY', 'APPLIED', 'FAILED_REVIEW')),
    received_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX payment_order_refund_inbox_waiting ON payment_order_refund_inbox(checkout_session_id, received_at, event_id)
    WHERE status = 'WAITING_DEPENDENCY';
