package com.example.payment_service;

import static com.example.payment_service.ProviderMockFixture.Fault.CALLBACK_BEFORE_RESPONSE;
import static com.example.payment_service.ProviderMockFixture.Fault.DELAY_AFTER_COMMIT;
import static com.example.payment_service.ProviderMockFixture.Fault.DROP_AFTER_COMMIT;
import static com.example.payment_service.ProviderMockFixture.Fault.REJECT;
import static com.example.payment_service.client.GatewayStatus.FAILED;
import static com.example.payment_service.client.GatewayStatus.PENDING;
import static com.example.payment_service.client.GatewayStatus.SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

import com.example.payment_service.client.PaymentGateway;
import com.example.payment_service.client.ProviderContracts.CreatePaymentRequest;
import com.example.payment_service.client.ProviderContracts.CreateRefundRequest;
import com.example.payment_service.client.ProviderHttpGateway;
import com.example.payment_service.client.ProviderHttpGateway.ProviderHttpException;
import com.example.payment_service.client.ProviderHttpGateway.UncertainResultException;
import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.json.JsonMapper;

class ProviderHttpContractTests {
    private static final JsonMapper JSON = new JsonMapper();
    private static final Instant NOW = Instant.parse("2026-11-21T04:00:00Z");

    @Test
    void createsOnePaymentAndReturnsTheSameOriginalExpiry() throws Exception {
        try (var fixture = new ProviderMockFixture(Clock.fixed(NOW, ZoneOffset.UTC))) {
            var provider = gateway(fixture);
            var request = payment();

            var first = provider.createPayment(request);
            assertThat(first.status()).isEqualTo(PENDING);
            assertThat(first.expiresAt()).isEqualTo(request.expiresAt());
            assertThat(first.paymentUrl()).startsWith(fixture.origin() + "/pay/");
            assertThat(provider.createPayment(request)).isEqualTo(first);
            var result = provider.queryPayment(request.paymentId());
            assertThat(result.providerTransactionId()).isEqualTo(first.providerTransactionId());
            assertThat(result.amountVnd()).isEqualTo(request.amountVnd());
            assertThat(result.currency()).isEqualTo("VND");
            assertThat(result.expiresAt()).isEqualTo(request.expiresAt());
            assertThat(result.paymentUrl()).isEqualTo(first.paymentUrl());
            assertThat(result.completedAt()).isNull();
            assertThat(fixture.paymentCount()).isOne();

            var conflict = new CreatePaymentRequest(request.paymentId(), request.checkoutSessionId(),
                    request.amountVnd() + 1, "VND", request.expiresAt());
            assertStatus(409, () -> provider.createPayment(conflict));
            assertStatus(404, () -> provider.queryPayment(UUID.randomUUID()));
            var unauthorized = new ProviderHttpGateway(fixture.origin(), ProviderMockFixture.CREDENTIAL_HEADER,
                    "invalid-credential", Duration.ofSeconds(2));
            assertStatus(401, () -> unauthorized.createPayment(request));
            assertThat(fixture.paymentCount()).isOne();
        }
    }

    @Test
    void recoversByPaymentIdAfterResponseIsLostFollowingCommit() throws Exception {
        try (var fixture = new ProviderMockFixture(Clock.fixed(NOW, ZoneOffset.UTC))) {
            var request = payment();
            var provider = gateway(fixture);
            fixture.nextPaymentFault(DROP_AFTER_COMMIT);

            assertThatThrownBy(() -> provider.createPayment(request)).isInstanceOf(UncertainResultException.class);
            var query = provider.queryPayment(request.paymentId());
            assertThat(query.status()).isEqualTo(PENDING);
            assertThat(query.expiresAt()).isEqualTo(request.expiresAt());
            assertThat(provider.createPayment(request).providerTransactionId()).isEqualTo(query.providerTransactionId());
            assertThat(fixture.paymentCount()).isOne();
        }
    }

