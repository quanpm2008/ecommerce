package com.example.identity_service.outbox;

import com.example.identity_service.EndpointTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EnabledIfEnvironmentVariable(named = "SELLER_APPROVAL_FINANCE_URL", matches = "http://127\\.0\\.0\\.1:\\d+")
@TestPropertySource(properties = {
		"identity.kafka.enabled=true",
		"identity.kafka.bootstrap-servers=127.0.0.1:55990",
		"identity.kafka.topic=ecommerce.acceptance.identity.events",
		"identity.outbox.relay.enabled=false"
})
class SellerApprovalKafkaIT extends EndpointTestSupport {
	@Autowired OutboxRelay relay;

	@Test
	void approvalPublishesFromOutboxAndCreatesFinanceWallet() throws Exception {
		var seller = pendingSeller();
		var request = HttpRequest.newBuilder(URI.create(System.getenv("SELLER_APPROVAL_FINANCE_URL")
				+ "/internal/v1/finance/sellers/" + seller.userId() + "/obligations"))
				.header("X-Service-Token", "acceptance-test-identity-to-finance").timeout(Duration.ofSeconds(3)).GET().build();
		try (var http = HttpClient.newHttpClient()) {
			assertThat(http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(404);
			call(HttpMethod.POST, "/admin/sellers/" + seller.userId() + "/approve", null, admin().accessToken())
					.andExpect(status().isOk());
			assertThat(relay.publishPending()).isPositive();
			assertThat(jdbc.queryForObject("""
					select status from outbox_events where event_type = 'seller.approved' and aggregate_id = ?
					""", String.class, seller.userId())).isEqualTo("PUBLISHED");
			var deadline = Instant.now().plusSeconds(20);
			HttpResponse<String> response;
			do {
				response = http.send(request, HttpResponse.BodyHandlers.ofString());
				if (response.statusCode() == 200) break;
				Thread.sleep(200);
			} while (Instant.now().isBefore(deadline));
			assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
			assertThat(response.body()).contains("\"pendingBalanceVnd\":0", "\"availableBalanceVnd\":0");
		}
	}
}
