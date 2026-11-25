CREATE TABLE settlement_inbox (
    event_id uuid PRIMARY KEY,
    event_type varchar(32) NOT NULL CHECK (event_type IN ('checkout.paid', 'seller.approved')),
    payload jsonb NOT NULL,
    status varchar(24) NOT NULL CHECK (status IN ('WAITING_DEPENDENCY', 'APPLIED', 'FAILED_REVIEW')),
    received_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX settlement_inbox_waiting_idx ON settlement_inbox (received_at, event_id)
    WHERE status = 'WAITING_DEPENDENCY';
