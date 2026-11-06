package com.example.product_service.media.common;

import com.example.product_service.media.common.exception.MediaInvalidException;
import com.example.product_service.media.dto.request.UploadUrlItemRequest;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class MediaValidationRule {

    private static final long MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024L;  // <= 5MB
    private static final long MAX_VIDEO_SIZE_BYTES = 50 * 1024 * 1024L; // <= 50MB
    private static final int MAX_VIDEO_DURATION_SECONDS = 60;            // <= 60s

    private static final Set<String> ALLOWED_IMAGE_MIMES = Set.of(
            "image/jpeg", "image/png", "image/webp"
    );
    private static final Set<String> ALLOWED_VIDEO_MIMES = Set.of(
            "video/mp4"
    );

    public void validateFiles(List<UploadUrlItemRequest> files) {
        if (files == null || files.isEmpty()) {
            throw new MediaInvalidException("Danh sách file upload không được để trống");
        }

        List<Map<String, Object>> errors = new ArrayList<>();
        Map<MediaPurpose, Integer> totalCountByPurpose = new EnumMap<>(MediaPurpose.class);
        Map<MediaPurpose, Integer> videoCountByPurpose = new EnumMap<>(MediaPurpose.class);

        for (int i = 0; i < files.size(); i++) {
            UploadUrlItemRequest file = files.get(i);
            String fieldPrefix = "files[" + i + "]";

            if (file.getPurpose() == null) {
                errors.add(Map.of("field", fieldPrefix + ".purpose", "issue", "PURPOSE_REQUIRED"));
                continue;
            }

            totalCountByPurpose.put(file.getPurpose(), totalCountByPurpose.getOrDefault(file.getPurpose(), 0) + 1);

            String mime = file.getMimeType() != null ? file.getMimeType().trim().toLowerCase() : "";
            boolean isImg = ALLOWED_IMAGE_MIMES.contains(mime);
            boolean isVid = ALLOWED_VIDEO_MIMES.contains(mime);

            if (!isImg && !isVid) {
                errors.add(Map.of(
                        "field", fieldPrefix + ".mimeType",
                        "issue", "UNSUPPORTED_MIME_TYPE",
                        "fileName", file.getFileName() != null ? file.getFileName() : ""
                ));
                continue;
            }

            if (isImg) {
                if (file.getSizeBytes() != null && file.getSizeBytes() > MAX_IMAGE_SIZE_BYTES) {
                    errors.add(Map.of(
                            "field", fieldPrefix + ".sizeBytes",
                            "issue", "IMAGE_SIZE_EXCEEDED",
                            "maxAllowed", MAX_IMAGE_SIZE_BYTES
                    ));
                }
            } else {
                videoCountByPurpose.put(file.getPurpose(), videoCountByPurpose.getOrDefault(file.getPurpose(), 0) + 1);
                if (file.getSizeBytes() != null && file.getSizeBytes() > MAX_VIDEO_SIZE_BYTES) {
                    errors.add(Map.of(
                            "field", fieldPrefix + ".sizeBytes",
                            "issue", "VIDEO_SIZE_EXCEEDED",
                            "maxAllowed", MAX_VIDEO_SIZE_BYTES
                    ));
                }
                if (file.getDurationSec() == null || file.getDurationSec() <= 0 || file.getDurationSec() > MAX_VIDEO_DURATION_SECONDS) {
                    errors.add(Map.of(
                            "field", fieldPrefix + ".durationSec",
                            "issue", "VIDEO_DURATION_INVALID",
                            "maxAllowedSeconds", MAX_VIDEO_DURATION_SECONDS
                    ));
                }
            }
        }

        int productMediaTotal = totalCountByPurpose.getOrDefault(MediaPurpose.PRODUCT_MEDIA, 0);
        int productVideoTotal = videoCountByPurpose.getOrDefault(MediaPurpose.PRODUCT_MEDIA, 0);
        if (productMediaTotal > 9) {
            errors.add(Map.of("field", "files", "issue", "PRODUCT_MEDIA_LIMIT_EXCEEDED", "maxAllowed", 9));
        }
        if (productVideoTotal > 2) {
            errors.add(Map.of("field", "files", "issue", "PRODUCT_VIDEO_LIMIT_EXCEEDED", "maxAllowed", 2));
        }
        int reviewMediaTotal = totalCountByPurpose.getOrDefault(MediaPurpose.REVIEW_MEDIA, 0);
        int reviewVideoTotal = videoCountByPurpose.getOrDefault(MediaPurpose.REVIEW_MEDIA, 0);
        if (reviewMediaTotal > 5) {
            errors.add(Map.of("field", "files", "issue", "REVIEW_MEDIA_LIMIT_EXCEEDED", "maxAllowed", 5));
        }
        if (reviewVideoTotal > 1) {
            errors.add(Map.of("field", "files", "issue", "REVIEW_VIDEO_LIMIT_EXCEEDED", "maxAllowed", 1));
        }
        if (!errors.isEmpty()) {
            throw new MediaInvalidException("Thông tin file upload không hợp lệ theo quy định", errors);
        }
    }
}
