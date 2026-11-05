package com.example.identity_service.common.error;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * Error envelope (API §3.3): {@code { "error": { code, message, details, requestId } }}.
 * {@code details} entries vary by code (field/issue, resource, currentStatus, ...), so they are free-form maps.
 */
public record ErrorResponse(Body error) {

	@Schema(name = "ErrorBody")
	public record Body(@Schema(description = "Stable machine code (API §4.1)", example = "VALIDATION_ERROR") String code,
			@Schema(description = "Human-readable message in English") String message,
			@Schema(description = "Per-code details, e.g. { field, issue, message } or { currentStatus }")
			List<Map<String, Object>> details,
			@Schema(description = "The request's X-Request-Id, or null when none was sent") String requestId) {
	}

	public static ErrorResponse of(ErrorCode code, String message, List<Map<String, Object>> details, String requestId) {
		return new ErrorResponse(new Body(code.name(), message, details, requestId));
	}

}
