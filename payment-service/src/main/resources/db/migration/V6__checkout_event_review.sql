CREATE TABLE payment_checkout_event_review (
    topic TEXT NOT NULL,
    kafka_partition INTEGER NOT NULL,
    kafka_offset BIGINT NOT NULL,
    message_key TEXT,
    raw_envelope TEXT,
    reason VARCHAR(32) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (topic, kafka_partition, kafka_offset)
);
