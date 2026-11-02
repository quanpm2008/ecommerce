package com.example.identity_service.outbox;

import com.example.identity_service.EndpointTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every Identity event, produced by the real flows, has the API §7.2 envelope and the documented payload (checklist
 * section 9). Keys are compared exactly, so an extra field (or a leaked secret) fails too.
 */
class EventPayloadTests extends EndpointTestSupport {

	private static final List<String> ENVELOPE = List.of("eventId", "eventType", "occurredAt", "producer",
			"aggregateId", "version", "actor", "payload");

	@Test
	void sellerLifecycleEvents() throws Exception {
		Session admin = admin();
		Session seller = pendingSeller();
		assertEvent("seller.registered", seller.userId(), "userId", "sellerId", "approvalStatus", "authzVersion");

		call(HttpMethod.POST, "/admin/sellers/" + seller.userId() + "/approve", null, admin.accessToken())
				.andExpect(status().isOk());
		Map<String, Object> approved = assertEvent("seller.approved", seller.userId(), "userId", "sellerId",
				"approvalStatus", "authzVersion");
		assertThat(approved).containsEntry("approvalStatus", "APPROVED");
		assertThat(actor("seller.approved", seller.userId())).containsEntry("kind", "USER")
				.containsEntry("context", "ADMIN").containsEntry("userId", admin.userId().toString());

		Session rejected = pendingSeller();
		call(HttpMethod.POST, "/admin/sellers/" + rejected.userId() + "/reject", json("reason", "Incomplete"),
				admin.accessToken()).andExpect(status().isOk());
		assertEvent("seller.rejected", rejected.userId(), "userId", "sellerId", "approvalStatus", "authzVersion",
				"reason");

		ban(admin, seller.userId(), "SELLER");
		assertEvent("seller.banned", seller.userId(), "userId", "sellerId", "status", "authzVersion", "reason");
		unban(admin, seller.userId(), "SELLER");
		assertEvent("seller.unbanned", seller.userId(), "userId", "sellerId", "status", "authzVersion", "reason");
	}

	@Test
	void customerAndUserModerationEvents() throws Exception {
		Session admin = admin();
		Session customer = customer();

		ban(admin, customer.userId(), "CUSTOMER");
		assertEvent("customer.banned", customer.userId(), "userId", "customerId", "banMode",
				"hasPaidOrderBeforeBan", "authzVersion");
		unban(admin, customer.userId(), "CUSTOMER");
		assertEvent("customer.unbanned", customer.userId(), "userId", "customerId", "banMode",
				"hasPaidOrderBeforeBan", "authzVersion");

		ban(admin, customer.userId(), "USER");
		assertEvent("user.disabled", customer.userId(), "userId", "authzVersion", "reason", "hasSellerProfile");
		unban(admin, customer.userId(), "USER");
		assertEvent("user.enabled", customer.userId(), "userId", "authzVersion", "reason", "hasSellerProfile");
	}

	@Test
	void rolesChangedCarriesTheCurrentRoles() throws Exception {
		Session customer = customer();

		Map<String, Object> payload = assertEvent("user.roles_changed", customer.userId(), "userId", "roleCodes",
				"authzVersion");
		assertThat(payload.get("roleCodes")).isEqualTo(List.of("CUSTOMER"));
		assertThat(actor("user.roles_changed", customer.userId())).containsEntry("context", "ACCOUNT");
	}

	@Test
	void passwordResetRequested() throws Exception {
		Session customer = customer();
		postJson("/password/forgot", json("email", customer.email())).andExpect(status().isAccepted());

		assertEvent("password.reset_requested", customer.userId(), "userId", "resetUrl", "expiresAt");
	}

	@Test
	void versionsNumberEachUsersEventsFromOne() throws Exception {
		Session admin = admin();
		Session seller = pendingSeller();
		call(HttpMethod.POST, "/admin/sellers/" + seller.userId() + "/approve", null, admin.accessToken())
				.andExpect(status().isOk());
		ban(admin, seller.userId(), "SELLER");

		List<Long> versions = jdbc.queryForList("""
				select (payload ->> 'version')::bigint from outbox_events where aggregate_id = ?
				order by version""", Long.class, seller.userId());
		List<Long> columns = jdbc.queryForList(
				"select version from outbox_events where aggregate_id = ? order by created_at, version", Long.class,
				seller.userId());
		// seller.registered, seller.approved + user.roles_changed, seller.banned
		assertThat(versions).containsExactly(1L, 2L, 3L, 4L);
		assertThat(columns).containsExactly(1L, 2L, 3L, 4L);
	}

	/** The latest event of this type for the aggregate: checks the envelope and returns the payload. */
	private Map<String, Object> assertEvent(String eventType, UUID aggregateId, String... payloadKeys) {
		Map<String, Object> envelope = envelope(eventType, aggregateId);
		assertThat(envelope).containsOnlyKeys(ENVELOPE);
		assertThat(envelope).containsEntry("eventType", eventType).containsEntry("producer", "identity-service")
				.containsEntry("aggregateId", aggregateId.toString());
		@SuppressWarnings("unchecked")
		Map<String, Object> payload = (Map<String, Object>) envelope.get("payload");
		assertThat(payload).containsOnlyKeys(payloadKeys);
		return payload;
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> actor(String eventType, UUID aggregateId) {
		return (Map<String, Object>) envelope(eventType, aggregateId).get("actor");
	}

	private Map<String, Object> envelope(String eventType, UUID aggregateId) {
		String json = jdbc.queryForObject("""
				select payload::text from outbox_events where event_type = ? and aggregate_id = ?
				order by version desc limit 1""", String.class, eventType, aggregateId);
		return JsonPath.read(json, "$");
	}

	private void ban(Session admin, UUID userId, String scope) throws Exception {
		call(HttpMethod.POST, "/admin/ban/" + userId, json("scope", scope, "reason", "Test"), admin.accessToken())
				.andExpect(status().isOk());
	}

	private void unban(Session admin, UUID userId, String scope) throws Exception {
		call(HttpMethod.POST, "/admin/unban/" + userId, json("scope", scope, "reason", "Appeal"),
				admin.accessToken()).andExpect(status().isOk());
	}

}
