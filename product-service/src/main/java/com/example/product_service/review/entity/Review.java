package com.example.product_service.review.entity;

import com.example.product_service.product.dto.model.MediaItem;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "reviews",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_reviews_order_item", columnNames = {"order_item_id"}),
                @UniqueConstraint(name = "uq_reviews_customer_product", columnNames = {"customer_id", "product_id"})
        },
        indexes = {
                @Index(name = "idx_reviews_product_id", columnList = "product_id"),
                @Index(name = "idx_reviews_customer_id", columnList = "customer_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "review_id", updatable = false, nullable = false)
    private UUID reviewId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "order_item_id", nullable = false)
    private UUID orderItemId;

    @Column(name = "rating", nullable = false)
    private Integer rating;

    @Column(name = "content", length = 1000)
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "media_metadata", columnDefinition = "jsonb")
    @Builder.Default
    private List<MediaItem> mediaMetadata = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
