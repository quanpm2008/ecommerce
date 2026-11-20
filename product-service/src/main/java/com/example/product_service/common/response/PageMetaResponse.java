package com.example.product_service.common.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageMetaResponse {

    private int page;

    private int size;

    private Long totalItems;

    private int totalPages;

    public static PageMetaResponse of(int page, int size, Long totalItems, int totalPages) {
        return PageMetaResponse.builder()
                .page(page)
                .size(size)
                .totalItems(totalItems)
                .totalPages(totalPages)
                .build();
    }

    public static PageMetaResponse fromPage(Page<?> page) {
        return PageMetaResponse.builder()
                .page(page.getNumber() + 1) // Spring Data là 0-based, API là 1-based
                .size(page.getSize())
                .totalItems(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }
}
