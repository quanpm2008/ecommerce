package com.example.notification_service.service;

import com.example.notification_service.repository.NotificationEventTransaction;

import com.example.notification_service.common.error.ApiException;
import com.example.notification_service.common.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Unusable events retain only a digest and an actionable error code, never a payload or secret. */
@Service
public class NotificationEventHandler {
    private final JsonMapper json;
    private final NotificationEventMapper mapper;
    private final NotificationApplication application;
    private final NotificationEventTransaction transactions;
    private final Clock clock;

    public NotificationEventHandler(JsonMapper json, NotificationEventMapper mapper, NotificationApplication application,
            NotificationEventTransaction transactions, Clock clock) {
        this.json = json;
        this.mapper = mapper;
        this.application = application;
        this.transactions = transactions;
        this.clock = clock;
    }

    public void handle(String topic, String body) {
        Instant now = clock.instant();
        String hash = NotificationApplication.hash(body);
        String id = "unreadable:" + hash;
        String type = NotificationEventMapper.SOURCES.containsKey(topic) ? topic : "unknown";
        JsonNode envelope;
        try {
            envelope = json.readTree(body);
            if (envelope == null || !envelope.isObject()) { throw new IllegalArgumentException(); }
            hash = application.canonicalHash(json.convertValue(envelope, Object.class));
            JsonNode eventId = envelope.path("eventId");
            if (!eventId.isString() || eventId.stringValue().isBlank() || eventId.stringValue().length() > 160) {
                throw new IllegalArgumentException();
            }
            id = eventId.stringValue();
        } catch (RuntimeException ex) {
            transactions.failed(id, hash, type, "INVALID_EVENT_ENVELOPE", now);
            return;
        }
        if (transactions.applied(id, hash)) { return; }
        try {
            String producer = NotificationEventMapper.SOURCES.get(topic);
            if (producer == null || !topic.equals(envelope.path("eventType").asString())
                    || !producer.equals(envelope.path("producer").asString())
                    || !envelope.path("version").isIntegralNumber() || envelope.path("version").longValue() < 1
                    || !envelope.path("aggregateId").isString()) {
                throw new IllegalArgumentException();
            }
            Instant occurredAt = Instant.parse(envelope.path("occurredAt").asString());
            var requests = mapper.map(id, topic, envelope.path("payload"));
            var rendered = application.prepare(producer, requests, occurredAt);
            transactions.apply(id, hash, topic, rendered, now);
        } catch (ApiException ex) {
            if (ex.code() == ErrorCode.SERVICE_UNAVAILABLE || ex.code() == ErrorCode.IDEMPOTENCY_KEY_REUSED) {
                throw ex;
            }
            transactions.failed(id, hash, type, ex.code().name(), now);
        } catch (IllegalArgumentException ex) {
            transactions.failed(id, hash, type, "INVALID_EVENT_ENVELOPE", now);
        }
    }
}
