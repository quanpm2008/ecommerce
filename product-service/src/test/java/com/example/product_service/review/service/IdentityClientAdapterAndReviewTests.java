package com.example.product_service.review.service;

import com.example.product_service.client.identity.IdentityClient;
import com.example.product_service.client.identity.IdentityClientAdapter;
import com.example.product_service.client.order.OrderInventoryClientAdapter;
import com.example.product_service.client.order.dto.ReviewEligibilityDto;
import com.example.product_service.common.response.ApiResponse;
import com.example.product_service.media.common.MediaPurpose;
import com.example.product_service.product.exception.ProductMediaInvalidException;
import com.example.product_service.product.repository.ProductRepository;
import com.example.product_service.review.dto.CreateReviewRequest;
import com.example.product_service.review.repository.ReviewRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityClientAdapterAndReviewTests {

    @Mock IdentityClient identityClient;
    @Mock ReviewRepository reviewRepository;
    @Mock ProductRepository productRepository;
    @Mock OrderInventoryClientAdapter orderInventoryClientAdapter;

    @Test
    void identityAdapterEnforcesPermissionsAndActiveStatus() {
        IdentityClientAdapter adapter = new IdentityClientAdapter(identityClient);
        UUID userId = UUID.randomUUID();

        when(identityClient.authorizationContext()).thenReturn(ApiResponse.of(
                new IdentityClient.SellerAuthorization(userId, "CUSTOMER", "BANNED", true, List.of("review:write:own"))));
        ResponseStatusException banned = assertThrows(ResponseStatusException.class, adapter::requireCustomerForReview);
        assertEquals(HttpStatus.FORBIDDEN, banned.getStatusCode());

        when(identityClient.authorizationContext()).thenReturn(ApiResponse.of(
                new IdentityClient.SellerAuthorization(userId, "ADMIN", "ACTIVE", true, List.of("operations:read:any"))));
        assertEquals(userId, adapter.requirePermission("operations:read:any"));
        assertThrows(ResponseStatusException.class, () -> adapter.requirePermission("product:moderate"));
        assertThrows(ResponseStatusException.class, () ->
                adapter.requireMediaUploadPermission(List.of(MediaPurpose.PRODUCT_MEDIA)));
    }

    @Test
    void reviewServiceRejectsMoreThanOneVideo() {
        ReviewServiceImpl reviewService = new ReviewServiceImpl(
                reviewRepository, productRepository, orderInventoryClientAdapter);
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID orderItemId = UUID.randomUUID();

        when(productRepository.existsById(productId)).thenReturn(true);
        when(reviewRepository.existsByOrderItemId(orderItemId)).thenReturn(false);
        when(reviewRepository.existsByCustomerIdAndProductId(customerId, productId)).thenReturn(false);
        when(orderInventoryClientAdapter.checkReviewEligibility(orderItemId, customerId))
                .thenReturn(ReviewEligibilityDto.builder().eligible(true).build());

        CreateReviewRequest request = CreateReviewRequest.builder()
                .orderItemId(orderItemId)
                .rating(5)
                .content("Tốt")
                .mediaObjectKeys(List.of("tmp/review_media/a.mp4", "tmp/review_media/b.mp4"))
                .build();

        assertThrows(ProductMediaInvalidException.class, () ->
                reviewService.createReview(customerId, productId, request));
    }
}
