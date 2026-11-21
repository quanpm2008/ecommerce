package com.example.order_service.service;

import com.example.order_service.entity.CheckoutEvent;
import com.example.order_service.entity.CheckoutSession;
import com.example.order_service.entity.CheckoutSession.CloseReason;
import com.example.order_service.entity.Order;
import com.example.order_service.entity.OrderItem;
import com.example.order_service.entity.StockMovement;
import com.example.order_service.repository.CheckoutOrderRepository;
import com.example.order_service.repository.CheckoutOutcomeRepository;
import com.example.order_service.repository.CheckoutSaleEvidenceRepository;
import com.example.order_service.repository.CheckoutSaleEvidenceRepository.Work;
import com.example.order_service.repository.CheckoutSessionJpaRepository;
import com.example.order_service.repository.InventoryStockRepository;
import com.example.order_service.repository.OrderItemRepository;
import com.example.order_service.service.CheckoutSaleEvidenceWorker.Decision;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

@Service
public class CheckoutOutcomeService {
	private final CheckoutSessionJpaRepository sessions;
	private final CheckoutOrderRepository orders;
	private final InventoryStockRepository inventory;
	private final CheckoutOutcomeRepository outcomes;
	private final CheckoutSaleEvidenceRepository evidence;
	private final OrderItemRepository items;
	private final JsonMapper json;

	CheckoutOutcomeService(CheckoutSessionJpaRepository sessions, CheckoutOrderRepository orders,
			InventoryStockRepository inventory, CheckoutOutcomeRepository outcomes, CheckoutSaleEvidenceRepository evidence,
			OrderItemRepository items, JsonMapper json) {
		this.sessions = sessions;
		this.orders = orders;
		this.inventory = inventory;
		this.outcomes = outcomes;
		this.evidence = evidence;
		this.items = items;
		this.json = json;
	}

	@Transactional
	public void payment(PaymentOutcome payment, String rawEnvelope) {
		var checkout = sessions.lock(payment.checkoutId()).orElse(null);
		if (!outcomes.receive(payment.eventId(), payment.checkoutId(), rawEnvelope)) {
			return;
		}
		if (checkout == null || (checkout.getPaymentId() != null && !checkout.getPaymentId().equals(payment.paymentId()))
				|| sessions.existsByPaymentIdAndCheckoutSessionIdNot(payment.paymentId(), payment.checkoutId())
				|| (payment.succeeded() && (checkout.getTotalAmountVnd() != payment.amountVnd()
					|| !payment.completedAt().isBefore(checkout.getExpiresAt())))) {
			outcomes.finish(payment.eventId(), "FAILED_REVIEW", "PAYMENT_SNAPSHOT_MISMATCH");
			return;
		}
		if (checkout.getStatus() != CheckoutSession.Status.AWAITING_PAYMENT) {
			outcomes.finish(payment.eventId(), "APPLIED", null);
			return;
		}

		// Read wall time in a separate DB statement once the checkout lock has been granted.
		Instant now = inventory.databaseNow();
		if (!now.isBefore(checkout.getExpiresAt())) {
			close(checkout, CloseReason.EXPIRED, now);
			outcomes.finish(payment.eventId(), "APPLIED", null);
		} else if (payment.succeeded()) {
			if (evidence.schedule(payment.checkoutId(), payment.paymentId(), payment.completedAt())) {
				checkout.bindPayment(payment.paymentId());
				outcomes.finish(payment.eventId(), "WAITING_DEPENDENCY", "HISTORICAL_SELLER_PRODUCT_EVIDENCE_REQUIRED");
			} else {
				outcomes.finish(payment.eventId(), "FAILED_REVIEW", "CONFLICTING_TERMINAL_PAYMENT_RESULT");
			}
		} else if (outcomes.waitingForEvidence(payment.checkoutId())) {
			outcomes.finish(payment.eventId(), "FAILED_REVIEW", "CONFLICTING_TERMINAL_PAYMENT_RESULT");
		} else {
			checkout.bindPayment(payment.paymentId());
			close(checkout, CloseReason.PAYMENT_FAILED, now);
			outcomes.finish(payment.eventId(), "APPLIED", null);
		}
	}

