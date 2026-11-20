package com.example.product_service.product.service;

import com.example.product_service.common.response.ApiPageResponse;
import com.example.product_service.product.dto.request.CreateProductRequest;
import com.example.product_service.product.dto.request.RemoveProductRequest;
import com.example.product_service.product.dto.request.UpdateProductRequest;
import com.example.product_service.product.dto.request.UpdateProductStatusRequest;
import com.example.product_service.product.dto.response.*;
import com.example.product_service.product.enums.ProductStatus;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface ProductService {

    ApiPageResponse<ProductCardResponse> searchPublicProducts(
            String q, UUID categoryId, UUID sellerId, Long minPriceVnd, Long maxPriceVnd, Pageable pageable);

    ProductDetailResponse getProductDetail(UUID productId);

    List<ProductCardResponse> getRelatedProducts(UUID productId, Integer limit);

    ApiPageResponse<SellerProductResponse> getSellerProducts(
            UUID sellerId, ProductStatus status, String stock, String q, UUID categoryId, Pageable pageable);

    SellerProductResponse getSellerProductDetail(UUID sellerId, UUID productId);

    SellerProductResponse createProduct(UUID sellerId, CreateProductRequest request);

    SellerProductResponse updateProduct(UUID sellerId, UUID productId, UpdateProductRequest request);

    SellerProductResponse updateProductStatus(UUID sellerId, UUID productId, UpdateProductStatusRequest request);

    ApiPageResponse<AdminProductResponse> searchAdminProducts(
            String q, UUID sellerId, UUID categoryId, ProductStatus status, Pageable pageable);

    AdminProductResponse getAdminProductDetail(UUID productId);

    AdminProductResponse removeProductByAdmin(UUID productId, RemoveProductRequest request);

    AdminProductResponse restoreProductByAdmin(UUID productId);

    List<ProductBatchItemResponse> getProductsBatch(List<UUID> productIds);
}
