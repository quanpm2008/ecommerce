package com.example.identity_service.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Keeps events pending when Kafka publishing is disabled.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "identity.kafka.enabled", havingValue = "false", matchIfMissing = true)
class UnconfiguredEventPublisher implements EventPublisher {

	UnconfiguredEventPublisher() {
		log.warn("Identity Kafka publishing is disabled. Outbox events stay PENDING");
	}

	@Override
	public boolean isConfigured() {
		return false;
	}

	@Override
	public void publish(String eventType, Map<String, Object> envelope) {
		throw new IllegalStateException("No message broker configured");
	}

}
