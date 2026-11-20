package com.example.gateway_service.security;

import java.io.IOException;

import com.example.gateway_service.error.ErrorCode;
import com.example.gateway_service.error.ErrorResponse;
import com.example.gateway_service.web.RequestIds;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

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
class AuthenticationErrors implements AuthenticationEntryPoint {

	private static final Logger log = LoggerFactory.getLogger(AuthenticationErrors.class);

	private final JsonMapper json;

	AuthenticationErrors(JsonMapper json) {
		this.json = json;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException failure)
			throws IOException {
		if (failure instanceof AuthenticationServiceException) {
			log.warn("{} {}: cannot check the access token, keys unavailable ({})", request.getMethod(),
					request.getRequestURI(), rootMessage(failure));
			write(request, response, ErrorCode.SERVICE_UNAVAILABLE, ErrorCode.SERVICE_UNAVAILABLE.defaultMessage());
			return;
		}
		if (!(failure instanceof OAuth2AuthenticationException)) {
			response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
			write(request, response, ErrorCode.UNAUTHENTICATED, ErrorCode.UNAUTHENTICATED.defaultMessage());
			return;
		}
		response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"");
		if (onlyExpired(failure)) {
			write(request, response, ErrorCode.TOKEN_EXPIRED, ErrorCode.TOKEN_EXPIRED.defaultMessage());
		}
		else {
			log.debug("{} {}: invalid access token ({})", request.getMethod(), request.getRequestURI(),
					failure.getMessage());
			write(request, response, ErrorCode.UNAUTHENTICATED, "The access token is not valid.");
		}
	}

	private void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code, String message)
			throws IOException {
		response.setStatus(code.status().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		json.writeValue(response.getOutputStream(), ErrorResponse.of(code, message, RequestIds.current(request)));
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
