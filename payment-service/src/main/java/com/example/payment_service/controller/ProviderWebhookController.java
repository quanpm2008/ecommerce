package com.example.payment_service.controller;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.example.payment_service.client.GatewayStatus;
import com.example.payment_service.service.PaymentOutcomeService;
import com.example.payment_service.service.PaymentOutcomeService.OutcomeConflict;
import com.example.payment_service.service.PaymentOutcomeService.ProviderResult;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@RestController
public class ProviderWebhookController {
    private final PaymentOutcomeService outcomes;
    private final ObjectProvider<RefundWebhookHandler> refunds;
    private final Clock clock;
    private final byte[] secret;
    private final JsonMapper json = JsonMapper.builder().build();

    public ProviderWebhookController(PaymentOutcomeService outcomes, ObjectProvider<RefundWebhookHandler> refunds,
            Clock clock, @Value("${payment.provider.webhook-secret:}") String secret) {
        this.outcomes = outcomes;
        this.refunds = refunds;
        this.clock = clock;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/api/v1/payments/webhooks/demo-provider")
    public ResponseEntity<Void> receive(
            @RequestHeader(value = "X-Webhook-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature,
            @RequestBody(required = false) byte[] request) {
        byte[] body = request == null ? new byte[0] : request;
        if (!authentic(timestamp, signature, body)) {
            return ResponseEntity.status(401).build();
        }
        JsonNode event;
        ProviderResult result;
        RefundReferences refundReferences;
        String type;
        try {
            event = json.readTree(body);
            type = requiredText(event, "type", 30);
            if (!"payment.result".equals(type) && !"refund.result".equals(type)) {
                return ResponseEntity.badRequest().build();
            }
            result = parse(event);
            refundReferences = "refund.result".equals(type) ? new RefundReferences(
                    UUID.fromString(requiredText(event, "refundId", 36)),
                    UUID.fromString(requiredText(event, "attemptId", 36)),
                    requiredText(event, "providerRefundId", 255)) : null;
        } catch (RuntimeException malformed) {
            return ResponseEntity.badRequest().build();
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(body);
            if ("payment.result".equals(type)) {
                outcomes.paymentWebhook(result, digest);
            } else {
                var handler = refunds.getIfAvailable();
                if (handler == null) {
                    return ResponseEntity.status(503).build();
                }
                handler.apply(result, refundReferences, digest);
            }
            return ResponseEntity.noContent().build();
        } catch (OutcomeConflict conflict) {
            return ResponseEntity.status(409).build();
        } catch (GeneralSecurityException | RuntimeException unavailable) {
            if (isProviderReferenceConflict(unavailable)) {
                return ResponseEntity.status(409).build();
            }
            return ResponseEntity.status(503).build();
        }
    }

    private static boolean isProviderReferenceConflict(Exception failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException conflict
                    && ("uq_payments_provider_transaction_id".equals(conflict.getConstraintName())
                        || "uq_refund_attempt_provider_ref".equals(conflict.getConstraintName())
                        || "uq_refunds_provider_refund_id".equals(conflict.getConstraintName()))) {
                return true;
            }
        }
        return false;
    }

    private boolean authentic(String timestamp, String signature, byte[] body) {
        if (secret.length == 0 || timestamp == null || signature == null
                || !timestamp.matches("[0-9]{1,19}") || !signature.matches("[0-9a-fA-F]{64}")) {
            return false;
        }
        try {
            Instant signedAt = Instant.ofEpochSecond(Long.parseLong(timestamp));
            if (Duration.between(signedAt, clock.instant()).abs().compareTo(Duration.ofMinutes(5)) > 0) {
                return false;
            }
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            mac.update(timestamp.getBytes(StandardCharsets.US_ASCII));
            mac.update((byte) '.');
            return MessageDigest.isEqual(mac.doFinal(body), HexFormat.of().parseHex(signature));
        } catch (GeneralSecurityException | RuntimeException invalid) {
            return false;
        }
    }

    private static ProviderResult parse(JsonNode event) {
        String eventId = requiredText(event, "eventId", 255);
        Instant.parse(requiredText(event, "occurredAt", 64));
        UUID paymentId = UUID.fromString(requiredText(event, "paymentId", 36));
        String providerRef = requiredText(event, "providerTransactionId", 255);
        JsonNode amount = event.path("amountVnd");
        if (!amount.isIntegralNumber() || !amount.canConvertToLong() || amount.asLong() <= 0) {
            throw new IllegalArgumentException("Invalid amount");
        }
        String currency = requiredText(event, "currency", 3);
        GatewayStatus status = GatewayStatus.valueOf(requiredText(event, "status", 20));
        if (status != GatewayStatus.SUCCESS && status != GatewayStatus.FAILED) {
            throw new IllegalArgumentException("Result must be terminal");
        }
        String error = status == GatewayStatus.FAILED ? requiredText(event, "errorCode", 100) : null;
        Instant completedAt = Instant.parse(requiredText(event, "completedAt", 64));
        return new ProviderResult(eventId, paymentId, providerRef, amount.asLong(), currency, status, completedAt, error);
    }

    private static String requiredText(JsonNode event, String field, int limit) {
        JsonNode value = event.path(field);
        if (!value.isString() || value.asString().isBlank() || value.asString().length() > limit) {
            throw new IllegalArgumentException("Invalid " + field);
        }
        return value.asString();
    }

    public interface RefundWebhookHandler {
        void apply(ProviderResult common, RefundReferences references, byte[] digest);
    }

    public record RefundReferences(UUID refundId, UUID attemptId, String providerRefundId) { }
}
