package com.example.cart_service.client;

import com.example.cart_service.common.error.ApiException;
import com.example.cart_service.common.error.ErrorCode;
import com.example.cart_service.common.web.ApiResponse;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Feign-backed {@link IdentityGateway}, {@link ProductGateway} and {@link InventoryGateway}. Calls happen outside
 * any write transaction. A failing source is {@code SERVICE_UNAVAILABLE}: it is never read as "not sellable" or
 * "0 in stock", which would remove or block cart lines wrongly.
 */
final class FeignGateways {

	private static final Logger log = LoggerFactory.getLogger(FeignGateways.class);

	private static final Pattern ERROR_CODE = Pattern.compile("\"code\"\\s*:\\s*\"([A-Z_]+)\"");

	private FeignGateways() {
	}

	static <T> T call(String source, Supplier<ApiResponse<T>> request) {
		ApiResponse<T> response;
		try {
			response = request.get();
		}
		catch (RuntimeException ex) {
			log.warn("{} call failed: {}", source, ex.toString());
			throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE);
		}
		if (response == null || response.data() == null) {
			log.warn("{} returned no data", source);
			throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE);
		}
		return response.data();
	}

	@Component
	static class Identity implements IdentityGateway {

		private final IdentityClient client;

		Identity(IdentityClient client) {
			this.client = client;
		}

		@Override
		public AuthorizationContext authorizationContext(String accessToken) {
			try {
				ApiResponse<AuthorizationContext> response = client.authorizationContext("Bearer " + accessToken);
				if (response == null || response.data() == null) {
					throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE);
				}
				return response.data();
			}
			catch (FeignException.Unauthorized ex) {
				// The user's token is the problem: pass Identity's code on so the client can refresh or log in.
				throw new ApiException(userTokenError(ex.contentUTF8()));
			}
			catch (ApiException ex) {
				throw ex;
			}
			catch (RuntimeException ex) {
				log.warn("Identity authorization context failed: {}", ex.toString());
				throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE);
			}
		}

		@Override
		public Map<UUID, Boolean> sellable(Collection<UUID> sellerIds) {
			if (sellerIds.isEmpty()) {
				return Map.of();
			}
			List<SellerStatus> statuses = call("Identity seller status",
					() -> client.sellerStatuses(List.copyOf(sellerIds)));
			Map<UUID, Boolean> result = new HashMap<>();
			statuses.forEach(status -> result.put(status.sellerId(), status.sellable()));
			return result;
		}

		@Override
		public Map<UUID, String> sellerNames(Collection<UUID> sellerIds) {
			Map<UUID, String> names = new HashMap<>();
			for (UUID sellerId : sellerIds) {
				try {
					ApiResponse<IdentityClient.SellerPublic> response = client.sellerPublic(sellerId);
					if (response != null && response.data() != null && response.data().sellerName() != null) {
						names.put(sellerId, response.data().sellerName());
					}
				}
				catch (RuntimeException ex) {
					log.debug("Seller name of {} unavailable: {}", sellerId, ex.toString());
				}
			}
			return names;
		}

		private static ErrorCode userTokenError(String body) {
			Matcher matcher = ERROR_CODE.matcher(body == null ? "" : body);
			String code = matcher.find() ? matcher.group(1) : "";
			return switch (code) {
				case "TOKEN_EXPIRED" -> ErrorCode.TOKEN_EXPIRED;
				case "TOKEN_STALE" -> ErrorCode.TOKEN_STALE;
				default -> ErrorCode.UNAUTHENTICATED;
			};
		}

	}

	@Component
	static class Product implements ProductGateway {

		private final ProductClient client;

		Product(ProductClient client) {
			this.client = client;
		}

		@Override
		public Map<UUID, ProductSnapshot> products(Collection<UUID> productIds) {
			if (productIds.isEmpty()) {
				return Map.of();
			}
			List<ProductSnapshot> products = call("Product batch", () -> client.batch(List.copyOf(productIds)));
			Map<UUID, ProductSnapshot> result = new HashMap<>();
			products.forEach(product -> result.put(product.productId(), product));
			return result;
		}

	}

	@Component
	static class Inventory implements InventoryGateway {

		private final InventoryClient client;

		Inventory(InventoryClient client) {
			this.client = client;
		}

		@Override
		public Map<UUID, Integer> availability(Collection<UUID> productIds) {
			if (productIds.isEmpty()) {
				return Map.of();
			}
			List<InventoryClient.Availability> rows = call("Order inventory availability",
					() -> client.availability(List.copyOf(productIds)));
			Map<UUID, Integer> result = new HashMap<>();
			rows.forEach(row -> result.put(row.productId(),
					row.availableQuantity() == null ? 0 : Math.max(0, row.availableQuantity())));
			return result;
		}

	}

}
