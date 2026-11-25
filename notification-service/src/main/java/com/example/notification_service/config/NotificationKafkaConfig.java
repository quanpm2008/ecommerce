package com.example.notification_service.config;

import com.example.notification_service.service.NotificationEventHandler;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@ConditionalOnProperty(name = "notification.events.kafka.enabled", havingValue = "true")
public class NotificationKafkaConfig {
    private static final Logger log = LoggerFactory.getLogger(NotificationKafkaConfig.class);
    private final NotificationEventHandler handler;

    public NotificationKafkaConfig(NotificationEventHandler handler) {
        this.handler = handler;
    }

    @KafkaListener(topics = {"checkout.paid", "checkout.closed", "order.delivered", "order.cancelled",
            "order.confirmed", "refund.succeeded", "refund.failed", "funds.available", "withdrawal.completed",
            "seller.registered", "seller.approved", "seller.rejected", "seller.banned", "seller.unbanned",
            "customer.banned", "customer.unbanned", "user.disabled", "user.enabled", "product.removed"})
    void receive(ConsumerRecord<String, String> record) {
        handler.handle(record.topic(), record.value());
    }

    @Bean
    CommonErrorHandler notificationKafkaErrorHandler() {
        // Retry transient failures indefinitely: never acknowledge an uncommitted notification or log its body.
        DefaultErrorHandler errors = new DefaultErrorHandler(new FixedBackOff(5000, FixedBackOff.UNLIMITED_ATTEMPTS));
        errors.setAckAfterHandle(false);
        // Retain/retry the record instead of throwing a seek exception that can include the raw payload in logs.
        errors.setSeekAfterError(false);
        errors.setRetryListeners((record, exception, attempt) ->
                log.warn("Notification delivery awaiting retry: topic={}, partition={}, offset={}, attempt={}",
                        record.topic(), record.partition(), record.offset(), attempt));
        return errors;
    }
}
