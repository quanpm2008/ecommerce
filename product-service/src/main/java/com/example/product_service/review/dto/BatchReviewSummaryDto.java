package com.example.product_service.review.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchReviewSummaryDto {

    private UUID productId;

    private UUID reviewId;

    private Boolean hasReviewed;
}
