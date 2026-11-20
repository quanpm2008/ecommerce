package com.example.gateway_service.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.List;

import com.example.gateway_service.support.GatewayClient;
import com.example.gateway_service.support.StubService;
import com.example.gateway_service.support.StubService.Reply;
import com.example.gateway_service.support.TestTokens;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Checklist §8: the access log, nothing secret in the logs, no stack-revealing headers, no cookies of the gateway's own. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "eureka.client.enabled=false")
@ExtendWith(OutputCaptureExtension.class)
class HygieneTests {

	static final StubService IDENTITY = StubService.start("identity-service");

	static final StubService JWKS = TestTokens.startJwks(TestTokens.KEY);

	@LocalServerPort
	int port;

	GatewayClient anonymous;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.cloud.discovery.client.simple.instances.identity-service[0].uri", IDENTITY::uri);
		registry.add("gateway.jwt.jwk-set-uri", () -> TestTokens.jwksUri(JWKS));
	}

	@AfterAll
	static void stopStubs() {
		IDENTITY.close();
		JWKS.close();
	}

	@BeforeEach
	void setUp() {
		anonymous = new GatewayClient(port);
		IDENTITY.reset();
	}

	@ParameterizedTest
	@CsvSource({ "/api/v1/identity/sellers, 200", "/api/v1/nothing-here, 404", "/api/v1/identity/users/me, 401",
			"/internal/v1/identity/users/u1/contact, 404", "/api/v1/orders/o1, 503" })
	void everyRequestGetsOneAccessLogLine(String path, int status, CapturedOutput output) {
		String id = "req-acc-" + status + "-" + path.length();
		GatewayClient client = status == 503 ? anonymous.withToken(TestTokens.valid()) : anonymous;

		assertThat(client.get(path, "X-Request-Id", id).statusCode()).isEqualTo(status);

		List<String> lines = output.getOut().lines()
				.filter(line -> line.contains(AccessLogFilter.LOGGER) && line.contains("requestId=" + id))
				.toList();
		assertThat(lines).singleElement().asString()
				.contains("GET " + path + " " + status + " ")
				.contains("ms client=127.0.0.1");
	}

	@Test
	void queryStringsTokensPasswordsAndCookiesAreNeverLogged(CapturedOutput output) {
		String token = TestTokens.valid();
		String expired = TestTokens.sign(TestTokens.KEY, TestTokens.claims()
				.expirationTime(java.util.Date.from(java.time.Instant.now().minusSeconds(600))).build());

		anonymous.get("/api/v1/identity/sellers?q=reset-token-abc123");
		anonymous.withToken(token).get("/api/v1/identity/users/me");
		anonymous.withToken(expired).get("/api/v1/identity/users/me");
		anonymous.withToken("not.a.jwt").get("/api/v1/identity/users/me");
		anonymous.send("POST", "/api/v1/identity/auth/login", "{\"email\":\"a@b.c\",\"password\":\"Sup3rSecret!\"}",
				"Content-Type", "application/json", "Cookie", "refresh_token=rt-secret-1");

		assertThat(output.getOut() + output.getErr()).doesNotContain("reset-token-abc123", token, expired,
				"not.a.jwt", "Sup3rSecret!", "rt-secret-1", "eyJ");
	}

	@Test
	void noHeaderRevealsTheServerSoftware() {
		IDENTITY.reply(Reply.json(200, "{}").withHeader("Server", "Apache-Coyote/1.1")
				.withHeader("X-Powered-By", "Servlet/6.0").withHeader("X-Application-Context", "identity-service:8083"));

		HttpResponse<String> proxied = anonymous.get("/api/v1/identity/sellers");
		HttpResponse<String> own = anonymous.get("/api/v1/nothing-here");

		for (HttpResponse<String> response : List.of(proxied, own)) {
			assertThat(response.headers().firstValue("Server")).isEmpty();
			assertThat(response.headers().firstValue("X-Powered-By")).isEmpty();
			assertThat(response.headers().firstValue("X-Application-Context")).isEmpty();
		}
	}

	@Test
	void theGatewaySetsNoCookiesOfItsOwn() {
		HttpResponse<String> proxied = anonymous.get("/api/v1/identity/sellers");
		HttpResponse<String> refused = anonymous.get("/api/v1/identity/users/me");
		HttpResponse<String> signedIn = anonymous.withToken(TestTokens.valid()).get("/api/v1/identity/users/me");

		for (HttpResponse<String> response : List.of(proxied, refused, signedIn)) {
			assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
		}
	}

}
