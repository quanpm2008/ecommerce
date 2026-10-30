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
 * Records an event in the caller's transaction, so it is stored exactly when the change commits. Publishing is a
 * separate job (step 9). Never log the payload: some events carry secrets (password reset links).
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
		Map<String, Object> envelope = new LinkedHashMap<>();
		envelope.put("eventId", eventId.toString());
		envelope.put("eventType", eventType);
		envelope.put("occurredAt", now.toString());
		envelope.put("producer", PRODUCER);
		envelope.put("aggregateId", aggregateId.toString());
		envelope.put("actor", actor);
		envelope.put("payload", payload);
		events.save(new OutboxEvent(eventId, eventType, aggregateId, envelope, now));
		return eventId;
	}

}
