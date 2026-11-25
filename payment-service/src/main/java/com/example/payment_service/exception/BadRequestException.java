package com.example.payment_service.exception;

public class BadRequestException extends RuntimeException {
    private final String errorCode;

    public BadRequestException(String message) {
        super(message);
        this.errorCode = "VALIDATION_ERROR";
    }

    public BadRequestException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
