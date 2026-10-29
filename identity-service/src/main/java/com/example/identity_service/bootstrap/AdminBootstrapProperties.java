package com.example.identity_service.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code identity.bootstrap.admin.*}: the admin account created at startup. Leave {@code email} unset to skip.
 */
@ConfigurationProperties("identity.bootstrap.admin")
public record AdminBootstrapProperties(String email, String password,
		@DefaultValue("Administrator") String fullName) {
}
