package com.example.payment_service.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.hibernate.query.TypedParameterValue;
import org.hibernate.type.StandardBasicTypes;
import org.springframework.stereotype.Repository;

import com.example.payment_service.client.ProviderContracts.PaymentQueryResponse;

import jakarta.persistence.EntityManager;

@Repository
public class PaymentCollectionWorkRepository {
    private final EntityManager entities;

    public PaymentCollectionWorkRepository(EntityManager entities) {
        this.entities = entities;
    }

    // Call under the local transaction using the same checkout key for create, webhook and close.
    public Guard lockCheckout(UUID checkoutId) {
        entities.createNativeQuery("""
                INSERT INTO payment_checkout_guard(checkout_session_id) VALUES (:checkoutId)
                ON CONFLICT (checkout_session_id) DO NOTHING
                """).setParameter("checkoutId", checkoutId).executeUpdate();
        Object closedAt = entities.createNativeQuery("""
                SELECT closed_at FROM payment_checkout_guard WHERE checkout_session_id = :checkoutId FOR UPDATE
                """).setParameter("checkoutId", checkoutId).getSingleResult();
        return new Guard(instant(closedAt));
    }

    public Instant closedAt(UUID checkoutId) {
        Object closedAt = entities.createNativeQuery("""
                SELECT closed_at FROM payment_checkout_guard WHERE checkout_session_id = :checkoutId
                """).setParameter("checkoutId", checkoutId).getSingleResultOrNull();
        return instant(closedAt);
    }

    public void schedule(UUID paymentId, Instant firstRun) {
        entities.createNativeQuery("""
                INSERT INTO payment_collection_work(payment_id, next_run_at) VALUES (:paymentId, :firstRun)
                """).setParameter("paymentId", paymentId).setParameter("firstRun", firstRun).executeUpdate();
    }

    public Optional<Work> find(UUID paymentId) {
        Object[] row = (Object[]) entities.createNativeQuery("""
                SELECT next_run_at, query_attempts, dispatch_started, lease_id, lease_until
                FROM payment_collection_work WHERE payment_id = :paymentId
                """).setParameter("paymentId", paymentId).getSingleResultOrNull();
        return Optional.ofNullable(row).map(result -> new Work(instant(result[0]),
                (Integer) result[1], (Boolean) result[2], (UUID) result[3], instant(result[4])));
    }

    @SuppressWarnings("unchecked")
    public List<UUID> dueCheckouts(Instant now) {
        return entities.createNativeQuery("""
                SELECT p.checkout_session_id FROM payment_collection_work w JOIN payments p USING (payment_id)
                JOIN payment_checkout_guard g USING (checkout_session_id)
                WHERE w.next_run_at <= :now AND (w.lease_until IS NULL OR w.lease_until <= :now)
                  AND g.closed_at IS NULL AND p.status = 'PENDING'
                ORDER BY w.next_run_at, p.payment_id LIMIT 32
                """).setParameter("now", now).getResultList();
    }

    public void claim(UUID paymentId, UUID leaseId, Instant leaseUntil) {
        entities.createNativeQuery("""
                UPDATE payment_collection_work SET dispatch_started = true, lease_id = :leaseId, lease_until = :leaseUntil
                WHERE payment_id = :paymentId
                """).setParameter("leaseId", leaseId).setParameter("leaseUntil", leaseUntil)
                .setParameter("paymentId", paymentId).executeUpdate();
    }

    public void retry(UUID paymentId, UUID leaseId, int queryAttempts, Instant nextRun) {
        entities.createNativeQuery("""
                UPDATE payment_collection_work
                SET next_run_at = :nextRun, query_attempts = :queryAttempts, lease_id = NULL, lease_until = NULL
                WHERE payment_id = :paymentId AND lease_id = :leaseId
                """).setParameter("nextRun", nextRun).setParameter("queryAttempts", queryAttempts)
                .setParameter("paymentId", paymentId).setParameter("leaseId", leaseId).executeUpdate();
    }

    public void stop(UUID paymentId) {
        entities.createNativeQuery("""
                UPDATE payment_collection_work SET next_run_at = NULL, lease_id = NULL, lease_until = NULL
                WHERE payment_id = :paymentId
                """).setParameter("paymentId", paymentId).executeUpdate();
    }

    public void observe(UUID paymentId, PaymentQueryResponse result, Instant observedAt) {
        entities.createNativeQuery("""
                INSERT INTO payment_collection_observation (
                    payment_id, provider_transaction_id, amount_vnd, currency, status,
                    completed_at, error_code, observed_at
                ) VALUES (:paymentId, :providerRef, :amount, :currency, :status, :completedAt, :errorCode, :observedAt)
                ON CONFLICT (payment_id) DO NOTHING
                """).setParameter("paymentId", paymentId).setParameter("providerRef", result.providerTransactionId())
                .setParameter("amount", result.amountVnd()).setParameter("currency", result.currency())
                .setParameter("status", result.status().name()).setParameter("completedAt", result.completedAt())
                .setParameter("errorCode", new TypedParameterValue<>(StandardBasicTypes.STRING, result.errorCode()))
                .setParameter("observedAt", observedAt).executeUpdate();
    }

    private static Instant instant(Object value) {
        return switch (value) {
            case null -> null;
            case Instant time -> time;
            case OffsetDateTime time -> time.toInstant();
            case Timestamp time -> time.toInstant();
            default -> throw new IllegalStateException("Unexpected timestamp type " + value.getClass().getName());
        };
    }

    public record Guard(Instant closedAt) {
        public boolean isClosed() {
            return closedAt != null;
        }
    }

    public record Work(Instant nextRun, int queryAttempts, boolean dispatchStarted,
            UUID leaseId, Instant leaseUntil) {
        public boolean isDue(Instant now) {
            return nextRun != null && !nextRun.isAfter(now)
                    && (leaseUntil == null || !leaseUntil.isAfter(now));
        }
    }
}
