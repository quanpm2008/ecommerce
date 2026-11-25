package com.example.finance_service.repository;

import com.example.finance_service.entity.Settlement;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SettlementRepository extends JpaRepository<Settlement, UUID>, JpaSpecificationExecutor<Settlement> {
	List<Settlement> findByOrderIdIn(Collection<UUID> orderIds);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Settlement> findByOrderId(UUID orderId);
}
