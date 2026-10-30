package com.example.identity_service.session;

/**
 * Custom access token claims (API §3.2). Standard ones ({@code sub}, {@code iss}, {@code aud}, {@code iat},
 * {@code exp}, {@code jti}) use their registered names.
 */
public final class TokenClaims {

	public static final String SESSION_ID = "sessionId";

	public static final String AUTHZ_VERSION = "authzVersion";

	private TokenClaims() {
	}

}