    @Test
    void boundsSlowProviderCallsWithoutTreatingTimeoutAsDecline() throws Exception {
        try (var fixture = new ProviderMockFixture(Clock.fixed(NOW, ZoneOffset.UTC))) {
            var request = payment();
            var provider = new ProviderHttpGateway(fixture.origin(), ProviderMockFixture.CREDENTIAL_HEADER,
                    ProviderMockFixture.CREDENTIAL, Duration.ofMillis(800));
            fixture.nextPaymentFault(DELAY_AFTER_COMMIT);

            assertThatThrownBy(() -> provider.createPayment(request)).isInstanceOf(UncertainResultException.class);
            assertThat(gateway(fixture).queryPayment(request.paymentId()).status()).isEqualTo(PENDING);
            assertThat(fixture.paymentCount()).isOne();
        }
    }

    @Test
    void expiresAtOriginalDeadlineAndDoesNotCreateAReplacement() throws Exception {
        var clock = new MutableClock(NOW);
        try (var fixture = new ProviderMockFixture(clock)) {
            var provider = gateway(fixture);
            var request = payment();
            var initial = provider.createPayment(request);
            clock.now = request.expiresAt();

            var query = provider.queryPayment(request.paymentId());
            assertThat(query.status()).isEqualTo(FAILED);
            assertThat(query.errorCode()).isEqualTo("LINK_EXPIRED");
            assertThat(query.paymentUrl()).isNull();
            var replay = provider.createPayment(request);
            assertThat(replay.providerTransactionId()).isEqualTo(initial.providerTransactionId());
            assertThat(replay.expiresAt()).isEqualTo(request.expiresAt());
            assertThat(replay.paymentUrl()).isNull();
            assertStatus(409, () -> provider.createPayment(payment()));
            assertThat(fixture.paymentCount()).isOne();
        }
    }

    @Test
    void storesRefundAttemptsAndOnlyOneSuccessfulRefundCanFollowFailure() throws Exception {
        var clock = new MutableClock(NOW);
        try (var fixture = new ProviderMockFixture(clock)) {
            var provider = gateway(fixture);
            var payment = payment();
            var created = provider.createPayment(payment);
            fixture.completePayment(payment.paymentId());
            clock.now = payment.expiresAt();
            assertThat(provider.queryPayment(payment.paymentId()).status()).isEqualTo(SUCCESS);

            var first = refund(payment, created.providerTransactionId());
            fixture.nextRefundFault(REJECT);
            var failed = provider.createRefund(first);
            assertThat(failed.status()).isEqualTo(FAILED);
            assertThat(provider.queryRefund(first.refundId(), first.attemptId())).isEqualTo(failed);
            assertThat(provider.createRefund(first)).isEqualTo(failed);
            var conflict = new CreateRefundRequest(first.refundId(), first.attemptId(), first.paymentId(),
                    first.providerTransactionId(), first.amountVnd() - 1, "VND");
            assertStatus(409, () -> provider.createRefund(conflict));

            var second = new CreateRefundRequest(first.refundId(), UUID.randomUUID(), first.paymentId(),
                    first.providerTransactionId(), first.amountVnd(), "VND");
            fixture.nextRefundFault(DROP_AFTER_COMMIT);
            assertThatThrownBy(() -> provider.createRefund(second)).isInstanceOf(UncertainResultException.class);
            var successful = provider.queryRefund(second.refundId(), second.attemptId());
            assertThat(successful.status()).isEqualTo(SUCCESS);
            assertThat(provider.createRefund(second)).isEqualTo(successful);
            assertThat(provider.queryRefund(first.refundId(), first.attemptId())).isEqualTo(failed);
            assertStatus(409, () -> provider.createRefund(new CreateRefundRequest(first.refundId(), UUID.randomUUID(),
                    first.paymentId(), first.providerTransactionId(), first.amountVnd(), "VND")));
            assertStatus(409, () -> provider.createRefund(refund(payment, created.providerTransactionId())));
            assertStatus(404, () -> provider.queryRefund(first.refundId(), UUID.randomUUID()));
            assertThat(fixture.refundSuccessCount(first.refundId())).isOne();
        }
    }

