package com.example.identity_service.dto;

import com.example.identity_service.entity.AccessMode;
import com.example.identity_service.entity.AccountType;

/**
 * API {@code AuthTokens} without the refresh token: that one travels only in the HttpOnly cookie (decision 0001,
 * REQ-IDN-006). The access token stays in the client's memory.
 */
public record AuthTokens(String accessToken, long accessTokenExpiresIn, String tokenType, AccountType accountType,
		AccessMode accessMode, UserSelfProfile profile) {

	/** A session's tokens: the body above and the refresh token for the cookie. */
	public record Issued(AuthTokens body, String refreshToken) {
	}

	public static Issued of(IssuedTokens tokens, UserSelfProfile profile) {
		return new Issued(new AuthTokens(tokens.accessToken(), tokens.accessTokenExpiresIn(), "Bearer",
				profile.accountType(), profile.accessMode(), profile), tokens.refreshToken());
	}

}
