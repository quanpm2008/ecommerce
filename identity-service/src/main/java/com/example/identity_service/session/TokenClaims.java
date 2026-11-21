package com.example.identity_service.session;

/**
 * Custom access token claims (API §3.2). Standard ones ({@code sub}, {@code iss}, {@code aud}, {@code iat},
 * {@code exp}, {@code jti}) use their registered names.
 */
public final class TokenClaims {

	public static final String SESSION_ID = "sessionId";

	public static final String AUTHZ_VERSION = "authzVersion";

	/** The account type ({@code CUSTOMER}, {@code SELLER}, {@code ADMIN}), for the UI only. */
	public static final String ACCOUNT_TYPE = "accountType";

	/** Snapshot of the account's one role, absent when it has none yet; for the UI only, never trusted for access. */
	public static final String ROLE = "role";

	private TokenClaims() {
	}

}
