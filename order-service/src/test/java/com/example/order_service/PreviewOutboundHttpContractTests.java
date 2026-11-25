package com.example.order_service;

import com.example.order_service.client.CheckoutIdentityClient;
import com.example.order_service.client.ProductBatchClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PreviewOutboundHttpContractTests {
	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final String ADDRESSES = "/api/v1/identity/customers/me/addresses";
	private static final String PRODUCT_BATCH = "/internal/v1/products/batch";
	private final AtomicReference<Request> request = new AtomicReference<>();
	private HttpServer server;
	private CheckoutIdentityClient identity;
	private ProductBatchClient products;

	@BeforeEach
	void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.start();
		String origin = "http://127.0.0.1:" + server.getAddress().getPort();
		identity = new CheckoutIdentityClient(origin, "order-to-identity");
		products = new ProductBatchClient(origin, "order-to-product");
	}

	@AfterEach
	void stop() {
		server.stop(0);
	}

	@Test
	void readsOnlyTheDefaultFromTheCustomerAddressBook() {
		UUID defaultId = UUID.randomUUID();
		reply(ADDRESSES, Map.of("data", List.of(address(UUID.randomUUID(), false), address(defaultId, true)),
				"meta", Map.of("maxAddresses", 3)));

		assertThat(identity.defaultAddress("customer-token").addressId()).isEqualTo(defaultId);
		assertThat(request.get()).isEqualTo(new Request("GET", "Bearer customer-token", null, null));
	}

	@Test
	void rejectsAmbiguousDefaultAddress() {
		reply(ADDRESSES, Map.of("data", List.of(address(UUID.randomUUID(), true), address(UUID.randomUUID(), true)),
				"meta", Map.of("maxAddresses", 3)));

		assertThatThrownBy(() -> identity.defaultAddress("customer-token"))
				.isInstanceOf(CheckoutIdentityClient.Unavailable.class);
	}

	@Test
	void rejectsIdentityResponseForDifferentCustomer() {
		UUID customer = UUID.randomUUID();
		reply("/internal/v1/identity/customers/" + customer + "/checkout-eligibility",
				Map.of("data", Map.of("userId", UUID.randomUUID(), "userStatus", "ACTIVE", "customerStatus", "ACTIVE",
						"paymentBlocked", false, "hasPhone", true, "addressCount", 1, "canCheckout", true, "authzVersion", 4)));

		assertThatThrownBy(() -> identity.eligibility(customer)).isInstanceOf(CheckoutIdentityClient.Unavailable.class);
		assertThat(request.get()).isEqualTo(new Request("GET", null, "order-to-identity", null));
	}

	@Test
	void rejectsProductBatchDataForAnUnrequestedProduct() {
		UUID productId = UUID.randomUUID();
		reply(PRODUCT_BATCH, Map.of("data", List.of(Map.of("productId", UUID.randomUUID(), "sellerId", UUID.randomUUID(),
				"priceVnd", 150000, "productName", "Product", "media", List.of(), "sellable", true))));

		assertThatThrownBy(() -> products.getProducts(List.of(productId)))
				.isInstanceOf(ProductBatchClient.ProductBatchUnavailable.class);
		JsonNode expectedBody = JSON.valueToTree(Map.of("productIds", List.of(productId)));
		assertThat(request.get()).isEqualTo(new Request("POST", null, "order-to-product", expectedBody));
	}

	private void reply(String path, Map<String, ?> body) {
		byte[] json = JSON.writeValueAsBytes(body);
		server.createContext(path, exchange -> {
			try (exchange) {
				byte[] bytes = exchange.getRequestBody().readAllBytes();
				request.set(new Request(exchange.getRequestMethod(), exchange.getRequestHeaders().getFirst("Authorization"),
						exchange.getRequestHeaders().getFirst("X-Service-Token"), bytes.length == 0 ? null : JSON.readTree(bytes)));
				exchange.getResponseHeaders().set("Content-Type", "application/json");
				exchange.sendResponseHeaders(200, json.length);
				exchange.getResponseBody().write(json);
			}
		});
	}

	private Map<String, ?> address(UUID id, boolean isDefault) {
		return Map.of("addressId", id, "recipientName", "Receiver", "recipientPhone", "0900000000",
				"provinceCode", "01", "provinceName", "Province", "wardCode", "00001", "wardName", "Ward",
				"addressDetail", "Address", "isDefault", isDefault, "createdAt", "2026-11-21T00:00:00Z");
	}

	private record Request(String method, String bearer, String token, JsonNode body) {
	}
}
