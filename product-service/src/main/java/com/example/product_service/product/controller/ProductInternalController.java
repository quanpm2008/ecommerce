package com.example.product_service.product.controller;

import com.example.product_service.common.response.ApiResponse;
import com.example.product_service.product.dto.response.ProductBatchItemResponse;
import com.example.product_service.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/products")
@RequiredArgsConstructor
public class ProductInternalController {

    private final ProductService productService;

    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<List<ProductBatchItemResponse>>> getProductsBatch(
            @RequestBody List<UUID> productIds) {

        return ResponseEntity.ok(ApiResponse.of(productService.getProductsBatch(productIds)));
    }
}
