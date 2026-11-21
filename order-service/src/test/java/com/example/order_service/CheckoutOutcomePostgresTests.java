package com.example.order_service;

import com.example.order_service.messaging.OrderCheckoutEventRelay;
import com.example.order_service.service.CheckoutSaleEvidenceWorker;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckoutOutcomePostgresTests extends PayHttpTestSupport {
	private static final String BROKER = localKafka();
	private static final String SUFFIX = UUID.randomUUID().toString();
	private static final String IN = "order-test-payment-" + SUFFIX;
	private static final String OUT = "order-test-closure-" + SUFFIX;
	private static final String GROUP = "order-test-group-" + SUFFIX;

	@Autowired
	OrderCheckoutEventRelay relay;
	@Autowired
	CheckoutSaleEvidenceWorker saleEvidence;

	@DynamicPropertySource
	static void kafka(DynamicPropertyRegistry config) throws Exception {
		configureKafka(config, IN, OUT, GROUP);
	}

	static void configureKafka(DynamicPropertyRegistry config, String incoming, String outgoing, String group) throws Exception {
		try (var admin = Admin.create(Map.of("bootstrap.servers", BROKER))) {
			admin.createTopics(List.of(new NewTopic(incoming, 1, (short) 1), new NewTopic(outgoing, 1, (short) 1)))
					.all().get(8, TimeUnit.SECONDS);
		}
		config.add("order.kafka.bootstrap-servers", () -> BROKER);
		config.add("order.kafka.payment-topic", () -> incoming);
		config.add("order.kafka.checkout-topic", () -> outgoing);
		config.add("order.kafka.payment-group", () -> group);
		config.add("order.kafka.poll-delay-ms", () -> 3600000);
		config.add("order.checkout.evidence.poll-delay-ms", () -> 3600000);
	}

	@Test
	void retriesTheExactHistoricalRequestAndPublishesOnePaidSaleAfterBothSourcesQualify() throws Exception {
		var fixture = checkout();
		UUID payment = UUID.randomUUID();
		UUID firstEvent = UUID.randomUUID();
		UUID duplicate = UUID.randomUUID();
		Instant capture = Instant.now().truncatedTo(ChronoUnit.MICROS).minusNanos(123);
		send(payment, success(firstEvent, payment, fixture.id(), 2000, capture));
		send(payment, success(duplicate, payment, fixture.id(), 2000, capture));
		await(() -> "WAITING_DEPENDENCY".equals(inbox(duplicate)));
		assertThat(inbox(firstEvent)).isEqualTo("WAITING_DEPENDENCY");
		assertThat(jdbc.queryForObject("select completed_at from orders.checkout_sale_evidence where checkout_session_id = ?",
				String.class, fixture.id())).isEqualTo(capture.toString());

		var identityRequest = new AtomicReference<JsonNode>();
		var productRequest = new AtomicReference<JsonNode>();
		var sawUnlockedCheckout = new AtomicBoolean();
		identityEvidence.set(exchange -> {
			var request = JSON.readTree(exchange.getRequestBody());
			identityRequest.set(request);
			sawUnlockedCheckout.set(canLockIndependently(fixture.id()));
			data(exchange, Map.of("completedAt", request.path("completedAt").asString(),
					"items", List.of(Map.of("sellerId", SELLER, "decision", "ELIGIBLE"))));
		});
		runEvidenceFor(fixture.id());
		assertWaitingWithRetry(fixture);

		productEvidence.set(exchange -> {
			var request = JSON.readTree(exchange.getRequestBody());
			productRequest.set(request);
			data(exchange, Map.of("completedAt", request.path("completedAt").asString(),
					"items", List.of(Map.of("productId", fixture.sample().product(),
							"sellerId", UUID.randomUUID(), "decision", "ELIGIBLE"))));
		});
		runEvidenceFor(fixture.id());
		assertWaitingWithRetry(fixture);

		productEvidence.set(exchange -> {
			var request = JSON.readTree(exchange.getRequestBody());
			productRequest.set(request);
			data(exchange, Map.of("completedAt", request.path("completedAt").asString(),
					"items", List.of(Map.of("productId", fixture.sample().product(),
							"sellerId", SELLER, "decision", "ELIGIBLE"))));
		});
		jdbc.update("update orders.inventory set reserved_quantity = 0 where product_id = ?", fixture.sample().product());
		try {
			assertThatThrownBy(() -> runEvidenceFor(fixture.id())).isInstanceOf(IllegalStateException.class)
					.hasMessage("Held stock is missing for checkout sale");
			assertThat(inbox(firstEvent)).isEqualTo("WAITING_DEPENDENCY");
			assertThat(state(fixture.id())).isEqualTo("AWAITING_PAYMENT");
			assertThat(eventCount(fixture.id())).isZero();
			assertThat(jdbc.queryForObject("""
					select count(*) from orders.stock_movement where product_id = ? and movement_type = 'SOLD'
					""", Integer.class, fixture.sample().product())).isZero();
		} finally {
			jdbc.update("update orders.inventory set reserved_quantity = 2 where product_id = ?", fixture.sample().product());
		}
		jdbc.update("update orders.checkout_sale_evidence set lease_until = statement_timestamp() - interval '1 second' where checkout_session_id = ?",
				fixture.id());
		runEvidenceFor(fixture.id());
		assertThat(sawUnlockedCheckout).isTrue();
		assertThat(identityRequest.get().path("completedAt").asString()).isEqualTo(capture.toString());
		assertThat(identityRequest.get().path("sellerIds")).isEqualTo(JSON.valueToTree(List.of(SELLER)));
		assertThat(productRequest.get().path("completedAt").asString()).isEqualTo(capture.toString());
		assertThat(productRequest.get().path("productIds")).isEqualTo(JSON.valueToTree(List.of(fixture.sample().product())));
		assertThat(state(fixture.id())).isEqualTo("PAID");
		assertThat(stock(fixture)).isZero();
		assertThat(jdbc.queryForObject("select on_hand_quantity from orders.inventory where product_id = ?", Integer.class,
				fixture.sample().product())).isEqualTo(6);
		assertThat(jdbc.queryForObject("""
				select count(*) from orders.stock_movement where product_id = ? and movement_type = 'SOLD'
					and quantity_delta = -2 and order_item_id is not null
				""", Integer.class, fixture.sample().product())).isOne();
		assertThat(jdbc.queryForObject("""
				select r.status from orders.reservation r join orders.order_item i using(order_item_id)
				join orders.purchase_order o using(order_id) where o.checkout_session_id = ?
				""", String.class, fixture.id())).isEqualTo("COMMITTED");
		assertThat(jdbc.queryForObject("""
				select status = 'PAID' and seller_action_due_at = paid_at + interval '7 days'
				from orders.purchase_order where checkout_session_id = ?
				""", Boolean.class, fixture.id())).isTrue();
		assertThat(inbox(firstEvent)).isEqualTo("APPLIED");
		assertThat(inbox(duplicate)).isEqualTo("APPLIED");
		var paid = eventFor(fixture.id());
		assertThat(paid.path("eventType").asString()).isEqualTo("checkout.paid");
		assertThat(paid.path("payload").path("paymentId").asString()).isEqualTo(payment.toString());
		assertThat(paid.path("payload").path("paidAt").asString()).isEqualTo(capture.toString());
		assertThat(paid.path("payload").path("cartItems").isEmpty()).isTrue();
		assertThat(paid.path("payload").path("orders").get(0).path("commissionBps").asInt()).isEqualTo(500);
		UUID laterReplay = UUID.randomUUID();
		send(payment, success(laterReplay, payment, fixture.id(), 2000, capture));
		await(() -> "APPLIED".equals(inbox(laterReplay)));
		assertThat(eventCount(fixture.id())).isOne();
		assertThat(jdbc.queryForObject("select count(*) from orders.stock_movement where product_id = ? and movement_type = 'SOLD'",
				Integer.class, fixture.sample().product())).isOne();

		makeFirst(UUID.fromString(paid.path("eventId").asString()));
		assertThat(relay.deliverNext()).isTrue();
		try (var subscriber = subscribe(OUT, GROUP + "-paid")) {
			assertThat(JSON.readTree(readClosure(subscriber, fixture.id()))).isEqualTo(paid);
		}
	}

	@Test
	void leavesAuthoritativeIneligibilityForTheCheckoutDeadlineWithoutPayingOrFailingEarly() throws Exception {
		var fixture = checkout();
		UUID payment = UUID.randomUUID();
		UUID event = UUID.randomUUID();
		send(payment, success(event, payment, fixture.id(), 2000));
		await(() -> "WAITING_DEPENDENCY".equals(inbox(event)));
		identityEvidence.set(exchange -> {
			var request = JSON.readTree(exchange.getRequestBody());
			data(exchange, Map.of("completedAt", request.path("completedAt").asString(),
					"items", List.of(Map.of("sellerId", SELLER, "decision", "ELIGIBLE"))));
		});
		productEvidence.set(exchange -> {
			var request = JSON.readTree(exchange.getRequestBody());
			data(exchange, Map.of("completedAt", request.path("completedAt").asString(),
					"items", List.of(JSON.createObjectNode().put("productId", fixture.sample().product().toString())
							.putNull("sellerId").put("decision", "INELIGIBLE"))));
		});
		runEvidenceFor(fixture.id());
		assertThat(state(fixture.id())).isEqualTo("AWAITING_PAYMENT");
		assertThat(inbox(event)).isEqualTo("WAITING_DEPENDENCY");
		assertThat(stock(fixture)).isEqualTo(2);
		assertThat(eventCount(fixture.id())).isZero();
		assertThat(jdbc.queryForObject("""
				select next_attempt_at is null and last_error = 'HISTORICAL_SALE_INELIGIBLE'
				from orders.checkout_sale_evidence where checkout_session_id = ?
				""", Boolean.class, fixture.id())).isTrue();
	}

	private void runEvidenceFor(UUID id) {
		jdbc.update("update orders.checkout_sale_evidence set next_attempt_at = '2000-01-01T00:00:00Z' where checkout_session_id = ?", id);
		assertThat(saleEvidence.runOnce()).isTrue();
	}

	private void assertWaitingWithRetry(Fixture fixture) {
		assertThat(state(fixture.id())).isEqualTo("AWAITING_PAYMENT");
		assertThat(stock(fixture)).isEqualTo(2);
		assertThat(eventCount(fixture.id())).isZero();
		assertThat(jdbc.queryForObject("""
				select next_attempt_at > statement_timestamp() and lease_token is null
					and last_error = 'HISTORICAL_SALE_EVIDENCE_UNAVAILABLE'
				from orders.checkout_sale_evidence where checkout_session_id = ?
				""", Boolean.class, fixture.id())).isTrue();
	}

	private static boolean canLockIndependently(UUID id) {
		try (var independent = DriverManager.getConnection(System.getenv("ORDER_TEST_DATABASE_URL"),
				System.getenv("ORDER_TEST_DATABASE_USER"), System.getenv("ORDER_TEST_DATABASE_PASSWORD"));
				var query = independent.prepareStatement("select 1 from orders.checkout_session where checkout_session_id = ? for update nowait")) {
			query.setObject(1, id);
			try (var row = query.executeQuery()) {
				return row.next();
			}
		} catch (java.sql.SQLException failure) {
			return false;
		}
	}

	@Test
	void appliesPaymentFailureOnceAndRelaysTheSameCommittedEventAfterLeaseLoss() throws Exception {
		var fixture = checkout();
		UUID payment = UUID.randomUUID();
		UUID failed = UUID.randomUUID();
		String raw = failure(failed, payment, fixture.id(), "DECLINED");
		send(payment, raw);
		await(() -> "APPLIED".equals(inbox(failed)));
		assertClosed(fixture, "PAYMENT_FAILED");
		send(payment, raw);
		UUID duplicate = UUID.randomUUID();
		send(payment, failure(duplicate, payment, fixture.id(), "DECLINED"));
		await(() -> "APPLIED".equals(inbox(duplicate)));
		assertClosed(fixture, "PAYMENT_FAILED");
		send(payment, failure(failed, payment, fixture.id(), "CHANGED"));
		await(() -> Boolean.TRUE.equals(jdbc.queryForObject("""
				select conflict_envelope is not null from orders.checkout_inbox where event_id = ?
				""", Boolean.class, failed)));

		var checkoutEvent = eventFor(fixture.id());
		UUID id = UUID.fromString(checkoutEvent.path("eventId").asString());
		makeFirst(id);
		assertThat(relay.deliverNext()).isTrue();
		assertThat(sent(id)).isTrue();
		try (var subscriber = subscribe(OUT, GROUP)) {
			String first = readClosure(subscriber, fixture.id());
			assertThat(JSON.readTree(first)).isEqualTo(checkoutEvent);
			jdbc.update("""
					update orders.checkout_event_outbox set sent_at = null, lease_token = ?,
						lease_until = statement_timestamp() - interval '1 second' where event_id = ?
					""", UUID.randomUUID(), id);
			assertThat(relay.deliverNext()).isTrue();
			assertThat(readClosure(subscriber, fixture.id())).isEqualTo(first);
			assertThat(sent(id)).isTrue();
		}
	}

	@Test
	void holdsVerifiedSuccessForHistoricalEvidenceAndClosesAtExpiryWithoutPaidEffects() throws Exception {
		var fixture = checkout();
		UUID paymentId = UUID.randomUUID();
		setDeadline(fixture.id(), 6);
		var capturedAt = Instant.now();
		UUID mismatched = UUID.randomUUID();
		send(paymentId, success(mismatched, paymentId, fixture.id(), 2001));
		await(() -> "FAILED_REVIEW".equals(inbox(mismatched)));
		UUID impossible = UUID.randomUUID();
		send(paymentId, success(impossible, paymentId, fixture.id(), 2000, capturedAt.plusSeconds(15)));
		await(() -> "FAILED_REVIEW".equals(inbox(impossible)));
		UUID waiting = UUID.randomUUID();
		send(paymentId, success(waiting, paymentId, fixture.id(), 2000, capturedAt));
		await(() -> "WAITING_DEPENDENCY".equals(inbox(waiting)));
		assertThat(state(fixture.id())).isEqualTo("AWAITING_PAYMENT");
		assertThat(stock(fixture)).isEqualTo(2);
		assertThat(eventCount(fixture.id())).isZero();

		UUID conflict = UUID.randomUUID();
		send(paymentId, failure(conflict, paymentId, fixture.id(), "DECLINED"));
		await(() -> "FAILED_REVIEW".equals(inbox(conflict)));
		assertThat(stock(fixture)).isEqualTo(2);
		await(() -> pastDeadline(fixture.id()));
		UUID afterDeadline = UUID.randomUUID();
		send(paymentId, success(afterDeadline, paymentId, fixture.id(), 2000, capturedAt));
		await(() -> "APPLIED".equals(inbox(afterDeadline)));
		assertClosed(fixture, "EXPIRED");
		assertThat(inbox(waiting)).isEqualTo("FAILED_REVIEW");
		assertThat(jdbc.queryForObject("select count(*) from orders.stock_movement where product_id = ? and movement_type = 'SOLD'",
				Integer.class, fixture.sample().product())).isZero();
	}

	@Test
	void readsPostgresTimeAfterTheCheckoutLockAndUsesExpiredWhenTheLockCrossesTheDeadline() throws Exception {
		var fixture = checkout();
		setDeadline(fixture.id(), 6);
		UUID paymentId = UUID.randomUUID();
		UUID eventId = UUID.randomUUID();
		try (var independent = DriverManager.getConnection(System.getenv("ORDER_TEST_DATABASE_URL"),
				System.getenv("ORDER_TEST_DATABASE_USER"), System.getenv("ORDER_TEST_DATABASE_PASSWORD"))) {
			independent.setAutoCommit(false);
			try (var lock = independent.prepareStatement("select 1 from orders.checkout_session where checkout_session_id = ? for update")) {
				lock.setObject(1, fixture.id());
				try (var row = lock.executeQuery()) {
					assertThat(row.next()).isTrue();
				}
			}
			send(paymentId, failure(eventId, paymentId, fixture.id(), "DECLINED"));
			await(() -> jdbc.queryForObject("""
					select exists (select 1 from pg_stat_activity
						where datname = current_database() and wait_event_type = 'Lock' and query like '%checkout_session%')
					""", Boolean.class));
			assertThat(pastDeadline(fixture.id())).isFalse();
			await(() -> pastDeadline(fixture.id()));
			assertThat(inbox(eventId)).isNull();
			independent.commit();
		}
		await(() -> "APPLIED".equals(inbox(eventId)));
		assertClosed(fixture, "EXPIRED");
	}

	@Test
	void retriesKafkaAfterRollbackWithoutCommittingTheOffsetOrPartialInventoryEffects() throws Exception {
		var fixture = checkout();
		jdbc.update("update orders.inventory set reserved_quantity = 0 where product_id = ?", fixture.sample().product());
		UUID paymentId = UUID.randomUUID();
		UUID eventId = UUID.randomUUID();
		long offset = send(paymentId, failure(eventId, paymentId, fixture.id(), "DECLINED"));
		try (var admin = Admin.create(Map.of("bootstrap.servers", BROKER))) {
			// Give the active listener two retry iterations with the failing stock invariant in place.
			Thread.sleep(Duration.ofSeconds(2));
			var committed = admin.listConsumerGroupOffsets(GROUP).partitionsToOffsetAndMetadata()
					.get(5, TimeUnit.SECONDS).get(new TopicPartition(IN, 0));
			assertThat(committed == null || committed.offset() <= offset).isTrue();
			assertThat(state(fixture.id())).isEqualTo("AWAITING_PAYMENT");
			assertThat(inbox(eventId)).isNull();
			assertThat(eventCount(fixture.id())).isZero();
		} finally {
			jdbc.update("update orders.inventory set reserved_quantity = 2 where product_id = ?", fixture.sample().product());
		}
		await(() -> "APPLIED".equals(inbox(eventId)));
		assertClosed(fixture, "PAYMENT_FAILED");
	}

	private Fixture checkout() throws Exception {
		var sample = sample(8, 2);
		var created = pay(sample.customer(), UUID.randomUUID(), quote(sample), sample.address());
		assertThat(created.statusCode()).as(created.body()).isEqualTo(202);
		return new Fixture(sample, UUID.fromString(body(created).path("data").path("checkoutSessionId").asString()));
	}

	private void assertClosed(Fixture fixture, String reason) {
		assertThat(state(fixture.id())).isEqualTo("CLOSED");
		assertThat(jdbc.queryForObject("select status from orders.purchase_order where checkout_session_id = ?", String.class,
				fixture.id())).isEqualTo(reason);
		assertThat(jdbc.queryForObject("""
				select r.status from orders.reservation r join orders.order_item i using (order_item_id)
				join orders.purchase_order o using (order_id) where o.checkout_session_id = ?
				""", String.class, fixture.id())).isEqualTo("RELEASED");
		assertThat(stock(fixture)).isZero();
		assertThat(jdbc.queryForObject("select on_hand_quantity from orders.inventory where product_id = ?", Integer.class,
				fixture.sample().product())).isEqualTo(8);
		assertThat(eventCount(fixture.id())).isOne();
		var closed = eventFor(fixture.id());
		assertThat(closed.path("eventType").asString()).isEqualTo("checkout.closed");
		assertThat(closed.path("payload").path("closeReason").asString()).isEqualTo(reason);
		assertThat(closed.path("payload").path("customerId").asString()).isEqualTo(fixture.sample().customer().toString());
	}

	private int stock(Fixture fixture) {
		return jdbc.queryForObject("select reserved_quantity from orders.inventory where product_id = ?", Integer.class,
				fixture.sample().product());
	}

	private String state(UUID id) {
		return jdbc.queryForObject("select status from orders.checkout_session where checkout_session_id = ?", String.class, id);
	}

	private String inbox(UUID eventId) {
		return jdbc.query("select state from orders.checkout_inbox where event_id = ?",
				(row, index) -> row.getString(1), eventId).stream().findFirst().orElse(null);
	}

	private int eventCount(UUID checkout) {
		return jdbc.queryForObject("select count(*) from orders.checkout_event_outbox where checkout_session_id = ?", Integer.class, checkout);
	}

	private JsonNode eventFor(UUID id) {
		return JSON.readTree(jdbc.queryForObject("select envelope from orders.checkout_event_outbox where checkout_session_id = ?",
				String.class, id));
	}

	private void makeFirst(UUID id) {
		jdbc.update("update orders.checkout_event_outbox set next_attempt_at = '2000-01-01T00:00:00Z' where event_id = ?", id);
	}

	private boolean sent(UUID id) {
		return jdbc.queryForObject("select sent_at is not null from orders.checkout_event_outbox where event_id = ?", Boolean.class, id);
	}

	private void setDeadline(UUID id, int secondsFromNow) {
		jdbc.update("""
				update orders.checkout_session set expires_at = statement_timestamp() + (? * interval '1 second'),
					created_at = statement_timestamp() + (? * interval '1 second') - interval '15 minutes'
				where checkout_session_id = ?
				""", secondsFromNow, secondsFromNow, id);
	}

	private boolean pastDeadline(UUID id) {
		return jdbc.queryForObject("select statement_timestamp() >= expires_at from orders.checkout_session where checkout_session_id = ?",
				Boolean.class, id);
	}

	static String localKafka() {
		String endpoint = System.getenv("ORDER_TEST_KAFKA_BOOTSTRAP");
		if (endpoint == null || !endpoint.matches("(127\\.0\\.0\\.1|localhost):[0-9]+")) {
			throw new IllegalStateException("Set ORDER_TEST_KAFKA_BOOTSTRAP to a dedicated local Kafka broker");
		}
		return endpoint;
	}

	private static long send(UUID payment, String raw) throws Exception {
		try (var publisher = new KafkaProducer<String, String>(Map.of("bootstrap.servers", BROKER, "acks", "all",
				"key.serializer", StringSerializer.class, "value.serializer", StringSerializer.class))) {
			return publisher.send(new ProducerRecord<>(IN, payment.toString(), raw)).get(5, TimeUnit.SECONDS).offset();
		}
	}

	static KafkaConsumer<String, String> subscribe(String topic, String group) {
		var subscriber = new KafkaConsumer<String, String>(Map.of("bootstrap.servers", BROKER, "group.id", group + "-probe",
				"auto.offset.reset", "earliest", "key.deserializer", StringDeserializer.class,
				"value.deserializer", StringDeserializer.class));
		subscriber.subscribe(List.of(topic));
		return subscriber;
	}

	static String readClosure(KafkaConsumer<String, String> subscriber, UUID checkoutId) {
		for (int i = 0; i < 40; i++) {
			for (var event : subscriber.poll(Duration.ofMillis(200))) {
				if (checkoutId.toString().equals(event.key())) {
					return event.value();
				}
			}
		}
		throw new AssertionError("Kafka checkout.closed not observed");
	}

	static void await(BooleanSupplier condition) throws InterruptedException {
		for (int i = 0; i < 120; i++) {
			if (condition.getAsBoolean()) {
				return;
			}
			Thread.sleep(100);
		}
		assertThat(condition.getAsBoolean()).isTrue();
	}

	private static String failure(UUID eventId, UUID payment, UUID checkout, String code) {
		return envelope("payment.failed", eventId, payment,
				Map.of("paymentId", payment, "checkoutSessionId", checkout, "errorCode", code));
	}

	private static String success(UUID eventId, UUID payment, UUID checkout, long amount) {
		return success(eventId, payment, checkout, amount, Instant.now());
	}

	private static String success(UUID eventId, UUID payment, UUID checkout, long amount, Instant completedAt) {
		return envelope("payment.succeeded", eventId, payment,
				Map.of("paymentId", payment, "checkoutSessionId", checkout, "amountVnd", amount, "completedAt", completedAt.toString()));
	}

	private static String envelope(String type, UUID event, UUID payment, Map<String, Object> payload) {
		var actor = JSON.createObjectNode().put("kind", "SERVICE").putNull("userId").putNull("context");
		return JSON.writeValueAsString(Map.of("eventId", event, "eventType", type, "occurredAt", Instant.now().toString(),
				"producer", "payment-service", "aggregateId", payment, "version", 1, "actor", actor, "payload", payload));
	}

	private record Fixture(Sample sample, UUID id) {}
}
