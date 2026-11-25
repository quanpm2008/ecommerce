package com.example.payment_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Entity lưu sự kiện Outbox (Transactional Outbox Pattern) trong payment-service.
 */
@Entity
@Getter
@NoArgsConstructor
@Table(name = "outbox_events")
public class OutboxEvent {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PUBLISHED = "PUBLISHED";

    @Id
    @Column(name = "event_id", updatable = false, nullable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "version", nullable = false)
    private long version = 1L;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Column(name = "status", nullable = false, length = 10)
    private String status = STATUS_PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    public OutboxEvent(UUID eventId, String eventType, UUID aggregateId, Map<String, Object> payload) {
        this(eventId, eventType, aggregateId, 1L, payload);
    }

    public OutboxEvent(UUID eventId, String eventType, UUID aggregateId, long version, Map<String, Object> payload) {
        this.eventId = eventId != null ? eventId : UUID.randomUUID();
        this.eventType = Objects.requireNonNull(eventType, "eventType is required");
        this.aggregateId = Objects.requireNonNull(aggregateId, "aggregateId is required");
        this.version = version;
        this.payload = Objects.requireNonNull(payload, "payload is required");
        this.status = STATUS_PENDING;
    }

    public boolean isPending() {
        return STATUS_PENDING.equals(this.status);
    }
    public void markPublished(Instant now) {
        this.status = STATUS_PUBLISHED;
        this.publishedAt = now != null ? now : Instant.now();
    }
}
