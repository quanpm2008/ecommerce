package com.example.order_service;

import com.example.order_service.service.OrderLifecycleJobs;
import com.example.order_service.service.OrderLifecycleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "order.lifecycle.sweep-delay-ms=3600000")
class OrderLifecyclePostgresTests extends PayHttpTestSupport {
	@Autowired
	OrderLifecycleService lifecycle;
	@Autowired
	OrderLifecycleJobs jobs;

	@Test
	void customerWithLimitedRightsCanCancelOwnedPaidOrderOnceAndReloadIt() throws Exception {
		var fixture = paidOrder();
		noReadPermission.set(fixture.sample().customer());
		assertRejected(call(fixture.sample().customer(), "GET", customer(fixture), null), 403, "FORBIDDEN");
		noReadPermission.set(null);
		limitedCustomer.set(fixture.sample().customer());
		String path = customer(fixture);
		assertRejected(call(UUID.randomUUID(), "GET", path, null), 404, "NOT_FOUND");
		assertThat(detail(call(fixture.sample().customer(), "GET", path, null)).path("allowedActions").get(0).asString())
				.isEqualTo("CANCEL");
		var invalid = call(fixture.sample().customer(), "POST", path + "/cancel",
				Map.of("reasonCode", "OTHER", "reasonNote", " "));
		assertRejected(invalid, 400, "VALIDATION_ERROR");
		assertRejected(call(fixture.sample().customer(), "POST", path + "/cancel",
				Map.of("reasonCode", "CHANGED_MIND", "reasonNote", "x".repeat(501))), 400, "VALIDATION_ERROR");
		var cancel = Map.of("reasonCode", "CHANGED_MIND", "reasonNote", "x".repeat(500));
		var cancelled = call(fixture.sample().customer(), "POST", path + "/cancel", cancel);
		assertThat(cancelled.statusCode()).isEqualTo(200);
		assertThat(detail(cancelled).path("refundPendingCreation").asBoolean()).isTrue();
		assertThat(detail(call(fixture.sample().customer(), "GET", path, null)).path("status").asString()).isEqualTo("CANCELLED");
		assertThat(call(fixture.sample().customer(), "POST", path + "/cancel", cancel).statusCode()).isEqualTo(200);
		assertThat(stock(fixture)).isEqualTo(5);
		assertThat(restocks(fixture)).isOne();
		assertThat(events(fixture, "order.cancelled")).isOne();
	}

