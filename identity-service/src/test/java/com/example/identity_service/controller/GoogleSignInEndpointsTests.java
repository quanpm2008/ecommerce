package com.example.identity_service.controller;

import com.example.identity_service.EndpointTestSupport;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code POST /auth/google} (API §6.1, UC-A01.ALT-01/04/05, UC-A03.ALT-06), with Google faked. */
class GoogleSignInEndpointsTests extends EndpointTestSupport {

	private org.springframework.test.web.servlet.ResultActions signIn(String idToken) throws Exception {
		return postJson("/google", json("idToken", idToken));
	}

	@Test
	void aNewGoogleAccountBecomesACustomerAndSignsIn() throws Exception {
		String email = uniqueEmail();
		String subject = UUID.randomUUID().toString();

		String body = body(signIn(google.issue(subject, email.toUpperCase(), "Tran Thi Binh"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isNewAccount").value(true))
				.andExpect(jsonPath("$.data.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.data.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.data.refreshToken").doesNotExist())
				.andExpect(cookie().httpOnly("refresh_token", true))
				.andExpect(cookie().secure("refresh_token", true))
				.andExpect(cookie().path("refresh_token", "/api/v1/identity/auth"))
				.andExpect(cookie().sameSite("refresh_token", "Strict"))
				.andExpect(jsonPath("$.data.accountType").value("CUSTOMER"))
				.andExpect(jsonPath("$.data.accessMode").value("FULL"))
				.andExpect(jsonPath("$.data.profile.authMethod").value("GOOGLE"))
				.andExpect(jsonPath("$.data.profile.email").value(email))
				.andExpect(jsonPath("$.data.profile.fullName").value("Tran Thi Binh"))
				.andExpect(jsonPath("$.data.profile.roles", contains("CUSTOMER")))
				.andExpect(jsonPath("$.data.profile.customerProfile.profileComplete").value(false))
				.andExpect(jsonPath("$.data.profile.sellerProfile").isEmpty()));

		Map<String, Object> row = jdbc.queryForMap("""
				select auth_method, google_subject, password_hash, phone_number, account_type from users
				where email = ?""", email);
		assertThat(row).containsEntry("auth_method", "GOOGLE").containsEntry("google_subject", subject)
				.containsEntry("account_type", "CUSTOMER");
		assertThat(row.get("password_hash")).isNull();
		assertThat(row.get("phone_number")).isNull();
		String userId = read(body, "$.data.profile.userId");
		assertThat(jdbc.queryForObject("select count(*) from customers where customer_id = ?::uuid", Integer.class,
				userId)).isOne();
	}

	@Test
	void theSameGoogleAccountSignsInAgainToTheSameAccount() throws Exception {
		String email = uniqueEmail();
		String subject = UUID.randomUUID().toString();
		String first = body(signIn(google.issue(subject, email, "An")).andExpect(status().isOk()));

		String second = body(signIn(google.issue(subject, email, "An")).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isNewAccount").value(false)));

		assertThat(read(second, "$.data.profile.userId")).isEqualTo(read(first, "$.data.profile.userId"));
		assertThat(jdbc.queryForObject("select count(*) from users where google_subject = ?", Integer.class,
				subject)).isOne();
	}

	@Test
	void anEmailRegisteredWithAPasswordIsNeverLinked() throws Exception {
		String customer = uniqueEmail();
		registerCustomer(customer);
		String seller = uniqueEmail();
		registerSeller(seller, "Shop " + UUID.randomUUID(), uniquePhone()).andExpect(status().isCreated());

		for (String email : new String[] { customer, seller }) {
			signIn(google.issue(UUID.randomUUID().toString(), email, "Someone")).andExpect(status().isConflict())
					.andExpect(jsonPath("$.error.code").value("WRONG_AUTH_METHOD"))
					.andExpect(jsonPath("$.error.details[0].authMethod").value("LOCAL"));
			assertThat(jdbc.queryForObject("select auth_method from users where email = ?", String.class, email))
					.isEqualTo("LOCAL");
		}
	}

	@Test
	void aTokenGoogleDidNotVerifyIsRejectedAndCreatesNothing() throws Exception {
		signIn("not-a-google-token").andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("GOOGLE_AUTH_FAILED"))
				.andExpect(cookie().doesNotExist("refresh_token"));
		postJson("/google", "{}").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.error.details[0].field").value("idToken"));
	}

	@Test
	void anotherGoogleAccountWithATakenEmailIsRejected() throws Exception {
		String email = uniqueEmail();
		insertGoogleUser(email);

		signIn(google.issue(UUID.randomUUID().toString(), email, "Other")).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("GOOGLE_AUTH_FAILED"));
	}

	@Test
	void aDisabledGoogleCustomerCannotSignIn() throws Exception {
		String email = uniqueEmail();
		String subject = UUID.randomUUID().toString();
		signIn(google.issue(subject, email, "An")).andExpect(status().isOk());
		disableUser(email);

		signIn(google.issue(subject, email, "An")).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ACCOUNT_DISABLED"));
	}

	@Test
	void withoutAGoogleNameTheEmailLocalPartIsTheName() throws Exception {
		String email = uniqueEmail();

		signIn(google.issue(UUID.randomUUID().toString(), email, null)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.profile.fullName").value(email.substring(0, email.indexOf('@'))));
	}

}
