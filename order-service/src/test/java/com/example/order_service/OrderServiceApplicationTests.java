package com.example.order_service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"eureka.client.enabled=false",
		"order.internal.tokens.product=product-inventory-test",
		"order.internal.tokens.cart=cart-inventory-test"
})
class OrderServiceApplicationTests extends OrderPostgresTestSupport {
	private static final String AVAILABILITY = "/internal/v1/inventory/availability";
	private static final String PRODUCT = "product-inventory-test";
	private static final String CART = "cart-inventory-test";

	@LocalServerPort
	private int port;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private JsonMapper json;

	@Test
	void postgresqlFlywayAndInventoryConstraints() {
		Integer version = jdbc.queryForObject("select current_setting('server_version_num')::integer", Integer.class);
		assertThat(version).isBetween(160000, 169999);
		assertThat(jdbc.queryForObject("""
				select count(*) from orders.flyway_schema_history where version = '1' and success
				""", Integer.class)).isEqualTo(1);
		UUID product = addStock(8, 3);
		assertThatThrownBy(() -> jdbc.update("update orders.inventory set on_hand_quantity = 2 where product_id = ?", product))
				.isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("inventory_quantities_valid");
		assertThatThrownBy(() -> jdbc.update("update orders.inventory set reserved_quantity = -1 where product_id = ?", product))
				.isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("inventory_quantities_valid");
		assertThat(jdbc.queryForObject("select on_hand_quantity - reserved_quantity from orders.inventory where product_id = ?",
				Integer.class, product)).isEqualTo(5);
	}

	@Test
	void productAndCartReadCommittedAvailability() throws Exception {
		UUID first = addStock(9, 4);
		UUID second = addStock(3, 3);
		UUID unrequested = addStock(20, 1);
		String csv = AVAILABILITY + "?productIds=" + first + "," + second;
		String repeated = AVAILABILITY + "?productIds=" + first + "&productIds=" + second;
		for (var response : new HttpResponse[]{get(csv, PRODUCT), get(repeated, CART)}) {
			assertThat(response.statusCode()).isEqualTo(200);
			var data = json.readTree(response.body().toString()).path("data");
			assertThat(data.size()).isEqualTo(2);
			for (var entry : data) {
				String id = entry.path("productId").asString();
				assertThat(id).isIn(first.toString(), second.toString()).isNotEqualTo(unrequested.toString());
				assertThat(entry.path("availableQuantity").asInt()).isEqualTo(id.equals(first.toString()) ? 5 : 0);
			}
		}
	}

	@Test
	void availabilityRejectsMissingOrUnrecognizedCredential() throws Exception {
		String uri = AVAILABILITY + "?productIds=" + UUID.randomUUID();
		assertError(get(uri, null), 401, "UNAUTHENTICATED");
		assertError(get(uri, "unrecognized-service"), 401, "UNAUTHENTICATED");
	}

	@Test
	void availabilityValidatesProductIds() throws Exception {
		assertError(get(AVAILABILITY, PRODUCT), 400, "VALIDATION_ERROR");
		assertError(get(AVAILABILITY + "?productIds=garbage", PRODUCT), 400, "VALIDATION_ERROR");
	}

	private UUID addStock(int onHand, int reserved) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into orders.inventory (product_id, on_hand_quantity, reserved_quantity) values (?, ?, ?)",
				id, onHand, reserved);
		return id;
	}

	private HttpResponse<String> get(String path, String token) throws Exception {
		var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET();
		if (token != null) {
			request.header("X-Service-Token", token);
		}
		try (var client = HttpClient.newHttpClient()) {
			return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
		}
	}

	private void assertError(HttpResponse<String> response, int status, String code) {
		assertThat(response.statusCode()).isEqualTo(status);
		var error = json.readTree(response.body()).path("error");
		assertThat(error.path("code").asString()).isEqualTo(code);
		assertThat(error.path("requestId").asString()).isEqualTo(response.headers().firstValue("X-Request-Id").orElseThrow());
	}
}
