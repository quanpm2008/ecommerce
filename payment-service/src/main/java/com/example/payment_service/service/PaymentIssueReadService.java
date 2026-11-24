package com.example.payment_service.service;

import com.example.payment_service.client.OrderCollectionReviewClient.Review;
import com.example.payment_service.security.RefundCustomerAuthorization.Rejected;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Comparator;
import java.util.ArrayList;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentIssueReadService {
    private final EntityManager entities;
    private final Clock clock;

    public PaymentIssueReadService(EntityManager entities, Clock clock) {
        this.entities = entities;
        this.clock = clock;
    }

    private static final String ISSUES = """
        with classified as (
          select 'PAYMENT_PENDING' as issue_type, p.payment_id, null::uuid as refund_id, p.checkout_session_id,
            null::uuid as order_id, p.amount_vnd, p.created_at as since, 0 as retry_count,
            'PAYMENT_LINK_DELAYED' as reason, null::timestamptz as next_retry
          from payments p left join payment_checkout_guard g using (checkout_session_id)
          where p.status='PENDING' and g.closed_at is null
            and p.created_at <= :linkCutoff and p.expires_at > :now
            and (p.payment_url is null or btrim(p.payment_url)='')
          union all
          select case when w.review_required_at is not null then 'MANUAL_REVIEW'
                      when r.status='FAILED' then 'REFUND_FAILED' else 'REFUND_STUCK' end,
            p.payment_id, r.refund_id, p.checkout_session_id, r.order_id, r.amount_vnd,
            coalesce(w.review_required_at, case when r.status='FAILED' then r.completed_at else a.created_at end,r.created_at),
            r.retry_count,
            case when w.review_required_at is not null then 'REFUND_REVIEW_REQUIRED'
                 when r.status='PENDING' then 'REFUND_ATTEMPT_STUCK'
                 when r.customer_retry_required then 'REFUND_WAITING_CUSTOMER'
                 else 'REFUND_WAITING_AUTO_RETRY' end,
            case when w.review_required_at is null and r.status='FAILED' and not r.customer_retry_required then w.next_run_at end
          from refunds r join payments p on p.payment_id=r.payment_id
          left join payment_refund_work w on w.refund_id=r.refund_id
          left join lateral (
            select status, created_at from payment_refund_attempts a where a.refund_id=r.refund_id
               and a.retry_cycle=r.retry_cycle order by attempt_number desc limit 1
          ) a on true
          where w.review_required_at is not null or r.status='FAILED' or
            (r.status='PENDING' and a.status='PENDING' and a.created_at <= :attemptCutoff)
        )
        """;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> issues(Type type, int page, int size, List<Review> flags) {
        PaymentReadService.page(page, size);
        long offset = (long) (page - 1) * size;
        if (offset > Integer.MAX_VALUE - size) throw new Rejected(400, "VALIDATION_ERROR");
        var excluded = flags.stream().map(Review::checkoutSessionId).toList();
        boolean includeCollection = type == null || type == Type.MANUAL_REVIEW;
        if (!includeCollection || flags.isEmpty()) {
            var result = local(type, offset, size, excluded);
            return Map.of("data", result.rows(), "meta", PaymentReadService.meta(page, size, result.count()));
        }
        var result = local(type, 0, (int) (offset + size), excluded);
        var rows = new ArrayList<>(result.rows());
        flags.stream().map(flag -> new Issue(Type.MANUAL_REVIEW, flag.paymentId(), null, flag.checkoutSessionId(), null,
                flag.amountVnd(), flag.since(), 0, "COLLECTION_REVIEW_REQUIRED", null)).forEach(rows::add);
        rows.sort(Comparator.comparing(Issue::since).thenComparing(row -> row.checkoutSessionId().toString())
                .thenComparing(row -> id(row.paymentId())).thenComparing(row -> id(row.refundId())));
        return Map.of("data", rows.stream().skip(offset).limit(size).toList(),
                "meta", PaymentReadService.meta(page, size, result.count() + flags.size()));
    }

    private Slice local(Type type, long offset, int limit, List<UUID> excluded) {
        String where = " where 1=1";
        if (type != null) where += " and issue_type=:type";
        if (!excluded.isEmpty()) where += " and (refund_id is not null or checkout_session_id not in (:excluded))";
        Instant now = clock.instant();
        var count = entities.createNativeQuery(ISSUES + "select count(*) from classified" + where)
                .setParameter("now", now).setParameter("linkCutoff", now.minusSeconds(120))
                .setParameter("attemptCutoff", now.minusSeconds(1800));
        var rows = entities.createNativeQuery(ISSUES + "select * from classified" + where
                + " order by since,checkout_session_id::text,payment_id::text,refund_id::text nulls first limit :limit offset :offset")
                .setParameter("now", now).setParameter("linkCutoff", now.minusSeconds(120))
                .setParameter("attemptCutoff", now.minusSeconds(1800)).setParameter("limit", limit).setParameter("offset", offset);
        if (type != null) {
            count.setParameter("type", type.name());
            rows.setParameter("type", type.name());
        }
        if (!excluded.isEmpty()) {
            count.setParameter("excluded", excluded);
            rows.setParameter("excluded", excluded);
        }
        List<Issue> data = rows.getResultList().stream().map(row -> issue((Object[]) row)).toList();
        return new Slice(data, ((Number) count.getSingleResult()).longValue());
    }

    private static String id(UUID id) { return id == null ? "" : id.toString(); }

    private static Issue issue(Object[] row) {
        return new Issue(Type.valueOf((String) row[0]), (UUID) row[1], (UUID) row[2], (UUID) row[3], (UUID) row[4],
                ((Number) row[5]).longValue(), instant(row[6]), ((Number) row[7]).intValue(),
                (String) row[8], instant(row[9]));
    }

    private static Instant instant(Object value) {
        return value instanceof OffsetDateTime timestamp ? timestamp.toInstant() : (Instant) value;
    }

    public enum Type { PAYMENT_PENDING, REFUND_FAILED, REFUND_STUCK, MANUAL_REVIEW }
    public record Slice(List<Issue> rows, long count) {}
    public record Issue(Type issueType, UUID paymentId, UUID refundId, UUID checkoutSessionId, UUID orderId, long amountVnd,
                        Instant since, int retryCount, String displayReason, Instant nextRetryAt) {}
}
