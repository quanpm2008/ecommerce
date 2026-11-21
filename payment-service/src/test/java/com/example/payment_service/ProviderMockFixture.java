package com.example.payment_service;

import static com.example.payment_service.client.GatewayStatus.FAILED;
import static com.example.payment_service.client.GatewayStatus.PENDING;
import static com.example.payment_service.client.GatewayStatus.SUCCESS;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.example.payment_service.client.GatewayStatus;
import com.example.payment_service.client.ProviderContracts.CreatePaymentRequest;
import com.example.payment_service.client.ProviderContracts.CreatePaymentResponse;
import com.example.payment_service.client.ProviderContracts.CreateRefundRequest;
import com.example.payment_service.client.ProviderContracts.PaymentQueryResponse;
import com.example.payment_service.client.ProviderContracts.RefundResponse;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

final class ProviderMockFixture implements AutoCloseable {
    static final String CREDENTIAL_HEADER = "X-Provider-Test-Key";
    static final String CREDENTIAL = "provider-fixture-local";
    static final String WEBHOOK_SECRET = "provider-fixture-webhook";
    static final String CONTROL_HEADER = "X-Provider-Fixture-Control";
    static final String CONTROL_KEY = "fixture-runner-local";
    private static final JsonMapper JSON = JsonMapper.builder().findAndAddModules().build();

    private final Clock clock;
    private final HttpServer server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final HttpClient webhook = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final Map<UUID, Payment> payments = new HashMap<>();
    private final Map<UUID, Map<UUID, Attempt>> refunds = new HashMap<>();
    private final Map<UUID, byte[]> events = new HashMap<>();
    private final URI origin;
    private final URI instantCallback;
    private Fault paymentFault = Fault.NONE;
    private Fault refundFault = Fault.NONE;
    private boolean closedByRunner;

    ProviderMockFixture(Clock clock) throws IOException {
        this(clock, null);
    }

    ProviderMockFixture(Clock clock, URI instantCallback) throws IOException {
        this.clock = clock;
        if (instantCallback != null) {
            requireLoopback(instantCallback);
        }
        this.instantCallback = instantCallback;
        server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        origin = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        server.setExecutor(executor);
        server.createContext("/", this::handle);
        server.start();
    }

    URI origin() {
        return origin;
    }

    synchronized void nextPaymentFault(Fault fault) {
        paymentFault = fault;
    }

    synchronized void nextRefundFault(Fault fault) {
        refundFault = fault;
    }

    synchronized int paymentCount() {
        return payments.size();
    }

    synchronized int refundSuccessCount(UUID refundId) {
        return (int) refunds.getOrDefault(refundId, Map.of()).values().stream()
                .filter(attempt -> attempt.response().status() == SUCCESS).count();
    }

    synchronized UUID completePayment(UUID paymentId) {
        Payment payment = payments.get(paymentId);
        if (payment == null) {
            throw new Rejected(404);
        }
        expire(payment);
        if (payment.status == PENDING) {
            finish(payment, SUCCESS, null);
        }
        return payment.eventId;
    }