    @Test
    void sendsCaptureAndRefundWebhooksWithRawBodySignaturesAndStableEventId() throws Exception {
        try (var sink = new CallbackSink();
             var fixture = new ProviderMockFixture(Clock.fixed(NOW, ZoneOffset.UTC), sink.url)) {
            var provider = gateway(fixture);
            var payment = payment();
            fixture.nextPaymentFault(CALLBACK_BEFORE_RESPONSE);
            var created = provider.createPayment(payment);
            assertThat(created.status()).isEqualTo(SUCCESS);
            var capture = sink.events.remove();
            var body = JSON.readTree(capture.body());
            assertThat(body.path("type").asString()).isEqualTo("payment.result");
            assertThat(body.path("paymentId").asString()).isEqualTo(payment.paymentId().toString());
            assertThat(body.path("providerTransactionId").asString()).isEqualTo(created.providerTransactionId());
            assertSignature(capture);

            UUID eventId = UUID.fromString(body.path("eventId").asString());
            assertThat(fixture.sendEvent(eventId, sink.url, NOW.plusSeconds(301))).isEqualTo(204);
            var redelivered = sink.events.remove();
            assertThat(redelivered.body()).isEqualTo(capture.body());
            assertThat(redelivered.timestamp()).isNotEqualTo(capture.timestamp());
            assertThat(redelivered.signature()).isNotEqualTo(capture.signature());
            assertSignature(redelivered);

            var refund = refund(payment, created.providerTransactionId());
            fixture.nextRefundFault(CALLBACK_BEFORE_RESPONSE);
            var result = provider.createRefund(refund);
            var callback = sink.events.remove();
            var refundBody = JSON.readTree(callback.body());
            assertThat(refundBody.path("type").asString()).isEqualTo("refund.result");
            assertThat(refundBody.path("refundId").asString()).isEqualTo(refund.refundId().toString());
            assertThat(refundBody.path("attemptId").asString()).isEqualTo(refund.attemptId().toString());
            assertThat(refundBody.path("providerRefundId").asString()).isEqualTo(result.providerRefundId());
            assertThat(refundBody.path("amountVnd").asLong()).isEqualTo(refund.amountVnd());
            assertSignature(callback);
        }
    }

    private static PaymentGateway gateway(ProviderMockFixture fixture) {
        return new ProviderHttpGateway(fixture.origin(), ProviderMockFixture.CREDENTIAL_HEADER,
                ProviderMockFixture.CREDENTIAL, Duration.ofSeconds(2));
    }

    private static CreatePaymentRequest payment() {
        return new CreatePaymentRequest(UUID.randomUUID(), UUID.randomUUID(), 42_000, "VND", NOW.plusSeconds(900));
    }

    private static CreateRefundRequest refund(CreatePaymentRequest payment, String providerTransactionId) {
        return new CreateRefundRequest(UUID.randomUUID(), UUID.randomUUID(), payment.paymentId(),
                providerTransactionId, payment.amountVnd(), payment.currency());
    }

    private static void assertStatus(int status, org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ProviderHttpException.class,
                exception -> assertThat(exception.statusCode()).isEqualTo(status));
    }

    private static void assertSignature(Callback callback) throws Exception {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(ProviderMockFixture.WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update((callback.timestamp() + ".").getBytes(StandardCharsets.UTF_8));
        assertThat(callback.signature()).isEqualTo(HexFormat.of().formatHex(mac.doFinal(callback.body())));
    }

    private static final class MutableClock extends Clock {
        private volatile Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        @Override public Instant instant() { return now; }
    }

    private static final class CallbackSink implements AutoCloseable {
        private final BlockingQueue<Callback> events = new LinkedBlockingQueue<>();
        private final HttpServer server;
        private final URI url;

        private CallbackSink() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/webhook", exchange -> {
                try (exchange) {
                    events.add(new Callback(exchange.getRequestBody().readAllBytes(),
                            exchange.getRequestHeaders().getFirst("X-Webhook-Timestamp"),
                            exchange.getRequestHeaders().getFirst("X-Webhook-Signature")));
                    exchange.sendResponseHeaders(204, -1);
                }
            });
            server.start();
            url = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/webhook");
        }

        @Override public void close() { server.stop(0); }
    }

    private record Callback(byte[] body, String timestamp, String signature) { }
}
