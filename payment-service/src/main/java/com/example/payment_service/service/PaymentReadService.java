package com.example.payment_service.service;

import com.example.payment_service.client.OrderCheckoutPayableClient;
import com.example.payment_service.entity.Payment;
import com.example.payment_service.entity.Refund;
import com.example.payment_service.entity.RefundStatus;
import com.example.payment_service.repository.PaymentRepository;
import com.example.payment_service.repository.RefundRepository;
import com.example.payment_service.security.RefundCustomerAuthorization.Actor;
import com.example.payment_service.security.RefundCustomerAuthorization.Rejected;
import feign.FeignException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentReadService {
    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final RefundReadService refundRead;
    private final PaymentCollectionService collections;
    private final OrderCheckoutPayableClient order;
    private final Clock clock;
    private final EntityManager entities;
    private final String credential;

    public PaymentReadService(PaymentRepository payments, RefundRepository refunds, RefundReadService refundRead,
            PaymentCollectionService collections, OrderCheckoutPayableClient order, Clock clock, EntityManager entities,
            @Value("${payment.order.credential:}") String credential) {
        this.payments = payments;
        this.refunds = refunds;
        this.refundRead = refundRead;
        this.collections = collections;
        this.order = order;
        this.clock = clock;
        this.entities = entities;
        this.credential = credential;
    }

    public Map<String, Object> customer(UUID checkoutId, Actor actor) {
        OrderCheckoutPayableClient.Payable checkout = checkout(checkoutId);
        if (!actor.userId().equals(checkout.customerId())) throw new Rejected(404, "NOT_FOUND");
        var payment = payments.findByCheckoutSessionId(checkoutId).orElse(null);
        var data = new LinkedHashMap<String, Object>();
        if (payment == null) {
            if (actor.limited() && !"AWAITING_PAYMENT".equals(checkout.status())) throw new Rejected(403, "ACCOUNT_BANNED");
            data.put("data", null);
        } else {
            if (!actor.userId().equals(payment.getCustomerId())) throw new Rejected(404, "NOT_FOUND");
            var matches = refunds.findByPaymentId(payment.getPaymentId());
            if (actor.limited() && !payment.isPending()
                    && matches.stream().noneMatch(refund -> refund.getStatus() != RefundStatus.SUCCESS)
                    && !openOrders(checkout, matches)) {
                throw new Rejected(403, "ACCOUNT_BANNED");
            }
            boolean validCheckout = !actor.limited() && "AWAITING_PAYMENT".equals(checkout.status())
                    && checkout.expiresAt().isAfter(clock.instant());
            data.put("data", view(payment, matches, validCheckout));
        }
        return data;
    }

    private static boolean openOrders(OrderCheckoutPayableClient.Payable checkout, List<Refund> refunds) {
        if (!"PAID".equals(checkout.status()) || checkout.orders() == null) return false;
        return checkout.orders().stream().anyMatch(order -> {
            if (order.status() == null) return false;
            return switch (order.status()) {
                case "PAID", "PACKING", "DELIVERED" -> true;
                case "CANCELLED" -> order.orderId() != null && refunds.stream()
                        .noneMatch(refund -> order.orderId().equals(refund.getOrderId()));
                default -> false;
            };
        });
    }

    public Map<String, Object> adminDetail(UUID checkoutId) {
        var payment = payments.findByCheckoutSessionId(checkoutId).orElseThrow(() -> new Rejected(404, "NOT_FOUND"));
        var matches = refunds.findByPaymentId(payment.getPaymentId());
        var view = view(payment, matches, false);
        view.put("refunds", matches.stream().map(refund -> refundRead.view(refund, false)).toList());
        view.put("manualNote", null);
        return view;
    }

    public Map<String, Object> adminRefunds(RefundStatus status, UUID orderId, UUID paymentId, int page, int size) {
        page(page, size);
        var result = refunds.findAll((root, query, cb) -> {
            var filters = new ArrayList<Predicate>();
            if (status != null) filters.add(cb.equal(root.get("status"), status));
            if (orderId != null) filters.add(cb.equal(root.get("orderId"), orderId));
            if (paymentId != null) filters.add(cb.equal(root.get("paymentId"), paymentId));
            return cb.and(filters.toArray(Predicate[]::new));
        }, PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt", "refundId")));
        return Map.of("data", result.map(refund -> refundRead.view(refund, false)).getContent(),
                "meta", meta(page, size, result.getTotalElements()));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> summary(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) throw new Rejected(400, "VALIDATION_ERROR");
        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        var start = from.atStartOfDay(zone).toInstant();
        var end = to.plusDays(1).atStartOfDay(zone).toInstant();
        var paymentSums = (Object[]) entities.createNativeQuery("""
                select coalesce(sum(amount_vnd) filter (where status='SUCCESS' and completed_at >= :start and completed_at < :end),0),
                       count(*) filter (where status='PENDING' and created_at >= :start and created_at < :end)
                from payments
                """).setParameter("start", start).setParameter("end", end).getSingleResult();
        var sums = (Object[]) entities.createNativeQuery("""
                select coalesce(sum(amount_vnd) filter (where status='SUCCESS'
                          and completed_at >= :start and completed_at < :end),0),
                       coalesce(sum(amount_vnd) filter (where status in ('PENDING','FAILED')
                          and coalesce(completed_at,created_at) >= :start and coalesce(completed_at,created_at) < :end),0),
                       count(*) filter (where status='FAILED' and completed_at >= :start and completed_at < :end)
                from refunds
                """).setParameter("start", start).setParameter("end", end).getSingleResult();
        return Map.of("collectedVnd", paymentSums[0], "refundedVnd", sums[0], "pendingRefundVnd", sums[1],
                "pendingPaymentCount", paymentSums[1], "failedRefundCount", sums[2], "isSimulated", true);
    }

    private Map<String, Object> view(Payment payment, List<Refund> refunds, boolean includeLink) {
        var collection = collections.read(payment.getCheckoutSessionId());
        var result = new LinkedHashMap<String, Object>();
        result.put("paymentId", payment.getPaymentId());
        result.put("checkoutSessionId", payment.getCheckoutSessionId());
        result.put("amountVnd", payment.getAmountVnd());
        result.put("currency", payment.getCurrency());
        result.put("provider", payment.getProvider());
        result.put("status", payment.getStatus());
        result.put("collectionClosedAt", collection.collectionClosedAt());
        result.put("providerTransactionId", payment.getProviderTransactionId());
        result.put("errorCode", payment.getErrorCode());
        result.put("createdAt", payment.getCreatedAt());
        result.put("completedAt", payment.getCompletedAt());
        result.put("isSimulated", true);
        long refunded = refunds.stream().filter(refund -> refund.getStatus() == RefundStatus.SUCCESS)
                .mapToLong(Refund::getAmountVnd).sum();
        result.put("refundedAmountVnd", refunded);
        result.put("refundState", refunded == 0 ? "NONE" : refunded == payment.getAmountVnd() ? "REFUNDED" : "PARTIALLY_REFUNDED");
        if (includeLink && collection.paymentUrl() != null) {
            result.put("paymentUrl", collection.paymentUrl());
            result.put("expiresAt", collection.expiresAt());
        }
        return result;
    }

    private OrderCheckoutPayableClient.Payable checkout(UUID checkoutId) {
        if (credential.isBlank()) throw new Rejected(503, "SERVICE_UNAVAILABLE");
        try {
            var envelope = order.payable(checkoutId, credential);
            if (envelope == null || envelope.data() == null || envelope.data().customerId() == null
                    || envelope.data().status() == null || envelope.data().expiresAt() == null) {
                throw new Rejected(503, "SERVICE_UNAVAILABLE");
            }
            return envelope.data();
        } catch (FeignException failure) {
            if (failure.status() == 404) throw new Rejected(404, "NOT_FOUND");
            throw new Rejected(503, "SERVICE_UNAVAILABLE");
        }
    }

    public static void page(int page, int size) {
        if (page < 1 || size < 1 || size > 100) throw new Rejected(400, "VALIDATION_ERROR");
    }

    static Map<String, Object> meta(int page, int size, long count) {
        return Map.of("page", page, "size", size, "totalItems", count, "totalPages", (count + size - 1) / size);
    }
}
