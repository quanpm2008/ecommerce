package com.example.notification_service.security;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Blank or short secrets disable the corresponding notification producer. */
@ConfigurationProperties("notification.internal")
public record InternalClientProperties(Map<String, String> clients) {
    public InternalClientProperties {
        clients = clients == null ? Map.of() : Map.copyOf(clients);
    }
}
