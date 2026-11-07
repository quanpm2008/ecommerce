package com.example.product_service.review.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateReviewRequest {

    @NotNull
    private UUID orderItemId;

    @NotNull
    @Min(value = 1)
    @Max(value = 5)
    private Integer rating;

    @Size(max = 1000)
    private String content;

    @Size(max = 5)
    private List<String> mediaObjectKeys;
}
