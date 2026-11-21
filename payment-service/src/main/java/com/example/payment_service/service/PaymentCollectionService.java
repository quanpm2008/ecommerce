package com.example.payment_service.service;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.payment_service.client.GatewayStatus;
import com.example.payment_service.client.OrderCheckoutPayableClient;
import com.example.payment_service.client.OrderCheckoutPayableClient.Payable;
import com.example.payment_service.client.PaymentGateway;
import com.example.payment_service.client.ProviderContracts.CreatePaymentRequest;
import com.example.payment_service.client.ProviderContracts.CreatePaymentResponse;
import com.example.payment_service.client.ProviderContracts.PaymentQueryResponse;
import com.example.payment_service.client.ProviderHttpGateway.ProviderHttpException;
import com.example.payment_service.client.ProviderHttpGateway.UncertainResultException;
import com.example.payment_service.entity.Payment;
import com.example.payment_service.entity.PaymentStatus;
import com.example.payment_service.repository.PaymentCollectionWorkRepository;
import com.example.payment_service.repository.PaymentRepository;

import feign.FeignException;

@Service
public class PaymentCollectionService {
    private static final int[] RETRY_SECONDS = {5, 15, 30, 60};

    private final PaymentRepository payments;
    private final PaymentCollectionWorkRepository work;
    private final OrderCheckoutPayableClient order;
    private final ObjectProvider<PaymentGateway> gateway;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final String orderCredential;
    private final String environment;
    private final Duration leaseLength;

    public PaymentCollectionService(PaymentRepository payments, PaymentCollectionWorkRepository work,
            OrderCheckoutPayableClient order, ObjectProvider<PaymentGateway> gateway,
            PlatformTransactionManager transactionManager, Clock clock,
            @Value("${payment.order.credential:}") String orderCredential,
            @Value("${payment.provider.environment:DEV}") String environment,
            @Value("${payment.provider.timeout:2s}") Duration providerTimeout) {
        this.payments = payments;
        this.work = work;
        this.order = order;
        this.gateway = gateway;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.orderCredential = orderCredential;
        this.environment = environment;
        this.leaseLength = providerTimeout.multipliedBy(4).plusSeconds(2);
    }

    public CollectionView createOrGet(UUID checkoutId) {
        if (work.closedAt(checkoutId) != null) {
            var existing = payments.findByCheckoutSessionId(checkoutId);
            if (existing.isEmpty()) {
                return null;
            }
            verifySnapshot(existing.orElseThrow(), fetchPayable(checkoutId));
            return view(checkoutId, true);
        }
        if (gateway.getIfAvailable() == null) {
            throw new SourceUnavailable();
        }
        Payable checkout = fetchPayable(checkoutId);
        boolean newlyCreated = Boolean.TRUE.equals(transactions.execute(transaction -> {
            var guard = work.lockCheckout(checkoutId);
            var existing = payments.findByCheckoutSessionId(checkoutId);
            if (existing.isPresent()) {
                verifySnapshot(existing.orElseThrow(), checkout);
                return false;
            }
            Instant now = clock.instant();
            if (guard.isClosed() || !"AWAITING_PAYMENT".equals(checkout.status())
                    || !checkout.expiresAt().isAfter(now)) {
                return false;
            }
            var payment = new Payment(checkoutId, checkout.customerId(), checkout.totalAmountVnd(),
                    "VND", "DEMO_PROVIDER", environment, checkoutId.toString());
            payment.setExpiresAt(checkout.expiresAt());
            payments.saveAndFlush(payment);
            work.schedule(payment.getPaymentId(), now);
            return true;
        }));
        if (newlyCreated) {
            process(checkoutId);
        }
        return view(checkoutId, !"AWAITING_PAYMENT".equals(checkout.status()) || !checkout.expiresAt().isAfter(clock.instant()));
    }

    public CollectionView read(UUID checkoutId) {
        return view(checkoutId, false);
    }

    public List<UUID> dueCheckouts() {
        return gateway.getIfAvailable() == null ? List.of() : work.dueCheckouts(clock.instant());
    }

