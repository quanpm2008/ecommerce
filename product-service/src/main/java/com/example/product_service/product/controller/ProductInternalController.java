package com.example.product_service.product.controller;

import com.example.product_service.common.response.ApiResponse;
import com.example.product_service.product.dto.response.ProductBatchItemResponse;
import com.example.product_service.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@Tag(name = "9. Products - Internal", description = "API nội bộ giữa các microservices (Cart, Order)")
@RestController
@RequestMapping("/internal/v1/products")
@RequiredArgsConstructor
public class ProductInternalController {

    private final ProductService productService;

    @Operation(summary = "Lấy thông tin danh sách sản phẩm theo lô (phục vụ Cart, Order)")
    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<List<ProductBatchItemResponse>>> getProductsBatch(
            @RequestBody List<UUID> productIds) {

        return ResponseEntity.ok(ApiResponse.of(productService.getProductsBatch(productIds)));
    }
}
