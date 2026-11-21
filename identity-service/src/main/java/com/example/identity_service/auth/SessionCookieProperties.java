package com.example.identity_service.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * {@code identity.session-cookie.*} (decision 0001, REQ-IDN-006): the refresh token travels only in an HttpOnly cookie.
 * {@code allowedOrigins} are the SPA's exact origins allowed to use it (refresh, logout); {@code secure} is off only in
 * the explicit {@code local} profile.
 */
@ConfigurationProperties("identity.session-cookie")
public record SessionCookieProperties(List<String> allowedOrigins, boolean secure) {

	public SessionCookieProperties {
		allowedOrigins = allowedOrigins == null ? List.of()
				: allowedOrigins.stream().map(String::strip).filter(origin -> !origin.isEmpty()).toList();
	}

}
