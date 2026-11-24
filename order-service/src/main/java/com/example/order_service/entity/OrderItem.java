package com.example.order_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "order_item")
public class OrderItem {
	@Id
	@Column(name = "order_item_id", nullable = false)
	private UUID orderItemId;

	@Column(name = "order_id", nullable = false)
	private UUID orderId;

	@Column(name = "product_id", nullable = false)
	private UUID productId;

	@Column(name = "product_name_snapshot", nullable = false, length = 255)
	private String productNameSnapshot;

	@Column(name = "cover_media_snapshot", nullable = false, columnDefinition = "text")
	private String coverMediaSnapshot;

	@Column(name = "unit_price_vnd_snapshot", nullable = false)
	private long unitPriceVndSnapshot;

	@Column(name = "quantity", nullable = false)
	private int quantity;

	@Column(name = "line_total_vnd", nullable = false, insertable = false, updatable = false)
	private long lineTotalVnd;

	@Column(name = "source_cart_item_id")
	private UUID sourceCartItemId;

	protected OrderItem() {
	}

	public OrderItem(UUID orderItemId, UUID orderId, UUID productId, String productNameSnapshot,
			String coverMediaSnapshot, long unitPriceVndSnapshot, int quantity) {
		this.orderItemId = orderItemId;
		this.orderId = orderId;
		this.productId = productId;
		this.productNameSnapshot = productNameSnapshot;
		this.coverMediaSnapshot = coverMediaSnapshot;
		this.unitPriceVndSnapshot = unitPriceVndSnapshot;
		this.quantity = quantity;
	}

	public UUID getOrderItemId() {
		return orderItemId;
	}

	public UUID getOrderId() {
		return orderId;
	}

	public UUID getProductId() {
		return productId;
	}

	public String getProductNameSnapshot() {
		return productNameSnapshot;
	}

	public String getCoverMediaSnapshot() {
		return coverMediaSnapshot;
	}

	public long getUnitPriceVndSnapshot() {
		return unitPriceVndSnapshot;
	}

	public int getQuantity() {
		return quantity;
	}

	public long getLineTotalVnd() {
		return lineTotalVnd;
	}

	public UUID getSourceCartItemId() {
		return sourceCartItemId;
	}
}
