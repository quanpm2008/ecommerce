package com.example.notification_service.common.error;
import org.springframework.http.HttpStatus;
public enum ErrorCode {
 VALIDATION_ERROR(HttpStatus.BAD_REQUEST,"Invalid request data"),
 UNAUTHENTICATED(HttpStatus.UNAUTHORIZED,"Not logged in or token is invalid"),
 TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED,"Access token has expired"),
 TOKEN_STALE(HttpStatus.UNAUTHORIZED,"Refresh the access token"),
 FORBIDDEN(HttpStatus.FORBIDDEN,"Permission denied"),
 ACCOUNT_BANNED(HttpStatus.FORBIDDEN,"Action not allowed for this banned account"),
 ACCOUNT_DISABLED(HttpStatus.FORBIDDEN,"Account is disabled"),
 NOT_FOUND(HttpStatus.NOT_FOUND,"Resource not found"),
 IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT,"Idempotency key was already used with different data"),
 NOTIFICATION_TYPE_NOT_ALLOWED(HttpStatus.FORBIDDEN,"Notification type is not allowed for this producer"),
 SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE,"A dependent service is unavailable"),
 INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR,"Unexpected error");
 private final HttpStatus status;
 private final String message;
 ErrorCode(HttpStatus status,String message){this.status=status;this.message=message;}
 public HttpStatus status(){return status;}
 public String defaultMessage(){return message;}
}
