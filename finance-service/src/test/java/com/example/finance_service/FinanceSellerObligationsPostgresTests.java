package com.example.finance_service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false", "logging.level.root=WARN",
        "finance.internal.tokens.identity=obligations-identity", "finance.internal.tokens.order=obligations-order",
        "spring.datasource.hikari.connection-init-sql=SET lock_timeout TO '500ms'"
})
class FinanceSellerObligationsPostgresTests {
    private static final String IDENTITY = "obligations-identity";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired JdbcTemplate db;
    @Autowired DataSource dataSource;
    @LocalServerPort int port;

    @Test
    void walletAndSettlement() throws Exception {
        assertTestDatabase();
        UUID owner = wallet(0, 0, 7);
        UUID other = wallet(0, 12_000, 3);
        assertError(read(owner), 401, "UNAUTHENTICATED");
        assertError(read(owner, "obligations-order"), 401, "UNAUTHENTICATED");
        assertError(read(owner, IDENTITY, IDENTITY), 401, "UNAUTHENTICATED");
        assertError(read(UUID.randomUUID(), IDENTITY), 404, "NOT_FOUND");
        assertSnapshot(owner, false, 0, 0, 7);
        assertSnapshot(other, false, 0, 12_000, 3);

        UUID order = UUID.randomUUID();
        settlement(order, UUID.randomUUID(), owner, 0, "PENDING");
        assertSnapshot(owner, true, 0, 0, 7);
        db.update("update finance.settlement set status='PENDING_REFUND' where order_id=?", order);
        assertSnapshot(owner, true, 0, 0, 7);
        db.update("update finance.wallet set pending_balance_vnd=850, row_version=8 where owner_id=?", owner);
        settlement(UUID.randomUUID(), UUID.randomUUID(), owner, 850, "PENDING");

        String before = databaseState(owner, other);
        assertSnapshot(owner, true, 850, 0, 8);
        assertSnapshot(other, false, 0, 12_000, 3);
        assertThat(databaseState(owner, other)).isEqualTo(before);

        try (Connection lock = dataSource.getConnection(); var statement = lock.createStatement()) {
            lock.setAutoCommit(false);
            try {
                statement.execute("lock table finance.settlement in access exclusive mode");
                assertError(read(owner, IDENTITY), 503, "SERVICE_UNAVAILABLE");
            } finally {
                lock.rollback();
            }
        }
        assertSnapshot(owner, true, 850, 0, 8);
        assertThat(databaseState(owner, other)).isEqualTo(before);
    }

    @Test
    void linkedInboxEffects() throws Exception {
        assertTestDatabase();
        UUID owner = wallet(0, 0, 7);
        UUID other = wallet(0, 0, 3);
        UUID checkout = UUID.randomUUID();
        UUID order = UUID.randomUUID();
        UUID otherOrder = UUID.randomUUID();
        UUID payment = UUID.randomUUID();
        settlement(otherOrder, payment, other, 100, "SETTLED");

        UUID refund = refund(UUID.randomUUID(), order, payment, "ORDER", "WAITING_DEPENDENCY");
        UUID paid = inbox("checkout.paid", "order-service", checkout, "WAITING_DEPENDENCY", Map.of(
                "checkoutSessionId", checkout, "customerId", UUID.randomUUID(), "paymentId", payment,
                "source", "BUY_NOW", "paidAt", Instant.now(), "cartItems", List.of(), "orders", List.of(
                        paidOrder(order, owner), paidOrder(otherOrder, other))));

        assertSnapshot(owner, true, 0, 0, 7);
        assertSnapshot(other, false, 0, 0, 3);
        UUID mismatch = refund(UUID.randomUUID(), order, UUID.randomUUID(), "ORDER", "WAITING_DEPENDENCY");
        assertError(read(owner, IDENTITY), 503, "SERVICE_UNAVAILABLE");
        assertSnapshot(other, false, 0, 0, 3);

        db.update("delete from finance.settlement_inbox where event_id=?", mismatch);
        settlement(order, payment, owner, 100, "REFUNDED");
        db.update("update finance.settlement_inbox set status='APPLIED' where event_id in (?,?)", paid, refund);
        refund(UUID.randomUUID(), null, payment, "FULL_CHECKOUT", "WAITING_DEPENDENCY");
        assertSnapshot(owner, false, 0, 0, 7);
        assertSnapshot(other, false, 0, 0, 3);
    }

    @Test
    void failedReviewRequiresUnappliedEvidenceForThisSeller() throws Exception {
        assertTestDatabase();
        UUID owner = wallet(0, 0, 5);
        UUID other = wallet(0, 0, 2);
        UUID order = UUID.randomUUID();
        UUID payment = UUID.randomUUID();
        settlement(order, payment, owner, 100, "SETTLED");
        inbox("order.confirmed", "order-service", order, "FAILED_REVIEW", Map.of("orderId", order,
                "sellerId", owner, "customerId", UUID.randomUUID(), "confirmedAt", Instant.now(), "confirmedBy", "SYSTEM"));
        assertSnapshot(owner, false, 0, 0, 5);

        refund(UUID.randomUUID(), order, payment, "ORDER", "FAILED_REVIEW");
        String before = databaseState(owner, other);
        assertError(read(owner, IDENTITY), 503, "SERVICE_UNAVAILABLE");
        assertSnapshot(other, false, 0, 0, 2);
        assertThat(databaseState(owner, other)).isEqualTo(before);
    }

