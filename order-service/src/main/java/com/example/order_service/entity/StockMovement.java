package com.example.order_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "stock_movement")
public class StockMovement {
	@Id
	@Column(name = "stock_movement_id", nullable = false)
	private UUID stockMovementId;

	@Column(name = "product_id", nullable = false)
	private UUID productId;

	@Enumerated(EnumType.STRING)
	@Column(name = "movement_type", nullable = false, length = 20)
	private Type movementType;

	@Column(name = "quantity_delta", nullable = false)
	private int quantityDelta;

	@Column(name = "order_item_id")
	private UUID orderItemId;

	@Column(name = "actor_id")
	private UUID actorId;

	@Column(name = "business_event_key", nullable = false, length = 128)
	private String businessEventKey;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "actor_type", nullable = false, length = 10)
	private ActorType actorType;

	@Column(name = "note", columnDefinition = "text")
	private String note;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "metadata", insertable = false, updatable = false, columnDefinition = "jsonb")
	private Map<String, Object> metadata;

	protected StockMovement() {
	}

	public StockMovement(UUID stockMovementId, UUID productId, Type movementType, int quantityDelta,
			UUID actorId, String businessEventKey, OffsetDateTime createdAt, ActorType actorType,
			String note) {
		this.stockMovementId = stockMovementId;
		this.productId = productId;
		this.movementType = movementType;
		this.quantityDelta = quantityDelta;
		this.actorId = actorId;
		this.businessEventKey = businessEventKey;
		this.createdAt = createdAt;
		this.actorType = actorType;
		this.note = note;
	}

	public UUID getStockMovementId() {
		return stockMovementId;
	}

	public static StockMovement sold(Reservation reservation, OffsetDateTime at) {
		var move = new StockMovement(UUID.randomUUID(), reservation.getProductId(), Type.SOLD, -reservation.getQuantity(),
				null, "SOLD:" + reservation.getOrderItemId(), at, ActorType.SYSTEM, null);
		move.orderItemId = reservation.getOrderItemId();
		return move;
	}

	public static StockMovement restock(OrderItem item, UUID actorId, OffsetDateTime at) {
		var move = new StockMovement(UUID.randomUUID(), item.getProductId(), Type.RESTOCK, item.getQuantity(),
				actorId, "RESTOCK:" + item.getOrderItemId(), at,
				actorId == null ? ActorType.SYSTEM : ActorType.USER, null);
		move.orderItemId = item.getOrderItemId();
		return move;
	}

	public UUID getProductId() {
		return productId;
	}

	public Type getMovementType() {
		return movementType;
	}

	public int getQuantityDelta() {
		return quantityDelta;
	}

	public UUID getOrderItemId() {
		return orderItemId;
	}

	public UUID getActorId() {
		return actorId;
	}

	public String getBusinessEventKey() {
		return businessEventKey;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	public ActorType getActorType() {
		return actorType;
	}

	public String getNote() {
		return note;
	}

	public Map<String, Object> getMetadata() {
		return metadata;
	}

	public enum Type { IMPORT, MANUAL_ADJUST, SOLD, RESTOCK }
	public enum ActorType { USER, SYSTEM }
}
