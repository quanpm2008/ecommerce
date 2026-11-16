package com.example.payment_service.repository;

import com.example.payment_service.entity.Refund;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefundRepository extends JpaRepository<Refund, UUID> {

    List<Refund> findByPaymentId(UUID paymentId);

    Optional<Refund> findByOrderId(UUID orderId);

    Optional<Refund> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT COALESCE(SUM(r.amountVnd), 0) FROM Refund r WHERE r.paymentId = :paymentId AND r.status IN ('PENDING', 'SUCCESS')")
    Long sumActiveRefundAmountByPaymentId(@Param("paymentId") UUID paymentId);

    List<Refund> findByNeedsManualReviewTrue();

    Page<Refund> findByNeedsManualReviewTrue(Pageable pageable);
}
