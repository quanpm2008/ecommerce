package com.example.payment_service.repository;

import com.example.payment_service.entity.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByCheckoutSessionId(UUID checkoutSessionId);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    //Khóa Payment (SELECT FOR UPDATE) để đảm bảo an toàn đồng thời khi tạo Refund
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.paymentId = :paymentId")
    Optional<Payment> findByIdForUpdate(@Param("paymentId") UUID paymentId);
}
