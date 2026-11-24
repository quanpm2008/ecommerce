CREATE TABLE orders.admin_audit_log (
    audit_id UUID PRIMARY KEY,
    actor_user_id UUID NOT NULL,
    action VARCHAR(32) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id UUID NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    before_after JSONB NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    happened_at TIMESTAMPTZ NOT NULL
);
