package com.example.payment_service.exception;

public class PaymentConflictException extends RuntimeException {
    private final String errorCode;

    public PaymentConflictException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
