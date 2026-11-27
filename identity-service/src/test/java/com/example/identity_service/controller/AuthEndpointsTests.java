package com.example.identity_service.controller;

import com.example.identity_service.EndpointTestSupport;
import com.example.identity_service.entity.RoleCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.ParameterizedTest;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Register, register-seller, login, refresh and logout through HTTP (API §6.1, checklist section 3).
 */
class AuthEndpointsTests extends EndpointTestSupport {

	// --- register ---

	@Test
	void registerCreatesACustomerAndReturnsTheProfileWithoutTokens() throws Exception {
		String email = uniqueEmail().replace("user-", "New.User-");
		String phone = uniquePhone();
		String international = "+84 " + phone.substring(1, 4) + " " + phone.substring(4, 7) + " " + phone.substring(7);

		register(email.toUpperCase(), international, PASSWORD)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.email").value(email.toLowerCase()))
				.andExpect(jsonPath("$.data.phoneNumber").value(phone))
				.andExpect(jsonPath("$.data.authMethod").value("LOCAL"))
				.andExpect(jsonPath("$.data.userStatus").value("ACTIVE"))
				.andExpect(jsonPath("$.data.roles", contains("CUSTOMER")))
				.andExpect(jsonPath("$.data.permissions", hasItems("cart:manage:own", "account:read:own")))
				.andExpect(jsonPath("$.data.customerProfile.status").value("ACTIVE"))
				.andExpect(jsonPath("$.data.customerProfile.accessMode").value("FULL"))
				.andExpect(jsonPath("$.data.customerProfile.accessMode").value("FULL"))
				.andExpect(jsonPath("$.data.customerProfile.profileComplete").value(false))
				.andExpect(jsonPath("$.data.sellerProfile").isEmpty())
				.andExpect(jsonPath("$.data.accessToken").doesNotExist())
				.andExpect(jsonPath("$.data.passwordHash").doesNotExist());
	}

