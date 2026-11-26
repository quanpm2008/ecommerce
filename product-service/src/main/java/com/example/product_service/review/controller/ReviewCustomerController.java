package com.example.product_service.review.controller;

import com.example.product_service.client.identity.IdentityClientAdapter;
import com.example.product_service.common.response.ApiResponse;
import com.example.product_service.review.dto.CreateReviewRequest;
import com.example.product_service.review.dto.ReviewResponse;
import com.example.product_service.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "5. Reviews - Customer", description = "Tạo đánh giá sản phẩm dành cho khách hàng đã mua")
@RestController
@RequestMapping("/products/{productId}/reviews")
@RequiredArgsConstructor
public class ReviewCustomerController {

    private final ReviewService reviewService;
    private final IdentityClientAdapter identityClientAdapter;

    @Operation(summary = "Tạo đánh giá sản phẩm (xác minh điều kiện mua hàng từ Order Service)")
    @PostMapping
    public ResponseEntity<ApiResponse<ReviewResponse>> createReview(
            @PathVariable UUID productId,
            @Valid @RequestBody CreateReviewRequest request) {
        UUID customerId = identityClientAdapter.requireCustomerForReview();
        ReviewResponse response = reviewService.createReview(customerId, productId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response));
    }
}
