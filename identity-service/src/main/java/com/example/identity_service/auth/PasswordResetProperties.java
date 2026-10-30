package com.example.identity_service.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * {@code identity.password-reset.*}. {@code linkBase} is the trusted FE page the emailed link points to; the token
 * is appended as {@code ?token=}. It never comes from the client. {@code ttl} is P-05 (30 minutes).
 */
@ConfigurationProperties("identity.password-reset")
public record PasswordResetProperties(String linkBase, Duration ttl) {
}
