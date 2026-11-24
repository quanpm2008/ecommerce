package com.example.order_service.service;

import com.example.order_service.entity.CheckoutSession;
import com.example.order_service.repository.CheckoutSessionJpaRepository;
import com.example.order_service.service.OrderLifecycleService.Rejected;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class CollectionReviewService {
    private final CheckoutSessionJpaRepository sessions;
    private final EntityManager entities;
    private final JsonMapper json;

    public CollectionReviewService(CheckoutSessionJpaRepository sessions, EntityManager entities, JsonMapper json) {
        this.sessions = sessions;
        this.entities = entities;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public Page reviews(UUID checkoutId, int page, int size) {
        if (checkoutId != null) {
            var row = sessions.findById(checkoutId).map(CollectionReviewService::view)
                    .orElseThrow(CollectionReviewService::missing);
            return new Page(List.of(row), 1);
        }
        if (page < 1 || size < 1 || size > 100) throw new Rejected(400, "VALIDATION_ERROR", null);
        var rows = sessions.findByNeedsManualReviewTrue(PageRequest.of(page - 1, size,
                Sort.by("createdAt", "checkoutSessionId")));
        return new Page(rows.map(CollectionReviewService::view).getContent(), rows.getTotalElements());
    }

    @Transactional
    public Handled mark(UUID checkoutId, UUID adminId, String note, String requestId) {
        if (note == null || note.isBlank() || note.codePointCount(0, note.length()) > 1000) {
            throw new Rejected(400, "VALIDATION_ERROR", null);
        }
        var session = sessions.lock(checkoutId).orElseThrow(CollectionReviewService::missing);
        var before = auditSnapshot(session);
        session.markCollectionHandled(note);
        var at = Instant.now().truncatedTo(ChronoUnit.MICROS);
        String change = json.writeValueAsString(Map.of("before", before, "after", auditSnapshot(session)));
        entities.createNativeQuery("""
                insert into orders.admin_audit_log
                   (audit_id,actor_user_id,action,target_type,target_id,reason,before_after,request_id,happened_at)
                values (:audit,:actor,'MARK_HANDLED','CHECKOUT_SESSION',:id,:note,cast(:change as jsonb),:request,:at)
                """).setParameter("audit", UUID.randomUUID()).setParameter("actor", adminId)
                .setParameter("id", checkoutId).setParameter("note", note).setParameter("change", change)
                .setParameter("request", requestId == null || requestId.isBlank() || requestId.length() > 128
                        ? UUID.randomUUID().toString() : requestId).setParameter("at", at).executeUpdate();
        return new Handled(checkoutId, note, at);
    }

    private static Map<String, Object> auditSnapshot(CheckoutSession session) {
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("needsManualReview", session.getNeedsManualReview());
        snapshot.put("manualNote", session.getManualNote());
        return snapshot;
    }

    private static View view(CheckoutSession session) {
        return new View(session.getCheckoutSessionId(), session.getPaymentId(), session.getTotalAmountVnd(),
                session.getCreatedAt(), session.getNeedsManualReview(), session.getManualNote());
    }

    private static Rejected missing() { return new Rejected(404, "NOT_FOUND", null); }
    public record View(UUID checkoutSessionId, UUID paymentId, long amountVnd, Instant since,
                       boolean needsManualReview, String manualNote) {}
    public record Page(List<View> items, long totalItems) {}
    public record Handled(UUID checkoutSessionId, String manualNote, Instant handledAt) {}
}
