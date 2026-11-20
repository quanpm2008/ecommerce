package com.example.product_service.product.exception;

public class ProductRemovedByAdminException extends RuntimeException {
    public ProductRemovedByAdminException(String message) {
        super(message);
    }
}
