package com.example.identity_service.internal;

import com.example.identity_service.EndpointTestSupport;
import com.example.identity_service.rbac.RoleCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /internal/v1/identity/auth/authorization-context} (API §7.1, checklist section 8): the user's Bearer
 * token plus the gateway's service token.
 */
class AuthorizationContextTests extends EndpointTestSupport {

	@Test
	void validTokenGivesTheCurrentContext() throws Exception {
		Session customer = customer();

		context(customer.accessToken()).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.userId").value(customer.userId().toString()))
				.andExpect(jsonPath("$.data.userStatus").value("ACTIVE"))
				.andExpect(jsonPath("$.data.authzVersion").value(2))
				.andExpect(jsonPath("$.data.sessionActive").value(true))
				.andExpect(jsonPath("$.data.roles", contains("CUSTOMER")))
				.andExpect(jsonPath("$.data.permissions", hasItem("cart:manage:own")))
				.andExpect(jsonPath("$.data.customerProfile.status").value("ACTIVE"))
				.andExpect(jsonPath("$.data.customerProfile.accessMode").value("FULL"))
				.andExpect(jsonPath("$.data.sellerProfile").isEmpty());
	}

	@Test
	void sellerProfileSaysWhetherTheShopCanOperate() throws Exception {
		context(approvedSeller().accessToken()).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.roles", contains("SELLER")))
				.andExpect(jsonPath("$.data.sellerProfile.approvalStatus").value("APPROVED"))
				.andExpect(jsonPath("$.data.sellerProfile.canOperate").value(true));
	}

	@Test
	void staleExpiredAndInvalidTokensAre401WithTheRightCode() throws Exception {
		Session seller = pendingSeller();
		tx.executeWithoutResult(status -> roleAssignments.grant(users.findById(seller.userId()).orElseThrow(),
				RoleCode.CUSTOMER, null));
		context(seller.accessToken()).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("TOKEN_STALE"));

		Session customer = customer();
		clock.advance(Duration.ofMinutes(16));
		context(customer.accessToken()).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("TOKEN_EXPIRED"));

		context("not.a.jwt").andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
		internal(HttpMethod.POST, "/auth/authorization-context", null, GATEWAY_TOKEN, null)
				.andExpect(status().isUnauthorized());
	}

	@Test
	void revokedSessionIsReportedWithNoPermissions() throws Exception {
		Session customer = customer();
		String body = loginBody(customer.email());
		String accessToken = read(body, "$.data.accessToken");
		postJson("/logout", json("refreshToken", read(body, "$.data.refreshToken")), accessToken)
				.andExpect(status().isNoContent());

		context(accessToken).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.sessionActive").value(false))
				.andExpect(jsonPath("$.data.roles", empty()))
				.andExpect(jsonPath("$.data.permissions", empty()));
	}

	@Test
	void bannedCustomerIsReportedWithTheG14Mode() throws Exception {
		Session customer = customer();
		jdbc.update("""
				update customers set status = 'BANNED', ban_reason = 'Chargebacks', ban_mode = 'PAYMENT_ONLY',
				has_paid_order_before_ban = true where customer_id = ?""", customer.userId());

		context(customer.accessToken()).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.customerProfile.status").value("BANNED"))
				.andExpect(jsonPath("$.data.customerProfile.accessMode").value("PAYMENT_BLOCKED"))
				.andExpect(jsonPath("$.data.customerProfile.paymentBlocked").value(true));
	}

	private ResultActions context(String accessToken) throws Exception {
		return internal(HttpMethod.POST, "/auth/authorization-context", null, GATEWAY_TOKEN, accessToken);
	}

}
