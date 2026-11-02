package com.example.identity_service.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Runs {@link OutboxRelay} every {@code identity.outbox.relay.interval}. Off with
 * {@code identity.outbox.relay.enabled=false} (tests drive the relay themselves).
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "identity.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
class OutboxRelayScheduling {

	private final OutboxRelay relay;

	@Scheduled(fixedDelayString = "${identity.outbox.relay.interval:PT2S}")
	void publishPending() {
		relay.publishPending();
	}

}
