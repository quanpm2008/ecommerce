package com.example.identity_service.messaging;

import java.util.Map;

/**
 * Sends one outbox envelope to the configured broker (API §2.1 "Message broker + Outbox").
 * Delivery is at least once: an event may be sent again if the relay's commit fails after sending, and consumers
 * de-duplicate by {@code eventId} (Inbox).
 */
public interface EventPublisher {

	/** False while no broker is set up: the relay then leaves every event PENDING. */
	boolean isConfigured();

	/** Throws if the broker did not accept the event. */
	void publish(String eventType, Map<String, Object> envelope);

}
