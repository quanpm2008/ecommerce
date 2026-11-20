package com.example.product_service.common.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiPageWithSummaryResponse <T, S> {

    private List<T> data;

    private PageMetaResponse meta;

    private S summary;

    public static <T, S> ApiPageWithSummaryResponse<T, S> of(List<T> data, Page<?> page, S summary) {
        return ApiPageWithSummaryResponse.<T, S>builder()
                .data(data)
                .meta(PageMetaResponse.fromPage(page))
                .summary(summary)
                .build();
    }

    public static <T, S> ApiPageWithSummaryResponse<T, S> of(List<T> data, PageMetaResponse meta, S summary) {
        return ApiPageWithSummaryResponse.<T, S>builder()
                .data(data)
                .meta(meta)
                .summary(summary)
                .build();
    }
}
