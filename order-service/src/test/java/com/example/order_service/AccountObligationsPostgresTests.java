package com.example.order_service;

import com.example.order_service.service.AccountObligationsService;
import com.example.order_service.service.AccountObligationsService.AccountType;
import com.example.order_service.service.AccountObligationsService.SourceUnavailable;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false", "logging.level.root=WARN",
        "order.checkout.quote-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "order.internal.tokens.identity=test-identity", "order.internal.tokens.product=test-product",
        "order.internal.tokens.payment=test-payment", "order.payment.service-token=test-order-to-payment"
})
class AccountObligationsPostgresTests extends OrderPostgresTestSupport {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PAID_AT = "2026-11-20T00:01:00Z";
    private static final Map<UUID, Map<String, Object>> PAYMENTS = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<String, Object>> ORDER_REFUNDS = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<String, Object>> FULL_REFUNDS = new ConcurrentHashMap<>();
    private static final List<List<UUID>> ORDER_FILTERS = new CopyOnWriteArrayList<>();
    private static final List<List<UUID>> CHECKOUT_FILTERS = new CopyOnWriteArrayList<>();
    private static final AtomicReference<Runnable> BEFORE_LOOKUP = new AtomicReference<>();
    private static final AtomicReference<Reply> PAYMENT_OVERRIDE = new AtomicReference<>();
    private static final AtomicReference<Reply> REFUND_OVERRIDE = new AtomicReference<>();
    private static final HttpServer PAYMENT_HTTP = fixture();

    @Autowired AccountObligationsService obligations;
    @Autowired JdbcTemplate jdbc;
    @LocalServerPort int port;

    @DynamicPropertySource
    static void paymentSource(DynamicPropertyRegistry config) {
        config.add("order.payment.base-url", () -> "http://127.0.0.1:" + PAYMENT_HTTP.getAddress().getPort());
    }

    @BeforeEach
    void reset() {
        PAYMENTS.clear();
        ORDER_REFUNDS.clear();
        FULL_REFUNDS.clear();
        ORDER_FILTERS.clear();
        CHECKOUT_FILTERS.clear();
        BEFORE_LOOKUP.set(null);
        PAYMENT_OVERRIDE.set(null);
        REFUND_OVERRIDE.set(null);
    }

    @Test
    void scopesOpenOrdersAndRefundBatchesBeforeCallingPayment() {
        var checkout = checkout("AWAITING_PAYMENT", null);
        UUID firstSeller = UUID.randomUUID();
        UUID secondSeller = UUID.randomUUID();
        UUID first = order(checkout, firstSeller, "AWAITING_PAYMENT");
        UUID second = order(checkout, secondSeller, "AWAITING_PAYMENT");
        assertThat(obligations.read(checkout.customer(), AccountType.CUSTOMER).openOrderIds())
                .containsExactlyInAnyOrder(first, second);
        assertThat(obligations.read(firstSeller, AccountType.SELLER).openOrderIds()).containsExactly(first);
        assertThat(obligations.read(secondSeller, AccountType.SELLER).openOrderIds()).containsExactly(second);
        assertThat(obligations.read(firstSeller, AccountType.CUSTOMER).hasOpenOrders()).isFalse();
        assertThat(ORDER_FILTERS).hasSize(3);
        assertThat(ORDER_FILTERS.getFirst()).containsExactlyInAnyOrder(first, second);
        assertThat(ORDER_FILTERS.get(1)).containsExactly(first);
        assertThat(ORDER_FILTERS.getLast()).containsExactly(second);
        assertThat(CHECKOUT_FILTERS).hasSize(3).allSatisfy(ids -> assertThat(ids).containsExactly(checkout.id()));

        PAYMENTS.put(checkout.id(), payment(checkout, "SUCCESS", false));
        assertThat(obligations.read(firstSeller, AccountType.SELLER).hasPendingPayment()).isTrue();
    }

