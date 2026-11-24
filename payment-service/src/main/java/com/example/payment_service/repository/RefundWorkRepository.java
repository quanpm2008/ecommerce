package com.example.payment_service.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

@Repository
public class RefundWorkRepository {
    private final EntityManager entities;

    public RefundWorkRepository(EntityManager entities) {
        this.entities = entities;
    }

    @SuppressWarnings("unchecked")
    public List<UUID> due(Instant now) {
        return entities.createNativeQuery("""
                SELECT refund_id FROM payment_refund_work
                WHERE next_run_at <= :now AND (lease_until IS NULL OR lease_until <= :now)
                    AND review_required_at IS NULL
                ORDER BY next_run_at, refund_id LIMIT 32
                """).setParameter("now", now).getResultList();
    }

    public boolean claim(UUID refundId, UUID leaseId, Instant now, Instant leaseUntil) {
        return entities.createNativeQuery("""
                UPDATE payment_refund_work SET lease_id = :leaseId, lease_until = :leaseUntil
                WHERE refund_id = :refundId AND next_run_at <= :now AND (lease_until IS NULL OR lease_until <= :now)
                    AND review_required_at IS NULL
                """).setParameter("refundId", refundId).setParameter("leaseId", leaseId)
                .setParameter("now", now).setParameter("leaseUntil", leaseUntil).executeUpdate() == 1;
    }

    public boolean ownsLease(UUID refundId, UUID leaseId) {
        return entities.createNativeQuery("""
                SELECT 1 FROM payment_refund_work WHERE refund_id = :refundId AND lease_id = :leaseId
                """).setParameter("refundId", refundId).setParameter("leaseId", leaseId).getSingleResultOrNull() != null;
    }

    public boolean isHeldForReview(UUID refundId) {
        return entities.createNativeQuery("""
                SELECT 1 FROM payment_refund_work WHERE refund_id = :refundId AND review_required_at IS NOT NULL
                """).setParameter("refundId", refundId).getSingleResultOrNull() != null;
    }

    public boolean scheduleCustomerRetry(UUID refundId, Instant now) {
        return entities.createNativeQuery("""
                UPDATE payment_refund_work SET next_run_at = :now, lease_id = NULL, lease_until = NULL
                WHERE refund_id = :refundId AND review_required_at IS NULL
                """).setParameter("refundId", refundId).setParameter("now", now).executeUpdate() == 1;
    }

    public void holdForReview(UUID refundId, UUID attemptId, Instant now) {
        int held = entities.createNativeQuery("""
                UPDATE payment_refund_work
                SET review_required_at = :now, next_run_at = NULL, lease_id = NULL, lease_until = NULL
                WHERE refund_id = :refundId AND review_required_at IS NULL
                """).setParameter("refundId", refundId).setParameter("now", now).executeUpdate();
        if (held == 1) {
            logQuery(attemptId, now, "CONFLICT");
        }
    }

    public void release(UUID refundId, Instant nextRun) {
        if (nextRun == null) {
            entities.createNativeQuery("""
                    UPDATE payment_refund_work SET next_run_at = NULL, lease_id = NULL, lease_until = NULL
                    WHERE refund_id = :refundId
                    """).setParameter("refundId", refundId).executeUpdate();
        } else {
            entities.createNativeQuery("""
                    UPDATE payment_refund_work SET next_run_at = :nextRun, lease_id = NULL, lease_until = NULL
                    WHERE refund_id = :refundId
                    """).setParameter("refundId", refundId).setParameter("nextRun", nextRun).executeUpdate();
        }
    }

    public void logQuery(UUID attemptId, Instant now, String outcome) {
        entities.createNativeQuery("""
                INSERT INTO payment_refund_attempt_queries(query_id, attempt_id, checked_at, outcome)
                VALUES (:queryId, :attemptId, :now, :outcome)
                """).setParameter("queryId", UUID.randomUUID()).setParameter("attemptId", attemptId)
                .setParameter("now", now).setParameter("outcome", outcome).executeUpdate();
    }
}