	@Transactional(readOnly = true)
	public Map<UUID, UUID> evidenceSnapshot(UUID checkoutId) {
		var checkout = sessions.findById(checkoutId).orElse(null);
		if (checkout == null || checkout.getStatus() != CheckoutSession.Status.AWAITING_PAYMENT
				|| !inventory.databaseNow().isBefore(checkout.getExpiresAt())) {
			return Map.of();
		}
		var sellerByOrder = new HashMap<UUID, UUID>();
		for (var order : orders.findByCheckoutSessionIdOrderBySellerId(checkoutId)) {
			sellerByOrder.put(order.getOrderId(), order.getSellerId());
		}
		var sellerByProduct = new HashMap<UUID, UUID>();
		for (var item : items.findByOrderIdInOrderByProductId(sellerByOrder.keySet())) {
			UUID sellerId = sellerByOrder.get(item.getOrderId());
			UUID existing = sellerByProduct.putIfAbsent(item.getProductId(), sellerId);
			if (existing != null && !existing.equals(sellerId)) {
				throw new IllegalStateException("Checkout snapshot assigns multiple sellers to one product");
			}
		}
		return Map.copyOf(sellerByProduct);
	}

	@Transactional
	public void applyEvidence(Work work, Decision decision) {
		var checkout = sessions.lock(work.checkoutId()).orElse(null);
		if (checkout == null || checkout.getStatus() != CheckoutSession.Status.AWAITING_PAYMENT
				|| !work.paymentId().equals(checkout.getPaymentId()) || !evidence.lockClaim(work)) {
			return;
		}
		Instant now = inventory.databaseNow();
		if (!now.isBefore(checkout.getExpiresAt())) {
			close(checkout, CloseReason.EXPIRED, now);
		} else {
			switch (decision) {
				case UNKNOWN -> evidence.postpone(work.checkoutId(), "HISTORICAL_SALE_EVIDENCE_UNAVAILABLE");
				case INELIGIBLE -> evidence.stop(work.checkoutId(), "HISTORICAL_SALE_INELIGIBLE");
				case ELIGIBLE -> pay(checkout, work.completedAt(), now);
			}
		}
	}

	@Transactional
	public void invalidPayment(UUID diagnosticId, String rawEnvelope) {
		if (outcomes.receive(diagnosticId, null, rawEnvelope)) {
			outcomes.finish(diagnosticId, "FAILED_REVIEW", "INVALID_PAYMENT_EVENT");
		}
	}

	@Transactional
	public ExpiryResult expire(UUID commandId, UUID checkoutId, Instant expiresAt) {
		if (!outcomes.matchesExpiry(commandId, checkoutId, expiresAt)) {
			return new ExpiryResult(ExpiryDisposition.INVALID, 0);
		}
		var checkout = sessions.lock(checkoutId).orElse(null);
		if (checkout == null || !checkout.getExpiresAt().equals(expiresAt)) {
			return new ExpiryResult(ExpiryDisposition.INVALID, 0);
		}
		if (checkout.getStatus() != CheckoutSession.Status.AWAITING_PAYMENT) {
			return new ExpiryResult(ExpiryDisposition.ACKNOWLEDGE, 0);
		}
		Instant now = inventory.databaseNow();
		if (now.isBefore(expiresAt)) {
			var remaining = Duration.between(now, expiresAt);
			int seconds = (int) (remaining.getSeconds() + (remaining.getNano() == 0 ? 0 : 1));
			return new ExpiryResult(ExpiryDisposition.EARLY, seconds);
		}
		close(checkout, CloseReason.EXPIRED, now);
		return new ExpiryResult(ExpiryDisposition.ACKNOWLEDGE, 0);
	}

