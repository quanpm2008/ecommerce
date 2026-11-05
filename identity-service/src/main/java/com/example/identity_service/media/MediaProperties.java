package com.example.identity_service.media;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * {@code identity.media.*}: S3-compatible object storage. {@code endpoint} is how this service reaches the storage;
 * {@code publicEndpoint} is how browsers reach it, and presigned URLs are signed for that host.
 */
@ConfigurationProperties("identity.media")
public record MediaProperties(String endpoint, String publicEndpoint, String region, String accessKey,
		String secretKey, String bucket, Duration uploadUrlTtl, Duration readUrlTtl, long sellerLogoMaxBytes) {
}
