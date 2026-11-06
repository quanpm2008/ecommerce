package com.example.product_service.category.exception;

public class CategoryNameDuplicateException extends RuntimeException{
    public CategoryNameDuplicateException(String message){
        super(message);
    }

}
