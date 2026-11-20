package com.example.product_service.common.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiPageResponse<T> {

    private List<T> data;

    private PageMetaResponse meta;

    public static <T> ApiPageResponse<T> of(List<T> data, PageMetaResponse meta) {
        return ApiPageResponse.<T>builder()
                .data(data)
                .meta(meta)
                .build();
    }
}
