package com.example.identity_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * {@code identity.seller-reapplication.*}. {@code linkBase} is the trusted FE page the emailed link points to (the
 * token is appended as {@code ?token=}); {@code ttl} is how long the single-use token works (30 minutes, like P-05;
 * coverage OQ-011).
 */
@ConfigurationProperties("identity.seller-reapplication")
public record SellerReapplicationProperties(String linkBase, Duration ttl) {
}
