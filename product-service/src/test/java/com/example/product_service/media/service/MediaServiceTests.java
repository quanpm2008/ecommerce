package com.example.product_service.media.service;

import com.example.product_service.media.common.MediaPurpose;
import com.example.product_service.media.common.MediaValidationRule;
import com.example.product_service.media.dto.request.UploadUrlItemRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaServiceTests {

    @Mock
    S3MediaStorage s3MediaStorage;

    @Test
    void delegatesUploadUrlGenerationToS3MediaStorage() {
        MediaService service = new MediaService(new MediaValidationRule(), s3MediaStorage);
        OffsetDateTime expires = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10);

        when(s3MediaStorage.presignUpload(startsWith("tmp/product_media/"), eq("image/jpeg"), eq(1024L)))
                .thenAnswer(inv -> new S3MediaStorage.PresignedUpload(
                        inv.getArgument(0),
                        "http://localhost:8333/ecommerce-media/" + inv.getArgument(0) + "?X-Amz-Signature=abc",
                        expires
                ));

        UploadUrlItemRequest item = UploadUrlItemRequest.builder()
                .fileName("photo.jpg")
                .mimeType("image/jpeg")
                .sizeBytes(1024L)
                .purpose(MediaPurpose.PRODUCT_MEDIA)
                .build();

        var responses = service.generateUploadUrls(List.of(item));
        assertEquals(1, responses.size());
        assertTrue(responses.getFirst().getObjectKey().startsWith("tmp/product_media/"));
        assertTrue(responses.getFirst().getUploadUrl().contains("X-Amz-Signature=abc"));
        assertEquals(expires, responses.getFirst().getExpiresAt());
    }
}
