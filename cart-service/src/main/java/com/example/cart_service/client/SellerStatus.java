package com.example.cart_service.client;

import java.util.UUID;

/** One entry of Identity's {@code GET /internal/v1/identity/sellers/status}; {@code sellable} is G-06. */
public record SellerStatus(UUID sellerId, boolean sellable) {
}
