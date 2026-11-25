package com.example.order_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "inventory")
public class Inventory {
	@Id
	@Column(name = "product_id", nullable = false)
	private UUID productId;

	@Column(name = "seller_id", nullable = false)
	private UUID sellerId;

	@Column(name = "on_hand_quantity", nullable = false)
	private int onHandQuantity;

	@Column(name = "reserved_quantity", nullable = false)
	private int reservedQuantity;

	@Version
	@Column(name = "row_version", nullable = false)
	private long rowVersion;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected Inventory() {
	}

	public UUID getProductId() {
		return productId;
	}

	public UUID getSellerId() {
		return sellerId;
	}

	public int getOnHandQuantity() {
		return onHandQuantity;
	}

	public int getReservedQuantity() {
		return reservedQuantity;
	}

	public long getRowVersion() {
		return rowVersion;
	}

	public OffsetDateTime getUpdatedAt() {
		return updatedAt;
	}

	public int availableQuantity() {
		return onHandQuantity - reservedQuantity;
	}

	public void adjustOnHand(int quantity, OffsetDateTime at) {
		onHandQuantity = quantity;
		updatedAt = at;
	}
}