	@Test
	void registerRejectsAnEmailOrPhoneAlreadyInUse() throws Exception {
		String email = uniqueEmail();
		String phone = uniquePhone();
		register(email, phone, PASSWORD).andExpect(status().isCreated());

		register(email.toUpperCase(), uniquePhone(), PASSWORD).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_USED"))
				.andExpect(jsonPath("$.error.details[0].field").value("email"));
		register(uniqueEmail(), "+84" + phone.substring(1), PASSWORD).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("PHONE_ALREADY_USED"))
				.andExpect(jsonPath("$.error.details[0].field").value("phoneNumber"));
	}

	@Test
	void registerValidatesPasswordsAndPhone() throws Exception {
		postJson("/register", json("email", uniqueEmail(), "fullName", "An", "phoneNumber", uniquePhone(),
				"password", PASSWORD, "confirmPassword", "Different123"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.error.details[0].field").value("confirmPassword"));
		register(uniqueEmail(), uniquePhone(), "alllowercase1").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("PASSWORD_POLICY_VIOLATION"));
		register(uniqueEmail(), "12345", PASSWORD).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.error.details[0].field").value("phoneNumber"));
		register("not-an-email", uniquePhone(), PASSWORD).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details[0].field").value("email"));
	}

	@Test
	void loginIsRateLimitedPerClientIpButNeverLocksTheAccount() throws Exception {
		String email = uniqueEmail();
		registerCustomer(email);
		for (int i = 1; i <= 30; i++) { // identity.rate-limit.login.limit in the tests
			login(email, "Wrong-password-1").andExpect(status().isUnauthorized());
		}

		login(email, PASSWORD).andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
		clientIp = randomIp();
		login(email, PASSWORD).andExpect(status().isOk()); // P-18: no lockout after failures
	}

	@Test
	void registrationIsRateLimitedPerClientIp() throws Exception {
		String email = uniqueEmail();
		register(email, uniquePhone(), PASSWORD).andExpect(status().isCreated());
		for (int i = 2; i <= 10; i++) {
			register(email, uniquePhone(), PASSWORD).andExpect(status().isConflict());
		}

		register(uniqueEmail(), uniquePhone(), PASSWORD).andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
		clientIp = randomIp();
		register(uniqueEmail(), uniquePhone(), PASSWORD).andExpect(status().isCreated());
	}

	// --- register-seller ---

	@Test
	void registerSellerCreatesASellerAccountWithAPendingApplicationAndNoRole() throws Exception {
		String email = uniqueEmail();
		String phone = uniquePhone();
		String shopName = "Shop " + UUID.randomUUID();

		String body = registerSeller(email, "  " + shopName + " ", "+84" + phone.substring(1))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.accountType").value("SELLER"))
				.andExpect(jsonPath("$.data.sellerProfile.approvalStatus").value("PENDING"))
				.andExpect(jsonPath("$.data.applicationId").isNotEmpty())
				.andExpect(jsonPath("$.data.accessToken").doesNotExist())
				.andReturn().getResponse().getContentAsString();

		UUID userId = UUID.fromString(read(body, "$.data.userId"));
		assertThat(read(body, "$.data.sellerProfile.sellerId")).isEqualTo(userId.toString());
		assertThat(read(body, "$.data.sellerProfile.latestApplicationId"))
				.isEqualTo(read(body, "$.data.applicationId"));
		assertThat(jdbc.queryForMap("select account_type, full_name, phone_number from users where user_id = ?",
				userId)).containsEntry("account_type", "SELLER").containsEntry("full_name", shopName)
				.containsEntry("phone_number", phone);
		assertThat(jdbc.queryForMap("""
				select application_id::text as id, submission_no, status, snapshot_full_name, snapshot_phone
				from seller_applications where seller_id = ?""", userId))
				.containsEntry("id", read(body, "$.data.applicationId")).containsEntry("submission_no", 1)
				.containsEntry("status", "PENDING").containsEntry("snapshot_full_name", shopName)
				.containsEntry("snapshot_phone", phone);
		assertThat(jdbc.queryForObject("select count(*) from user_roles where user_id = ?", Integer.class, userId))
				.isZero();
		assertThat(jdbc.queryForObject("select count(*) from customers where customer_id = ?", Integer.class, userId))
				.isZero();
		Map<String, Object> event = jdbc.queryForMap("""
				select payload -> 'payload' ->> 'approvalStatus' as approval_status,
				       payload -> 'actor' ->> 'context' as actor_context,
				       payload ->> 'producer' as producer, status
				from outbox_events where event_type = 'seller.registered' and aggregate_id = ?""", userId);
		assertThat(event).containsEntry("approval_status", "PENDING").containsEntry("actor_context", "SELLER")
				.containsEntry("producer", "identity-service").containsEntry("status", "PENDING");
	}

	@Test
	void registerSellerRejectsATakenShopNameOrPhone() throws Exception {
		String name = "Shop Ao Xinh " + UUID.randomUUID();
		String phone = uniquePhone();
		registerSeller(uniqueEmail(), name, phone).andExpect(status().isCreated());

		registerSeller(uniqueEmail(), "  " + name.toUpperCase().replace(" ", "   ") + " ", uniquePhone())
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("SELLER_NAME_ALREADY_USED"))
				.andExpect(jsonPath("$.error.details[0].field").value("fullName"));
		registerSeller(uniqueEmail(), "Shop " + UUID.randomUUID(), phone).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("PHONE_ALREADY_USED"))
				.andExpect(jsonPath("$.error.details[0].field").value("phoneNumber"));
	}

	@Test
	void aRejectedSellerFreesItsShopNameAndCustomersMayShareNames() throws Exception {
		String name = "Shop Freed " + UUID.randomUUID();
		UUID rejected = UUID.fromString(read(body(registerSeller(uniqueEmail(), name, uniquePhone())
				.andExpect(status().isCreated())), "$.data.userId"));
		rejectSeller(rejected);

		registerSeller(uniqueEmail(), name, uniquePhone()).andExpect(status().isCreated());
		register(uniqueEmail(), uniquePhone(), PASSWORD).andExpect(status().isCreated());
		register(uniqueEmail(), uniquePhone(), PASSWORD).andExpect(status().isCreated());
	}

	@Test
	void twoRegistrationsOfOneShopNameAtOnceLeaveOne() throws Exception {
		String name = "Shop Race " + UUID.randomUUID();
		var pool = java.util.concurrent.Executors.newFixedThreadPool(4);
		try {
			var results = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
			for (int i = 0; i < 4; i++) {
				results.add(pool.submit(() -> registerSeller(uniqueEmail(), name, uniquePhone()).andReturn()
						.getResponse().getStatus()));
			}
			var statuses = new java.util.ArrayList<Integer>();
			for (var result : results) {
				statuses.add(result.get());
			}
			assertThat(statuses).containsOnly(201, 409).filteredOn(code -> code == 201).hasSize(1);
		}
		finally {
			pool.shutdown();
		}
	}

	// --- login ---

	@Test
	void loginReturnsTokensForTheAccountOfItsChannel() throws Exception {
		String email = uniqueEmail();
		UUID userId = UUID.fromString(registerCustomer(email));

		login(email.toUpperCase(), PASSWORD, "CUSTOMER").andExpect(status().isOk())
				.andExpect(jsonPath("$.data.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.data.accessTokenExpiresIn").value(900))
				.andExpect(jsonPath("$.data.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.data.refreshToken").doesNotExist())
				.andExpect(cookie().httpOnly("refresh_token", true))
				.andExpect(cookie().secure("refresh_token", true))
				.andExpect(cookie().path("refresh_token", "/api/v1/identity/auth"))
				.andExpect(cookie().sameSite("refresh_token", "Strict"))
				.andExpect(cookie().maxAge("refresh_token", 7 * 24 * 3600))
				.andExpect(jsonPath("$.data.accountType").value("CUSTOMER"))
				.andExpect(jsonPath("$.data.profile.accountType").value("CUSTOMER"))
				.andExpect(jsonPath("$.data.profile.roles", contains("CUSTOMER")))
				.andExpect(jsonPath("$.data.profile.permissions", hasItems("cart:manage:own", "refund:retry:own")))
				.andExpect(jsonPath("$.data.profile.customerProfile.customerId").value(userId.toString()))
				.andExpect(jsonPath("$.data.profile.sellerProfile").isEmpty());
	}

	@ParameterizedTest
	@ValueSource(strings = { "SELLER", "ADMIN" })
	void theRightPasswordOnAnotherChannelIsTheSameAnswerAsAWrongOne(String channel) throws Exception {
		String email = uniqueEmail();
		registerCustomer(email);

		String wrongChannel = body(login(email, PASSWORD, channel).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS")));
		String wrongPassword = body(login(email, "Wrong1234", "CUSTOMER").andExpect(status().isUnauthorized()));

		assertThat(wrongChannel).isEqualTo(wrongPassword);
		assertThat(jdbc.queryForObject("""
				select count(*) from users where email = ? and session_id is not null""", Integer.class,
				email)).isZero();
	}

	@Test
	void loginNeedsAKnownAccountType() throws Exception {
		String email = uniqueEmail();
		registerCustomer(email);

		postJson("/login", json("email", email, "password", PASSWORD)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.error.details[0].field").value("accountType"));
		login(email, PASSWORD, "STAFF").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
	}

	@Test
	void unknownEmailAndWrongPasswordGiveTheSameAnswer() throws Exception {
		String email = uniqueEmail();
		registerCustomer(email);

		String wrongPassword = login(email, "Wrong1234").andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"))
				.andReturn().getResponse().getContentAsString();
		String unknownEmail = login(uniqueEmail(), PASSWORD).andExpect(status().isUnauthorized())
				.andReturn().getResponse().getContentAsString();

		assertThat(unknownEmail).isEqualTo(wrongPassword);
	}

	@Test
	void googleAccountIsToldToUseGoogle() throws Exception {
		String email = uniqueEmail();
		insertGoogleUser(email);

		login(email, PASSWORD).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("WRONG_AUTH_METHOD"))
				.andExpect(jsonPath("$.error.details[0].authMethod").value("GOOGLE"));
	}

	@Test
	void disabledUserIsToldOnlyAfterTheRightPassword() throws Exception {
		String email = uniqueEmail();
		registerCustomer(email);
		disableUser(email);

		login(email, "Wrong1234").andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
		login(email, PASSWORD).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ACCOUNT_DISABLED"));
	}

	@Test
	void aSellerSignsInOnlyOnceApproved() throws Exception {
		String pending = uniqueEmail();
		registerSeller(pending, "Shop " + UUID.randomUUID(), uniquePhone()).andExpect(status().isCreated());
		Session rejected = pendingSeller();
		UUID reviewer = admin().userId();
		jdbc.update("""
				update sellers set approval_status = 'REJECTED', rejection_reason = 'Blurry', reviewed_at = now(),
				reviewed_by_user_id = ? where seller_id = ?""", reviewer, rejected.userId());
		Session approved = approvedSeller();

		login(pending, "Wrong1234", "SELLER").andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
		login(pending, PASSWORD, "SELLER").andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("SELLER_PENDING_APPROVAL"));
		login(rejected.email(), PASSWORD, "SELLER").andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("SELLER_REJECTED"));
		login(approved.email(), PASSWORD, "SELLER").andExpect(status().isOk())
				.andExpect(jsonPath("$.data.accountType").value("SELLER"))
				.andExpect(jsonPath("$.data.profile.roles", contains("SELLER")))
				.andExpect(jsonPath("$.data.profile.customerProfile").isEmpty());
		assertThat(jdbc.queryForObject("select count(*) from users where user_id in (?, ?) and session_id is not null", Integer.class,
				rejected.userId(), users.findByEmail(pending).orElseThrow().getUserId())).isZero();
	}

	@Test
	void refreshChecksTheSellerApprovalAgain() throws Exception {
		Session seller = approvedSeller();
		String refreshToken = read(loginBody(seller.email()), "$.refreshToken");
		jdbc.update("""
				update sellers set approval_status = 'PENDING', reviewed_at = null, reviewed_by_user_id = null
				where seller_id = ?""", seller.userId());

		refresh(refreshToken).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("SELLER_PENDING_APPROVAL"));
	}

	@Test
	void aBannedCustomerWithOpenOrdersStillSignsIn() throws Exception {
		String customerEmail = uniqueEmail();
		UUID customerId = UUID.fromString(registerCustomer(customerEmail));
		jdbc.update("""
				update customers set status = 'BANNED', ban_reason = 'Fraud' where customer_id = ?""", customerId);

		obligations.openOrders(customerId);
		login(customerEmail, PASSWORD).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.accessMode").value("OPEN_ORDERS_ONLY"))
				.andExpect(jsonPath("$.data.profile.accountStatus").value("BANNED"))
				.andExpect(jsonPath("$.data.profile.customerProfile.status").value("BANNED"))
				.andExpect(jsonPath("$.data.profile.customerProfile.accessMode").value("OPEN_ORDERS_ONLY"));
	}

	// --- refresh ---

	@Test
	void refreshReturnsNewTokensWithTheCurrentRoles() throws Exception {
		String email = uniqueEmail();
		UUID userId = UUID.fromString(registerCustomer(email));
		String first = read(loginBody(email), "$.refreshToken");
		tx.executeWithoutResult(status -> roleAssignments.revoke(users.findById(userId).orElseThrow(), RoleCode.CUSTOMER));

		String body = withRefreshToken(refresh(first).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.profile.roles", empty()))
				.andExpect(jsonPath("$.data.profile.permissions", not(hasItem("cart:manage:own"))))
				.andExpect(jsonPath("$.data.refreshToken").doesNotExist()));

		assertThat(read(body, "$.refreshToken")).isNotEqualTo(first);
		refresh(first).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
	}

	// --- logout ---

	@Test
	void logoutEndsTheSession() throws Exception {
		String session = loginBody(withCustomer(uniqueEmail()));

		logout(read(session, "$.refreshToken"), read(session, "$.data.accessToken"))
				.andExpect(status().isNoContent());

		refresh(read(session, "$.refreshToken")).andExpect(status().isUnauthorized());
		call(HttpMethod.GET, "/users/me", null, read(session, "$.data.accessToken"))
				.andExpect(status().isUnauthorized());
		logout(read(session, "$.refreshToken"), read(session, "$.data.accessToken"))
				.andExpect(status().isUnauthorized());
	}

	/** Decision 0003: one session per user. A new login replaces the previous one, which cannot touch the new one. */
	@Test
	void aNewLoginReplacesThePreviousSession() throws Exception {
		String email = uniqueEmail();
		registerCustomer(email);
		String phone = loginBody(email);
		String laptop = loginBody(email);

		refresh(read(phone, "$.refreshToken")).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
		call(HttpMethod.GET, "/users/me", null, read(phone, "$.data.accessToken"))
				.andExpect(status().isUnauthorized());
		logout(read(phone, "$.refreshToken"), read(phone, "$.data.accessToken"))
				.andExpect(status().isUnauthorized());
		logout(read(phone, "$.refreshToken"), read(laptop, "$.data.accessToken"))
				.andExpect(status().isNoContent());

		call(HttpMethod.GET, "/users/me", null, read(laptop, "$.data.accessToken")).andExpect(status().isOk());
		refresh(read(laptop, "$.refreshToken")).andExpect(status().isOk());
		assertThat(jdbc.queryForMap("""
				select refresh_token_hash, refresh_token_expires_at, session_id from users where email = ?""", email))
				.doesNotContainValue(null);
	}

	@Test
	void aFailedLoginKeepsTheCurrentSession() throws Exception {
		String email = uniqueEmail();
		registerCustomer(email);
		String session = loginBody(email);

		login(email, "Wrong1234").andExpect(status().isUnauthorized());

		refresh(read(session, "$.refreshToken")).andExpect(status().isOk());
	}

	@Test
	void logoutCannotEndSomeoneElsesSession() throws Exception {
		String mine = loginBody(withCustomer(uniqueEmail()));
		String theirs = loginBody(withCustomer(uniqueEmail()));

		logout(read(theirs, "$.refreshToken"), read(mine, "$.data.accessToken"))
				.andExpect(status().isNoContent());

		refresh(read(theirs, "$.refreshToken")).andExpect(status().isOk());
	}

	@Test
	void logoutWithAnUnknownRefreshTokenStillSucceedsAndChangesNothing() throws Exception {
		String session = loginBody(withCustomer(uniqueEmail()));

		logout("not-a-real-token", read(session, "$.data.accessToken"))
				.andExpect(status().isNoContent())
				.andExpect(content().string(""));

		refresh(read(session, "$.refreshToken")).andExpect(status().isOk());
	}

	@Test
	void logoutClearsTheCookie() throws Exception {
		String session = loginBody(withCustomer(uniqueEmail()));

		logout(read(session, "$.refreshToken"), read(session, "$.data.accessToken")).andExpect(status().isNoContent())
				.andExpect(cookie().maxAge("refresh_token", 0))
				.andExpect(cookie().path("refresh_token", "/api/v1/identity/auth"))
				.andExpect(cookie().httpOnly("refresh_token", true));
	}

	@ParameterizedTest
	@ValueSource(strings = { "http://evil.example", "" })
	void refreshAndLogoutNeedATrustedOrigin(String origin) throws Exception {
		String session = loginBody(withCustomer(uniqueEmail()));
		String refreshToken = read(session, "$.refreshToken");

		var refresh = post(AuthController.PATH + "/refresh").cookie(new jakarta.servlet.http.Cookie("refresh_token",
				refreshToken));
		var logout = post(AuthController.PATH + "/logout").cookie(new jakarta.servlet.http.Cookie("refresh_token",
				refreshToken)).header("Authorization", "Bearer " + read(session, "$.data.accessToken"));
		if (!origin.isEmpty()) {
			refresh.header("Origin", origin);
			logout.header("Origin", origin);
		}
		mvc.perform(refresh).andExpect(status().isForbidden());
		mvc.perform(logout).andExpect(status().isForbidden());
		refresh(refreshToken).andExpect(status().isOk());
	}

	@Test
	void refreshWithoutTheCookieIsAnInvalidRefreshToken() throws Exception {
		mvc.perform(post(AuthController.PATH + "/refresh").header("Origin", SPA_ORIGIN))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
	}

	@Test
	void logoutNeedsAnAccessToken() throws Exception {
		logout("anything", null).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
	}

	private String withCustomer(String email) throws Exception {
		registerCustomer(email);
		return email;
	}

}
