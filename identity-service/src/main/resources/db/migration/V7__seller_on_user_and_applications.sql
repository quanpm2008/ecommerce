-- Database v5.1 §3.1.1/§3.1.3, API v1.1 §6.1: a seller account's shop name is users.full_name, its contact phone is
-- users.phone_number and its logo is users.avatar_object_key (the account's avatar). Every submission of a seller
-- application is its own immutable SellerApplication row; a REJECTED seller applies again with a new row after
-- proving the email (seller_reapplication_tokens), never by editing the old one.

-- 1. The account's avatar (for a seller, the shop logo).
ALTER TABLE users ADD COLUMN avatar_object_key TEXT;

-- 2. Move shop name, contact phone and logo onto the seller's account. A name longer than users.full_name allows, or a
--    contact phone another account already uses, cannot be moved silently: stop and say so (dev: reset the database).
DO $$
DECLARE
    long_names     BIGINT;
    taken_phones   BIGINT;
BEGIN
    SELECT count(*) INTO long_names FROM sellers WHERE length(btrim(seller_name)) > 150;
    -- users.phone_number is unique across all accounts; a REJECTED seller could share a contact phone before.
    SELECT count(*) INTO taken_phones FROM sellers s
    WHERE EXISTS (SELECT 1 FROM users other
                  WHERE other.phone_number = s.contact_phone AND other.user_id <> s.seller_id)
       OR EXISTS (SELECT 1 FROM sellers twin
                  WHERE twin.contact_phone = s.contact_phone AND twin.seller_id <> s.seller_id);
    IF long_names + taken_phones > 0 THEN
        RAISE EXCEPTION 'V7 seller data: % shop name(s) longer than 150 characters, % contact phone(s) already used by '
            'another account. On a dev database run "docker compose -p ecommerce down -v" and start again.',
            long_names, taken_phones;
    END IF;
END $$;

UPDATE users u
SET full_name = btrim(s.seller_name),
    phone_number = s.contact_phone,
    avatar_object_key = s.logo_object_key
FROM sellers s
WHERE s.seller_id = u.user_id;

-- 3. Application history. One row per submission; only the review fields of a PENDING row ever change.
CREATE TABLE seller_applications (
    application_id      UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    seller_id           UUID         NOT NULL REFERENCES sellers (seller_id) ON DELETE RESTRICT,
    submission_no       INT          NOT NULL,
    status              VARCHAR(10)  NOT NULL DEFAULT 'PENDING',
    snapshot_full_name  VARCHAR(150) NOT NULL,
    snapshot_phone      VARCHAR(20)  NOT NULL,
    description         TEXT,
    submitted_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    reviewed_at         TIMESTAMPTZ,
    reviewed_by_user_id UUID         REFERENCES users (user_id) ON DELETE RESTRICT,
    rejection_reason    TEXT,
    CONSTRAINT uq_seller_applications_submission UNIQUE (seller_id, submission_no),
    CONSTRAINT ck_seller_applications_submission_no CHECK (submission_no >= 1),
    CONSTRAINT ck_seller_applications_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_seller_applications_snapshot_name CHECK (btrim(snapshot_full_name) <> ''),
    CONSTRAINT ck_seller_applications_snapshot_phone CHECK (snapshot_phone ~ '^0[0-9]{9}$'),
    CONSTRAINT ck_seller_applications_rejection_reason CHECK ((status = 'REJECTED') = (rejection_reason IS NOT NULL)),
    CONSTRAINT ck_seller_applications_review CHECK (
        (status = 'PENDING') = (reviewed_at IS NULL) AND (reviewed_at IS NULL) = (reviewed_by_user_id IS NULL)),
    CONSTRAINT ck_seller_applications_no_self_review CHECK (reviewed_by_user_id <> seller_id)
);
-- At most one PENDING application per seller at a time.
CREATE UNIQUE INDEX uq_seller_applications_one_pending ON seller_applications (seller_id) WHERE status = 'PENDING';
CREATE INDEX ix_seller_applications_seller ON seller_applications (seller_id, submission_no DESC);

CREATE FUNCTION identity_seller_application_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.status <> 'PENDING'
       OR NEW.seller_id <> OLD.seller_id OR NEW.submission_no <> OLD.submission_no
       OR NEW.snapshot_full_name <> OLD.snapshot_full_name OR NEW.snapshot_phone <> OLD.snapshot_phone
       OR NEW.description IS DISTINCT FROM OLD.description OR NEW.submitted_at <> OLD.submitted_at THEN
        RAISE EXCEPTION 'Seller application % is immutable: only a PENDING one is reviewed, once', OLD.application_id
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_seller_applications_immutable BEFORE UPDATE ON seller_applications
    FOR EACH ROW EXECUTE FUNCTION identity_seller_application_immutable();

-- Every existing seller's current application becomes submission 1 (earlier resubmissions overwrote the row and
-- left no history).
INSERT INTO seller_applications (seller_id, submission_no, status, snapshot_full_name, snapshot_phone, description,
                                 submitted_at, reviewed_at, reviewed_by_user_id, rejection_reason)
SELECT s.seller_id, 1, s.approval_status, u.full_name, u.phone_number, s.description, s.registered_at, s.reviewed_at,
       s.reviewed_by_user_id, s.rejection_reason
FROM sellers s JOIN users u ON u.user_id = s.seller_id;

-- 4. The seller row keeps only the shop's own data and the state of its latest application.
DROP INDEX uq_sellers_seller_name_active;
DROP INDEX uq_sellers_contact_phone_active;
ALTER TABLE sellers DROP CONSTRAINT ck_sellers_seller_name;
ALTER TABLE sellers DROP CONSTRAINT ck_sellers_contact_phone;
ALTER TABLE sellers DROP COLUMN seller_name;
ALTER TABLE sellers DROP COLUMN contact_phone;
ALTER TABLE sellers DROP COLUMN logo_object_key;

-- 5. Single-use email verification for a REJECTED seller applying again (API §6.1 #30/#31): stored as a hash, like
--    password_reset_tokens; lifetime by configuration (30 min, coverage OQ-011).
CREATE TABLE seller_reapplication_tokens (
    token_id   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    seller_id  UUID         NOT NULL REFERENCES sellers (seller_id) ON DELETE RESTRICT,
    token_hash VARCHAR(128) NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_seller_reapplication_tokens_token_hash UNIQUE (token_hash)
);
