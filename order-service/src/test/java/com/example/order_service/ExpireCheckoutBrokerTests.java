package com.example.order_service;

import com.example.order_service.service.CheckoutCommandRelay;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.net.URI;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {
		"order.checkout.delivery.enabled=true",
		"order.checkout.delivery.poll-delay-ms=3600000",
		"order.checkout.delivery.region=us-east-1"
})
class ExpireCheckoutBrokerTests extends PayHttpTestSupport {
	private static final String ENDPOINT = localEndpoint();
	private static final SqsClient BROKER = SqsClient.builder().endpointOverride(URI.create(ENDPOINT))
			.region(Region.US_EAST_1).credentialsProvider(StaticCredentialsProvider.create(
					AwsBasicCredentials.create("elasticmq-test", "elasticmq-test")))
			.overrideConfiguration(config -> config.apiCallTimeout(Duration.ofSeconds(15))).build();
	private static final String QUEUE = BROKER.createQueue(request ->
			request.queueName("order-p2-delivery-test-" + UUID.randomUUID())).queueUrl();

	@Autowired
	CheckoutCommandRelay relay;

	@DynamicPropertySource
	static void delivery(DynamicPropertyRegistry config) {
		config.add("order.checkout.delivery.endpoint", () -> ENDPOINT);
		config.add("order.checkout.delivery.queue-url", () -> QUEUE);
	}

	@AfterAll
	static void deleteTestQueue() {
		try (BROKER) {
			BROKER.deleteQueue(request -> request.queueUrl(QUEUE));
		}
	}

	@Test
	void relaysBothCommittedCommandsAndRecoversWithoutChangingExpiryOrCommandId() throws Exception {
		assertThat(BROKER.getQueueAttributes(request -> request.queueUrl(QUEUE).attributeNames(QueueAttributeName.ALL))
				.attributes().get(QueueAttributeName.FIFO_QUEUE)).isNotEqualTo("true");

		var sample = sample(8, 2);
		var paid = pay(sample.customer(), UUID.randomUUID(), quote(sample), sample.address());
		assertThat(paid.statusCode()).as(paid.body()).isEqualTo(202);
		var checkout = body(paid).path("data");
		UUID id = UUID.fromString(checkout.path("checkoutSessionId").asString());
		Instant deadline = Instant.parse(checkout.path("expiresAt").asString());
		UUID create = commandId(id, "CreateProviderSession");
		UUID expire = commandId(id, "ExpireCheckout");

		var paymentRequests = new AtomicInteger();
		var sawCommittedLease = new AtomicInteger();
		PAYMENT_WRITES.put(id, new PaymentReply(503, Map.of(), exchange -> {
			paymentRequests.incrementAndGet();
			if (independentLeaseMatches(id, create)) {
				sawCommittedLease.incrementAndGet();
			}
		}));
		makeFirst(create);
		assertThat(relay.deliverNext()).isTrue();
		assertThat(paymentRequests.get()).isOne();
		assertThat(sawCommittedLease.get()).isOne();
		assertThat(delivered(create)).isFalse();
		assertThat(jdbc.queryForObject("""
				select lease_token is null and next_attempt_at > statement_timestamp()
				from orders.checkout_outbox where command_id = ?
				""", Boolean.class, create)).isTrue();

		PAYMENT_WRITES.put(id, new PaymentReply(200, java.util.Collections.singletonMap("data", null), exchange -> {
			paymentRequests.incrementAndGet();
			if (independentLeaseMatches(id, create)) {
				sawCommittedLease.incrementAndGet();
			}
		}));
		makeFirst(create);
		assertThat(relay.deliverNext()).isTrue();
		assertThat(paymentRequests.get()).isEqualTo(2);
		assertThat(sawCommittedLease.get()).isEqualTo(2);
		assertThat(delivered(create)).isTrue();

		CLOCK.advance(Duration.between(CLOCK.instant(), deadline.minusMillis(6200)));
		makeFirst(expire);
		long sentAt = System.nanoTime();
		assertThat(relay.deliverNext()).isTrue();
		assertThat(delivered(expire)).isTrue();
		assertThat(receive(0)).isEmpty();
		var delayed = receive(12).getFirst();
		assertThat(Duration.ofNanos(System.nanoTime() - sentAt)).isGreaterThan(Duration.ofSeconds(6));
		var payload = JSON.readTree(delayed.body());
		assertThat(payload.path("commandId").asString()).isEqualTo(expire.toString());
		assertThat(payload.path("checkoutSessionId").asString()).isEqualTo(id.toString());
		assertThat(payload.path("expiresAt")).isEqualTo(checkout.path("expiresAt"));

		// Recreate the durable state left by a worker lost after SQS received the message.
		jdbc.update("""
				update orders.checkout_outbox set delivered_at = null, lease_token = ?,
					lease_until = statement_timestamp() - interval '1 second'
				where command_id = ?
				""", UUID.randomUUID(), expire);
		CLOCK.advance(Duration.ofSeconds(7));
		assertThat(relay.deliverNext()).isTrue();
		assertThat(delivered(expire)).isTrue();
		var recovered = receive(2).getFirst();
		assertThat(recovered.messageId()).isNotEqualTo(delayed.messageId());
		assertThat(recovered.body()).isEqualTo(delayed.body());
		assertThat(commandId(id, "CreateProviderSession")).isEqualTo(create);
		assertThat(commandId(id, "ExpireCheckout")).isEqualTo(expire);
	}

	private java.util.List<Message> receive(int waitSeconds) {
		return BROKER.receiveMessage(request -> request.queueUrl(QUEUE).waitTimeSeconds(waitSeconds)
				.maxNumberOfMessages(1).visibilityTimeout(30)).messages();
	}

	private UUID commandId(UUID checkout, String type) {
		return jdbc.queryForObject("select command_id from orders.checkout_outbox where checkout_session_id = ? and command_type = ?",
				UUID.class, checkout, type);
	}

	private void makeFirst(UUID command) {
		jdbc.update("update orders.checkout_outbox set next_attempt_at = '2000-01-01T00:00:00Z' where command_id = ?", command);
	}

	private boolean delivered(UUID command) {
		return Boolean.TRUE.equals(jdbc.queryForObject("select delivered_at is not null from orders.checkout_outbox where command_id = ?",
				Boolean.class, command));
	}

	private static boolean independentLeaseMatches(UUID checkout, UUID command) {
		try (var connection = DriverManager.getConnection(System.getenv("ORDER_TEST_DATABASE_URL"),
				System.getenv("ORDER_TEST_DATABASE_USER"), System.getenv("ORDER_TEST_DATABASE_PASSWORD"));
				var query = connection.prepareStatement("""
						select lease_token is not null and delivered_at is null from orders.checkout_outbox
						join orders.checkout_session using (checkout_session_id)
						where checkout_session_id = ? and command_id = ?
						""")) {
			query.setObject(1, checkout);
			query.setObject(2, command);
			try (var row = query.executeQuery()) {
				return row.next() && row.getBoolean(1);
			}
		} catch (SQLException failure) {
			throw new IllegalStateException("Unable to check committed checkout command", failure);
		}
	}

	private static String localEndpoint() {
		String endpoint = System.getenv("ORDER_TEST_SQS_ENDPOINT");
		if (endpoint == null || !("127.0.0.1".equals(URI.create(endpoint).getHost())
				|| "localhost".equals(URI.create(endpoint).getHost()))) {
			throw new IllegalStateException("Set ORDER_TEST_SQS_ENDPOINT to a dedicated local ElasticMQ endpoint");
		}
		return endpoint;
	}
}
