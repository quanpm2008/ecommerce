package com.example.payment_service.exception;

public class IdempotencyConflictException extends RuntimeException {
    private final String errorCode;

    public IdempotencyConflictException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
