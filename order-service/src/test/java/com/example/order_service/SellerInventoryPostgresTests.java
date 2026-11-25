package com.example.order_service;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"eureka.client.enabled=false", "logging.level.root=WARN",
		"order.internal.tokens.product=seller-test-product", "order.identity.service-token=test-order",
		"order.product.service-token=test-order"
})
class SellerInventoryPostgresTests extends OrderPostgresTestSupport {
	private static final String INVENTORY = "/api/v1/orders/seller/inventory";
	private static final String PROVISION = "/internal/v1/inventory";
	private static final UUID SELLER = UUID.randomUUID();
	private static final UUID OTHER = UUID.randomUUID();
	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final Map<UUID, UUID> OWNERS = new ConcurrentHashMap<>();
	private static final RSAKey KEY = signingKey();
	private static final HttpServer SOURCES = startSources();

	@LocalServerPort
	private int port;
	@Autowired
	private JdbcTemplate jdbc;

	@DynamicPropertySource
	static void sources(DynamicPropertyRegistry properties) {
		String base = "http://127.0.0.1:" + SOURCES.getAddress().getPort();
		properties.add("order.identity.base-url", () -> base);
		properties.add("order.product.base-url", () -> base);
	}

	@AfterAll
	static void stopSources() {
		SOURCES.stop(0);
	}

	@Test
	void sellerUpdatesOnlyTheirStockWithVersionAndReservationGuards() throws Exception {
		UUID product = provision(SELLER);
		UUID foreign = provision(OTHER);
		jdbc.update("update orders.inventory set reserved_quantity = 4 where product_id = ?", product);
		var response = call("GET", INVENTORY + "?productIds=" + product + "," + foreign, SELLER, null);
		assertThat(response.statusCode()).isEqualTo(200);
		var data = JSON.readTree(response.body()).path("data");
		assertThat(data.size()).isEqualTo(1);
		assertThat(data.get(0).path("productId").asString()).isEqualTo(product.toString());
		assertThat(data.get(0).path("availableQuantity").asInt()).isEqualTo(6);
		String path = INVENTORY + "/" + product;
		assertThat(call("PUT", path, OTHER, adjustment(25, 0)).statusCode()).isEqualTo(404);
		var below = call("PUT", path, SELLER, adjustment(3, 0));
		assertThat(below.statusCode()).isEqualTo(422);
		var error = JSON.readTree(below.body()).path("error");
		assertThat(error.path("code").asString()).isEqualTo("STOCK_BELOW_RESERVED");
		assertThat(error.path("details").get(0).path("minAllowed").asInt()).isEqualTo(4);
		var updated = call("PUT", path, SELLER, adjustment(25, 0));
		assertThat(updated.statusCode()).isEqualTo(200);
		assertThat(JSON.readTree(updated.body()).path("data").path("version").asLong()).isEqualTo(1);
		var stale = call("PUT", path, SELLER, adjustment(12, 0));
		assertThat(stale.statusCode()).isEqualTo(409);
		assertThat(JSON.readTree(stale.body()).path("error").path("details").get(0)
				.path("current").path("onHandQuantity").asInt()).isEqualTo(25);
		assertThat(jdbc.queryForObject("select on_hand_quantity from orders.inventory where product_id = ?", Integer.class,
				product)).isEqualTo(25);
		assertThat(jdbc.queryForList("select quantity_delta from orders.stock_movement where product_id = ?", Integer.class,
				product)).containsExactly(15);
	}

	@Test
	void movementHistoryIsOwnedAndProvisionReplayDoesNotChangeIt() throws Exception {
		UUID product = provision(SELLER);
		String path = INVENTORY + "/" + product;
		assertThat(call("PUT", path, SELLER, adjustment(25, 0)).statusCode()).isEqualTo(200);
		var before = jdbc.queryForMap("select * from orders.inventory where product_id = ?", product);
		assertThat(provisionRequest(product, SELLER, 10)).isEqualTo(200);
		assertThat(provisionRequest(product, SELLER, 19)).isEqualTo(200);
		assertThat(jdbc.queryForMap("select * from orders.inventory where product_id = ?", product)).isEqualTo(before);
		assertThat(call("GET", path + "/movements", OTHER, null).statusCode()).isEqualTo(404);
		var history = call("GET", path + "/movements?page=1&size=1", SELLER, null);
		assertThat(history.statusCode()).isEqualTo(200);
		var response = JSON.readTree(history.body());
		assertThat(response.path("meta").path("totalItems").asLong()).isEqualTo(1);
		var movement = response.path("data").get(0);
		assertThat(movement.path("movementType").asString()).isEqualTo("MANUAL_ADJUST");
		assertThat(movement.path("note").asString()).isEqualTo("Kiểm kê");
		assertThat(movement.path("quantityDelta").asInt()).isEqualTo(15);
	}

