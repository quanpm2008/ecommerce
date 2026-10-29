-- Identity schema (DB §3.1 and §6). Enums are VARCHAR + CHECK so they map to Java enums stored as strings.
-- Phone numbers are stored normalized as 0xxxxxxxxx (API §6.1); the application normalizes before writing.

-- 3.1.1 User: the only identity and credential store; Customer and Seller share its PK.
CREATE TABLE users (
    user_id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(254) NOT NULL,
    auth_method     VARCHAR(10)  NOT NULL,
    password_hash   TEXT,
    google_subject  VARCHAR(255),
    full_name       VARCHAR(150) NOT NULL,
    phone_number    VARCHAR(20),
    status          VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    disabled_reason TEXT,
    authz_version   BIGINT       NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT uq_users_phone_number UNIQUE (phone_number),
    CONSTRAINT uq_users_google_subject UNIQUE (google_subject),
    CONSTRAINT ck_users_email_normalized CHECK (email = lower(btrim(email)) AND email <> ''),
    CONSTRAINT ck_users_auth_method CHECK (
        (auth_method = 'LOCAL' AND password_hash IS NOT NULL AND google_subject IS NULL)
        OR (auth_method = 'GOOGLE' AND password_hash IS NULL AND google_subject IS NOT NULL)),
    CONSTRAINT ck_users_phone_number CHECK (phone_number ~ '^0[0-9]{9}$'),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_users_disabled_reason CHECK ((status = 'DISABLED') = (disabled_reason IS NOT NULL)),
    CONSTRAINT ck_users_authz_version CHECK (authz_version >= 1)
);

-- 3.1.2 Customer. ban_mode / has_paid_order_before_ban are the G-14 policy snapshot taken at ban time (API §10.4).
CREATE TABLE customers (
    customer_id               UUID        PRIMARY KEY REFERENCES users (user_id) ON DELETE RESTRICT,
    status                    VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    ban_reason                TEXT,
    ban_mode                  VARCHAR(20),
    has_paid_order_before_ban BOOLEAN,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_customers_status CHECK (status IN ('ACTIVE', 'BANNED')),
    CONSTRAINT ck_customers_ban_mode CHECK (ban_mode IN ('PAYMENT_ONLY', 'CUSTOMER_SCOPE')),
    CONSTRAINT ck_customers_ban_fields CHECK (
        (status = 'BANNED') = (ban_reason IS NOT NULL)
        AND (status = 'BANNED') = (ban_mode IS NOT NULL)
        AND (status = 'BANNED') = (has_paid_order_before_ban IS NOT NULL))
);

