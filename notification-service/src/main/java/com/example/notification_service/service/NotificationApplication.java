package com.example.notification_service.service;

import com.example.notification_service.client.IdentityGateway;
import com.example.notification_service.common.error.ApiException;
import com.example.notification_service.common.error.ErrorCode;
import com.example.notification_service.model.NotificationData.*;
import com.example.notification_service.repository.NotificationStore;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/** Remote validation precedes the local write transaction; Kafka and internal POST share creation/dedupe. */
@Service
public class NotificationApplication {
    private final NotificationTemplates templates;
    private final NotificationStore store;
    private final IdentityGateway identity;
    private final JsonMapper json;
    private final Clock clock;

    public NotificationApplication(NotificationTemplates templates, NotificationStore store,
            IdentityGateway identity, JsonMapper json, Clock clock) {
        this.templates = templates;
        this.store = store;
        this.identity = identity;
        this.json = json;
        this.clock = clock;
    }

    public CreationResult create(String producer, CreateRequest request) {
        NotificationTemplates.require(request != null, "body");
        return store.create(prepare(producer, List.of(request), clock.instant()));
    }

    public List<Rendered> prepare(String producer, List<CreateRequest> requests, Instant createdAt) {
        List<Rendered> rendered = new ArrayList<>(requests.size());
        for (CreateRequest request : requests) {
            NotificationTemplates.require(request != null, "body");
            NotificationTemplates.require(request.eventId() != null && !request.eventId().isBlank()
                    && request.eventId().length() <= 160, "eventId");
            NotificationTemplates.require(request.recipientUserId() != null, "recipientUserId");
            NotificationTemplates.require(request.audienceContext() != null, "audienceContext");
            NotificationTemplates.require(Set.of("IN_APP").equals(request.channels()), "channels");
            NotificationTemplates.Message message = templates.render(producer, request);
            String key;
            if ("REFUND_RETRY_REQUIRED".equals(request.type())) {
                Map<String, Object> data = request.templateData();
                key = "refund:" + NotificationTemplates.uuid(data, "refundId") + ":"
                        + NotificationTemplates.positive(data, "retryCycle") + ":"
                        + request.recipientUserId() + ":IN_APP";
            } else {
                key = request.eventId() + ":IN_APP:" + request.recipientUserId() + ":" + request.audienceContext();
            }
            // Event id is deliberately excluded: refund business dedupe also covers a new envelope id.
            Map<String, Object> fingerprint = new TreeMap<>();
            fingerprint.put("recipientUserId", request.recipientUserId().toString());
            fingerprint.put("audienceContext", request.audienceContext());
            fingerprint.put("type", request.type());
            fingerprint.put("reference", request.reference());
            fingerprint.put("templateData", canonical(request.templateData()));
            rendered.add(new Rendered(UUID.randomUUID(), key, hash(json.writeValueAsString(fingerprint)),
                    request.recipientUserId(), request.audienceContext(), request.type(), message.title(),
                    message.content(), request.reference(), createdAt));
        }
        // Validate all templates first; no partial persistence or email/contact dependency.
        Map<UUID, IdentityGateway.UserReference> recipients = new HashMap<>();
        for (Rendered notification : rendered) {
            var recipient = recipients.computeIfAbsent(notification.recipientUserId(), identity::recipient);
            if (recipient == null || !notification.recipientUserId().equals(recipient.userId())) {
                throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE);
            }
            if (!"ACCOUNT".equals(notification.audienceContext())
                    && !notification.audienceContext().equals(recipient.accountType())) {
                throw ApiException.forField(ErrorCode.VALIDATION_ERROR, "audienceContext", "WRONG_ACCOUNT_TYPE");
            }
        }
        return rendered;
    }

    public String canonicalHash(Object value) {
        return hash(json.writeValueAsString(canonical(value)));
    }

    private static Object canonical(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new TreeMap<>();
            map.forEach((key, item) -> result.put(key.toString(), canonical(item)));
            return result;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(NotificationApplication::canonical).toList();
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString()).stripTrailingZeros();
        }
        return value;
    }

    public static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
