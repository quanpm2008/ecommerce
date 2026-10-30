package com.example.identity_service.session;

import com.example.identity_service.MutableClock;
import com.example.identity_service.TestClockConfiguration;
import com.example.identity_service.TestcontainersConfiguration;
import com.example.identity_service.common.error.ApiException;
import com.example.identity_service.common.error.ErrorCode;
import com.example.identity_service.rbac.RoleAssignments;
import com.example.identity_service.rbac.RoleCode;
import com.example.identity_service.user.User;
import com.example.identity_service.user.UserRepository;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Not {@code @Transactional}: each service call commits on its own, as in production, so revocations that must
 * survive an exception are really persisted. Every test uses a fresh user.
 */
@SpringBootTest(properties = { "eureka.client.enabled=false", "identity.token.access-ttl=PT5M",
		"identity.token.refresh-ttl=P2D" })
@Import({ TestcontainersConfiguration.class, TestClockConfiguration.class })
class SessionServiceTests {

	@Autowired
	SessionService sessions;

	@Autowired
	UserRepository users;

	@Autowired
	RoleAssignments roleAssignments;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	TransactionTemplate tx;

	@Autowired
	MutableClock clock;

	@BeforeEach
	void resetClock() {
		clock.reset();
	}

	@Test
	void accessTokenCarriesExactlyTheSpecifiedClaims() throws Exception {
		User user = newUser();

		IssuedTokens tokens = sessions.start(user);

		SignedJWT jwt = SignedJWT.parse(tokens.accessToken());
		assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
		assertThat(jwt.getHeader().getKeyID()).isNotBlank();
		JWTClaimsSet claims = jwt.getJWTClaimsSet();
		assertThat(claims.getClaims().keySet()).containsExactlyInAnyOrder("sub", "iss", "aud", "iat", "exp", "jti",
				"sessionId", "authzVersion");
		assertThat(claims.getSubject()).isEqualTo(user.getUserId().toString());
		assertThat(claims.getIssuer()).isEqualTo("ecommerce-identity");
		assertThat(claims.getAudience()).containsExactly("ecommerce-api");
		assertThat(claims.getStringClaim("sessionId")).isEqualTo(tokens.sessionId().toString());
		assertThat(claims.getLongClaim("authzVersion")).isEqualTo(user.getAuthzVersion());
		assertThat(claims.getJWTID()).isNotBlank();
		assertThat(jwt.getPayload().toString()).doesNotContain(user.getEmail());
	}

	@Test
	void lifetimesComeFromConfiguration() throws Exception {
		Instant now = clock.instant();

		IssuedTokens tokens = sessions.start(newUser());

		JWTClaimsSet claims = SignedJWT.parse(tokens.accessToken()).getJWTClaimsSet();
		assertThat(Duration.between(claims.getIssueTime().toInstant(), claims.getExpirationTime().toInstant()))
				.isEqualTo(Duration.ofMinutes(5));
		assertThat(tokens.accessTokenExpiresIn()).isEqualTo(300);
		Instant sessionExpiry = jdbc.queryForObject("select expires_at from auth_sessions where session_id = ?",
				Instant.class, tokens.sessionId());
		assertThat(sessionExpiry).isCloseTo(now.plus(Duration.ofDays(2)), within(1, ChronoUnit.SECONDS));
	}

	@Test
	void onlyTheRefreshTokenHashIsStored() {
		IssuedTokens tokens = sessions.start(newUser());

		String stored = jdbc.queryForObject("select refresh_token_hash from auth_sessions where session_id = ?",
				String.class, tokens.sessionId());
		assertThat(stored).isNotEqualTo(tokens.refreshToken()).isEqualTo(RefreshTokens.hash(tokens.refreshToken()));
	}

	@Test
	void refreshRotatesTheTokenWithinTheSameSession() {
		IssuedTokens first = sessions.start(newUser());

		IssuedTokens second = sessions.refresh(first.refreshToken());
		IssuedTokens third = sessions.refresh(second.refreshToken());

		assertThat(List.of(first.refreshToken(), second.refreshToken(), third.refreshToken())).doesNotHaveDuplicates();
		assertThat(List.of(second.sessionId(), third.sessionId())).containsOnly(first.sessionId());
	}

	@Test
	void reusingARotatedTokenRevokesTheWholeSession() {
		IssuedTokens first = sessions.start(newUser());
		IssuedTokens second = sessions.refresh(first.refreshToken());

		assertInvalidRefresh(first.refreshToken());

		assertThat(revokedAt(first.sessionId())).isNotNull();
		assertInvalidRefresh(second.refreshToken());
	}

	@Test
	void refreshCarriesTheCurrentAuthzVersion() throws Exception {
		User user = newUser();
		IssuedTokens before = sessions.start(user);
		tx.executeWithoutResult(status -> roleAssignments.grant(users.findById(user.getUserId()).orElseThrow(),
				RoleCode.CUSTOMER, null));

		IssuedTokens after = sessions.refresh(before.refreshToken());

		assertThat(authzVersion(after)).isEqualTo(authzVersion(before) + 1);
	}

	@Test
	void unknownRefreshTokenIsRejected() {
		assertInvalidRefresh(RefreshTokens.generate());
	}

	@Test
	void expiredRefreshTokenIsRejected() {
		IssuedTokens tokens = sessions.start(newUser());
		clock.advance(Duration.ofDays(2).plusSeconds(1));

		assertInvalidRefresh(tokens.refreshToken());
	}

	@Test
	void refreshOfARevokedSessionIsRejected() {
		IssuedTokens tokens = sessions.start(newUser());
		sessions.revoke(tokens.sessionId());

		assertInvalidRefresh(tokens.refreshToken());
	}

	@Test
	void refreshForADisabledUserIsRejected() {
		User user = newUser();
		IssuedTokens tokens = sessions.start(user);
		User stored = users.findById(user.getUserId()).orElseThrow();
		stored.disable("Fraud");
		users.save(stored);

		assertInvalidRefresh(tokens.refreshToken());
	}

	@Test
	void revokeAllEndsEverySessionOfThatUserOnly() {
		User user = newUser();
		IssuedTokens phone = sessions.start(user);
		IssuedTokens laptop = sessions.start(user);
		IssuedTokens someoneElse = sessions.start(newUser());

		sessions.revokeAllOf(user.getUserId());

		assertThat(revokedAt(phone.sessionId())).isNotNull();
		assertThat(revokedAt(laptop.sessionId())).isNotNull();
		assertThat(revokedAt(someoneElse.sessionId())).isNull();
	}

	private void assertInvalidRefresh(String refreshToken) {
		assertThatThrownBy(() -> sessions.refresh(refreshToken)).isInstanceOfSatisfying(ApiException.class,
				ex -> assertThat(ex.code()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN));
	}

	private Instant revokedAt(UUID sessionId) {
		return jdbc.queryForObject("select revoked_at from auth_sessions where session_id = ?", Instant.class,
				sessionId);
	}

	private static long authzVersion(IssuedTokens tokens) throws Exception {
		return SignedJWT.parse(tokens.accessToken()).getJWTClaimsSet().getLongClaim("authzVersion");
	}

	private User newUser() {
		return users.save(User.local(UUID.randomUUID() + "@example.test", "{noop}x", "Test User", null));
	}

}
