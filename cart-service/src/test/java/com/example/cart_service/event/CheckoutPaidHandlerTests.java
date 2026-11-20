package com.example.cart_service.event;

import com.example.cart_service.IntegrationTestSupport;
import com.example.cart_service.cart.domain.CartItem;
import com.example.cart_service.cart.domain.CartStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** UC-B03.MAIN-04/ALT-04/ALT-05/POST-01, REQ-ORD-005, AC-ORD-001, AT-19, AT-21 for the checkout.paid consumer. */
class CheckoutPaidHandlerTests extends IntegrationTestSupport {

	@Autowired
	private CheckoutPaidHandler handler;

	@Autowired
	private CartStore store;

	private UUID customerId;

	private UUID shirt;

	private UUID jeans;

	@BeforeEach
	void cart() {
		customerId = UUID.randomUUID();
		shirt = UUID.randomUUID();
		jeans = UUID.randomUUID();
	}

	@Test
	void decrementsThePurchasedQuantityAndKeepsUnitsAddedAfterCheckout() {
		store.add(customerId, shirt, 2);
		store.add(customerId, jeans, 1);
		UUID shirtLine = line(shirt).getCartItemId();
		UUID jeansLine = line(jeans).getCartItemId();
		// After the checkout snapshot (shirt x2, jeans x1) the customer adds 3 more shirts.
		store.add(customerId, shirt, 3);

		String event = cartEvent("evt-1", UUID.randomUUID(), Map.of(shirtLine, 2, jeansLine, 1));
		assertThat(handler.handle(event)).isEqualTo(CheckoutPaidHandler.Outcome.APPLIED);

		assertThat(quantities()).containsExactlyInAnyOrderEntriesOf(Map.of(shirt, 3));
	}

	@Test
	void replayOrAnotherEventForTheSameCheckoutDecrementsOnce() {
		store.add(customerId, shirt, 5);
		UUID shirtLine = line(shirt).getCartItemId();
		UUID checkout = UUID.randomUUID();

		assertThat(handler.handle(cartEvent("evt-a", checkout, Map.of(shirtLine, 2))))
				.isEqualTo(CheckoutPaidHandler.Outcome.APPLIED);
		assertThat(handler.handle(cartEvent("evt-a", checkout, Map.of(shirtLine, 2))))
				.isEqualTo(CheckoutPaidHandler.Outcome.DUPLICATE);
		assertThat(handler.handle(cartEvent("evt-b", checkout, Map.of(shirtLine, 2))))
				.isEqualTo(CheckoutPaidHandler.Outcome.DUPLICATE);

		assertThat(quantities()).containsExactlyEntriesOf(Map.of(shirt, 3));
		assertThat(jdbc.queryForList("SELECT state FROM inbox_events ORDER BY event_id", String.class))
				.containsExactly("APPLIED", "SKIPPED");
	}

	@Test
	void lineDeletedAndAddedAgainIsNotDecremented() {
		store.add(customerId, shirt, 2);
		CartItem original = line(shirt);
		store.remove(customerId, original.getCartItemId());
		store.add(customerId, shirt, 4);

		handler.handle(cartEvent("evt-1", UUID.randomUUID(), Map.of(original.getCartItemId(), 2)));

		assertThat(quantities()).containsExactlyEntriesOf(Map.of(shirt, 4));
	}

	@Test
	void lineReducedBelowTheSnapshotIsRemovedNotNegative() {
		store.add(customerId, shirt, 3);
		CartItem item = line(shirt);
		store.changeQuantity(customerId, item.getCartItemId(), 1);

		handler.handle(cartEvent("evt-1", UUID.randomUUID(), Map.of(item.getCartItemId(), 3)));

		assertThat(quantities()).isEmpty();
	}

	@Test
	void buyNowNeverTouchesTheCart() {
		store.add(customerId, shirt, 3);
		String event = """
				{ "eventId": "evt-bn", "eventType": "checkout.paid", "producer": "order-service",
				  "payload": { "checkoutSessionId": "%s", "customerId": "%s", "source": "BUY_NOW",
				               "paidAt": "2026-11-20T02:00:00Z", "cartItems": [], "orders": [] } }
				""".formatted(UUID.randomUUID(), customerId);

		assertThat(handler.handle(event)).isEqualTo(CheckoutPaidHandler.Outcome.APPLIED);
		assertThat(quantities()).containsExactlyEntriesOf(Map.of(shirt, 3));
	}

	@Test
	void anotherCustomersLineIsNotTouched() {
		UUID other = UUID.randomUUID();
		store.add(other, shirt, 5);
		UUID otherLine = store.contents(other).items().get(0).getCartItemId();
		store.add(customerId, jeans, 1);

		handler.handle(cartEvent("evt-1", UUID.randomUUID(), Map.of(otherLine, 5)));

		assertThat(store.contents(other).items().get(0).getQuantity()).isEqualTo(5);
	}

	@Test
	void unusablePayloadIsKeptForReviewAndChangesNothing() {
		store.add(customerId, shirt, 2);
		String event = """
				{ "eventId": "evt-bad", "eventType": "checkout.paid",
				  "payload": { "checkoutSessionId": "%s", "customerId": "%s", "source": "CART" } }
				""".formatted(UUID.randomUUID(), customerId);

		assertThat(handler.handle(event)).isEqualTo(CheckoutPaidHandler.Outcome.FAILED_REVIEW);
		assertThat(handler.handle(event)).isEqualTo(CheckoutPaidHandler.Outcome.DUPLICATE);
		assertThat(handler.handle("{ not json")).isEqualTo(CheckoutPaidHandler.Outcome.UNREADABLE);
		assertThat(quantities()).containsExactlyEntriesOf(Map.of(shirt, 2));
		assertThat(jdbc.queryForObject("SELECT state FROM inbox_events WHERE event_id = 'evt-bad'", String.class))
				.isEqualTo("FAILED_REVIEW");
	}

	private String cartEvent(String eventId, UUID checkoutSessionId, Map<UUID, Integer> lines) {
		String items = lines.entrySet().stream()
				.map(e -> "{\"cartItemId\":\"" + e.getKey() + "\",\"quantity\":" + e.getValue() + "}")
				.collect(Collectors.joining(","));
		return """
				{ "eventId": "%s", "eventType": "checkout.paid", "occurredAt": "2026-11-20T02:00:00Z",
				  "producer": "order-service", "aggregateId": "%s", "version": 1,
				  "payload": { "checkoutSessionId": "%s", "customerId": "%s", "source": "CART",
				               "paidAt": "2026-11-20T02:00:00Z", "cartItems": [%s],
				               "orders": [ { "orderId": "%s", "sellerId": "%s", "totalAmountVnd": 300000,
				                             "commissionBps": 500 } ] } }
				""".formatted(eventId, checkoutSessionId, checkoutSessionId, customerId, items, UUID.randomUUID(),
				UUID.randomUUID());
	}

	private CartItem line(UUID productId) {
		Optional<CartItem> line = store.contents(customerId).items().stream()
				.filter(i -> i.getProductId().equals(productId)).findFirst();
		return line.orElseThrow();
	}

	private Map<UUID, Integer> quantities() {
		List<CartItem> items = store.contents(customerId).items();
		return items.stream().collect(Collectors.toMap(CartItem::getProductId, CartItem::getQuantity));
	}

}
