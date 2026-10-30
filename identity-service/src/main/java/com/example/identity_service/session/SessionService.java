package com.example.identity_service.session;

import com.example.identity_service.common.error.ApiException;
import com.example.identity_service.common.security.OpaqueTokens;
import com.example.identity_service.common.error.ErrorCode;
import com.example.identity_service.user.User;
import com.example.identity_service.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Sessions and their tokens (API §3.2, DB §6). Refresh rotates the token; an already rotated token presented again
 * revokes the whole session. Access tokens always carry the user's current {@code authzVersion}, never data copied
 * from an older token.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService {

	private final AuthSessionRepository sessions;

	private final RotatedRefreshTokenRepository rotatedTokens;

	private final UserRepository users;

	private final AccessTokenIssuer accessTokens;

	private final TokenProperties properties;

	private final Clock clock;

	/** Starts a session for a user whose credentials were already checked. */
	@Transactional
	public IssuedTokens start(User user) {
		if (!user.isActive()) {
			throw new IllegalStateException("Cannot start a session for a disabled user");
		}
		String refreshToken = OpaqueTokens.generate();
		AuthSession session = sessions.save(new AuthSession(user.getUserId(), OpaqueTokens.hash(refreshToken),
				clock.instant().plus(properties.refreshTtl())));
		return tokens(user, session, refreshToken);
	}

	/**
	 * Exchanges a refresh token for a new pair. Unknown, expired, revoked or reused tokens and disabled users all give
	 * {@code INVALID_REFRESH_TOKEN}. {@code noRollbackFor} keeps the revocation of a reused token's session.
	 */
	@Transactional(noRollbackFor = ApiException.class)
	public IssuedTokens refresh(String refreshToken) {
		Instant now = clock.instant();
		String hash = OpaqueTokens.hash(refreshToken);
		Optional<AuthSession> current = sessions.findByRefreshTokenHash(hash);
		if (current.isEmpty()) {
			rotatedTokens.findById(hash)
					.flatMap(rotated -> sessions.findLockedById(rotated.getSessionId()))
					.ifPresent(session -> revokeReused(session, now));
			throw new ApiException(ErrorCode.INVALID_REFRESH_TOKEN);
		}
		AuthSession session = current.get();
		if (!session.canRefresh(now)) {
			throw new ApiException(ErrorCode.INVALID_REFRESH_TOKEN);
		}
		User user = users.findById(session.getUserId()).orElseThrow();
		if (!user.isActive()) {
			throw new ApiException(ErrorCode.INVALID_REFRESH_TOKEN);
		}
		rotatedTokens.save(new RotatedRefreshToken(hash, session.getSessionId(), now));
		String next = OpaqueTokens.generate();
		session.rotate(OpaqueTokens.hash(next), now.plus(properties.refreshTtl()));
		return tokens(user, session, next);
	}

	/**
	 * Logout: revokes the session only if {@code refreshToken} (current or already rotated) belongs to the caller's
	 * own session. Anything else, including an expired or already revoked session, is silently ignored.
	 */
	@Transactional
	public void logout(UUID userId, UUID sessionId, String refreshToken) {
		String hash = OpaqueTokens.hash(refreshToken);
		Optional<UUID> tokenSession = sessions.findByRefreshTokenHash(hash).map(AuthSession::getSessionId)
				.or(() -> rotatedTokens.findById(hash).map(RotatedRefreshToken::getSessionId));
		if (tokenSession.filter(sessionId::equals).isPresent()) {
			sessions.findLockedById(sessionId)
					.filter(session -> session.getUserId().equals(userId))
					.ifPresent(session -> session.revoke(clock.instant()));
		}
	}

	@Transactional
	public void revoke(UUID sessionId) {
		sessions.findLockedById(sessionId).ifPresent(session -> session.revoke(clock.instant()));
	}

	/** Ends every session of the user, e.g. after a password change or when the user is disabled. */
	@Transactional
	public void revokeAllOf(UUID userId) {
		sessions.revokeAllOfUser(userId, clock.instant());
	}

	private void revokeReused(AuthSession session, Instant now) {
		if (!session.isRevoked()) {
			log.warn("Rotated refresh token reused; revoking session {} of user {}", session.getSessionId(),
					session.getUserId());
			session.revoke(now);
		}
	}

	private IssuedTokens tokens(User user, AuthSession session, String refreshToken) {
		return new IssuedTokens(user.getUserId(), session.getSessionId(),
				accessTokens.issue(user, session.getSessionId()), properties.accessTtl().toSeconds(), refreshToken);
	}

}