	private void close(CheckoutSession checkout, CloseReason reason, Instant now) {
		UUID checkoutId = checkout.getCheckoutSessionId();
		var quantities = new TreeMap<UUID, Integer>();
		for (var reservation : outcomes.reservations(checkoutId)) {
			reservation.release(now);
			quantities.merge(reservation.getProductId(), reservation.getQuantity(), Math::addExact);
		}
		quantities.forEach((product, quantity) -> {
			if (inventory.release(product, quantity) != 1) {
				throw new IllegalStateException("Held stock is missing for checkout release");
			}
		});
		Order.Status status = reason == CloseReason.EXPIRED ? Order.Status.EXPIRED : Order.Status.PAYMENT_FAILED;
		orders.findByCheckoutSessionIdOrderBySellerId(checkoutId).forEach(order -> order.closeAwaiting(status, now));
		checkout.close(reason);
		outcomes.closeWaits(checkoutId);
		evidence.stop(checkoutId, null);
		enqueue(checkoutId, "checkout.closed", new Closed(checkoutId, checkout.getCustomerId(), reason, now.toString()), now);
	}

	private void pay(CheckoutSession checkout, Instant completedAt, Instant now) {
		UUID checkoutId = checkout.getCheckoutSessionId();
		var quantities = new TreeMap<UUID, Integer>();
		for (var reservation : outcomes.reservations(checkoutId)) {
			reservation.commit(now);
			quantities.merge(reservation.getProductId(), reservation.getQuantity(), Math::addExact);
			outcomes.recordStockMovement(StockMovement.sold(reservation, now.atOffset(ZoneOffset.UTC)));
		}
		quantities.forEach((product, quantity) -> {
			if (inventory.sellReserved(product, quantity) != 1) {
				throw new IllegalStateException("Held stock is missing for checkout sale");
			}
		});
		var dueAt = completedAt.plus(Duration.ofDays(7));
		var savedOrders = orders.findByCheckoutSessionIdOrderBySellerId(checkoutId);
		for (var order : savedOrders) {
			order.markPaid(completedAt, dueAt, now);
		}
		checkout.markPaid(completedAt);
		outcomes.markWaitsApplied(checkoutId);
		evidence.stop(checkoutId, null);
		var paidOrders = savedOrders.stream().map(order -> new PaidOrder(order.getOrderId(), order.getSellerId(),
				order.getTotalAmountVnd(), order.getCommissionBpsSnapshot())).toList();
		List<CartItem> cartItems = checkout.getCheckoutSource() == CheckoutSession.Source.BUY_NOW ? List.of()
				: items.findByOrderIdInOrderByProductId(savedOrders.stream().map(Order::getOrderId).toList()).stream()
						.map(item -> new CartItem(Objects.requireNonNull(item.getSourceCartItemId()), item.getQuantity())).toList();
		enqueue(checkoutId, "checkout.paid", new Paid(checkoutId, checkout.getPaymentId(), checkout.getCustomerId(),
				checkout.getCheckoutSource(), completedAt.toString(), cartItems, paidOrders), now);
	}

	private void enqueue(UUID checkoutId, String eventType, Object payload, Instant now) {
		UUID eventId = UUID.randomUUID();
		var envelope = new Envelope(eventId, eventType, now.toString(), "order-service", checkoutId, 1,
				new Actor(null, "SERVICE", null), payload);
		outcomes.enqueue(new CheckoutEvent(eventId, checkoutId, json.writeValueAsString(envelope), now));
	}

	public record PaymentOutcome(UUID eventId, UUID checkoutId, UUID paymentId, Long amountVnd, Instant completedAt) {
		boolean succeeded() { return amountVnd != null; }
	}

	public enum ExpiryDisposition { ACKNOWLEDGE, EARLY, INVALID }
	public record ExpiryResult(ExpiryDisposition disposition, int visibilitySeconds) {}

	private record Closed(UUID checkoutSessionId, UUID customerId, CloseReason closeReason, String closedAt) {}
	private record Paid(UUID checkoutSessionId, UUID paymentId, UUID customerId, CheckoutSession.Source source,
			String paidAt, List<CartItem> cartItems, List<PaidOrder> orders) {}
	private record CartItem(UUID cartItemId, int quantity) {}
	private record PaidOrder(UUID orderId, UUID sellerId, long totalAmountVnd, int commissionBps) {}
	private record Actor(UUID userId, String kind, String context) {}
	private record Envelope(UUID eventId, String eventType, String occurredAt, String producer, UUID aggregateId,
			long version, Actor actor, Object payload) {}
}
