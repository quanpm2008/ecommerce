package com.example.payment_service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

public record ErrorResponse(ErrorBody error) {

    public record ErrorBody(
            String code,
            String message,
            List<?> details,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            String requestId
    ) {}

    public static ErrorResponse of(String code, String message, List<?> details, String requestId) {
        return new ErrorResponse(new ErrorBody(code, message, details != null ? details : List.of(), requestId));
    }

    public static ErrorResponse of(String code, String message, String requestId) {
        return new ErrorResponse(new ErrorBody(code, message, List.of(), requestId));
    }
}
