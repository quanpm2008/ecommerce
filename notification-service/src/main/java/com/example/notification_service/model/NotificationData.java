package com.example.notification_service.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** API §6.7/7.1.1: no recipient id or delivery state in the public view. */
public final class NotificationData {
    private NotificationData() {}

    public record Reference(String type, UUID id, String code) {}

    public record CreateRequest(String eventId, UUID recipientUserId, String audienceContext, String type,
            Set<String> channels, Reference reference, Map<String, Object> templateData) {}

    public record Rendered(UUID notificationId, String dedupeKey, String payloadHash, UUID recipientUserId,
            String audienceContext, String type, String title, String content, Reference reference,
            Instant createdAt) {}

    public record CreationResult(List<UUID> notificationIds, boolean created) {}

    public record View(UUID notificationId, String audienceContext, String type, String title, String content,
            Reference reference, boolean isRead, Instant createdAt) {}

    public record Page(List<View> items, long total, int page, int size) {}
}
