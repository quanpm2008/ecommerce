package com.example.order_service;

import com.example.order_service.client.ProductBatchClient;
import com.example.order_service.client.ProductBatchClient.ProductBatchUnavailable;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductBatchHttpContractTests {
	private static final UUID FIRST = UUID.randomUUID();
	private static final UUID SECOND = UUID.randomUUID();
	private static final UUID SELLER = UUID.randomUUID();
	private final JsonMapper json = new JsonMapper();
	private final AtomicReference<Call> call = new AtomicReference<>();
	private final AtomicReference<Reply> reply = new AtomicReference<>();
	private HttpServer server;

	@BeforeEach
	void startProduct() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/internal/v1/products/batch", exchange -> {
			try (exchange) {
				call.set(new Call(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
						exchange.getRequestHeaders().getFirst("X-Service-Token"),
						exchange.getRequestHeaders().getFirst("Authorization"),
						exchange.getRequestHeaders().getFirst("Content-Type"),
						new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
				Reply response = reply.get();
				byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().set("Content-Type", "application/json");
				exchange.sendResponseHeaders(response.status(), body.length);
				exchange.getResponseBody().write(body);
			}
		});
		server.start();
	}

	@AfterEach
	void stopProduct() {
		server.stop(0);
	}

	@Test
	void sendsObjectWithProductIdsAndServiceCredentialAndDecodesBusinessOutcomes() {
		respond(200, Map.of("data", List.of(item(FIRST, true), item(SECOND, false))));
		var products = client().getProducts(List.of(FIRST, SECOND));
		assertThat(call.get().method()).isEqualTo("POST");
		assertThat(call.get().path()).isEqualTo("/internal/v1/products/batch");
		assertThat(call.get().service()).isEqualTo("order-fixture-token");
		assertThat(call.get().userAuthorization()).isNull();
		assertThat(call.get().contentType()).startsWith("application/json");
		assertThat(json.readTree(call.get().body())).isEqualTo(json.valueToTree(Map.of("productIds", List.of(FIRST, SECOND))));
		assertThat(products).extracting(ProductBatchClient.ProductInfo::sellable).containsExactly(true, false);
		assertThat(products.getFirst().sellerId()).isEqualTo(SELLER);
		assertThat(products.getFirst().priceVnd()).isEqualTo(125_000L);
		assertThat(products.getFirst().media().isEmpty()).isTrue();
	}

	@Test
	void emptyConfirmedBatchIsDifferentFromHttpFailureAndMalformedEvidence() {
		respond(200, Map.of("data", List.of()));
		assertThat(client().getProducts(List.of(FIRST))).isEmpty();
		respond(500, Map.of("error", Map.of("code", "PRODUCT_ERROR")));
		assertThatThrownBy(() -> client().getProducts(List.of(FIRST))).isInstanceOf(ProductBatchUnavailable.class);
		respond(200, Map.of("data", List.of(Map.of("productId", FIRST, "sellerId", SELLER))));
		assertThatThrownBy(() -> client().getProducts(List.of(FIRST))).isInstanceOf(ProductBatchUnavailable.class);
		respond(200, Map.of("data", List.of(item(SECOND, true))));
		assertThatThrownBy(() -> client().getProducts(List.of(FIRST))).isInstanceOf(ProductBatchUnavailable.class);
	}

	@Test
	void connectionFailureIsNotInterpretedAsUnsellable() {
		var client = client();
		server.stop(0);
		assertThatThrownBy(() -> client.getProducts(List.of(FIRST))).isInstanceOf(ProductBatchUnavailable.class);
	}

	private void respond(int status, Map<String, ?> body) {
		reply.set(new Reply(status, json.writeValueAsString(body)));
	}

	private static Map<String, ?> item(UUID id, boolean sellable) {
		return Map.of("productId", id, "sellerId", SELLER, "priceVnd", 125_000L,
				"productName", "Fixture", "media", List.of(), "sellable", sellable);
	}

	private ProductBatchClient client() {
		return new ProductBatchClient("http://127.0.0.1:" + server.getAddress().getPort(), "order-fixture-token");
	}

	private record Call(String method, String path, String service, String userAuthorization, String contentType, String body) {
	}

	private record Reply(int status, String body) {
	}
}
