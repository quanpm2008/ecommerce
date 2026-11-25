package com.example.product_service.product.controller;

import com.example.product_service.common.response.ApiResponse;
import com.example.product_service.product.dto.response.ProductBatchItemResponse;
import com.example.product_service.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;

@Tag(name = "9. Products - Internal", description = "API nội bộ giữa các microservices (Cart, Order)")
@RestController
@RequestMapping("/internal/v1/products")
@RequiredArgsConstructor
public class ProductInternalController {

    private final ProductService productService;

    @Value("${product.internal.tokens.order:}")
    private String orderToken;

    @Value("${product.internal.tokens.cart:}")
    private String cartToken;

    @Operation(summary = "Lấy thông tin danh sách sản phẩm theo lô (phục vụ Cart, Order)")
    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<List<ProductBatchItemResponse>>> getProductsBatch(
            @RequestHeader(value = "X-Service-Token", required = false) List<String> credentials,
            @Valid @RequestBody BatchRequest request) {

        if (credentials == null || credentials.size() != 1
                || !(matches(orderToken, credentials.getFirst()) || matches(cartToken, credentials.getFirst()))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return ResponseEntity.ok(ApiResponse.of(productService.getProductsBatch(request.productIds())));
    }

    private static boolean matches(String expected, String supplied) {
        return !expected.isBlank() && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8));
    }

    public record BatchRequest(@NotNull @Size(min = 1) List<@NotNull UUID> productIds) {}
}
