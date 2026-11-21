package com.example.payment_service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.example.payment_service.client.PaymentGateway;
import com.example.payment_service.client.ProviderHttpGateway;
import com.example.payment_service.client.ProviderContracts.CreatePaymentRequest;
import com.example.payment_service.entity.Payment;
import com.example.payment_service.repository.PaymentCollectionWorkRepository;
import com.example.payment_service.repository.PaymentRepository;
import com.example.payment_service.service.PaymentCollectionJob;
import com.example.payment_service.service.PaymentCollectionService;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class PaymentWebhookPostgresTests extends PaymentOutcomePostgresFixture {
    @Test
    void signedExactBytesBindMissingRefAndRejectReplayConflicts() throws Exception {
        var payment = preparePayment().payment();
        String ref = "early-" + payment.getPaymentId();
        UUID event = UUID.randomUUID();
        byte[] original = result(payment, event, ref, 55000L);
        byte[] changed = result(payment, event, ref, 55001L);
        String stamp = Long.toString(clock.instant().getEpochSecond());
        assertThat(send(changed, stamp, signature(stamp, original))).isEqualTo(401);
        assertThat(sendSigned(original, clock.instant().minusSeconds(301))).isEqualTo(401);
        assertThat(sendSigned("bad json".getBytes(StandardCharsets.UTF_8), clock.instant())).isEqualTo(400);
        assertThat(sendSigned(changed, clock.instant())).isEqualTo(409);
        assertThat(inboxCount(event)).isZero();
        assertThat(payments.findById(payment.getPaymentId()).orElseThrow().isPending()).isTrue();

        try (var reader = resultConsumer()) {
            var one = HTTP.sendAsync(request(original, stamp, signature(stamp, original)), HttpResponse.BodyHandlers.discarding());
            var two = HTTP.sendAsync(request(original, stamp, signature(stamp, original)), HttpResponse.BodyHandlers.discarding());
            assertThat(one.join().statusCode()).isEqualTo(204);
            assertThat(two.join().statusCode()).isEqualTo(204);
            assertThat(sendSigned(changed, clock.instant())).isEqualTo(409);
            assertThat(inboxCount(event)).isEqualTo(1L);
            assertThat(payments.findById(payment.getPaymentId()).orElseThrow().getProviderTransactionId()).isEqualTo(ref);
            var published = awaitEvent(reader, payment, "payment.succeeded");
            assertThat(published.path("payload").path("checkoutSessionId").asString()).isEqualTo(payment.getCheckoutSessionId().toString());
            assertThat(published.path("producer").asString()).isEqualTo("payment-service");
            assertThat(published.path("version").asLong()).isEqualTo(1);
            assertThat(outboxCount(payment)).isEqualTo(1L);
        }
    }

    @Test
    void rollbackLeavesPaymentAndInboxUnchangedAndSameDeliveryCanRetry() throws Exception {
        var payment = preparePayment().payment();
        UUID event = UUID.randomUUID();
        byte[] original = result(payment, event, "rollback-" + event, 55000);
        String constraint = "ck_test_outbox_" + event.toString().replace("-", "");
        jdbc.sql("ALTER TABLE outbox_events ADD CONSTRAINT " + constraint
                + " CHECK (aggregate_id <> '" + payment.getPaymentId() + "'::uuid)").update();
        try {
            assertThat(sendSigned(original, clock.instant())).isEqualTo(503);
            assertThat(inboxCount(event)).isZero();
            assertThat(outboxCount(payment)).isZero();
            assertThat(payments.findById(payment.getPaymentId()).orElseThrow().isPending()).isTrue();
        } finally {
            jdbc.sql("ALTER TABLE outbox_events DROP CONSTRAINT " + constraint).update();
        }
        assertThat(sendSigned(original, clock.instant())).isEqualTo(204);
        assertThat(inboxCount(event)).isEqualTo(1L);
    }

    @Test
    void realProviderCallbackCanApplyBeforeItsCreateResponseArrives() throws Exception {
        var payment = preparePayment().payment();
        assertThat(payment.getProviderTransactionId()).isNull();
        try (var earlyProvider = new ProviderMockFixture(clock, callback())) {
            earlyProvider.nextPaymentFault(ProviderMockFixture.Fault.CALLBACK_BEFORE_RESPONSE);
            var httpGateway = new ProviderHttpGateway(earlyProvider.origin(), ProviderMockFixture.CREDENTIAL_HEADER,
                    ProviderMockFixture.CREDENTIAL, Duration.ofSeconds(6));
            var response = httpGateway.createPayment(new CreatePaymentRequest(payment.getPaymentId(),
                    payment.getCheckoutSessionId(), payment.getAmountVnd(), "VND", payment.getExpiresAt()));
            var confirmed = payments.findById(payment.getPaymentId()).orElseThrow();
            assertThat(confirmed.isSuccess()).isTrue();
            assertThat(confirmed.getProviderTransactionId()).isEqualTo(response.providerTransactionId());
            assertThat(confirmed.getPaymentUrl()).isNull();
            assertThat(outboxCount(payment)).isEqualTo(1L);
        }
    }

    @Test
    void providerHttpJ2PublishesBothTerminalOutcomesAndWebhookReplayIsHarmless() throws Exception {
        var success = preparePayment().payment();
        var failed = preparePayment().payment();
        openAtProvider(success);
        openAtProvider(failed);
        UUID event = provider.completePayment(success.getPaymentId());
        clock.set(failed.getExpiresAt().plusSeconds(1));

        try (var reader = resultConsumer()) {
            job.runOnce();
            var replay = CompletableFuture.supplyAsync(() -> sendFixture(event));
            job.runOnce();
            assertThat(replay.join()).isEqualTo(204);
            assertThat(payments.findById(success.getPaymentId()).orElseThrow().isSuccess()).isTrue();
            assertThat(payments.findById(failed.getPaymentId()).orElseThrow().isFailed()).isTrue();
            awaitEvent(reader, success, "payment.succeeded");
            var rejection = awaitEvent(reader, failed, "payment.failed");
            assertThat(rejection.path("payload").path("errorCode").asString()).isEqualTo("LINK_EXPIRED");
            assertThat(jdbc.sql("SELECT applied_at IS NOT NULL FROM payment_collection_observation WHERE payment_id = ?")
                    .param(failed.getPaymentId()).query(Boolean.class).single()).isTrue();
            assertThat(jdbc.sql("SELECT applied_at IS NOT NULL FROM payment_collection_observation WHERE payment_id = ?")
                    .param(success.getPaymentId()).query(Boolean.class).single()).isTrue();
            job.runOnce();
            assertThat(outboxCount(success)).isEqualTo(1L);
            assertThat(outboxCount(failed)).isEqualTo(1L);
        }
    }
}

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false", "payment.collection.initial-delay=3600000", "payment.kafka.relay-interval=120",
        "payment.provider.timeout=6s"
})
@Import(PaymentOutcomePostgresFixture.ControlledTime.class)
abstract class PaymentOutcomePostgresFixture {
    static final Instant START = Instant.parse("2026-11-21T10:00:00Z");
    static final AdjustableClock clock = new AdjustableClock();
    static final ProviderMockFixture provider;
    static final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
    static final HttpClient HTTP = HttpClient.newHttpClient();
    static final String RUN = UUID.randomUUID().toString();
    static final String CLOSURES = "payment-test-closures-" + RUN;
    static final String RESULTS = "payment-test-results-" + RUN;
    static final String BOOTSTRAP = System.getenv().getOrDefault("PAYMENT_TEST_KAFKA_SERVERS", "localhost:55990");
    private final List<JsonNode> seen = new ArrayList<>();

