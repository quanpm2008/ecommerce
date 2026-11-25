package com.example.order_service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"eureka.client.enabled=false",
		"order.internal.tokens.product=product-inventory-test",
		"order.internal.tokens.cart=cart-inventory-test"
})
class OrderInternalHttpContractTests extends OrderPostgresTestSupport {

	private static final String PRODUCT = "product-inventory-test";

	@LocalServerPort
	private int port;
	@Autowired
	private JsonMapper json;
	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void createsWithoutHeaderAndReplaysWithOrWithoutHeader() throws Exception {
		UUID product = UUID.randomUUID();
		UUID seller = UUID.randomUUID();
		assertSuccess(post(body(product, seller, 10), PRODUCT, null), 201);
		jdbc.update("update orders.inventory set on_hand_quantity = 25, row_version = 3 where product_id = ?", product);
		var before = jdbc.queryForMap("select * from orders.inventory where product_id = ?", product);
		assertSuccess(post(body(product, seller, 12), PRODUCT, null), 200);
		assertSuccess(post(body(product, seller, 10), PRODUCT, UUID.randomUUID().toString()), 200);
		var conflict = post(body(product, UUID.randomUUID(), 10), PRODUCT, null);
		assertThat(conflict.statusCode()).isEqualTo(409);
		assertThat(json.readTree(conflict.body()).path("error").path("code").asString())
				.isEqualTo("INVENTORY_PROVISION_CONFLICT");
		assertThat(jdbc.queryForMap("select * from orders.inventory where product_id = ?", product)).isEqualTo(before);
	}

	@Test
	void provisionRequiresProductCredentialAndValidRequest() throws Exception {
		String payload = body(UUID.randomUUID(), UUID.randomUUID(), 0);
		assertThat(post(payload, null, null).statusCode()).isEqualTo(401);
		assertThat(post(payload, "wrong", null).statusCode()).isEqualTo(401);
		assertThat(post(payload, "cart-inventory-test", null).statusCode()).isEqualTo(403);
		assertThat(post("{}", PRODUCT, null).statusCode()).isEqualTo(400);
		assertThat(post("{", PRODUCT, null).statusCode()).isEqualTo(400);
		assertThat(post(body(UUID.randomUUID(), UUID.randomUUID(), -1), PRODUCT, null).statusCode()).isEqualTo(400);
		assertSuccess(post(payload, PRODUCT, UUID.randomUUID().toString()), 201);
	}

	private String body(UUID product, UUID seller, int quantity) {
		return json.writeValueAsString(Map.of("productId", product, "sellerId", seller, "onHandQuantity", quantity));
	}

	private void assertSuccess(HttpResponse<String> response, int status) {
		assertThat(response.statusCode()).isEqualTo(status);
		assertThat(json.readTree(response.body())).isEqualTo(json.readTree("{\"data\":null}"));
	}

	private HttpResponse<String> post(String payload, String token, String key) throws Exception {
		var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/internal/v1/inventory"))
				.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(payload));
		if (token != null) {
			request.header("X-Service-Token", token);
		}
		if (key != null) {
			request.header("Idempotency-Key", key);
		}
		try (var client = HttpClient.newHttpClient()) {
			return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
		}
	}
}
