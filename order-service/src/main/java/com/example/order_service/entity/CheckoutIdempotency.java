package com.example.order_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_record")
public class CheckoutIdempotency {
	@EmbeddedId
	private Key id;

	@Column(name = "request_hash", nullable = false, length = 128)
	private String requestHash;

	@Column(name = "result_ref", nullable = false)
	private UUID resultRef;

	@Column(name = "created_at", insertable = false, updatable = false)
	private Instant createdAt;

	protected CheckoutIdempotency() {
	}

	public CheckoutIdempotency(Key id, String requestHash, UUID resultRef) {
		this.id = id;
		this.requestHash = requestHash;
		this.resultRef = resultRef;
	}

	public UUID getResultRef() {
		return resultRef;
	}

	@Embeddable
	public record Key(@Column(name = "actor_user_id") UUID customerId,
			@Column(name = "operation_context", length = 20) String context,
			@Column(length = 100) String endpoint,
			@Column(name = "idempotency_key", length = 128) String key) implements Serializable {
	}
}