    int sendEvent(UUID eventId, URI callback, Instant signingTime) throws IOException, InterruptedException {
        requireLoopback(callback);
        byte[] body;
        synchronized (this) {
            body = events.get(eventId);
        }
        if (body == null) {
            throw new Rejected(404);
        }
        String timestamp = Long.toString(signingTime.getEpochSecond());
        var request = HttpRequest.newBuilder(callback).timeout(Duration.ofSeconds(4))
                .header("Content-Type", "application/json")
                .header("X-Webhook-Timestamp", timestamp)
                .header("X-Webhook-Signature", signature(timestamp, body))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        return webhook.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    synchronized void waitForRunner(Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!closedByRunner) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                throw new IllegalStateException("Provider fixture shutdown deadline exceeded");
            }
            java.util.concurrent.TimeUnit.NANOSECONDS.timedWait(this, remaining);
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            try {
                String path = exchange.getRequestURI().getPath();
                if (path.startsWith("/__fixture/")) {
                    control(exchange, path);
                    return;
                }
                if (!CREDENTIAL.equals(exchange.getRequestHeaders().getFirst(CREDENTIAL_HEADER))) {
                    throw new Rejected(401);
                }
                String[] parts = path.split("/");
                String method = exchange.getRequestMethod();
                if ("POST".equals(method) && "/v1/payments".equals(path)) {
                    var reply = createPayment(read(exchange, CreatePaymentRequest.class));
                    finishReply(exchange, reply);
                } else if ("GET".equals(method) && parts.length == 4 && "payments".equals(parts[2])
                        && "v1".equals(parts[1])) {
                    write(exchange, 200, queryPayment(UUID.fromString(parts[3])));
                } else if ("POST".equals(method) && "/v1/refunds".equals(path)) {
                    var reply = createRefund(read(exchange, CreateRefundRequest.class));
                    finishReply(exchange, reply);
                } else if ("GET".equals(method) && parts.length == 6 && "v1".equals(parts[1])
                        && "refunds".equals(parts[2]) && "attempts".equals(parts[4])) {
                    write(exchange, 200, queryRefund(UUID.fromString(parts[3]), UUID.fromString(parts[5])));
                } else {
                    throw new Rejected(404);
                }
            } catch (Rejected rejected) {
                exchange.sendResponseHeaders(rejected.status, -1);
            } catch (IllegalArgumentException | JacksonException invalid) {
                exchange.sendResponseHeaders(400, -1);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void control(HttpExchange exchange, String path) throws IOException, InterruptedException {
        if (!CONTROL_KEY.equals(exchange.getRequestHeaders().getFirst(CONTROL_HEADER))) {
            throw new Rejected(401);
        }
        if ("/__fixture/ready".equals(path) && "GET".equals(exchange.getRequestMethod())) {
            write(exchange, 200, Map.of("ready", true));
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            throw new Rejected(404);
        }
        if ("/__fixture/shutdown".equals(path)) {
            exchange.sendResponseHeaders(204, -1);
            synchronized (this) {
                closedByRunner = true;
                notifyAll();
            }
            return;
        }
        var command = read(exchange, Control.class);
        switch (path) {
            case "/__fixture/payment-fault" -> nextPaymentFault(command.fault());
            case "/__fixture/refund-fault" -> nextRefundFault(command.fault());
            case "/__fixture/confirm" -> {
                UUID eventId = completePayment(command.paymentId());
                write(exchange, 200, Map.of("eventId", eventId));
                return;
            }
            case "/__fixture/refund-event" -> {
                UUID eventId = refundEvent(command.refundId(), command.attemptId());
                write(exchange, 200, Map.of("eventId", eventId));
                return;
            }
            case "/__fixture/send-event" -> {
                URI target = command.callback() == null ? instantCallback : command.callback();
                Instant signedAt = command.signedAt() == null ? clock.instant() : command.signedAt();
                int callbackStatus = sendEvent(command.eventId(), target, signedAt);
                write(exchange, 200, Map.of("callbackStatus", callbackStatus));
                return;
            }
            default -> throw new Rejected(404);
        }
        exchange.sendResponseHeaders(204, -1);
    }

    private synchronized Reply createPayment(CreatePaymentRequest request) {
        Payment existing = payments.get(request.paymentId());
        if (existing != null) {
            if (!existing.request.equals(request)) {
                throw new Rejected(409);
            }
            expire(existing);
            return new Reply(existing.linkResponse(), Fault.NONE, null);
        }
        if (!clock.instant().isBefore(request.expiresAt())) {
            throw new Rejected(409);
        }
        Fault fault = paymentFault;
        requireInstantCallback(fault);
        paymentFault = Fault.NONE;
        var payment = new Payment(request, origin.resolve("/pay/" + UUID.randomUUID()).toString());
        payments.put(request.paymentId(), payment);
        if (fault == Fault.CALLBACK_BEFORE_RESPONSE || fault == Fault.REJECT) {
            finish(payment, fault == Fault.REJECT ? FAILED : SUCCESS, fault == Fault.REJECT ? "DECLINED" : null);
        }
        return new Reply(payment.linkResponse(), fault, payment.eventId);
    }

    private synchronized PaymentQueryResponse queryPayment(UUID id) {
        Payment payment = payments.get(id);
        if (payment == null) {
            throw new Rejected(404);
        }
        expire(payment);
        return new PaymentQueryResponse(payment.providerId, payment.request.amountVnd(),
                payment.request.currency(), payment.status, payment.request.expiresAt(),
                payment.status == PENDING ? payment.link : null, payment.completedAt, payment.errorCode);
    }

    private synchronized Reply createRefund(CreateRefundRequest request) {
        var attempts = refunds.computeIfAbsent(request.refundId(), ignored -> new LinkedHashMap<>());
        Attempt existing = attempts.get(request.attemptId());
        if (existing != null) {
            if (!existing.request().equals(request)) {
                throw new Rejected(409);
            }
            return new Reply(existing.response(), Fault.NONE, null);
        }
        Payment payment = payments.get(request.paymentId());
        if (payment == null || payment.status != SUCCESS || !payment.providerId.equals(request.providerTransactionId())) {
            throw new Rejected(409);
        }
        if (attempts.values().stream().anyMatch(attempt -> !sameObligation(attempt.request(), request)
                || attempt.response().status() != FAILED)) {
            throw new Rejected(409);
        }
        long remaining = payment.request.amountVnd();
        for (var refund : refunds.values()) {
            for (var attempt : refund.values()) {
                if (attempt.request().paymentId().equals(request.paymentId()) && attempt.response().status() == SUCCESS) {
                    remaining -= attempt.request().amountVnd();
                }
            }
        }
        if (request.amountVnd() > remaining) {
            throw new Rejected(409);
        }
        Fault fault = refundFault;
        refundFault = Fault.NONE;
        requireInstantCallback(fault);
        GatewayStatus status = fault == Fault.REJECT ? FAILED : SUCCESS;
        Instant now = clock.instant();
        var response = new RefundResponse("refund-" + request.attemptId(), status, request.amountVnd(),
                request.currency(), now, status == FAILED ? "REFUND_DECLINED" : null);
        var data = paymentEvent(payment, "refund.result", status, now, request.amountVnd(), response.errorCode());
        data.put("refundId", request.refundId());
        data.put("attemptId", request.attemptId());
        data.put("providerRefundId", response.providerRefundId());
        UUID eventId = storeEvent(data);
        attempts.put(request.attemptId(), new Attempt(request, response, eventId));
        return new Reply(response, fault, eventId);
    }

    private synchronized RefundResponse queryRefund(UUID refundId, UUID attemptId) {
        Attempt result = refunds.getOrDefault(refundId, Map.of()).get(attemptId);
        if (result == null) {
            throw new Rejected(404);
        }
        return result.response();
    }

    synchronized UUID refundEvent(UUID refundId, UUID attemptId) {
        Attempt result = refunds.getOrDefault(refundId, Map.of()).get(attemptId);
        if (result == null) {
            throw new Rejected(404);
        }
        return result.eventId();
    }

    private void requireInstantCallback(Fault fault) {
        if (fault == Fault.CALLBACK_BEFORE_RESPONSE && instantCallback == null) {
            throw new IllegalArgumentException("Callback target was not set for provider fixture");
        }
    }

    private void finishReply(HttpExchange exchange, Reply reply) throws IOException, InterruptedException {
        switch (reply.fault()) {
            case DROP_AFTER_COMMIT -> { return; }
            case DELAY_AFTER_COMMIT -> Thread.sleep(Duration.ofSeconds(4));
            case CALLBACK_BEFORE_RESPONSE -> sendEvent(reply.eventId(), instantCallback, clock.instant());
            case NONE, REJECT -> { }
        }
        write(exchange, 200, reply.body());
    }

    private void expire(Payment payment) {
        if (payment.status == PENDING && !clock.instant().isBefore(payment.request.expiresAt())) {
            finish(payment, FAILED, "LINK_EXPIRED");
        }
    }

    private void finish(Payment payment, GatewayStatus status, String errorCode) {
        payment.status = status;
        payment.completedAt = clock.instant();
        payment.errorCode = errorCode;
        payment.eventId = storeEvent(paymentEvent(payment, "payment.result", status, payment.completedAt,
                payment.request.amountVnd(), errorCode));
    }

    private Map<String, Object> paymentEvent(Payment payment, String type, GatewayStatus status,
            Instant completedAt, long amount, String errorCode) {
        var body = new LinkedHashMap<String, Object>();
        body.put("type", type);
        body.put("occurredAt", completedAt);
        body.put("paymentId", payment.request.paymentId());
        body.put("providerTransactionId", payment.providerId);
        body.put("amountVnd", amount);
        body.put("currency", payment.request.currency());
        body.put("status", status);
        body.put("completedAt", completedAt);
        if (errorCode != null) {
            body.put("errorCode", errorCode);
        }
        return body;
    }

    private UUID storeEvent(Map<String, Object> body) {
        UUID eventId = UUID.randomUUID();
        body.put("eventId", eventId);
        events.put(eventId, JSON.writeValueAsBytes(body));
        return eventId;
    }

    private static boolean sameObligation(CreateRefundRequest a, CreateRefundRequest b) {
        return a.paymentId().equals(b.paymentId()) && a.providerTransactionId().equals(b.providerTransactionId())
                && a.amountVnd() == b.amountVnd() && a.currency().equals(b.currency());
    }

    private static String signature(String timestamp, byte[] body) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (java.security.GeneralSecurityException impossible) {
            throw new IllegalStateException("Provider fixture signature unavailable", impossible);
        }
    }

