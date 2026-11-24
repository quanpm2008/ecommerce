package com.example.order_service.service;

import com.example.order_service.client.PaymentCheckoutClient;
import com.example.order_service.client.PaymentCheckoutClient.Collection;
import com.example.order_service.client.PaymentCheckoutClient.Refund;
import com.example.order_service.client.PaymentCheckoutClient.Refunds;
import com.example.order_service.client.PaymentCheckoutClient.Status;
import com.example.order_service.entity.CheckoutSession;
import com.example.order_service.entity.Order;
import com.example.order_service.repository.AccountObligationRepository;
import com.example.order_service.repository.AccountObligationRepository.Row;
import com.example.order_service.repository.AccountObligationRepository.Snapshot;
import feign.FeignException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class AccountObligationsService {
    private static final Set<Order.Status> OPEN = Set.of(Order.Status.AWAITING_PAYMENT, Order.Status.PAID,
            Order.Status.PACKING, Order.Status.DELIVERED);

    private final AccountObligationRepository repository;
    private final PaymentCheckoutClient client;
    private final String credential;

    AccountObligationsService(AccountObligationRepository repository, PaymentCheckoutClient client,
            @Value("${order.payment.service-token:}") String credential) {
        this.repository = repository;
        this.client = client;
        this.credential = credential;
    }

    public Obligations read(UUID userId, AccountType accountType) {
        boolean seller = accountType == AccountType.SELLER;
        try {
            Snapshot local = repository.read(userId, seller);
            if (local.rows().isEmpty()) {
                return new Obligations(List.of(), false, false);
            }
            var orders = new LinkedHashMap<UUID, Row>();
            var checkouts = new LinkedHashMap<UUID, Row>();
            for (Row row : local.rows()) {
                validateLocal(row, userId, seller);
                orders.put(row.orderId(), row);
                checkouts.putIfAbsent(row.checkoutId(), row);
            }
            if (credential.isBlank()) {
                throw new SourceUnavailable();
            }
            var payments = new HashMap<UUID, Collection>();
            var paymentCheckouts = new HashMap<UUID, Row>();
            for (Row checkout : checkouts.values()) {
                Collection payment = payment(checkout, local.creatingPayments().contains(checkout.checkoutId()));
                if (payment != null) {
                    payments.put(checkout.checkoutId(), payment);
                    if (paymentCheckouts.put(payment.paymentId(), checkout) != null) {
                        throw new SourceUnavailable();
                    }
                }
            }
            var refundIds = new HashSet<UUID>();
            var orderRefunds = orderRefunds(orders, payments, refundIds);
            var fullRefunds = fullRefunds(checkouts, paymentCheckouts, payments, refundIds);
            boolean paymentPending = false;
            for (var checkout : checkouts.values()) {
                var payment = payments.get(checkout.checkoutId());
                paymentPending |= pending(checkout, payment, fullRefunds.get(checkout.checkoutId()));
            }
            if (!local.equals(repository.read(userId, seller))) {
                throw new SourceUnavailable();
            }
            var openOrders = orders.values().stream().filter(row -> OPEN.contains(row.orderStatus()))
                    .map(Row::orderId).sorted(java.util.Comparator.comparing(UUID::toString)).toList();
            boolean refundUnresolved = orderRefunds.values().stream().anyMatch(AccountObligationsService::unresolved)
                    || fullRefunds.values().stream().anyMatch(AccountObligationsService::unresolved);
            return new Obligations(openOrders, paymentPending, refundUnresolved);
        } catch (DataAccessException | FeignException failure) {
            throw new SourceUnavailable(failure);
        }
    }

    private static void validateLocal(Row row, UUID userId, boolean seller) {
        if (row.orderId() == null || !row.checkoutCustomerId().equals(row.orderCustomerId())
                || (!seller && !userId.equals(row.checkoutCustomerId())) || !"VND".equals(row.currency())
                || row.orderAmountVnd() > row.checkoutAmountVnd()) {
            throw new SourceUnavailable();
        }
        boolean consistent = switch (row.checkoutStatus()) {
            case AWAITING_PAYMENT -> row.orderStatus() == Order.Status.AWAITING_PAYMENT && row.orderPaidAt() == null;
            case PAID -> row.checkoutPaidAt() != null && row.checkoutPaidAt().equals(row.orderPaidAt())
                    && row.paymentId() != null && (OPEN.contains(row.orderStatus())
                    && row.orderStatus() != Order.Status.AWAITING_PAYMENT
                    || row.orderStatus() == Order.Status.CONFIRMED || row.orderStatus() == Order.Status.CANCELLED);
            case CLOSED -> row.orderPaidAt() == null && row.orderStatus() ==
                    (row.closeReason() == CheckoutSession.CloseReason.EXPIRED ? Order.Status.EXPIRED : Order.Status.PAYMENT_FAILED);
        };
        if (!consistent || (row.checkoutStatus() != CheckoutSession.Status.PAID && row.checkoutPaidAt() != null)) {
            throw new SourceUnavailable();
        }
    }

    private Collection payment(Row checkout, boolean creationPending) {
        var envelope = client.read(checkout.checkoutId(), credential);
        if (envelope == null) {
            throw new SourceUnavailable();
        }
        var payment = envelope.data();
        if (payment == null) {
            if (checkout.paymentId() != null || (!creationPending && checkout.closeReason() != CheckoutSession.CloseReason.EXPIRED)) {
                throw new SourceUnavailable();
            }
            return null;
        }
        if (!checkout.checkoutId().equals(payment.checkoutSessionId())
                || !checkout.checkoutCustomerId().equals(payment.customerId()) || payment.paymentId() == null
                || (checkout.paymentId() != null && !checkout.paymentId().equals(payment.paymentId()))
                || !Objects.equals(checkout.checkoutAmountVnd(), payment.amountVnd())
                || !checkout.currency().equals(payment.currency()) || payment.status() == null
                || (payment.collectionClosedAt() != null && checkout.checkoutStatus() != CheckoutSession.Status.CLOSED)
                || (payment.status() == Status.PENDING && checkout.paymentId() != null)
                || (checkout.closeReason() == CheckoutSession.CloseReason.PAYMENT_FAILED && payment.status() != Status.FAILED)
                || (payment.status() == Status.SUCCESS
                    && (payment.completedAt() == null || !payment.completedAt().isBefore(checkout.expiresAt())))
                || (checkout.checkoutStatus() == CheckoutSession.Status.PAID
                    && (payment.status() != Status.SUCCESS || !checkout.checkoutPaidAt().equals(payment.completedAt())))) {
            throw new SourceUnavailable();
        }
        return payment;
    }

    private Map<UUID, Refund> orderRefunds(Map<UUID, Row> orders, Map<UUID, Collection> payments, Set<UUID> refundIds) {
        var byOrder = new HashMap<UUID, Refund>();
        for (Refund refund : verifiedRefunds(client.refundsByOrders(List.copyOf(orders.keySet()), credential), refundIds)) {
            Row order = orders.get(refund.orderId());
            if (!"ORDER".equals(refund.scope()) || order == null || order.orderStatus() != Order.Status.CANCELLED
                    || refund.amountVnd() != order.orderAmountVnd() || !matchesPayment(refund, payments.get(order.checkoutId()))
                    || byOrder.put(order.orderId(), refund) != null) {
                throw new SourceUnavailable();
            }
        }
        if (orders.values().stream().anyMatch(row -> row.orderStatus() == Order.Status.CANCELLED
                && !byOrder.containsKey(row.orderId()))) {
            throw new SourceUnavailable();
        }
        return byOrder;
    }

    private Map<UUID, Refund> fullRefunds(Map<UUID, Row> checkouts, Map<UUID, Row> paymentCheckouts,
            Map<UUID, Collection> payments, Set<UUID> refundIds) {
        var byCheckout = new HashMap<UUID, Refund>();
        for (Refund refund : verifiedRefunds(client.refundsByCheckouts(List.copyOf(checkouts.keySet()), credential), refundIds)) {
            Row checkout = paymentCheckouts.get(refund.paymentId());
            if (!"FULL_CHECKOUT".equals(refund.scope()) || refund.orderId() != null || checkout == null
                    || checkout.checkoutStatus() != CheckoutSession.Status.CLOSED
                    || refund.amountVnd() != checkout.checkoutAmountVnd() || !matchesPayment(refund, payments.get(checkout.checkoutId()))
                    || byCheckout.put(checkout.checkoutId(), refund) != null) {
                throw new SourceUnavailable();
            }
        }
        return byCheckout;
    }

    private static List<Refund> verifiedRefunds(Refunds envelope, Set<UUID> refundIds) {
        if (envelope == null || envelope.data() == null) {
            throw new SourceUnavailable();
        }
        for (Refund refund : envelope.data()) {
            if (refund == null || refund.refundId() == null || !refundIds.add(refund.refundId())
                    || refund.paymentId() == null || refund.status() == null) {
                throw new SourceUnavailable();
            }
            switch (refund.status()) {
                case "SUCCESS" -> {
                    if (refund.completedAt() == null) {
                        throw new SourceUnavailable();
                    }
                }
                case "PENDING", "FAILED" -> { }
                default -> throw new SourceUnavailable();
            }
        }
        return envelope.data();
    }

    private static boolean matchesPayment(Refund refund, Collection payment) {
        return payment != null && payment.status() == Status.SUCCESS && payment.paymentId().equals(refund.paymentId());
    }

    private static boolean pending(Row checkout, Collection payment, Refund fullRefund) {
        if (payment == null || payment.status() == Status.FAILED || checkout.checkoutStatus() == CheckoutSession.Status.PAID) {
            return false;
        }
        // Payment creates the full refund atomically when it has observed both closure and success.
        if (payment.status() == Status.SUCCESS && payment.collectionClosedAt() != null && fullRefund == null) {
            throw new SourceUnavailable();
        }
        return fullRefund == null || unresolved(fullRefund);
    }

    private static boolean unresolved(Refund refund) {
        return !"SUCCESS".equals(refund.status());
    }

    public enum AccountType { CUSTOMER, SELLER }

    public record Obligations(List<UUID> openOrderIds, boolean hasPendingPayment, boolean hasUnresolvedRefund) {
        public boolean hasOpenOrders() {
            return !openOrderIds.isEmpty();
        }
    }

    public static final class SourceUnavailable extends RuntimeException {
        SourceUnavailable() {}

        SourceUnavailable(RuntimeException cause) {
            super(cause);
        }
    }
}
