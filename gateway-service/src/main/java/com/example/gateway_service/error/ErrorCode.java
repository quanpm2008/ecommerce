package com.example.gateway_service.error;

import org.springframework.http.HttpStatus;

/** The API §4.1 codes the gateway itself can answer with. Downstream services' codes pass through untouched. */
public enum ErrorCode {

	VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "The request is not valid."),
	UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication is required."),
	TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "The access token has expired. Refresh it and try again."),
	FORBIDDEN(HttpStatus.FORBIDDEN, "Access is denied."),
	NOT_FOUND(HttpStatus.NOT_FOUND, "No resource exists at this path."),
	RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Try again later."),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred."),
	SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "The service is temporarily unavailable. Try again later.");

	private final HttpStatus status;

	private final String defaultMessage;

	ErrorCode(HttpStatus status, String defaultMessage) {
		this.status = status;
		this.defaultMessage = defaultMessage;
	}

	public HttpStatus status() {
		return status;
	}

	public String defaultMessage() {
		return defaultMessage;
	}

	/** The code for an error status the gateway produced: exact matches first, else the generic 4xx / 5xx code. */
	public static ErrorCode forStatus(int status) {
		for (ErrorCode code : values()) {
			if (code.status.value() == status) {
				return code;
			}
		}
		return status < 500 ? VALIDATION_ERROR : INTERNAL_ERROR;
	}

}
