package com.example.product_service.category.utils.exception;

import com.example.product_service.category.exception.CategoryInUseException;
import com.example.product_service.category.exception.CategoryNameDuplicateException;
import com.example.product_service.category.utils.dto.ApiErrorResponse;
import com.example.product_service.category.utils.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(value = CategoryNameDuplicateException.class)
    public ResponseEntity<ApiErrorResponse> handleCategoryNameDuplicateException(
            CategoryNameDuplicateException e,
            HttpServletRequest request
    ) {

        ErrorResponse error = ErrorResponse.builder()
                .code("CATEGORY_NAME_DUPLICATE")
                .message(e.getMessage())
                .details(java.util.List.of())
                .requestId(request.getHeader("X-Request-Id"))
                .build();

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(error));
    }

    @ExceptionHandler(value = CategoryInUseException.class)
    public ResponseEntity<ApiErrorResponse> handleCategoryInUseException(
            CategoryInUseException e,
            HttpServletRequest request
    ) {

        ErrorResponse error = ErrorResponse.builder()
                .code("CATEGORY_IN_USE")
                .message(e.getMessage())
                .details(java.util.List.of())
                .requestId(request.getHeader("X-Request-Id"))
                .build();

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(error));
    }
}
