package com.example.product_service.product.service;

import com.example.product_service.category.entity.Category;
import com.example.product_service.category.enums.CategoryStatus;
import com.example.product_service.category.exception.CategoryNotAvailableException;
import com.example.product_service.category.repository.CategoryRepository;
import com.example.product_service.client.identity.IdentityClientAdapter;
import com.example.product_service.client.notification.NotificationClient;
import com.example.product_service.client.notification.dto.CreateNotificationInternalRequest;
import com.example.product_service.client.order.OrderInventoryClientAdapter;
import com.example.product_service.client.order.dto.InventoryDetailDto;
import com.example.product_service.common.response.ApiPageResponse;
import com.example.product_service.common.response.PageMetaResponse;
import com.example.product_service.product.dto.CategorySummaryDto;
import com.example.product_service.product.dto.SellerPublicDto;
import com.example.product_service.product.dto.request.CreateProductRequest;
import com.example.product_service.product.dto.request.RemoveProductRequest;
import com.example.product_service.product.dto.request.UpdateProductRequest;
import com.example.product_service.product.dto.request.UpdateProductStatusRequest;
import com.example.product_service.product.dto.response.*;
import com.example.product_service.product.entity.Product;
import com.example.product_service.product.enums.ProductStatus;
import com.example.product_service.product.exception.*;
import com.example.product_service.product.repository.ProductRepository;
import com.example.product_service.product.repository.specification.ProductSpecification;
import com.example.product_service.product.validator.ProductValidator;
import com.example.product_service.review.dto.ReviewSummaryDto;
import com.example.product_service.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductValidator productValidator;
    private final ReviewService reviewService;
    private final IdentityClientAdapter identityClientAdapter;
    private final OrderInventoryClientAdapter orderInventoryClientAdapter;
    private final NotificationClient notificationClient;

    @Override
    @Transactional(readOnly = true)
    public ApiPageResponse<ProductCardResponse> searchPublicProducts(
            String q, UUID categoryId, UUID sellerId, Long minPriceVnd, Long maxPriceVnd, Pageable pageable) {

        List<UUID> allowedSellers = null;
        if (sellerId != null) {
            if (!identityClientAdapter.isSellerSellable(sellerId)) {
                return ApiPageResponse.of(
                        Collections.emptyList(),
                        PageMetaResponse.builder()
                                .page(1)
                                .size(pageable.getPageSize())
                                .totalItems(0L)
                                .totalPages(0)
                                .build()
                );
            }
            allowedSellers = List.of(sellerId);
        }

        Specification<Product> spec = ProductSpecification.filterPublic(q, categoryId, sellerId, minPriceVnd, maxPriceVnd, allowedSellers);
        Page<Product> page = productRepository.findAll(spec, pageable);

        Map<UUID, Integer> availabilityMap = orderInventoryClientAdapter.getAvailabilityMap(
                page.getContent().stream().map(Product::getProductId).toList());

        List<ProductCardResponse> cards = page.getContent().stream()
                .map(p -> toProductCardResponse(p, availabilityMap.getOrDefault(p.getProductId(), 0)))
                .toList();

        return ApiPageResponse.of(cards, PageMetaResponse.fromPage(page));
    }

    @Override
    @Transactional(readOnly = true)
    public ProductDetailResponse getProductDetail(UUID productId) {

        Product p = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Không tìm thấy sản phẩm với mã: " + productId));

        if (p.getStatus() == ProductStatus.REMOVED) {
            throw new ProductRemovedByAdminException("Sản phẩm đã bị gỡ bỏ bởi Quản trị viên.");
        }

        Category category = categoryRepository.findById(p.getCategoryId())
                .orElse(Category.builder().categoryId(p.getCategoryId()).categoryName("Danh mục").build());

        Map<UUID, Integer> availabilityMap = orderInventoryClientAdapter.getAvailabilityMap(List.of(productId));
        int availableQty = availabilityMap.getOrDefault(productId, 0);

        ReviewSummaryDto reviewSummary = reviewService.getProductReviewSummary(productId);
        SellerPublicDto sellerPublic = identityClientAdapter.getSellerPublic(p.getSellerId());

        return ProductDetailResponse.builder()
                .productId(p.getProductId())
                .productName(p.getProductName())
                .priceVnd(p.getPriceVnd())
                .coverImageUrl(productValidator.resolveCoverImageUrl(p.getMedia()))
                .ratingAverage(reviewSummary.getRatingAverage())
                .ratingCount(reviewSummary.getRatingCount())
                .stockStatus(availableQty > 0 ? "IN_STOCK" : "OUT_OF_STOCK")
                .description(p.getDescription())
                .category(CategorySummaryDto.builder()
                        .categoryId(category.getCategoryId())
                        .categoryName(category.getCategoryName())
                        .build())
                .media(p.getMedia())
                .availableQuantity(availableQty)
                .maxPerOrder(Math.min(10, Math.max(0, availableQty)))
                .seller(sellerPublic)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductCardResponse> getRelatedProducts(UUID productId, Integer limit) {

        Product p = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Không tìm thấy sản phẩm với mã: " + productId));

        int fetchLimit = (limit != null && limit > 0) ? Math.min(limit, 20) : 8;

        List<Product> related = productRepository.findByCategoryIdAndStatusAndProductIdNot(
                p.getCategoryId(), ProductStatus.SELLING, productId, PageRequest.of(0, fetchLimit));

        Map<UUID, Integer> availabilityMap = orderInventoryClientAdapter.getAvailabilityMap(
                related.stream().map(Product::getProductId).toList());

        return related.stream()
                .map(r -> toProductCardResponse(r, availabilityMap.getOrDefault(r.getProductId(), 0)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ApiPageResponse<SellerProductResponse> getSellerProducts(
            UUID sellerId, ProductStatus status, String stock, String q, UUID categoryId, Pageable pageable) {

        Specification<Product> spec = ProductSpecification.filterSeller(sellerId, status, q, categoryId);
        Page<Product> page = productRepository.findAll(spec, pageable);

        Map<UUID, InventoryDetailDto> detailMap = orderInventoryClientAdapter.getDetailMap(
                page.getContent().stream().map(Product::getProductId).toList());

        Map<UUID, String> catNames = categoryRepository.findAllById(
                        page.getContent().stream().map(Product::getCategoryId).distinct().toList())
                .stream().collect(Collectors.toMap(Category::getCategoryId, Category::getCategoryName));

        List<SellerProductResponse> responses = page.getContent().stream()
                .map(p -> toSellerProductResponse(p, catNames.get(p.getCategoryId()), detailMap.get(p.getProductId())))
                .toList();

        return ApiPageResponse.of(responses, PageMetaResponse.fromPage(page));
    }

    @Override
    @Transactional(readOnly = true)
    public SellerProductResponse getSellerProductDetail(UUID sellerId, UUID productId) {

        Product p = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Không tìm thấy sản phẩm với mã: " + productId));

        if (!sellerId.equals(p.getSellerId())) {
            throw new UnauthorizedProductAccessException("Không phải sản phẩm của bạn.");
        }

        String catName = categoryRepository.findById(p.getCategoryId()).map(Category::getCategoryName).orElse("N/A");
        InventoryDetailDto inv = orderInventoryClientAdapter.getDetailMap(List.of(productId)).get(productId);

        return toSellerProductResponse(p, catName, inv);
    }

    @Override
    @Transactional
    public SellerProductResponse createProduct(UUID sellerId, CreateProductRequest request) {

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new CategoryNotAvailableException("Danh mục không tồn tại: " + request.getCategoryId()));

        if (category.getStatus() != CategoryStatus.VISIBLE) {
            throw new CategoryNotAvailableException("Danh mục đã bị ẩn, không thể thêm sản phẩm.");
        }

        productValidator.validateAndNormalizeMedia(request.getMedia());

        ProductStatus targetStatus = request.getStatus() != null ? request.getStatus() : ProductStatus.SELLING;

        Product product = Product.builder()
                .sellerId(sellerId)
                .categoryId(request.getCategoryId())
                .productName(request.getProductName().trim())
                .priceVnd(request.getPriceVnd())
                .description(request.getDescription())
                .status(ProductStatus.HIDDEN)
                .media(request.getMedia())
                .build();

        Product saved = productRepository.save(product);

        orderInventoryClientAdapter.initInventory(
                saved.getProductId(),
                sellerId,
                request.getInitialStock() != null ? request.getInitialStock() : 0);

        saved.setStatus(targetStatus);
        Product finalized = productRepository.save(saved);

        InventoryDetailDto inv = InventoryDetailDto.builder()
                .onHandQuantity(request.getInitialStock())
                .reservedQuantity(0)
                .availableQuantity(request.getInitialStock())
                .version(1L)
                .build();

        return toSellerProductResponse(finalized, category.getCategoryName(), inv);
    }

    @Override
    @Transactional
    public SellerProductResponse updateProduct(UUID sellerId, UUID productId, UpdateProductRequest request) {

        Product p = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Không tìm thấy sản phẩm với mã: " + productId));

        if (!sellerId.equals(p.getSellerId())) {
            throw new UnauthorizedProductAccessException("Không phải sản phẩm của bạn.");
        }

        if (p.getStatus() == ProductStatus.REMOVED) {
            throw new ProductRemovedByAdminException("Sản phẩm đã bị Admin gỡ bỏ.");
        }

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new CategoryNotAvailableException("Danh mục không tồn tại: " + request.getCategoryId()));

        if (category.getStatus() != CategoryStatus.VISIBLE) {
            throw new CategoryNotAvailableException("Danh mục đã bị ẩn.");
        }

        productValidator.validateAndNormalizeMedia(request.getMedia());

        p.setProductName(request.getProductName().trim());
        p.setCategoryId(request.getCategoryId());
        p.setPriceVnd(request.getPriceVnd());
        p.setDescription(request.getDescription());
        p.setMedia(request.getMedia());

        Product updated = productRepository.save(p);
        InventoryDetailDto inv = orderInventoryClientAdapter.getDetailMap(List.of(productId)).get(productId);

        return toSellerProductResponse(updated, category.getCategoryName(), inv);
    }

    @Override
    @Transactional
    public SellerProductResponse updateProductStatus(UUID sellerId, UUID productId, UpdateProductStatusRequest request) {

        Product p = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Không tìm thấy sản phẩm với mã: " + productId));

        if (!sellerId.equals(p.getSellerId())) {
            throw new UnauthorizedProductAccessException("Không phải sản phẩm của bạn.");
        }

        if (p.getStatus() == ProductStatus.REMOVED) {
            throw new ProductRemovedByAdminException("Sản phẩm đã bị Admin gỡ bỏ.");
        }

        if (request.getStatus() == ProductStatus.REMOVED) {
            throw new InvalidStateTransitionException("Seller không thể chuyển sản phẩm sang REMOVED.");
        }

        p.setStatus(request.getStatus());
        Product updated = productRepository.save(p);

        String catName = categoryRepository.findById(p.getCategoryId()).map(Category::getCategoryName).orElse("N/A");
        InventoryDetailDto inv = orderInventoryClientAdapter.getDetailMap(List.of(productId)).get(productId);

        return toSellerProductResponse(updated, catName, inv);
    }

    @Override
    @Transactional(readOnly = true)
    public ApiPageResponse<AdminProductResponse> searchAdminProducts(
            String q, UUID sellerId, UUID categoryId, ProductStatus status, Pageable pageable) {

        Specification<Product> spec = ProductSpecification.filterAdmin(q, sellerId, categoryId, status);

        Page<Product> page = productRepository.findAll(spec, pageable);

        Map<UUID, Integer> availabilityMap = orderInventoryClientAdapter.getAvailabilityMap(
                page.getContent().stream().map(Product::getProductId).toList());

        Map<UUID, String> catNames = categoryRepository.findAllById(
                        page.getContent().stream().map(Product::getCategoryId).distinct().toList())
                .stream().collect(Collectors.toMap(Category::getCategoryId, Category::getCategoryName));

        List<AdminProductResponse> responses = page.getContent().stream()
                .map(p -> toAdminProductResponse(p, catNames.get(p.getCategoryId()), availabilityMap.getOrDefault(p.getProductId(), 0), false))
                .toList();

        return ApiPageResponse.of(responses, PageMetaResponse.fromPage(page));
    }

    @Override
    @Transactional(readOnly = true)
    public AdminProductResponse getAdminProductDetail(UUID productId) {

        Product p = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Không tìm thấy sản phẩm với mã: " + productId));

        String catName = categoryRepository.findById(p.getCategoryId()).map(Category::getCategoryName).orElse("N/A");

        int availableQty = orderInventoryClientAdapter.getAvailabilityMap(List.of(productId)).getOrDefault(productId, 0);

        return toAdminProductResponse(p, catName, availableQty, true);
    }

    @Override
    @Transactional
    public AdminProductResponse removeProductByAdmin(UUID productId, RemoveProductRequest request) {

        Product p = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Không tìm thấy sản phẩm với mã: " + productId));

        if (p.getStatus() == ProductStatus.REMOVED) {
            String catName = categoryRepository.findById(p.getCategoryId()).map(Category::getCategoryName).orElse("N/A");
            return toAdminProductResponse(p, catName, 0, true);
        }

        p.setStatus(ProductStatus.REMOVED);
        p.setRemovalReason(request.getReason());
        p.setRemovedAt(OffsetDateTime.now());

        Product saved = productRepository.save(p);

        try {
            notificationClient.sendNotification(CreateNotificationInternalRequest.builder()
                    .eventId(UUID.randomUUID().toString())
                    .recipientUserId(p.getSellerId())
                    .audienceContext("SELLER")
                    .type("PRODUCT_REMOVED")
                    .channels(List.of("IN_APP"))
                    .templateData(Map.of(
                            "productName", p.getProductName(),
                            "reason", request.getReason()
                    ))
                    .build());
        } catch (Exception e) {
            log.warn("Không thể gửi thông báo tới Seller {}: {}", p.getSellerId(), e.getMessage());
        }

        String catName = categoryRepository.findById(p.getCategoryId()).map(Category::getCategoryName).orElse("N/A");

        return toAdminProductResponse(saved, catName, 0, true);
    }

    @Override
    @Transactional
    public AdminProductResponse restoreProductByAdmin(UUID productId) {

        Product p = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Không tìm thấy sản phẩm với mã: " + productId));

        if (p.getStatus() != ProductStatus.REMOVED) {
            throw new InvalidStateTransitionException("Chỉ sản phẩm REMOVED mới có thể khôi phục.");
        }

        if (p.getRemovedAt() != null) {
            long daysPassed = Duration.between(p.getRemovedAt(), OffsetDateTime.now()).toDays();
            if (daysPassed > 30) {
                throw new RestoreWindowExpiredException("Đã quá thời hạn 30 ngày khôi phục.");
            }
        }

        p.setStatus(ProductStatus.HIDDEN);
        p.setRemovalReason(null);
        p.setRemovedAt(null);

        Product saved = productRepository.save(p);

        String catName = categoryRepository.findById(p.getCategoryId()).map(Category::getCategoryName).orElse("N/A");
        int availableQty = orderInventoryClientAdapter.getAvailabilityMap(List.of(productId)).getOrDefault(productId, 0);

        return toAdminProductResponse(saved, catName, availableQty, true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductBatchItemResponse> getProductsBatch(List<UUID> productIds) {

        List<Product> products = productRepository.findByProductIdIn(productIds);
        if (products.isEmpty()) {
            return Collections.emptyList();
        }

        List<UUID> sellerIds = products.stream().map(Product::getSellerId).distinct().toList();
        Map<UUID, Boolean> sellerSellableMap = identityClientAdapter.getSellableMap(sellerIds);

        return products.stream()
                .map(p -> {
                    boolean sellerOk = Boolean.TRUE.equals(sellerSellableMap.get(p.getSellerId()));
                    boolean isSellable = (p.getStatus() == ProductStatus.SELLING) && sellerOk;

                    return ProductBatchItemResponse.builder()
                            .productId(p.getProductId())
                            .sellerId(p.getSellerId())
                            .priceVnd(p.getPriceVnd())
                            .productName(p.getProductName())
                            .coverImageUrl(productValidator.resolveCoverImageUrl(p.getMedia()))
                            .media(p.getMedia())
                            .status(p.getStatus())
                            .sellable(isSellable)
                            .build();
                })
                .toList();
    }

    private ProductCardResponse toProductCardResponse(Product p, int availableQty) {

        ReviewSummaryDto summary = reviewService.getProductReviewSummary(p.getProductId());
        SellerPublicDto seller = identityClientAdapter.getSellerPublic(p.getSellerId());

        return ProductCardResponse.builder()
                .productId(p.getProductId())
                .productName(p.getProductName())
                .priceVnd(p.getPriceVnd())
                .coverImageUrl(productValidator.resolveCoverImageUrl(p.getMedia()))
                .ratingAverage(summary.getRatingAverage())
                .ratingCount(summary.getRatingCount())
                .stockStatus(availableQty > 0 ? "IN_STOCK" : "OUT_OF_STOCK")
                .sellerId(p.getSellerId())
                .sellerName(seller != null ? seller.getSellerName() : "Gian hàng")
                .build();
    }

    private SellerProductResponse toSellerProductResponse(Product p, String catName, InventoryDetailDto inv) {

        int onHand = inv != null && inv.getOnHandQuantity() != null ? inv.getOnHandQuantity() : 0;
        int reserved = inv != null && inv.getReservedQuantity() != null ? inv.getReservedQuantity() : 0;
        int available = inv != null && inv.getAvailableQuantity() != null ? inv.getAvailableQuantity() : 0;
        Long version = inv != null && inv.getVersion() != null ? inv.getVersion() : 1L;

        return SellerProductResponse.builder()
                .productId(p.getProductId())
                .productName(p.getProductName())
                .category(CategorySummaryDto.builder().categoryId(p.getCategoryId()).categoryName(catName).build())
                .priceVnd(p.getPriceVnd())
                .status(p.getStatus())
                .coverImageUrl(productValidator.resolveCoverImageUrl(p.getMedia()))
                .media(p.getMedia())
                .description(p.getDescription())
                .onHandQuantity(onHand)
                .reservedQuantity(reserved)
                .availableQuantity(available)
                .inventoryVersion(version)
                .removalReason(p.getRemovalReason())
                .removedAt(p.getRemovedAt())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }

    private AdminProductResponse toAdminProductResponse(
            Product p, String catName, int availableQty, boolean includeDetail) {

        ReviewSummaryDto summary = reviewService.getProductReviewSummary(p.getProductId());
        SellerPublicDto seller = identityClientAdapter.getSellerPublic(p.getSellerId());

        OffsetDateTime restoreDeadline = null;
        Integer daysLeft = null;
        if (p.getRemovedAt() != null) {
            restoreDeadline = p.getRemovedAt().plusDays(30);
            long days = Duration.between(OffsetDateTime.now(), restoreDeadline).toDays();
            daysLeft = Math.max(0, (int) days);
        }

        AdminProductResponse.AdminProductResponseBuilder builder = AdminProductResponse.builder()
                .productId(p.getProductId())
                .productName(p.getProductName())
                .priceVnd(p.getPriceVnd())
                .coverImageUrl(productValidator.resolveCoverImageUrl(p.getMedia()))
                .ratingAverage(summary.getRatingAverage())
                .ratingCount(summary.getRatingCount())
                .stockStatus(availableQty > 0 ? "IN_STOCK" : "OUT_OF_STOCK")
                .sellerId(p.getSellerId())
                .sellerName(seller != null ? seller.getSellerName() : "Gian hàng")
                .status(p.getStatus())
                .categoryName(catName)
                .removalReason(p.getRemovalReason())
                .removedAt(p.getRemovedAt())
                .restoreDeadline(restoreDeadline)
                .restoreDaysLeft(daysLeft);

        if (includeDetail) {
            builder.media(p.getMedia()).description(p.getDescription());
        }

        return builder.build();
    }
}
