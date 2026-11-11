package com.example.product_service.media.service;

import com.example.product_service.media.common.MediaValidationRule;
import com.example.product_service.media.dto.request.UploadUrlItemRequest;
import com.example.product_service.media.dto.response.UploadUrlResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MediaService {

    private final MediaValidationRule validationRule;

    @Value("${media.storage.base-url:http://localhost:8087/mock-storage}")
    private String storageBaseUrl;

    public List<UploadUrlResponse> generateUploadUrls(List<UploadUrlItemRequest> files){
        validationRule.validateFiles(files);

        // Đường dẫn ký sẵn hết hạn sau 10 phút (API Spec mục 3.9)
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10);
        List<UploadUrlResponse> responses = new ArrayList<>();

        for (UploadUrlItemRequest file : files) {
            String extension = extractExtension(file.getFileName());
            String randomId = UUID.randomUUID().toString().replace("-", "");
            // 3. Quy ước objectKey: tmp/{purpose}/{uuid}.{ext} (N-08: dọn dẹp sau 24h nếu không gắn vào entity)
            String objectKey = String.format("tmp/%s/%s%s",
                    file.getPurpose().name().toLowerCase(),
                    randomId,
                    extension);
            // 4. URL upload dạng PUT
            String uploadUrl = String.format("%s/upload/%s?expires=%s",
                    storageBaseUrl,
                    objectKey,
                    expiresAt.toEpochSecond());
            responses.add(UploadUrlResponse.builder()
                    .objectKey(objectKey)
                    .uploadUrl(uploadUrl)
                    .expiresAt(expiresAt)
                    .build());
        }
        return responses;
    }

    private String extractExtension(String fileName) {
        if (fileName != null && fileName.lastIndexOf('.') != -1) {
            return fileName.substring(fileName.lastIndexOf('.')).toLowerCase();
        }
        return "";
    }
}
