package com.example.order_service.service;

import com.example.order_service.entity.CheckoutEvent;
import com.example.order_service.entity.Order;
import com.example.order_service.entity.Order.CancelledByType;
import com.example.order_service.entity.Order.ConfirmedBy;
import com.example.order_service.entity.Order.Status;
import com.example.order_service.entity.OrderItem;
import com.example.order_service.entity.StockMovement;
import com.example.order_service.repository.CheckoutOrderRepository;
import com.example.order_service.repository.CheckoutOutcomeRepository;
import com.example.order_service.repository.InventoryStockRepository;
import com.example.order_service.repository.OrderItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class OrderLifecycleService {
	private static final Set<String> CUSTOMER_REASONS = Set.of(
			"CHANGED_MIND", "ORDERED_BY_MISTAKE", "FOUND_BETTER_PRICE", "ADDRESS_CHANGE", "OTHER");
	private static final Set<String> SELLER_REASONS = Set.of("DEFECTIVE", "OUT_OF_STOCK_ACTUAL", "CANNOT_DELIVER", "OTHER");
	private final CheckoutOrderRepository orders;
	private final OrderItemRepository items;
	private final InventoryStockRepository inventory;
	private final CheckoutOutcomeRepository events;
	private final JsonMapper json;

	public OrderLifecycleService(CheckoutOrderRepository orders, OrderItemRepository items,
			InventoryStockRepository inventory, CheckoutOutcomeRepository events, JsonMapper json) {
		this.orders = orders;
		this.items = items;
		this.inventory = inventory;
		this.events = events;
		this.json = json;
	}

	@Transactional
	public void customerCancel(UUID orderId, UUID customer, String reason, String note) {
		var order = owned(orderId, customer, false);
		if (order.getStatus() == Status.CANCELLED) {
			return;
		}
		if (order.getStatus() != Status.PAID) {
			throw rejected(409, order.getStatus() == Status.AWAITING_PAYMENT ? "PAYMENT_IN_PROGRESS" : "ORDER_NOT_CANCELLABLE", order);
		}
		validateReason(reason, note, false);
		cancel(order, CancelledByType.CUSTOMER, customer, reason, note, "CUSTOMER_CANCEL", inventory.databaseNow());
	}

	@Transactional
	public void sellerAction(UUID orderId, UUID seller, SellerAction action, String reason, String note) {
		var order = owned(orderId, seller, true);
		boolean permitted = switch (action) {
			case PACK -> order.getStatus() == Status.PAID;
			case DELIVER -> order.getStatus() == Status.PACKING;
			case REJECT -> order.getStatus() == Status.PAID || order.getStatus() == Status.PACKING;
		};
		if (!permitted) {
			throw rejected(409, "INVALID_STATE_TRANSITION", order);
		}
		Instant now = inventory.databaseNow();
		if (due(order.getSellerActionDueAt(), now)) {
			throw rejected(409, "SELLER_DEADLINE_PASSED", order);
		}
		switch (action) {
			case PACK -> {
				order.startPacking(now);
				emit(order, "order.packing", now, seller, "SELLER", Map.of());
			}
			case DELIVER -> {
				order.markDelivered(now, now.plus(7, ChronoUnit.DAYS));
				emit(order, "order.delivered", now, seller, "SELLER", Map.of("customerId", order.getCustomerId(),
						"deliveredAt", now, "customerConfirmDueAt", order.getCustomerConfirmDueAt()));
			}
			case REJECT -> {
				validateReason(reason, note, true);
				cancel(order, CancelledByType.SELLER, seller, reason, note, "SELLER_REJECT", now);
			}
		}
	}

	@Transactional
	public void customerConfirm(UUID orderId, UUID customer) {
		var order = owned(orderId, customer, false);
		if (order.getStatus() == Status.CONFIRMED) {
			return;
		}
		if (order.getStatus() != Status.DELIVERED) {
			throw rejected(409, "INVALID_STATE_TRANSITION", order);
		}
		confirm(order, customer, inventory.databaseNow());
	}

	@Transactional
	public void expireSellerAction(UUID orderId) {
		var order = orders.lock(orderId).orElseThrow();
		Instant now = inventory.databaseNow();
		if ((order.getStatus() == Status.PAID || order.getStatus() == Status.PACKING)
				&& due(order.getSellerActionDueAt(), now)) {
			cancel(order, CancelledByType.SYSTEM, null, null, null, "SELLER_TIMEOUT", now);
		}
	}

	@Transactional
	public void autoConfirm(UUID orderId) {
		var order = orders.lock(orderId).orElseThrow();
		Instant now = inventory.databaseNow();
		if (order.getStatus() == Status.DELIVERED && due(order.getCustomerConfirmDueAt(), now)) {
			confirm(order, null, now);
		}
	}

	private void confirm(Order order, UUID actor, Instant now) {
		var by = actor == null ? ConfirmedBy.SYSTEM : ConfirmedBy.CUSTOMER;
		order.confirmReceipt(by, now);
		emit(order, "order.confirmed", now, actor, "CUSTOMER", Map.of("customerId", order.getCustomerId(),
				"confirmedAt", now, "confirmedBy", by));
	}

	void cancel(Order order, CancelledByType by, UUID actor, String reason, String note, String refundReason, Instant now) {
		var lines = items.findByOrderIdInOrderByProductId(List.of(order.getOrderId()));
		if (lines.isEmpty()) {
			throw new IllegalStateException("Cannot restock an order with no items");
		}
		for (var line : lines.stream().sorted(Comparator.comparing(OrderItem::getProductId)).toList()) {
			if (inventory.restock(line.getProductId(), order.getSellerId(), line.getQuantity()) != 1) {
				throw new IllegalStateException("Cannot restock order item " + line.getOrderItemId());
			}
			events.recordStockMovement(StockMovement.restock(line, actor, now.atOffset(ZoneOffset.UTC)));
		}
		order.cancel(by, actor, reason, note, now);
		var payload = new LinkedHashMap<String, Object>(Map.of("checkoutSessionId", order.getCheckoutSessionId(),
				"customerId", order.getCustomerId(), "totalAmountVnd", order.getTotalAmountVnd(),
				"wasPaid", true, "cancelledBy", by, "refundReason", refundReason));
		payload.put("reasonCode", reason);
		payload.put("reasonNote", note);
		emit(order, "order.cancelled", now, actor, by.name(), payload);
	}

	private Order owned(UUID id, UUID actor, boolean seller) {
		var order = orders.lock(id).orElse(null);
		if (order == null || !actor.equals(seller ? order.getSellerId() : order.getCustomerId())
				|| seller && order.getPaidAt() == null) {
			throw new Rejected(404, "NOT_FOUND", null);
		}
		return order;
	}

	private static void validateReason(String reason, String note, boolean seller) {
		if (reason == null || !(seller ? SELLER_REASONS : CUSTOMER_REASONS).contains(reason)
				|| note != null && note.codePointCount(0, note.length()) > 500
				|| (seller || "OTHER".equals(reason)) && (note == null || note.isBlank())) {
			throw new Rejected(400, "VALIDATION_ERROR", null);
		}
	}

	private void emit(Order order, String type, Instant now, UUID actorId, String context, Map<String, Object> payload) {
		var eventId = UUID.randomUUID();
		var body = new LinkedHashMap<>(payload);
		body.put("orderId", order.getOrderId());
		body.put("sellerId", order.getSellerId());
		var actor = actorId == null ? Map.of("kind", "SERVICE")
				: Map.of("kind", "USER", "context", context, "userId", actorId);
		String envelope = json.writeValueAsString(Map.of("eventId", eventId, "eventType", type,
				"producer", "order-service", "aggregateId", order.getOrderId(), "aggregateType", "ORDER",
				"version", order.getLifecycleVersion(), "occurredAt", now, "payload", body, "actor", actor));
		events.enqueue(new CheckoutEvent(eventId, order, envelope, now));
	}

	private static boolean due(Instant deadline, Instant now) {
		return deadline != null && !now.isBefore(deadline);
	}

	private static Rejected rejected(int status, String code, Order order) {
		return new Rejected(status, code, order.getStatus());
	}

	public enum SellerAction { PACK, DELIVER, REJECT }

	public static final class Rejected extends RuntimeException {
		private final int status;
		private final Status current;

		public Rejected(int status, String code, Status current) {
			super(code);
			this.status = status;
			this.current = current;
		}

		public int status() { return status; }
		public Status current() { return current; }
	}
}
