package com.example.product_service.review.controller;

import com.example.product_service.common.response.ApiResponse;
import com.example.product_service.review.dto.BatchReviewCheckRequest;
import com.example.product_service.review.dto.BatchReviewSummaryDto;
import com.example.product_service.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "10. Reviews - Internal", description = "API nội bộ kiểm tra trạng thái review của đơn hàng (Order)")
@RestController
@RequestMapping("/internal/v1/products/reviews")
@RequiredArgsConstructor
public class ReviewInternalController {

    private final ReviewService reviewService;

    @Operation(summary = "Kiểm tra xem khách hàng đã đánh giá các sản phẩm trong đơn hàng hay chưa")
    @PostMapping("/summary")
    public ResponseEntity<ApiResponse<List<BatchReviewSummaryDto>>> checkBatchReviews(
            @Valid @RequestBody BatchReviewCheckRequest request) {

        return ResponseEntity.ok(ApiResponse.of(reviewService.checkBatchReviews(request)));
    }
}
