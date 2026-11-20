package com.example.gateway_service.routing;

import java.util.List;

/**
 * The public route table (API §2.2): each service, by its Eureka name ({@code spring.application.name}), and the public
 * paths it owns.
 * <p>
 * Most services serve the full public path ({@code /api/v1/identity/...}), so the path is forwarded unchanged.
 * product-service serves its paths without the base path ({@code /products}, {@code /categories}, ...), so its route
 * drops {@code /api/v1} ({@link #stripsBasePath()}); its {@code /internal/v1/products/**} paths are never routed anyway.
 * <p>
 * Product's public paths have no service prefix (API §2.2; the old {@code /catalog} name is gone);
 * {@code /api/v1/identity/media/upload-urls} is Identity's (under its prefix) while {@code /api/v1/media/upload-urls} is
 * Product's. Anything not listed here is a 404 at the gateway, including other {@code /seller/**} and {@code /admin/**}
 * paths, and {@code /internal/**} is never listed.
 */
public enum ServiceRoute {

	IDENTITY("identity-service", false, "/api/v1/identity/**"),
	PRODUCT("product-service", true, "/api/v1/categories/**", "/api/v1/products/**", "/api/v1/seller/products/**",
			"/api/v1/admin/products/**", "/api/v1/admin/categories/**", "/api/v1/media/upload-urls"),
	CART("cart-service", false, "/api/v1/cart/**"),
	ORDER("order-service", false, "/api/v1/orders/**"),
	PAYMENT("payment-service", false, "/api/v1/payments/**"),
	FINANCE("finance-service", false, "/api/v1/finance/**"),
	NOTIFICATION("notification-service", false, "/api/v1/notifications/**");

	/** The public base path (API §3.1); every path above starts with it. */
	public static final String BASE_PATH = "/api/v1";

	private final String serviceId;

	private final boolean stripsBasePath;

	private final List<String> paths;

	ServiceRoute(String serviceId, boolean stripsBasePath, String... paths) {
		this.serviceId = serviceId;
		this.stripsBasePath = stripsBasePath;
		this.paths = List.of(paths);
	}

	public String serviceId() {
		return serviceId;
	}

	/** Whether the service is sent the path without {@link #BASE_PATH} ({@code /api/v1/products/p1} → {@code /products/p1}). */
	public boolean stripsBasePath() {
		return stripsBasePath;
	}

	public List<String> paths() {
		return paths;
	}

}
