package com.example.order_service;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

final class Phase2LocalBrokerSupport implements AutoCloseable {
	static final JsonMapper JSON = JsonMapper.builder().build();
	private static final String KAFKA = "127.0.0.1:55990";
	private static final URI SQS = URI.create("http://127.0.0.1:55991");
	private static final String DB = "jdbc:postgresql://127.0.0.1:55989/";
	private static final String DB_USER = System.getenv().getOrDefault("PHASE2_4A_DB_USER", "ecommerce");
	private static final String DB_PASSWORD = System.getenv().getOrDefault("PHASE2_4A_DB_PASSWORD", "ecommerce");
	static final String ORDER_TO_PAYMENT = "phase2-4a-order-payment";
	static final String PAYMENT_TO_ORDER = "phase2-4a-payment-order";
	static final String ORDER_TO_FINANCE = "phase2-4a-order-finance";
	static final String FINANCE_TO_ORDER = "phase4-finance-order";
	static final String FINANCE_TO_PAYMENT = "phase4-finance-payment";
	static final String ORDER_TO_IDENTITY = "phase2-4a-order-identity";
	static final String ORDER_TO_PRODUCT = "phase2-4a-order-product";
	static final String PRODUCT_TO_ORDER = "phase2-4a-product-order";
	static final Set<String> SERVICES = Set.of("order", "payment", "finance");

