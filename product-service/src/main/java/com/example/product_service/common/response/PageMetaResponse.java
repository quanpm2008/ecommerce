package com.example.product_service.common.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}
