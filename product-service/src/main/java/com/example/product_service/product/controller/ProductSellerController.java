package com.example.product_service.product.controller;

import com.example.product_service.common.response.ApiPageResponse;
import com.example.product_service.common.response.ApiResponse;
import com.example.product_service.product.dto.request.CreateProductRequest;
import com.example.product_service.product.dto.request.UpdateProductRequest;
import com.example.product_service.product.dto.request.UpdateProductStatusRequest;
import com.example.product_service.product.dto.response.SellerProductResponse;
import com.example.product_service.product.enums.ProductStatus;
import com.example.product_service.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/seller/products")
@RequiredArgsConstructor
public class ProductSellerController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<ApiPageResponse<SellerProductResponse>> getSellerProducts(
            @RequestHeader("X-User-Id") UUID sellerId,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) String stock,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "updatedAt,desc") String sort) {

        int zeroBasedPage = Math.max(page - 1, 0);

        String[] parts = sort.split(",");
        Sort s = Sort.by(parts.length > 1 && parts[1].equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC, parts[0]);
        Pageable pageable = PageRequest.of(zeroBasedPage, size, s);

        return ResponseEntity.ok(productService.getSellerProducts(sellerId, status, stock, q, categoryId, pageable));
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<SellerProductResponse>> getSellerProductDetail(
            @RequestHeader("X-User-Id") UUID sellerId,
            @PathVariable UUID productId) {

        return ResponseEntity.ok(ApiResponse.of(productService.getSellerProductDetail(sellerId, productId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SellerProductResponse>> createProduct(
            @RequestHeader("X-User-Id") UUID sellerId,
            @Valid @RequestBody CreateProductRequest request) {

        SellerProductResponse response = productService.createProduct(sellerId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response));
    }

    @PutMapping("/{productId}")
    public ResponseEntity<ApiResponse<SellerProductResponse>> updateProduct(
            @RequestHeader("X-User-Id") UUID sellerId,
            @PathVariable UUID productId,
            @Valid @RequestBody UpdateProductRequest request) {

        return ResponseEntity.ok(ApiResponse.of(productService.updateProduct(sellerId, productId, request)));
    }

    @PatchMapping("/{productId}/status")
    public ResponseEntity<ApiResponse<SellerProductResponse>> updateProductStatus(
            @RequestHeader("X-User-Id") UUID sellerId,
            @PathVariable UUID productId,
            @Valid @RequestBody UpdateProductStatusRequest request) {

        return ResponseEntity.ok(ApiResponse.of(productService.updateProductStatus(sellerId, productId, request)));
    }
}