    @Test
    void keepsClosedCheckoutAndFullRefundObligationsUntilVerifiedSuccess() {
        var checkout = checkout("CLOSED", null);
        UUID seller = UUID.randomUUID();
        order(checkout, seller, "EXPIRED");
        var verifiedAbsent = obligations.read(seller, AccountType.SELLER);
        assertThat(verifiedAbsent.hasOpenOrders()).isFalse();
        assertThat(verifiedAbsent.hasPendingPayment()).isFalse();

        PAYMENTS.put(checkout.id(), payment(checkout, "PENDING", true));
        assertThat(obligations.read(seller, AccountType.SELLER).hasPendingPayment()).isTrue();
        PAYMENTS.put(checkout.id(), payment(checkout, "SUCCESS", false));
        var beforeCloseEvent = obligations.read(seller, AccountType.SELLER);
        assertThat(beforeCloseEvent.hasPendingPayment()).isTrue();
        assertThat(beforeCloseEvent.hasUnresolvedRefund()).isFalse();

        PAYMENTS.put(checkout.id(), payment(checkout, "SUCCESS", true));
        assertUnavailable(seller, AccountType.SELLER);
        for (String status : List.of("PENDING", "FAILED", "SUCCESS")) {
            FULL_REFUNDS.put(checkout.id(), refund(checkout, null, status));
            var result = obligations.read(seller, AccountType.SELLER);
            assertThat(result.hasOpenOrders()).isFalse();
            assertThat(result.hasPendingPayment()).isEqualTo(!status.equals("SUCCESS"));
            assertThat(result.hasUnresolvedRefund()).isEqualTo(!status.equals("SUCCESS"));
            assertThat(obligations.read(checkout.customer(), AccountType.CUSTOMER)).isEqualTo(result);
        }
    }

    @Test
    void excludesAnotherSellersOrderRefundAndRequiresTheExpectedRefundForItsOwner() {
        var checkout = checkout("PAID", UUID.randomUUID());
        UUID firstSeller = UUID.randomUUID();
        UUID secondSeller = UUID.randomUUID();
        UUID first = order(checkout, firstSeller, "PACKING");
        UUID cancelled = order(checkout, secondSeller, "CANCELLED");
        PAYMENTS.put(checkout.id(), payment(checkout, "SUCCESS", false));

        var firstResult = obligations.read(firstSeller, AccountType.SELLER);
        assertThat(firstResult.openOrderIds()).containsExactly(first);
        assertThat(firstResult.hasPendingPayment()).isFalse();
        assertThat(firstResult.hasUnresolvedRefund()).isFalse();
        assertThat(ORDER_FILTERS.getLast()).containsExactly(first);
        assertUnavailable(secondSeller, AccountType.SELLER);
        for (String status : List.of("PENDING", "FAILED", "SUCCESS")) {
            ORDER_REFUNDS.put(cancelled, refund(checkout, cancelled, status));
            assertThat(obligations.read(secondSeller, AccountType.SELLER).hasUnresolvedRefund())
                    .isEqualTo(!status.equals("SUCCESS"));
            assertThat(obligations.read(checkout.customer(), AccountType.CUSTOMER).hasUnresolvedRefund())
                    .isEqualTo(!status.equals("SUCCESS"));
        }
        REFUND_OVERRIDE.set(new Reply(200, Map.of("data", List.of(refund(checkout, cancelled, "SUCCESS")))));
        assertUnavailable(firstSeller, AccountType.SELLER);
    }

    @Test
    void failsClosedForMissingOrMalformedSourcesAndConcurrentOrderChanges() {
        var checkout = checkout("AWAITING_PAYMENT", null);
        order(checkout, UUID.randomUUID(), "AWAITING_PAYMENT");
        UUID customer = checkout.customer();
        assertThat(obligations.read(customer, AccountType.CUSTOMER).hasOpenOrders()).isTrue();
        jdbc.update("update orders.checkout_outbox set delivered_at = now() where checkout_session_id = ?", checkout.id());
        assertUnavailable(customer, AccountType.CUSTOMER);
        for (int status : List.of(401, 404, 503)) {
            PAYMENT_OVERRIDE.set(new Reply(status, Map.of()));
            assertUnavailable(customer, AccountType.CUSTOMER);
        }
        PAYMENT_OVERRIDE.set(new Reply(200, Map.of("meta", Map.of())));
        assertUnavailable(customer, AccountType.CUSTOMER);

        var valid = payment(checkout, "PENDING", false);
        for (var entry : Map.of("customerId", UUID.randomUUID(), "checkoutSessionId", UUID.randomUUID(),
                "currency", "USD", "amountVnd", 1L, "status", "UNKNOWN").entrySet()) {
            var corrupt = new HashMap<>(valid);
            corrupt.put(entry.getKey(), entry.getValue());
            PAYMENTS.put(checkout.id(), corrupt);
            assertUnavailable(customer, AccountType.CUSTOMER);
        }
        PAYMENTS.put(checkout.id(), valid);
        REFUND_OVERRIDE.set(new Reply(200, Collections.singletonMap("data", null)));
        assertUnavailable(customer, AccountType.CUSTOMER);
        BEFORE_LOOKUP.set(() -> jdbc.update("""
                update orders.purchase_order set lifecycle_version=lifecycle_version+1 where checkout_session_id=?
                """, checkout.id()));
        assertUnavailable(customer, AccountType.CUSTOMER);
        assertThat(obligations.read(customer, AccountType.CUSTOMER).hasPendingPayment()).isTrue();

        var paid = checkout("PAID", UUID.randomUUID());
        order(paid, UUID.randomUUID(), "DELIVERED");
        assertUnavailable(paid.customer(), AccountType.CUSTOMER);
        var wrongPayment = payment(paid, "SUCCESS", false);
        wrongPayment.put("paymentId", UUID.randomUUID());
        PAYMENTS.put(paid.id(), wrongPayment);
        assertUnavailable(paid.customer(), AccountType.CUSTOMER);
    }

