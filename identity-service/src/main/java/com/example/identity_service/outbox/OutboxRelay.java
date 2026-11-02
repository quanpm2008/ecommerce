package com.example.identity_service.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Publishes pending outbox events, oldest first, and marks them PUBLISHED. It stops at the first failure, so a later
 * event of the same user never overtakes an earlier one; the failed event is retried on the next run. Secrets an
 * event carried only for its consumer (the reset link) are blanked in the stored copy once published.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

	/** Payload fields removed from the stored envelope after publishing. */
	private static final Map<String, Set<String>> SECRETS = Map.of("password.reset_requested", Set.of("resetUrl"));

	private final OutboxEventRepository events;

	private final EventPublisher publisher;

	private final Clock clock;

	@Value("${identity.outbox.relay.batch-size:100}")
	private int batchSize;

	/** Returns how many events were published in this run. */
	@Transactional
	public int publishPending() {
		if (!publisher.isConfigured()) {
			return 0;
		}
		List<OutboxEvent> batch = events.lockPending(batchSize);
		int published = 0;
		for (OutboxEvent event : batch) {
			try {
				publisher.publish(event.getEventType(), event.getPayload());
			}
			catch (RuntimeException ex) {
				// Never log the payload: it may hold a secret.
				log.warn("Publishing event {} ({}) failed, will retry: {}", event.getEventId(), event.getEventType(),
						ex.getMessage());
				break;
			}
			event.markPublished(clock.instant(), withoutSecrets(event));
			published++;
		}
		return published;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> withoutSecrets(OutboxEvent event) {
		Set<String> secrets = SECRETS.get(event.getEventType());
		if (secrets == null || !(event.getPayload().get("payload") instanceof Map<?, ?> payload)) {
			return event.getPayload();
		}
		Map<String, Object> cleaned = new LinkedHashMap<>((Map<String, Object>) payload);
		secrets.forEach(field -> cleaned.computeIfPresent(field, (key, value) -> "[redacted]"));
		Map<String, Object> envelope = new LinkedHashMap<>(event.getPayload());
		envelope.put("payload", cleaned);
		return envelope;
	}

}
