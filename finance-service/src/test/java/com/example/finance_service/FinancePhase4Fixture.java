package com.example.finance_service;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false", "logging.level.root=WARN", "finance.identity.service-token=phase4-identity",
        "finance.order.credential=phase4-order", "finance.payment.credential=phase4-payment"
})
abstract class FinancePhase4Fixture {
    static final JsonMapper JSON = JsonMapper.builder().build();
    static final UUID ADMIN = UUID.randomUUID();
    static final String READ_TIME = "2026-11-24T07:00:00Z";
    static final AtomicReference<Map<String,Object>> PAYMENT = new AtomicReference<>();
    static final Map<UUID, Boolean> SELLERS = new ConcurrentHashMap<>();
    static final java.util.Set<UUID> PENDING_ONLY = ConcurrentHashMap.newKeySet();
    static final RSAKey KEY;
    static final HttpServer PEERS;
    static {
        try {
            KEY = new RSAKeyGenerator(2048).keyID("finance-phase4").generate();
            PEERS = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            PEERS.createContext("/.well-known/jwks.json",
                    exchange -> reply(exchange, 200, new JWKSet(KEY.toPublicJWK()).toJSONObject()));
            PEERS.createContext("/internal/v1/identity/auth/authorization-context", FinancePhase4Fixture::identity);
            PEERS.createContext("/api/v1/identity/admin/sellers/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                UUID seller = UUID.fromString(path.substring(path.lastIndexOf('/') + 1));
                reply(exchange, 200, Map.of("data", Map.of("user", Map.of("userId", seller),
                        "sellerProfile", Map.of("sellerName", "Finance test seller"))));
            });
            PEERS.createContext("/internal/v1/orders/reconciliation-totals", exchange ->
                    reply(exchange, 200, Map.of("data", Map.of("paidCheckoutCount", 0, "paidCheckoutTotalVnd", 0, "readAt", READ_TIME))));
            PEERS.createContext("/internal/v1/payments/totals", exchange -> {
                var payload = PAYMENT.get();
                reply(exchange, payload == null ? 503 : 200, payload == null ? Map.of() : Map.of("data", payload));
            });
            PEERS.start();
            Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().unstarted(() -> PEERS.stop(0)));
        } catch (Exception failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    @DynamicPropertySource
    static void sources(DynamicPropertyRegistry registry) {
        String origin = "http://127.0.0.1:" + PEERS.getAddress().getPort();
        registry.add("finance.identity.base-url", () -> origin);
        registry.add("finance.order.base-url", () -> origin);
        registry.add("finance.payment.base-url", () -> origin);
    }

    @Autowired JdbcTemplate db;
    @LocalServerPort int port;

    UUID seller(long amount, boolean banned) {
        assertThat(db.queryForObject("select current_database()", String.class)).startsWith("finance_test_");
        UUID seller = UUID.randomUUID();
        UUID wallet = UUID.randomUUID();
        UUID settlement = UUID.randomUUID();
        UUID order = UUID.randomUUID();
        db.update("""
                insert into finance.wallet(wallet_id,owner_type,owner_id,currency,pending_balance_vnd,available_balance_vnd,
                    total_withdrawn_vnd,row_version,created_at,updated_at) values (?,'SELLER',?,'VND',0,?,0,0,now(),now())
                """, wallet, seller, amount);
        db.update("""
                insert into finance.settlement(settlement_id,order_id,payment_id,seller_id,gross_amount_vnd,commission_bps_snapshot,
                    commission_amount_vnd,seller_amount_vnd,status,created_at,settled_at) values (?,?,?,?,?,0,0,?,'SETTLED',now(),now())
                """, settlement, order, UUID.randomUUID(), seller, amount, amount);
        db.update("""
                insert into finance.wallet_transaction(wallet_transaction_id,wallet_id,settlement_id,reference_type,reference_id,
                    transaction_type,pending_delta_vnd,available_delta_vnd,withdrawn_delta_vnd,business_event_key,created_at)
                values (?,?,?,'ORDER',?,'SETTLEMENT_RELEASED',0,?,0,?,now())
                """, UUID.randomUUID(), wallet, settlement, order, amount, "fixture:" + order);
        SELLERS.put(seller, banned);
        return seller;
    }

    int countWithdrawals(UUID seller) {
        return db.queryForObject("select count(*) from finance.withdrawal where seller_id=?", Integer.class, seller);
    }

    HttpResponse<String> withdraw(UUID seller, String key, long amount) throws Exception {
        String uuid = UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        return send(request(seller, "/api/v1/finance/seller/withdrawals").header("Idempotency-Key", uuid)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(Map.of("amountVnd", amount)))).build());
    }

    HttpResponse<String> get(UUID actor, String path) throws Exception {
        return send(request(actor, path).GET().build());
    }

    JsonNode json(HttpResponse<String> response) { return JSON.readTree(response.body()); }

    private HttpRequest.Builder request(UUID actor, String path) throws Exception {
        var claims = new JWTClaimsSet.Builder().subject(actor.toString()).issuer("ecommerce-identity").audience("ecommerce-api")
                .expirationTime(Date.from(Instant.now().plusSeconds(120))).claim("sessionId", UUID.randomUUID().toString())
                .claim("authzVersion", 1).build();
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(KEY));
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).header("Authorization", "Bearer " + jwt.serialize());
    }

    private static HttpResponse<String> send(HttpRequest request) throws Exception {
        try (var client = HttpClient.newHttpClient()) { return client.send(request, HttpResponse.BodyHandlers.ofString()); }
    }

    private static void identity(HttpExchange exchange) throws IOException {
        try {
            var bearer = exchange.getRequestHeaders().getFirst("Authorization");
            if (!"phase4-identity".equals(exchange.getRequestHeaders().getFirst("X-Service-Token")) || bearer == null) {
                reply(exchange, 401, Map.of());
                return;
            }
            UUID id = UUID.fromString(SignedJWT.parse(bearer.substring(7)).getJWTClaimsSet().getSubject());
            boolean admin = ADMIN.equals(id);
            if (!admin && !SELLERS.containsKey(id)) {
                reply(exchange, 401, Map.of());
                return;
            }
            boolean banned = Boolean.TRUE.equals(SELLERS.get(id));
            boolean pendingOnly = PENDING_ONLY.contains(id);
            var context = new java.util.LinkedHashMap<String, Object>();
            context.put("userId", id);
            context.put("accountType", admin ? "ADMIN" : "SELLER");
            context.put("accountStatus", banned ? "BANNED" : "ACTIVE");
            context.put("userStatus", "ACTIVE");
            context.put("accessMode", pendingOnly ? "OBLIGATIONS_READ_ONLY" : banned ? "WITHDRAWAL_ONLY" : "FULL");
            context.put("authzVersion", 1);
            context.put("sessionActive", true);
            context.put("roles", List.of(admin ? "ADMIN" : "SELLER"));
            context.put("permissions", admin ? List.of("operations:read:any")
                    : pendingOnly ? List.of("wallet:read:own") : List.of("wallet:read:own", "withdrawal:create:own"));
            if (!admin) context.put("sellerProfile", Map.of("approvalStatus", "APPROVED", "status", banned ? "BANNED" : "ACTIVE"));
            reply(exchange, 200, Map.of("data", context));
        } catch (java.text.ParseException invalid) {
            reply(exchange, 401, Map.of());
        }
    }

    private static void reply(HttpExchange exchange, int status, Object payload) throws IOException {
        try (exchange) {
            var data = JSON.writeValueAsBytes(payload);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, data.length);
            exchange.getResponseBody().write(data);
        }
    }
}
