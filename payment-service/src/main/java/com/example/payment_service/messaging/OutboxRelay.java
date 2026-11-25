package com.example.payment_service.messaging;

import com.example.payment_service.entity.OutboxEvent;
import com.example.payment_service.repository.OutboxEventRepository;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository outboxEventRepository;
    private final EventPublisher eventPublisher;
    private final int batchSize;

    public OutboxRelay(OutboxEventRepository outboxEventRepository,
                       EventPublisher eventPublisher,
                       @Value("${payment.outbox.relay.batch-size:50}") int batchSize) {
        this.outboxEventRepository = outboxEventRepository;
        this.eventPublisher = eventPublisher;
        this.batchSize = batchSize;
    }

    @Transactional
    public int publishPending() {
        if (!eventPublisher.isConfigured()) {
            return 0;
        }

        List<OutboxEvent> pendingEvents = outboxEventRepository.lockPending(batchSize);
        if (pendingEvents.isEmpty()) {
            return 0;
        }

        int publishedCount = 0;
        Instant now = Instant.now();

        for (OutboxEvent event : pendingEvents) {
            try {
                eventPublisher.publish(event.getEventType(), event.getPayload());
                event.markPublished(now);
                outboxEventRepository.save(event);
                publishedCount++;
                log.info("[OutboxRelay] Successfully published event: id={}, type={}",
                        event.getEventId(), event.getEventType());
            } catch (Exception ex) {
                log.warn("[OutboxRelay] Failed to publish event id={} (type={}): {}. Will retry in next run.",
                        event.getEventId(), event.getEventType(), ex.getMessage());
                break;
            }
        }
        return publishedCount;
    }

}
