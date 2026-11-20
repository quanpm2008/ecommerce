package com.example.product_service.review.controller;

import com.example.product_service.common.response.ApiPageWithSummaryResponse;
import com.example.product_service.review.dto.ReviewResponse;
import com.example.product_service.review.dto.ReviewSummaryDto;
import com.example.product_service.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "3. Reviews - Public", description = "Xem đánh giá và thống kê số sao của sản phẩm")
@RestController
@RequestMapping("/products/{productId}/reviews")
@RequiredArgsConstructor
public class ReviewPublicController {

    private final ReviewService reviewService;

    @Operation(summary = "Xem danh sách đánh giá của sản phẩm (lọc theo rating sao, phân trang, kèm summary trung bình sao & countByStar)")
    @GetMapping
    public ResponseEntity<ApiPageWithSummaryResponse<ReviewResponse, ReviewSummaryDto>> getProductReviews(
            @PathVariable UUID productId,
            @RequestParam(required = false) Integer rating,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader(value = "X-User-Id", required = false) UUID currentUserId) {

        int zeroBasedPage = Math.max(page - 1, 0);

        Pageable pageable = PageRequest.of(zeroBasedPage, size, Sort.by("createdAt").descending());

        return ResponseEntity.ok(reviewService.getProductReviews(productId, rating, pageable, currentUserId));
    }
}
