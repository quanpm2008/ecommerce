package com.example.notification_service.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Only digests are retained here; all eligible digests are compared before returning a producer. */
@Component
public class ServiceCredentials {
    private static final int MIN_SECRET_LENGTH = 32;
    private static final Set<String> PRODUCERS = Set.of("identity-service", "product-service", "order-service",
            "payment-service", "finance-service");
    private final Map<String, byte[]> digests = new LinkedHashMap<>();

    public ServiceCredentials(InternalClientProperties properties) {
        properties.clients().forEach((service, secret) -> {
            if (!PRODUCERS.contains(service) || secret == null || secret.isBlank()
                    || secret.length() < MIN_SECRET_LENGTH) {
                return;
            }
            byte[] digest = sha256(secret);
            for (byte[] configured : digests.values()) {
                if (MessageDigest.isEqual(digest, configured)) {
                    throw new IllegalArgumentException("Notification producers must have distinct service tokens");
                }
            }
            digests.put(service, digest);
        });
    }

    public Optional<String> serviceFor(String token) {
        if (token == null || token.isBlank() || token.length() < MIN_SECRET_LENGTH) {
            return Optional.empty();
        }
        byte[] presented = sha256(token);
        String producer = null;
        for (Map.Entry<String, byte[]> entry : digests.entrySet()) {
            if (MessageDigest.isEqual(presented, entry.getValue())) {
                producer = entry.getKey();
            }
        }
        return Optional.ofNullable(producer);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
