package com.example.product_service.media.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("product.media")
public record MediaProperties(
        String endpoint,
        String publicEndpoint,
        String region,
        String accessKey,
        String secretKey,
        String bucket,
        Duration uploadUrlTtl,
        Duration readUrlTtl
) {
}
