package com.example.identity_service.session;

import java.util.UUID;

/**
 * Result of a login or refresh. {@code accessTokenExpiresIn} is in seconds, as in the API's {@code AuthTokens}.
 */
public record IssuedTokens(UUID userId, UUID sessionId, String accessToken, long accessTokenExpiresIn,
		String refreshToken) {
}
