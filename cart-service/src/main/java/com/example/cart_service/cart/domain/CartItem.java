package com.example.cart_service.cart.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A product line with the quantity the customer wants (Database §3.3.2). Quantity is always 1–99. */
@Entity
@Table(name = "cart_items")
public class CartItem {

	@Id
	@Column(name = "cart_item_id")
	private UUID cartItemId;

	@Column(name = "cart_id", nullable = false, updatable = false)
	private UUID cartId;

	@Column(name = "product_id", nullable = false, updatable = false)
	private UUID productId;

	@Column(name = "quantity", nullable = false)
	private int quantity;

	@Column(name = "row_version", nullable = false)
	private long rowVersion;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected CartItem() {
	}

	public CartItem(UUID cartId, UUID productId, int quantity, Instant now) {
		this.cartItemId = UUID.randomUUID();
		this.cartId = cartId;
		this.productId = productId;
		this.quantity = quantity;
		this.rowVersion = 0;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void changeQuantity(int quantity, Instant now) {
		this.quantity = quantity;
		this.rowVersion++;
		this.updatedAt = now;
	}

	public UUID getCartItemId() {
		return cartItemId;
	}

	public UUID getCartId() {
		return cartId;
	}

	public UUID getProductId() {
		return productId;
	}

	public int getQuantity() {
		return quantity;
	}

	public long getRowVersion() {
		return rowVersion;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
