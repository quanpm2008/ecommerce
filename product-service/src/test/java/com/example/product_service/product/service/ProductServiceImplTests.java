package com.example.product_service.product.service;

import com.example.product_service.category.entity.Category;
import com.example.product_service.category.repository.CategoryRepository;
import com.example.product_service.client.identity.IdentityClientAdapter;
import com.example.product_service.client.notification.NotificationClient;
import com.example.product_service.client.order.OrderInventoryClientAdapter;
import com.example.product_service.client.order.dto.InventoryDetailDto;
import com.example.product_service.media.common.MediaType;
import com.example.product_service.product.dto.SellerPublicDto;
import com.example.product_service.product.dto.model.MediaItem;
import com.example.product_service.product.dto.request.RemoveProductRequest;
import com.example.product_service.product.entity.Product;
import com.example.product_service.product.enums.ProductStatus;
import com.example.product_service.product.exception.ProductNotFoundException;
import com.example.product_service.product.exception.RestoreWindowExpiredException;
import com.example.product_service.product.repository.ProductRepository;
import com.example.product_service.product.validator.ProductValidator;
import com.example.product_service.review.dto.ReviewSummaryDto;
import com.example.product_service.review.service.ReviewService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTests {

    @Mock ProductRepository productRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock ReviewService reviewService;
    @Mock IdentityClientAdapter identityClientAdapter;
    @Mock OrderInventoryClientAdapter orderInventoryClientAdapter;
    @Mock NotificationClient notificationClient;
    @Mock ProductInventoryProvision inventoryProvision;
    @Mock ProductSaleEvidence saleEvidence;

    private ProductServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProductServiceImpl(
                productRepository,
                categoryRepository,
                new ProductValidator(),
                reviewService,
                identityClientAdapter,
                orderInventoryClientAdapter,
                notificationClient,
                inventoryProvision,
                saleEvidence,
                new ObjectMapper()
        );
    }

    @Test
    void searchPublicProductsValidatesPriceRangeAndPageSize() {
        assertThrows(IllegalArgumentException.class, () ->
                service.searchPublicProducts(null, null, null, 500L, 100L, PageRequest.of(0, 20)));
        assertThrows(IllegalArgumentException.class, () ->
                service.searchPublicProducts(null, null, null, null, null, PageRequest.of(0, 101)));
    }

    @Test
    void searchPublicProductsReturnsEmptyWhenNoSellableSellersExist() {
        UUID bannedSeller = UUID.randomUUID();
        when(productRepository.findDistinctSellerIdsByStatus(ProductStatus.SELLING))
                .thenReturn(List.of(bannedSeller));
        when(identityClientAdapter.getSellableMap(List.of(bannedSeller)))
                .thenReturn(Map.of(bannedSeller, false));

        var result = service.searchPublicProducts(null, null, null, null, null, PageRequest.of(0, 20));
        assertTrue(result.getData().isEmpty());
        assertEquals(0L, result.getMeta().getTotalItems());
        verify(productRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void getProductDetailRejectsHiddenAndNonSellableAndCapsMaxPerOrderAt99() {
        UUID productId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        Product hidden = Product.builder().productId(productId).sellerId(sellerId).status(ProductStatus.HIDDEN).build();
        when(productRepository.findById(productId)).thenReturn(Optional.of(hidden));
        assertThrows(ProductNotFoundException.class, () -> service.getProductDetail(productId));

        Product selling = Product.builder()
                .productId(productId)
                .sellerId(sellerId)
                .categoryId(categoryId)
                .productName("Điện thoại")
                .priceVnd(100000L)
                .status(ProductStatus.SELLING)
                .media(List.of(MediaItem.builder().objectKey("a.jpg").mediaType(MediaType.IMAGE).sortOrder(1).isCover(true).build()))
                .build();
        when(productRepository.findById(productId)).thenReturn(Optional.of(selling));
        when(identityClientAdapter.isSellerSellable(sellerId)).thenReturn(false);
        assertThrows(ProductNotFoundException.class, () -> service.getProductDetail(productId));

        when(identityClientAdapter.isSellerSellable(sellerId)).thenReturn(true);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(Category.builder().categoryId(categoryId).categoryName("Tech").build()));
        when(orderInventoryClientAdapter.getAvailabilityMap(List.of(productId))).thenReturn(Map.of(productId, 150));
        when(reviewService.getProductReviewSummary(productId)).thenReturn(ReviewSummaryDto.builder().ratingAverage(4.5).ratingCount(2L).build());
        when(identityClientAdapter.getSellerPublic(sellerId)).thenReturn(SellerPublicDto.builder().sellerId(sellerId).sellerName("Shop").build());

        var detail = service.getProductDetail(productId);
        assertEquals(99, detail.getMaxPerOrder());
        assertEquals(150, detail.getAvailableQuantity());
    }

    @Test
    void getSellerProductsFiltersByStockStatus() {
        UUID sellerId = UUID.randomUUID();
        UUID catId = UUID.randomUUID();
        UUID inStockId = UUID.randomUUID();
        UUID outStockId = UUID.randomUUID();

        Product p1 = Product.builder().productId(inStockId).sellerId(sellerId).categoryId(catId).productName("P1").priceVnd(1000L).status(ProductStatus.SELLING).build();
        Product p2 = Product.builder().productId(outStockId).sellerId(sellerId).categoryId(catId).productName("P2").priceVnd(1000L).status(ProductStatus.SELLING).build();

        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(p1, p2), PageRequest.of(0, 10), 2));
        when(orderInventoryClientAdapter.getDetailMap(List.of(inStockId, outStockId))).thenReturn(Map.of(
                inStockId, InventoryDetailDto.builder().availableQuantity(5).onHandQuantity(5).reservedQuantity(0).build(),
                outStockId, InventoryDetailDto.builder().availableQuantity(0).onHandQuantity(2).reservedQuantity(2).build()
        ));
        when(categoryRepository.findAllById(List.of(catId))).thenReturn(List.of(Category.builder().categoryId(catId).categoryName("Cat").build()));

        var outOfStock = service.getSellerProducts(sellerId, null, "OUT_OF_STOCK", null, null, PageRequest.of(0, 10));
        assertEquals(1, outOfStock.getData().size());
        assertEquals(outStockId, outOfStock.getData().getFirst().getProductId());
    }

    @Test
    void removeProductRejectsEmptyOtherReasonAndRestoreEnforcesExact30DayWindow() {
        UUID productId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () ->
                service.removeProductByAdmin(productId, new RemoveProductRequest("Khác")));
        assertThrows(IllegalArgumentException.class, () ->
                service.removeProductByAdmin(productId, new RemoveProductRequest("Khác:   ")));

        Product expired = Product.builder()
                .productId(productId)
                .status(ProductStatus.REMOVED)
                .removedAt(OffsetDateTime.now().minusDays(30).minusHours(1))
                .build();
        when(productRepository.findById(productId)).thenReturn(Optional.of(expired));
        assertThrows(RestoreWindowExpiredException.class, () -> service.restoreProductByAdmin(productId));
    }
}
