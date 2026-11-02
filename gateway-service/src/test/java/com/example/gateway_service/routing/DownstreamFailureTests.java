package com.example.gateway_service.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.time.Duration;

import com.example.gateway_service.support.GatewayClient;
import com.example.gateway_service.support.StubService;
import com.example.gateway_service.support.StubService.Reply;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Checklist §1: no response from a service is a 503 {@code SERVICE_UNAVAILABLE} envelope. identity-service points at a
 * closed port, cart-service has no instance at all, and order-service answers after the (shortened) read timeout.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "eureka.client.enabled=false", "spring.http.clients.read-timeout=300ms" })
class DownstreamFailureTests {

	static final StubService SLOW_ORDER = StubService.start("order-service");

	static final ObjectMapper JSON = new ObjectMapper();

	@LocalServerPort
	int port;

	GatewayClient gateway;

	@DynamicPropertySource
	static void instances(DynamicPropertyRegistry registry) {
		registry.add("spring.cloud.discovery.client.simple.instances.identity-service[0].uri", StubService::refusedUri);
		registry.add("spring.cloud.discovery.client.simple.instances.order-service[0].uri", SLOW_ORDER::uri);
	}

	@AfterAll
	static void stopStubs() {
		SLOW_ORDER.close();
	}

	@BeforeEach
	void setUp() {
		gateway = new GatewayClient(port);
		SLOW_ORDER.reply(Reply.json(200, "{\"data\":{}}").after(Duration.ofSeconds(2)));
	}

	@ParameterizedTest
	@ValueSource(strings = { "/api/v1/identity/users/me", "/api/v1/cart", "/api/v1/orders/o1" })
	void noResponseIsServiceUnavailable(String path) throws Exception {
		HttpResponse<String> response = gateway.get(path, "X-Request-Id", "req-503");

		assertThat(response.statusCode()).isEqualTo(503);
		assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
				type -> assertThat(type).startsWith("application/json"));
		JsonNode error = JSON.readTree(response.body()).path("error");
		assertThat(error.path("code").asString()).isEqualTo("SERVICE_UNAVAILABLE");
		assertThat(error.path("requestId").asString()).isEqualTo("req-503");
		assertThat(response.body()).doesNotContain("127.0.0.1", "Exception", "order-service", "identity-service");
	}

}
