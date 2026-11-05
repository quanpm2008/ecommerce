package com.example.identity_service.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Records an event in the caller's transaction, so it is stored exactly when the change commits; {@link OutboxRelay}
 * publishes it afterwards. The envelope's {@code version} numbers the aggregate's events 1, 2, 3...; two transactions
 * writing for the same user at once cannot both commit a number (unique constraint), and admin actions lock the user
 * row anyway. Never log the payload: some events carry secrets (password reset links).
 */
@Component
@RequiredArgsConstructor
public class OutboxWriter {

	static final String PRODUCER = "identity-service";

	private final OutboxEventRepository events;

	private final Clock clock;

	@Transactional(propagation = Propagation.MANDATORY)
	public UUID write(String eventType, UUID aggregateId, EventActor actor, Map<String, Object> payload) {
		UUID eventId = UUID.randomUUID();
		Instant now = clock.instant();
		long version = events.nextVersion(aggregateId);
		Map<String, Object> envelope = new LinkedHashMap<>();
		envelope.put("eventId", eventId.toString());
		envelope.put("eventType", eventType);
		envelope.put("occurredAt", now.toString());
		envelope.put("producer", PRODUCER);
		envelope.put("aggregateId", aggregateId.toString());
		envelope.put("version", version);
		envelope.put("actor", actor);
		envelope.put("payload", payload);
		// Flushed now, so a second event for the same aggregate in this transaction numbers after this one.
		events.saveAndFlush(new OutboxEvent(eventId, eventType, aggregateId, version, envelope, now));
		return eventId;
	}

}
