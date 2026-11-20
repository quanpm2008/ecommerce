package com.example.product_service.review.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewSummaryDto {

    private Double ratingAverage;

    private Long ratingCount;

    private Map<String, Long> countByStar;
}
