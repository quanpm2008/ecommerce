package com.example.identity_service.session;

import com.example.identity_service.MutableClock;
import com.example.identity_service.TestClockConfiguration;
import com.example.identity_service.TestcontainersConfiguration;
import com.example.identity_service.common.web.ApiResponse;
import com.example.identity_service.rbac.RoleAssignments;
import com.example.identity_service.rbac.RoleCode;
import com.example.identity_service.user.User;
import com.example.identity_service.user.UserRepository;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Access token checks on a protected endpoint, through the real security filter chain.
 */
@SpringBootTest(properties = { "eureka.client.enabled=false", "identity.token.access-ttl=PT5M" })
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestClockConfiguration.class,
		AccessTokenAuthenticationTests.WhoAmIController.class })
class AccessTokenAuthenticationTests {

	@Autowired
	MockMvc mvc;

	@Autowired
	SessionService sessions;

	@Autowired
	UserRepository users;

	@Autowired
	RoleAssignments roleAssignments;

	@Autowired
	JwtEncoder signingEncoder;

	@Autowired
	TransactionTemplate tx;

	@Autowired
	MutableClock clock;

	@BeforeEach
	void resetClock() {
		clock.reset();
	}

	@Test
	void validTokenIsAccepted() throws Exception {
		User user = newUser();
		IssuedTokens tokens = sessions.start(user);

		call(tokens.accessToken()).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.sub").value(user.getUserId().toString()));
	}

	@Test
	void missingTokenIsUnauthenticatedInTheErrorEnvelope() throws Exception {
		mvc.perform(get("/test/whoami").header("X-Request-Id", "req-401"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"))
				.andExpect(jsonPath("$.error.details").isEmpty())
				.andExpect(jsonPath("$.error.requestId").value("req-401"));
	}

	@Test
	void expiredTokenIsTokenExpired() throws Exception {
		IssuedTokens tokens = sessions.start(newUser());
		clock.advance(Duration.ofMinutes(5).plus(Duration.ofMinutes(2)));

		expectError(call(tokens.accessToken()), "TOKEN_EXPIRED");
	}

	@Test
	void tokenFromBeforeARoleChangeIsTokenStale() throws Exception {
		User user = newUser();
		IssuedTokens tokens = sessions.start(user);
		tx.executeWithoutResult(status -> roleAssignments.grant(users.findById(user.getUserId()).orElseThrow(),
				RoleCode.CUSTOMER, null));

		expectError(call(tokens.accessToken()), "TOKEN_STALE");
		call(sessions.refresh(tokens.refreshToken()).accessToken()).andExpect(status().isOk());
	}

	@Test
	void tokenOfARevokedSessionIsRejected() throws Exception {
		IssuedTokens tokens = sessions.start(newUser());
		sessions.revoke(tokens.sessionId());

		expectError(call(tokens.accessToken()), "UNAUTHENTICATED");
	}

	@Test
	void tokenOfADisabledUserIsRejected() throws Exception {
		User user = newUser();
		IssuedTokens tokens = sessions.start(user);
		User stored = users.findById(user.getUserId()).orElseThrow();
		stored.disable("Fraud");
		users.save(stored);

		// The version bump alone would say TOKEN_STALE; a disabled user must not be told to refresh.
		expectError(call(tokens.accessToken()), "UNAUTHENTICATED");
	}

	@Test
	void tokenSignedWithAnotherKeyIsRejected() throws Exception {
		IssuedTokens tokens = sessions.start(newUser());
		RSAKey otherKey = new RSAKeyGenerator(2048).generate();
		JwtEncoder otherEncoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(otherKey)));

		expectError(call(resign(otherEncoder, tokens.accessToken(), claims -> { })), "UNAUTHENTICATED");
	}

	@Test
	void unsignedTokenIsRejected() throws Exception {
		IssuedTokens tokens = sessions.start(newUser());
		String header = Base64.getUrlEncoder().withoutPadding()
				.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
		String payload = tokens.accessToken().split("\\.")[1];

		expectError(call(header + "." + payload + "."), "UNAUTHENTICATED");
	}

	@Test
	void wrongIssuerOrAudienceIsRejected() throws Exception {
		IssuedTokens tokens = sessions.start(newUser());

		expectError(call(resign(signingEncoder, tokens.accessToken(), c -> c.put("iss", "someone-else"))),
				"UNAUTHENTICATED");
		expectError(call(resign(signingEncoder, tokens.accessToken(), c -> c.put("aud", List.of("other-api")))),
				"UNAUTHENTICATED");
	}

	@Test
	void tokenForAnotherUsersSessionIsRejected() throws Exception {
		IssuedTokens mine = sessions.start(newUser());
		IssuedTokens theirs = sessions.start(newUser());

		String forged = resign(signingEncoder, mine.accessToken(),
				c -> c.put("sessionId", theirs.sessionId().toString()));

		expectError(call(forged), "UNAUTHENTICATED");
	}

	@Test
	void jwksPublishesOnlyThePublicKeyThatVerifiesTokens() throws Exception {
		IssuedTokens tokens = sessions.start(newUser());

		String body = mvc.perform(get(JwksController.PATH)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data").doesNotExist())
				.andReturn().getResponse().getContentAsString();

		List<JWK> keys = JWKSet.parse(body).getKeys();
		assertThat(keys).singleElement().satisfies(key -> assertThat(key.isPrivate()).isFalse());
		SignedJWT jwt = SignedJWT.parse(tokens.accessToken());
		assertThat(keys.getFirst().getKeyID()).isEqualTo(jwt.getHeader().getKeyID());
		assertThat(jwt.verify(new RSASSAVerifier(keys.getFirst().toRSAKey()))).isTrue();
	}

	@Test
	void healthNeedsNoToken() throws Exception {
		mvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}

	private ResultActions call(String accessToken) throws Exception {
		return mvc.perform(get("/test/whoami").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
	}

	private static void expectError(ResultActions result, String code) throws Exception {
		result.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value(code));
	}

	/** Re-signs a real token's claims, after {@code change}, with {@code encoder}. */
	private static String resign(JwtEncoder encoder, String token, Consumer<Map<String, Object>> change)
			throws Exception {
		Map<String, Object> claims = new java.util.HashMap<>(SignedJWT.parse(token).getJWTClaimsSet().toJSONObject());
		change.accept(claims);
		JwtClaimsSet claimSet = JwtClaimsSet.builder().claims(c -> c.putAll(claims)).build();
		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claimSet))
				.getTokenValue();
	}

	private User newUser() {
		return users.save(User.local(UUID.randomUUID() + "@example.test", "{noop}x", "Test User", null));
	}

	@RestController
	static class WhoAmIController {

		@GetMapping("/test/whoami")
		ApiResponse<Map<String, String>> whoAmI(@AuthenticationPrincipal Jwt jwt) {
			return ApiResponse.of(Map.of("sub", jwt.getSubject()));
		}

	}

}
