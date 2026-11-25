package com.example.order_service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static com.example.order_service.Phase2LocalBrokerIT.*;
import static com.example.order_service.Phase2LocalBrokerSupport.*;
import static com.example.order_service.Phase3LocalBrokerIT.*;
import static org.assertj.core.api.Assertions.assertThat;

class Phase4LocalBrokerIT {
    @Test
    @Timeout(300)
    void tracesCheckoutReviewWithdrawalAndReconciliationThroughTheRealServices() throws Exception {
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
            String admin = identity.authorization(identity.admin);
            var orders = stack.call(stack.origin("order").resolve("/api/v1/orders/me?checkoutSessionId=" + paid.checkout()),
                    "GET", null, Map.of("Authorization", customer), 200);
            assertThat(orders.path("data").size()).isEqualTo(2);
            var sellerOrders = stack.call(stack.origin("order").resolve("/api/v1/orders/seller/orders?status=PAID"),
                    "GET", null, Map.of("Authorization", seller), 200);
            assertThat(sellerOrders.path("data").size()).isOne();
            var payment = stack.call(stack.origin("payment").resolve("/api/v1/payments/by-checkout/" + paid.checkout()),
                    "GET", null, Map.of("Authorization", customer), 200);
            assertThat(payment.path("data").path("status").asString()).isEqualTo("SUCCESS");
            stack.execute("order", "update orders.checkout_session set needs_manual_review=true where checkout_session_id=?", paid.checkout());
            var flagged = stack.call(stack.origin("payment").resolve("/api/v1/payments/admin/issues?type=MANUAL_REVIEW&size=100"),
                    "GET", null, Map.of("Authorization", admin), 200);
            assertThat(flagged.path("data").toString()).contains(paid.checkout().toString(), "COLLECTION_REVIEW_REQUIRED");
            String mark = "/api/v1/payments/admin/checkout-sessions/" + paid.checkout() + "/mark-handled";
            stack.call(stack.origin("payment").resolve(mark), "POST", Map.of("note", "audited in E2E"),
                    Map.of("Authorization", identity.authorization(identity.readAdmin)), 403);
            stack.call(stack.origin("payment").resolve(mark), "POST", Map.of("note", "audited in E2E"),
                    Map.of("Authorization", admin), 200);
            assertThat(stack.db("order", "select count(*) from orders.admin_audit_log where target_id=?", paid.checkout())).isEqualTo("1");
            String sellerPath = "/api/v1/orders/seller/orders/" + paid.firstOrder();
            postOrder(stack, sellerPath + "/start-packing", seller, null);
            postOrder(stack, sellerPath + "/mark-delivered", seller, null);
            postOrder(stack, "/api/v1/orders/me/" + paid.firstOrder() + "/confirm-receipt", customer, null);
            postOrder(stack, "/api/v1/orders/me/" + paid.secondOrder() + "/cancel", customer, Map.of("reasonCode", "CHANGED_MIND"));
            awaitSettlement(stack, paid.firstOrder(), "SETTLED");
            awaitSettlement(stack, paid.secondOrder(), "REFUNDED");

            identity.bannedForWithdrawal.add(identity.seller);
            var withdrawalUrl = stack.origin("finance").resolve("/api/v1/finance/seller/withdrawals");
            var headers = Map.of("Authorization", seller, "Idempotency-Key", UUID.randomUUID().toString());
            var request = Map.of("amountVnd", 950);
            var attempts = new java.util.ArrayList<java.net.http.HttpResponse<String>>();
            try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
                var first = pool.submit(() -> stack.request(withdrawalUrl, "POST", request, headers));
                var second = pool.submit(() -> stack.request(withdrawalUrl, "POST", request, headers));
                attempts.add(first.get());
                attempts.add(second.get());
            }
            assertThat(attempts.stream().map(java.net.http.HttpResponse::statusCode).toList()).containsExactlyInAnyOrder(200, 201);
            var receipt = JSON.readTree(attempts.getFirst().body()).path("data");
            assertThat(JSON.readTree(attempts.getLast().body()).path("data")).isEqualTo(receipt);
            UUID id = UUID.fromString(receipt.path("withdrawalId").asString());
            var event = JSON.readTree(event(stack, stack.financeTopic, id, "withdrawal.completed"));
            assertThat(event.path("payload").path("availableAfterVnd").asLong()).isZero();
            assertThat(event.path("payload").path("amountVnd").asLong()).isEqualTo(950);
            assertThat(event.path("actor").path("kind").asString()).isEqualTo("USER");
            assertThat(event.path("actor").path("context").asString()).isEqualTo("SELLER");
            assertThat(stack.db("finance", "select count(*) from finance.withdrawal_outbox where withdrawal_id=?", id)).isEqualTo("1");

            var reconciliation = stack.origin("finance").resolve("/api/v1/finance/admin/reconciliation");
            var report = stack.call(reconciliation, "GET", null, Map.of("Authorization", admin), 200).path("data");
            assertThat(report.path("status").asString()).isIn("COMPLETE", "DIFFERENCE");
            for (String source : List.of("ORDER", "PAYMENT", "FINANCE")) {
                assertThat(report.path("sourceReadAt").path(source).asString()).isNotBlank();
            }
            stack.processes.get("payment").destroy();
            stack.processes.get("payment").waitFor(15, java.util.concurrent.TimeUnit.SECONDS);
            var incomplete = stack.call(reconciliation, "GET", null, Map.of("Authorization", admin), 200).path("data");
            assertThat(incomplete.path("status").asString()).isEqualTo("INCOMPLETE");
            assertThat(incomplete.path("missingSources").get(0).asString()).isEqualTo("PAYMENT");
            assertThat(incomplete.path("differenceVnd").isNull()).isTrue();
        }
    }
}
