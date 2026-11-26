package com.example.product_service.media.service;

import com.example.product_service.media.config.MediaProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class S3MediaStorage {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final MediaProperties properties;
    private final AtomicBoolean bucketReady = new AtomicBoolean();

    public PresignedUpload presignUpload(String objectKey, String mimeType, Long sizeBytes) {
        ensureBucket();
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plus(properties.uploadUrlTtl());
        try {
            String url = s3Presigner.presignPutObject(request -> request
                            .signatureDuration(properties.uploadUrlTtl())
                            .putObjectRequest(builder -> {
                                PutObjectRequest.Builder put = builder
                                        .bucket(properties.bucket())
                                        .key(objectKey)
                                        .contentType(mimeType);
                                if (sizeBytes != null && sizeBytes > 0) {
                                    put.contentLength(sizeBytes);
                                }
                            }))
                    .url()
                    .toString();
            return new PresignedUpload(objectKey, url, expiresAt);
        } catch (SdkException ex) {
            throw unavailable(ex);
        }
    }

    public String resolveReadUrl(String objectKeyOrUrl) {
        if (objectKeyOrUrl == null || objectKeyOrUrl.isBlank()) {
            return objectKeyOrUrl;
        }
        if (objectKeyOrUrl.startsWith("http://") || objectKeyOrUrl.startsWith("https://")) {
            return objectKeyOrUrl;
        }
        return s3Presigner.presignGetObject(request -> request
                        .signatureDuration(properties.readUrlTtl())
                        .getObjectRequest(GetObjectRequest.builder()
                                .bucket(properties.bucket())
                                .key(objectKeyOrUrl)
                                .build()))
                .url()
                .toString();
    }

    private void ensureBucket() {
        if (bucketReady.get()) {
            return;
        }
        try {
            s3Client.headBucket(request -> request.bucket(properties.bucket()));
        } catch (NoSuchBucketException ex) {
            createBucket();
        } catch (S3Exception ex) {
            if (ex.statusCode() != 404) {
                throw unavailable(ex);
            }
            createBucket();
        } catch (SdkException ex) {
            throw unavailable(ex);
        }
        bucketReady.set(true);
    }

    private void createBucket() {
        try {
            s3Client.createBucket(request -> request.bucket(properties.bucket()));
            log.info("Created media bucket {}", properties.bucket());
        } catch (SdkException ex) {
            throw unavailable(ex);
        }
    }

    private static ResponseStatusException unavailable(SdkException ex) {
        log.warn("Object storage call failed: {}", ex.getMessage());
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Media storage is not available");
    }

    public record PresignedUpload(String objectKey, String uploadUrl, OffsetDateTime expiresAt) {
    }
}
