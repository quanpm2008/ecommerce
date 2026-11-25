package com.example.finance_service.repository;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class WithdrawalOutboxRepository {
    private final EntityManager entities;

    public WithdrawalOutboxRepository(EntityManager entities) {
        this.entities = entities;
    }

    public void enqueue(UUID withdrawalId, Instant occurredAt) {
        entities.createNativeQuery("""
                insert into finance.withdrawal_outbox (event_id, withdrawal_id, occurred_at)
                values (:id, :id, :at)
                """).setParameter("id", withdrawalId).setParameter("at", occurredAt).executeUpdate();
    }

    @Transactional(readOnly = true)
    public List<Event> pending() {
        var rows = entities.createNativeQuery("""
                select e.event_id, w.withdrawal_id, w.seller_id, w.amount_vnd, w.available_after_vnd, e.occurred_at
                from finance.withdrawal_outbox e join finance.withdrawal w using (withdrawal_id)
                where e.published_at is null order by e.occurred_at, e.event_id limit 32
                """).getResultList();
        return rows.stream().map(Object[].class::cast).map(raw -> {
            Object[] row = (Object[]) raw;
            Instant occurredAt = row[5] instanceof java.time.OffsetDateTime value ? value.toInstant() : (Instant) row[5];
            return new Event((UUID) row[0], (UUID) row[1], (UUID) row[2], ((Number) row[3]).longValue(),
                    ((Number) row[4]).longValue(), occurredAt);
        }).toList();
    }

    @Transactional
    public void markPublished(UUID id) {
        entities.createNativeQuery("""
                update finance.withdrawal_outbox set published_at=statement_timestamp()
                where event_id=:id and published_at is null
                """).setParameter("id", id).executeUpdate();
    }

    public record Event(UUID eventId, UUID withdrawalId, UUID sellerId, long amountVnd, long availableAfterVnd,
                        Instant occurredAt) {}
}
