package com.example.gateway_service.routing;

import java.util.List;

/**
 * The public route table (API §2.2): each service, by its Eureka name ({@code spring.application.name}), and the public
 * paths it owns. Services serve these full paths, so requests are forwarded with the path unchanged.
 * <p>
 * Catalog's public paths have no {@code /catalog} prefix; {@code /api/v1/identity/media/upload-urls} is Identity's
 * (under its prefix) while {@code /api/v1/media/upload-urls} is Catalog's. Anything not listed here is a 404 at the
 * gateway, including other {@code /seller/**} and {@code /admin/**} paths, and {@code /internal/**} is never listed.
 */
public enum ServiceRoute {

	IDENTITY("identity-service", "/api/v1/identity/**"),
	CATALOG("product-service", "/api/v1/categories/**", "/api/v1/products/**", "/api/v1/seller/products/**",
			"/api/v1/admin/products/**", "/api/v1/admin/categories/**", "/api/v1/media/upload-urls"),
	CART("cart-service", "/api/v1/cart/**"),
	ORDER("order-service", "/api/v1/orders/**"),
	PAYMENT("payment-service", "/api/v1/payments/**"),
	FINANCE("finance-service", "/api/v1/finance/**"),
	NOTIFICATION("notification-service", "/api/v1/notifications/**");

	private final String serviceId;

	private final List<String> paths;

	ServiceRoute(String serviceId, String... paths) {
		this.serviceId = serviceId;
		this.paths = List.of(paths);
	}

	public String serviceId() {
		return serviceId;
	}

	public List<String> paths() {
		return paths;
	}

}
