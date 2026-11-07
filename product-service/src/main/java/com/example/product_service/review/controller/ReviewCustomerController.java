package com.example.product_service.review.controller;

import com.example.product_service.common.response.ApiResponse;
import com.example.product_service.review.dto.CreateReviewRequest;
import com.example.product_service.review.dto.ReviewResponse;
import com.example.product_service.review.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/products/{productId}/reviews")
@RequiredArgsConstructor
public class ReviewCustomerController {

    private final ReviewService reviewService;

    @PostMapping
    public ResponseEntity<ApiResponse<ReviewResponse>> createReview(
            @RequestHeader("X-User-Id") UUID customerId,
            @PathVariable UUID productId,
            @Valid @RequestBody CreateReviewRequest request) {
        ReviewResponse response = reviewService.createReview(customerId, productId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response));
    }
}
