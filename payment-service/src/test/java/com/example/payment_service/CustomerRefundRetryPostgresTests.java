package com.example.payment_service;

import com.example.payment_service.entity.Refund;
import com.example.payment_service.entity.RefundScope;
import com.example.payment_service.repository.RefundRepository;
import com.example.payment_service.service.FullCheckoutRefundService;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "payment.refund.query-interval=60s")
class CustomerRefundRetryPostgresTests extends PaymentOutcomePostgresFixture {
    private static final RSAKey KEY;
    private static final HttpServer IDENTITY;
    private static final AtomicReference<String> ACCESS = new AtomicReference<>("OPEN_ORDERS_ONLY");

    static {
        try {
            KEY = new RSAKeyGenerator(2048).keyID("p3-refund").generate();
            IDENTITY = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            IDENTITY.createContext("/.well-known/jwks.json", exchange -> {
                byte[] data = json.writeValueAsBytes(new JWKSet(KEY.toPublicJWK()).toJSONObject());
                exchange.sendResponseHeaders(200, data.length);
                try (var body = exchange.getResponseBody()) { body.write(data); }
            });
            IDENTITY.createContext("/internal/v1/identity/auth/authorization-context", exchange -> {
                try {
                    if (!"payment-test-identity".equals(exchange.getRequestHeaders().getFirst("X-Service-Token"))) {
                        exchange.sendResponseHeaders(403, -1);
                        exchange.close();
                        return;
                    }
                    var token = SignedJWT.parse(exchange.getRequestHeaders().getFirst("Authorization").substring(7));
                    String mode = ACCESS.get();
                    boolean seller = "SELLER".equals(mode);
                    boolean limited = !seller;
                    var permissions = "OPEN_ORDERS_ONLY".equals(mode) ? List.of("order:read:own", "refund:retry:own")
                            : List.of("order:read:own");
                    byte[] data = json.writeValueAsBytes(Map.of("data", Map.of(
                            "userId", token.getJWTClaimsSet().getSubject(), "accountType", seller ? "SELLER" : "CUSTOMER",
                            "accountStatus", limited ? "BANNED" : "ACTIVE", "userStatus", "ACTIVE",
                            "accessMode", seller ? "FULL" : mode, "authzVersion", 4, "sessionActive", true,
                            "roles", List.of(seller ? "SELLER" : "CUSTOMER"), "permissions", permissions,
                            "customerProfile", Map.of("status", limited ? "BANNED" : "ACTIVE"))));
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, data.length);
                    try (var body = exchange.getResponseBody()) { body.write(data); }
                } catch (java.text.ParseException invalid) {
                    exchange.sendResponseHeaders(400, -1);
                    exchange.close();
                }
            });
            IDENTITY.start();
        } catch (Exception failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    @DynamicPropertySource
    static void authorization(DynamicPropertyRegistry registry) {
        registry.add("payment.identity.base-url", () -> "http://127.0.0.1:" + IDENTITY.getAddress().getPort());
        registry.add("payment.identity.service-token", () -> "payment-test-identity");
    }

    @AfterAll
    static void stopIdentity() {
        IDENTITY.stop(0);
    }

    @Autowired RefundRepository refunds;
    @Autowired FullCheckoutRefundService worker;

    @Test
    void concurrentCustomerRequestsOpenOneCycleAndReplayStaysStable() throws Exception {
        ACCESS.set("OPEN_ORDERS_ONLY");
        var payment = preparePayment().payment();
        openAtProvider(payment);
        assertThat(sendFixture(provider.completePayment(payment.getPaymentId()))).isEqualTo(204);
        publishClose(payment.getCheckoutSessionId(), payment.getCheckoutSessionId(), payment.getCustomerId(),
                UUID.randomUUID(), "EXPIRED", "order-service", 1);
        await(() -> refunds.findByPaymentIdAndScope(payment.getPaymentId(), RefundScope.FULL_CHECKOUT).isPresent());
        Refund refund = refunds.findByPaymentIdAndScope(payment.getPaymentId(), RefundScope.FULL_CHECKOUT).orElseThrow();
        exhaust(refund.getRefundId());
        String owner = token(payment.getCustomerId());
        var list = send("GET", "/api/v1/payments/refunds?checkoutSessionId=" + payment.getCheckoutSessionId(), owner, null, null);
        assertThat(list.statusCode()).isEqualTo(200);
        assertThat(json.readTree(list.body()).path("data").get(0).path("canRetryRefund").asBoolean()).isTrue();
        assertThat(retry(refund, token(UUID.randomUUID()), UUID.randomUUID(), 0).statusCode()).isEqualTo(404);

        UUID firstKey = UUID.randomUUID(), secondKey = UUID.randomUUID();
        UUID winning;
        HttpResponse<String> accepted;
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = pool.submit(() -> retry(refund, owner, firstKey, 0));
            var second = pool.submit(() -> retry(refund, owner, secondKey, 0));
            var a = first.get();
            var b = second.get();
            assertThat(List.of(a.statusCode(), b.statusCode())).containsExactlyInAnyOrder(202, 409);
            winning = a.statusCode() == 202 ? firstKey : secondKey;
            accepted = a.statusCode() == 202 ? a : b;
        }
        UUID id = refund.getRefundId();
        assertThat(refunds.findById(id).orElseThrow().getRetryCycle()).isOne();
        assertThat(provider.refundPostCount(id)).isEqualTo(4);
        assertThat(code(retry(refund, owner, winning, 1))).isEqualTo("IDEMPOTENCY_KEY_REUSED");
        ACCESS.set("OBLIGATIONS_READ_ONLY");
        assertThat(retry(refund, owner, winning, 0).statusCode()).isEqualTo(403);
        ACCESS.set("OPEN_ORDERS_ONLY");
        worker.process(id);
        assertThat(provider.refundPostCount(id)).isEqualTo(5);
        assertThat(provider.refundQueryCount(id)).isEqualTo(4);
        assertThat(jdbc.sql("""
                SELECT attempt_number FROM payment_refund_attempts WHERE refund_id=? AND retry_cycle=1
                """).param(id).query(Integer.class).single()).isZero();
        var replay = retry(refund, owner, winning, 0);
        assertThat(replay.statusCode()).isEqualTo(202);
        assertThat(json.readTree(replay.body())).isEqualTo(json.readTree(accepted.body()));
    }

