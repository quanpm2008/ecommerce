package com.example.gateway_service.security;

import java.util.List;

import org.springframework.http.HttpMethod;

/**
 * The endpoints API v1.1 marks PUBLIC (§6.1 Identity, §6.2 Product): the only routed requests the gateway forwards
 * without an access token. Every other routed path needs a valid token here (401 otherwise); unrouted paths are 404.
 * Exact method and path: {@code POST /products/{id}/reviews} needs a token while {@code GET} does not. There is no guest
 * cart (API §9: Cart is CUSTOMER only). Add a row here when the spec adds a PUBLIC endpoint.
 */
final class PublicEndpoints {

	record Endpoint(HttpMethod method, String path) {
	}

	static final List<Endpoint> ALL = List.of(
			// Identity: sign-up, sign-in and password reset
			post("/api/v1/identity/auth/register"),
			post("/api/v1/identity/auth/register-seller"),
			post("/api/v1/identity/auth/login"),
			post("/api/v1/identity/auth/google"),
			post("/api/v1/identity/auth/refresh"),
			post("/api/v1/identity/auth/password/forgot"),
			post("/api/v1/identity/auth/password/reset"),
			// Identity: a REJECTED seller applies again after proving the email (API §6.1 #30, #31)
			post("/api/v1/identity/auth/seller-reapplication/challenge"),
			post("/api/v1/identity/auth/seller-reapplication"),
			// Identity: regions and public shop pages
			get("/api/v1/identity/regions/provinces"),
			get("/api/v1/identity/regions/provinces/*/wards"),
			get("/api/v1/identity/sellers"),
			get("/api/v1/identity/sellers/*"),
			// Product: browsing
			get("/api/v1/categories"),
			get("/api/v1/products"),
			get("/api/v1/products/*"),
			get("/api/v1/products/*/related"),
			get("/api/v1/products/*/reviews"));

	private PublicEndpoints() {
	}

	private static Endpoint get(String path) {
		return new Endpoint(HttpMethod.GET, path);
	}

	private static Endpoint post(String path) {
		return new Endpoint(HttpMethod.POST, path);
	}

}
