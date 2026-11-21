package com.example.payment_service.messaging;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.example.payment_service.service.PaymentOutcomeService;
import com.example.payment_service.service.PaymentOutcomeService.OutcomeConflict;
import com.example.payment_service.repository.PaymentCheckoutGuardRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.AcknowledgingMessageListener;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
@ConditionalOnProperty("payment.kafka.bootstrap-servers")
public class CheckoutClosedListener {
    private static final Logger logger = LoggerFactory.getLogger(CheckoutClosedListener.class);
    private final PaymentOutcomeService outcomes;
    private final PaymentCheckoutGuardRepository review;
    private final KafkaMessageListenerContainer<String, String> container;
    private final JsonMapper json = JsonMapper.builder().build();

    public CheckoutClosedListener(PaymentOutcomeService outcomes, PaymentCheckoutGuardRepository review,
            @Value("${payment.kafka.bootstrap-servers}") String bootstrap,
            @Value("${payment.kafka.checkout-events-topic:ecommerce.order.events.v1}") String topic,
            @Value("${payment.kafka.checkout-group:payment-service-checkout}") String group) {
        this.outcomes = outcomes;
        this.review = review;
        var factory = new DefaultKafkaConsumerFactory<String, String>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap,
                ConsumerConfig.GROUP_ID_CONFIG, group,
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
        var properties = new ContainerProperties(topic);
        properties.setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        properties.setMessageListener((AcknowledgingMessageListener<String, String>) this::receive);
        this.container = new KafkaMessageListenerContainer<>(factory, properties);
        this.container.setCommonErrorHandler(new DefaultErrorHandler(new FixedBackOff(1000L, FixedBackOff.UNLIMITED_ATTEMPTS)));
    }

    @PostConstruct
    void start() {
        container.start();
    }

    @PreDestroy
    void stop() {
        container.stop();
    }

    void receive(ConsumerRecord<String, String> record, Acknowledgment ack) {
        Close close;
        try {
            close = read(record);
        } catch (RuntimeException invalid) {
            retain(record, "INVALID_CLOSURE");
            ack.acknowledge();
            return;
        }
        if (close != null) {
            try {
                outcomes.checkoutClosed(close.eventId(), close.checkoutId(), close.customerId(), close.version(), close.closedAt());
            } catch (OutcomeConflict invalid) {
                retain(record, "CHECKOUT_MISMATCH");
            }
        }
        ack.acknowledge();
    }

    private void retain(ConsumerRecord<String, String> record, String reason) {
        review.retainForReview(record.topic(), record.partition(), record.offset(), record.key(), record.value(), reason);
        logger.warn("Checkout event retained for review at partition {} offset {} ({})",
                record.partition(), record.offset(), reason);
    }

    private Close read(ConsumerRecord<String, String> record) {
        JsonNode envelope = json.readTree(record.value());
        if (envelope == null || !envelope.isObject() || !envelope.path("eventType").isString()) {
            throw new IllegalArgumentException("Invalid event envelope");
        }
        if (!"checkout.closed".equals(envelope.path("eventType").asString())) {
            return null;
        }
        JsonNode payload = envelope.path("payload");
        JsonNode version = envelope.path("version");
        UUID checkoutId = UUID.fromString(payload.path("checkoutSessionId").asString());
        String reason = payload.path("closeReason").asString();
        if (!"order-service".equals(envelope.path("producer").asString())
                || !checkoutId.toString().equals(envelope.path("aggregateId").asString())
                || !checkoutId.toString().equals(record.key())
                || !version.isIntegralNumber() || !version.canConvertToLong() || version.asLong() < 1
                || !("PAYMENT_FAILED".equals(reason) || "EXPIRED".equals(reason))) {
            throw new IllegalArgumentException("Invalid closure envelope");
        }
        Instant.parse(envelope.path("occurredAt").asString());
        return new Close(UUID.fromString(envelope.path("eventId").asString()), checkoutId,
                UUID.fromString(payload.path("customerId").asString()), version.asLong(),
                Instant.parse(payload.path("closedAt").asString()));
    }

    private record Close(UUID eventId, UUID checkoutId, UUID customerId, long version, Instant closedAt) { }
}
