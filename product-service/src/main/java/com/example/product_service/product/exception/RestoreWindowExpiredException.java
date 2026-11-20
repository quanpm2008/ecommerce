package com.example.product_service.product.exception;

public class RestoreWindowExpiredException extends RuntimeException {
    public RestoreWindowExpiredException(String message) {
        super(message);
    }
}
