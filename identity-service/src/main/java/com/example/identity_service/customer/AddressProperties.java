package com.example.identity_service.customer;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code identity.address.*}: {@code maxPerCustomer} is P-09 (5).
 */
@ConfigurationProperties("identity.address")
public record AddressProperties(int maxPerCustomer) {
}
