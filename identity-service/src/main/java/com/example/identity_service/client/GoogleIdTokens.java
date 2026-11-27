package com.example.identity_service.client;

/**
 * Verifies a Google ID token from Google Identity Services (API §6.1 {@code POST /auth/google}). An interface because
 * tests replace Google.
 */
public interface GoogleIdTokens {

	/**
	 * The verified identity: {@code subject} is Google's stable account id ({@code sub}), {@code email} is verified by
	 * Google, {@code name} may be null.
	 *
	 * @throws com.example.identity_service.exception.ApiException {@code GOOGLE_AUTH_FAILED} when the token is not a
	 * valid, unexpired token for this app with a verified email; {@code SERVICE_UNAVAILABLE} when Google sign-in is
	 * not configured or Google's keys cannot be fetched
	 */
	GoogleIdentity verify(String idToken);

	record GoogleIdentity(String subject, String email, String name) {
	}

}
