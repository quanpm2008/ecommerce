package com.example.gateway_service.error;

import java.util.List;
import java.util.Map;

/** Error envelope (API §3.3): {@code { "error": { code, message, details, requestId } }}, as identity-service writes it. */
public record ErrorResponse(Body error) {

	public record Body(String code, String message, List<Map<String, Object>> details, String requestId) {
	}

	public static ErrorResponse of(ErrorCode code, String message, String requestId) {
		return new ErrorResponse(new Body(code.name(), message, List.of(), requestId));
	}

}
