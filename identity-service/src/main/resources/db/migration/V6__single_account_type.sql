-- API v1.1 §3.2.1, Database v5.1 §3.1.1/§3.1.7: every user is one independent account of a fixed type, CUSTOMER,
-- SELLER or ADMIN, with at most one active role, and that role matches the type. Customer and Seller profiles never
-- share a user id, and ADMIN has neither. A person with several roles uses several accounts (own email, sessions).

-- 1. The old model let one user hold several profiles and roles. Such rows cannot be split automatically (DB §5.8
--    wants the users' own new emails), so stop and say so; on a dev database, reset it and start again.
DO $$
DECLARE
    both_profiles  BIGINT;
    admin_profiles BIGINT;
    several_roles  BIGINT;
    no_profile     BIGINT;
BEGIN
    SELECT count(*) INTO both_profiles FROM customers c JOIN sellers s ON s.seller_id = c.customer_id;
    SELECT count(DISTINCT ur.user_id) INTO admin_profiles
    FROM user_roles ur JOIN roles r ON r.role_id = ur.role_id
    WHERE r.code = 'ADMIN' AND ur.revoked_at IS NULL
      AND (EXISTS (SELECT 1 FROM customers c WHERE c.customer_id = ur.user_id)
           OR EXISTS (SELECT 1 FROM sellers s WHERE s.seller_id = ur.user_id));
    SELECT count(*) INTO several_roles
    FROM (SELECT user_id FROM user_roles WHERE revoked_at IS NULL GROUP BY user_id HAVING count(*) > 1) multi;
    SELECT count(*) INTO no_profile FROM users u
    WHERE NOT EXISTS (SELECT 1 FROM customers c WHERE c.customer_id = u.user_id)
      AND NOT EXISTS (SELECT 1 FROM sellers s WHERE s.seller_id = u.user_id)
      AND NOT EXISTS (SELECT 1 FROM user_roles ur JOIN roles r ON r.role_id = ur.role_id
                      WHERE ur.user_id = u.user_id AND r.code = 'ADMIN' AND ur.revoked_at IS NULL);
    IF both_profiles + admin_profiles + several_roles + no_profile > 0 THEN
        RAISE EXCEPTION 'V6 single account type: % user(s) with both a Customer and a Seller profile, % ADMIN(s) '
            'with a profile, % user(s) with several active roles, % user(s) with no profile and no ADMIN role. The '
            'one-account-per-role model cannot convert them automatically. On a dev database run '
            '"docker compose -p ecommerce down -v" and start again.',
            both_profiles, admin_profiles, several_roles, no_profile;
    END IF;
END $$;

-- 2. The account type, fixed at creation.
ALTER TABLE users ADD COLUMN account_type VARCHAR(16);
UPDATE users u SET account_type = CASE
    WHEN EXISTS (SELECT 1 FROM sellers s WHERE s.seller_id = u.user_id) THEN 'SELLER'
    WHEN EXISTS (SELECT 1 FROM customers c WHERE c.customer_id = u.user_id) THEN 'CUSTOMER'
    ELSE 'ADMIN' END;
ALTER TABLE users ALTER COLUMN account_type SET NOT NULL;
ALTER TABLE users ADD CONSTRAINT ck_users_account_type CHECK (account_type IN ('CUSTOMER', 'SELLER', 'ADMIN'));
-- Google sign-in is for customers only (DB §3.1.1).
ALTER TABLE users ADD CONSTRAINT ck_users_google_customer CHECK (auth_method <> 'GOOGLE' OR account_type = 'CUSTOMER');

CREATE FUNCTION identity_account_type_unchanged() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.account_type <> OLD.account_type THEN
        RAISE EXCEPTION 'users.account_type cannot change (user %)', OLD.user_id USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_users_account_type_unchanged BEFORE UPDATE OF account_type ON users
    FOR EACH ROW EXECUTE FUNCTION identity_account_type_unchanged();

-- 3. A profile belongs only to a user of its own type. (One function per table: PL/pgSQL resolves NEW.<column> even
--    in a branch that is not taken, so a shared function cannot name both key columns.)
CREATE FUNCTION identity_customer_matches_account_type() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    actual TEXT;
BEGIN
    SELECT account_type INTO actual FROM users WHERE user_id = NEW.customer_id;
    IF actual IS DISTINCT FROM 'CUSTOMER' THEN
        RAISE EXCEPTION 'A customer profile needs a CUSTOMER account, not %', coalesce(actual, 'none')
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_customers_account_type BEFORE INSERT OR UPDATE OF customer_id ON customers
    FOR EACH ROW EXECUTE FUNCTION identity_customer_matches_account_type();

CREATE FUNCTION identity_seller_matches_account_type() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    actual TEXT;
BEGIN
    SELECT account_type INTO actual FROM users WHERE user_id = NEW.seller_id;
    IF actual IS DISTINCT FROM 'SELLER' THEN
        RAISE EXCEPTION 'A seller profile needs a SELLER account, not %', coalesce(actual, 'none')
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_sellers_account_type BEFORE INSERT OR UPDATE OF seller_id ON sellers
    FOR EACH ROW EXECUTE FUNCTION identity_seller_matches_account_type();

-- 4. At most one active role per user, and it is the account's own (role code = account type).
CREATE UNIQUE INDEX uq_user_roles_one_active ON user_roles (user_id) WHERE revoked_at IS NULL;

CREATE FUNCTION identity_role_matches_account_type() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    role_code TEXT;
    actual    TEXT;
BEGIN
    IF NEW.revoked_at IS NOT NULL THEN
        RETURN NEW;
    END IF;
    SELECT code INTO role_code FROM roles WHERE role_id = NEW.role_id;
    SELECT account_type INTO actual FROM users WHERE user_id = NEW.user_id;
    IF role_code IS DISTINCT FROM actual THEN
        RAISE EXCEPTION 'Role % does not match account type %', role_code, actual USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_user_roles_account_type BEFORE INSERT OR UPDATE ON user_roles
    FOR EACH ROW EXECUTE FUNCTION identity_role_matches_account_type();

-- 5. Permission seed of API v1.1 §3.2.1. The baseline policy is account:read:own / account:update:own only:
--    seller:application:own goes (new sellers apply through public registration and email verification). ADMIN reads
--    refunds but never retries them (refund:retry goes); CUSTOMER retries its own (refund:retry:own); ADMIN gains
--    operations:annotate (reconciliation notes).
DELETE FROM role_permissions
WHERE permission_id IN (SELECT permission_id FROM permissions WHERE code IN ('refund:retry', 'seller:application:own'));
DELETE FROM permissions WHERE code IN ('refund:retry', 'seller:application:own');

INSERT INTO permissions (code, description) VALUES
    ('refund:retry:own', 'Open a new retry cycle for an own failed refund when P-RF-01 allows it'),
    ('operations:annotate', 'Add reconciliation notes to collections; never change balances or confirm by hand');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM (VALUES ('CUSTOMER', 'refund:retry:own'), ('ADMIN', 'operations:annotate')) AS m (role_code, permission_code)
JOIN roles r ON r.code = m.role_code
JOIN permissions p ON p.code = m.permission_code;

-- 6. Cut-over (DB §5.8): permissions changed and logins now name the account type, so every token issued before this
--    migration is stale and every session ends; everyone signs in again.
UPDATE users SET authz_version = authz_version + 1;
UPDATE auth_sessions SET revoked_at = now() WHERE revoked_at IS NULL;
