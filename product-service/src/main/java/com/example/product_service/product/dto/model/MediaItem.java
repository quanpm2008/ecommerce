package com.example.product_service.product.dto.model;

import com.example.product_service.media.common.MediaType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaItem implements Serializable {

    @NotBlank
    private String objectKey;

    @NotNull
    private MediaType mediaType;

    private Integer sortOrder;

    private Boolean isCover;
}
