package com.example.finance_service;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

class PendingSettlementPostgresTests {
	private static final String ORDER_CREDENTIAL = "phase2-finance-order";
	static String ORDER_TOPIC;
	static String PAYMENT_TOPIC;
	private static String IDENTITY_TOPIC;
	private static String FINANCE_TOPIC;
	private static String GROUP;
	private static final JsonMapper JSON = new JsonMapper();
	static ConfigurableApplicationContext finance;
	private static KafkaProducer<String, String> producer;
	private static Admin broker;
	static JdbcTemplate db;
	private static String bootstrap;

	@BeforeAll
	static void start() throws Exception {
		GROUP = "finance-it-" + UUID.randomUUID();
		ORDER_TOPIC = GROUP + "-order";
		PAYMENT_TOPIC = GROUP + "-payment";
		IDENTITY_TOPIC = GROUP + "-identity";
		FINANCE_TOPIC = GROUP + "-finance";
		var url = required("SPRING_DATASOURCE_URL");
		if (!url.startsWith("jdbc:postgresql://") || !url.matches(".*/finance_test_[A-Za-z0-9_]+")) {
			throw new IllegalStateException("Use a dedicated finance_test_* PostgreSQL database");
		}
		db = new JdbcTemplate(new DriverManagerDataSource(url, required("SPRING_DATASOURCE_USERNAME"),
				required("SPRING_DATASOURCE_PASSWORD")));
		bootstrap = required("FINANCE_TEST_KAFKA_BOOTSTRAP");
		broker = Admin.create(Map.of("bootstrap.servers", bootstrap));
		broker.createTopics(List.of(ORDER_TOPIC, PAYMENT_TOPIC, IDENTITY_TOPIC, FINANCE_TOPIC).stream()
				.map(topic -> new NewTopic(topic, 1, (short) 1)).toList())
				.all().get(10, TimeUnit.SECONDS);
		producer = new KafkaProducer<>(Map.of("bootstrap.servers", bootstrap, "acks", "all"),
				new StringSerializer(), new StringSerializer());
		startFinance();
	}

	@AfterAll
	static void stop() {
		if (finance != null) {
			finance.close();
		}
		if (producer != null) {
			producer.close(Duration.ofSeconds(2));
		}
		if (broker != null) {
			broker.close(Duration.ofSeconds(2));
		}
	}

