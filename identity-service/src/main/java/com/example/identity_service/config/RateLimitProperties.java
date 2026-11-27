package com.example.identity_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * {@code identity.rate-limit.*}: requests allowed per client IP and window (N-04 gives no numbers). {@code login} only
 * slows a client down: P-18 forbids locking an account after failed sign-ins.
 */
@ConfigurationProperties("identity.rate-limit")
public record RateLimitProperties(Limit register, Limit forgotPassword, Limit sellerReapplication, Limit login) {

	public record Limit(int limit, Duration window) {
	}

}
