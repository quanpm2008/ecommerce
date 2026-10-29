package com.example.identity_service.common.error;

import java.util.List;
import java.util.Map;

/**
 * Error envelope (API §3.3): {@code { "error": { code, message, details, requestId } }}.
 * {@code details} entries vary by code (field/issue, resource, currentStatus, ...), so they are free-form maps.
 */
public record ErrorResponse(Body error) {

	public record Body(String code, String message, List<Map<String, Object>> details, String requestId) {
	}

	public static ErrorResponse of(ErrorCode code, String message, List<Map<String, Object>> details, String requestId) {
		return new ErrorResponse(new Body(code.name(), message, details, requestId));
	}

}