	@Test
	void commissionRateIsOnlyExposedToAnAuthenticatedOrderCaller() throws Exception {
		try (var http = HttpClient.newHttpClient()) {
			int port = ((WebServerApplicationContext) finance).getWebServer().getPort();
			var target = URI.create("http://127.0.0.1:" + port + "/internal/v1/finance/commission-rate");
			assertThat(http.send(HttpRequest.newBuilder(target).GET().build(),
					HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(401);
			assertThat(http.send(HttpRequest.newBuilder(target).header("X-Service-Token", "another-service").GET().build(),
					HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(401);
			var result = http.send(HttpRequest.newBuilder(target).header("X-Service-Token", ORDER_CREDENTIAL).GET().build(),
					HttpResponse.BodyHandlers.ofString());
			assertThat(result.statusCode()).isEqualTo(200);
			assertThat(JSON.readTree(result.body()).path("data").path("commissionBps").asInt()).isEqualTo(500);
		}
	}

	@Test
	void appliesPendingPerOrderWithRoundingAndBusinessDedupe() throws Exception {
		UUID seller = UUID.randomUUID();
		approve(seller);
		long platformBefore = platformPending();
		UUID checkout = UUID.randomUUID(), payment = UUID.randomUUID(), first = UUID.randomUUID(), second = UUID.randomUUID();
		UUID eventId = UUID.randomUUID(), duplicateId = UUID.randomUUID(), conflictId = UUID.randomUUID();
		var lines = List.of(line(first, seller, 10_010, 500), line(second, seller, 9, 500));
		String original = paid(eventId, checkout, payment, lines);
		publish(ORDER_TOPIC, checkout, original);
		publish(ORDER_TOPIC, checkout, original);
		publish(ORDER_TOPIC, checkout, paid(eventId, checkout, payment, List.of(line(first, seller, 900, 500))));
		publish(ORDER_TOPIC, checkout, paid(duplicateId, checkout, payment, lines));
		publish(ORDER_TOPIC, checkout, paid(conflictId, checkout, payment, List.of(line(first, seller, 900, 500))));
		await(() -> "FAILED_REVIEW".equals(state(conflictId)));

		assertThat(state(eventId)).isEqualTo("APPLIED");
		assertThat(state(duplicateId)).isEqualTo("APPLIED");
		assertThat(count("select count(*) from finance.settlement where payment_id = ?", payment)).isEqualTo(2);
		assertThat(count("""
				select count(*) from finance.settlement where order_id = ? and status = 'PENDING'
				and gross_amount_vnd = 10010 and commission_amount_vnd = 501 and seller_amount_vnd = 9509
				""", first)).isEqualTo(1);
		assertThat(count("select count(*) from finance.wallet_transaction where reference_id = ?", payment)).isEqualTo(3);
		assertThat(count("""
				select count(*) from finance.wallet_transaction where reference_id = ? and (
				    pending_delta_vnd <= 0 or available_delta_vnd <> 0 or withdrawn_delta_vnd <> 0)
				""", payment)).isZero();
		assertThat(sellerPending(seller)).isEqualTo(9518);
		assertThat(platformPending()).isEqualTo(platformBefore + 501);
	}

	@Test
	void keepsMissingWalletCheckoutAcrossRestartAndOnlyResumesAfterValidApproval() throws Exception {
		UUID seller = UUID.randomUUID(), secondSeller = UUID.randomUUID(), checkout = UUID.randomUUID(), payment = UUID.randomUUID();
		UUID first = UUID.randomUUID(), second = UUID.randomUUID(), event = UUID.randomUUID();
		approve(seller);
		long platformBefore = platformPending();
		long offset = publish(ORDER_TOPIC, checkout, paid(event, checkout, payment,
				List.of(line(first, seller, 100, 500), line(second, secondSeller, 100, 500))));
		await(() -> "WAITING_DEPENDENCY".equals(state(event)));
		await(() -> committed(ORDER_TOPIC, offset));
		assertThat(sellerPending(seller)).isZero();
		assertThat(settlementsFor(payment)).isZero();
		finance.close();
		startFinance();

		UUID malformedApproval = UUID.randomUUID();
		long rejected = publish(IDENTITY_TOPIC, secondSeller, approved(malformedApproval, secondSeller, "product-service"));
		await(() -> committed(IDENTITY_TOPIC, rejected));
		UUID diagnosticId = UUID.nameUUIDFromBytes((IDENTITY_TOPIC + ":0:" + rejected).getBytes(StandardCharsets.UTF_8));
		assertThat(state(diagnosticId)).isEqualTo("FAILED_REVIEW");
		assertThat(state(event)).isEqualTo("WAITING_DEPENDENCY");
		assertThat(walletsFor(secondSeller)).isZero();
		assertThat(settlementsFor(payment)).isZero();

		UUID approval = UUID.randomUUID(), approvalReplay = UUID.randomUUID();
		String firstApproval = approved(approval, secondSeller, "identity-service");
		publish(IDENTITY_TOPIC, secondSeller, firstApproval);
		publish(IDENTITY_TOPIC, secondSeller, firstApproval);
		publish(IDENTITY_TOPIC, secondSeller, approved(approvalReplay, secondSeller, "identity-service"));
		await(() -> "APPLIED".equals(state(event)) && "APPLIED".equals(state(approvalReplay)));
		assertThat(walletsFor(secondSeller)).isOne();
		assertThat(settlementsFor(payment)).isEqualTo(2);
		assertThat(sellerPending(seller)).isEqualTo(95);
		assertThat(sellerPending(secondSeller)).isEqualTo(95);
		assertThat(platformPending()).isEqualTo(platformBefore + 10);
	}

	@Test
	void rollbackLeavesNoSettlementOrLedgerAndKafkaRetriesTheSameEvent() throws Exception {
		UUID seller = UUID.randomUUID(), checkout = UUID.randomUUID(), payment = UUID.randomUUID();
		UUID order = UUID.randomUUID(), event = UUID.randomUUID();
		approve(seller);
		long platformBefore = platformPending();
		db.execute("create sequence finance.p2_settlement_fault");
		db.execute("""
				create function finance.p2_fail_settlement() returns trigger language plpgsql as $$
				begin
				    perform nextval('finance.p2_settlement_fault');
				    raise exception 'phase2 test ledger rollback';
				end;
				$$
				""");
		db.execute("""
				create trigger p2_fail_settlement before insert on finance.wallet_transaction for each row
				when (new.reference_id = '%s') execute function finance.p2_fail_settlement()
				""".formatted(payment));
		long offset;
		try {
			offset = publish(ORDER_TOPIC, checkout, paid(event, checkout, payment, List.of(line(order, seller, 10, 500))));
			await(() -> Boolean.TRUE.equals(db.queryForObject("select is_called from finance.p2_settlement_fault", Boolean.class)));
			assertThat(settlementsFor(payment)).isZero();
			assertThat(count("select count(*) from finance.wallet_transaction where reference_id = ?", payment)).isZero();
			assertThat(sellerPending(seller)).isZero();
			assertThat(platformPending()).isEqualTo(platformBefore);
			assertThat(state(event)).isNull();
			assertThat(committed(ORDER_TOPIC, offset)).isFalse();
		} finally {
			db.execute("drop trigger p2_fail_settlement on finance.wallet_transaction");
			db.execute("drop function finance.p2_fail_settlement()");
			db.execute("drop sequence finance.p2_settlement_fault");
		}
		await(() -> "APPLIED".equals(state(event)) && committed(ORDER_TOPIC, offset));
		assertThat(settlementsFor(payment)).isOne();
		assertThat(count("select count(*) from finance.wallet_transaction where reference_id = ?", payment)).isEqualTo(2);
		assertThat(sellerPending(seller)).isEqualTo(9);
		assertThat(platformPending()).isEqualTo(platformBefore + 1);
	}

	@Test
	void captureAndLateFullCheckoutRefundCannotMintSettlement() throws Exception {
		UUID payment = UUID.randomUUID(), checkout = UUID.randomUUID();
		long platformBefore = platformPending();
		publish(ORDER_TOPIC, payment, envelope(UUID.randomUUID(), "payment.succeeded", "payment-service", payment,
				Map.of("paymentId", payment, "checkoutSessionId", checkout, "amountVnd", 100), Map.of("kind", "SERVICE")));
		long last = publish(ORDER_TOPIC, payment, envelope(UUID.randomUUID(), "refund.succeeded", "payment-service", payment,
				Map.of("paymentId", payment, "scope", "FULL_CHECKOUT", "amountVnd", 100), Map.of("kind", "SERVICE")));
		await(() -> committed(ORDER_TOPIC, last));
		assertThat(settlementsFor(payment)).isZero();
		assertThat(count("select count(*) from finance.wallet_transaction where reference_id = ?", payment)).isZero();
		assertThat(platformPending()).isEqualTo(platformBefore);
	}

	static void startFinance() {
		finance = new SpringApplicationBuilder(FinanceServiceApplication.class).run(
				"--server.port=0", "--eureka.client.enabled=false", "--logging.level.root=WARN",
				"--finance.internal.tokens.order=" + ORDER_CREDENTIAL,
				"--finance.kafka.bootstrap-servers=" + bootstrap, "--finance.kafka.group=" + GROUP,
				"--finance.kafka.order-topic=" + ORDER_TOPIC, "--finance.kafka.identity-topic=" + IDENTITY_TOPIC,
				"--finance.kafka.payment-topic=" + PAYMENT_TOPIC, "--finance.kafka.result-topic=" + FINANCE_TOPIC,
				"--finance.settlement.sweep-delay-ms=100");
	}

	static void approve(UUID seller) throws Exception {
		UUID event = UUID.randomUUID();
		publish(IDENTITY_TOPIC, seller, approved(event, seller, "identity-service"));
		await(() -> "APPLIED".equals(state(event)));
	}

	private static String approved(UUID event, UUID seller, String source) {
		return envelope(event, "seller.approved", source, seller,
				Map.of("userId", seller, "sellerId", seller, "approvalStatus", "APPROVED", "authzVersion", 2),
				Map.of("kind", "USER", "context", "ADMIN", "userId", UUID.randomUUID()));
	}

	static String paid(UUID event, UUID checkout, UUID payment, List<Map<String, Object>> orders) {
		return envelope(event, "checkout.paid", "order-service", checkout, Map.of("paymentId", payment,
				"checkoutSessionId", checkout, "customerId", UUID.randomUUID(), "paidAt", Instant.now().toString(),
				"source", "BUY_NOW", "cartItems", List.of(), "orders", orders), Map.of("kind", "SERVICE"));
	}

	static Map<String, Object> line(UUID order, UUID seller, long amount, int rate) {
		return Map.of("orderId", order, "sellerId", seller, "totalAmountVnd", amount, "commissionBps", rate);
	}

	static String envelope(UUID event, String type, String source, UUID aggregate, Map<String, Object> payload,
			Map<String, Object> actor) {
		return JSON.writeValueAsString(Map.of("eventId", event, "eventType", type, "occurredAt", Instant.now().toString(),
				"producer", source, "aggregateId", aggregate, "version", 1, "payload", payload, "actor", actor));
	}

	static long publish(String topic, UUID key, String payload) throws Exception {
		return producer.send(new ProducerRecord<>(topic, key.toString(), payload)).get(10, TimeUnit.SECONDS).offset();
	}

	static boolean committed(String topic, long offset) {
		try {
			OffsetAndMetadata result = broker.listConsumerGroupOffsets(GROUP).partitionsToOffsetAndMetadata()
					.get(3, TimeUnit.SECONDS).get(new TopicPartition(topic, 0));
			return result != null && result.offset() > offset;
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new AssertionError("Interrupted while reading committed offset", interrupted);
		} catch (Exception failure) {
			throw new AssertionError("Cannot read committed offset", failure);
		}
	}

	static void await(BooleanSupplier condition) throws InterruptedException {
		long limit = System.nanoTime() + Duration.ofSeconds(18).toNanos();
		while (!condition.getAsBoolean()) {
			if (System.nanoTime() >= limit) {
				throw new AssertionError("Timed out waiting for PostgreSQL or Kafka evidence");
			}
			Thread.sleep(100);
		}
	}

	static String state(UUID event) {
		return db.queryForList("select status from finance.settlement_inbox where event_id = ?", String.class, event)
				.stream().findFirst().orElse(null);
	}

	private static long settlementsFor(UUID payment) {
		return count("select count(*) from finance.settlement where payment_id = ?", payment);
	}

	private static long walletsFor(UUID seller) {
		return count("select count(*) from finance.wallet where owner_type = 'SELLER' and owner_id = ?", seller);
	}

	private static long sellerPending(UUID seller) {
		return count("select pending_balance_vnd from finance.wallet where owner_type = 'SELLER' and owner_id = ?", seller);
	}

	private static long platformPending() {
		return count("select pending_balance_vnd from finance.wallet where owner_type = 'PLATFORM'");
	}

	private static long count(String sql, Object... args) {
		return db.queryForObject(sql, Long.class, args);
	}

	private static String required(String key) {
		String value = System.getenv(key);
		if (value == null || value.isBlank()) {
			throw new IllegalStateException("Missing test setting " + key);
		}
		return value;
	}
}