    @Test
    void servesExactCanonicalVersionsForBothAccountTypes() throws Exception {
        UUID empty = UUID.fromString("00000000-0000-0000-0000-000000000053");
        var emptyCustomer = data(account(empty, "CUSTOMER", "test-identity"));
        var emptySeller = data(account(empty, "SELLER", "test-identity"));
        assertThat(emptyCustomer.path("hasOpenOrders").asBoolean()).isFalse();
        assertThat(emptyCustomer.path("openOrderIds").isArray()).isTrue();
        assertThat(emptyCustomer.path("openOrderIds").isEmpty()).isTrue();
        assertThat(emptyCustomer.path("hasPendingPayment").asBoolean()).isFalse();
        assertThat(emptyCustomer.path("hasUnresolvedRefund").asBoolean()).isFalse();
        assertThat(version(emptyCustomer)).isEqualTo("99491deae7f0e85a65861aeff6157a87acf8b37134c34dc84c70133ab392d6c3");
        assertThat(version(emptySeller)).isEqualTo("5e0b77c1c11382fc29b2a83453ad431bf72da23734cdac2d9920e3f61b34804f");
        assertThat(ORDER_FILTERS).isEmpty();
        assertThat(CHECKOUT_FILTERS).isEmpty();

        var checkout = checkout("AWAITING_PAYMENT", null);
        UUID seller = UUID.randomUUID();
        UUID higher = UUID.fromString("f" + UUID.randomUUID().toString().substring(1));
        UUID lower = UUID.fromString("0" + UUID.randomUUID().toString().substring(1));
        order(checkout, seller, "AWAITING_PAYMENT", higher);
        order(checkout, UUID.randomUUID(), "AWAITING_PAYMENT", lower);
        var customer = data(account(checkout.customer(), "CUSTOMER", "test-identity"));
        assertThat(customer.path("openOrderIds").get(0).asString()).isEqualTo(lower.toString());
        assertThat(customer.path("openOrderIds").get(1).asString()).isEqualTo(higher.toString());
        assertThat(version(customer)).isEqualTo(digest("CUSTOMER", checkout.customer(), false, lower, higher));
        var sellerData = data(account(seller, "SELLER", "test-identity"));
        assertThat(version(sellerData)).isEqualTo(digest("SELLER", seller, false, higher));
        var repeated = data(account(checkout.customer(), "CUSTOMER", "test-identity"));
        assertThat(Instant.parse(repeated.path("evaluatedAt").asString()))
                .isAfter(Instant.parse(customer.path("evaluatedAt").asString()));
        assertThat(version(repeated)).isEqualTo(version(customer));

        PAYMENTS.put(checkout.id(), payment(checkout, "PENDING", false));
        var pending = data(account(checkout.customer(), "CUSTOMER", "test-identity"));
        assertThat(version(pending)).isEqualTo(digest("CUSTOMER", checkout.customer(), true, lower, higher));
        assertThat(version(pending)).isNotEqualTo(version(customer));
    }

