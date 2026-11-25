package com.example.payment_service.repository;

import com.example.payment_service.entity.Payment;
import com.example.payment_service.entity.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByCheckoutSessionId(UUID checkoutSessionId);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.paymentId = :paymentId")
    Optional<Payment> findByIdForUpdate(@Param("paymentId") UUID paymentId);

    List<Payment> findByStatus(PaymentStatus status);

    long countByStatus(PaymentStatus status);

    @Query("SELECT COALESCE(SUM(p.amountVnd), 0) FROM Payment p WHERE p.status = 'SUCCESS'")
    Long sumCollectedAmount();
}
