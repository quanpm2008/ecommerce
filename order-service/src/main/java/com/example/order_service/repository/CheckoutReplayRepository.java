package com.example.order_service.repository;

import com.example.order_service.client.PayIdentityClient.ShippingAddress;
import com.example.order_service.dto.response.CheckoutResponse;
import com.example.order_service.exception.BuyNowPayRejected;
import com.example.order_service.entity.CheckoutIdempotency;
import com.example.order_service.entity.CheckoutSession;
import com.example.order_service.entity.Order;
import com.example.order_service.entity.OrderItem;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class CheckoutReplayRepository {
	private final CheckoutSessionJpaRepository sessions;
	private final CheckoutIdempotencyRepository keys;
	private final CheckoutOrderRepository orders;
	private final OrderItemRepository items;

	CheckoutReplayRepository(CheckoutSessionJpaRepository sessions, CheckoutIdempotencyRepository keys,
			CheckoutOrderRepository orders, OrderItemRepository items) {
		this.sessions = sessions;
		this.keys = keys;
		this.orders = orders;
		this.items = items;
	}

	@Transactional
	public CheckoutResponse findOrRegisterAlias(UUID customer, String key, UUID intent, String hash) {
		UUID keySession = findKey(customer, key);
		var matches = sessions.lockForReplay(customer, keySession, intent);
		if (matches.isEmpty()) {
			return null;
		}
		if (matches.size() > 1) {
			throw conflict("CHECKOUT_INTENT_CONFLICT");
		}
		CheckoutSession session = matches.getFirst();
		if (!session.getRequestHash().equals(hash) || !session.getCheckoutIntentId().equals(intent)) {
			throw conflict(keySession == null ? "CHECKOUT_INTENT_CONFLICT" : "IDEMPOTENCY_KEY_REUSED");
		}
		if (keySession == null) {
			keys.insertAlias(customer, CheckoutCommitRepository.PAY_ENDPOINT, key, hash, session.getCheckoutSessionId());
			if (!session.getCheckoutSessionId().equals(findKey(customer, key))) {
				throw conflict("CHECKOUT_INTENT_CONFLICT");
			}
		}
		return response(session);
	}

	private UUID findKey(UUID customer, String key) {
		return keys.findById(new CheckoutIdempotency.Key(customer, "CUSTOMER", CheckoutCommitRepository.PAY_ENDPOINT, key))
				.map(CheckoutIdempotency::getResultRef).orElse(null);
	}

	@Transactional(readOnly = true)
	public Optional<CheckoutResponse> current(UUID customer) {
		return sessions.findByCustomerIdAndStatus(customer, CheckoutSession.Status.AWAITING_PAYMENT).map(this::response);
	}

	@Transactional(readOnly = true)
	public Optional<CheckoutResponse> owned(UUID checkoutId, UUID customer) {
		return sessions.findByCheckoutSessionIdAndCustomerId(checkoutId, customer).map(this::response);
	}

	@Transactional(readOnly = true)
	public Optional<Payable> payable(UUID checkoutId) {
		return sessions.findById(checkoutId).map(session -> new Payable(session.getCustomerId(),
				session.getStatus().name(), session.getExpiresAt(), session.getTotalAmountVnd(),
				orders.findByCheckoutSessionIdOrderBySellerId(checkoutId).stream()
						.map(order -> new PayableOrder(order.getOrderId(), order.getStatus().name())).toList()));
	}

	public record Payable(UUID customerId, String status, Instant expiresAt, long totalAmountVnd,
			List<PayableOrder> orders) {}

	public record PayableOrder(UUID orderId, String status) {}

	private CheckoutResponse response(CheckoutSession session) {
		UUID id = session.getCheckoutSessionId();
		var savedOrders = orders.findByCheckoutSessionIdOrderBySellerId(id);
		var linesByOrder = items.findByOrderIdInOrderByProductId(savedOrders.stream().map(Order::getOrderId).toList())
				.stream().collect(Collectors.groupingBy(OrderItem::getOrderId));
		var summaries = savedOrders.stream().map(order -> {
			var lines = linesByOrder.get(order.getOrderId());
			var first = lines.getFirst();
			return new CheckoutResponse.OrderSummary(order.getOrderId(), id,
					new CheckoutResponse.Seller(order.getSellerId(), order.getSellerNameSnapshot()),
					order.getStatus().name(), order.getTotalAmountVnd(), lines.size(),
					new CheckoutResponse.FirstItem(first.getProductNameSnapshot(), first.getCoverMediaSnapshot()),
					order.getCreatedAt(), session.getPaidAt(), null);
		}).toList();
		var first = savedOrders.getFirst();
		var address = new ShippingAddress(first.getRecipientName(), first.getRecipientPhone(), first.getProvinceCode(),
				first.getProvinceName(), first.getWardCode(), first.getWardName(), first.getAddressDetail());
		var reason = session.getCloseReason();
		return new CheckoutResponse(id, session.getCheckoutSource().name(), session.getStatus().name(),
				reason == null ? null : reason.name(), "NONE", null, session.getTotalAmountVnd(), 0,
				session.getExpiresAt(), session.getPaidAt(), address, summaries, List.of(), true);
	}

	private static BuyNowPayRejected conflict(String code) {
		return new BuyNowPayRejected(409, code);
	}
}