	@Test
	void packingCompetesWithCancelAndEventContainsCurrentStatusAfterLosing() throws Exception {
		var fixture = paidOrder();
		try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
			var cancel = pool.submit(() -> call(fixture.sample().customer(), "POST", customer(fixture) + "/cancel",
					Map.of("reasonCode", "CHANGED_MIND")));
			var pack = pool.submit(() -> call(SELLER, "POST", seller(fixture) + "/start-packing", null));
			var first = cancel.get();
			var second = pack.get();
			assertThat(List.of(first.statusCode(), second.statusCode())).containsExactlyInAnyOrder(200, 409);
			if (first.statusCode() == 409) {
				assertRejected(first, 409, "ORDER_NOT_CANCELLABLE");
				assertThat(error(first).path("details").get(0).path("currentStatus").asString()).isEqualTo("PACKING");
				assertThat(restocks(fixture)).isZero();
				assertThat(events(fixture, "order.packing")).isOne();
			} else {
				assertRejected(second, 409, "INVALID_STATE_TRANSITION");
				assertThat(error(second).path("details").get(0).path("currentStatus").asString()).isEqualTo("CANCELLED");
				assertThat(restocks(fixture)).isOne();
			}
		}
	}

	@Test
	void deliveryAndCustomerConfirmationUseIndependentDeadlineAndJ3IsIdempotent() throws Exception {
		var fixture = paidOrder();
		assertThat(call(SELLER, "POST", seller(fixture) + "/start-packing", null).statusCode()).isEqualTo(200);
		var delivered = call(SELLER, "POST", seller(fixture) + "/mark-delivered", null);
		assertThat(delivered.statusCode()).isEqualTo(200);
		var data = detail(delivered);
		assertThat(java.time.Instant.parse(data.path("customerConfirmDueAt").asString()))
				.isEqualTo(java.time.Instant.parse(data.path("deliveredAt").asString()).plusSeconds(7 * 86400));
		jdbc.update("update orders.purchase_order set customer_confirm_due_at=statement_timestamp() where order_id=?", fixture.order());
		try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
			var confirm = pool.submit(() -> call(fixture.sample().customer(), "POST", customer(fixture) + "/confirm-receipt", null));
			var job = pool.submit(() -> lifecycle.autoConfirm(fixture.order()));
			assertThat(confirm.get().statusCode()).isEqualTo(200);
			job.get();
		}
		var reread = detail(call(fixture.sample().customer(), "GET", customer(fixture), null));
		assertThat(reread.path("status").asString()).isEqualTo("CONFIRMED");
		assertThat(reread.path("reviewSummary").get(0).path("canReview").asBoolean()).isTrue();
		assertThat(events(fixture, "order.confirmed")).isOne();
		assertThat(restocks(fixture)).isZero();
	}

	@Test
	void expiredSellerDeadlineRejectsDeliveryAndJ4RollsBackOnRestockFailure() throws Exception {
		var fixture = paidOrder();
		assertThat(call(SELLER, "POST", seller(fixture) + "/start-packing", null).statusCode()).isEqualTo(200);
		jdbc.update("update orders.purchase_order set seller_action_due_at=statement_timestamp() where order_id=?", fixture.order());
		assertRejected(call(SELLER, "POST", seller(fixture) + "/mark-delivered", null), 409, "SELLER_DEADLINE_PASSED");
		String constraint = "phase3_test_forced_restock_failure";
		jdbc.execute("alter table orders.stock_movement add constraint " + constraint + " check (product_id <> '"
				+ fixture.sample().product() + "' or movement_type <> 'RESTOCK') not valid");
		try {
			org.assertj.core.api.Assertions.assertThatThrownBy(() -> lifecycle.expireSellerAction(fixture.order()))
					.isInstanceOf(RuntimeException.class);
			assertThat(stock(fixture)).isEqualTo(3);
			assertThat(events(fixture, "order.cancelled")).isZero();
			assertThat(detail(call(SELLER, "GET", seller(fixture), null)).path("status").asString()).isEqualTo("PACKING");
		} finally {
			jdbc.execute("alter table orders.stock_movement drop constraint " + constraint);
		}
		lifecycle.expireSellerAction(fixture.order());
		lifecycle.expireSellerAction(fixture.order());
		assertThat(stock(fixture)).isEqualTo(5);
		assertThat(restocks(fixture)).isOne();
		assertThat(events(fixture, "order.cancelled")).isOne();
	}

	@Test
	void sellerRejectionAndDeliveryCannotBothWin() throws Exception {
		var fixture = paidOrder();
		assertThat(call(SELLER, "POST", seller(fixture) + "/start-packing", null).statusCode()).isEqualTo(200);
		String sellerPath = seller(fixture);
		var reason = Map.of("reasonCode", "OUT_OF_STOCK_ACTUAL", "reasonNote", "Không còn hàng có thể giao");
		try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
			var rejected = pool.submit(() -> call(SELLER, "POST", sellerPath + "/reject", reason));
			var delivered = pool.submit(() -> call(SELLER, "POST", sellerPath + "/mark-delivered", null));
			assertThat(List.of(rejected.get().statusCode(), delivered.get().statusCode())).containsExactlyInAnyOrder(200, 409);
		}
		lifecycle.expireSellerAction(fixture.order());
		int cancellations = events(fixture, "order.cancelled");
		assertThat(cancellations + events(fixture, "order.delivered")).isOne();
		assertThat(restocks(fixture)).isEqualTo(cancellations);
	}

	@Test
	void sweepProcessesBothDeadlinesThroughRepositoryQueries() throws Exception {
		var pendingSeller = paidOrder();
		var pendingCustomer = paidOrder();
		assertThat(call(SELLER, "POST", seller(pendingCustomer) + "/start-packing", null).statusCode()).isEqualTo(200);
		assertThat(call(SELLER, "POST", seller(pendingCustomer) + "/mark-delivered", null).statusCode()).isEqualTo(200);
		jdbc.update("update orders.purchase_order set seller_action_due_at=statement_timestamp() where order_id=?",
				pendingSeller.order());
		jdbc.update("update orders.purchase_order set customer_confirm_due_at=statement_timestamp() where order_id=?",
				pendingCustomer.order());
		jobs.runDueActions();
		jobs.runDueActions();
		assertThat(events(pendingSeller, "order.cancelled")).isOne();
		assertThat(events(pendingCustomer, "order.confirmed")).isOne();
		assertThat(restocks(pendingSeller)).isOne();
		assertThat(restocks(pendingCustomer)).isZero();
	}

	private Fixture paidOrder() throws Exception {
		var sample = sample(5, 2);
		var response = pay(sample.customer(), UUID.randomUUID(), quote(sample), sample.address());
		assertThat(response.statusCode()).isEqualTo(202);
		var data = detail(response);
		var order = UUID.fromString(data.path("orders").get(0).path("orderId").asString());
		jdbc.update("update orders.purchase_order set status='PAID', paid_at=statement_timestamp(), "
				+ "seller_action_due_at=statement_timestamp()+interval '7 days' where order_id=?", order);
		jdbc.update("update orders.inventory set on_hand_quantity=3, reserved_quantity=0 where product_id=?", sample.product());
		return new Fixture(sample, order);
	}

	private HttpResponse<String> call(UUID user, String method, String path, Map<String, String> body) throws Exception {
		var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
				.header("Authorization", "Bearer " + token(user)).header("Content-Type", "application/json")
				.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
						: HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build();
		try (var client = HttpClient.newHttpClient()) {
			return client.send(request, HttpResponse.BodyHandlers.ofString());
		}
	}

	private JsonNode error(HttpResponse<String> response) {
		return JSON.readTree(response.body()).path("error");
	}

	private static JsonNode detail(HttpResponse<String> response) {
		assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);
		return body(response).path("data");
	}

	private int stock(Fixture fixture) {
		return jdbc.queryForObject("select on_hand_quantity from orders.inventory where product_id=?",
				Integer.class, fixture.sample().product());
	}

	private int restocks(Fixture fixture) {
		return jdbc.queryForObject("select count(*) from orders.stock_movement where product_id=? and movement_type='RESTOCK'",
				Integer.class, fixture.sample().product());
	}

	private int events(Fixture fixture, String type) {
		return jdbc.queryForObject("""
				select count(*) from orders.checkout_event_outbox where order_id=? and envelope::jsonb->>'eventType'=?
				""", Integer.class, fixture.order(), type);
	}

	private static String customer(Fixture fixture) { return "/api/v1/orders/me/" + fixture.order(); }
	private static String seller(Fixture fixture) { return "/api/v1/orders/seller/orders/" + fixture.order(); }

	private record Fixture(Sample sample, UUID order) {}
}
