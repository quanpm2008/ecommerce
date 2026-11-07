package com.example.product_service.product.controller;

import com.example.product_service.common.response.ApiPageResponse;
import com.example.product_service.common.response.ApiResponse;
import com.example.product_service.product.dto.response.ProductCardResponse;
import com.example.product_service.product.dto.response.ProductDetailResponse;
import com.example.product_service.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductPublicController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<ApiPageResponse<ProductCardResponse>> searchProducts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID sellerId,
            @RequestParam(required = false) Long minPriceVnd,
            @RequestParam(required = false) Long maxPriceVnd,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {

        int zeroBasedPage = Math.max(page - 1, 0);

        String[] parts = sort.split(",");
        Sort s = Sort.by(parts.length > 1 && parts[1].equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC, parts[0]);
        Pageable pageable = PageRequest.of(zeroBasedPage, size, s);

        return ResponseEntity.ok(productService.searchPublicProducts(q, categoryId, sellerId, minPriceVnd, maxPriceVnd, pageable));
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> getProductDetail(@PathVariable UUID productId) {
        return ResponseEntity.ok(ApiResponse.of(productService.getProductDetail(productId)));
    }

    @GetMapping("/{productId}/related")
    public ResponseEntity<ApiResponse<List<ProductCardResponse>>> getRelatedProducts(
            @PathVariable UUID productId,
            @RequestParam(defaultValue = "8") Integer limit) {
        return ResponseEntity.ok(ApiResponse.of(productService.getRelatedProducts(productId, limit)));
    }
}