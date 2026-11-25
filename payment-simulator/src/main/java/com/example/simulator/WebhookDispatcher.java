package com.example.simulator;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.example.simulator.SimulatorModels.GatewayStatus;
import com.example.simulator.SimulatorModels.PaymentRecord;
import com.example.simulator.SimulatorModels.RefundAttemptRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public final class WebhookDispatcher implements AutoCloseable {
    private final URI webhookUrl;
    private final byte[] secret;
    private final Clock clock;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public WebhookDispatcher(URI webhookUrl, String webhookSecret, Clock clock) {
        this.webhookUrl = webhookUrl;
        this.secret = webhookSecret.getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public void dispatchPaymentResultAsync(PaymentRecord payment) {
        UUID eventId = payment.lastEventId != null ? payment.lastEventId : UUID.randomUUID();
        payment.lastEventId = eventId;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "payment.result");
        body.put("eventId", eventId);
        body.put("occurredAt", payment.completedAt);
        body.put("paymentId", payment.paymentId);
        body.put("providerTransactionId", payment.providerTransactionId);
        body.put("amountVnd", payment.amountVnd);
        body.put("currency", payment.currency);
        body.put("status", payment.status);
        body.put("completedAt", payment.completedAt);
        if (payment.status == GatewayStatus.FAILED && payment.errorCode != null) {
            body.put("errorCode", payment.errorCode);
        }
        submitWithRetry(body);
    }

    public void dispatchRefundResultAsync(RefundAttemptRecord attempt) {
        UUID eventId = attempt.lastEventId != null ? attempt.lastEventId : UUID.randomUUID();
        attempt.lastEventId = eventId;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "refund.result");
        body.put("eventId", eventId);
        body.put("occurredAt", attempt.completedAt);
        body.put("paymentId", attempt.paymentId);
        body.put("providerTransactionId", attempt.providerTransactionId);
        body.put("refundId", attempt.refundId);
        body.put("attemptId", attempt.attemptId);
        body.put("providerRefundId", attempt.providerRefundId);
        body.put("amountVnd", attempt.amountVnd);
        body.put("currency", attempt.currency);
        body.put("status", attempt.status);
        body.put("completedAt", attempt.completedAt);
        if (attempt.status == GatewayStatus.FAILED && attempt.errorCode != null) {
            body.put("errorCode", attempt.errorCode);
        }
        submitWithRetry(body);
    }

    public int sendSync(Map<String, Object> payload) {
        try {
            byte[] bytes = mapper.writeValueAsBytes(payload);
            String timestamp = Long.toString(Instant.now(clock).getEpochSecond());
            String signature = sign(timestamp, bytes);
            HttpRequest request = HttpRequest.newBuilder(webhookUrl)
                    .timeout(Duration.ofSeconds(4))
                    .header("Content-Type", "application/json")
                    .header("X-Webhook-Timestamp", timestamp)
                    .header("X-Webhook-Signature", signature)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
                    .build();
            return httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (Exception ex) {
            return 0;
        }
    }

    private void submitWithRetry(Map<String, Object> payload) {
        executor.submit(() -> {
            long[] delaysMs = {0L, 500L, 1500L, 3000L, 5000L};
            for (long delay : delaysMs) {
                if (delay > 0) {
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                int code = sendSync(payload);
                if (code == 204 || code == 200 || code == 409) {
                    return;
                }
            }
        });
    }

    private String sign(String timestamp, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            mac.update(timestamp.getBytes(StandardCharsets.US_ASCII));
            mac.update((byte) '.');
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", impossible);
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
        httpClient.close();
    }
}
