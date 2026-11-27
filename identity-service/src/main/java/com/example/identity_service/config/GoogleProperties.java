package com.example.identity_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * {@code identity.google.*}: Google sign-in for customers (API §6.1 {@code POST /auth/google}, Should). An ID token is
 * accepted only when its audience is {@code clientId} (the web app's OAuth client), its issuer one of {@code issuers}
 * and its signature verifies against Google's keys at {@code jwkSetUri}. No {@code clientId} → Google sign-in is off.
 */
@ConfigurationProperties("identity.google")
public record GoogleProperties(String clientId, String jwkSetUri, List<String> issuers) {

	public boolean enabled() {
		return clientId != null && !clientId.isBlank();
	}

}
