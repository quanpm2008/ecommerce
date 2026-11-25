package com.example.payment_service.exception;

public class RefundLimitExceededException extends RuntimeException {
    private final String errorCode;

    public RefundLimitExceededException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
