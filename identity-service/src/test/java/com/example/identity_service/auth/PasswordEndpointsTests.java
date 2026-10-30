package com.example.identity_service.auth;

import com.example.identity_service.common.security.OpaqueTokens;
import com.example.identity_service.session.SessionService;
import com.example.identity_service.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Forgot, reset and change password through HTTP (API §6.1, UC-A04, A05, checklist section 3).
 */
@ExtendWith(OutputCaptureExtension.class)
class PasswordEndpointsTests extends AuthEndpointTestSupport {

	static final String NEW_PASSWORD = "Changed456";

	@Autowired
	SessionService sessions;

	@Autowired
	UserRepository users;

	// --- forgot ---

	@Test
	void forgotAnswersTheSameWhateverTheAccount() throws Exception {
		String local = uniqueEmail();
		registerCustomer(local);
		String pendingSeller = uniqueEmail();
		registerSeller(pendingSeller, "Shop " + UUID.randomUUID(), uniquePhone()).andExpect(status().isCreated());
		String google = uniqueEmail();
		insertGoogleUser(google);
		String disabled = uniqueEmail();
		registerCustomer(disabled);
		disableUser(disabled);

		String expected = forgot(local);
		for (String email : new String[] { pendingSeller, google, disabled, uniqueEmail() }) {
			clientIp = randomIp();
			assertThat(forgot(email)).isEqualTo(expected);
		}

		assertThat(read(expected, "$.data.message")).isEqualTo(AuthController.FORGOT_PASSWORD_MESSAGE);
		assertThat(resetTokenCount(local)).isEqualTo(1);
		assertThat(resetTokenCount(pendingSeller)).isEqualTo(1);
		assertThat(resetTokenCount(google)).isZero();
		assertThat(resetTokenCount(disabled)).isZero();
	}

	@Test
	void forgotStoresOnlyAHashAndQueuesTheLinkWithoutLoggingIt(CapturedOutput output) throws Exception {
		String email = uniqueEmail();
		UUID userId = UUID.fromString(registerCustomer(email));
		Instant now = clock.instant();

		forgot(email);

		String resetUrl = resetUrlOf(userId);
		assertThat(resetUrl).startsWith("http://localhost:3000/reset-password?token=");
		String token = tokenIn(resetUrl);
		assertThat(jdbc.queryForObject("select token_hash from password_reset_tokens where user_id = ?", String.class,
				userId)).isEqualTo(OpaqueTokens.hash(token)).isNotEqualTo(token);
		assertThat(jdbc.queryForObject("select expires_at from password_reset_tokens where user_id = ?", Instant.class,
				userId)).isCloseTo(now.plus(Duration.ofMinutes(30)), within(1, ChronoUnit.SECONDS));
		assertThat(output).doesNotContain(token);
	}

	@Test
	void forgotIsRateLimitedPerClientIp() throws Exception {
		for (int i = 1; i <= 5; i++) {
			forgot(uniqueEmail());
		}

		postJson("/password/forgot", json("email", uniqueEmail())).andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
	}

	// --- reset ---

	@Test
	void resetSetsTheNewPasswordOnceAndEndsEverySession() throws Exception {
		String email = uniqueEmail();
		UUID userId = UUID.fromString(registerCustomer(email));
		String session = loginBody(email);
		forgot(email);
		String token = tokenIn(resetUrlOf(userId));

		reset(token, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

		login(email, PASSWORD).andExpect(status().isUnauthorized());
		login(email, NEW_PASSWORD).andExpect(status().isOk());
		refresh(read(session, "$.data.refreshToken")).andExpect(status().isUnauthorized());
		reset(token, "Another789", "Another789").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("RESET_TOKEN_INVALID"));
	}

	@Test
	void expiredResetLinkIsRejected() throws Exception {
		String email = uniqueEmail();
		UUID userId = UUID.fromString(registerCustomer(email));
		forgot(email);
		clock.advance(Duration.ofMinutes(30));

		reset(tokenIn(resetUrlOf(userId)), NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("RESET_TOKEN_INVALID"));
	}

	@Test
	void weakNewPasswordDoesNotUseUpTheLink() throws Exception {
		String email = uniqueEmail();
		UUID userId = UUID.fromString(registerCustomer(email));
		forgot(email);
		String token = tokenIn(resetUrlOf(userId));

		reset(token, "weak", "weak").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("PASSWORD_POLICY_VIOLATION"));
		reset(token, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());
	}

	@Test
	void unknownResetTokenIsRejected() throws Exception {
		reset(OpaqueTokens.generate(), NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("RESET_TOKEN_INVALID"));
	}

	// --- change ---

	@Test
	void changePasswordEndsEverySessionIncludingTheCurrentOne() throws Exception {
		String email = uniqueEmail();
		registerCustomer(email);
		String current = read(loginBody(email), "$.data.accessToken");
		String other = loginBody(email);

		changePassword(current, PASSWORD, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

		changePassword(current, NEW_PASSWORD, "Third7890", "Third7890").andExpect(status().isUnauthorized());
		refresh(read(other, "$.data.refreshToken")).andExpect(status().isUnauthorized());
		login(email, NEW_PASSWORD).andExpect(status().isOk());
	}

	@Test
	void changePasswordReportsEachProblemAndKeepsTheSession() throws Exception {
		String email = uniqueEmail();
		registerCustomer(email);
		String token = read(loginBody(email), "$.data.accessToken");

		changePassword(token, "Wrong1234", NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("PASSWORD_INCORRECT"));
		changePassword(token, PASSWORD, PASSWORD, PASSWORD).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("PASSWORD_SAME_AS_OLD"));
		changePassword(token, PASSWORD, NEW_PASSWORD, "Mismatch123").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.error.details[0].field").value("confirmPassword"));
		changePassword(token, PASSWORD, "weak", "weak").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("PASSWORD_POLICY_VIOLATION"));

		changePassword(token, PASSWORD, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());
	}

	@Test
	void googleAccountCannotChangePassword() throws Exception {
		UUID userId = insertGoogleUser(uniqueEmail());
		String token = sessions.start(users.findById(userId).orElseThrow()).accessToken();

		changePassword(token, PASSWORD, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
	}

	@Test
	void changePasswordNeedsAnAccessToken() throws Exception {
		changePassword("not-a-token", PASSWORD, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isUnauthorized());
	}

	private String forgot(String email) throws Exception {
		return postJson("/password/forgot", json("email", email)).andExpect(status().isAccepted())
				.andReturn().getResponse().getContentAsString();
	}

	private ResultActions reset(String token, String password, String confirm)
			throws Exception {
		return postJson("/password/reset", json("token", token, "newPassword", password, "confirmPassword", confirm));
	}

	private int resetTokenCount(String email) {
		return jdbc.queryForObject("""
				select count(*) from password_reset_tokens t join users u using (user_id) where u.email = ?""",
				Integer.class, email);
	}

	private String resetUrlOf(UUID userId) {
		return jdbc.queryForObject("""
				select payload -> 'payload' ->> 'resetUrl' from outbox_events
				where event_type = 'password.reset_requested' and aggregate_id = ?""", String.class, userId);
	}

	private static String tokenIn(String resetUrl) {
		return UriComponentsBuilder.fromUriString(resetUrl).build().getQueryParams().getFirst("token");
	}

}
