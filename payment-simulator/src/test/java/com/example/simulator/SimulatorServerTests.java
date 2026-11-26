package com.example.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.simulator.SimulatorModels.CreatePaymentRequest;
import com.example.simulator.SimulatorModels.CreatePaymentResponse;
import com.example.simulator.SimulatorModels.CreateRefundRequest;
import com.example.simulator.SimulatorModels.FaultMode;
import com.example.simulator.SimulatorModels.GatewayStatus;
import com.example.simulator.SimulatorModels.PaymentQueryResponse;
import com.example.simulator.SimulatorModels.RefundAttemptAbsence;
import com.example.simulator.SimulatorModels.RefundResponse;
import com.example.simulator.SimulatorModels.ScenarioControlRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpServer;

class SimulatorServerTests {
    private static final String CRED_HEADER = "X-Provider-Key";
    private static final String CRED_VALUE = "test-key";
    private static final String WEBHOOK_SECRET = "test-webhook-secret";

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void paymentConfirmDispatchesSignedWebhookAndPersistsAcrossRestart(@TempDir Path tempDir) throws Exception {
        Path stateFile = tempDir.resolve("sim-state.json");
        List<JsonNode> receivedWebhooks = new CopyOnWriteArrayList<>();
        CountDownLatch webhookLatch = new CountDownLatch(1);
        HttpServer webhookReceiver = startWebhookReceiver(receivedWebhooks, webhookLatch);

        try {
            URI webhookUrl = URI.create("http://127.0.0.1:" + webhookReceiver.getAddress().getPort() + "/webhook");
            Clock clock = Clock.systemUTC();
            SimulatorConfig config = new SimulatorConfig(0, URI.create("http://localhost:8089"),
                    URI.create("http://localhost:5173/checkout"),
                    CRED_HEADER, CRED_VALUE, webhookUrl, WEBHOOK_SECRET, stateFile);

            UUID paymentId = UUID.randomUUID();
            UUID checkoutId = UUID.randomUUID();
            Instant expiresAt = clock.instant().plus(15, ChronoUnit.MINUTES);
            String token;

            try (SimulatorServer server = new SimulatorServer(config, clock)) {
                server.start();
                URI base = URI.create("http://127.0.0.1:" + server.port());

                HttpResponse<String> unauth = http.send(
                        HttpRequest.newBuilder(base.resolve("/v1/payments/" + paymentId)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(401, unauth.statusCode());

                CreatePaymentRequest createReq = new CreatePaymentRequest(paymentId, checkoutId, 150000L, "VND", expiresAt);
                HttpResponse<String> createRes = postProvider(base.resolve("/v1/payments"), createReq);
                assertEquals(200, createRes.statusCode());
                CreatePaymentResponse created = mapper.readValue(createRes.body(), CreatePaymentResponse.class);
                assertEquals(GatewayStatus.PENDING, created.status());
                assertNotNull(created.paymentUrl());
                token = created.paymentUrl().substring(created.paymentUrl().lastIndexOf('/') + 1);

                HttpResponse<String> idempotentRes = postProvider(base.resolve("/v1/payments"), createReq);
                assertEquals(200, idempotentRes.statusCode());

                CreatePaymentRequest conflictReq = new CreatePaymentRequest(paymentId, checkoutId, 200000L, "VND", expiresAt);
                HttpResponse<String> conflictRes = postProvider(base.resolve("/v1/payments"), conflictReq);
                assertEquals(409, conflictRes.statusCode());

                HttpResponse<String> payPage = http.send(
                        HttpRequest.newBuilder(base.resolve("/pay/" + token)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(200, payPage.statusCode());
                assertTrue(payPage.body().contains("150,000 VND"));

                HttpResponse<String> confirmRes = http.send(
                        HttpRequest.newBuilder(base.resolve("/pay/" + token + "/confirm"))
                                .header("Content-Type", "application/x-www-form-urlencoded")
                                .POST(HttpRequest.BodyPublishers.ofString("action=SUCCESS"))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(200, confirmRes.statusCode());
                assertTrue(confirmRes.body().contains("SUCCESS"));
                assertTrue(confirmRes.body().contains("http://localhost:5173/checkout/" + checkoutId));

                assertTrue(webhookLatch.await(5, TimeUnit.SECONDS));
                assertEquals(1, receivedWebhooks.size());
                JsonNode event = receivedWebhooks.get(0);
                assertEquals("payment.result", event.path("type").asText());
                assertEquals(paymentId.toString(), event.path("paymentId").asText());
                assertEquals("SUCCESS", event.path("status").asText());
                assertEquals(150000L, event.path("amountVnd").asLong());

                HttpResponse<String> secondConfirm = http.send(
                        HttpRequest.newBuilder(base.resolve("/pay/" + token + "/confirm"))
                                .header("Accept", "application/json")
                                .header("Content-Type", "application/x-www-form-urlencoded")
                                .POST(HttpRequest.BodyPublishers.ofString("action=FAILED"))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(200, secondConfirm.statusCode());
                JsonNode secondJson = mapper.readTree(secondConfirm.body());
                assertEquals("SUCCESS", secondJson.path("status").asText());
                assertTrue(secondJson.path("alreadyFinalized").asBoolean());
            }

            try (SimulatorServer restarted = new SimulatorServer(config, clock)) {
                restarted.start();
                URI base = URI.create("http://127.0.0.1:" + restarted.port());
                HttpResponse<String> queryRes = http.send(
                        HttpRequest.newBuilder(base.resolve("/v1/payments/" + paymentId))
                                .header(CRED_HEADER, CRED_VALUE)
                                .GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(200, queryRes.statusCode());
                PaymentQueryResponse queried = mapper.readValue(queryRes.body(), PaymentQueryResponse.class);
                assertEquals(GatewayStatus.SUCCESS, queried.status());
                assertNull(queried.paymentUrl());
            }
        } finally {
            webhookReceiver.stop(0);
        }
    }

    @Test
    void refundFlowsAndFailuresMatchProviderContracts(@TempDir Path tempDir) throws Exception {
        Path stateFile = tempDir.resolve("refund-sim-state.json");
        List<JsonNode> receivedWebhooks = new CopyOnWriteArrayList<>();
        CountDownLatch webhookLatch = new CountDownLatch(2);
        HttpServer webhookReceiver = startWebhookReceiver(receivedWebhooks, webhookLatch);

        try {
            URI webhookUrl = URI.create("http://127.0.0.1:" + webhookReceiver.getAddress().getPort() + "/webhook");
            Clock clock = Clock.systemUTC();
            SimulatorConfig config = new SimulatorConfig(0, URI.create("http://localhost:8089"),
                    URI.create("http://localhost:5173/checkout"),
                    CRED_HEADER, CRED_VALUE, webhookUrl, WEBHOOK_SECRET, stateFile);

            try (SimulatorServer server = new SimulatorServer(config, clock)) {
                server.start();
                URI base = URI.create("http://127.0.0.1:" + server.port());

                UUID paymentId = UUID.randomUUID();
                UUID checkoutId = UUID.randomUUID();
                CreatePaymentRequest createReq = new CreatePaymentRequest(
                        paymentId, checkoutId, 100000L, "VND", clock.instant().plus(15, ChronoUnit.MINUTES));
                CreatePaymentResponse created = mapper.readValue(
                        postProvider(base.resolve("/v1/payments"), createReq).body(), CreatePaymentResponse.class);
                String token = created.paymentUrl().substring(created.paymentUrl().lastIndexOf('/') + 1);

                http.send(HttpRequest.newBuilder(base.resolve("/pay/" + token + "/confirm"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString("action=SUCCESS"))
                        .build(), HttpResponse.BodyHandlers.discarding());

                UUID refundId = UUID.randomUUID();
                UUID missingAttemptId = UUID.randomUUID();
                HttpResponse<String> missingRes = http.send(
                        HttpRequest.newBuilder(base.resolve("/v1/refunds/" + refundId + "/attempts/" + missingAttemptId))
                                .header(CRED_HEADER, CRED_VALUE).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(404, missingRes.statusCode());
                RefundAttemptAbsence absence = mapper.readValue(missingRes.body(), RefundAttemptAbsence.class);
                assertEquals("REFUND_ATTEMPT_NOT_FOUND", absence.errorCode());

                Thread.sleep(20);
                HttpResponse<String> repeatedMissingRes = http.send(
                        HttpRequest.newBuilder(base.resolve("/v1/refunds/" + refundId + "/attempts/" + missingAttemptId))
                                .header(CRED_HEADER, CRED_VALUE).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(404, repeatedMissingRes.statusCode());
                RefundAttemptAbsence repeatedAbsence = mapper.readValue(repeatedMissingRes.body(), RefundAttemptAbsence.class);
                assertEquals(absence.providerRefundId(), repeatedAbsence.providerRefundId());
                assertEquals(absence.completedAt(), repeatedAbsence.completedAt());

                CreateRefundRequest closedAttemptPost = new CreateRefundRequest(
                        refundId, missingAttemptId, paymentId, created.providerTransactionId(), 60000L, "VND");
                HttpResponse<String> closedAttemptPostRes = postProvider(base.resolve("/v1/refunds"), closedAttemptPost);
                assertEquals(409, closedAttemptPostRes.statusCode());

                ScenarioControlRequest rejectNext = new ScenarioControlRequest(null, FaultMode.REJECT, null);
                http.send(HttpRequest.newBuilder(base.resolve("/__sim/scenario"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(rejectNext)))
                        .build(), HttpResponse.BodyHandlers.discarding());

                UUID failedAttemptId = UUID.randomUUID();
                CreateRefundRequest failedRefundReq = new CreateRefundRequest(
                        refundId, failedAttemptId, paymentId, created.providerTransactionId(), 60000L, "VND");
                HttpResponse<String> failedRefundRes = postProvider(base.resolve("/v1/refunds"), failedRefundReq);
                assertEquals(200, failedRefundRes.statusCode());
                RefundResponse failedRefund = mapper.readValue(failedRefundRes.body(), RefundResponse.class);
                assertEquals(GatewayStatus.FAILED, failedRefund.status());
                assertEquals("REFUND_DECLINED", failedRefund.errorCode());

                UUID retryAttemptId = UUID.randomUUID();
                CreateRefundRequest retryRefundReq = new CreateRefundRequest(
                        refundId, retryAttemptId, paymentId, created.providerTransactionId(), 60000L, "VND");
                HttpResponse<String> retryRefundRes = postProvider(base.resolve("/v1/refunds"), retryRefundReq);
                assertEquals(200, retryRefundRes.statusCode());
                RefundResponse retryRefund = mapper.readValue(retryRefundRes.body(), RefundResponse.class);
                assertEquals(GatewayStatus.SUCCESS, retryRefund.status());

                UUID overRefundId = UUID.randomUUID();
                CreateRefundRequest overRefundReq = new CreateRefundRequest(
                        overRefundId, UUID.randomUUID(), paymentId, created.providerTransactionId(), 50000L, "VND");
                HttpResponse<String> overRefundRes = postProvider(base.resolve("/v1/refunds"), overRefundReq);
                assertEquals(409, overRefundRes.statusCode());

                assertTrue(webhookLatch.await(5, TimeUnit.SECONDS));
            }
        } finally {
            webhookReceiver.stop(0);
        }
    }

    private HttpResponse<String> postProvider(URI uri, Object body) throws Exception {
        return http.send(
                HttpRequest.newBuilder(uri)
                        .header(CRED_HEADER, CRED_VALUE)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpServer startWebhookReceiver(List<JsonNode> received, CountDownLatch latch) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> error = new AtomicReference<>();
        server.createContext("/webhook", exchange -> {
            try (exchange) {
                byte[] body = exchange.getRequestBody().readAllBytes();
                String ts = exchange.getRequestHeaders().getFirst("X-Webhook-Timestamp");
                String sig = exchange.getRequestHeaders().getFirst("X-Webhook-Signature");
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                mac.update((ts + ".").getBytes(StandardCharsets.US_ASCII));
                String expected = HexFormat.of().formatHex(mac.doFinal(body));
                if (!expected.equals(sig)) {
                    error.set("Invalid signature");
                    exchange.sendResponseHeaders(401, -1);
                    return;
                }
                received.add(mapper.readTree(body));
                latch.countDown();
                exchange.sendResponseHeaders(204, -1);
            } catch (Exception ex) {
                error.set(ex.getMessage());
                exchange.sendResponseHeaders(500, -1);
            }
        });
        server.start();
        return server;
    }
}
