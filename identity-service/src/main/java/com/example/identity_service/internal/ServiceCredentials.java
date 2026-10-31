package com.example.identity_service.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves an {@code X-Service-Token} to the calling service. Only SHA-256 digests of the configured secrets are kept,
 * and every digest is compared in constant time, so the response time does not reveal how much of a token matched.
 */
@Slf4j
@Component
public class ServiceCredentials {

	/** Authority prefix: a caller {@code order-service} gets {@code service:order-service}. */
	public static final String AUTHORITY_PREFIX = "service:";

	private static final int MIN_SECRET_LENGTH = 32;

	private final Map<String, byte[]> digests = new LinkedHashMap<>();

	public ServiceCredentials(InternalClientProperties properties) {
		properties.clients().forEach((service, secret) -> {
			if (secret == null || secret.isBlank()) {
				return;
			}
			if (secret.length() < MIN_SECRET_LENGTH) {
				log.warn("Internal client {} has a secret shorter than {} characters; it is ignored", service,
						MIN_SECRET_LENGTH);
				return;
			}
			digests.put(service, sha256(secret));
		});
		if (digests.isEmpty()) {
			log.warn("No internal clients configured (identity.internal.clients.*): every /internal call is refused");
		}
	}

	public Optional<String> serviceFor(String token) {
		if (token == null || token.isEmpty()) {
			return Optional.empty();
		}
		byte[] presented = sha256(token);
		String match = null;
		for (Map.Entry<String, byte[]> entry : digests.entrySet()) {
			if (MessageDigest.isEqual(presented, entry.getValue())) {
				match = entry.getKey();
			}
		}
		return Optional.ofNullable(match);
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
