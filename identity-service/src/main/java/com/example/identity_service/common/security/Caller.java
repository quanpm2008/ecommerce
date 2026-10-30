package com.example.identity_service.common.security;

import com.example.identity_service.session.TokenClaims;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/**
 * The authenticated user and session behind a request, taken from the verified access token. Owner ids always come
 * from here, never from the request body, query or headers.
 */
public record Caller(UUID userId, UUID sessionId) {

	public static Caller of(Jwt jwt) {
		return new Caller(UUID.fromString(jwt.getSubject()),
				UUID.fromString(jwt.getClaimAsString(TokenClaims.SESSION_ID)));
	}

}
