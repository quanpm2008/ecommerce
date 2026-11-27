package com.example.identity_service.controller;

import com.example.identity_service.config.SessionCookieProperties;
import com.example.identity_service.exception.ApiException;
import com.example.identity_service.exception.ErrorCode;
import com.example.identity_service.config.TokenProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * The refresh token's cookie (decision 0001, REQ-IDN-006, SAD §6.3): {@value #NAME}, HttpOnly, Secure (except the
 * {@code local} profile), SameSite=Strict, Path {@code /api/v1/identity/auth}, living as long as the refresh token.
 * Never in a JSON body. The endpoints that read it (refresh, logout) also require an {@code Origin} on the allowlist,
 * the CSRF defence next to SameSite=Strict.
 */
@Component
@RequiredArgsConstructor
class RefreshCookies {

	static final String NAME = "refresh_token";

	private final SessionCookieProperties properties;

	private final TokenProperties tokens;

	ResponseCookie issue(String refreshToken) {
		return build(refreshToken, tokens.refreshTtl());
	}

	/** Same name, path and attributes with no value and {@code Max-Age=0}, so the browser drops it. */
	ResponseCookie clear() {
		return build("", Duration.ZERO);
	}

	/** 403 {@code FORBIDDEN} unless the request comes from an allowed SPA origin. */
	void requireTrustedOrigin(HttpServletRequest request) {
		String origin = request.getHeader("Origin");
		if (origin == null || !properties.allowedOrigins().contains(origin)) {
			throw new ApiException(ErrorCode.FORBIDDEN, "This origin may not use the session cookie");
		}
	}

	private ResponseCookie build(String value, Duration maxAge) {
		return ResponseCookie.from(NAME, value)
				.httpOnly(true)
				.secure(properties.secure())
				.sameSite("Strict")
				.path(AuthController.PATH)
				.maxAge(maxAge)
				.build();
	}

}
