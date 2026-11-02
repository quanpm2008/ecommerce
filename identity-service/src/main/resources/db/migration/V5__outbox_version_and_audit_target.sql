-- API §7.2: every aggregate's events carry a version that increases monotonically, so consumers can drop an event
-- older than one they already applied. Identity's aggregate is the user (aggregate_id = user_id), numbered 1, 2, 3...
-- Existing rows are numbered in creation order and the envelope copy in payload gets the same value.
ALTER TABLE outbox_events ADD COLUMN version BIGINT;

UPDATE outbox_events o
SET version = numbered.version,
    payload = jsonb_set(o.payload, '{version}', to_jsonb(numbered.version))
FROM (SELECT event_id, row_number() OVER (PARTITION BY aggregate_id ORDER BY created_at, event_id) AS version
      FROM outbox_events) numbered
WHERE o.event_id = numbered.event_id;

ALTER TABLE outbox_events ALTER COLUMN version SET NOT NULL;
ALTER TABLE outbox_events ADD CONSTRAINT ck_outbox_events_version CHECK (version >= 1);
-- Two transactions numbering the same aggregate at once cannot both commit the same version.
ALTER TABLE outbox_events ADD CONSTRAINT uq_outbox_events_aggregate_version UNIQUE (aggregate_id, version);

-- The scope of an audited action is part of its meaning (a user, customer and seller share one id).
ALTER TABLE admin_audit_log ADD CONSTRAINT ck_admin_audit_log_target_type
    CHECK (target_type IN ('USER', 'CUSTOMER', 'SELLER'));
