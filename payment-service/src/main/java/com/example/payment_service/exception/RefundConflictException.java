package com.example.payment_service.exception;

public class RefundConflictException extends RuntimeException {
    private final String errorCode;

    public RefundConflictException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
