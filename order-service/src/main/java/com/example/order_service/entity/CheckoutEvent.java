package com.example.order_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "checkout_event_outbox")
public class CheckoutEvent {
	@Id
	@Column(name = "event_id")
	private UUID eventId;

	@Column(name = "checkout_session_id", nullable = false)
	private UUID checkoutSessionId;

	@Column(name = "order_id")
	private UUID orderId;

	@Column(name = "order_version")
	private Long orderVersion;

	@Column(name = "envelope", nullable = false, columnDefinition = "text")
	private String envelope;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected CheckoutEvent() {}

	public CheckoutEvent(UUID eventId, UUID checkoutSessionId, String envelope, Instant createdAt) {
		this.eventId = eventId;
		this.checkoutSessionId = checkoutSessionId;
		this.envelope = envelope;
		this.createdAt = createdAt;
	}

	public CheckoutEvent(UUID eventId, Order order, String envelope, Instant createdAt) {
		this(eventId, order.getCheckoutSessionId(), envelope, createdAt);
		this.orderId = order.getOrderId();
		this.orderVersion = order.getLifecycleVersion();
	}

	public UUID aggregateId() {
		return orderId == null ? checkoutSessionId : orderId;
	}

	public UUID getEventId() {
		return eventId;
	}

	public UUID getCheckoutSessionId() {
		return checkoutSessionId;
	}

	public String getEnvelope() {
		return envelope;
	}
}
