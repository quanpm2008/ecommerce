package com.example.identity_service.service;

import com.example.identity_service.config.TokenProperties;
import com.example.identity_service.dto.IssuedTokens;
import com.example.identity_service.entity.RotatedRefreshToken;
import com.example.identity_service.repository.RotatedRefreshTokenRepository;
import com.example.identity_service.security.AccessTokenIssuer;
import com.example.identity_service.exception.ApiException;
import com.example.identity_service.security.OpaqueTokens;
import com.example.identity_service.exception.ErrorCode;
import com.example.identity_service.entity.User;
import com.example.identity_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The one refresh session of each user (decision 0003, REQ-IDN-009), stored on {@link User}: login replaces it,
 * refresh rotates its token, logout/password change/DISABLED end it. Every change locks the user row first, so a late
 * refresh or logout of an old session cannot overwrite a newer login. Only hashes are stored; rotated tokens are kept
 * as marks to detect reuse.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService {

	private final UserRepository users;

	private final RotatedRefreshTokenRepository rotatedTokens;

	private final AccessTokenIssuer accessTokens;

	private final TokenProperties properties;

	private final Clock clock;

	/**
	 * Starts a new session for a user whose credentials were already checked, replacing the current one: its refresh
	 * token stops working and access tokens with its {@code sessionId} are rejected on their next check.
	 */
	@Transactional
	public IssuedTokens start(User account) {
		User user = users.findLockedById(account.getUserId()).orElseThrow();
		if (!user.isActive()) {
			throw new IllegalStateException("Cannot start a session for a disabled user");
		}
		String refreshToken = OpaqueTokens.generate();
		UUID sessionId = user.startSession(OpaqueTokens.hash(refreshToken),
				clock.instant().plus(properties.refreshTtl()));
		rotatedTokens.deleteOtherSessionsOf(user.getUserId(), sessionId);
		return tokens(user, refreshToken);
	}

	/**
	 * Exchanges the current refresh token for a new one in the same session; the expiry moves to now + refresh TTL.
	 * Unknown, expired or replaced tokens and disabled users all give {@code INVALID_REFRESH_TOKEN}. A rotated token
	 * of the user's current session means the chain was copied: the session ends (committed, {@code noRollbackFor})
	 * and the answer is the same 401. {@code signIn} checks the account may still get a session before rotating.
	 */
	@Transactional(noRollbackFor = ApiException.class)
	public IssuedTokens refresh(String refreshToken, Consumer<User> signIn) {
		Instant now = clock.instant();
		String hash = OpaqueTokens.hash(refreshToken);
		Optional<User> current = users.findByRefreshTokenHash(hash);
		if (current.isEmpty()) {
			rotatedTokens.findById(hash).ifPresent(this::revokeIfReused);
			throw new ApiException(ErrorCode.INVALID_REFRESH_TOKEN);
		}
		User user = current.get();
		if (!user.canRefresh(now) || !user.isActive()) {
			throw new ApiException(ErrorCode.INVALID_REFRESH_TOKEN);
		}
		signIn.accept(user);
		rotatedTokens.save(new RotatedRefreshToken(hash, user.getUserId(), user.getSessionId(), now));
		String next = OpaqueTokens.generate();
		user.rotateRefreshToken(OpaqueTokens.hash(next), now.plus(properties.refreshTtl()));
		return tokens(user, next);
	}

	/** The account a current (not rotated) refresh token belongs to, if any; changes nothing. */
	@Transactional(readOnly = true)
	public Optional<UUID> ownerOf(String refreshToken) {
		return users.findUserIdByRefreshTokenHash(OpaqueTokens.hash(refreshToken));
	}

	/**
	 * Logout: ends the session only when the Bearer's {@code sessionId} is the user's current session and
	 * {@code refreshToken} is that session's current token or one of its rotated ones. Anything else (an old cookie,
	 * someone else's token, a session already replaced) changes nothing.
	 */
	@Transactional
	public void logout(UUID userId, UUID sessionId, String refreshToken) {
		String hash = OpaqueTokens.hash(refreshToken);
		users.findLockedById(userId).filter(user -> user.hasSession(sessionId)).ifPresent(user -> {
			boolean ofThisSession = hash.equals(user.getRefreshTokenHash()) || rotatedTokens.findById(hash)
					.filter(mark -> mark.getUserId().equals(userId) && mark.getSessionId().equals(sessionId))
					.isPresent();
			if (ofThisSession) {
				end(user);
			}
		});
	}

	/** Ends the user's session, e.g. after a password change or when the user is disabled. */
	@Transactional
	public void revokeAllOf(UUID userId) {
		users.findLockedById(userId).ifPresent(this::end);
	}

	private void revokeIfReused(RotatedRefreshToken mark) {
		users.findLockedById(mark.getUserId()).filter(user -> user.hasSession(mark.getSessionId())).ifPresent(user -> {
			log.warn("Rotated refresh token reused; ending session {} of user {}", mark.getSessionId(),
					user.getUserId());
			end(user);
		});
	}

	private void end(User user) {
		user.endSession();
		rotatedTokens.deleteAllOf(user.getUserId());
	}

	private IssuedTokens tokens(User user, String refreshToken) {
		return new IssuedTokens(user.getUserId(), user.getSessionId(), accessTokens.issue(user, user.getSessionId()),
				properties.accessTtl().toSeconds(), refreshToken);
	}

}
