create table orders.checkout_session (
    checkout_session_id uuid primary key,
    checkout_intent_id uuid not null,
    customer_id uuid not null,
    checkout_source varchar(16) not null check (checkout_source in ('BUY_NOW', 'CART')),
    source_cart_id uuid,
    cart_snapshot jsonb,
    idempotency_key varchar(128) not null,
    request_hash varchar(128) not null,
    total_amount_vnd bigint not null check (total_amount_vnd > 0),
    currency char(3) not null check (currency = 'VND'),
    status varchar(24) not null check (status in ('AWAITING_PAYMENT', 'PAID', 'CLOSED')),
    payment_id uuid unique,
    close_reason varchar(24) check (close_reason in ('PAYMENT_FAILED', 'EXPIRED')),
    address_id uuid not null,
    paid_at timestamptz,
    needs_manual_review boolean not null default false,
    manual_note text,
    created_at timestamptz not null,
    expires_at timestamptz not null,
    unique (customer_id, checkout_intent_id),
    unique (customer_id, idempotency_key),
    check ((checkout_source = 'CART') = (source_cart_id is not null and cart_snapshot is not null)),
    check (checkout_source <> 'BUY_NOW' or (source_cart_id is null and cart_snapshot is null)),
    check ((status = 'CLOSED') = (close_reason is not null)),
    check (expires_at = created_at + interval '15 minutes')
);

create unique index checkout_session_one_pending_per_customer
    on orders.checkout_session (customer_id) where status = 'AWAITING_PAYMENT';
