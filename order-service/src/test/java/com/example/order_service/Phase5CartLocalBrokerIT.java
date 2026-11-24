package com.example.order_service;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.example.order_service.Phase2LocalBrokerIT.approveSeller;
import static com.example.order_service.Phase2LocalBrokerIT.capture;
import static com.example.order_service.Phase2LocalBrokerIT.deliverWebhook;
import static com.example.order_service.Phase2LocalBrokerIT.event;
import static com.example.order_service.Phase2LocalBrokerSupport.JSON;
import static com.example.order_service.Phase2LocalBrokerSupport.PRODUCT_TO_ORDER;
import static com.example.order_service.Phase2LocalBrokerSupport.waitFor;
import static org.assertj.core.api.Assertions.assertThat;

class Phase5CartLocalBrokerIT {
    @Test
    @Timeout(300)
    void cartPaidReachesKafka() throws Exception {
        try (var stack = new Phase2LocalBrokerSupport(); var sale = new Phase2LocalBrokerIT.SaleFixture()) {
            stack.infrastructure();
            try (var cart = new CartCheckoutBrokerFixture(stack)) {
                stack.startApplications(sale.origin(), cart.origin(), CartCheckoutBrokerFixture.TOKEN);
                UUID owner = UUID.randomUUID();
                UUID foreignOwner = UUID.randomUUID();
                UUID cartId = cart.cart(owner);
                approveSeller(stack, sale.seller);
                var first = provision(stack, sale, cart, owner, cartId, sale.seller, 2);
                var second = provision(stack, sale, cart, owner, cartId, sale.seller, 1);
                var foreign = cart.add(cart.cart(foreignOwner), first.productId(), 3);
                var invalid = stack.call(orderUrl(stack, "/checkout/preview"), "POST", Map.of("source", "CART",
                        "cartItemIds", List.of(first.cartItemId(), foreign.cartItemId())),
                        Map.of("Authorization", sale.authorization(owner)), 404);
                assertThat(invalid.path("error").path("code").asString()).isEqualTo("NOT_FOUND");

                var checkout = cartCheckout(stack, sale, owner, List.of(first.cartItemId(), second.cartItemId()), false);
                assertThat(stack.db("order", """
                        select count(*) from orders.reservation r join orders.order_item i using(order_item_id)
                        join orders.purchase_order o using(order_id) where o.checkout_session_id=? and r.status='HELD'
                        """, checkout.id())).isEqualTo("2");
                assertThat(stack.db("order", "select source_cart_id from orders.checkout_session where checkout_session_id=?",
                        checkout.id())).isEqualTo(cartId.toString());

                UUID customerPayment = awaitPaymentLink(stack, checkout);
                UUID webhook = capture(stack, customerPayment);
                deliverWebhook(stack, webhook);
                var kafka = record(stack, checkout.id(), "checkout.paid");
                var envelope = JSON.readTree(kafka.value());
                var payload = envelope.path("payload");
                assertThat(id(envelope, "aggregateId")).isEqualTo(checkout.id());
                assertThat(envelope.path("producer").asString()).isEqualTo("order-service");
                assertThat(envelope.path("version").asInt()).isEqualTo(1);
                assertThat(envelope.path("actor").path("kind").asString()).isEqualTo("SERVICE");
                assertThat(id(payload, "checkoutSessionId")).isEqualTo(checkout.id());
                assertThat(id(payload, "paymentId")).isEqualTo(customerPayment);
                assertThat(id(payload, "customerId")).isEqualTo(owner);
                assertThat(payload.path("source").asString()).isEqualTo("CART");
                assertThat(stack.db("order", """
                        select count(*) from orders.checkout_session where checkout_session_id=? and payment_id=?
                        and status='PAID' and paid_at=cast(? as timestamptz)
                        """, checkout.id(), customerPayment, payload.path("paidAt").asString())).isEqualTo("1");
                assertThat(Instant.parse(payload.path("paidAt").asString())).isNotNull();
                assertPayloadAndStock(stack, checkout.id(), payload, first, second);
                assertThat(stack.db("order", "select envelope from orders.checkout_event_outbox where event_id=? and checkout_session_id=?",
                        id(envelope, "eventId"), checkout.id())).isEqualTo(kafka.value());
                waitFor("sent checkout.paid outbox", 10, () -> "1".equals(stack.db("order", """
                        select count(*) from orders.checkout_event_outbox where event_id=? and sent_at is not null
                        """, id(envelope, "eventId"))));

                deliverWebhook(stack, webhook);
                String paymentWire = event(stack, stack.paymentTopic, customerPayment, "payment.succeeded");
                stack.awaitCommitted("order", stack.send(stack.paymentTopic, customerPayment.toString(), paymentWire));
                assertThat(id(pay(stack, checkout.request(), checkout.headers(), 200), "checkoutSessionId"))
                        .isEqualTo(checkout.id());
                assertThat(stack.db("order", """
                        select count(*) from orders.checkout_event_outbox where checkout_session_id=?
                        and envelope::jsonb->>'eventType'='checkout.paid'
                        """, checkout.id())).isEqualTo("1");
                assertPayloadAndStock(stack, checkout.id(), payload, first, second);
                System.out.printf("PHASE5_CART_PAID checkout=%s payment=%s event=%s topic=%s offset=%d%n",
                        checkout.id(), customerPayment, id(envelope, "eventId"), kafka.topic(), kafka.offset());
            }
        }
    }

