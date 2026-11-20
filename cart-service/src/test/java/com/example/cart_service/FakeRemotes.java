package com.example.cart_service;

import com.example.cart_service.client.AuthorizationContext;
import com.example.cart_service.client.IdentityGateway;
import com.example.cart_service.client.InventoryGateway;
import com.example.cart_service.client.ProductGateway;
import com.example.cart_service.client.ProductSnapshot;
import com.example.cart_service.common.error.ApiException;
import com.example.cart_service.common.error.ErrorCode;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Identity, Product and Order in place of the real services. Tokens map to authorization contexts; products, sellers
 * and stock are set per test; each source can be switched off to simulate an outage (503).
 */
public class FakeRemotes implements IdentityGateway, ProductGateway, InventoryGateway {

	private final Map<String, Object> tokens = new ConcurrentHashMap<>();

	private final Map<UUID, ProductSnapshot> products = new ConcurrentHashMap<>();

	private final Map<UUID, Boolean> sellers = new ConcurrentHashMap<>();

	private final Map<UUID, String> sellerNames = new ConcurrentHashMap<>();

	private final Map<UUID, Integer> stock = new ConcurrentHashMap<>();

	public volatile boolean identityUp = true;

	public volatile boolean productUp = true;

	public volatile boolean inventoryUp = true;

	public void reset() {
		tokens.clear();
		products.clear();
		sellers.clear();
		sellerNames.clear();
		stock.clear();
		identityUp = true;
		productUp = true;
		inventoryUp = true;
	}

	/** An ACTIVE customer with {@code cart:manage:own}; returns the bearer token to send. */
	public String activeCustomer(UUID customerId) {
		return token(context(customerId, "ACTIVE", "ACTIVE", List.of("cart:manage:own", "checkout:create:own")));
	}

	public String token(AuthorizationContext context) {
		String token = "token-" + UUID.randomUUID();
		tokens.put(token, context);
		return token;
	}

	public String tokenFailing(ErrorCode code) {
		String token = "token-" + UUID.randomUUID();
		tokens.put(token, code);
		return token;
	}

	public static AuthorizationContext context(UUID userId, String userStatus, String customerStatus,
			List<String> permissions) {
		AuthorizationContext.CustomerAccess customer = customerStatus == null ? null
				: new AuthorizationContext.CustomerAccess(customerStatus,
						"BANNED".equals(customerStatus) ? "OPEN_ORDERS_ONLY" : "FULL", "BANNED".equals(customerStatus));
		return new AuthorizationContext(userId, userStatus, 1, List.of("CUSTOMER"), permissions, customer, true);
	}

	public void seller(UUID sellerId, String name, boolean sellable) {
		sellers.put(sellerId, sellable);
		if (name == null) {
			sellerNames.remove(sellerId);
		}
		else {
			sellerNames.put(sellerId, name);
		}
	}

	public void product(UUID productId, UUID sellerId, String name, long price, String status, int available) {
		products.put(productId, new ProductSnapshot(productId, sellerId, price, name, "https://cdn/" + productId,
				status));
		stock.put(productId, available);
	}

	public void status(UUID productId, String status) {
		ProductSnapshot p = products.get(productId);
		products.put(productId, new ProductSnapshot(p.productId(), p.sellerId(), p.priceVnd(), p.productName(),
				p.coverImageUrl(), status));
	}

	public void stock(UUID productId, int available) {
		stock.put(productId, available);
	}

	@Override
	public AuthorizationContext authorizationContext(String accessToken) {
		if (!identityUp) {
			throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE);
		}
		Object entry = tokens.get(accessToken);
		if (entry instanceof ErrorCode code) {
			throw new ApiException(code);
		}
		if (entry == null) {
			throw new ApiException(ErrorCode.UNAUTHENTICATED);
		}
		return (AuthorizationContext) entry;
	}

	@Override
	public Map<UUID, Boolean> sellable(Collection<UUID> sellerIds) {
		if (!identityUp) {
			throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE);
		}
		Map<UUID, Boolean> result = new HashMap<>();
		sellerIds.forEach(id -> {
			if (sellers.containsKey(id)) {
				result.put(id, sellers.get(id));
			}
		});
		return result;
	}

	@Override
	public Map<UUID, String> sellerNames(Collection<UUID> sellerIds) {
		Map<UUID, String> result = new HashMap<>();
		if (identityUp) {
			sellerIds.forEach(id -> {
				if (sellerNames.containsKey(id)) {
					result.put(id, sellerNames.get(id));
				}
			});
		}
		return result;
	}

	@Override
	public Map<UUID, ProductSnapshot> products(Collection<UUID> productIds) {
		if (!productUp) {
			throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE);
		}
		Map<UUID, ProductSnapshot> result = new HashMap<>();
		productIds.forEach(id -> {
			if (products.containsKey(id)) {
				result.put(id, products.get(id));
			}
		});
		return result;
	}

	@Override
	public Map<UUID, Integer> availability(Collection<UUID> productIds) {
		if (!inventoryUp) {
			throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE);
		}
		Map<UUID, Integer> result = new HashMap<>();
		productIds.forEach(id -> {
			if (stock.containsKey(id)) {
				result.put(id, stock.get(id));
			}
		});
		return result;
	}

	@TestConfiguration(proxyBeanMethods = false)
	public static class Configuration {

		@Bean
		@Primary
		FakeRemotes fakeRemotes() {
			return new FakeRemotes();
		}

	}

}
