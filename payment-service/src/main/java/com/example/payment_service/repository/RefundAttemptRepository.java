package com.example.payment_service.repository;

import java.util.Optional;
import java.util.UUID;

import com.example.payment_service.entity.RefundAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundAttemptRepository extends JpaRepository<RefundAttempt, UUID> {
    Optional<RefundAttempt> findFirstByRefundIdOrderByRetryCycleDescAttemptNumberDesc(UUID refundId);

    Optional<RefundAttempt> findByAttemptIdAndRefundId(UUID attemptId, UUID refundId);

    boolean existsByProviderRefundIdAndAttemptIdNot(String providerRefundId, UUID attemptId);
}
