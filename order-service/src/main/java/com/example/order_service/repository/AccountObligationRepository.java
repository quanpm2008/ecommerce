package com.example.order_service.repository;

import com.example.order_service.entity.CheckoutSession;
import com.example.order_service.entity.Order;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class AccountObligationRepository {
    private final EntityManager entities;

    AccountObligationRepository(EntityManager entities) {
        this.entities = entities;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Snapshot read(UUID userId, boolean seller) {
        var rows = entities.createQuery("""
                select new com.example.order_service.repository.AccountObligationRepository$Row(
                    c.checkoutSessionId, c.customerId, c.status, c.closeReason, c.totalAmountVnd,
                    c.currency, c.paymentId, c.paidAt, c.expiresAt, o.orderId, o.customerId,
                    o.status, o.totalAmountVnd, o.paidAt, o.lifecycleVersion)
                from CheckoutSession c left join Order o on o.checkoutSessionId = c.checkoutSessionId
                where (:seller = true and o.sellerId = :userId)
                    or (:seller = false and (c.customerId = :userId or o.customerId = :userId))
                order by c.checkoutSessionId, o.orderId
                """, Row.class).setParameter("userId", userId).setParameter("seller", seller).getResultList();
        var unbound = rows.stream().filter(row -> row.paymentId() == null)
                .map(Row::checkoutId).distinct().toList();
        List<?> creating = unbound.isEmpty() ? List.of() : entities.createNativeQuery("""
                select checkout_session_id from orders.checkout_outbox
                where checkout_session_id in (:ids) and command_type = 'CreateProviderSession' and delivered_at is null
                order by checkout_session_id
                """, UUID.class).setParameter("ids", unbound).getResultList();
        return new Snapshot(List.copyOf(rows), creating.stream().map(UUID.class::cast).toList());
    }

    public record Snapshot(List<Row> rows, List<UUID> creatingPayments) {}

    public record Row(UUID checkoutId, UUID checkoutCustomerId, CheckoutSession.Status checkoutStatus,
            CheckoutSession.CloseReason closeReason, long checkoutAmountVnd, String currency, UUID paymentId,
            Instant checkoutPaidAt, Instant expiresAt, UUID orderId, UUID orderCustomerId, Order.Status orderStatus,
            Long orderAmountVnd, Instant orderPaidAt, Long orderVersion) {}
}
