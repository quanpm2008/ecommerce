package com.example.identity_service.service;

import com.example.identity_service.dto.IssuedTokens;
import com.example.identity_service.MutableClock;
import com.example.identity_service.TestClockConfiguration;
import com.example.identity_service.TestcontainersConfiguration;
import com.example.identity_service.exception.ApiException;
import com.example.identity_service.security.OpaqueTokens;
import com.example.identity_service.exception.ErrorCode;
import com.example.identity_service.entity.RoleCode;
import com.example.identity_service.entity.AccountType;
import com.example.identity_service.entity.User;
import com.example.identity_service.repository.UserRepository;
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
				"sessionId", "authzVersion", "accountType");
		assertThat(claims.getStringClaim("accountType")).isEqualTo("CUSTOMER");
		assertThat(claims.getSubject()).isEqualTo(user.getUserId().toString());
		assertThat(claims.getIssuer()).isEqualTo("ecommerce-identity");
		assertThat(claims.getAudience()).containsExactly("ecommerce-api");
		assertThat(claims.getStringClaim("sessionId")).isEqualTo(tokens.sessionId().toString());
		assertThat(claims.getLongClaim("authzVersion")).isEqualTo(user.getAuthzVersion());
		assertThat(claims.getJWTID()).isNotBlank();
		assertThat(jwt.getPayload().toString()).doesNotContain(user.getEmail());
	}

	@Test
	void accessTokenCarriesASnapshotOfTheOneRole() throws Exception {
		User user = newUser();
		tx.executeWithoutResult(status -> roleAssignments.grant(users.findById(user.getUserId()).orElseThrow(),
				RoleCode.CUSTOMER, null));

		IssuedTokens tokens = sessions.start(users.findById(user.getUserId()).orElseThrow());

		JWTClaimsSet claims = SignedJWT.parse(tokens.accessToken()).getJWTClaimsSet();
		assertThat(claims.getStringClaim("role")).isEqualTo("CUSTOMER");
		assertThat(claims.getStringClaim("accountType")).isEqualTo("CUSTOMER");
	}

	@Test
	void lifetimesComeFromConfiguration() throws Exception {
		Instant now = clock.instant();

		IssuedTokens tokens = sessions.start(newUser());

		JWTClaimsSet claims = SignedJWT.parse(tokens.accessToken()).getJWTClaimsSet();
		assertThat(Duration.between(claims.getIssueTime().toInstant(), claims.getExpirationTime().toInstant()))
				.isEqualTo(Duration.ofMinutes(5));
		assertThat(tokens.accessTokenExpiresIn()).isEqualTo(300);
		Instant sessionExpiry = jdbc.queryForObject("select refresh_token_expires_at from users where session_id = ?",
				Instant.class, tokens.sessionId());
		assertThat(sessionExpiry).isCloseTo(now.plus(Duration.ofDays(2)), within(1, ChronoUnit.SECONDS));
	}

	@Test
	void onlyTheRefreshTokenHashIsStored() {
		IssuedTokens tokens = sessions.start(newUser());

		String stored = jdbc.queryForObject("select refresh_token_hash from users where session_id = ?",
				String.class, tokens.sessionId());
		assertThat(stored).isNotEqualTo(tokens.refreshToken()).isEqualTo(OpaqueTokens.hash(tokens.refreshToken()));
	}

	@Test
	void refreshRotatesTheTokenWithinTheSameSession() {
		IssuedTokens first = sessions.start(newUser());

		IssuedTokens second = sessions.refresh(first.refreshToken(), account -> {
		});
		IssuedTokens third = sessions.refresh(second.refreshToken(), account -> {
		});

		assertThat(List.of(first.refreshToken(), second.refreshToken(), third.refreshToken())).doesNotHaveDuplicates();
		assertThat(List.of(second.sessionId(), third.sessionId())).containsOnly(first.sessionId());
	}

	@Test
	void reusingARotatedTokenRevokesTheWholeSession() {
		IssuedTokens first = sessions.start(newUser());
		IssuedTokens second = sessions.refresh(first.refreshToken(), account -> {
		});

		assertInvalidRefresh(first.refreshToken());

		assertThat(currentSessionOf(first.userId())).isNull();
		assertInvalidRefresh(second.refreshToken());
	}

	@Test
	void refreshKeepsTheSessionAndMovesItsExpiry() {
		IssuedTokens first = sessions.start(newUser());
		clock.advance(Duration.ofDays(1));

		IssuedTokens second = sessions.refresh(first.refreshToken(), account -> {
		});

		assertThat(second.sessionId()).isEqualTo(first.sessionId());
		Instant expiry = jdbc.queryForObject("select refresh_token_expires_at from users where user_id = ?",
				Instant.class, first.userId());
		assertThat(expiry).isCloseTo(clock.instant().plus(Duration.ofDays(2)), within(1, ChronoUnit.SECONDS));
	}

	@Test
	void aNewSessionReplacesTheOldOneAndItsTokensCannotEndIt() {
		User user = newUser();
		IssuedTokens old = sessions.start(user);
		IssuedTokens oldRotated = sessions.refresh(old.refreshToken(), account -> {
		});
		IssuedTokens current = sessions.start(user);

		assertThat(current.sessionId()).isNotEqualTo(old.sessionId());
		assertInvalidRefresh(oldRotated.refreshToken());
		assertInvalidRefresh(old.refreshToken());
		sessions.logout(user.getUserId(), old.sessionId(), oldRotated.refreshToken());

		assertThat(currentSessionOf(user.getUserId())).isEqualTo(current.sessionId());
		assertThat(sessions.refresh(current.refreshToken(), account -> {
		}).sessionId()).isEqualTo(current.sessionId());
		assertThat(jdbc.queryForObject("select count(*) from rotated_refresh_tokens where session_id = ?",
				Integer.class, old.sessionId())).isZero();
	}

	@Test
	void logoutAcceptsARotatedTokenOfTheCurrentSession() {
		User user = newUser();
		IssuedTokens first = sessions.start(user);
		IssuedTokens second = sessions.refresh(first.refreshToken(), account -> {
		});

		sessions.logout(user.getUserId(), second.sessionId(), first.refreshToken());

		assertThat(currentSessionOf(user.getUserId())).isNull();
		assertInvalidRefresh(second.refreshToken());
	}

	@Test
	void refreshCarriesTheCurrentAuthzVersion() throws Exception {
		User user = newUser();
		IssuedTokens before = sessions.start(user);
		tx.executeWithoutResult(status -> roleAssignments.grant(users.findById(user.getUserId()).orElseThrow(),
				RoleCode.CUSTOMER, null));

		IssuedTokens after = sessions.refresh(before.refreshToken(), account -> {
		});

		assertThat(authzVersion(after)).isEqualTo(authzVersion(before) + 1);
	}

	@Test
	void unknownRefreshTokenIsRejected() {
		assertInvalidRefresh(OpaqueTokens.generate());
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
		sessions.revokeAllOf(tokens.userId());

		assertInvalidRefresh(tokens.refreshToken());
	}

	@Test
	void refreshForADisabledUserIsRejected() {
		User user = newUser();
		IssuedTokens tokens = sessions.start(user);
		User stored = users.findById(user.getUserId()).orElseThrow();
		stored.startBan(java.time.Instant.now(), com.example.identity_service.entity.AccessMode.LOGIN_BLOCKED);
		stored.disableForBan("Khác: Fraud", java.time.Instant.now());
		users.save(stored);

		assertInvalidRefresh(tokens.refreshToken());
	}

	@Test
	void revokeAllEndsTheSessionOfThatUserOnly() {
		User user = newUser();
		IssuedTokens mine = sessions.start(user);
		IssuedTokens someoneElse = sessions.start(newUser());

		sessions.revokeAllOf(user.getUserId());

		assertThat(jdbc.queryForMap("""
				select refresh_token_hash, refresh_token_expires_at, session_id from users where user_id = ?""",
				mine.userId())).containsOnlyKeys("refresh_token_hash", "refresh_token_expires_at", "session_id")
				.allSatisfy((column, value) -> assertThat(value).isNull());
		assertThat(currentSessionOf(someoneElse.userId())).isEqualTo(someoneElse.sessionId());
	}

	private void assertInvalidRefresh(String refreshToken) {
		assertThatThrownBy(() -> sessions.refresh(refreshToken, account -> {
		})).isInstanceOfSatisfying(ApiException.class,
				ex -> assertThat(ex.code()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN));
	}

	private UUID currentSessionOf(UUID userId) {
		return jdbc.queryForObject("select session_id from users where user_id = ?", UUID.class, userId);
	}

	private static long authzVersion(IssuedTokens tokens) throws Exception {
		return SignedJWT.parse(tokens.accessToken()).getJWTClaimsSet().getLongClaim("authzVersion");
	}

	private User newUser() {
		return users.save(User.local(AccountType.CUSTOMER, UUID.randomUUID() + "@example.test", "{noop}x", "Test User", null));
	}

}
