package com.example.order_service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderRealServiceE1SupportTests {
	@TempDir
	Path evidence;

	@Test
	void missingRevisionFailsAndKeepsDiagnostic() throws Exception {
		try (var e1 = new OrderRealServiceE1Support("missing", Map.<String, String>of()::get, evidence)) {
			assertThatThrownBy(() -> e1.revision("E1_ORDER_REVISION"))
					.isInstanceOf(IllegalStateException.class).hasMessageContaining("E1_ORDER_REVISION");
		}
		assertThat(Files.readString(evidence.resolve("missing.txt"))).contains("MISSING_INPUT E1_ORDER_REVISION");
	}

	@Test
	void reportsModeAndHttpFailureWithoutRecordingPrivateFixtureData() throws Exception {
		var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/case", exchange -> {
			try (exchange) {
				boolean failing = "fail=true".equals(exchange.getRequestURI().getQuery());
				byte[] data = "{\"message\":\"private-fixture-content\"}".getBytes(StandardCharsets.UTF_8);
				exchange.sendResponseHeaders(failing ? 503 : 200, data.length);
				exchange.getResponseBody().write(data);
			}
		});
		server.start();
		var values = Map.of("E1_ORDER_URL", "http://127.0.0.1:" + server.getAddress().getPort(), "E1_ORDER_REVISION", "abc123");
		try (var e1 = new OrderRealServiceE1Support("http-proof", values::get, evidence)) {
			e1.revision("E1_ORDER_REVISION");
			e1.scope("Identity", "mock");
			e1.scope("Product", "real");
			assertThat(e1.request("E1_ORDER_URL", "GET", "/case", "hidden-bearer", "hidden-service-token", null, 200)
					.path("message").asString()).isEqualTo("private-fixture-content");
			assertThatThrownBy(() -> e1.request("E1_ORDER_URL", "POST", "/case?fail=true", "hidden-bearer", null,
					Map.of("fixture", "hidden-body"), 200)).isInstanceOf(AssertionError.class);
		} finally {
			server.stop(0);
		}
		assertThat(Files.readString(evidence.resolve("http-proof.txt"))).contains(
				"E1_ORDER_REVISION abc123", "boundary-only Identity", "boundary Product",
				"GET E1_ORDER_URL/case status=200", "POST E1_ORDER_URL/case status=503")
				.doesNotContain("hidden-bearer", "hidden-service-token", "hidden-body", "private-fixture-content", "fail=true");
	}
}
