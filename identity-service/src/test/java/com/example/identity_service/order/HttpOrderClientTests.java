package com.example.identity_service.order;

import com.example.identity_service.common.error.ApiException;
import com.example.identity_service.common.error.ErrorCode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The has-paid-order client against a local stand-in for order-service: the answer is read from the envelope, and
 * every failure is 503 so a ban never guesses.
 */
class HttpOrderClientTests {

	private static final UUID CUSTOMER = UUID.fromString("5d2a0000-0000-0000-0000-000000000001");

	private HttpServer order;

	private volatile int status = 200;

	private volatile String body = "{}";

	private volatile Duration delay = Duration.ZERO;

	private volatile String path;

	@BeforeEach
	void startOrder() throws IOException {
		order = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		order.createContext("/", exchange -> {
			path = exchange.getRequestURI().getPath();
			try {
				Thread.sleep(delay);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(status, bytes.length);
			exchange.getResponseBody().write(bytes);
			exchange.close();
		});
		order.start();
	}

	@AfterEach
	void stopOrder() {
		order.stop(0);
	}

	@Test
	void readsTheAnswerFromTheEnvelope() {
		body = "{\"data\":{\"hasPaidOrder\":true,\"evaluatedAt\":\"2026-10-31T00:00:00Z\"}}";
		assertThat(client(Duration.ofSeconds(2)).hasPaidOrder(CUSTOMER)).isTrue();
		assertThat(path).isEqualTo("/internal/v1/orders/customers/" + CUSTOMER + "/has-paid-order");

		body = "{\"data\":{\"hasPaidOrder\":false}}";
		assertThat(client(Duration.ofSeconds(2)).hasPaidOrder(CUSTOMER)).isFalse();
	}

	@Test
	void errorsMissingAnswersAndTimeoutsAreUnavailable() {
		status = 500;
		assertUnavailable(client(Duration.ofSeconds(2)));

		status = 404;
		assertUnavailable(client(Duration.ofSeconds(2)));

		status = 200;
		body = "{\"data\":{}}";
		assertUnavailable(client(Duration.ofSeconds(2)));

		body = "{\"data\":{\"hasPaidOrder\":true}}";
		delay = Duration.ofSeconds(2);
		assertUnavailable(client(Duration.ofMillis(300)));
	}

	@Test
	void unreachableOrderIsUnavailable() {
		int port = order.getAddress().getPort();
		order.stop(0);
		assertUnavailable(new HttpOrderClient(new OrderProperties("http://127.0.0.1:" + port, Duration.ofSeconds(1))));
	}

	private HttpOrderClient client(Duration timeout) {
		return new HttpOrderClient(
				new OrderProperties("http://127.0.0.1:" + order.getAddress().getPort(), timeout));
	}

	private static void assertUnavailable(HttpOrderClient client) {
		assertThatThrownBy(() -> client.hasPaidOrder(CUSTOMER)).isInstanceOfSatisfying(ApiException.class,
				ex -> assertThat(ex.code()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE));
	}

}
