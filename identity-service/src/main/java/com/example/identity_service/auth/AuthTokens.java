package com.example.identity_service.auth;

import com.example.identity_service.account.UserSelfProfile;
import com.example.identity_service.session.IssuedTokens;
import com.example.identity_service.user.AccountType;

/**
 * API {@code AuthTokens}: the token pair, the account type of the session and the current profile.
 */
public record AuthTokens(String accessToken, long accessTokenExpiresIn, String tokenType, String refreshToken,
		AccountType accountType, UserSelfProfile profile) {

	static AuthTokens of(IssuedTokens tokens, UserSelfProfile profile) {
		return new AuthTokens(tokens.accessToken(), tokens.accessTokenExpiresIn(), "Bearer", tokens.refreshToken(),
				profile.accountType(), profile);
	}

}
