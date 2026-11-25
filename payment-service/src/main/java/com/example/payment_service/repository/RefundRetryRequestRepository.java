package com.example.payment_service.repository;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class RefundRetryRequestRepository {
    private final EntityManager entities;

    public RefundRetryRequestRepository(EntityManager entities) {
        this.entities = entities;
    }

    public boolean begin(UUID customer, UUID key, UUID refund, int cycle) {
        return entities.createNativeQuery("""
                INSERT INTO payment_refund_retry_request(customer_id, idempotency_key, refund_id, expected_retry_cycle)
                VALUES(:customer, :key, :refund, :cycle) ON CONFLICT DO NOTHING
                """).setParameter("customer", customer).setParameter("key", key)
                .setParameter("refund", refund).setParameter("cycle", cycle).executeUpdate() == 1;
    }

    public Accepted find(UUID customer, UUID key) {
        var row = (Object[]) entities.createNativeQuery("""
                SELECT refund_id, expected_retry_cycle, CAST(response AS text)
                FROM payment_refund_retry_request WHERE customer_id = :customer AND idempotency_key = :key
                """).setParameter("customer", customer).setParameter("key", key).getSingleResult();
        return new Accepted((UUID) row[0], (Integer) row[1], (String) row[2]);
    }

    public void complete(UUID customer, UUID key, String response) {
        entities.createNativeQuery("""
                UPDATE payment_refund_retry_request SET response = CAST(:response AS jsonb)
                WHERE customer_id = :customer AND idempotency_key = :key
                """).setParameter("customer", customer).setParameter("key", key)
                .setParameter("response", response).executeUpdate();
    }

    public record Accepted(UUID refundId, int expectedCycle, String response) {}
}
