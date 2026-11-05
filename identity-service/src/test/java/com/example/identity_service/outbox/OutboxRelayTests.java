package com.example.identity_service.outbox;

import com.example.identity_service.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link OutboxRelay} with a recording publisher (API §7.2, checklist section 9): order, per-aggregate versions,
 * stop on failure, secrets blanked after publishing. The scheduler is off; tests run the relay themselves.
 */
@SpringBootTest(properties = { "eureka.client.enabled=false", "identity.outbox.relay.enabled=false" })
@Import({ TestcontainersConfiguration.class, OutboxRelayTests.Publisher.class })
class OutboxRelayTests {

	@Autowired
	private OutboxRelay relay;

	@Autowired
	private OutboxWriter outbox;

	@Autowired
	private RecordingPublisher publisher;

	@Autowired
	private TransactionTemplate tx;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void reset() {
		publisher.reset();
		drain();
		publisher.reset();
	}

	@Test
	void publishesInOrderWithIncreasingVersionsAndMarksPublished() {
		UUID user = UUID.randomUUID();
		UUID first = write("user.roles_changed", user, Map.of("userId", user));
		UUID second = write("user.disabled", user, Map.of("userId", user));

		assertThat(relay.publishPending()).isEqualTo(2);

		assertThat(publisher.sent).extracting(envelope -> envelope.get("eventId"))
				.containsExactly(first.toString(), second.toString());
		assertThat(publisher.sent).extracting(envelope -> ((Number) envelope.get("version")).longValue())
				.containsExactly(1L, 2L);
		assertThat(jdbc.queryForObject("""
				select count(*) from outbox_events
				where aggregate_id = ? and status = 'PUBLISHED' and published_at is not null""", Integer.class, user))
				.isEqualTo(2);
		assertThat(relay.publishPending()).isZero();
	}

	@Test
	void aFailureStopsTheRunSoLaterEventsNeverOvertake() {
		UUID user = UUID.randomUUID();
		UUID first = write("seller.banned", user, Map.of("userId", user));
		UUID failing = write("seller.unbanned", user, Map.of("userId", user));
		UUID later = write("seller.banned", user, Map.of("userId", user));
		publisher.failOn.add(failing.toString());

		assertThat(relay.publishPending()).isEqualTo(1);
		assertThat(status(first)).isEqualTo("PUBLISHED");
		assertThat(status(failing)).isEqualTo("PENDING");
		assertThat(status(later)).isEqualTo("PENDING");

		publisher.failOn.clear();
		assertThat(relay.publishPending()).isEqualTo(2);
		assertThat(status(later)).isEqualTo("PUBLISHED");
	}

	@Test
	void resetLinkIsSentButNotKeptAfterPublishing() {
		UUID user = UUID.randomUUID();
		UUID event = write("password.reset_requested", user,
				Map.of("userId", user, "resetUrl", "http://fe/reset?token=secret", "expiresAt", "2026-10-31T00:30:00Z"));

		relay.publishPending();

		assertThat(publisher.sent.getFirst()).extracting(envelope -> ((Map<?, ?>) envelope.get("payload")).get("resetUrl"))
				.isEqualTo("http://fe/reset?token=secret");
		assertThat(jdbc.queryForObject("select payload -> 'payload' ->> 'resetUrl' from outbox_events where event_id = ?",
				String.class, event)).isEqualTo("[redacted]");
		assertThat(jdbc.queryForObject("select payload -> 'payload' ->> 'expiresAt' from outbox_events where event_id = ?",
				String.class, event)).isEqualTo("2026-10-31T00:30:00Z");
	}

	@Test
	void withoutABrokerEverythingStaysPending() {
		UUID event = write("user.enabled", UUID.randomUUID(), Map.of("reason", "x"));
		publisher.configured = false;

		assertThat(relay.publishPending()).isZero();
		assertThat(status(event)).isEqualTo("PENDING");
		assertThat(publisher.sent).isEmpty();
	}

	private UUID write(String eventType, UUID aggregateId, Map<String, Object> payload) {
		return tx.execute(status -> outbox.write(eventType, aggregateId,
				EventActor.user(aggregateId, EventActor.Context.ACCOUNT), payload));
	}

	private String status(UUID eventId) {
		return jdbc.queryForObject("select status from outbox_events where event_id = ?", String.class, eventId);
	}

	private void drain() {
		while (relay.publishPending() > 0) {
			// publish whatever earlier tests left pending
		}
	}

	static class RecordingPublisher implements EventPublisher {

		final List<Map<String, Object>> sent = new CopyOnWriteArrayList<>();

		final Set<String> failOn = ConcurrentHashMap.newKeySet();

		volatile boolean configured = true;

		void reset() {
			sent.clear();
			failOn.clear();
			configured = true;
		}

		@Override
		public boolean isConfigured() {
			return configured;
		}

		@Override
		public void publish(String eventType, Map<String, Object> envelope) {
			if (failOn.contains(String.valueOf(envelope.get("eventId")))) {
				throw new IllegalStateException("broker down");
			}
			sent.add(envelope);
		}

	}

	@TestConfiguration(proxyBeanMethods = false)
	static class Publisher {

		@Bean
		@Primary
		RecordingPublisher recordingPublisher() {
			return new RecordingPublisher();
		}

	}

}
