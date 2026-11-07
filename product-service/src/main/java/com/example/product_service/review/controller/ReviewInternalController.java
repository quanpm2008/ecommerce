package com.example.product_service.review.controller;

import com.example.product_service.common.response.ApiResponse;
import com.example.product_service.review.dto.BatchReviewCheckRequest;
import com.example.product_service.review.dto.BatchReviewSummaryDto;
import com.example.product_service.review.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/internal/v1/products/reviews")
@RequiredArgsConstructor
public class ReviewInternalController {

    private final ReviewService reviewService;

    @PostMapping("/summary")
    public ResponseEntity<ApiResponse<List<BatchReviewSummaryDto>>> checkBatchReviews(
            @Valid @RequestBody BatchReviewCheckRequest request) {

        return ResponseEntity.ok(ApiResponse.of(reviewService.checkBatchReviews(request)));
    }
}
