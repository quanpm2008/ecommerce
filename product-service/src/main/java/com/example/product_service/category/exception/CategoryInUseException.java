package com.example.product_service.category.exception;

public class CategoryInUseException extends RuntimeException{
    public CategoryInUseException(String message){
        super(message);
    }

}
