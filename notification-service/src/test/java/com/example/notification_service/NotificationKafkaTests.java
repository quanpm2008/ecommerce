package com.example.notification_service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false", "spring.cloud.discovery.enabled=false", "notification.retention.enabled=false",
        "notification.events.kafka.enabled=true",
        "notification.internal.service-token=notification-outgoing-test-0123456789abcdef",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer"})
@Import(NotificationKafkaTests.Broker.class)
@ExtendWith(OutputCaptureExtension.class)
class NotificationKafkaTests extends IntegrationTestSupport {
    @Autowired KafkaTemplate<String,String> kafka;
    @Autowired JsonMapper json;

    @Test
    void brokerRedeliveryCommitsOneInAppNotificationAndDurableInbox() throws Exception {
        UUID seller = UUID.randomUUID();
        RECIPIENTS.put(seller, "SELLER");
        String eventId = UUID.randomUUID().toString();
        String body = json.writeValueAsString(Map.of("eventId", eventId, "eventType", "seller.approved",
                "producer", "identity-service", "aggregateId", seller, "version", 1,
                "occurredAt", NOW.toString(), "payload", Map.of("userId", seller, "sellerId", seller)));
        kafka.send("seller.approved", seller.toString(), body).get(30, TimeUnit.SECONDS);
        kafka.send("seller.approved", seller.toString(), body).get(30, TimeUnit.SECONDS);
        long deadline = System.nanoTime() + Duration.ofSeconds(45).toNanos();
        while (System.nanoTime() < deadline && jdbc.queryForObject(
                "SELECT count(*) FROM notification_inbox WHERE event_id=? AND state='APPLIED'", Long.class, eventId) == 0) {
            Thread.sleep(100);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE recipient_user_id=?", Long.class, seller)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT state FROM notification_inbox WHERE event_id=?", String.class, eventId)).isEqualTo("APPLIED");
        assertThat(jdbc.queryForObject("SELECT content FROM notifications WHERE recipient_user_id=?", String.class, seller))
                .contains("đăng nhập tài khoản người bán");
    }

    @Test
    void identityFailureRetainsMessageUntilRecoveryWithoutLoggingPayload(CapturedOutput output) throws Exception {
        UUID seller = UUID.randomUUID();
        RECIPIENTS.put(seller, "UNAVAILABLE_ACCOUNT_TYPE");
        String eventId = UUID.randomUUID().toString();
        String marker = "do-not-log-" + UUID.randomUUID();
        String body = json.writeValueAsString(Map.of("eventId", eventId, "eventType", "seller.approved",
                "producer", "identity-service", "aggregateId", seller, "version", 1,
                "occurredAt", NOW.toString(),
                "payload", Map.of("userId", seller, "sellerId", seller, "reason", marker)));
        kafka.send("seller.approved", seller.toString(), body).get(30, TimeUnit.SECONDS);
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline && !output.getAll().contains("Notification delivery awaiting retry")) {
            Thread.sleep(100);
        }
        assertThat(output.getAll()).contains("Notification delivery awaiting retry").doesNotContain(marker);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE recipient_user_id=?",
                Long.class, seller)).isZero();
        RECIPIENTS.put(seller, "SELLER");
        deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline && jdbc.queryForObject(
                "SELECT count(*) FROM notification_inbox WHERE event_id=? AND state='APPLIED'", Long.class, eventId) == 0) {
            Thread.sleep(100);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE recipient_user_id=?",
                Long.class, seller)).isEqualTo(1);
        assertThat(output.getAll()).doesNotContain(marker);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Broker {
        @Bean @ServiceConnection
        KafkaContainer kafkaContainer() { return new KafkaContainer("apache/kafka-native:3.8.0"); }
    }
}
