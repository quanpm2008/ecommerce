package com.example.payment_service.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "payment.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelayScheduling {

    private final OutboxRelay outboxRelay;

    public OutboxRelayScheduling(OutboxRelay outboxRelay) {
        this.outboxRelay = outboxRelay;
    }

    @Scheduled(fixedDelayString = "${payment.outbox.relay.interval:PT2S}")
    public void schedulePublishPending() {
        outboxRelay.publishPending();
    }
}
