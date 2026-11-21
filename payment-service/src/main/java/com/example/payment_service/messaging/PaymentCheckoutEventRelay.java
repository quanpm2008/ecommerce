package com.example.payment_service.messaging;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.example.payment_service.entity.OutboxEvent;
import com.example.payment_service.repository.OutboxEventRepository;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@Component
@ConditionalOnProperty("payment.kafka.bootstrap-servers")
public class PaymentCheckoutEventRelay {
    private static final Logger logger = LoggerFactory.getLogger(PaymentCheckoutEventRelay.class);
    private final OutboxEventRepository outbox;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final KafkaProducer<String, String> producer;
    private final String topic;
    private final JsonMapper json = JsonMapper.builder().build();

    public PaymentCheckoutEventRelay(OutboxEventRepository outbox, Clock clock, PlatformTransactionManager manager,
            @Value("${payment.kafka.bootstrap-servers}") String bootstrap,
            @Value("${payment.kafka.result-topic:ecommerce.payment.events.v1}") String topic) {
        this.outbox = outbox;
        this.clock = clock;
        this.transactions = new TransactionTemplate(manager);
        this.topic = topic;
        this.producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap,
                ProducerConfig.ACKS_CONFIG, "all",
                ProducerConfig.MAX_BLOCK_MS_CONFIG, 3000,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class));
    }

    @Scheduled(initialDelayString = "${payment.kafka.relay-initial-delay:1000}",
            fixedDelayString = "${payment.kafka.relay-interval:1000}")
    public void runOnce() {
        transactions.executeWithoutResult(transaction -> {
            for (OutboxEvent event : outbox.lockPending(16)) {
                if (!publish(event)) {
                    return;
                }
                event.markPublished(clock.instant());
            }
        });
    }

    private boolean publish(OutboxEvent event) {
        String aggregate = event.getAggregateId().toString();
        try {
            producer.send(new ProducerRecord<>(topic, aggregate, json.writeValueAsString(Map.of(
                    "eventId", event.getEventId().toString(), "eventType", event.getEventType(),
                    "occurredAt", event.getCreatedAt().toString(), "producer", "payment-service",
                    "aggregateId", aggregate, "version", event.getVersion(),
                    "actor", new SystemActor(null, "SERVICE", null), "payload", event.getPayload())))).get(3, TimeUnit.SECONDS);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception unavailable) {
            logger.warn("Payment outbox publication will retry event {} ({})",
                    event.getEventId(), unavailable.getClass().getSimpleName());
            return false;
        }
    }

    @PreDestroy
    void close() {
        producer.close(Duration.ofSeconds(3));
    }

    private record SystemActor(String userId, String kind, String context) { }
}
