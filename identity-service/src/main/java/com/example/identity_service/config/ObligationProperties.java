package com.example.identity_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * {@code identity.obligations.*}: where Order and Finance answer (API §7.1), the HTTP timeout (2 s, API §7.1 default),
 * and the {@code X-Service-Token} Identity sends, if any (the service credential scheme is still open, OQ-004).
 */
@ConfigurationProperties("identity.obligations")
public record ObligationProperties(String orderBaseUrl, String financeBaseUrl, Duration timeout, String serviceToken) {
}
