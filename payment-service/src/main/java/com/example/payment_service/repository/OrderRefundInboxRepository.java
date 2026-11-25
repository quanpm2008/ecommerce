package com.example.payment_service.repository;

import com.example.payment_service.entity.RefundReason;
import com.example.payment_service.messaging.OrderRefundEvent.Cancelled;
import com.example.payment_service.messaging.OrderRefundEvent.Paid;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class OrderRefundInboxRepository {
    private final EntityManager entities;

    public OrderRefundInboxRepository(EntityManager entities) {
        this.entities = entities;
    }

    public boolean acceptPaidSnapshot(Paid event, String orders) {
        int inserted = entities.createNativeQuery("""
                INSERT INTO payment_paid_checkout(checkout_session_id, event_id, payment_id, customer_id, orders)
                VALUES (:checkout, :event, :payment, :customer, CAST(:orders AS jsonb)) ON CONFLICT DO NOTHING
                """).setParameter("checkout", event.checkoutId()).setParameter("event", event.eventId())
                .setParameter("payment", event.paymentId()).setParameter("customer", event.customerId())
                .setParameter("orders", orders).executeUpdate();
        return inserted == 1 || entities.createNativeQuery("""
                SELECT 1 FROM payment_paid_checkout WHERE checkout_session_id = :checkout
                    AND payment_id = :payment AND customer_id = :customer AND orders = CAST(:orders AS jsonb)
                """).setParameter("checkout", event.checkoutId()).setParameter("payment", event.paymentId())
                .setParameter("customer", event.customerId()).setParameter("orders", orders).getSingleResultOrNull() != null;
    }

    public boolean hasPaidSnapshot(UUID checkout) {
        return entities.createNativeQuery("SELECT 1 FROM payment_paid_checkout WHERE checkout_session_id = :checkout")
                .setParameter("checkout", checkout).getSingleResultOrNull() != null;
    }

    public boolean matchesPaidOrder(Cancelled event, UUID payment) {
        return entities.createNativeQuery("""
                SELECT 1 FROM payment_paid_checkout WHERE checkout_session_id = :checkout
                    AND payment_id = :payment AND customer_id = :customer
                    AND orders #>> ARRAY[CAST(:orderId AS text), 'sellerId'] = :seller
                    AND orders #>> ARRAY[CAST(:orderId AS text), 'totalAmountVnd'] = :amount
                """).setParameter("checkout", event.checkoutId()).setParameter("payment", payment)
                .setParameter("customer", event.customerId()).setParameter("orderId", event.orderId().toString())
                .setParameter("seller", event.sellerId().toString()).setParameter("amount", Long.toString(event.amountVnd()))
                .getSingleResultOrNull() != null;
    }

    public boolean receive(Cancelled event) {
        return entities.createNativeQuery("""
                INSERT INTO payment_order_refund_inbox(event_id, checkout_session_id, order_id, customer_id, seller_id,
                    amount_vnd, reason, status)
                VALUES (:id, :checkout, :orderId, :customer, :seller, :amount, :reason, 'WAITING_DEPENDENCY')
                ON CONFLICT (event_id) DO NOTHING
                """).setParameter("id", event.eventId()).setParameter("checkout", event.checkoutId())
                .setParameter("orderId", event.orderId()).setParameter("customer", event.customerId())
                .setParameter("seller", event.sellerId()).setParameter("amount", event.amountVnd())
                .setParameter("reason", event.reason().name()).executeUpdate() == 1;
    }

    public Cancelled recorded(UUID eventId) {
        var row = (Object[]) entities.createNativeQuery("""
                SELECT event_id, checkout_session_id, order_id, customer_id, seller_id, amount_vnd, reason
                FROM payment_order_refund_inbox WHERE event_id = :event
                """).setParameter("event", eventId).getSingleResult();
        return cancellation(row);
    }

    public List<Cancelled> waiting(UUID checkout) {
        List<?> rows = entities.createNativeQuery("""
                SELECT event_id, checkout_session_id, order_id, customer_id, seller_id, amount_vnd, reason
                FROM payment_order_refund_inbox WHERE checkout_session_id = :checkout AND status = 'WAITING_DEPENDENCY'
                ORDER BY received_at, event_id
                """).setParameter("checkout", checkout).getResultList();
        return rows.stream().map(Object[].class::cast).map(OrderRefundInboxRepository::cancellation).toList();
    }

    public void finish(UUID event, boolean applied) {
        entities.createNativeQuery("UPDATE payment_order_refund_inbox SET status = :status WHERE event_id = :event")
                .setParameter("status", applied ? "APPLIED" : "FAILED_REVIEW").setParameter("event", event).executeUpdate();
    }

    public void rejectWaiting(UUID checkout) {
        entities.createNativeQuery("""
                UPDATE payment_order_refund_inbox SET status = 'FAILED_REVIEW'
                WHERE checkout_session_id = :checkout AND status = 'WAITING_DEPENDENCY'
                """).setParameter("checkout", checkout).executeUpdate();
    }

    private static Cancelled cancellation(Object[] row) {
        return new Cancelled((UUID) row[0], (UUID) row[1], (UUID) row[2], (UUID) row[3], (UUID) row[4],
                ((Number) row[5]).longValue(), RefundReason.valueOf((String) row[6]));
    }
}
