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

/**
 * An event waiting to be published (DB §6), written in the same transaction as the change it describes.
 * {@code payload} holds the whole API §7.2 envelope, so the publisher sends it as is.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "outbox_events")
public class OutboxEvent {

	@Id
	private UUID eventId;

	private String eventType;

	private UUID aggregateId;

	@JdbcTypeCode(SqlTypes.JSON)
	private Map<String, Object> payload;

	private String status;

	private Instant createdAt;

	private Instant publishedAt;

	OutboxEvent(UUID eventId, String eventType, UUID aggregateId, Map<String, Object> payload, Instant createdAt) {
		this.eventId = eventId;
		this.eventType = eventType;
		this.aggregateId = aggregateId;
		this.payload = payload;
		this.status = "PENDING";
		this.createdAt = createdAt;
	}

}
