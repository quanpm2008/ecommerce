package com.example.payment_service.repository;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PaymentCheckoutGuardRepository {
    private final EntityManager entities;

    public PaymentCheckoutGuardRepository(EntityManager entities) {
        this.entities = entities;
    }

    @Transactional
    public void retainForReview(String topic, int partition, long offset, String key, String value, String reason) {
        entities.createNativeQuery("""
                INSERT INTO payment_checkout_event_review
                    (topic, kafka_partition, kafka_offset, message_key, raw_envelope, reason)
                VALUES (:topic, :partition, :offset, :key, :value, :reason)
                ON CONFLICT (topic, kafka_partition, kafka_offset) DO NOTHING
                """).setParameter("topic", topic).setParameter("partition", partition)
                .setParameter("offset", offset).setParameter("key", key).setParameter("value", value)
                .setParameter("reason", reason).executeUpdate();
    }

    public boolean acceptClose(UUID eventId, UUID checkoutId, long version, Instant closedAt, Instant now) {
        int inserted = entities.createNativeQuery("""
                INSERT INTO payment_checkout_inbox(event_id, checkout_session_id, version, received_at)
                VALUES (:eventId, :checkoutId, :version, :now) ON CONFLICT (event_id) DO NOTHING
                """).setParameter("eventId", eventId).setParameter("checkoutId", checkoutId)
                .setParameter("version", version).setParameter("now", now).executeUpdate();
        if (inserted == 0) {
            return false;
        }
        return entities.createNativeQuery("""
                UPDATE payment_checkout_guard SET closed_at = :closedAt
                WHERE checkout_session_id = :checkoutId AND closed_at IS NULL
                """).setParameter("closedAt", closedAt).setParameter("checkoutId", checkoutId)
                .executeUpdate() == 1;
    }
}