    static {
        try {
            provider = new ProviderMockFixture(clock);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    @DynamicPropertySource
    static void peers(DynamicPropertyRegistry properties) {
        properties.add("payment.kafka.bootstrap-servers", () -> BOOTSTRAP);
        properties.add("payment.kafka.checkout-events-topic", () -> CLOSURES);
        properties.add("payment.kafka.result-topic", () -> RESULTS);
        properties.add("payment.kafka.checkout-group", () -> "payment-test-" + RUN);
        properties.add("payment.provider.base-url", () -> provider.origin().toString());
        properties.add("payment.provider.credential-header", () -> ProviderMockFixture.CREDENTIAL_HEADER);
        properties.add("payment.provider.credential", () -> ProviderMockFixture.CREDENTIAL);
        properties.add("payment.provider.webhook-secret", () -> ProviderMockFixture.WEBHOOK_SECRET);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ControlledTime {
        @Bean
        @Primary
        Clock outcomeTestClock() {
            return clock;
        }
    }

    @LocalServerPort int port;
    @Autowired PaymentRepository payments;
    @Autowired PaymentCollectionWorkRepository work;
    @Autowired JdbcClient jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired PaymentCollectionJob job;
    @Autowired PaymentCollectionService collection;
    @Autowired PaymentGateway gateway;

    @BeforeEach
    void useTestDatabase() {
        assertThat(jdbc.sql("SELECT current_database()").query(String.class).single()).contains("test");
        clock.set(START);
    }

    Prepared preparePayment() {
        return new TransactionTemplate(transactions).execute(status -> {
            UUID checkoutId = UUID.randomUUID();
            UUID customerId = UUID.randomUUID();
            work.lockCheckout(checkoutId);
            var payment = new Payment(checkoutId, customerId, 55000L, "VND", "DEMO_PROVIDER", "DEV", checkoutId.toString());
            payment.setExpiresAt(START.plusSeconds(900));
            payments.saveAndFlush(payment);
            work.schedule(payment.getPaymentId(), START.plusSeconds(5));
            return new Prepared(payment);
        });
    }

    void openAtProvider(Payment payment) {
        gateway.createPayment(new CreatePaymentRequest(payment.getPaymentId(), payment.getCheckoutSessionId(),
                payment.getAmountVnd(), "VND", payment.getExpiresAt()));
        jdbc.sql("UPDATE payment_collection_work SET dispatch_started = true WHERE payment_id = ?")
                .param(payment.getPaymentId()).update();
    }

    URI callback() {
        return URI.create("http://127.0.0.1:" + port + "/api/v1/payments/webhooks/demo-provider");
    }

    byte[] result(Payment payment, UUID eventId, String ref, long amount) {
        return ("""
                {
                  "eventId": "%s", "type": "payment.result",
                  "occurredAt": "%s", "paymentId": "%s",
                  "providerTransactionId": "%s", "amountVnd": %d, "currency": "VND",
                  "status": "SUCCESS", "completedAt": "%s"
                }
                """.formatted(eventId, START, payment.getPaymentId(), ref, amount, START)).getBytes(StandardCharsets.UTF_8);
    }

    int sendFixture(UUID event) {
        try {
            return provider.sendEvent(event, callback(), clock.instant());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    int sendSigned(byte[] data, Instant time) throws Exception {
        String stamp = Long.toString(time.getEpochSecond());
        return send(data, stamp, signature(stamp, data));
    }

    int send(byte[] data, String stamp, String signature) throws Exception {
        return HTTP.send(request(data, stamp, signature), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    HttpRequest request(byte[] data, String stamp, String signature) {
        return HttpRequest.newBuilder(callback()).timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json")
                .header("X-Webhook-Timestamp", stamp).header("X-Webhook-Signature", signature)
                .POST(HttpRequest.BodyPublishers.ofByteArray(data)).build();
    }

    static String signature(String stamp, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(ProviderMockFixture.WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update((stamp + ".").getBytes(StandardCharsets.US_ASCII));
        return HexFormat.of().formatHex(mac.doFinal(data));
    }

    long inboxCount(UUID eventId) {
        return jdbc.sql("SELECT count(*) FROM provider_webhook_inbox WHERE event_id = ?")
                .param(eventId.toString()).query(Long.class).single();
    }

    long outboxCount(Payment payment) {
        return jdbc.sql("SELECT count(*) FROM outbox_events WHERE aggregate_id = ?")
                .param(payment.getPaymentId()).query(Long.class).single();
    }

    KafkaConsumer<String, String> resultConsumer() {
        var reader = new KafkaConsumer<String, String>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP,
                ConsumerConfig.GROUP_ID_CONFIG, "payment-proof-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
        reader.subscribe(List.of(RESULTS));
        return reader;
    }

    JsonNode awaitEvent(KafkaConsumer<String, String> reader, Payment payment, String type) throws Exception {
        String key = payment.getPaymentId().toString();
        long end = System.nanoTime() + Duration.ofSeconds(18).toNanos();
        do {
            for (var message : reader.poll(Duration.ofMillis(200))) {
                var event = json.readTree(message.value());
                assertThat(message.key()).isEqualTo(event.path("aggregateId").asString());
                seen.add(event);
            }
            for (var event : seen) {
                if (key.equals(event.path("aggregateId").asString()) && type.equals(event.path("eventType").asString())) {
                    return event;
                }
            }
        } while (System.nanoTime() < end);
        throw new AssertionError("Kafka did not deliver " + type + " for " + key);
    }

    void publishClose(UUID key, UUID aggregate, UUID customer, UUID event, String reason, String source, long version)
            throws Exception {
        try (var writer = new KafkaProducer<String, String>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class))) {
            String close = json.writeValueAsString(Map.of(
                    "eventId", event.toString(), "eventType", "checkout.closed", "occurredAt", START.toString(),
                    "producer", source, "aggregateId", aggregate.toString(), "version", version,
                    "payload", Map.of("checkoutSessionId", aggregate.toString(), "customerId", customer.toString(),
                            "closedAt", START.toString(), "closeReason", reason)));
            writer.send(new ProducerRecord<>(CLOSURES, key.toString(), close)).get();
        }
    }

    static void await(BooleanSupplier condition) throws InterruptedException {
        long end = System.nanoTime() + Duration.ofSeconds(18).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < end) {
            Thread.sleep(40);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    record Prepared(Payment payment) { }

    static final class AdjustableClock extends Clock {
        private final AtomicReference<Instant> current = new AtomicReference<>(START);

        void set(Instant instant) { current.set(instant); }
        @Override public Instant instant() { return current.get(); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
    }
}