    public void process(UUID checkoutId) {
        PaymentGateway provider = gateway.getIfAvailable();
        if (provider == null) {
            return;
        }
        Dispatch dispatch = transactions.execute(transaction -> claim(checkoutId));
        if (dispatch == null) {
            return;
        }
        CreatePaymentResponse created = null;
        PaymentQueryResponse queried = null;
        try {
            if (!dispatch.query()) {
                created = provider.createPayment(dispatch.request());
            } else {
                queried = provider.queryPayment(dispatch.request().paymentId());
            }
        } catch (ProviderHttpException upstream) {
            if (dispatch.query() && upstream.statusCode() == 404 && dispatch.providerRef() == null
                    && canResumeInitialSend(dispatch)) {
                created = resumeInitialSend(provider, dispatch.request());
            }
        } catch (UncertainResultException upstream) {
            // The durable intent stays pending until a matching provider result is observed.
        }
        var createResult = created;
        var queryResult = queried;
        transactions.executeWithoutResult(transaction -> finish(dispatch, createResult, queryResult));
    }

    private Dispatch claim(UUID checkoutId) {
        var guard = work.lockCheckout(checkoutId);
        Payment payment = payments.findByCheckoutSessionId(checkoutId).orElse(null);
        if (payment == null || guard.isClosed() || !payment.isPending()) {
            return null;
        }
        UUID paymentId = payment.getPaymentId();
        var scheduled = work.find(paymentId).orElse(null);
        Instant now = clock.instant();
        if (scheduled == null || !scheduled.isDue(now)) {
            return null;
        }
        if (!scheduled.dispatchStarted() && !payment.getExpiresAt().isAfter(now)) {
            work.stop(paymentId);
            return null;
        }
        UUID lease = UUID.randomUUID();
        work.claim(paymentId, lease, now.plus(leaseLength));
        return new Dispatch(new CreatePaymentRequest(paymentId, checkoutId, payment.getAmountVnd(),
                payment.getCurrency(), payment.getExpiresAt()), lease, scheduled.dispatchStarted(),
                scheduled.queryAttempts(), payment.getProviderTransactionId());
    }

    private boolean canResumeInitialSend(Dispatch dispatch) {
        return Boolean.TRUE.equals(transactions.execute(transaction -> {
            var guard = work.lockCheckout(dispatch.request().checkoutSessionId());
            return !guard.isClosed() && dispatch.request().expiresAt().isAfter(clock.instant())
                    && work.find(dispatch.request().paymentId())
                            .map(job -> dispatch.lease().equals(job.leaseId())).orElse(false);
        }));
    }

    private static CreatePaymentResponse resumeInitialSend(PaymentGateway provider, CreatePaymentRequest request) {
        try {
            return provider.createPayment(request);
        } catch (UncertainResultException | ProviderHttpException upstream) {
            return null;
        }
    }

    private void finish(Dispatch dispatch, CreatePaymentResponse created, PaymentQueryResponse queried) {
        var guard = work.lockCheckout(dispatch.request().checkoutSessionId());
        UUID paymentId = dispatch.request().paymentId();
        var current = work.find(paymentId).orElseThrow();
        if (!dispatch.lease().equals(current.leaseId())) {
            return;
        }
        Payment payment = payments.findByIdForUpdate(paymentId).orElseThrow();
        Instant now = clock.instant();
        if (!payment.isPending()) {
            work.stop(paymentId);
            return;
        }
        boolean terminal = applyObservation(payment, guard.isClosed(), created, queried, now);
        if (guard.isClosed() || terminal) {
            work.stop(paymentId);
            return;
        }
        int attempt = dispatch.query() ? Math.min(dispatch.queryAttempts() + 1, RETRY_SECONDS.length - 1) : 0;
        work.retry(paymentId, dispatch.lease(), attempt, now.plusSeconds(RETRY_SECONDS[attempt]));
    }

    private boolean applyObservation(Payment payment, boolean closed,
            CreatePaymentResponse created, PaymentQueryResponse queried, Instant now) {
        if (queried != null && matches(payment, queried)) {
            if (queried.status() != GatewayStatus.PENDING) {
                payment.setProviderTransactionId(queried.providerTransactionId());
                payment.setPaymentUrl(null);
                work.observe(payment.getPaymentId(), queried, now);
                return true;
            }
            if (!closed) {
                updateLink(payment, queried.providerTransactionId(), queried.paymentUrl(), now);
            }
        } else if (!closed && created != null && matchesRef(payment, created.providerTransactionId())) {
            updateLink(payment, created.providerTransactionId(),
                    created.status() == GatewayStatus.PENDING ? created.paymentUrl() : null, now);
        }
        return false;
    }