	private UUID provision(UUID seller) throws Exception {
		UUID product = UUID.randomUUID();
		OWNERS.put(product, seller);
		assertThat(provisionRequest(product, seller, 10)).isEqualTo(201);
		return product;
	}

	private int provisionRequest(UUID product, UUID seller, int quantity) throws Exception {
		return call("POST", PROVISION, null,
				JSON.writeValueAsString(Map.of("productId", product, "sellerId", seller, "onHandQuantity", quantity)))
				.statusCode();
	}

	private static String adjustment(int onHand, long version) {
		return JSON.writeValueAsString(Map.of("onHandQuantity", onHand, "version", version, "note", "Kiểm kê"));
	}

	private HttpResponse<String> call(String method, String path, UUID seller, String body) throws Exception {
		var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
				.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
		if (body != null) {
			request.header("Content-Type", "application/json");
		}
		if (seller == null) {
			request.header("X-Service-Token", "seller-test-product");
		} else {
			request.header("Authorization", "Bearer " + token(seller));
		}
		try (var client = HttpClient.newHttpClient()) {
			return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
		}
	}

	private static HttpServer startSources() {
		try {
			var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			server.createContext("/.well-known/jwks.json", x -> reply(x, new JWKSet(KEY.toPublicJWK()).toJSONObject()));
			server.createContext("/internal/v1/identity/auth/authorization-context", x -> {
				try {
					String token = x.getRequestHeaders().getFirst("Authorization").substring(7);
					String seller = SignedJWT.parse(token).getJWTClaimsSet().getSubject();
					reply(x, Map.of("data", Map.of("userId", seller, "accountType", "SELLER", "accountStatus", "ACTIVE",
							"userStatus", "ACTIVE", "accessMode", "FULL", "authzVersion", 1, "roles", List.of("SELLER"),
							"permissions", List.of("inventory:manage:own"), "sessionActive", true, "sellerProfile",
							Map.of("approvalStatus", "APPROVED", "status", "ACTIVE"))));
				} catch (java.text.ParseException failure) {
					throw new IOException(failure);
				}
			});
			server.createContext("/internal/v1/products/batch", x -> {
				var products = new ArrayList<>();
				for (var id : JSON.readTree(x.getRequestBody()).path("productIds")) {
					UUID product = UUID.fromString(id.asString());
					products.add(Map.of("productId", product, "sellerId", OWNERS.get(product),
							"productName", "Sản phẩm thử", "priceVnd", 1000, "sellable", true, "media", List.of()));
				}
				reply(x, Map.of("data", products));
			});
			server.start();
			return server;
		} catch (IOException failure) {
			throw new IllegalStateException(failure);
		}
	}

	private static void reply(HttpExchange exchange, Object body) throws IOException {
		try (exchange) {
			byte[] bytes = JSON.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, bytes.length);
			exchange.getResponseBody().write(bytes);
		}
	}

	private static String token(UUID seller) throws Exception {
		var claims = new JWTClaimsSet.Builder().subject(seller.toString()).issuer("ecommerce-identity")
				.audience("ecommerce-api").expirationTime(Date.from(Instant.now().plusSeconds(120)))
				.claim("sessionId", UUID.randomUUID().toString()).claim("authzVersion", 1).build();
		var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.getKeyID()).build(), claims);
		jwt.sign(new RSASSASigner(KEY));
		return jwt.serialize();
	}

	private static RSAKey signingKey() {
		try {
			return new RSAKeyGenerator(2048).keyID("seller-inventory-test").generate();
		} catch (com.nimbusds.jose.JOSEException failure) {
			throw new IllegalStateException(failure);
		}
	}
}
