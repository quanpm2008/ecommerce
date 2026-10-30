package com.example.identity_service.session;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * {@code identity.token.*}. {@code signingKey} is a PKCS#8 PEM RSA private key; when blank a temporary key is
 * generated at startup (dev only).
 */
@ConfigurationProperties("identity.token")
public record TokenProperties(String issuer, String audience, Duration accessTtl, Duration refreshTtl,
		String signingKey) {
}
