package com.example.order_service.client;

import feign.FeignException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@FeignClient(name = "cart-service", contextId = "cartCheckoutItems",
		url = "${order.cart.base-url:http://localhost:8080}", configuration = CartCheckoutItemsClient.Options.class)
public interface CartCheckoutItemsClient {
	int MAX_LINES = 50;

	@PostMapping("/internal/v1/cart/checkout-items")
	ResponseEntity<JsonNode> read(@RequestHeader("X-Service-Token") String serviceToken, @RequestBody Request request);

	record Request(UUID customerId, List<UUID> cartItemIds) {}
	record Snapshot(UUID cartId, UUID customerId, List<Line> items) {}
	record Line(UUID cartItemId, UUID productId, int quantity, long rowVersion) {}

	class Options {
		@Bean
		feign.Request.Options cartCheckoutOptions() {
			Duration timeout = Duration.ofSeconds(2);
			return new feign.Request.Options(timeout, timeout, false);
		}
	}

	@Component
	class Reader {
		private final CartCheckoutItemsClient client;
		private final JsonMapper json;
		private final String serviceToken;

		public Reader(CartCheckoutItemsClient client, JsonMapper json,
				@Value("${order.cart.service-token:}") String serviceToken) {
			this.client = client;
			this.json = json;
			this.serviceToken = serviceToken;
		}

		/**
		 * MissingItems represents the verified Cart 404 for missing or foreign lines.
		 * Callers map that to 404 for Preview and quote changed for a fresh Pay.
		 */
		public Snapshot read(UUID customerId, List<UUID> cartItemIds) {
			if (serviceToken.isBlank()) {
				throw new Unavailable();
			}
			try {
				var response = client.read(serviceToken, new Request(customerId, List.copyOf(cartItemIds)));
				var body = response.getBody();
				if (response.getStatusCode().value() != 200 || body == null) {
					throw new Unavailable();
				}
				return snapshot(body.path("data"), customerId, cartItemIds);
			} catch (FeignException error) {
				if (error.status() == 404 && hasNotFoundCode(error.contentUTF8())) {
					throw new MissingItems();
				}
				throw new Unavailable();
			} catch (IllegalArgumentException error) {
				throw new Unavailable();
			}
		}

		private boolean hasNotFoundCode(String body) {
			try {
				return "NOT_FOUND".equals(json.readTree(body).path("error").path("code").asString());
			} catch (JacksonException error) {
				return false;
			}
		}

		private Snapshot snapshot(JsonNode data, UUID customerId, List<UUID> cartItemIds) {
			UUID cartId = uuid(data.path("cartId"));
			UUID owner = uuid(data.path("customerId"));
			JsonNode items = data.path("items");
			if (!customerId.equals(owner) || !items.isArray() || items.size() != cartItemIds.size()) {
				throw new Unavailable();
			}
			var remaining = new HashSet<>(cartItemIds);
			List<Line> lines = new ArrayList<>(items.size());
			for (JsonNode item : items) {
				UUID cartItemId = uuid(item.path("cartItemId"));
				UUID productId = uuid(item.path("productId"));
				JsonNode quantity = item.path("quantity");
				JsonNode version = item.path("rowVersion");
				if (!remaining.remove(cartItemId) || !quantity.isIntegralNumber() || !quantity.canConvertToInt()
						|| quantity.asInt() < 1 || quantity.asInt() > 99 || !version.isIntegralNumber()
						|| !version.canConvertToLong() || version.asLong() < 0) {
					throw new Unavailable();
				}
				lines.add(new Line(cartItemId, productId, quantity.asInt(), version.asLong()));
			}
			return new Snapshot(cartId, owner, List.copyOf(lines));
		}

		private UUID uuid(JsonNode node) {
			if (!node.isString() || node.asString().length() != 36) {
				throw new Unavailable();
			}
			return UUID.fromString(node.asString());
		}
	}

	class MissingItems extends RuntimeException {}
	class Unavailable extends RuntimeException {}
}