    @Test
    void reviewHoldAndWrongAccountPreventRetryOfOrderRefund() throws Exception {
        ACCESS.set("OPEN_ORDERS_ONLY");
        var payment = preparePayment().payment();
        openAtProvider(payment);
        assertThat(sendFixture(provider.completePayment(payment.getPaymentId()))).isEqualTo(204);
        UUID order = UUID.randomUUID();
        Refund refund = refunds.saveAndFlush(Refund.createOrderRefund(payment.getPaymentId(), order, 10_000L,
                com.example.payment_service.entity.RefundReason.CUSTOMER_CANCEL, "order:" + order));
        jdbc.sql("INSERT INTO payment_refund_work(refund_id,next_run_at) VALUES (?,?)")
                .params(refund.getRefundId(), java.sql.Timestamp.from(START)).update();
        exhaust(refund.getRefundId());
        jdbc.sql("UPDATE payment_refund_work SET review_required_at=statement_timestamp() WHERE refund_id=?")
                .param(refund.getRefundId()).update();
        String owner = token(payment.getCustomerId());
        var listed = send("GET", "/api/v1/payments/refunds?checkoutSessionId=" + payment.getCheckoutSessionId(), owner, null, null);
        assertThat(json.readTree(listed.body()).path("data").get(0).path("canRetryRefund").asBoolean()).isFalse();
        var blocked = retry(refund, owner, UUID.randomUUID(), 0);
        assertThat(blocked.statusCode()).isEqualTo(409);
        assertThat(code(blocked)).isEqualTo("REFUND_RETRY_NOT_SAFE");
        ACCESS.set("SELLER");
        assertThat(retry(refund, owner, UUID.randomUUID(), 0).statusCode()).isEqualTo(403);
        assertThat(provider.refundPostCount(refund.getRefundId())).isEqualTo(4);
    }

    private void exhaust(UUID refundId) {
        for (long elapsed : new long[]{0, 60, 360, 1260}) {
            clock.set(START.plusSeconds(elapsed));
            provider.nextRefundFault(ProviderMockFixture.Fault.REJECT);
            worker.process(refundId);
        }
        assertThat(refunds.findById(refundId).orElseThrow().isCustomerRetryRequired()).isTrue();
    }

    private HttpResponse<String> retry(Refund refund, String bearer, UUID key, int cycle) throws Exception {
        return send("POST", "/api/v1/payments/refunds/" + refund.getRefundId() + "/retry", bearer, key,
                json.writeValueAsString(Map.of("expectedRetryCycle", cycle)));
    }

    private HttpResponse<String> send(String method, String path, String bearer, UUID key, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Authorization", "Bearer " + bearer).header("Content-Type", "application/json");
        if (key != null) {
            builder.header("Idempotency-Key", key.toString());
        }
        return HTTP.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private String code(HttpResponse<String> reply) {
        return json.readTree(reply.body()).path("error").path("code").asString();
    }

    private static String token(UUID customer) throws Exception {
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.getKeyID()).build(),
                new JWTClaimsSet.Builder().subject(customer.toString()).issuer("ecommerce-identity").audience("ecommerce-api")
                        .expirationTime(Date.from(Instant.now().plusSeconds(300))).claim("sessionId", UUID.randomUUID().toString())
                        .claim("authzVersion", 4).build());
        jwt.sign(new RSASSASigner(KEY));
        return jwt.serialize();
    }
}
