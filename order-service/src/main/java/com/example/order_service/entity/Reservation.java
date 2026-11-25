package com.example.order_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reservation")
public class Reservation {
	@Id
	@Column(name = "reservation_id", nullable = false)
	private UUID reservationId;

	@Column(name = "order_item_id", nullable = false)
	private UUID orderItemId;

	@Column(name = "product_id", nullable = false)
	private UUID productId;

	@Column(name = "quantity", nullable = false)
	private int quantity;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 16)
	private Status status;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "resolved_at")
	private Instant resolvedAt;

	protected Reservation() {
	}

	public Reservation(UUID reservationId, UUID orderItemId, UUID productId, int quantity,
			Status status, Instant expiresAt, Instant createdAt) {
		this.reservationId = reservationId;
		this.orderItemId = orderItemId;
		this.productId = productId;
		this.quantity = quantity;
		this.status = status;
		this.expiresAt = expiresAt;
		this.createdAt = createdAt;
	}

	public UUID getReservationId() {
		return reservationId;
	}

	public UUID getOrderItemId() {
		return orderItemId;
	}

	public UUID getProductId() {
		return productId;
	}

	public int getQuantity() {
		return quantity;
	}

	public Status getStatus() {
		return status;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getResolvedAt() {
		return resolvedAt;
	}

	public void release(Instant at) {
		resolve(Status.RELEASED, at);
	}

	public void commit(Instant at) {
		resolve(Status.COMMITTED, at);
	}

	private void resolve(Status resolved, Instant at) {
		if (status != Status.HELD) {
			throw new IllegalStateException("Checkout reservation is no longer held");
		}
		status = resolved;
		resolvedAt = at;
	}

	public enum Status { HELD, COMMITTED, RELEASED }
}
