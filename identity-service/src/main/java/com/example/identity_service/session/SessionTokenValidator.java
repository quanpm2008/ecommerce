package com.example.identity_service.session;

import com.example.identity_service.user.User;
import com.example.identity_service.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Checks a signed access token against current state on every request (no caching, API §2.3): the session exists,
 * belongs to {@code sub} and is not revoked; the user is ACTIVE; and {@code authzVersion} is current. An older
 * version fails with {@link #TOKEN_STALE} so the client knows to refresh.
 */
@Component
@RequiredArgsConstructor
public class SessionTokenValidator implements OAuth2TokenValidator<Jwt> {

	public static final String TOKEN_STALE = "TOKEN_STALE";

	private final AuthSessionRepository sessions;

	private final UserRepository users;

	@Override
	public OAuth2TokenValidatorResult validate(Jwt jwt) {
		Optional<UUID> userId = uuid(jwt.getSubject());
		Optional<UUID> sessionId = uuid(jwt.getClaimAsString(TokenClaims.SESSION_ID));
		Object version = jwt.getClaims().get(TokenClaims.AUTHZ_VERSION);
		if (userId.isEmpty() || sessionId.isEmpty() || !(version instanceof Number authzVersion)) {
			return invalid("Token is missing sub, sessionId or authzVersion");
		}
		Optional<AuthSession> session = sessions.findById(sessionId.get());
		if (session.isEmpty() || !session.get().getUserId().equals(userId.get()) || session.get().isRevoked()) {
			return invalid("Session is not active");
		}
		Optional<User> user = users.findById(userId.get()).filter(User::isActive);
		if (user.isEmpty()) {
			return invalid("User is not active");
		}
		long current = user.get().getAuthzVersion();
		if (authzVersion.longValue() < current) {
			return OAuth2TokenValidatorResult.failure(new OAuth2Error(TOKEN_STALE, "authzVersion is out of date", null));
		}
		if (authzVersion.longValue() > current) {
			return invalid("authzVersion is newer than the user's");
		}
		return OAuth2TokenValidatorResult.success();
	}

	private static OAuth2TokenValidatorResult invalid(String description) {
		return OAuth2TokenValidatorResult.failure(new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, description, null));
	}

	private static Optional<UUID> uuid(String value) {
		try {
			return Optional.ofNullable(value).map(UUID::fromString);
		}
		catch (IllegalArgumentException ex) {
			return Optional.empty();
		}
	}

}
