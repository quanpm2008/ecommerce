package com.example.payment_service.service;

import com.example.payment_service.entity.RefundStatus;
import com.example.payment_service.repository.PaymentRepository;
import com.example.payment_service.repository.RefundRepository;
import com.example.payment_service.repository.RefundRetryRequestRepository;
import com.example.payment_service.repository.RefundWorkRepository;
import com.example.payment_service.security.RefundCustomerAuthorization.Actor;
import com.example.payment_service.security.RefundCustomerAuthorization.Rejected;
import com.example.payment_service.service.RefundReadService.View;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class CustomerRefundService {
    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final RefundWorkRepository work;
    private final RefundRetryRequestRepository requests;
    private final RefundReadService views;
    private final JsonMapper json;
    private final Clock clock;

    public CustomerRefundService(PaymentRepository payments, RefundRepository refunds, RefundWorkRepository work,
            RefundRetryRequestRepository requests, RefundReadService views, JsonMapper json, Clock clock) {
        this.payments = payments;
        this.refunds = refunds;
        this.work = work;
        this.requests = requests;
        this.views = views;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<View> list(UUID checkout, Actor actor) {
        var payment = payments.findByCheckoutSessionId(checkout)
                .filter(candidate -> candidate.getCustomerId().equals(actor.userId())).orElseThrow(CustomerRefundService::missing);
        var obligations = refunds.findByPaymentId(payment.getPaymentId());
        if (actor.limited() && obligations.stream().noneMatch(refund -> refund.getStatus() != RefundStatus.SUCCESS)) {
            throw new Rejected(403, "ACCOUNT_BANNED");
        }
        return obligations.stream().filter(refund -> !actor.limited() || refund.getStatus() != RefundStatus.SUCCESS)
                .sorted(Comparator.comparing(com.example.payment_service.entity.Refund::getCreatedAt)
                        .thenComparing(com.example.payment_service.entity.Refund::getRefundId))
                .map(refund -> views.view(refund, actor.canRetry())).toList();
    }

    @Transactional
    public View retry(UUID refundId, UUID key, int expectedCycle, Actor actor) {
        if (!actor.canRetry()) {
            throw new Rejected(403, "FORBIDDEN");
        }
        var refund = refunds.findByIdForUpdate(refundId).orElseThrow(CustomerRefundService::missing);
        var payment = payments.findById(refund.getPaymentId())
                .filter(candidate -> candidate.getCustomerId().equals(actor.userId())).orElseThrow(CustomerRefundService::missing);
        if (!requests.begin(actor.userId(), key, refundId, expectedCycle)) {
            var previous = requests.find(actor.userId(), key);
            if (!refundId.equals(previous.refundId()) || expectedCycle != previous.expectedCycle()) {
                throw new Rejected(409, "IDEMPOTENCY_KEY_REUSED");
            }
            return json.readValue(previous.response(), View.class);
        }
        if (!payment.isSuccess() || refund.getStatus() != RefundStatus.FAILED || !refund.isCustomerRetryRequired()
                || refund.getRetryCycle() != expectedCycle || refund.getRetryCycle() == Integer.MAX_VALUE
                || !work.scheduleCustomerRetry(refundId, clock.instant())) {
            throw new Rejected(409, "REFUND_RETRY_NOT_SAFE");
        }
        refund.openNextCycle();
        var result = views.view(refund, true);
        requests.complete(actor.userId(), key, json.writeValueAsString(result));
        return result;
    }

    private static Rejected missing() {
        return new Rejected(404, "NOT_FOUND");
    }
}