    @Test
    void permitsOnlyIdentityAndReportsUnverifiedSourcesWithoutChangingTheLegacySellerRoute() throws Exception {
        var checkout = checkout("AWAITING_PAYMENT", null);
        UUID seller = UUID.randomUUID();
        order(checkout, seller, "AWAITING_PAYMENT");
        var account = account(seller, "SELLER", "test-identity");
        assertThat(data(account).path("hasOpenOrders").asBoolean()).isTrue();
        var legacy = call("/internal/v1/orders/sellers/" + seller + "/open-orders", "test-identity");
        assertThat(data(legacy).path("hasOpenOrders").asBoolean()).isFalse();
        assertError(account(seller, "SELLER"), 401, "UNAUTHENTICATED");
        assertError(account(seller, "SELLER", "test-identity", "test-product"), 401, "UNAUTHENTICATED");
        assertError(account(seller, "SELLER", "test-product"), 403, "FORBIDDEN");
        assertError(account(seller, "SELLER", "test-payment"), 403, "FORBIDDEN");
        assertError(account(seller, "ADMIN", "test-identity"), 400, "VALIDATION_ERROR");
        PAYMENT_OVERRIDE.set(new Reply(503, Map.of()));
        assertError(account(seller, "SELLER", "test-identity"), 503, "SERVICE_UNAVAILABLE");
    }

    private HttpResponse<String> account(UUID user, String type, String... tokens) throws Exception {
        return call("/internal/v1/orders/accounts/" + user + "/obligations?accountType=" + type, tokens);
    }

