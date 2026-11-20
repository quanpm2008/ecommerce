package com.example.product_service.product.exception;

public class UnauthorizedProductAccessException extends RuntimeException {
    public UnauthorizedProductAccessException(String message) {
        super(message);
    }
}