	final String name = "phase2-4a-" + UUID.randomUUID();
	final String orderTopic = name + "-order";
	final String paymentTopic = name + "-payment";
	final String identityTopic = name + "-identity";
	final String financeTopic = name + "-finance";
	final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
	final URI provider;
	final Admin kafka = Admin.create(Map.of("bootstrap.servers", KAFKA, "request.timeout.ms", "4000"));
	final KafkaProducer<String, String> producer = new KafkaProducer<>(Map.of("bootstrap.servers", KAFKA,
			"key.serializer", "org.apache.kafka.common.serialization.StringSerializer",
			"value.serializer", "org.apache.kafka.common.serialization.StringSerializer", "max.block.ms", "5000"));
	final SqsClient sqs = SqsClient.builder().endpointOverride(SQS).region(Region.US_EAST_1)
			.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("phase2-4a", "fixture")))
			.overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(5))).build();
	final Map<String, Process> processes = new HashMap<>();
	final Map<String, Integer> ports = new HashMap<>();
	String queue;

	Phase2LocalBrokerSupport() {
		String origin = System.getenv("PHASE2_4A_PROVIDER_URL");
		if (origin == null) {
			throw new IllegalStateException("Start ProviderMockFixtureHostTests and set PHASE2_4A_PROVIDER_URL");
		}
		provider = URI.create(origin);
		if (!"http".equals(provider.getScheme()) || !"127.0.0.1".equals(provider.getHost())
				|| provider.getPort() <= 0 || provider.getUserInfo() != null || !provider.getPath().isEmpty()) {
			throw new IllegalArgumentException("Provider fixture origin must be HTTP IPv4 loopback");
		}
	}

	void infrastructure() throws Exception {
		assertThat(Runtime.version().feature()).isEqualTo(21);
		assertThat(kafka.describeCluster().nodes().get(8, TimeUnit.SECONDS)).isNotEmpty();
		kafka.createTopics(List.of(new NewTopic(orderTopic, 1, (short) 1),
				new NewTopic(paymentTopic, 1, (short) 1), new NewTopic(identityTopic, 1, (short) 1),
				new NewTopic(financeTopic, 1, (short) 1)))
				.all().get(10, TimeUnit.SECONDS);
		queue = sqs.createQueue(request -> request.queueName(name)).queueUrl();
		assertThat(queue).endsWith("/" + name);
		for (String service : SERVICES) {
			assertThat(db(service, "show server_version_num")).startsWith("16");
			assertThat(db(service, "select current_database()")).isEqualTo(service + "_test_4a");
		}
		assertThat(call(provider.resolve("/__fixture/ready"), "GET", null, providerHeaders(), 200)
				.path("ready").asBoolean()).isTrue();
	}

	void prepareOrderTimeFixture() throws Exception {
		execute("order", "create schema if not exists phase2_test_clock");
		execute("order", """
				create table if not exists phase2_test_clock.offset_seconds
				(singleton boolean primary key check(singleton), seconds integer not null)
				""");
		execute("order", "insert into phase2_test_clock.offset_seconds values (true,0) on conflict (singleton) do update set seconds=0");
		execute("order", """
				create or replace function phase2_test_clock.statement_timestamp() returns timestamptz
				language sql stable as 'select pg_catalog.statement_timestamp() + make_interval(secs => seconds)
				from phase2_test_clock.offset_seconds where singleton'
				""");
	}

	void clockOffset(int seconds) throws Exception {
		execute("order", "update phase2_test_clock.offset_seconds set seconds = ? where singleton", seconds);
	}

	void startApplications(URI fixture) throws Exception {
		prepareOrderTimeFixture();
		for (String service : List.of("order", "payment", "finance")) {
			ports.put(service, freePort());
		}
		start("finance", fixture);
		start("payment", fixture);
		start("order", fixture);
		for (String service : SERVICES) {
			ready(service);
			String schema = switch (service) {
				case "order" -> "orders";
				case "payment" -> "payment_service";
				default -> "finance";
			};
			assertThat(db(service, "select count(*) from " + schema + ".flyway_schema_history where success"))
					.isNotIn("0", null);
		}
	}

	void restartOrder(URI fixture) throws Exception {
		stop("order");
		start("order", fixture);
		ready("order");
	}

	URI origin(String service) {
		return URI.create("http://127.0.0.1:" + ports.get(service));
	}

	RecordMetadata send(String topic, String key, Object value) throws Exception {
		String wire = value instanceof String raw ? raw : JSON.writeValueAsString(value);
		return producer.send(new ProducerRecord<>(topic, key, wire)).get(10, TimeUnit.SECONDS);
	}

	void awaitCommitted(String consumer, RecordMetadata record) throws Exception {
		var partition = new TopicPartition(record.topic(), record.partition());
		waitFor(consumer + " Kafka replay acknowledgement", 15, () -> {
			var position = kafka.listConsumerGroupOffsets(name + "-" + consumer)
					.partitionsToOffsetAndMetadata().get(4, TimeUnit.SECONDS).get(partition);
			return position != null && position.offset() > record.offset();
		});
	}

	KafkaConsumer<String, String> observer(String topic) {
		var observer = new KafkaConsumer<String, String>(Map.of("bootstrap.servers", KAFKA,
				"group.id", name + "-observer-" + UUID.randomUUID(), "auto.offset.reset", "earliest",
				"key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer",
				"value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer"));
		observer.subscribe(List.of(topic));
		return observer;
	}

	JsonNode call(URI uri, String method, Object body, Map<String, String> headers, int expected) throws Exception {
		var response = request(uri, method, body, headers);
		assertThat(response.statusCode()).as("%s %s returned %s", method, uri.getPath(), response.body()).isEqualTo(expected);
		return response.body().isBlank() ? JSON.createObjectNode() : JSON.readTree(response.body());
	}

	HttpResponse<String> request(URI uri, String method, Object body, Map<String, String> headers) throws Exception {
		var builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(9));
		headers.forEach(builder::header);
		if (body == null) {
			builder.method(method, HttpRequest.BodyPublishers.noBody());
		} else {
			builder.header("Content-Type", "application/json")
					.method(method, HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
		}
		return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
	}

	static Map<String, String> providerHeaders() {
		return Map.of("X-Provider-Fixture-Control", "fixture-runner-local");
	}

	String db(String service, String sql, Object... args) throws Exception {
		try (var connection = DriverManager.getConnection(DB + service + "_test_4a", DB_USER, DB_PASSWORD);
			 var statement = connection.prepareStatement(sql)) {
			for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
			try (var result = statement.executeQuery()) {
				return result.next() ? result.getString(1) : null;
			}
		}
	}

	void execute(String service, String sql, Object... args) throws Exception {
		try (var connection = DriverManager.getConnection(DB + service + "_test_4a", DB_USER, DB_PASSWORD);
			 var statement = connection.prepareStatement(sql)) {
			for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
			statement.executeUpdate();
		}
	}

	static <T> T waitFor(String description, int seconds, Callable<T> probe) throws Exception {
		long deadline = System.nanoTime() + Duration.ofSeconds(seconds).toNanos();
		while (System.nanoTime() < deadline) {
			T value = probe.call();
			if (value != null && !Boolean.FALSE.equals(value)) return value;
			Thread.sleep(180);
		}
		throw new AssertionError("Timed out waiting for " + description);
	}

	private static int freePort() throws Exception {
		try (var socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
			return socket.getLocalPort();
		}
	}

	private void ready(String service) throws Exception {
		waitFor(service + " HTTP health", 90, () -> {
			Process process = processes.get(service);
			if (!process.isAlive()) {
				throw new AssertionError(service + " exited " + process.exitValue() + ", see target/" + name + "-" + service + ".log");
			}
			try {
				return request(origin(service).resolve("/actuator/health"), "GET", null, Map.of()).statusCode() == 200;
			} catch (java.io.IOException unavailable) {
				return false;
			}
		});
	}

	private void start(String service, URI fixture) throws Exception {
		Path directory = Path.of("").toAbsolutePath();
		Path root = Files.isDirectory(directory.resolve("order-service")) ? directory : directory.getParent();
		Path jar;
		try (var files = Files.list(root.resolve(service + "-service/target"))) {
			jar = files.filter(path -> path.getFileName().toString().matches(service + "-service-.*[.]jar"))
					.findFirst().orElseThrow(() -> new IllegalStateException("Package " + service + "-service first"));
		}
		Path java = Path.of(System.getProperty("java.home"), "bin",
				System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
		var command = new ArrayList<>(List.of(java.toString(), "-Duser.timezone=UTC", "-jar", jar.toString(),
				"--server.address=127.0.0.1", "--server.port=" + ports.get(service), "--eureka.client.enabled=false",
				"--logging.level.root=WARN", "--spring.datasource.url=" + DB + service + "_test_4a"
				+ ("order".equals(service) ? "?currentSchema=phase2_test_clock,pg_catalog,orders" : ""),
				"--spring.datasource.username=" + DB_USER, "--spring.datasource.password=" + DB_PASSWORD));
		if ("order".equals(service)) {
			command.addAll(List.of("--order.identity.base-url=" + fixture,
					"--order.product.base-url=" + fixture, "--order.payment.base-url=" + origin("payment"),
					"--order.finance.base-url=" + origin("finance"), "--order.identity.service-token=" + ORDER_TO_IDENTITY,
					"--order.product.service-token=" + ORDER_TO_PRODUCT, "--order.payment.service-token=" + ORDER_TO_PAYMENT,
					"--order.finance.service-token=" + ORDER_TO_FINANCE, "--order.internal.tokens.product=" + PRODUCT_TO_ORDER,
					"--order.internal.tokens.payment=" + PAYMENT_TO_ORDER,
					"--order.internal.tokens.finance=" + FINANCE_TO_ORDER,
					"--order.checkout.quote-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
					"--order.checkout.delivery.enabled=true", "--order.checkout.delivery.region=us-east-1",
					"--order.checkout.delivery.endpoint=" + SQS, "--order.checkout.delivery.queue-url=" + queue,
					"--order.kafka.bootstrap-servers=" + KAFKA, "--order.kafka.checkout-topic=" + orderTopic,
					"--order.kafka.payment-topic=" + paymentTopic, "--order.kafka.payment-group=" + name + "-order"));
		} else if ("payment".equals(service)) {
			command.addAll(List.of("--payment.internal.order-token=" + ORDER_TO_PAYMENT,
					"--payment.internal.finance-token=" + FINANCE_TO_PAYMENT,
					"--payment.identity.base-url=" + fixture, "--payment.identity.service-token=" + ORDER_TO_IDENTITY,
					"--payment.order.credential=" + PAYMENT_TO_ORDER, "--payment.order.base-url=" + origin("order"),
					"--payment.provider.base-url=" + provider,
					"--payment.provider.credential-header=X-Provider-Test-Key",
					"--payment.provider.credential=provider-fixture-local",
					"--payment.provider.webhook-secret=provider-fixture-webhook",
					"--payment.kafka.bootstrap-servers=" + KAFKA, "--payment.kafka.result-topic=" + paymentTopic,
					"--payment.kafka.checkout-events-topic=" + orderTopic, "--payment.kafka.checkout-group=" + name + "-payment",
					"--payment.collection.initial-delay=600000", "--payment.refund.query-interval=2s"));
		} else {
			command.addAll(List.of("--finance.internal.tokens.order=" + ORDER_TO_FINANCE,
					"--finance.identity.base-url=" + fixture, "--finance.identity.service-token=" + ORDER_TO_IDENTITY,
					"--finance.order.base-url=" + origin("order"), "--finance.order.credential=" + FINANCE_TO_ORDER,
					"--finance.payment.base-url=" + origin("payment"), "--finance.payment.credential=" + FINANCE_TO_PAYMENT,
					"--finance.kafka.bootstrap-servers=" + KAFKA, "--finance.kafka.order-topic=" + orderTopic,
					"--finance.kafka.payment-topic=" + paymentTopic, "--finance.kafka.result-topic=" + financeTopic,
					"--finance.kafka.identity-topic=" + identityTopic, "--finance.kafka.group=" + name + "-finance"));
		}
		var builder = new ProcessBuilder(command).redirectErrorStream(true).directory(root.toFile());
		builder.environment().put("AWS_ACCESS_KEY_ID", "phase2-4a");
		builder.environment().put("AWS_SECRET_ACCESS_KEY", "fixture");
		builder.environment().put("AWS_EC2_METADATA_DISABLED", "true");
		builder.redirectOutput(ProcessBuilder.Redirect.appendTo(directory.resolve("target/" + name + "-" + service + ".log").toFile()));
		processes.put(service, builder.start());
	}

	private void stop(String service) throws InterruptedException {
		var process = processes.remove(service);
		if (process != null && process.isAlive()) {
			process.destroy();
			if (!process.waitFor(8, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				process.waitFor(5, TimeUnit.SECONDS);
			}
		}
	}

	@Override
	public void close() throws Exception {
		try {
			for (String service : SERVICES) stop(service);
			if (queue != null) sqs.deleteQueue(request -> request.queueUrl(queue));
		} finally {
			try {
				kafka.deleteTopics(List.of(orderTopic, paymentTopic, identityTopic)).all().get(8, TimeUnit.SECONDS);
			} finally {
				producer.close(Duration.ofSeconds(2));
				kafka.close(Duration.ofSeconds(2));
				sqs.close();
				http.close();
			}
		}
	}
}
