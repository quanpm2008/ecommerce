package com.example.product_service.media.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadUrlResponse {

    private String objectKey;

    private String uploadUrl;

    private OffsetDateTime expiresAt;
}