    private static void assertPayloadAndStock(Phase2LocalBrokerSupport stack, UUID checkout, JsonNode payload,
            CartCheckoutBrokerFixture.Item... items) throws Exception {
        var quantities = new HashMap<UUID, Integer>();
        for (var row : payload.path("cartItems")) {
            assertThat(quantities.put(id(row, "cartItemId"), row.path("quantity").asInt())).isNull();
        }
        assertThat(quantities).hasSize(items.length);
        for (var item : items) {
            assertThat(quantities).containsEntry(item.cartItemId(), item.quantity());
            assertThat(stack.db("order", """
                    select concat_ws(':', i.quantity, r.status,
                        (select count(*) from orders.stock_movement m where m.order_item_id=i.order_item_id and m.movement_type='SOLD'),
                        stock.on_hand_quantity, stock.reserved_quantity)
                    from orders.order_item i join orders.reservation r using(order_item_id)
                    join orders.purchase_order o using(order_id) join orders.inventory stock on stock.product_id=i.product_id
                    where o.checkout_session_id=? and i.source_cart_item_id=?
                    """, checkout, item.cartItemId())).isEqualTo(item.quantity() + ":COMMITTED:1:" + (10 - item.quantity()) + ":0");
        }
        assertThat(payload.path("orders").size()).isEqualTo(1);
        for (var order : payload.path("orders")) {
            String persisted = stack.db("order", """
                    select jsonb_build_object('orderId',order_id,'sellerId',seller_id,'totalAmountVnd',total_amount_vnd,
                        'commissionBps',commission_bps_snapshot)
                    from orders.purchase_order where checkout_session_id=? and order_id=? and status='PAID'
                    """, checkout, id(order, "orderId"));
            assertThat(persisted).isNotNull();
            assertThat(JSON.readTree(persisted)).isEqualTo(order);
        }
    }

    private static CartCheckoutBrokerFixture.Item provision(Phase2LocalBrokerSupport stack,
            Phase2LocalBrokerIT.SaleFixture sale, CartCheckoutBrokerFixture cart, UUID customer,
            UUID cartId, UUID seller, int quantity) throws Exception {
        UUID product = UUID.randomUUID();
        sale.register(product, customer, seller);
        stack.call(stack.origin("order").resolve("/internal/v1/inventory"), "POST",
                Map.of("productId", product, "sellerId", seller, "onHandQuantity", 10),
                Map.of("X-Service-Token", PRODUCT_TO_ORDER), 201);
        return cart.add(cartId, product, quantity);
    }

    private static Checkout cartCheckout(Phase2LocalBrokerSupport stack, Phase2LocalBrokerIT.SaleFixture sale,
            UUID customer, List<UUID> cartItemIds, boolean shortExpiry) throws Exception {
        String bearer = sale.authorization(customer);
        var preview = stack.call(orderUrl(stack, "/checkout/preview"), "POST",
                Map.of("source", "CART", "cartItemIds", cartItemIds), Map.of("Authorization", bearer), 200).path("data");
        assertThat(preview.path("canPlaceOrder").asBoolean()).as(preview.toString()).isTrue();
        var request = Map.of("quoteToken", preview.path("quoteToken").asString(),
                "addressId", preview.path("defaultAddress").path("addressId").asString());
        var headers = Map.of("Authorization", bearer, "Idempotency-Key", UUID.randomUUID().toString());
        if (shortExpiry) stack.clockOffset(-870);
        try {
            var result = pay(stack, request, headers, 202);
            return new Checkout(id(result, "checkoutSessionId"), request, headers);
        } finally {
            if (shortExpiry) stack.clockOffset(0);
        }
    }

    private static JsonNode pay(Phase2LocalBrokerSupport stack, Map<String, String> body,
            Map<String, String> headers, int status) throws Exception {
        return stack.call(orderUrl(stack, "/checkout/pay"), "POST", body, headers, status).path("data");
    }

    private static UUID awaitPaymentLink(Phase2LocalBrokerSupport stack, Checkout checkout) throws Exception {
        waitFor("payment link for " + checkout.id(), 25, () -> checkoutStatus(stack, checkout).path("paymentUrl").isString());
        return UUID.fromString(stack.db("payment", "select payment_id from payment_service.payments where checkout_session_id=?",
                checkout.id()));
    }

    private static JsonNode checkoutStatus(Phase2LocalBrokerSupport stack, Checkout checkout) throws Exception {
        return stack.call(orderUrl(stack, "/checkout-sessions/" + checkout.id()), "GET", null,
                Map.of("Authorization", checkout.headers().get("Authorization")), 200).path("data");
    }

    private static ConsumerRecord<String, String> record(Phase2LocalBrokerSupport stack, UUID checkout, String type) throws Exception {
        try (var observer = stack.observer(stack.orderTopic)) {
            return waitFor(type, 18, () -> {
                for (var record : observer.poll(Duration.ofMillis(500))) {
                    if (checkout.toString().equals(record.key()) && type.equals(JSON.readTree(record.value()).path("eventType").asString())) {
                        return record;
                    }
                }
                return null;
            });
        }
    }

    private static java.net.URI orderUrl(Phase2LocalBrokerSupport stack, String path) {
        return stack.origin("order").resolve("/api/v1/orders" + path);
    }

    private static UUID id(JsonNode object, String field) {
        return UUID.fromString(object.path(field).asString());
    }

    private record Checkout(UUID id, Map<String, String> request, Map<String, String> headers) {}
}
