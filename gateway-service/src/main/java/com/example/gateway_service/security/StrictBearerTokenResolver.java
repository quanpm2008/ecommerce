package com.example.gateway_service.security;

import java.util.Collections;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.BearerTokenErrors;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

/**
 * Reads the access token from exactly one {@code Authorization: Bearer <token>} header (API §3.2). Any other
 * {@code Authorization} header (another scheme, {@code Bearer} without a token, a malformed token, the header sent
 * twice) is an invalid token rather than "no token", so it is a 401 even on a public endpoint and never forwarded.
 * Tokens in the query string or form body are not accepted.
 */
final class StrictBearerTokenResolver implements BearerTokenResolver {

	private static final String BEARER = "Bearer ";

	private final DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();

	@Override
	public String resolve(HttpServletRequest request) {
		List<String> values = Collections.list(request.getHeaders(HttpHeaders.AUTHORIZATION));
		if (values.isEmpty()) {
			return null;
		}
		if (values.size() > 1) {
			throw invalid("Send a single Authorization header");
		}
		if (!values.getFirst().regionMatches(true, 0, BEARER, 0, BEARER.length())) {
			throw invalid("The Authorization header must be a Bearer token");
		}
		return delegate.resolve(request);
	}

	private static OAuth2AuthenticationException invalid(String description) {
		return new OAuth2AuthenticationException(BearerTokenErrors.invalidToken(description));
	}

}
