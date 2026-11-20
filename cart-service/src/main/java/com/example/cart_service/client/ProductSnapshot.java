package com.example.cart_service.client;

import java.util.UUID;

/**
 * One entry of Product's {@code POST /internal/v1/products/batch} (API §7.1): current price, name, cover image and
 * status. {@code status} is SELLING, HIDDEN or REMOVED.
 */
public record ProductSnapshot(UUID productId, UUID sellerId, Long priceVnd, String productName, String coverImageUrl,
		String status) {

	public boolean selling() {
		return "SELLING".equals(status);
	}

}
