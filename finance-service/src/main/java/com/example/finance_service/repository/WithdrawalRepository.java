package com.example.finance_service.repository;

import com.example.finance_service.entity.Withdrawal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface WithdrawalRepository extends JpaRepository<Withdrawal, UUID>, JpaSpecificationExecutor<Withdrawal> {
    Optional<Withdrawal> findBySellerIdAndIdempotencyKey(UUID sellerId, String idempotencyKey);
    Optional<Withdrawal> findByWithdrawalIdAndSellerId(UUID withdrawalId, UUID sellerId);
}
