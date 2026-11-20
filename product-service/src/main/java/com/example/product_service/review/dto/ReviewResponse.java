package com.example.product_service.review.dto;

import com.example.product_service.product.dto.model.MediaItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResponse {

    private UUID reviewId;

    private UUID productId;

    private UUID orderItemId;

    private Integer rating;

    private String content;

    private List<MediaItem> media;

    private String reviewerDisplayName;

    private Boolean isMine;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}