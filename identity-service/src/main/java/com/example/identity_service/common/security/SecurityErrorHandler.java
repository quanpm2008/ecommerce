package com.example.identity_service.common.security;

import com.example.identity_service.common.error.ErrorCode;
import com.example.identity_service.common.error.ErrorResponse;
import com.example.identity_service.common.web.RequestIdFilter;
import com.example.identity_service.session.AccessTokenDecoderConfig;
import com.example.identity_service.session.SessionTokenValidator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.List;

/**
 * Writes Spring Security's 401/403 responses in the API §3.3 error envelope; these never reach
 * {@code GlobalExceptionHandler}. Expired and stale tokens get their own codes so the client knows to refresh.
 */
@Component
@RequiredArgsConstructor
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

	private final JsonMapper jsonMapper;

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
			throws IOException {
		write(request, response, codeFor(ex));
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
			throws IOException {
		write(request, response, ErrorCode.FORBIDDEN);
	}

	private static ErrorCode codeFor(AuthenticationException ex) {
		if (ex.getCause() instanceof JwtValidationException invalid) {
			List<String> codes = invalid.getErrors().stream().map(OAuth2Error::getErrorCode).toList();
			if (codes.contains(AccessTokenDecoderConfig.TOKEN_EXPIRED)) {
				return ErrorCode.TOKEN_EXPIRED;
			}
			if (codes.contains(SessionTokenValidator.TOKEN_STALE)) {
				return ErrorCode.TOKEN_STALE;
			}
		}
		return ErrorCode.UNAUTHENTICATED;
	}

	private void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code) throws IOException {
		Object requestId = request.getAttribute(RequestIdFilter.ATTRIBUTE);
		response.setStatus(code.status().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		jsonMapper.writeValue(response.getOutputStream(), ErrorResponse.of(code, code.defaultMessage(), List.of(),
				requestId != null ? requestId.toString() : null));
	}

}
