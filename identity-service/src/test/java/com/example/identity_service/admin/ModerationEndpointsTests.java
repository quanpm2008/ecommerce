package com.example.identity_service.admin;

import com.example.identity_service.EndpointTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /admin/ban/{userId}} and {@code /unban/{userId}} (API §6.1, G-14, checklist section 7). Order's
 * {@code has-paid-order} is {@code FakeOrderClient}.
 */
class ModerationEndpointsTests extends EndpointTestSupport {

	private Session admin;

	@BeforeEach
	void signInAdmin() throws Exception {
		admin = admin();
	}

	@Test
	void scopeIsRequiredAndMustBeKnown() throws Exception {
		Session customer = customer();

		ban(customer.userId(), json("reason", "Fraud")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details[0].field").value("scope"));
		ban(customer.userId(), json("scope", "ADMIN", "reason", "Fraud")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
		ban(customer.userId(), json("scope", "USER")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details[0].field").value("reason"));
		unban(customer.userId(), "{}").andExpect(status().isBadRequest());
	}

	@Test
	void banUserDisablesAndRevokesEverySession() throws Exception {
		Session seller = approvedSeller();
		String refreshToken = read(loginBody(seller.email()), "$.data.refreshToken");

		ban(seller.userId(), json("scope", "USER", "reason", "Fraud")).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.userStatus").value("DISABLED"))
				.andExpect(jsonPath("$.data.sellerStatus").value("ACTIVE"));

		refresh(refreshToken).andExpect(status().isUnauthorized());
		call(HttpMethod.GET, "/users/me", null, seller.accessToken()).andExpect(status().isUnauthorized());
		login(seller.email(), PASSWORD).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ACCOUNT_DISABLED"));
		assertThat(audits("DISABLE", seller.userId())).isEqualTo(1);
		assertThat(events("user.disabled", seller.userId())).isEqualTo(1);
		assertThat(payload("user.disabled", seller.userId(), "hasSellerProfile")).isEqualTo("true");
	}

	@Test
	void customerWithAPaidOrderOnlyLosesPayments() throws Exception {
		Session customer = customer();
		orders.markPaid(customer.userId());

		ban(customer.userId(), json("scope", "CUSTOMER", "reason", "Chargebacks")).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.customerStatus").value("BANNED"))
				.andExpect(jsonPath("$.data.banMode").value("PAYMENT_ONLY"))
				.andExpect(jsonPath("$.data.userStatus").value("ACTIVE"));

		assertThat(jdbc.queryForObject("select has_paid_order_before_ban from customers where customer_id = ?",
				Boolean.class, customer.userId())).isTrue();
		assertThat(payload("customer.banned", customer.userId(), "banMode")).isEqualTo("PAYMENT_ONLY");
		assertThat(jdbc.queryForObject("""
				select before_after -> 'after' ->> 'hasPaidOrderBeforeBan' from admin_audit_log
				where action = 'BAN' and target_type = 'CUSTOMER' and target_id = ?""", String.class,
				customer.userId())).isEqualTo("true");
		String fresh = read(loginBody(customer.email()), "$.data.accessToken");
		call(HttpMethod.GET, "/users/me", null, fresh)
				.andExpect(jsonPath("$.data.customerProfile.accessMode").value("PAYMENT_BLOCKED"));
	}

	@Test
	void customerWithoutAPaidOrderLosesTheCustomerScope() throws Exception {
		Session customer = customer();

		ban(customer.userId(), json("scope", "CUSTOMER", "reason", "Spam")).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.banMode").value("CUSTOMER_SCOPE"));
		assertThat(audits("BAN", customer.userId())).isEqualTo(1);
		assertThat(events("customer.banned", customer.userId())).isEqualTo(1);
	}

	@Test
	void orderUnavailableChangesNothing() throws Exception {
		Session customer = customer();
		orders.setAvailable(false);

		ban(customer.userId(), json("scope", "CUSTOMER", "reason", "Spam")).andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.error.code").value("SERVICE_UNAVAILABLE"));

		assertThat(jdbc.queryForObject("select status from customers where customer_id = ?", String.class,
				customer.userId())).isEqualTo("ACTIVE");
		assertThat(audits("BAN", customer.userId())).isZero();
		assertThat(events("customer.banned", customer.userId())).isZero();
		call(HttpMethod.GET, "/users/me", null, customer.accessToken()).andExpect(status().isOk());
	}

	@Test
	void banSellerKeepsTheRoleAndHidesTheShop() throws Exception {
		Session seller = approvedSeller();

		ban(seller.userId(), json("scope", "SELLER", "reason", "Counterfeits")).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.sellerStatus").value("BANNED"))
				.andExpect(jsonPath("$.data.userStatus").value("ACTIVE"));

		assertThat(events("seller.banned", seller.userId())).isEqualTo(1);
		assertThat(orders.calls()).isZero();
		call(HttpMethod.GET, "/sellers/" + seller.userId(), null, null).andExpect(status().isNotFound());
		String fresh = read(loginBody(seller.email()), "$.data.accessToken");
		call(HttpMethod.GET, "/users/me", null, fresh).andExpect(jsonPath("$.data.roles", contains("SELLER")))
				.andExpect(jsonPath("$.data.sellerProfile.status").value("BANNED"));
		call(HttpMethod.POST, "/media/upload-urls", """
				{"files":[{"fileName":"logo.png","mimeType":"image/png","sizeBytes":100,"purpose":"SELLER_LOGO"}]}""",
				fresh).andExpect(status().isForbidden());
	}

	@Test
	void sameBanAgainChangesNothingAndAnotherReasonIsAConflict() throws Exception {
		Session seller = approvedSeller();
		ban(seller.userId(), json("scope", "SELLER", "reason", "Counterfeits")).andExpect(status().isOk());

		ban(seller.userId(), json("scope", "SELLER", "reason", " Counterfeits ")).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.sellerStatus").value("BANNED"));
		ban(seller.userId(), json("scope", "SELLER", "reason", "Something else")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("INVALID_STATE_TRANSITION"))
				.andExpect(jsonPath("$.error.details[0].currentStatus").value("BANNED"));
		assertThat(audits("BAN", seller.userId())).isEqualTo(1);
		assertThat(events("seller.banned", seller.userId())).isEqualTo(1);
	}

	@Test
	void adminsCannotBeBannedAndMissingTargetsAreNotFound() throws Exception {
		Session otherAdmin = admin();

		ban(otherAdmin.userId(), json("scope", "USER", "reason", "Test")).andExpect(status().isForbidden());
		ban(admin.userId(), json("scope", "CUSTOMER", "reason", "Test")).andExpect(status().isForbidden());
		ban(UUID.randomUUID(), json("scope", "USER", "reason", "Test")).andExpect(status().isNotFound());
		ban(pendingSeller().userId(), json("scope", "CUSTOMER", "reason", "Test")).andExpect(status().isNotFound());
		ban(customer().userId(), json("scope", "SELLER", "reason", "Test")).andExpect(status().isNotFound());
	}

	@Test
	void moderationNeedsAccountModerate() throws Exception {
		Session customer = customer();

		ban(customer.userId(), json("scope", "USER", "reason", "Test"), customer().accessToken())
				.andExpect(status().isForbidden());
		ban(customer.userId(), json("scope", "USER", "reason", "Test"), null).andExpect(status().isUnauthorized());
	}

	@Test
	void unbanRestoresOnlyThatScope() throws Exception {
		Session seller = approvedSeller();
		ban(seller.userId(), json("scope", "USER", "reason", "Fraud")).andExpect(status().isOk());
		ban(seller.userId(), json("scope", "SELLER", "reason", "Fraud")).andExpect(status().isOk());

		unban(seller.userId(), json("scope", "USER", "reason", "Appeal accepted")).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.userStatus").value("ACTIVE"))
				.andExpect(jsonPath("$.data.sellerStatus").value("BANNED"));
		assertThat(audits("ENABLE", seller.userId())).isEqualTo(1);
		assertThat(events("user.enabled", seller.userId())).isEqualTo(1);

		unban(seller.userId(), json("scope", "SELLER")).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.sellerStatus").value("ACTIVE"));
		assertThat(events("seller.unbanned", seller.userId())).isEqualTo(1);
		call(HttpMethod.GET, "/sellers/" + seller.userId(), null, null).andExpect(status().isOk());
	}

	@Test
	void unbanningAPendingSellerNeverGrantsTheRole() throws Exception {
		Session seller = pendingSeller();
		ban(seller.userId(), json("scope", "SELLER", "reason", "Fake documents")).andExpect(status().isOk());

		unban(seller.userId(), json("scope", "SELLER")).andExpect(status().isOk());

		String fresh = read(loginBody(seller.email()), "$.data.accessToken");
		call(HttpMethod.GET, "/users/me", null, fresh)
				.andExpect(jsonPath("$.data.roles", org.hamcrest.Matchers.empty()))
				.andExpect(jsonPath("$.data.sellerProfile.approvalStatus").value("PENDING"));
	}

	@Test
	void unbanningSomethingActiveChangesNothing() throws Exception {
		Session customer = customer();

		unban(customer.userId(), json("scope", "CUSTOMER")).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.customerStatus").value("ACTIVE"));
		unban(customer.userId(), json("scope", "USER")).andExpect(status().isOk());
		assertThat(events("customer.unbanned", customer.userId())).isZero();
		assertThat(events("user.enabled", customer.userId())).isZero();
		assertThat(audits("UNBAN", customer.userId())).isZero();
	}

	@Test
	void unbanCustomerClearsTheG14Decision() throws Exception {
		Session customer = customer();
		orders.markPaid(customer.userId());
		ban(customer.userId(), json("scope", "CUSTOMER", "reason", "Chargebacks")).andExpect(status().isOk());

		unban(customer.userId(), json("scope", "CUSTOMER")).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.customerStatus").value("ACTIVE"));

		assertThat(jdbc.queryForObject("""
				select ban_mode is null and has_paid_order_before_ban is null and ban_reason is null
				from customers where customer_id = ?""", Boolean.class, customer.userId())).isTrue();
		assertThat(events("customer.unbanned", customer.userId())).isEqualTo(1);
	}

	private ResultActions ban(UUID userId, String body) throws Exception {
		return ban(userId, body, admin.accessToken());
	}

	private ResultActions ban(UUID userId, String body, String token) throws Exception {
		return call(HttpMethod.POST, "/admin/ban/" + userId, body, token);
	}

	private ResultActions unban(UUID userId, String body) throws Exception {
		return call(HttpMethod.POST, "/admin/unban/" + userId, body, admin.accessToken());
	}

	private String payload(String eventType, UUID aggregateId, String field) {
		return jdbc.queryForObject("""
				select payload -> 'payload' ->> ? from outbox_events where event_type = ? and aggregate_id = ?""",
				String.class, field, eventType, aggregateId);
	}

}
