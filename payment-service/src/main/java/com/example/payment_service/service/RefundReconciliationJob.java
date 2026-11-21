package com.example.payment_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty("payment.refund.query-interval")
public class RefundReconciliationJob {
    private static final Logger logger = LoggerFactory.getLogger(RefundReconciliationJob.class);
    private final FullCheckoutRefundService refunds;

    public RefundReconciliationJob(FullCheckoutRefundService refunds) {
        this.refunds = refunds;
    }

    @Scheduled(initialDelayString = "${payment.refund.initial-delay:1000}",
            fixedDelayString = "${payment.refund.poll-interval:1000}")
    public void runOnce() {
        for (var refundId : refunds.dueRefunds()) {
            try {
                refunds.process(refundId);
            } catch (RuntimeException failure) {
                logger.warn("Refund reconciliation will retry refund {} ({})", refundId, failure.getClass().getSimpleName());
            }
        }
    }
}
