package com.example.gateway_service.security;

import com.example.gateway_service.error.ErrorCode;
import com.example.gateway_service.error.ErrorWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Writes the gateway's authentication failures in the API §3.3 envelope (never logging the token):
 * <ul>
 * <li>no token on a protected route → 401 {@code UNAUTHENTICATED}, {@code WWW-Authenticate: Bearer}</li>
 * <li>expired token (and nothing else wrong) → 401 {@code TOKEN_EXPIRED}, so the client refreshes</li>
 * <li>any other bad token or {@code Authorization} header → 401 {@code UNAUTHENTICATED}</li>
 * <li>the token can't be checked because identity-service's JWKS can't be fetched → 503 {@code SERVICE_UNAVAILABLE},
 * never let through</li>
 * </ul>
 */
@Component
class AuthenticationErrors implements ServerAuthenticationEntryPoint {

	private static final Logger log = LoggerFactory.getLogger(AuthenticationErrors.class);

	private final ErrorWriter errors;

	AuthenticationErrors(ErrorWriter errors) {
		this.errors = errors;
	}

	@Override
	public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException failure) {
		ServerHttpRequest request = exchange.getRequest();
		HttpHeaders headers = exchange.getResponse().getHeaders();
		if (failure instanceof AuthenticationServiceException) {
			log.warn("{} {}: cannot check the access token, keys unavailable ({})", request.getMethod(),
					request.getPath(), rootMessage(failure));
			return errors.write(exchange, ErrorCode.SERVICE_UNAVAILABLE);
		}
		if (!(failure instanceof OAuth2AuthenticationException)) {
			headers.set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
			return errors.write(exchange, ErrorCode.UNAUTHENTICATED);
		}
		headers.set(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"");
		if (onlyExpired(failure)) {
			return errors.write(exchange, ErrorCode.TOKEN_EXPIRED);
		}
		log.debug("{} {}: invalid access token ({})", request.getMethod(), request.getPath(), failure.getMessage());
		return errors.write(exchange, ErrorCode.UNAUTHENTICATED, "The access token is not valid.");
	}

	private static boolean onlyExpired(Throwable failure) {
		for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
			if (cause instanceof JwtValidationException invalid) {
				return !invalid.getErrors().isEmpty() && invalid.getErrors().stream()
						.allMatch(error -> ErrorCode.TOKEN_EXPIRED.name().equals(error.getErrorCode()));
			}
		}
		return false;
	}

	private static String rootMessage(Throwable failure) {
		Throwable root = failure;
		while (root.getCause() != null) {
			root = root.getCause();
		}
		return root.toString();
	}

}
