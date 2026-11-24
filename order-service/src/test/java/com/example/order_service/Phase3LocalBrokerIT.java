package com.example.order_service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static com.example.order_service.Phase2LocalBrokerIT.*;
import static com.example.order_service.Phase2LocalBrokerSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

class Phase3LocalBrokerIT {
	@Test
	@Timeout(300)
	void verifiesFulfillmentPerOrderAndRestocksOnlyHistoricalIneligibility() throws Exception {
		try (var stack = new Phase2LocalBrokerSupport(); var identity = new SaleFixture()) {
			stack.infrastructure();
			stack.startApplications(identity.origin());
			UUID secondSeller = UUID.randomUUID();
			approveSeller(stack, identity.seller);
			approveSeller(stack, secondSeller);

			var paid = prepare(stack, identity, secondSeller);
			deliverWebhook(stack, capture(stack, paid.payment()));
			awaitSettlement(stack, paid.firstOrder(), "PENDING");
			awaitSettlement(stack, paid.secondOrder(), "PENDING");
			String seller = identity.authorization(identity.seller);
			String customer = identity.authorization(paid.customer());
			var sellerPath = "/api/v1/orders/seller/orders/" + paid.firstOrder();
			postOrder(stack, sellerPath + "/start-packing", seller, null);
			postOrder(stack, sellerPath + "/mark-delivered", seller, null);
			postOrder(stack, "/api/v1/orders/me/" + paid.firstOrder() + "/confirm-receipt", customer, null);
			rejectNextRefund(stack);
			postOrder(stack, "/api/v1/orders/me/" + paid.secondOrder() + "/cancel", customer,
					Map.of("reasonCode", "CHANGED_MIND"));

			awaitSettlement(stack, paid.firstOrder(), "SETTLED");
			awaitSettlement(stack, paid.secondOrder(), "PENDING_REFUND");
			exhaustAndRetry(stack, paid, customer);
			awaitSettlement(stack, paid.secondOrder(), "REFUNDED");
			assertThat(stack.db("finance", """
					select pending_balance_vnd || ':' || available_balance_vnd
					from finance.wallet where owner_id=?
					""", identity.seller)).isEqualTo("0:950");
			var released = JSON.readTree(event(stack, stack.financeTopic, paid.firstOrder(), "funds.available"));
			assertThat(released.path("payload").path("sellerAmountVnd").asInt()).isEqualTo(950);

			String cancellation = event(stack, stack.orderTopic, paid.secondOrder(), "order.cancelled");
			var replay = stack.send(stack.orderTopic, paid.secondOrder().toString(), cancellation);
			stack.awaitCommitted("payment", replay);
			stack.awaitCommitted("finance", replay);
			assertRefundAndStock(stack, paid);
			assertThat(stack.db("finance", """
					select count(*) from finance.wallet_transaction w join finance.settlement s using(settlement_id)
					where s.order_id=? and transaction_type='SETTLEMENT_RELEASED'
					""", paid.firstOrder())).isEqualTo("2");
			var response = stack.call(stack.origin("payment").resolve("/api/v1/payments/refunds?checkoutSessionId=" + paid.checkout()),
					"GET", null, Map.of("Authorization", customer), 200).path("data");
			assertThat(response.size()).isOne();
			assertThat(response.get(0).path("status").asString()).isEqualTo("SUCCESS");

			var mixed = prepare(stack, identity, secondSeller);
			identity.invalidProducts.add(mixed.secondProduct());
			deliverWebhook(stack, capture(stack, mixed.payment()));
			awaitSettlement(stack, mixed.firstOrder(), "PENDING");
			awaitSettlement(stack, mixed.secondOrder(), "REFUNDED");
			assertThat(stack.db("order", "select status from orders.purchase_order where order_id=?", mixed.firstOrder())).isEqualTo("PAID");
			assertThat(stack.db("order", "select status from orders.purchase_order where order_id=?", mixed.secondOrder())).isEqualTo("CANCELLED");
			assertThat(stack.db("payment", "select status from payment_service.payments where payment_id=?", mixed.payment()))
					.isEqualTo("SUCCESS");
			assertThat(stack.db("payment", "select reason from payment_service.refunds where order_id=?", mixed.secondOrder()))
					.isEqualTo("SELLER_CANCEL");
			assertRefundAndStock(stack, mixed);
		}
	}

	private static void rejectNextRefund(Phase2LocalBrokerSupport stack) throws Exception {
		stack.call(stack.provider.resolve("/__fixture/refund-fault"), "POST", Map.of("fault", "REJECT"), providerHeaders(), 204);
	}

	private static void exhaustAndRetry(Phase2LocalBrokerSupport stack, CartFixture paid, String customer) throws Exception {
		for (int retry = 0; retry <= 3; retry++) {
			String expected = "FAILED:" + retry;
			waitFor("confirmed provider failure " + retry, 12, () -> expected.equals(stack.db("payment", """
					select status || ':' || retry_count from payment_service.refunds where order_id=?
					""", paid.secondOrder())));
			if (retry < 3) {
				rejectNextRefund(stack);
				// Make the work due without waiting real minutes. The configured backoff is asserted in the Payment suite.
				stack.execute("payment", """
						update payment_service.payment_refund_work w set next_run_at=statement_timestamp()
						from payment_service.refunds r where w.refund_id=r.refund_id and r.order_id=?
						""", paid.secondOrder());
			}
		}
		var list = stack.call(stack.origin("payment").resolve("/api/v1/payments/refunds?checkoutSessionId=" + paid.checkout()),
				"GET", null, Map.of("Authorization", customer), 200).path("data");
		assertThat(list.get(0).path("canRetryRefund").asBoolean()).isTrue();
		String refund = list.get(0).path("refundId").asString();
		var route = stack.origin("payment").resolve("/api/v1/payments/refunds/" + refund + "/retry");
		var headers = Map.of("Authorization", customer, "Idempotency-Key", UUID.randomUUID().toString());
		var body = Map.of("expectedRetryCycle", 0);
		var accepted = stack.call(route, "POST", body, headers, 202).path("data");
		assertThat(accepted.path("retryCycle").asInt()).isOne();
		assertThat(stack.call(route, "POST", body, headers, 202).path("data")).isEqualTo(accepted);
	}

