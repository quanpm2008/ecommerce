package com.example.payment_service.repository;

import com.example.payment_service.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    // Earlier unpublished versions block later versions even while another relay owns their lease.
    @Query(value = """
            WITH candidate AS (
                SELECT event.event_id FROM payment_service.outbox_events event
                WHERE event.status = 'PENDING'
                  AND (event.lease_until IS NULL OR event.lease_until <= statement_timestamp())
                  AND NOT EXISTS (
                      SELECT 1 FROM payment_service.outbox_events earlier
                      WHERE earlier.aggregate_id = event.aggregate_id AND earlier.status = 'PENDING'
                        AND earlier.version < event.version
                  )
                ORDER BY event.created_at, event.event_id
                LIMIT 1 FOR UPDATE OF event SKIP LOCKED
            )
            UPDATE payment_service.outbox_events event
            SET lease_id = :leaseId, lease_until = statement_timestamp() + interval '10 seconds'
            FROM candidate WHERE candidate.event_id = event.event_id
            RETURNING event.*
            """, nativeQuery = true)
    Optional<OutboxEvent> claim(@Param("leaseId") UUID leaseId);

    @Modifying
    @Query(value = """
            UPDATE payment_service.outbox_events
            SET status = 'PUBLISHED', published_at = :at, lease_id = NULL, lease_until = NULL
            WHERE event_id = :eventId AND lease_id = :leaseId
            """, nativeQuery = true)
    void acknowledge(UUID eventId, UUID leaseId, Instant at);
}
