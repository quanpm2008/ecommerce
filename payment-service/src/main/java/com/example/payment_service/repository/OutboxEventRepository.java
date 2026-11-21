package com.example.payment_service.repository;

import com.example.payment_service.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Khóa và lấy danh sách các sự kiện PENDING cũ nhất để relay phát sang message broker (G-11).
     * Dùng native query với "FOR UPDATE SKIP LOCKED" để tránh tranh chấp giữa các worker/pod.
     */
    @Query(value = """
            SELECT event.* FROM payment_service.outbox_events event
            WHERE event.status = 'PENDING' AND NOT EXISTS (
                SELECT 1 FROM payment_service.outbox_events earlier
                WHERE earlier.aggregate_id = event.aggregate_id AND earlier.status = 'PENDING'
                  AND earlier.version < event.version
            )
            ORDER BY event.created_at ASC, event.event_id ASC
            LIMIT :limit 
            FOR UPDATE OF event SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockPending(@Param("limit") int limit);
}
