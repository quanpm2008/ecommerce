package com.example.product_service.media.dto.request;

import com.example.product_service.media.common.MediaPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadUrlItemRequest {
    @NotBlank
    private String fileName;

    @NotBlank
    private String mimeType;

    @NotNull
    @Positive(message = "Dung lượng file phải lớn hơn 0")
    private Long sizeBytes;

    private Integer durationSec;

    @NotNull
    private MediaPurpose purpose;
}
