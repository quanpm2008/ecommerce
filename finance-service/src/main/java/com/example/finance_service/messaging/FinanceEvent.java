package com.example.finance_service.messaging;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public final class FinanceEvent {
	private FinanceEvent() {
	}

	public record Paid(UUID eventId, UUID checkoutId, UUID paymentId, List<PaidOrder> orders) {
	}

	public record PaidOrder(UUID orderId, UUID sellerId, long grossVnd, int commissionBps) {
	}

	public record Approved(UUID eventId, UUID sellerId) {
	}

	public static Paid checkoutPaid(JsonMapper json, String raw) {
		var root = json.readTree(raw);
		if (!"checkout.paid".equals(root.path("eventType").asString(null))) {
			return null;
		}
		var payload = root.path("payload");
		var checkoutId = uuid(payload, "checkoutSessionId");
		var eventId = envelope(root, "order-service", checkoutId);
		var paymentId = uuid(payload, "paymentId");
		uuid(payload, "customerId");
		instant(payload, "paidAt");
		String source = payload.path("source").asString(null);
		var cartItems = payload.path("cartItems");
		if (!"SERVICE".equals(root.path("actor").path("kind").asString(null))
				|| !("BUY_NOW".equals(source) || "CART".equals(source))
				|| !cartItems.isArray() || ("BUY_NOW".equals(source) && !cartItems.isEmpty())) {
			throw new IllegalArgumentException("Invalid checkout.paid");
		}
		var lines = payload.path("orders");
		if (!lines.isArray() || lines.isEmpty()) {
			throw new IllegalArgumentException("No orders in checkout.paid");
		}
		var seen = new HashSet<UUID>();
		var orders = new ArrayList<PaidOrder>(lines.size());
		for (var line : lines) {
			var id = uuid(line, "orderId");
			long gross = integer(line, "totalAmountVnd", 1, Long.MAX_VALUE);
			int rate = (int) integer(line, "commissionBps", 0, 10_000);
			if (!seen.add(id)) {
				throw new IllegalArgumentException("Repeated order in checkout.paid");
			}
			orders.add(new PaidOrder(id, uuid(line, "sellerId"), gross, rate));
		}
		return new Paid(eventId, checkoutId, paymentId, List.copyOf(orders));
	}

	static Approved sellerApproved(JsonMapper json, String raw) {
		var root = json.readTree(raw);
		if (!"seller.approved".equals(root.path("eventType").asString(null))) {
			return null;
		}
		var payload = root.path("payload");
		var sellerId = uuid(payload, "sellerId");
		var eventId = envelope(root, "identity-service", sellerId);
		var actor = root.path("actor");
		if (!sellerId.equals(uuid(payload, "userId")) || !"APPROVED".equals(payload.path("approvalStatus").asString(null))
				|| !"USER".equals(actor.path("kind").asString(null))
				|| !"ADMIN".equals(actor.path("context").asString(null))
				|| sellerId.equals(uuid(actor, "userId"))) {
			throw new IllegalArgumentException("Invalid seller.approved");
		}
		integer(payload, "authzVersion", 0, Long.MAX_VALUE);
		return new Approved(eventId, sellerId);
	}

	private static UUID envelope(JsonNode root, String producer, UUID aggregate) {
		if (!producer.equals(root.path("producer").asString(null)) || !aggregate.equals(uuid(root, "aggregateId"))) {
			throw new IllegalArgumentException("Invalid producer or aggregate");
		}
		integer(root, "version", 1, Long.MAX_VALUE);
		instant(root, "occurredAt");
		return uuid(root, "eventId");
	}

	private static UUID uuid(JsonNode node, String field) {
		var text = node.path(field).asString(null);
		if (text == null || text.length() != 36) {
			throw new IllegalArgumentException("Invalid UUID");
		}
		return UUID.fromString(text);
	}

	private static void instant(JsonNode node, String field) {
		var text = node.path(field).asString(null);
		if (text == null) {
			throw new IllegalArgumentException("Missing timestamp");
		}
		Instant.parse(text);
	}

	private static long integer(JsonNode node, String field, long min, long max) {
		var number = node.path(field);
		if (!number.isIntegralNumber() || !number.canConvertToLong() || number.asLong() < min || number.asLong() > max) {
			throw new IllegalArgumentException("Invalid integer");
		}
		return number.asLong();
	}
}
