package com.example.cart_service.event;

import com.example.cart_service.cart.domain.Cart;
import com.example.cart_service.cart.domain.CartItem;
import com.example.cart_service.cart.domain.CartItemRepository;
import com.example.cart_service.cart.domain.CartRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Applies {@code checkout.paid} (API §7.2) to the cart, following SAD A.5.3 and REQ-ORD-005:
 * <ul>
 * <li>source BUY_NOW: the cart is never touched (G-16, UC-B03.ALT-05);</li>
 * <li>source CART: for each snapshot line {@code { cartItemId, quantity }} still in the customer's cart, subtract
 * {@code min(snapshot, current)}; a line that reaches 0 is deleted. Units added after the snapshot stay. A line deleted
 * and added again has a new id, so it is not decremented by mistake.</li>
 * </ul>
 * The inbox row and the cart change commit in one local transaction. A redelivered event (same eventId) or another
 * event for the same checkout (same business key) changes nothing (UC-B03.POST-01, AT-21). A payload that cannot be
 * applied is kept as FAILED_REVIEW and logged instead of being dropped silently (SAD A.5.2).
 */
@Service
public class CheckoutPaidHandler {

	public static final String EVENT_TYPE = "checkout.paid";

	private static final Logger log = LoggerFactory.getLogger(CheckoutPaidHandler.class);

	public enum Outcome {
		APPLIED, DUPLICATE, FAILED_REVIEW, UNREADABLE
	}

	private final JsonMapper json;

	private final JdbcTemplate jdbc;

	private final CartRepository carts;

	private final CartItemRepository items;

	private final Clock clock;

	public CheckoutPaidHandler(JsonMapper json, JdbcTemplate jdbc, CartRepository carts, CartItemRepository items,
			Clock clock) {
		this.json = json;
		this.jdbc = jdbc;
		this.carts = carts;
		this.items = items;
		this.clock = clock;
	}

	record Envelope(String eventId, String eventType, String producer, String aggregateId, JsonNode payload) {
	}

	record Payload(UUID checkoutSessionId, UUID customerId, String source, List<Line> cartItems) {
	}

	record Line(UUID cartItemId, Integer quantity) {
	}

	@Transactional
	public Outcome handle(String message) {
		Envelope envelope;
		try {
			envelope = json.readValue(message, Envelope.class);
		}
		catch (JacksonException ex) {
			log.error("Unreadable checkout.paid message, not applied: {}", ex.getOriginalMessage());
			return Outcome.UNREADABLE;
		}
		if (envelope.eventId() == null || envelope.eventId().isBlank() || envelope.eventId().length() > 100) {
			log.error("checkout.paid message without a usable eventId, not applied");
			return Outcome.UNREADABLE;
		}
		String eventId = envelope.eventId();
		String hash = sha256(envelope.payload() == null ? "" : envelope.payload().toString());
		Optional<String> seen = jdbc.query("SELECT payload_hash FROM inbox_events WHERE event_id = ?",
				(rs, n) -> rs.getString(1), eventId).stream().findFirst();
		if (seen.isPresent()) {
			if (!seen.get().equals(hash)) {
				log.warn("Event {} redelivered with a different payload; the first one stands", eventId);
			}
			return Outcome.DUPLICATE;
		}

		Payload payload = parse(envelope);
		String problem = problem(envelope, payload);
		if (problem != null) {
			log.error("checkout.paid {} kept for review: {}", eventId, problem);
			insert(eventId, "invalid:" + eventId, hash, "FAILED_REVIEW", problem);
			return Outcome.FAILED_REVIEW;
		}

		String businessKey = EVENT_TYPE + ":" + payload.checkoutSessionId();
		if (insert(eventId, businessKey, hash, "APPLIED", null) == 0) {
			// Same checkout already applied under another eventId: record this one, change nothing.
			insert(eventId, businessKey, hash, "SKIPPED", "checkout already applied");
			return Outcome.DUPLICATE;
		}
		if ("CART".equals(payload.source())) {
			decrement(payload);
		}
		return Outcome.APPLIED;
	}

	private void decrement(Payload payload) {
		Optional<Cart> cart = carts.lockByCustomerId(payload.customerId());
		if (cart.isEmpty()) {
			return;
		}
		Instant now = clock.instant();
		boolean changed = false;
		for (Line snapshot : payload.cartItems()) {
			Optional<CartItem> current = items.findByCartItemIdAndCartId(snapshot.cartItemId(),
					cart.get().getCartId());
			if (current.isEmpty()) {
				continue;
			}
			CartItem line = current.get();
			int remaining = line.getQuantity() - Math.min(snapshot.quantity(), line.getQuantity());
			if (remaining <= 0) {
				items.delete(line);
			}
			else {
				line.changeQuantity(remaining, now);
			}
			changed = true;
		}
		if (changed) {
			cart.get().touch(now);
		}
	}

	private Payload parse(Envelope envelope) {
		if (envelope.payload() == null) {
			return null;
		}
		try {
			return json.treeToValue(envelope.payload(), Payload.class);
		}
		catch (JacksonException ex) {
			return null;
		}
	}

	private static String problem(Envelope envelope, Payload payload) {
		if (!EVENT_TYPE.equals(envelope.eventType())) {
			return "unexpected eventType " + envelope.eventType();
		}
		if (payload == null || payload.checkoutSessionId() == null || payload.customerId() == null) {
			return "payload without checkoutSessionId/customerId";
		}
		if ("BUY_NOW".equals(payload.source())) {
			return null;
		}
		if (!"CART".equals(payload.source())) {
			return "unknown source " + payload.source();
		}
		if (payload.cartItems() == null) {
			return "CART checkout without cartItems";
		}
		for (Line line : payload.cartItems()) {
			if (line == null || line.cartItemId() == null || line.quantity() == null || line.quantity() < 1) {
				return "invalid cartItems entry";
			}
		}
		return null;
	}

	private int insert(String eventId, String businessKey, String hash, String state, String error) {
		Timestamp now = Timestamp.from(clock.instant());
		return jdbc.update("""
				INSERT INTO inbox_events (event_id, event_type, business_key, payload_hash, state, last_error,
				                          received_at, applied_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT DO NOTHING
				""", eventId, EVENT_TYPE, businessKey, hash, state, error, now,
				"APPLIED".equals(state) ? now : null);
	}

	private static String sha256(String value) {
		try {
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
