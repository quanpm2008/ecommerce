package com.example.identity_service.outbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Stand-in until a broker is chosen: nothing is sent and events stay PENDING in {@code outbox_events}, ready to go
 * out once a real {@link EventPublisher} replaces this one.
 */
@Slf4j
@Component
class UnconfiguredEventPublisher implements EventPublisher {

	UnconfiguredEventPublisher() {
		log.warn("No message broker configured: outbox events stay PENDING until an EventPublisher is added");
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