    private static <T> T read(HttpExchange exchange, Class<T> type) throws IOException {
        return JSON.readValue(exchange.getRequestBody(), type);
    }

    private static void write(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = JSON.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static void requireLoopback(URI target) {
        if (target == null || !"http".equals(target.getScheme()) || !"127.0.0.1".equals(target.getHost())
                || target.getUserInfo() != null) {
            throw new IllegalArgumentException("Fixture callback must be on HTTP loopback");
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
        webhook.close();
    }

    enum Fault {
        NONE, REJECT, DROP_AFTER_COMMIT, DELAY_AFTER_COMMIT, CALLBACK_BEFORE_RESPONSE
    }

    record Control(Fault fault, UUID paymentId, UUID refundId, UUID attemptId, UUID eventId,
            URI callback, Instant signedAt) { }
    private record Reply(Object body, Fault fault, UUID eventId) { }
    private record Attempt(CreateRefundRequest request, RefundResponse response, UUID eventId) { }

    private static final class Payment {
        private final CreatePaymentRequest request;
        private final String providerId;
        private final String link;
        private GatewayStatus status = PENDING;
        private Instant completedAt;
        private String errorCode;
        private UUID eventId;

        private Payment(CreatePaymentRequest request, String link) {
            this.request = request;
            this.providerId = "payment-" + request.paymentId();
            this.link = link;
        }

        private CreatePaymentResponse linkResponse() {
            return new CreatePaymentResponse(providerId, status, request.expiresAt(), status == PENDING ? link : null);
        }
    }

    private static final class Rejected extends RuntimeException {
        private final int status;

        private Rejected(int status) {
            this.status = status;
        }
    }
}
