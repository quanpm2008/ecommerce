package com.example.identity_service.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

	List<OutboxEvent> findByAggregateIdAndEventType(UUID aggregateId, String eventType);

	@Query(value = "select coalesce(max(version), 0) + 1 from outbox_events where aggregate_id = :aggregateId",
			nativeQuery = true)
	long nextVersion(UUID aggregateId);

	/**
	 * The oldest pending events, locked; rows another relay instance holds are skipped, so instances never publish
	 * the same event at once.
	 */
	@Query(value = """
			select * from outbox_events where status = 'PENDING'
			order by created_at, aggregate_id, version limit :limit
			for update skip locked""", nativeQuery = true)
	List<OutboxEvent> lockPending(int limit);

}
