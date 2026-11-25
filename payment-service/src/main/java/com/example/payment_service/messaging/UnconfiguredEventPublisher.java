package com.example.payment_service.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnMissingBean(name = "kafkaEventPublisher")
public class UnconfiguredEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(UnconfiguredEventPublisher.class);

    public UnconfiguredEventPublisher() {
        log.warn("[PaymentMessaging] No message broker (Kafka) configured: outbox events will remain PENDING in outbox_events table");
    }

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public void publish(String eventType, Map<String, Object> payload) {
        log.debug("[PaymentMessaging] Skipping publish for eventType={} because broker is not configured", eventType);
        throw new IllegalStateException("No message broker configured for payment-service");
    }

}
