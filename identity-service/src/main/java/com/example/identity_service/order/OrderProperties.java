package com.example.identity_service.order;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * {@code identity.order.*}: where order-service's internal API is ({@code http://order-service:8085} in Docker) and
 * how long to wait (API §7.1 default 2 s).
 */
@ConfigurationProperties("identity.order")
public record OrderProperties(String baseUrl, Duration timeout) {
}