    @Test
    void orderRefundWaitsForSellerEvidence() throws Exception {
        assertTestDatabase();
        UUID owner = wallet(0, 0, 7);
        UUID other = wallet(0, 0, 3);
        UUID checkout = UUID.randomUUID();
        UUID order = UUID.randomUUID();
        UUID payment = UUID.randomUUID();
        UUID event = refund(UUID.randomUUID(), order, payment, "ORDER", "WAITING_DEPENDENCY");
        String before = databaseState(owner, other);
        assertSnapshot(owner, false, 0, 0, 7);
        assertSnapshot(other, false, 0, 0, 3);
        assertThat(databaseState(owner, other)).isEqualTo(before);
        assertThat(db.queryForObject("select status from finance.settlement_inbox where event_id=?", String.class, event))
                .isEqualTo("WAITING_DEPENDENCY");

        UUID paid = inbox("checkout.paid", "order-service", checkout, "WAITING_DEPENDENCY", Map.of(
                "checkoutSessionId", checkout, "customerId", UUID.randomUUID(), "paymentId", payment,
                "source", "BUY_NOW", "paidAt", Instant.now(), "cartItems", List.of(),
                "orders", List.of(paidOrder(order, owner))));
        String linked = databaseState(owner, other);
        assertSnapshot(owner, true, 0, 0, 7);
        assertSnapshot(other, false, 0, 0, 3);
        assertThat(databaseState(owner, other)).isEqualTo(linked);

        settlement(order, payment, owner, 100, "REFUNDED");
        db.update("update finance.settlement_inbox set status='APPLIED' where event_id in (?,?)", paid, event);
        assertSnapshot(owner, false, 0, 0, 7);
        assertSnapshot(other, false, 0, 0, 3);
    }

    private UUID refund(UUID id, UUID order, UUID payment, String scope, String status) {
        var payload = JSON.createObjectNode().put("refundId", id.toString()).put("paymentId", payment.toString())
                .put("scope", scope).put("amountVnd", 100);
        if (order == null) payload.putNull("orderId");
        else payload.put("orderId", order.toString());
        return inbox("refund.succeeded", "payment-service", id, status, payload);
    }

    private static Map<String, Object> paidOrder(UUID order, UUID seller) {
        return Map.of("orderId", order, "sellerId", seller, "totalAmountVnd", 100, "commissionBps", 0);
    }

    private UUID inbox(String type, String producer, UUID aggregate, String status, Object payload) {
        UUID id = UUID.randomUUID();
        String event = JSON.writeValueAsString(Map.of("eventId", id, "eventType", type, "producer", producer,
                "aggregateId", aggregate, "occurredAt", Instant.now(), "version", 1,
                "actor", Map.of("kind", "SERVICE"), "payload", payload));
        db.update("insert into finance.settlement_inbox(event_id,event_type,payload,status) values (?,?,?::jsonb,?)",
                id, type, event, status);
        return id;
    }

    private void assertTestDatabase() {
        assertThat(db.queryForObject("select current_database()", String.class)).startsWith("finance_test_");
    }

    private UUID wallet(long pending, long available, long version) {
        UUID owner = UUID.randomUUID();
        db.update("""
                insert into finance.wallet(wallet_id,owner_type,owner_id,currency,pending_balance_vnd,available_balance_vnd,
                    total_withdrawn_vnd,row_version,created_at,updated_at) values (?,'SELLER',?,'VND',?,?,0,?,now(),now())
                """, UUID.randomUUID(), owner, pending, available, version);
        return owner;
    }

    private void settlement(UUID order, UUID payment, UUID owner, long sellerAmount, String status) {
        long gross = Math.max(1, sellerAmount);
        long commission = gross - sellerAmount;
        db.update("""
                insert into finance.settlement(settlement_id,order_id,payment_id,seller_id,gross_amount_vnd,
                    commission_bps_snapshot,commission_amount_vnd,seller_amount_vnd,status,created_at,settled_at)
                values (?,?,?,?,?,?,?,?,?,now(),case when ?='SETTLED' then now() end)
                """, UUID.randomUUID(), order, payment, owner, gross, commission == 0 ? 0 : 10_000,
                commission, sellerAmount, status, status);
    }

    private String databaseState(UUID owner, UUID other) {
        return db.queryForObject("""
                select jsonb_build_array(
                    (select jsonb_agg(to_jsonb(w) order by w.wallet_id) from finance.wallet w where w.owner_id in (?,?)),
                    (select jsonb_agg(to_jsonb(s) order by s.order_id) from finance.settlement s where s.seller_id in (?,?)),
                    (select jsonb_agg(to_jsonb(i) order by i.event_id) from finance.settlement_inbox i),
                    (select jsonb_agg(to_jsonb(t) order by t.wallet_transaction_id) from finance.wallet_transaction t))::text
                """, String.class, owner, other, owner, other);
    }

    private void assertSnapshot(UUID owner, boolean hasPending, long pending, long available, long version) throws Exception {
        Instant before = Instant.now();
        var response = read(owner, IDENTITY);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonNode data = JSON.readTree(response.body()).path("data");
        assertThat(data.path("hasPendingFinance").isBoolean()).isTrue();
        assertThat(data.path("hasPendingFinance").asBoolean()).isEqualTo(hasPending);
        assertThat(data.path("pendingBalanceVnd").asLong(-1)).isEqualTo(pending);
        assertThat(data.path("availableBalanceVnd").asLong(-1)).isEqualTo(available);
        assertThat(data.path("financeStateVersion").asLong(-1)).isEqualTo(version);
        assertThat(Instant.parse(data.path("evaluatedAt").asString())).isBetween(before, Instant.now());
    }

    private HttpResponse<String> read(UUID owner, String... tokens) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                + "/internal/v1/finance/sellers/" + owner + "/obligations")).GET();
        for (String token : tokens) {
            request.header("X-Service-Token", token);
        }
        try (var client = HttpClient.newHttpClient()) {
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private static void assertError(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(JSON.readTree(response.body()).path("error").path("code").asString()).isEqualTo(code);
    }
}
