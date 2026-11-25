package com.example.payment_service.exception;

public class RefundRetryNotSafeException extends RuntimeException {
    private final String errorCode;

    public RefundRetryNotSafeException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