-- 3.1.3 Seller. reviewed_* are set once an admin approves or rejects and cleared on resubmission.
CREATE TABLE sellers (
    seller_id           UUID         PRIMARY KEY REFERENCES users (user_id) ON DELETE RESTRICT,
    seller_name         VARCHAR(160) NOT NULL,
    description         TEXT,
    logo_object_key     TEXT,
    contact_phone       VARCHAR(20)  NOT NULL,
    status              VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    ban_reason          TEXT,
    approval_status     VARCHAR(10)  NOT NULL DEFAULT 'PENDING',
    rejection_reason    TEXT,
    registered_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    reviewed_at         TIMESTAMPTZ,
    reviewed_by_user_id UUID         REFERENCES users (user_id) ON DELETE RESTRICT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_sellers_seller_name CHECK (btrim(seller_name) <> ''),
    CONSTRAINT ck_sellers_contact_phone CHECK (contact_phone ~ '^0[0-9]{9}$'),
    CONSTRAINT ck_sellers_status CHECK (status IN ('ACTIVE', 'BANNED')),
    CONSTRAINT ck_sellers_ban_reason CHECK ((status = 'BANNED') = (ban_reason IS NOT NULL)),
    CONSTRAINT ck_sellers_approval_status CHECK (approval_status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_sellers_rejection_reason CHECK ((approval_status = 'REJECTED') = (rejection_reason IS NOT NULL)),
    CONSTRAINT ck_sellers_review CHECK (
        (approval_status = 'PENDING') = (reviewed_at IS NULL)
        AND (reviewed_at IS NULL) = (reviewed_by_user_id IS NULL)),
    CONSTRAINT ck_sellers_no_self_review CHECK (reviewed_by_user_id <> seller_id)
);

-- Seller name and contact phone are unique only among PENDING/APPROVED sellers, so a REJECTED seller frees them.
CREATE UNIQUE INDEX uq_sellers_seller_name_active
    ON sellers (lower(regexp_replace(btrim(seller_name), '\s+', ' ', 'g')))
    WHERE approval_status IN ('PENDING', 'APPROVED');
CREATE UNIQUE INDEX uq_sellers_contact_phone_active
    ON sellers (contact_phone)
    WHERE approval_status IN ('PENDING', 'APPROVED');

-- 3.1.4 CustomerAddress. Deleting an address referenced by an order is a soft delete (G-13); the default
-- address is derived (latest last_used_at, else earliest created_at), not stored.
CREATE TABLE customer_addresses (
    address_id      UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id     UUID         NOT NULL REFERENCES customers (customer_id) ON DELETE RESTRICT,
    recipient_name  VARCHAR(150) NOT NULL,
    recipient_phone VARCHAR(20)  NOT NULL,
    province_code   VARCHAR(32)  NOT NULL,
    ward_code       VARCHAR(32)  NOT NULL,
    address_detail  VARCHAR(300) NOT NULL,
    last_used_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT ck_customer_addresses_recipient_phone CHECK (recipient_phone ~ '^0[0-9]{9}$')
);
CREATE INDEX ix_customer_addresses_customer ON customer_addresses (customer_id) WHERE deleted_at IS NULL;

-- 3.1.5–3.1.8 RBAC. Roles and permissions are seeded by migrations only; there is no CRUD API.
CREATE TABLE roles (
    role_id     UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_roles_code UNIQUE (code)
);

CREATE TABLE permissions (
    permission_id UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code          VARCHAR(100) NOT NULL,
    description   TEXT         NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_permissions_code UNIQUE (code)
);

-- A revoked role keeps its row (revoked_at set); granting it again updates the same row.
CREATE TABLE user_roles (
    user_id            UUID        NOT NULL REFERENCES users (user_id) ON DELETE RESTRICT,
    role_id            UUID        NOT NULL REFERENCES roles (role_id) ON DELETE RESTRICT,
    granted_by_user_id UUID        REFERENCES users (user_id) ON DELETE RESTRICT,
    granted_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at         TIMESTAMPTZ,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE role_permissions (
    role_id       UUID        NOT NULL REFERENCES roles (role_id) ON DELETE RESTRICT,
    permission_id UUID        NOT NULL REFERENCES permissions (permission_id) ON DELETE RESTRICT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (role_id, permission_id)
);

-- §6 technical tables.
CREATE TABLE auth_sessions (
    session_id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID         NOT NULL REFERENCES users (user_id) ON DELETE RESTRICT,
    refresh_token_hash VARCHAR(128) NOT NULL,
    expires_at         TIMESTAMPTZ  NOT NULL,
    revoked_at         TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_auth_sessions_refresh_token_hash UNIQUE (refresh_token_hash)
);
CREATE INDEX ix_auth_sessions_user ON auth_sessions (user_id) WHERE revoked_at IS NULL;

CREATE TABLE password_reset_tokens (
    token_id   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL REFERENCES users (user_id) ON DELETE RESTRICT,
    token_hash VARCHAR(128) NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_password_reset_tokens_token_hash UNIQUE (token_hash)
);

-- actor_user_id is NULL only for system actions such as the admin bootstrap. Never store tokens or passwords here.
CREATE TABLE admin_audit_log (
    audit_id      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id UUID        REFERENCES users (user_id) ON DELETE RESTRICT,
    action        VARCHAR(30) NOT NULL,
    target_type   VARCHAR(30) NOT NULL,
    target_id     UUID        NOT NULL,
    reason        TEXT,
    before_after  JSONB,
    request_id    VARCHAR(128),
    happened_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_admin_audit_log_action CHECK (action IN
        ('APPROVE', 'REJECT', 'BAN', 'UNBAN', 'DISABLE', 'ENABLE', 'GRANT_ROLE', 'REVOKE_ROLE'))
);
CREATE INDEX ix_admin_audit_log_target ON admin_audit_log (target_type, target_id, happened_at);

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
