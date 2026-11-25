package com.example.payment_service.dto;

public record PageMeta(
        int page,
        int size,
        long totalItems,
        int totalPages
) {
    public static PageMeta of(int page, int size, long totalItems, int totalPages) {
        return new PageMeta(page, size, totalItems, totalPages);
    }
}
