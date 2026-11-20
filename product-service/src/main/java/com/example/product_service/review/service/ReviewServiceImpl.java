package com.example.product_service.review.service;

import com.example.product_service.client.order.OrderInventoryClientAdapter;
import com.example.product_service.client.order.dto.ReviewEligibilityDto;
import com.example.product_service.common.response.ApiPageWithSummaryResponse;
import com.example.product_service.media.common.MediaType;
import com.example.product_service.product.dto.model.MediaItem;
import com.example.product_service.product.exception.ProductNotFoundException;
import com.example.product_service.product.repository.ProductRepository;
import com.example.product_service.review.dto.*;
import com.example.product_service.review.entity.Review;
import com.example.product_service.review.exception.ReviewAlreadyExistsException;
import com.example.product_service.review.exception.ReviewNotAllowedException;
import com.example.product_service.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final OrderInventoryClientAdapter orderInventoryClientAdapter;

    @Override
    @Transactional
    public ReviewResponse createReview(UUID customerId, UUID productId, CreateReviewRequest request) {

        if (!productRepository.existsById(productId)) {
            throw new ProductNotFoundException("Không tìm thấy sản phẩm với mã: " + productId);
        }

        if (reviewRepository.existsByOrderItemId(request.getOrderItemId())) {
            throw new ReviewAlreadyExistsException("Mục đơn hàng này đã được đánh giá.");
        }

        if (reviewRepository.existsByCustomerIdAndProductId(customerId, productId)) {
            throw new ReviewAlreadyExistsException("Bạn đã gửi đánh giá cho sản phẩm này.");
        }

        ReviewEligibilityDto eligibility = orderInventoryClientAdapter.checkReviewEligibility(
                request.getOrderItemId(), customerId);

        if (eligibility == null || !Boolean.TRUE.equals(eligibility.getEligible())) {
            throw new ReviewNotAllowedException("Đơn hàng chưa hoàn thành hoặc mục không đủ điều kiện đánh giá.");
        }

        List<MediaItem> mediaList = new ArrayList<>();
        if (request.getMediaObjectKeys() != null) {
            for (int i = 0; i < request.getMediaObjectKeys().size(); i++) {
                String key = request.getMediaObjectKeys().get(i);
                MediaType type = (key != null && (key.endsWith(".mp4") || key.endsWith(".mov")))
                        ? MediaType.VIDEO
                        : MediaType.IMAGE;
                mediaList.add(MediaItem.builder()
                        .objectKey(key)
                        .mediaType(type)
                        .sortOrder(i + 1)
                        .isCover(false)
                        .build());
            }
        }
        Review review = Review.builder()
                .productId(productId)
                .customerId(customerId)
                .orderItemId(request.getOrderItemId())
                .rating(request.getRating())
                .content(request.getContent())
                .mediaMetadata(mediaList)
                .build();
        Review saved = reviewRepository.save(review);
        return toReviewResponse(saved, customerId);
    }

    @Override
    @Transactional(readOnly = true)
    public ApiPageWithSummaryResponse<ReviewResponse, ReviewSummaryDto> getProductReviews(
            UUID productId, Integer star, Pageable pageable, UUID currentUserId) {

        if (!productRepository.existsById(productId)) {
            throw new ProductNotFoundException("Không tìm thấy sản phẩm với mã: " + productId);
        }

        Page<Review> reviewPage = (star != null && star >= 1 && star <= 5)
                ? reviewRepository.findByProductIdAndRating(productId, star, pageable)
                : reviewRepository.findByProductId(productId, pageable);

        ReviewSummaryDto summary = getProductReviewSummary(productId);

        List<ReviewResponse> dtoList = reviewPage.getContent().stream()
                .map(r -> toReviewResponse(r, currentUserId))
                .toList();

        return ApiPageWithSummaryResponse.of(dtoList, reviewPage, summary);
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewSummaryDto getProductReviewSummary(UUID productId) {

        Double avg = reviewRepository.calculateAverageRatingByProductId(productId);
        long count = reviewRepository.countByProductId(productId);

        Map<String, Long> countByStar = new HashMap<>();
        for (int i = 1; i <= 5; i++) {
            countByStar.put(String.valueOf(i), 0L);
        }

        List<Object[]> starGroups = reviewRepository.countReviewsGroupedByStar(productId);
        for (Object[] row : starGroups) {
            Integer s = (Integer) row[0];
            Long c = (Long) row[1];
            if (s != null && s >= 1 && s <= 5) {
                countByStar.put(String.valueOf(s), c);
            }
        }

        double roundedAvg = avg != null ? Math.round(avg * 10.0) / 10.0 : 0.0;

        return ReviewSummaryDto.builder()
                .ratingAverage(roundedAvg)
                .ratingCount(count)
                .countByStar(countByStar)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BatchReviewSummaryDto> checkBatchReviews(BatchReviewCheckRequest request) {

        List<Review> existingReviews = reviewRepository.findByCustomerIdAndProductIdIn(
                request.getCustomerId(), request.getProductIds());

        Map<UUID, UUID> map = existingReviews.stream()
                .collect(Collectors.toMap(Review::getProductId, Review::getReviewId, (a, b) -> a));

        return request.getProductIds().stream()
                .map(pId -> BatchReviewSummaryDto.builder()
                        .productId(pId)
                        .reviewId(map.get(pId))
                        .hasReviewed(map.containsKey(pId))
                        .build())
                .toList();
    }

    private ReviewResponse toReviewResponse(Review r, UUID currentUserId) {
        boolean isMine = currentUserId != null && currentUserId.equals(r.getCustomerId());
        return ReviewResponse.builder()
                .reviewId(r.getReviewId())
                .productId(r.getProductId())
                .orderItemId(r.getOrderItemId())
                .rating(r.getRating())
                .content(r.getContent())
                .media(r.getMediaMetadata() != null ? r.getMediaMetadata() : Collections.emptyList())
                .reviewerDisplayName(maskName(r.getCustomerId().toString()))
                .isMine(isMine)
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }

    private String maskName(String identifier) {
        if (identifier == null || identifier.length() < 2) return "k***h";
        return identifier.charAt(0) + "***" + identifier.charAt(identifier.length() - 1);
    }
}