	private static void assertRefundAndStock(Phase2LocalBrokerSupport stack, CartFixture fixture) throws Exception {
		assertThat(stack.db("payment", """
				select count(*) from payment_service.refunds where payment_id=? and order_id=? and scope='ORDER'
				    and status='SUCCESS' and amount_vnd=1000
				""", fixture.payment(), fixture.secondOrder())).isEqualTo("1");
		assertThat(stack.db("payment", "select count(*) from payment_service.refunds where payment_id=?", fixture.payment()))
				.isEqualTo("1");
		assertThat(stack.db("order", """
				select count(*) from orders.stock_movement s join orders.order_item i using (order_item_id)
				where i.order_id=? and s.movement_type='RESTOCK'
				""", fixture.secondOrder())).isEqualTo("1");
		assertThat(stack.db("finance", """
				select count(*) from finance.wallet_transaction w join finance.settlement s using(settlement_id)
				where s.order_id=? and transaction_type='REFUND_DEBIT'
				""", fixture.secondOrder())).isEqualTo("2");
	}

	static void postOrder(Phase2LocalBrokerSupport stack, String path, String bearer, Object body) throws Exception {
		stack.call(stack.origin("order").resolve(path), "POST", body, Map.of("Authorization", bearer), 200);
	}

	static void awaitSettlement(Phase2LocalBrokerSupport stack, UUID order, String status) throws Exception {
		waitFor("Settlement " + order + " " + status, 40, () -> status.equals(stack.db("finance",
				"select status from finance.settlement where order_id=?", order)));
	}

	// Seed only the CART input snapshot. Payment collection and every fulfillment or money transition run through the services.
	static CartFixture prepare(Phase2LocalBrokerSupport stack, SaleFixture fixture, UUID secondSeller) throws Exception {
		UUID customer = UUID.randomUUID(), checkout = UUID.randomUUID(), firstOrder = UUID.randomUUID(), secondOrder = UUID.randomUUID();
		UUID firstProduct = UUID.randomUUID(), secondProduct = UUID.randomUUID();
		fixture.register(firstProduct, customer, fixture.seller);
		fixture.register(secondProduct, customer, secondSeller);
		stack.execute("order", """
				insert into orders.checkout_session(checkout_session_id,checkout_intent_id,customer_id,checkout_source,
				    source_cart_id,cart_snapshot,idempotency_key,request_hash,total_amount_vnd,currency,status,address_id,created_at,expires_at)
				values (?,gen_random_uuid(),?,'CART',gen_random_uuid(),'{}'::jsonb,gen_random_uuid()::text,'fixture',2000,'VND',
				    'AWAITING_PAYMENT',gen_random_uuid(),statement_timestamp(),statement_timestamp()+interval '15 minutes')
				""", checkout, customer);
		addOrder(stack, checkout, customer, fixture.seller, firstOrder, firstProduct);
		addOrder(stack, checkout, customer, secondSeller, secondOrder, secondProduct);
		var origin = stack.origin("payment").resolve("/internal/v1/payments/checkouts/" + checkout + "/provider-session");
		var result = stack.call(origin, "POST", null, Map.of("X-Service-Token", ORDER_TO_PAYMENT), 200).path("data");
		assertThat(result.path("paymentUrl").asString()).isNotBlank();
		UUID payment = UUID.fromString(result.path("paymentId").asString());
		return new CartFixture(checkout, payment, customer, firstOrder, secondOrder, secondProduct);
	}

	private static void addOrder(Phase2LocalBrokerSupport stack, UUID checkout, UUID customer, UUID seller, UUID order,
			UUID product) throws Exception {
		UUID item = UUID.randomUUID();
		stack.execute("order", "insert into orders.inventory(product_id,seller_id,on_hand_quantity,reserved_quantity) values (?,?,4,1)",
				product, seller);
		stack.execute("order", """
				insert into orders.purchase_order(order_id,checkout_session_id,seller_id,seller_name_snapshot,customer_id,total_amount_vnd,
				    currency,commission_bps_snapshot,recipient_name,recipient_phone,province_code,province_name,ward_code,ward_name,
				    address_detail,status,created_at,updated_at)
				values (?,?,?,'Test seller',?,1000,'VND',500,'Test recipient','0900000000','01','Test province','001','Test ward',
				    'Test address','AWAITING_PAYMENT',statement_timestamp(),statement_timestamp())
				""", order, checkout, seller, customer);
		stack.execute("order", """
				insert into orders.order_item(order_item_id,order_id,product_id,product_name_snapshot,cover_media_snapshot,
				    unit_price_vnd_snapshot,quantity,source_cart_item_id)
				values (?,?,?,'Test product','cover',1000,1,gen_random_uuid())
				""", item, order, product);
		stack.execute("order", """
				insert into orders.reservation(reservation_id,order_item_id,product_id,quantity,status,expires_at,created_at)
				select gen_random_uuid(),?,?,1,'HELD',expires_at,created_at from orders.checkout_session where checkout_session_id=?
				""", item, product, checkout);
	}

	record CartFixture(UUID checkout, UUID payment, UUID customer, UUID firstOrder, UUID secondOrder, UUID secondProduct) {}
}
