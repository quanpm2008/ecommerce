package com.example.identity_service.outbox;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "outbox_events")
public class OutboxEvent {

	static final String PENDING = "PENDING";

	static final String PUBLISHED = "PUBLISHED";

	@Id
	private UUID eventId;

	private String eventType;

	private UUID aggregateId;

	/** Position among the aggregate's events: 1, 2, 3... (API §7.2). */
	private long version;

	/** The whole API §7.2 envelope, as published. */
	@JdbcTypeCode(SqlTypes.JSON)
	private Map<String, Object> payload;

	private String status;

	private Instant createdAt;

	private Instant publishedAt;

	OutboxEvent(UUID eventId, String eventType, UUID aggregateId, long version, Map<String, Object> payload,
			Instant createdAt) {
		this.eventId = eventId;
		this.eventType = eventType;
		this.aggregateId = aggregateId;
		this.version = version;
		this.payload = payload;
		this.status = PENDING;
		this.createdAt = createdAt;
	}

	public boolean isPending() {
		return PENDING.equals(status);
	}

	/** Delivered to the broker; {@code storedPayload} replaces the stored envelope (e.g. without secrets). */
	void markPublished(Instant now, Map<String, Object> storedPayload) {
		this.status = PUBLISHED;
		this.publishedAt = now;
		this.payload = storedPayload;
	}

}
