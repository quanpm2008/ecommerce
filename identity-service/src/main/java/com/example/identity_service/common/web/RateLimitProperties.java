package com.example.identity_service.common.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * {@code identity.rate-limit.*}: requests allowed per client IP and window (N-04 gives no numbers).
 */
@ConfigurationProperties("identity.rate-limit")
public record RateLimitProperties(Limit register, Limit forgotPassword) {

	public record Limit(int limit, Duration window) {
	}

}
