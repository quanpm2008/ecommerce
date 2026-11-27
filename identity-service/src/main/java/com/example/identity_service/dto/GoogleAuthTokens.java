package com.example.identity_service.dto;

import com.example.identity_service.entity.AccessMode;
import com.example.identity_service.entity.AccountType;

/**
 * {@code POST /auth/google} response (API §6.1): {@link AuthTokens} plus {@code isNewAccount}, true when this sign-in
 * created the customer account. The refresh token is only in the cookie.
 */
public record GoogleAuthTokens(String accessToken, long accessTokenExpiresIn, String tokenType, AccountType accountType,
		AccessMode accessMode, UserSelfProfile profile, boolean isNewAccount) {

	/** A Google session's tokens: the body above and the refresh token for the cookie. */
	public record Issued(GoogleAuthTokens body, String refreshToken) {
	}

	public static Issued of(AuthTokens.Issued issued, boolean isNewAccount) {
		AuthTokens t = issued.body();
		return new Issued(new GoogleAuthTokens(t.accessToken(), t.accessTokenExpiresIn(), t.tokenType(),
				t.accountType(), t.accessMode(), t.profile(), isNewAccount), issued.refreshToken());
	}

}
