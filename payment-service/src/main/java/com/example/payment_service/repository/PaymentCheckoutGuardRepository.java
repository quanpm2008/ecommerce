package com.example.payment_service.repository;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentCheckoutGuardRepository {
    private final EntityManager entities;

    public PaymentCheckoutGuardRepository(EntityManager entities) {
        this.entities = entities;
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
