package com.example.payment_service.service;

import com.example.payment_service.entity.Refund;
import com.example.payment_service.entity.RefundReason;
import com.example.payment_service.entity.RefundScope;
import com.example.payment_service.entity.RefundStatus;
import com.example.payment_service.repository.RefundRepository;
import com.example.payment_service.repository.RefundWorkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class RefundReadService {
    private final RefundRepository refunds;
    private final RefundWorkRepository work;

    public RefundReadService(RefundRepository refunds, RefundWorkRepository work) {
        this.refunds = refunds;
        this.work = work;
    }

    @Transactional(readOnly = true)
    public List<View> lookup(List<UUID> orderIds, List<UUID> checkoutIds) {
        var matches = orderIds.isEmpty() ? refunds.findFullCheckoutRefunds(checkoutIds) : refunds.findByOrderIdIn(orderIds);
        return matches.stream().map(this::view).toList();
    }

    View view(Refund refund) {
        return view(refund, true);
    }

    View view(Refund refund, boolean hasPermission) {
        boolean canRetry = hasPermission && refund.getStatus() == RefundStatus.FAILED && refund.isCustomerRetryRequired()
                && !work.isHeldForReview(refund.getRefundId());
        return new View(refund.getRefundId(), refund.getPaymentId(), refund.getOrderId(), refund.getScope(), refund.getAmountVnd(),
                refund.getReason(), refund.getStatus(), refund.getRetryCycle(), refund.getRetryCount(),
                refund.isCustomerRetryRequired(), canRetry, refund.getProviderRefundId(), refund.getCreatedAt(),
                refund.getCompletedAt(), true);
    }

    public record View(UUID refundId, UUID paymentId, UUID orderId, RefundScope scope, long amountVnd, RefundReason reason,
            RefundStatus status, int retryCycle, int retryCount, boolean customerRetryRequired, boolean canRetryRefund,
            String providerRefundId, Instant createdAt, Instant completedAt, boolean isSimulated) { }
}
