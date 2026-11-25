CREATE TABLE seller_sale_evidence (
    evidence_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    seller_id uuid NOT NULL,
    effective_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    eligible boolean NOT NULL
);

CREATE INDEX ix_seller_sale_evidence_lookup
    ON seller_sale_evidence (seller_id, effective_at DESC, evidence_id DESC);

CREATE FUNCTION seller_sale_eligibility(subject uuid) RETURNS boolean
LANGUAGE sql STABLE SET search_path = identity, pg_catalog AS $$
    SELECT EXISTS (
        SELECT FROM sellers s JOIN users u ON u.user_id = s.seller_id
        WHERE s.seller_id = subject AND u.status = 'ACTIVE'
          AND s.status = 'ACTIVE' AND s.approval_status = 'APPROVED'
          AND EXISTS (SELECT FROM user_roles ur JOIN roles r USING (role_id)
                      WHERE ur.user_id = subject AND ur.revoked_at IS NULL AND r.code = 'SELLER')
    )
$$;

INSERT INTO seller_sale_evidence (seller_id, eligible)
SELECT seller_id, seller_sale_eligibility(seller_id) FROM sellers;

CREATE FUNCTION capture_seller_sale_evidence() RETURNS trigger
LANGUAGE plpgsql SET search_path = identity, pg_catalog AS $$
DECLARE
    subject uuid;
    decision boolean;
BEGIN
    IF TG_TABLE_NAME = 'sellers' THEN
        subject := CASE WHEN TG_OP = 'DELETE' THEN OLD.seller_id ELSE NEW.seller_id END;
    ELSE
        subject := CASE WHEN TG_OP = 'DELETE' THEN OLD.user_id ELSE NEW.user_id END;
        IF NOT EXISTS (SELECT FROM sellers WHERE seller_id = subject) THEN
            RETURN NULL;
        END IF;
    END IF;
    decision := seller_sale_eligibility(subject);
    IF decision IS DISTINCT FROM (
        SELECT eligible FROM seller_sale_evidence WHERE seller_id = subject
        ORDER BY effective_at DESC, evidence_id DESC LIMIT 1
    ) THEN
        INSERT INTO seller_sale_evidence (seller_id, eligible) VALUES (subject, decision);
    END IF;
    RETURN NULL;
END
$$;

-- Capture the final state of every relevant change in its owning transaction.
CREATE CONSTRAINT TRIGGER seller_sale_evidence_seller
    AFTER INSERT OR UPDATE OF status, approval_status OR DELETE ON sellers
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION capture_seller_sale_evidence();
CREATE CONSTRAINT TRIGGER seller_sale_evidence_user
    AFTER UPDATE OF status ON users
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION capture_seller_sale_evidence();
CREATE CONSTRAINT TRIGGER seller_sale_evidence_role
    AFTER INSERT OR UPDATE OR DELETE ON user_roles
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION capture_seller_sale_evidence();
