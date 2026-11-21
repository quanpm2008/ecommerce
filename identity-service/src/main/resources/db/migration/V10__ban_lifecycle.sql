-- G-14 / UC-E03 / Thẻ 15 (DB v5.1 §3.1.1, API v1.1 §3.2, §6.1 ban/unban, §7.3 J7): one ban lifecycle per account, no
-- scope. accountStatus is derived from the pair (users.status, profile status):
--   ACTIVE   = ACTIVE   + ACTIVE
--   BANNED   = ACTIVE   + BANNED   (open obligations: a limited session, accessMode says which)
--   DISABLED = DISABLED + BANNED   (every obligation verified gone: no sign-in)
-- banned_at starts the ban episode; disabled_at is the first DISABLED of the episode and restore_until = disabled_at +
-- 30 days (UTC), the last moment an admin may lift it. Repeated bans and J7 keep the episode's timestamps.

ALTER TABLE users ADD COLUMN banned_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN disabled_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN restore_until TIMESTAMPTZ;
-- Last evaluated access mode (API §4.2); FULL unless banned. Re-evaluated at ban, login, refresh and by J7.
ALTER TABLE users ADD COLUMN access_mode VARCHAR(32) NOT NULL DEFAULT 'FULL';
ALTER TABLE users ADD CONSTRAINT ck_users_access_mode CHECK (access_mode IN ('FULL', 'OPEN_ORDERS_ONLY',
    'ORDERS_AND_WITHDRAWAL', 'WITHDRAWAL_ONLY', 'OBLIGATIONS_READ_ONLY', 'LOGIN_BLOCKED'));

-- The old model's states become valid pairs. A user disabled through the old USER scope gets its profile banned with
-- the same reason (or "Khác: <reason>" when the old text is no P-21d label); profile bans start their episode at the
-- last change of the profile; a disabled account's restore window starts at the last change of the user.
UPDATE customers c SET status = 'BANNED', ban_reason = u.disabled_reason
FROM users u WHERE u.user_id = c.customer_id AND u.status = 'DISABLED' AND c.status = 'ACTIVE';
UPDATE sellers s SET status = 'BANNED', ban_reason = u.disabled_reason
FROM users u WHERE u.user_id = s.seller_id AND u.status = 'DISABLED' AND s.status = 'ACTIVE'
  AND s.approval_status = 'APPROVED';
-- A disabled seller that was never approved cannot be banned in the new model: it simply cannot sign in anyway.
UPDATE users u SET status = 'ACTIVE', disabled_reason = NULL, authz_version = authz_version + 1
FROM sellers s WHERE s.seller_id = u.user_id AND u.status = 'DISABLED' AND s.approval_status <> 'APPROVED';

UPDATE users u SET banned_at = coalesce(c.updated_at, now())
FROM customers c WHERE c.customer_id = u.user_id AND c.status = 'BANNED';
UPDATE users u SET banned_at = coalesce(s.updated_at, now())
FROM sellers s WHERE s.seller_id = u.user_id AND s.status = 'BANNED';
UPDATE users SET disabled_at = coalesce(updated_at, now()), restore_until = coalesce(updated_at, now()) + interval '30 days',
    access_mode = 'LOGIN_BLOCKED'
WHERE status = 'DISABLED' AND banned_at IS NOT NULL;
-- Banned but still signed in: the obligations are not known here; J7 / the next sign-in evaluates them. Until then
-- the account has the narrowest limited session.
UPDATE users SET access_mode = 'OBLIGATIONS_READ_ONLY' WHERE status = 'ACTIVE' AND banned_at IS NOT NULL;

-- The G-14 policy snapshot of the old model goes: obligations are always read again from Order and Finance.
ALTER TABLE customers DROP CONSTRAINT ck_customers_ban_fields;
ALTER TABLE customers DROP CONSTRAINT ck_customers_ban_mode;
ALTER TABLE customers DROP COLUMN ban_mode;
ALTER TABLE customers DROP COLUMN has_paid_order_before_ban;
ALTER TABLE customers ADD CONSTRAINT ck_customers_ban_reason CHECK ((status = 'BANNED') = (ban_reason IS NOT NULL));

ALTER TABLE users ADD CONSTRAINT ck_users_ban_timestamps CHECK (
    (disabled_at IS NULL OR banned_at IS NOT NULL)
    AND (disabled_at IS NULL) = (restore_until IS NULL)
    AND (restore_until IS NULL OR restore_until = disabled_at + interval '30 days')
    AND (status <> 'DISABLED' OR disabled_at IS NOT NULL OR account_type = 'ADMIN'));

UPDATE users SET authz_version = authz_version + 1 WHERE banned_at IS NOT NULL;
