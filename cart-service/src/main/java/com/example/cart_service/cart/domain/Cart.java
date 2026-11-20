package com.example.cart_service.cart.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One cart per customer (Database §3.3.1). Created by {@link CartRepository#insertIfAbsent}. */
@Entity
@Table(name = "carts")
public class Cart {

	@Id
	@Column(name = "cart_id")
	private UUID cartId;

	@Column(name = "customer_id", nullable = false, updatable = false)
	private UUID customerId;

	@Column(name = "row_version", nullable = false)
	private long rowVersion;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Cart() {
	}

	public void touch(Instant now) {
		rowVersion++;
		updatedAt = now;
	}

	public UUID getCartId() {
		return cartId;
	}

	public UUID getCustomerId() {
		return customerId;
	}

	public long getRowVersion() {
		return rowVersion;
	}

}