    private HttpResponse<String> call(String path, String... tokens) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET();
        for (String token : tokens) {
            request.header("X-Service-Token", token);
        }
        try (var client = HttpClient.newHttpClient()) {
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private static JsonNode data(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return JSON.readTree(response.body()).path("data");
    }

    private static void assertError(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        var envelope = JSON.readTree(response.body());
        assertThat(envelope.has("data")).isFalse();
        assertThat(envelope.path("error").path("code").asString()).isEqualTo(code);
    }

    private static String version(JsonNode data) {
        return data.path("orderStateVersion").asString();
    }

    private static String digest(String type, UUID owner, boolean pending, UUID... openOrders) throws Exception {
        var canonical = JSON.writeValueAsBytes(List.of(type, owner, true, openOrders, pending, false));
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
    }

    private void assertUnavailable(UUID owner, AccountType accountType) {
        assertThatThrownBy(() -> obligations.read(owner, accountType)).isInstanceOf(SourceUnavailable.class);
    }

    private Checkout checkout(String state, UUID boundPayment) {
        var checkout = new Checkout(UUID.randomUUID(), UUID.randomUUID(),
                boundPayment == null ? UUID.randomUUID() : boundPayment);
        jdbc.update("""
                insert into orders.checkout_session (checkout_session_id, checkout_intent_id, customer_id,
                    checkout_source, idempotency_key, request_hash, total_amount_vnd, currency, status, payment_id,
                    close_reason, address_id, paid_at, created_at, expires_at)
                values (?, ?, ?, 'BUY_NOW', ?, 'hash', 2500, 'VND', ?, ?, ?, ?, cast(? as timestamptz),
                    '2026-11-20T00:00:00Z', '2026-11-20T00:15:00Z')
                """, checkout.id(), UUID.randomUUID(), checkout.customer(), UUID.randomUUID().toString(), state,
                boundPayment, state.equals("CLOSED") ? "EXPIRED" : null, UUID.randomUUID(), state.equals("PAID") ? PAID_AT : null);
        jdbc.update("""
                insert into orders.checkout_outbox (command_id, checkout_session_id, command_type, expires_at, created_at)
                values (?, ?, 'CreateProviderSession', '2026-11-20T00:15:00Z', '2026-11-20T00:00:00Z')
                """, UUID.randomUUID(), checkout.id());
        return checkout;
    }

    private UUID order(Checkout checkout, UUID seller, String state) {
        return order(checkout, seller, state, UUID.randomUUID());
    }

    private UUID order(Checkout checkout, UUID seller, String state, UUID id) {
        jdbc.update("""
                insert into orders.purchase_order (order_id, checkout_session_id, seller_id, seller_name_snapshot,
                    customer_id, total_amount_vnd, currency, commission_bps_snapshot, recipient_name, recipient_phone,
                    province_code, province_name, ward_code, ward_name, address_detail, status, created_at, updated_at, paid_at)
                values (?, ?, ?, 'Shop', ?, 1000, 'VND', 500, 'Recipient', '0123456789', '01', 'City', '01', 'Ward',
                    'Address', ?, now(), now(), cast(? as timestamptz))
                """, id, checkout.id(), seller, checkout.customer(), state,
                List.of("AWAITING_PAYMENT", "EXPIRED").contains(state) ? null : PAID_AT);
        return id;
    }

    private static Map<String, Object> payment(Checkout checkout, String status, boolean closed) {
        var data = new HashMap<String, Object>(Map.of("checkoutSessionId", checkout.id(), "customerId", checkout.customer(),
                "paymentId", checkout.payment(), "status", status, "amountVnd", 2500L, "currency", "VND", "isSimulated", true));
        if (closed) {
            data.put("collectionClosedAt", "2026-11-20T00:15:00Z");
        }
        if (status.equals("SUCCESS")) {
            data.put("completedAt", PAID_AT);
        }
        return data;
    }

    private static Map<String, Object> refund(Checkout checkout, UUID orderId, String status) {
        var data = new HashMap<String, Object>();
        data.put("refundId", UUID.randomUUID());
        data.put("paymentId", checkout.payment());
        data.put("orderId", orderId);
        data.put("scope", orderId == null ? "FULL_CHECKOUT" : "ORDER");
        data.put("amountVnd", orderId == null ? 2500L : 1000L);
        data.put("status", status);
        data.put("completedAt", status.equals("PENDING") ? null : "2026-11-20T00:16:00Z");
        data.put("retryCount", 0);
        data.put("retryCycle", 0);
        data.put("customerRetryRequired", false);
        data.put("canRetryRefund", false);
        data.put("isSimulated", true);
        return data;
    }

    private static HttpServer fixture() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/internal/v1/payments/by-checkout/", exchange -> {
                if (!authorized(exchange)) return;
                var before = BEFORE_LOOKUP.getAndSet(null);
                if (before != null) before.run();
                var override = PAYMENT_OVERRIDE.getAndSet(null);
                UUID id = UUID.fromString(exchange.getRequestURI().getPath().split("/")[5]);
                respond(exchange, override != null ? override : new Reply(200, Collections.singletonMap("data", PAYMENTS.get(id))));
            });
            server.createContext("/internal/v1/payments/refunds", AccountObligationsPostgresTests::refunds);
            server.start();
            Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().unstarted(() -> server.stop(0)));
            return server;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static void refunds(HttpExchange exchange) throws IOException {
        if (!authorized(exchange)) return;
        var override = REFUND_OVERRIDE.getAndSet(null);
        if (override != null) {
            respond(exchange, override);
            return;
        }
        var filters = new HashMap<String, List<UUID>>();
        String query = exchange.getRequestURI().getRawQuery();
        if (query != null) {
            for (String part : query.split("&")) {
                String[] param = part.split("=", 2);
                for (String id : URLDecoder.decode(param[1], StandardCharsets.UTF_8).split(",")) {
                    filters.computeIfAbsent(param[0], ignored -> new ArrayList<>()).add(UUID.fromString(id));
                }
            }
        }
        if (filters.size() != 1 || (!filters.containsKey("orderIds") && !filters.containsKey("checkoutSessionIds"))) {
            respond(exchange, new Reply(400, Map.of()));
            return;
        }
        boolean orders = filters.containsKey("orderIds");
        var ids = filters.values().iterator().next();
        (orders ? ORDER_FILTERS : CHECKOUT_FILTERS).add(List.copyOf(ids));
        var source = orders ? ORDER_REFUNDS : FULL_REFUNDS;
        respond(exchange, new Reply(200, Map.of("data", ids.stream().filter(source::containsKey).map(source::get).toList())));
    }

    private static boolean authorized(HttpExchange exchange) throws IOException {
        if (exchange.getRequestMethod().equals("GET") && !exchange.getRequestHeaders().containsKey("Authorization")
                && List.of("test-order-to-payment").equals(exchange.getRequestHeaders().get("X-Service-Token"))) {
            return true;
        }
        respond(exchange, new Reply(401, Map.of()));
        return false;
    }

    private static void respond(HttpExchange exchange, Reply reply) throws IOException {
        byte[] bytes = JSON.writeValueAsBytes(reply.body());
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(reply.status(), bytes.length);
        try (var out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private record Checkout(UUID id, UUID customer, UUID payment) {}
    private record Reply(int status, Object body) {}
}
