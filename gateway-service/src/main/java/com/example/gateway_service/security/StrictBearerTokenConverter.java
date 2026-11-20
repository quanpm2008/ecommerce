package com.example.gateway_service.security;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.BearerTokenErrors;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Reads the access token from exactly one {@code Authorization: Bearer <token>} header (API §3.2). Any other
 * {@code Authorization} header (another scheme, {@code Bearer} without a token, a malformed token, the header sent
 * twice) is an invalid token rather than "no token", so it is a 401 even on a public endpoint and never forwarded.
 * Tokens in the query string or form body are not accepted.
 */
final class StrictBearerTokenConverter implements ServerAuthenticationConverter {

	private static final String BEARER = "Bearer ";

	private final ServerBearerTokenAuthenticationConverter delegate = new ServerBearerTokenAuthenticationConverter();

	@Override
	public Mono<Authentication> convert(ServerWebExchange exchange) {
		List<String> values = exchange.getRequest().getHeaders().getOrEmpty(HttpHeaders.AUTHORIZATION);
		if (values.isEmpty()) {
			return Mono.empty();
		}
		if (values.size() > 1) {
			return Mono.error(invalid("Send a single Authorization header"));
		}
		if (!values.getFirst().regionMatches(true, 0, BEARER, 0, BEARER.length())) {
			return Mono.error(invalid("The Authorization header must be a Bearer token"));
		}
		return delegate.convert(exchange);
	}

	private static OAuth2AuthenticationException invalid(String description) {
		return new OAuth2AuthenticationException(BearerTokenErrors.invalidToken(description));
	}

}
