package com.example.payment_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaymentCollectionJob {
    private static final Logger logger = LoggerFactory.getLogger(PaymentCollectionJob.class);
    private final PaymentCollectionService collection;

    public PaymentCollectionJob(PaymentCollectionService collection) {
        this.collection = collection;
    }

    @Scheduled(initialDelayString = "${payment.collection.initial-delay:1000}",
            fixedDelayString = "${payment.collection.poll-interval:1000}")
    public void runOnce() {
        for (var checkoutId : collection.dueCheckouts()) {
            try {
                collection.process(checkoutId);
            } catch (RuntimeException failure) {
                logger.warn("Payment collection job did not finish for checkout {} ({})",
                        checkoutId, failure.getClass().getSimpleName());
            }
        }
    }
}