    private static boolean matches(Payment payment, PaymentQueryResponse queried) {
        return matchesRef(payment, queried.providerTransactionId())
                && payment.getAmountVnd() == queried.amountVnd()
                && "VND".equals(queried.currency())
                && payment.getExpiresAt().equals(queried.expiresAt())
                && (queried.errorCode() == null || queried.errorCode().length() <= 100);
    }

    private static boolean matchesRef(Payment payment, String ref) {
        return ref != null && !ref.isBlank() && ref.length() <= 255
                && (payment.getProviderTransactionId() == null || payment.getProviderTransactionId().equals(ref));
    }

    private static void updateLink(Payment payment, String ref, String url, Instant now) {
        payment.setProviderTransactionId(ref);
        if (payment.getExpiresAt().isAfter(now) && usableLink(url) && payment.getPaymentUrl() == null) {
            payment.setPaymentUrl(url);
        }
    }

    private static boolean usableLink(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            var uri = URI.create(url);
            return uri.getHost() != null && uri.getUserInfo() == null
                    && ("https".equalsIgnoreCase(uri.getScheme())
                        || ("http".equalsIgnoreCase(uri.getScheme()) && ("127.0.0.1".equals(uri.getHost())
                            || "localhost".equalsIgnoreCase(uri.getHost()) || "[::1]".equals(uri.getHost()))));
        } catch (IllegalArgumentException malformed) {
            return false;
        }
    }

    private CollectionView view(UUID checkoutId, boolean hideLink) {
        return transactions.execute(transaction -> {
            var payment = payments.findByCheckoutSessionId(checkoutId).orElse(null);
            if (payment == null) {
                return null;
            }
            Instant closedAt = work.closedAt(checkoutId);
            String url = !hideLink && closedAt == null && payment.isPending() && payment.getExpiresAt() != null
                    && payment.getExpiresAt().isAfter(clock.instant()) ? payment.getPaymentUrl() : null;
            return new CollectionView(payment.getPaymentId(), checkoutId, payment.getStatus(), url,
                    payment.getExpiresAt(), closedAt, payment.getAmountVnd(), payment.getCurrency(),
                    payment.getProviderTransactionId(), payment.getErrorCode(), payment.getCompletedAt(), true);
        });
    }

    private Payable fetchPayable(UUID checkoutId) {
        if (orderCredential.isBlank()) {
            throw new SourceUnavailable();
        }
        try {
            var envelope = order.payable(checkoutId, orderCredential);
            var payable = envelope == null ? null : envelope.data();
            if (payable == null || payable.customerId() == null || payable.expiresAt() == null
                    || payable.status() == null || payable.totalAmountVnd() == null || payable.totalAmountVnd() <= 0
                    || payable.orders() == null || payable.orders().isEmpty()
                    || payable.orders().stream().anyMatch(row -> row == null || row.orderId() == null)) {
                throw new SourceUnavailable();
            }
            return payable;
        } catch (FeignException upstream) {
            if (upstream.status() == 404) {
                throw new CheckoutNotFound();
            }
            throw new SourceUnavailable();
        }
    }

    private static void verifySnapshot(Payment payment, Payable checkout) {
        if (!payment.getCustomerId().equals(checkout.customerId())
                || !payment.getAmountVnd().equals(checkout.totalAmountVnd())
                || !checkout.expiresAt().equals(payment.getExpiresAt())) {
            throw new CheckoutConflict();
        }
    }

    private record Dispatch(CreatePaymentRequest request, UUID lease, boolean query,
            int queryAttempts, String providerRef) { }

    public record CollectionView(UUID paymentId, UUID checkoutSessionId, PaymentStatus status, String paymentUrl,
            Instant expiresAt, Instant collectionClosedAt, long amountVnd, String currency,
            String providerTransactionId, String errorCode, Instant completedAt, boolean isSimulated) { }

    public static final class SourceUnavailable extends RuntimeException { }
    public static final class CheckoutNotFound extends RuntimeException { }
    public static final class CheckoutConflict extends RuntimeException { }
}
