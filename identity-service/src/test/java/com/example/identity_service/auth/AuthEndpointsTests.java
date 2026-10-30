package com.example.identity_service.auth;

import com.example.identity_service.rbac.RoleAssignments;
import com.example.identity_service.rbac.RoleCode;
import com.example.identity_service.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Register, register-seller, login, refresh and logout through HTTP (API §6.1, checklist section 3).
 */
class AuthEndpointsTests extends AuthEndpointTestSupport {

	@Autowired
	RoleAssignments roleAssignments;

	@Autowired
	UserRepository users;

	@Autowired
	TransactionTemplate tx;

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
				.andExpect(jsonPath("$.data.customerProfile.paymentBlocked").value(false))
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
	void registerSellerCreatesAPendingSellerWithoutAnyRole() throws Exception {
		String email = uniqueEmail();
		String contactPhone = uniquePhone();

		String body = registerSeller(email, "Shop " + UUID.randomUUID(), "+84" + contactPhone.substring(1))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.roles", empty()))
				.andExpect(jsonPath("$.data.permissions",
						containsInAnyOrder("account:read:own", "account:update:own", "seller:application:own")))
				.andExpect(jsonPath("$.data.phoneNumber").isEmpty())
				.andExpect(jsonPath("$.data.customerProfile").isEmpty())
				.andExpect(jsonPath("$.data.sellerProfile.approvalStatus").value("PENDING"))
				.andExpect(jsonPath("$.data.sellerProfile.status").value("ACTIVE"))
				.andExpect(jsonPath("$.data.sellerProfile.contactPhone").value(contactPhone))
				.andReturn().getResponse().getContentAsString();

		UUID userId = UUID.fromString(read(body, "$.data.userId"));
		assertThat(read(body, "$.data.sellerProfile.sellerId")).isEqualTo(userId.toString());
		assertThat(jdbc.queryForObject("select count(*) from user_roles where user_id = ?", Integer.class, userId))
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
	void registerSellerRejectsATakenShopNameOrContactPhone() throws Exception {
		String name = "Shop Ao Xinh " + UUID.randomUUID();
		String phone = uniquePhone();
		registerSeller(uniqueEmail(), name, phone).andExpect(status().isCreated());

		registerSeller(uniqueEmail(), "  " + name.toUpperCase().replace(" ", "   ") + " ", uniquePhone())
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("SELLER_NAME_ALREADY_USED"))
				.andExpect(jsonPath("$.error.details[0].field").value("sellerName"));
		registerSeller(uniqueEmail(), "Shop " + UUID.randomUUID(), phone).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("PHONE_ALREADY_USED"))
				.andExpect(jsonPath("$.error.details[0].field").value("contactPhone"));
	}

	// --- login ---

	@Test
	void oneLoginReturnsTokensAndEveryProfileTheUserHas() throws Exception {
		String email = uniqueEmail();
		UUID userId = UUID.fromString(registerCustomer(email));
		makeApprovedSeller(userId);

		login(email.toUpperCase(), PASSWORD).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.data.accessTokenExpiresIn").value(900))
				.andExpect(jsonPath("$.data.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
				.andExpect(jsonPath("$.data.profile.roles", contains("CUSTOMER", "SELLER")))
				.andExpect(jsonPath("$.data.profile.permissions", hasItems("cart:manage:own", "product:manage:own")))
				.andExpect(jsonPath("$.data.profile.customerProfile.customerId").value(userId.toString()))
				.andExpect(jsonPath("$.data.profile.sellerProfile.approvalStatus").value("APPROVED"));
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
	void restrictedProfilesStillLogIn() throws Exception {
		String sellerEmail = uniqueEmail();
		registerSeller(sellerEmail, "Shop " + UUID.randomUUID(), uniquePhone()).andExpect(status().isCreated());
		String customerEmail = uniqueEmail();
		UUID customerId = UUID.fromString(registerCustomer(customerEmail));
		jdbc.update("""
				update customers set status = 'BANNED', ban_reason = 'Fraud', ban_mode = 'CUSTOMER_SCOPE',
				has_paid_order_before_ban = false where customer_id = ?""", customerId);

		login(sellerEmail, PASSWORD).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.profile.roles", empty()))
				.andExpect(jsonPath("$.data.profile.sellerProfile.approvalStatus").value("PENDING"));
		login(customerEmail, PASSWORD).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.profile.customerProfile.status").value("BANNED"))
				.andExpect(jsonPath("$.data.profile.customerProfile.paymentBlocked").value(true))
				.andExpect(jsonPath("$.data.profile.customerProfile.accessMode").value("BLOCKED"));
	}

	// --- refresh ---

	@Test
	void refreshReturnsNewTokensWithTheCurrentRoles() throws Exception {
		String email = uniqueEmail();
		UUID userId = UUID.fromString(registerCustomer(email));
		String first = read(loginBody(email), "$.data.refreshToken");
		tx.executeWithoutResult(status -> roleAssignments.revoke(users.findById(userId).orElseThrow(), RoleCode.CUSTOMER));

		String body = refresh(first).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.profile.roles", empty()))
				.andExpect(jsonPath("$.data.profile.permissions", not(hasItem("cart:manage:own"))))
				.andReturn().getResponse().getContentAsString();

		assertThat(read(body, "$.data.refreshToken")).isNotEqualTo(first);
		refresh(first).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
	}

	// --- logout ---

	@Test
	void logoutEndsOnlyTheCurrentSession() throws Exception {
		String email = uniqueEmail();
		registerCustomer(email);
		String phone = loginBody(email);
		String laptop = loginBody(email);

		postJson("/logout", json("refreshToken", read(phone, "$.data.refreshToken")), read(phone, "$.data.accessToken"))
				.andExpect(status().isNoContent());

		refresh(read(phone, "$.data.refreshToken")).andExpect(status().isUnauthorized());
		postJson("/logout", json("refreshToken", read(phone, "$.data.refreshToken")), read(phone, "$.data.accessToken"))
				.andExpect(status().isUnauthorized());
		refresh(read(laptop, "$.data.refreshToken")).andExpect(status().isOk());
	}

	@Test
	void logoutCannotEndSomeoneElsesSession() throws Exception {
		String mine = loginBody(withCustomer(uniqueEmail()));
		String theirs = loginBody(withCustomer(uniqueEmail()));

		postJson("/logout", json("refreshToken", read(theirs, "$.data.refreshToken")), read(mine, "$.data.accessToken"))
				.andExpect(status().isNoContent());

		refresh(read(theirs, "$.data.refreshToken")).andExpect(status().isOk());
	}

	@Test
	void logoutWithAnUnknownRefreshTokenStillSucceedsAndChangesNothing() throws Exception {
		String session = loginBody(withCustomer(uniqueEmail()));

		postJson("/logout", json("refreshToken", "not-a-real-token"), read(session, "$.data.accessToken"))
				.andExpect(status().isNoContent())
				.andExpect(content().string(""));

		refresh(read(session, "$.data.refreshToken")).andExpect(status().isOk());
	}

	@Test
	void logoutNeedsAnAccessToken() throws Exception {
		postJson("/logout", json("refreshToken", "anything")).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
	}

	private String withCustomer(String email) throws Exception {
		registerCustomer(email);
		return email;
	}

	/** Gives the user an APPROVED Seller profile and the SELLER role, as an admin approval would. */
	private void makeApprovedSeller(UUID userId) {
		UUID reviewer = jdbc.queryForObject("""
				insert into users (email, auth_method, password_hash, full_name) values (?, 'LOCAL', 'x', 'Reviewer')
				returning user_id""", UUID.class, uniqueEmail());
		jdbc.update("""
				insert into sellers (seller_id, seller_name, contact_phone, approval_status, reviewed_at, reviewed_by_user_id)
				values (?, ?, ?, 'APPROVED', now(), ?)""", userId, "Shop " + userId, uniquePhone(), reviewer);
		tx.executeWithoutResult(status -> roleAssignments.grant(users.findById(userId).orElseThrow(), RoleCode.SELLER,
				reviewer));
	}

}
