package com.example.payment_service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;

import com.example.payment_service.client.OrderCheckoutPayableClient;
import com.example.payment_service.client.OrderCheckoutPayableClient.Envelope;
import com.example.payment_service.client.OrderCheckoutPayableClient.Order;
import com.example.payment_service.client.OrderCheckoutPayableClient.Payable;
import com.example.payment_service.client.PaymentGateway;
import com.example.payment_service.repository.PaymentCollectionWorkRepository;
import com.example.payment_service.repository.PaymentRepository;
import com.example.payment_service.service.PaymentCollectionJob;
import com.example.payment_service.service.PaymentCollectionService;
import com.example.payment_service.service.PaymentOutcomeService;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false", "payment.internal.order-token=test-order",
        "payment.order.credential=test-payment", "payment.collection.initial-delay=3600000"
})
class PaymentCollectionPostgresTests {
    private static final Instant START = Instant.parse("2026-11-21T10:00:00Z");
    private static final TestClock CLOCK = new TestClock();
    private static final ProviderMockFixture PROVIDER;
    private static final HttpServer ORDER;
    private static final String PAYABLE_PATH = "/internal/v1/orders/checkout-sessions/";
    private static final ConcurrentHashMap<UUID, Payable> PAYABLES = new ConcurrentHashMap<>();
    private static final AtomicInteger PAYABLE_REQUESTS = new AtomicInteger();
    private static final UUID UNAVAILABLE = UUID.randomUUID();
    private static final JsonMapper JSON = JsonMapper.builder().findAndAddModules().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    static {
        try {
            PROVIDER = new ProviderMockFixture(CLOCK);
            ORDER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ORDER.createContext(PAYABLE_PATH, exchange -> {
                try (exchange) {
                    String path = exchange.getRequestURI().getPath();
                    if (!path.endsWith("/payable") || !"GET".equals(exchange.getRequestMethod())) {
                        exchange.sendResponseHeaders(404, -1);
                        return;
                    }
                    if (!List.of("test-payment").equals(exchange.getRequestHeaders().get("X-Service-Token"))) {
                        exchange.sendResponseHeaders(401, -1);
                        return;
                    }
                    PAYABLE_REQUESTS.incrementAndGet();
                    UUID id = UUID.fromString(path.substring(PAYABLE_PATH.length(), path.length() - "/payable".length()));
                    if (UNAVAILABLE.equals(id) || !PAYABLES.containsKey(id)) {
                        exchange.sendResponseHeaders(UNAVAILABLE.equals(id) ? 503 : 404, -1);
                        return;
                    }
                    byte[] body = JSON.writeValueAsString(new Envelope(PAYABLES.get(id))).getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                }
            });
            ORDER.start();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    @DynamicPropertySource
    static void peers(DynamicPropertyRegistry registry) {
        registry.add("payment.order.base-url", () -> "http://127.0.0.1:" + ORDER.getAddress().getPort());
        registry.add("payment.provider.base-url", () -> PROVIDER.origin().toString());
        registry.add("payment.provider.credential-header", () -> ProviderMockFixture.CREDENTIAL_HEADER);
        registry.add("payment.provider.credential", () -> ProviderMockFixture.CREDENTIAL);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ControlledTime {
        @Bean
        @Primary
        Clock collectionTestClock() {
            return CLOCK;
        }
    }

    @LocalServerPort int port;
    @Autowired PaymentRepository payments;
    @Autowired PaymentCollectionWorkRepository work;
    @Autowired JdbcClient jdbc;
    @Autowired PaymentCollectionJob job;
    @Autowired OrderCheckoutPayableClient feign;
    @Autowired ObjectProvider<PaymentGateway> gateway;
    @Autowired PlatformTransactionManager transactions;
    @Autowired PaymentOutcomeService outcomes;

    @BeforeEach
    void resetTestState() {
        String database = jdbc.sql("SELECT current_database()").query(String.class).single();
        assertThat(database).contains("test");
        jdbc.sql("DELETE FROM payment_refund_work").update();
        jdbc.sql("DELETE FROM refunds").update();
        jdbc.sql("DELETE FROM payment_collection_observation").update();
        jdbc.sql("DELETE FROM payment_collection_work").update();
        payments.deleteAll();
        jdbc.sql("DELETE FROM payment_checkout_guard").update();
        CLOCK.set(START);
    }

    @AfterAll
    static void stopPeers() {
        ORDER.stop(0);
        PROVIDER.close();
    }

    @Test
    void concurrentCreateUsesOnePaymentAndProviderTransaction() throws Exception {
        UUID checkout = payable();
        int before = PROVIDER.paymentCount();
        int requests = PAYABLE_REQUESTS.get();
        var first = HTTP.sendAsync(create(checkout, "test-order"), HttpResponse.BodyHandlers.ofString());
        var second = HTTP.sendAsync(create(checkout, "test-order"), HttpResponse.BodyHandlers.ofString());
        CompletableFuture.allOf(first, second).get(10, TimeUnit.SECONDS);
        assertThat(first.join().statusCode()).isEqualTo(200);
        assertThat(second.join().statusCode()).isEqualTo(200);
        var view = body(send(read(checkout, "test-order"))).get("data");
        UUID id = UUID.fromString(view.get("paymentId").asText());
        assertThat(body(first.join()).at("/data/paymentId").asText()).isEqualTo(id.toString());
        assertThat(body(second.join()).at("/data/paymentId").asText()).isEqualTo(id.toString());
        assertThat(view.get("paymentUrl").asText()).startsWith(PROVIDER.origin().toString());
        assertThat(view.get("currency").asText()).isEqualTo("VND");
        assertThat(view.get("amountVnd").asLong()).isEqualTo(55000);
        assertThat(PAYABLE_REQUESTS.get() - requests).isEqualTo(2);
        assertThat(PROVIDER.paymentCount() - before).isEqualTo(1);
        assertThat(payments.count()).isEqualTo(1);
        assertThat(payments.findByIdempotencyKey(checkout.toString()).orElseThrow().getPaymentId()).isEqualTo(id);
    }

    @Test
    void onlyOrderCanAccessAndMissingDiffersFromUnavailability() throws Exception {
        UUID checkout = payable();
        int requests = PAYABLE_REQUESTS.get();
        assertThat(send(create(checkout, null)).statusCode()).isEqualTo(401);
        assertThat(send(read(checkout, "test-payment")).statusCode()).isEqualTo(401);
        var duplicateToken = request("/by-checkout/" + checkout, "test-order")
                .header("X-Service-Token", "test-order").GET().build();
        assertThat(send(duplicateToken).statusCode()).isEqualTo(401);
        assertThat(PAYABLE_REQUESTS.get()).isEqualTo(requests);
        var missing = send(read(checkout, "test-order"));
        assertThat(missing.statusCode()).isEqualTo(200);
        assertThat(body(missing).get("data").isNull()).isTrue();
        assertThat(send(create(UUID.randomUUID(), "test-order")).statusCode()).isEqualTo(404);
        assertThat(send(create(UNAVAILABLE, "test-order")).statusCode()).isEqualTo(503);
        assertThat(payments.count()).isZero();
    }

    @Test
    void replayVerifiesOwnerAndHidesLinkAfterExpiryOrClosure() throws Exception {
        UUID checkout = payable();
        assertThat(send(create(checkout, "test-order")).statusCode()).isEqualTo(200);
        var stored = payments.findByCheckoutSessionId(checkout).orElseThrow();
        PAYABLES.computeIfPresent(checkout, (id, value) -> new Payable(UUID.randomUUID(),
                value.status(), value.expiresAt(), value.totalAmountVnd(), value.orders()));
        assertThat(send(create(checkout, "test-order")).statusCode()).isEqualTo(409);
        assertThat(payments.findByCheckoutSessionId(checkout).orElseThrow().getCustomerId())
                .isEqualTo(stored.getCustomerId());
        UUID closed = payable();
        assertThat(send(create(closed, "test-order")).statusCode()).isEqualTo(200);
        int before = PROVIDER.paymentCount();
        markClosed(closed);
        assertThat(body(send(read(closed, "test-order"))).at("/data/paymentUrl").isNull()).isTrue();
        assertThat(body(send(create(closed, "test-order"))).at("/data/paymentUrl").isNull()).isTrue();
        UUID noPayment = payable();
        markClosed(noPayment);
        assertThat(body(send(create(noPayment, "test-order"))).get("data").isNull()).isTrue();
        assertThat(PROVIDER.paymentCount()).isEqualTo(before);
        CLOCK.set(stored.getExpiresAt());
        assertThat(body(send(read(checkout, "test-order"))).at("/data/paymentUrl").isNull()).isTrue();
    }

    @Test
    void durableJobRecoversLostResponseAtApprovedIntervalsWithSamePayment() throws Exception {
        UUID checkout = payable();
        PROVIDER.nextPaymentFault(ProviderMockFixture.Fault.DROP_AFTER_COMMIT);
        int before = PROVIDER.paymentCount();
        var response = send(create(checkout, "test-order"));
        assertThat(response.statusCode()).isEqualTo(200);
        var view = body(response).get("data");
        UUID paymentId = UUID.fromString(view.get("paymentId").asText());
        assertThat(view.get("paymentUrl").isNull()).isTrue();
        assertThat(view.get("status").asText()).isEqualTo("PENDING");
        assertThat(work.find(paymentId).orElseThrow().nextRun()).isEqualTo(START.plusSeconds(5));
        assertThat(send(create(checkout, "test-order")).statusCode()).isEqualTo(200);
        assertThat(work.find(paymentId).orElseThrow().nextRun()).isEqualTo(START.plusSeconds(5));
        var restartedJob = new PaymentCollectionJob(new PaymentCollectionService(payments, work, outcomes, feign,
                gateway, transactions, CLOCK, "test-payment", "DEV", Duration.ofSeconds(2)));
        CLOCK.set(START.plusSeconds(4));
        restartedJob.runOnce();
        assertThat(payments.findById(paymentId).orElseThrow().getPaymentUrl()).isNull();
        CLOCK.set(START.plusSeconds(5));
        restartedJob.runOnce();
        var recovered = payments.findById(paymentId).orElseThrow();
        assertThat(recovered.getPaymentUrl()).startsWith(PROVIDER.origin().toString());
        assertThat(work.find(paymentId).orElseThrow().nextRun()).isEqualTo(START.plusSeconds(20));
        CLOCK.set(START.plusSeconds(20));
        job.runOnce();
        assertThat(work.find(paymentId).orElseThrow().nextRun()).isEqualTo(START.plusSeconds(50));
        CLOCK.set(START.plusSeconds(50));
        job.runOnce();
        assertThat(work.find(paymentId).orElseThrow().nextRun()).isEqualTo(START.plusSeconds(110));
        CLOCK.set(START.plusSeconds(110));
        PROVIDER.completePayment(paymentId);
        restartedJob.runOnce();
        assertThat(work.find(paymentId).orElseThrow().nextRun()).isNull();
        var result = jdbc.sql("""
                SELECT provider_transaction_id, status, amount_vnd, currency, completed_at
                FROM payment_collection_observation WHERE payment_id = ?
                """).param(paymentId).query((rs, row) -> Map.of("ref", rs.getString(1),
                "status", rs.getString(2), "amount", rs.getLong(3), "currency", rs.getString(4),
                "completedAt", rs.getTimestamp(5).toInstant())).single();
        assertThat(result).containsEntry("ref", recovered.getProviderTransactionId())
                .containsEntry("status", "SUCCESS").containsEntry("amount", 55000L)
                .containsEntry("currency", "VND").containsEntry("completedAt", CLOCK.instant());
        assertThat(PROVIDER.paymentCount() - before).isEqualTo(1);
        assertThat(body(send(read(checkout, "test-order"))).at("/data/paymentUrl").isNull()).isTrue();
    }

    @Test
    void closedCheckoutStopsDueQueriesForItsKnownPayment() throws Exception {
        UUID checkout = payable();
        assertThat(send(create(checkout, "test-order")).statusCode()).isEqualTo(200);
        UUID paymentId = payments.findByCheckoutSessionId(checkout).orElseThrow().getPaymentId();
        PROVIDER.completePayment(paymentId);
        markClosed(checkout);
        CLOCK.set(START.plusSeconds(5));
        job.runOnce();
        assertThat(jdbc.sql("SELECT count(*) FROM payment_collection_observation WHERE payment_id = ?")
                .param(paymentId).query(Long.class).single()).isZero();
        assertThat(body(send(read(checkout, "test-order"))).at("/data/paymentUrl").isNull()).isTrue();
    }

    @Test
    void mismatchedProviderReferenceCannotReplaceTheCurrentTransaction() throws Exception {
        UUID checkout = payable();
        assertThat(send(create(checkout, "test-order")).statusCode()).isEqualTo(200);
        UUID paymentId = payments.findByCheckoutSessionId(checkout).orElseThrow().getPaymentId();
        String storedRef = "different-persisted-transaction";
        jdbc.sql("UPDATE payments SET provider_transaction_id = ? WHERE payment_id = ?")
                .params(storedRef, paymentId).update();
        PROVIDER.completePayment(paymentId);
        CLOCK.set(START.plusSeconds(5));
        job.runOnce();
        var payment = payments.findById(paymentId).orElseThrow();
        assertThat(payment.getProviderTransactionId()).isEqualTo(storedRef);
        assertThat(payment.isPending()).isTrue();
        assertThat(jdbc.sql("SELECT count(*) FROM payment_collection_observation WHERE payment_id = ?")
                .param(paymentId).query(Long.class).single()).isZero();
        assertThat(work.find(paymentId).orElseThrow().nextRun()).isEqualTo(START.plusSeconds(20));
    }

    private void markClosed(UUID checkout) {
        jdbc.sql("""
                INSERT INTO payment_checkout_guard(checkout_session_id, closed_at) VALUES (?, ?)
                ON CONFLICT (checkout_session_id) DO UPDATE SET closed_at = EXCLUDED.closed_at
                """).params(checkout, Timestamp.from(CLOCK.instant())).update();
    }

    private static UUID payable() {
        UUID checkout = UUID.randomUUID();
        PAYABLES.put(checkout, new Payable(UUID.randomUUID(), "AWAITING_PAYMENT", START.plusSeconds(900),
                55000L, List.of(new Order(UUID.randomUUID()))));
        return checkout;
    }

    private HttpRequest create(UUID checkout, String token) {
        return request("/checkouts/" + checkout + "/provider-session", token)
                .POST(HttpRequest.BodyPublishers.noBody()).build();
    }

    private HttpRequest read(UUID checkout, String token) {
        return request("/by-checkout/" + checkout, token).GET().build();
    }

    private HttpRequest.Builder request(String path, String token) {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/internal/v1/payments" + path))
                .timeout(Duration.ofSeconds(8));
        return token == null ? request : request.header("X-Service-Token", token);
    }

    private static HttpResponse<String> send(HttpRequest request) throws Exception {
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static JsonNode body(HttpResponse<String> response) {
        return JSON.readTree(response.body());
    }

    private static final class TestClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(START);
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
        @Override public Instant instant() { return now.get(); }
        void set(Instant instant) { now.set(instant); }
    }
}
