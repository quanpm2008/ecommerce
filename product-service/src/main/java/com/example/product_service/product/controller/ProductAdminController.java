package com.example.product_service.product.controller;

import com.example.product_service.common.response.ApiPageResponse;
import com.example.product_service.common.response.ApiResponse;
import com.example.product_service.product.dto.request.RemoveProductRequest;
import com.example.product_service.product.dto.response.AdminProductResponse;
import com.example.product_service.product.enums.ProductStatus;
import com.example.product_service.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "7. Products - Admin", description = "Quản trị, kiểm duyệt và khôi phục sản phẩm (Admin)")
@RestController
@RequestMapping("/admin/products")
@RequiredArgsConstructor
public class ProductAdminController {

    private final ProductService productService;

    @Operation(summary = "Tra cứu danh sách sản phẩm toàn hệ thống (kèm thông tin kiểm duyệt, hạn khôi phục)")
    @GetMapping
    public ResponseEntity<ApiPageResponse<AdminProductResponse>> searchAdminProducts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID sellerId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {

        int zeroBasedPage = Math.max(page - 1, 0);

        String[] parts = sort.split(",");
        Sort s = Sort.by(parts.length > 1 && parts[1].equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC, parts[0]);
        Pageable pageable = PageRequest.of(zeroBasedPage, size, s);

        return ResponseEntity.ok(productService.searchAdminProducts(q, sellerId, categoryId, status, pageable));
    }

    @Operation(summary = "Xem chi tiết sản phẩm cho Admin")
    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<AdminProductResponse>> getAdminProductDetail(@PathVariable UUID productId) {

        return ResponseEntity.ok(ApiResponse.of(productService.getAdminProductDetail(productId)));
    }

    @Operation(summary = "Gỡ bỏ sản phẩm vi phạm (chuyển sang REMOVED và gửi notification cho Seller)")
    @PostMapping("/{productId}/remove")
    public ResponseEntity<ApiResponse<AdminProductResponse>> removeProduct(
            @PathVariable UUID productId,
            @Valid @RequestBody RemoveProductRequest request) {

        return ResponseEntity.ok(ApiResponse.of(productService.removeProductByAdmin(productId, request)));
    }

    @Operation(summary = "Khôi phục sản phẩm đã bị gỡ về trạng thái Tạm ẩn (HIDDEN) trong thời hạn 30 ngày")
    @PostMapping("/{productId}/restore")
    public ResponseEntity<ApiResponse<AdminProductResponse>> restoreProduct(@PathVariable UUID productId) {

        return ResponseEntity.ok(ApiResponse.of(productService.restoreProductByAdmin(productId)));
    }
}
