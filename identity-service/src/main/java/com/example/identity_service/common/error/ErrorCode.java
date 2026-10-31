package com.example.identity_service.common.error;

import org.springframework.http.HttpStatus;

/**
 * Error codes from API §4.1. Only codes listed there may be returned; add feature codes here as they are implemented.
 */
public enum ErrorCode {

	VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Invalid request data"),
	UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Not logged in or token is invalid"),
	TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Access token has expired"),
	TOKEN_STALE(HttpStatus.UNAUTHORIZED, "Access token permissions are out of date; refresh the token"),
	INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Refresh token is invalid, expired or revoked"),
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Email or password is incorrect"),
	ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "This account is disabled"),
	EMAIL_ALREADY_USED(HttpStatus.CONFLICT, "Email is already used by another account"),
	PHONE_ALREADY_USED(HttpStatus.CONFLICT, "Phone number is already used"),
	SELLER_NAME_ALREADY_USED(HttpStatus.CONFLICT, "Seller name is already used"),
	WRONG_AUTH_METHOD(HttpStatus.CONFLICT, "This email is registered with a different sign-in method"),
	PASSWORD_POLICY_VIOLATION(HttpStatus.BAD_REQUEST,
			"Password must have at least 8 characters with an uppercase letter, a lowercase letter and a digit"),
	PASSWORD_INCORRECT(HttpStatus.BAD_REQUEST, "Current password is incorrect"),
	PASSWORD_SAME_AS_OLD(HttpStatus.BAD_REQUEST, "New password must differ from the current one"),
	RESET_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "Password reset link is invalid, used or expired"),
	ADDRESS_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_CONTENT, "The address book is full"),
	INVALID_REGION(HttpStatus.UNPROCESSABLE_CONTENT, "The ward does not belong to the selected province"),
	MEDIA_INVALID(HttpStatus.UNPROCESSABLE_CONTENT,
			"The file has a wrong type or size, does not exist or does not belong to you"),
	REGION_SOURCE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "The administrative region source is not available"),
	FORBIDDEN(HttpStatus.FORBIDDEN, "You do not have permission to perform this action"),
	NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
	INVALID_STATE_TRANSITION(HttpStatus.CONFLICT, "The current state does not allow this action"),
	RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error"),
	SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "A dependent service is not responding, please try again");

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

}
