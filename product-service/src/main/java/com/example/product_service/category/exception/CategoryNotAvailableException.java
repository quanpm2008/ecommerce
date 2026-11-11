package com.example.product_service.category.exception;

public class CategoryNotAvailableException extends RuntimeException {
    public CategoryNotAvailableException(String message){
        super(message);
    }

}
