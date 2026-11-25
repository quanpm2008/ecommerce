package com.example.simulator;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.example.simulator.SimulatorModels.CompleteRequest;
import com.example.simulator.SimulatorModels.CreatePaymentRequest;
import com.example.simulator.SimulatorModels.CreatePaymentResponse;
import com.example.simulator.SimulatorModels.CreateRefundRequest;
import com.example.simulator.SimulatorModels.FaultMode;
import com.example.simulator.SimulatorModels.GatewayStatus;
import com.example.simulator.SimulatorModels.PaymentQueryResponse;
import com.example.simulator.SimulatorModels.PaymentRecord;
import com.example.simulator.SimulatorModels.RefundAttemptAbsence;
import com.example.simulator.SimulatorModels.RefundAttemptRecord;
import com.example.simulator.SimulatorModels.RefundResponse;
import com.example.simulator.SimulatorModels.ScenarioControlRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public final class SimulatorServer implements AutoCloseable {
    private static final String REFUND_ATTEMPT_NOT_FOUND = "REFUND_ATTEMPT_NOT_FOUND";

    private final SimulatorConfig config;
    private final JsonStateStore store;
    private final WebhookDispatcher webhooks;
    private final Clock clock;
    private final HttpServer server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final ObjectMapper mapper;

    public SimulatorServer(SimulatorConfig config, Clock clock) throws IOException {
        this.config = config;
        this.clock = clock;
        this.store = new JsonStateStore(config.stateFile());
        this.webhooks = new WebhookDispatcher(config.webhookUrl(), config.webhookSecret(), clock);
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.server = HttpServer.create(new InetSocketAddress("0.0.0.0", config.port()), 0);
        this.server.setExecutor(executor);
        this.server.createContext("/", this::handle);
    }

    public void start() {
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            try {
                if (path.startsWith("/pay/")) {
                    handleCustomerPay(exchange, method, path);
                    return;
                }
                if (path.startsWith("/__sim/")) {
                    handleControl(exchange, method, path);
                    return;
                }
                if (!isAuthorizedProviderRequest(exchange)) {
                    exchange.sendResponseHeaders(401, -1);
                    return;
                }
                handleProviderApi(exchange, method, path);
            } catch (HttpError error) {
                exchange.sendResponseHeaders(error.status, -1);
            } catch (IllegalArgumentException invalid) {
                exchange.sendResponseHeaders(400, -1);
            }
        }
    }

    private boolean isAuthorizedProviderRequest(HttpExchange exchange) {
        String presented = exchange.getRequestHeaders().getFirst(config.credentialHeader());
        return config.credential().equals(presented);
    }

    private void handleProviderApi(HttpExchange exchange, String method, String path) throws IOException {
        String[] parts = path.split("/");
        if ("POST".equals(method) && "/v1/payments".equals(path)) {
            CreatePaymentRequest request = readJson(exchange, CreatePaymentRequest.class);
            PaymentOutcome outcome = createPayment(request);
            applyFaultAndReply(exchange, outcome.fault(), 200, outcome.body());
            return;
        }
        if ("GET".equals(method) && parts.length == 4 && "v1".equals(parts[1]) && "payments".equals(parts[2])) {
            UUID paymentId = UUID.fromString(parts[3]);
            PaymentQueryResponse response = queryPayment(paymentId);
            writeJson(exchange, 200, response);
            return;
        }
        if ("POST".equals(method) && "/v1/refunds".equals(path)) {
            CreateRefundRequest request = readJson(exchange, CreateRefundRequest.class);
            RefundOutcome outcome = createRefund(request);
            applyFaultAndReply(exchange, outcome.fault(), 200, outcome.body());
            return;
        }
        if ("GET".equals(method) && parts.length == 6 && "v1".equals(parts[1])
                && "refunds".equals(parts[2]) && "attempts".equals(parts[4])) {
            UUID refundId = UUID.fromString(parts[3]);
            UUID attemptId = UUID.fromString(parts[5]);
            Object response = queryRefund(refundId, attemptId);
            int status = response instanceof RefundAttemptAbsence ? 404 : 200;
            writeJson(exchange, status, response);
            return;
        }
        throw new HttpError(404);
    }

    private void handleCustomerPay(HttpExchange exchange, String method, String path) throws IOException {
        String[] parts = path.split("/");
        if ("GET".equals(method) && parts.length == 3 && "pay".equals(parts[1])) {
            PaymentRecord payment = refreshExpirationByToken(parts[2]);
            if (payment == null) {
                writeHtml(exchange, 404, PaymentPageRenderer.renderNotFound());
                return;
            }
            writeHtml(exchange, 200, PaymentPageRenderer.renderPaymentPage(payment, null));
            return;
        }
        if ("POST".equals(method) && parts.length == 4 && "pay".equals(parts[1]) && "confirm".equals(parts[3])) {
            String token = parts[2];
            ConfirmInput input = readConfirmInput(exchange);
            ConfirmOutcome outcome = confirmPayment(token, input.status(), input.errorCode());
            if (outcome.payment() == null) {
                writeHtml(exchange, 404, PaymentPageRenderer.renderNotFound());
                return;
            }
            String accept = exchange.getRequestHeaders().getFirst("Accept");
            if (accept != null && accept.contains("application/json")) {
                writeJson(exchange, 200, Map.of(
                        "paymentId", outcome.payment().paymentId,
                        "status", outcome.payment().status,
                        "completedAt", String.valueOf(outcome.payment().completedAt),
                        "alreadyFinalized", outcome.alreadyFinalized()
                ));
            } else {
                String notice = outcome.alreadyFinalized()
                        ? "Giao dịch đã được xử lý trước đó, không thu tiền lần hai."
                        : "Đã ghi nhận kết quả thanh toán và gửi webhook về hệ thống.";
                writeHtml(exchange, 200, PaymentPageRenderer.renderPaymentPage(outcome.payment(), notice));
            }
            return;
        }
        throw new HttpError(404);
    }

    private void handleControl(HttpExchange exchange, String method, String path) throws IOException {
        if ("GET".equals(method) && "/__sim/health".equals(path)) {
            writeJson(exchange, 200, Map.of("status", "UP"));
            return;
        }
        if ("GET".equals(method) && "/__sim/state".equals(path)) {
            writeJson(exchange, 200, store.snapshot());
            return;
        }
        if ("POST".equals(method) && "/__sim/scenario".equals(path)) {
            ScenarioControlRequest req = readJson(exchange, ScenarioControlRequest.class);
            store.configureScenario(req.nextPaymentFault(), req.nextRefundFault(), req.lateSuccessDelayMs());
            writeJson(exchange, 200, store.snapshot());
            return;
        }
        String[] parts = path.split("/");
        if ("POST".equals(method) && parts.length == 5 && "__sim".equals(parts[1])
                && "payments".equals(parts[2]) && "complete".equals(parts[4])) {
            UUID paymentId = UUID.fromString(parts[3]);
            CompleteRequest req = readOptionalJson(exchange, CompleteRequest.class);
            GatewayStatus status = req != null && req.status() != null ? req.status() : GatewayStatus.SUCCESS;
            String errorCode = status == GatewayStatus.FAILED
                    ? (req != null && req.errorCode() != null ? req.errorCode() : "DECLINED")
                    : null;
            PaymentRecord completed = completePaymentById(paymentId, status, errorCode);
            if (completed == null) {
                throw new HttpError(404);
            }
            writeJson(exchange, 200, completed);
            return;
        }
        if ("POST".equals(method) && parts.length == 7 && "__sim".equals(parts[1])
                && "refunds".equals(parts[2]) && "attempts".equals(parts[4]) && "complete".equals(parts[6])) {
            UUID refundId = UUID.fromString(parts[3]);
            UUID attemptId = UUID.fromString(parts[5]);
            CompleteRequest req = readOptionalJson(exchange, CompleteRequest.class);
            GatewayStatus status = req != null && req.status() != null ? req.status() : GatewayStatus.SUCCESS;
            String errorCode = status == GatewayStatus.FAILED
                    ? (req != null && req.errorCode() != null ? req.errorCode() : "REFUND_DECLINED")
                    : null;
            RefundAttemptRecord completed = completeRefundAttempt(refundId, attemptId, status, errorCode);
            if (completed == null) {
                throw new HttpError(404);
            }
            writeJson(exchange, 200, completed.toResponse());
            return;
        }
        throw new HttpError(404);
    }

    private PaymentOutcome createPayment(CreatePaymentRequest request) {
        if (request.paymentId() == null || request.checkoutSessionId() == null || request.expiresAt() == null
                || request.amountVnd() <= 0 || !"VND".equals(request.currency())) {
            throw new HttpError(400);
        }
        synchronized (store) {
            PaymentRecord existing = store.findPayment(request.paymentId());
            if (existing != null) {
                if (!samePaymentRequest(existing, request)) {
                    throw new HttpError(409);
                }
                expireIfDue(existing);
                return new PaymentOutcome(toCreateResponse(existing), FaultMode.NONE);
            }
            if (!clock.instant().isBefore(request.expiresAt())) {
                throw new HttpError(409);
            }
            FaultMode fault = store.consumeNextPaymentFault();
            String token = UUID.randomUUID().toString();
            PaymentRecord created = new PaymentRecord(request, token);
            if (fault == FaultMode.REJECT) {
                created.status = GatewayStatus.FAILED;
                created.completedAt = clock.instant();
                created.errorCode = "DECLINED";
                store.savePayment(created);
                webhooks.dispatchPaymentResultAsync(created);
                store.savePayment(created);
            } else {
                store.savePayment(created);
                if (fault == FaultMode.LATE_SUCCESS) {
                    long delayMs = store.lateSuccessDelayMs();
                    scheduler.schedule(
                            () -> completePaymentById(created.paymentId, GatewayStatus.SUCCESS, null),
                            delayMs,
                            TimeUnit.MILLISECONDS
                    );
                }
            }
            return new PaymentOutcome(toCreateResponse(created), fault);
        }
    }

    private PaymentQueryResponse queryPayment(UUID paymentId) {
        synchronized (store) {
            PaymentRecord payment = store.findPayment(paymentId);
            if (payment == null) {
                throw new HttpError(404);
            }
            expireIfDue(payment);
            String paymentUrl = payment.status == GatewayStatus.PENDING ? paymentUrlFor(payment.token) : null;
            return new PaymentQueryResponse(
                    payment.providerTransactionId,
                    payment.amountVnd,
                    payment.currency,
                    payment.status,
                    payment.expiresAt,
                    paymentUrl,
                    payment.completedAt,
                    payment.errorCode
            );
        }
    }

    private RefundOutcome createRefund(CreateRefundRequest request) {
        if (request.refundId() == null || request.attemptId() == null || request.paymentId() == null
                || request.providerTransactionId() == null || request.providerTransactionId().isBlank()
                || request.amountVnd() <= 0 || !"VND".equals(request.currency())) {
            throw new HttpError(400);
        }
        synchronized (store) {
            RefundAttemptRecord existing = store.findRefundAttempt(request.refundId(), request.attemptId());
            if (existing != null) {
                if (!sameRefundRequest(existing, request)) {
                    throw new HttpError(409);
                }
                return new RefundOutcome(existing.toResponse(), FaultMode.NONE);
            }
            PaymentRecord payment = store.findPayment(request.paymentId());
            if (payment == null || payment.status != GatewayStatus.SUCCESS
                    || !payment.providerTransactionId.equals(request.providerTransactionId())) {
                throw new HttpError(409);
            }
            List<RefundAttemptRecord> priorForRefund = store.findRefundsByRefundId(request.refundId());
            for (RefundAttemptRecord prior : priorForRefund) {
                if (!sameRefundObligation(prior, request) || prior.status != GatewayStatus.FAILED) {
                    throw new HttpError(409);
                }
            }
            long remaining = payment.amountVnd;
            for (RefundAttemptRecord attempt : store.findRefundsByPaymentId(request.paymentId())) {
                if (attempt.status == GatewayStatus.SUCCESS) {
                    remaining -= attempt.amountVnd;
                }
            }
            if (request.amountVnd() > remaining) {
                throw new HttpError(409);
            }

            FaultMode fault = store.consumeNextRefundFault();
            Instant now = clock.instant();
            GatewayStatus status;
            Instant completedAt;
            String errorCode;
            if (fault == FaultMode.REJECT) {
                status = GatewayStatus.FAILED;
                completedAt = now;
                errorCode = "REFUND_DECLINED";
            } else if (fault == FaultMode.PENDING || fault == FaultMode.LATE_SUCCESS) {
                status = GatewayStatus.PENDING;
                completedAt = null;
                errorCode = null;
            } else {
                status = GatewayStatus.SUCCESS;
                completedAt = now;
                errorCode = null;
            }
            RefundAttemptRecord created = new RefundAttemptRecord(request, status, completedAt, errorCode);
            store.saveRefundAttempt(created);
            if (status == GatewayStatus.SUCCESS || status == GatewayStatus.FAILED) {
                webhooks.dispatchRefundResultAsync(created);
                store.saveRefundAttempt(created);
            } else if (fault == FaultMode.LATE_SUCCESS) {
                long delayMs = store.lateSuccessDelayMs();
                scheduler.schedule(
                        () -> completeRefundAttempt(created.refundId, created.attemptId, GatewayStatus.SUCCESS, null),
                        delayMs,
                        TimeUnit.MILLISECONDS
                );
            }
            return new RefundOutcome(created.toResponse(), fault);
        }
    }

    private Object queryRefund(UUID refundId, UUID attemptId) {
        synchronized (store) {
            RefundAttemptRecord existing = store.findRefundAttempt(refundId, attemptId);
            if (existing != null) {
                return existing.toResponse();
            }
            return new RefundAttemptAbsence(
                    refundId,
                    attemptId,
                    "refund-" + attemptId,
                    clock.instant(),
                    REFUND_ATTEMPT_NOT_FOUND
            );
        }
    }

    private PaymentRecord refreshExpirationByToken(String token) {
        synchronized (store) {
            PaymentRecord payment = store.findPaymentByToken(token);
            if (payment == null) {
                return null;
            }
            expireIfDue(payment);
            return payment;
        }
    }

    private ConfirmOutcome confirmPayment(String token, GatewayStatus requestedStatus, String requestedError) {
        synchronized (store) {
            PaymentRecord payment = store.findPaymentByToken(token);
            if (payment == null) {
                return new ConfirmOutcome(null, false);
            }
            expireIfDue(payment);
            if (payment.status != GatewayStatus.PENDING) {
                return new ConfirmOutcome(payment, true);
            }
            payment.status = requestedStatus == GatewayStatus.FAILED ? GatewayStatus.FAILED : GatewayStatus.SUCCESS;
            payment.completedAt = clock.instant();
            payment.errorCode = payment.status == GatewayStatus.FAILED
                    ? (requestedError != null && !requestedError.isBlank() ? requestedError : "CUSTOMER_DECLINED")
                    : null;
            webhooks.dispatchPaymentResultAsync(payment);
            store.savePayment(payment);
            return new ConfirmOutcome(payment, false);
        }
    }

    private PaymentRecord completePaymentById(UUID paymentId, GatewayStatus status, String errorCode) {
        synchronized (store) {
            PaymentRecord payment = store.findPayment(paymentId);
            if (payment == null) {
                return null;
            }
            expireIfDue(payment);
            if (payment.status == GatewayStatus.PENDING) {
                payment.status = status == GatewayStatus.FAILED ? GatewayStatus.FAILED : GatewayStatus.SUCCESS;
                payment.completedAt = clock.instant();
                payment.errorCode = payment.status == GatewayStatus.FAILED ? errorCode : null;
                webhooks.dispatchPaymentResultAsync(payment);
                store.savePayment(payment);
            }
            return payment;
        }
    }

    private RefundAttemptRecord completeRefundAttempt(UUID refundId, UUID attemptId, GatewayStatus status, String errorCode) {
        synchronized (store) {
            RefundAttemptRecord attempt = store.findRefundAttempt(refundId, attemptId);
            if (attempt == null) {
                return null;
            }
            if (attempt.status == GatewayStatus.PENDING) {
                attempt.status = status == GatewayStatus.FAILED ? GatewayStatus.FAILED : GatewayStatus.SUCCESS;
                attempt.completedAt = clock.instant();
                attempt.errorCode = attempt.status == GatewayStatus.FAILED ? errorCode : null;
                webhooks.dispatchRefundResultAsync(attempt);
                store.saveRefundAttempt(attempt);
            }
            return attempt;
        }
    }

    private void expireIfDue(PaymentRecord payment) {
        if (payment.status == GatewayStatus.PENDING && !clock.instant().isBefore(payment.expiresAt)) {
            payment.status = GatewayStatus.FAILED;
            payment.completedAt = clock.instant();
            payment.errorCode = "LINK_EXPIRED";
            webhooks.dispatchPaymentResultAsync(payment);
            store.savePayment(payment);
        }
    }

    private CreatePaymentResponse toCreateResponse(PaymentRecord payment) {
        String url = payment.status == GatewayStatus.PENDING ? paymentUrlFor(payment.token) : null;
        return new CreatePaymentResponse(payment.providerTransactionId, payment.status, payment.expiresAt, url);
    }

    private String paymentUrlFor(String token) {
        return config.publicOrigin().resolve("/pay/" + token).toString();
    }

    private void applyFaultAndReply(HttpExchange exchange, FaultMode fault, int status, Object body) throws IOException {
        if (fault == FaultMode.DROP_AFTER_COMMIT) {
            return;
        }
        if (fault == FaultMode.DELAY_AFTER_COMMIT) {
            try {
                Thread.sleep(3500L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        writeJson(exchange, status, body);
    }

    private ConfirmInput readConfirmInput(HttpExchange exchange) throws IOException {
        byte[] raw = exchange.getRequestBody().readAllBytes();
        if (raw.length == 0) {
            return new ConfirmInput(GatewayStatus.SUCCESS, null);
        }
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType != null && contentType.contains("application/json")) {
            CompleteRequest req = mapper.readValue(raw, CompleteRequest.class);
            GatewayStatus status = req.status() != null ? req.status() : GatewayStatus.SUCCESS;
            return new ConfirmInput(status, req.errorCode());
        }
        Map<String, String> form = parseForm(new String(raw, StandardCharsets.UTF_8));
        String action = form.getOrDefault("action", "SUCCESS").trim().toUpperCase();
        GatewayStatus status = "FAILED".equals(action) ? GatewayStatus.FAILED : GatewayStatus.SUCCESS;
        return new ConfirmInput(status, form.get("errorCode"));
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String pair : body.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String val = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                result.put(key, val);
            }
        }
        return result;
    }

    private <T> T readJson(HttpExchange exchange, Class<T> type) throws IOException {
        try {
            return mapper.readValue(exchange.getRequestBody(), type);
        } catch (IOException ex) {
            throw new HttpError(400);
        }
    }

    private <T> T readOptionalJson(HttpExchange exchange, Class<T> type) {
        try {
            byte[] raw = exchange.getRequestBody().readAllBytes();
            if (raw.length == 0) {
                return null;
            }
            return mapper.readValue(raw, type);
        } catch (IOException ex) {
            throw new HttpError(400);
        }
    }

    private void writeJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = mapper.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private void writeHtml(HttpExchange exchange, int status, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static boolean samePaymentRequest(PaymentRecord existing, CreatePaymentRequest req) {
        return Objects.equals(existing.paymentId, req.paymentId())
                && Objects.equals(existing.checkoutSessionId, req.checkoutSessionId())
                && existing.amountVnd == req.amountVnd()
                && Objects.equals(existing.currency, req.currency())
                && Objects.equals(existing.expiresAt, req.expiresAt());
    }

    private static boolean sameRefundRequest(RefundAttemptRecord existing, CreateRefundRequest req) {
        return Objects.equals(existing.refundId, req.refundId())
                && Objects.equals(existing.attemptId, req.attemptId())
                && sameRefundObligation(existing, req);
    }

    private static boolean sameRefundObligation(RefundAttemptRecord existing, CreateRefundRequest req) {
        return Objects.equals(existing.paymentId, req.paymentId())
                && Objects.equals(existing.providerTransactionId, req.providerTransactionId())
                && existing.amountVnd == req.amountVnd()
                && Objects.equals(existing.currency, req.currency());
    }

    @Override
    public void close() {
        server.stop(0);
        scheduler.shutdownNow();
        executor.shutdownNow();
        webhooks.close();
    }

    private record PaymentOutcome(CreatePaymentResponse body, FaultMode fault) {
    }

    private record RefundOutcome(RefundResponse body, FaultMode fault) {
    }

    private record ConfirmInput(GatewayStatus status, String errorCode) {
    }

    private record ConfirmOutcome(PaymentRecord payment, boolean alreadyFinalized) {
    }

    private static final class HttpError extends RuntimeException {
        private final int status;

        private HttpError(int status) {
            this.status = status;
        }
    }
}
