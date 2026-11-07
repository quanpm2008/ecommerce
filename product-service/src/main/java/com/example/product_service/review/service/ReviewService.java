package com.example.product_service.review.service;

import com.example.product_service.common.response.ApiPageWithSummaryResponse;
import com.example.product_service.review.dto.*;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface ReviewService {

    ReviewResponse createReview(UUID customerId, UUID productId, CreateReviewRequest request);

    ApiPageWithSummaryResponse<ReviewResponse, ReviewSummaryDto> getProductReviews(
            UUID productId, Integer star, Pageable pageable, UUID currentUserId);

    ReviewSummaryDto getProductReviewSummary(UUID productId);

    List<BatchReviewSummaryDto> checkBatchReviews(BatchReviewCheckRequest request);
}
