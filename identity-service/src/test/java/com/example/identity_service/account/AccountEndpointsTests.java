package com.example.identity_service.account;

import com.example.identity_service.EndpointTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;

import static org.hamcrest.Matchers.contains;
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

	/**
	 * One account per role (API v1.1 §3.2.1): an account never adds a Customer profile or applies to sell; sellers
	 * register through the public endpoints. {@code GET /users/me/customer-profile} is marked removed in §6.1.
	 */
	@ParameterizedTest
	@CsvSource({ "POST, /users/me/customer-profile", "GET, /users/me/customer-profile",
			"POST, /users/me/seller-application", "GET, /users/me/seller-application" })
	void accountsCannotAddProfilesToThemselves(String method, String path) throws Exception {
		Session customer = customer();

		call(HttpMethod.valueOf(method), path, method.equals("POST") ? "{}" : null, customer.accessToken())
				.andExpect(status().isNotFound());
		call(HttpMethod.GET, "/users/me", null, customer.accessToken())
				.andExpect(jsonPath("$.data.roles", contains("CUSTOMER")))
				.andExpect(jsonPath("$.data.sellerProfile").isEmpty());
	}

	@Test
	void aSellersFullNameIsItsShopNameAndIsLocked() throws Exception {
		Session seller = approvedSeller();
		String shopName = users.findById(seller.userId()).orElseThrow().getFullName();
		String phone = uniquePhone();

		call(HttpMethod.PATCH, "/users/me", json("fullName", "Another Shop"), seller.accessToken())
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("SELLER_NAME_LOCKED"));
		call(HttpMethod.PATCH, "/users/me", json("fullName", shopName, "phoneNumber", phone), seller.accessToken())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.fullName").value(shopName))
				.andExpect(jsonPath("$.data.phoneNumber").value(phone));
		call(HttpMethod.GET, "/sellers/" + seller.userId(), null, null)
				.andExpect(jsonPath("$.data.sellerName").value(shopName));
	}

	@Test
	void aBannedAccountCannotEditItsProfile() throws Exception {
		Session customer = customer();
		jdbc.update("""
				update customers set status = 'BANNED', ban_reason = 'Khác: Fraud', ban_mode = 'CUSTOMER_SCOPE',
				has_paid_order_before_ban = false where customer_id = ?""", customer.userId());

		call(HttpMethod.PATCH, "/users/me", json("fullName", "New Name"), customer.accessToken())
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ACCOUNT_BANNED"));
	}

	@Test
	void meNamesTheAccountType() throws Exception {
		Session customer = customer();
		Session seller = approvedSeller();
		Session admin = admin();

		call(HttpMethod.GET, "/users/me", null, customer.accessToken())
				.andExpect(jsonPath("$.data.accountType").value("CUSTOMER"))
				.andExpect(jsonPath("$.data.roles", contains("CUSTOMER")));
		call(HttpMethod.GET, "/users/me", null, seller.accessToken())
				.andExpect(jsonPath("$.data.accountType").value("SELLER"))
				.andExpect(jsonPath("$.data.roles", contains("SELLER")))
				.andExpect(jsonPath("$.data.customerProfile").isEmpty());
		call(HttpMethod.GET, "/users/me", null, admin.accessToken())
				.andExpect(jsonPath("$.data.accountType").value("ADMIN"))
				.andExpect(jsonPath("$.data.roles", contains("ADMIN")))
				.andExpect(jsonPath("$.data.customerProfile").isEmpty())
				.andExpect(jsonPath("$.data.sellerProfile").isEmpty());
	}

}
