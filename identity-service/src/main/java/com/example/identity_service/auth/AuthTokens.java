package com.example.identity_service.auth;

import com.example.identity_service.account.UserSelfProfile;
import com.example.identity_service.session.IssuedTokens;

/**
 * API {@code AuthTokens}: the token pair plus the current profile.
 */
public record AuthTokens(String accessToken, long accessTokenExpiresIn, String tokenType, String refreshToken,
		UserSelfProfile profile) {

	static AuthTokens of(IssuedTokens tokens, UserSelfProfile profile) {
		return new AuthTokens(tokens.accessToken(), tokens.accessTokenExpiresIn(), "Bearer", tokens.refreshToken(),
				profile);
	}

}
