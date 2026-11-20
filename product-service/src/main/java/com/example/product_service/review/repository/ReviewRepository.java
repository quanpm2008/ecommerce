package com.example.product_service.review.repository;

import com.example.product_service.review.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReviewRepository extends JpaRepository<Review, UUID> {

    boolean existsByOrderItemId(UUID orderItemId);

    boolean existsByCustomerIdAndProductId(UUID customerId, UUID productId);

    Page<Review> findByProductId(UUID productId, Pageable pageable);

    Page<Review> findByProductIdAndRating(UUID productId, Integer rating, Pageable pageable);

    long countByProductId(UUID productId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.productId = :productId")
    Double calculateAverageRatingByProductId(@Param("productId") UUID productId);

    @Query("SELECT r.rating, COUNT(r) FROM Review r WHERE r.productId = :productId GROUP BY r.rating")
    List<Object[]> countReviewsGroupedByStar(@Param("productId") UUID productId);

    List<Review> findByCustomerIdAndProductIdIn(UUID customerId, List<UUID> productIds);
}
