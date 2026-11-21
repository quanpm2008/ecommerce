package com.example.cart_service.security;

import java.util.UUID;

/**
 * The verified caller of a cart endpoint: {@code customerId} is the token's {@code sub} as confirmed by Identity.
 * Owner ids in the body, query or headers are never trusted (API §3.1).
 */
public record CurrentCustomer(UUID customerId, boolean paymentBlocked) {

	public static final String ATTRIBUTE = "com.example.cart_service.security.CurrentCustomer";

}
