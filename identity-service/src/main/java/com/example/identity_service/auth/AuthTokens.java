package com.example.identity_service.auth;

import com.example.identity_service.account.UserSelfProfile;
import com.example.identity_service.session.IssuedTokens;
import com.example.identity_service.user.AccessMode;
import com.example.identity_service.user.AccountType;

/**
 * API {@code AuthTokens} without the refresh token: that one travels only in the HttpOnly cookie (decision 0001,
 * REQ-IDN-006). The access token stays in the client's memory.
 */
public record AuthTokens(String accessToken, long accessTokenExpiresIn, String tokenType, AccountType accountType,
		AccessMode accessMode, UserSelfProfile profile) {

	/** A session's tokens: the body above and the refresh token for the cookie. */
	record Issued(AuthTokens body, String refreshToken) {
	}

	static Issued of(IssuedTokens tokens, UserSelfProfile profile) {
		return new Issued(new AuthTokens(tokens.accessToken(), tokens.accessTokenExpiresIn(), "Bearer",
				profile.accountType(), profile.accessMode(), profile), tokens.refreshToken());
	}

}
