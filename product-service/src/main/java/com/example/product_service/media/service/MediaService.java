package com.example.product_service.media.service;

import com.example.product_service.media.common.MediaValidationRule;
import com.example.product_service.media.dto.request.UploadUrlItemRequest;
import com.example.product_service.media.dto.response.UploadUrlResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MediaService {

    private final MediaValidationRule validationRule;
    private final S3MediaStorage s3MediaStorage;

    public List<UploadUrlResponse> generateUploadUrls(List<UploadUrlItemRequest> files) {
        validationRule.validateFiles(files);

        List<UploadUrlResponse> responses = new ArrayList<>();

        for (UploadUrlItemRequest file : files) {
            String extension = extractExtension(file.getFileName());
            String randomId = UUID.randomUUID().toString().replace("-", "");
            // Quy ước objectKey: tmp/{purpose}/{uuid}.{ext} (N-08: dọn dẹp sau 24h nếu không gắn vào entity)
            String objectKey = String.format("tmp/%s/%s%s",
                    file.getPurpose().name().toLowerCase(),
                    randomId,
                    extension);
            S3MediaStorage.PresignedUpload presigned = s3MediaStorage.presignUpload(
                    objectKey,
                    file.getMimeType(),
                    file.getSizeBytes()
            );
            responses.add(UploadUrlResponse.builder()
                    .objectKey(presigned.objectKey())
                    .uploadUrl(presigned.uploadUrl())
                    .expiresAt(presigned.expiresAt())
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
