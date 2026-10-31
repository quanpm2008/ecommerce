package com.example.identity_service.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * {@code identity.internal.clients.<service-name>=<secret>}: the services allowed to call {@code /internal/v1/identity}
 * and the token each one sends in {@code X-Service-Token}. Names are the callers' {@code spring.application.name}
 * (e.g. {@code order-service}); a blank secret disables that service.
 */
@ConfigurationProperties("identity.internal")
public record InternalClientProperties(Map<String, String> clients) {

	public InternalClientProperties {
		clients = clients == null ? Map.of() : Map.copyOf(clients);
	}

}
