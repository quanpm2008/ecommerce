package com.example.identity_service.account;

import com.example.identity_service.EndpointTestSupport;
import com.example.identity_service.rbac.RoleCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /users/me} and {@code /users/me/customer-profile} (API §6.1, UC-A01, A06, checklist section 4).
 */
class AccountEndpointsTests extends EndpointTestSupport {

	@Test
	void meReturnsTheCurrentProfile() throws Exception {
		Session customer = customer();

		call(HttpMethod.GET, "/users/me", null, customer.accessToken()).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.userId").value(customer.userId().toString()))
				.andExpect(jsonPath("$.data.email").value(customer.email()))
				.andExpect(jsonPath("$.data.roles", contains("CUSTOMER")))
				.andExpect(jsonPath("$.data.authzVersion").value(2))
				.andExpect(jsonPath("$.data.customerProfile.customerId").value(customer.userId().toString()))
				.andExpect(jsonPath("$.data.sellerProfile").isEmpty());
	}

	@Test
	void meNeedsAnAccessToken() throws Exception {
		call(HttpMethod.GET, "/users/me", null, null).andExpect(status().isUnauthorized());
	}

	@Test
	void patchMeChangesNameAndPhone() throws Exception {
		Session customer = customer();
		String phone = uniquePhone();

		call(HttpMethod.PATCH, "/users/me", json("fullName", "  Le Thi C ", "phoneNumber", "+84" + phone.substring(1)),
				customer.accessToken())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.fullName").value("Le Thi C"))
				.andExpect(jsonPath("$.data.phoneNumber").value(phone));
	}

	@Test
	void patchMeRejectsAnyOtherFieldAndChangesNothing() throws Exception {
		Session customer = customer();

		call(HttpMethod.PATCH, "/users/me", "{\"fullName\":\"New Name\",\"email\":\"x@example.test\",\"roles\":[\"ADMIN\"]}",
				customer.accessToken())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.error.details[*].field", org.hamcrest.Matchers.containsInAnyOrder("email", "roles")))
				.andExpect(jsonPath("$.error.details[0].issue").value("UNKNOWN_FIELD"));

		call(HttpMethod.GET, "/users/me", null, customer.accessToken())
				.andExpect(jsonPath("$.data.fullName").value("Nguyen Van An"))
				.andExpect(jsonPath("$.data.email").value(customer.email()));
	}

	@Test
	void patchMeRejectsBlankNameAndBadPhone() throws Exception {
		Session customer = customer();

		call(HttpMethod.PATCH, "/users/me", "{\"fullName\":null}", customer.accessToken())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details[0].field").value("fullName"));
		call(HttpMethod.PATCH, "/users/me", json("phoneNumber", "123"), customer.accessToken())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details[0].field").value("phoneNumber"));
	}

	@Test
	void patchMeRejectsAPhoneUsedByAnotherUserButAcceptsItsOwn() throws Exception {
		Session customer = customer();
		String taken = jdbc.queryForObject("select phone_number from users where user_id = ?", String.class,
				customer().userId());
		String own = jdbc.queryForObject("select phone_number from users where user_id = ?", String.class,
				customer.userId());

		call(HttpMethod.PATCH, "/users/me", json("phoneNumber", taken), customer.accessToken())
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("PHONE_ALREADY_USED"));
		call(HttpMethod.PATCH, "/users/me", json("phoneNumber", own), customer.accessToken())
				.andExpect(status().isOk());
	}

	@Test
	void sellerCanAddACustomerProfileOnce() throws Exception {
		Session seller = pendingSeller();

		call(HttpMethod.POST, "/users/me/customer-profile", null, seller.accessToken())
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.roles", contains("CUSTOMER")))
				.andExpect(jsonPath("$.data.customerProfile.status").value("ACTIVE"))
				.andExpect(jsonPath("$.data.sellerProfile.approvalStatus").value("PENDING"));

		// The role grant bumped authzVersion, so the old token must be refreshed before the second call.
		call(HttpMethod.POST, "/users/me/customer-profile", null, seller.accessToken())
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("TOKEN_STALE"));
		String fresh = read(loginBody(seller.email()), "$.data.accessToken");
		call(HttpMethod.POST, "/users/me/customer-profile", null, fresh).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.roles", contains("CUSTOMER")));
	}

	@Test
	void bannedCustomerIsReturnedUnchanged() throws Exception {
		Session customer = customer();
		jdbc.update("""
				update customers set status = 'BANNED', ban_reason = 'Fraud', ban_mode = 'CUSTOMER_SCOPE',
				has_paid_order_before_ban = false where customer_id = ?""", customer.userId());

		call(HttpMethod.POST, "/users/me/customer-profile", null, customer.accessToken()).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.customerProfile.status").value("BANNED"))
				.andExpect(jsonPath("$.data.customerProfile.banReason").value("Fraud"));
	}

	@Test
	void customerRoleRevokedByAnAdminIsNotGrantedBack() throws Exception {
		Session seller = pendingSeller();
		tx.executeWithoutResult(status -> {
			var user = users.findById(seller.userId()).orElseThrow();
			roleAssignments.grant(user, RoleCode.CUSTOMER, null);
			roleAssignments.revoke(user, RoleCode.CUSTOMER);
		});
		String fresh = read(loginBody(seller.email()), "$.data.accessToken");

		call(HttpMethod.POST, "/users/me/customer-profile", null, fresh).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
		call(HttpMethod.GET, "/users/me", null, fresh).andExpect(jsonPath("$.data.roles", empty()))
				.andExpect(jsonPath("$.data.customerProfile").isEmpty());
	}

}
