package com.example.payment_service.service;

import com.example.payment_service.client.OrderCollectionReviewClient;
import com.example.payment_service.client.OrderCollectionReviewClient.Review;
import com.example.payment_service.security.RefundCustomerAuthorization.Rejected;
import feign.FeignException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CollectionReviewClientService {
    private final OrderCollectionReviewClient order;
    private final String credential;

    CollectionReviewClientService(OrderCollectionReviewClient order,
            @Value("${payment.order.credential:}") String credential) {
        this.order = order;
        this.credential = credential;
    }

    public List<Review> flagged(String bearer) {
        var flagged = new LinkedHashMap<UUID, Review>();
        for (int page = 1;; page++) {
            var result = read(bearer, null, page);
            for (var review : result.items()) {
                if (!review.needsManualReview() || review.checkoutSessionId() == null || review.since() == null) {
                    throw new Rejected(503, "SERVICE_UNAVAILABLE");
                }
                flagged.putIfAbsent(review.checkoutSessionId(), review);
            }
            if (result.items().isEmpty() || (long) page * 100 >= result.totalItems()) break;
        }
        return List.copyOf(flagged.values());
    }

    public String note(UUID id, String bearer) {
        var result = read(bearer, id, 1);
        if (result.items().size() != 1 || !id.equals(result.items().getFirst().checkoutSessionId())) {
            throw new Rejected(503, "SERVICE_UNAVAILABLE");
        }
        return result.items().getFirst().manualNote();
    }

    public OrderCollectionReviewClient.Result mark(UUID id, String bearer, String requestId, String note) {
        requireCredential();
        try {
            var result = order.mark(credential, bearer, requestId, id, new OrderCollectionReviewClient.Note(note));
            if (result == null || result.data() == null || !id.equals(result.data().checkoutSessionId())
                    || result.data().handledAt() == null || !note.equals(result.data().manualNote())) {
                throw new Rejected(503, "SERVICE_UNAVAILABLE");
            }
            return result.data();
        } catch (FeignException failure) {
            throw upstream(failure);
        }
    }

    private OrderCollectionReviewClient.Page read(String bearer, UUID id, int page) {
        requireCredential();
        try {
            var result = order.reviews(credential, bearer, id, page, 100);
            if (result == null || result.data() == null || result.data().items() == null || result.data().totalItems() < 0) {
                throw new Rejected(503, "SERVICE_UNAVAILABLE");
            }
            return result.data();
        } catch (FeignException failure) {
            throw upstream(failure);
        }
    }

    private void requireCredential() {
        if (credential.isBlank()) throw new Rejected(503, "SERVICE_UNAVAILABLE");
    }

    private static Rejected upstream(FeignException failure) {
        return switch (failure.status()) {
            case 403 -> new Rejected(403, "FORBIDDEN");
            case 404 -> new Rejected(404, "NOT_FOUND");
            default -> new Rejected(503, "SERVICE_UNAVAILABLE");
        };
    }
}
