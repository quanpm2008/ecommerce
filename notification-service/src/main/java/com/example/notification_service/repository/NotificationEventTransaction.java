package com.example.notification_service.repository;

import com.example.notification_service.common.error.ApiException;
import com.example.notification_service.common.error.ErrorCode;
import com.example.notification_service.model.NotificationData.Rendered;
import com.example.notification_service.repository.NotificationStore;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Inbox and all recipient notifications commit together; no remote calls inside this transaction. */
@Repository
public class NotificationEventTransaction {
    private final JdbcTemplate jdbc;
    private final NotificationStore store;

    public NotificationEventTransaction(JdbcTemplate jdbc, NotificationStore store) {
        this.jdbc = jdbc;
        this.store = store;
    }

    public boolean applied(String eventId, String hash) {
        var rows = jdbc.queryForList("SELECT payload_hash, state FROM notification_inbox WHERE event_id = ?", eventId);
        if (rows.isEmpty()) { return false; }
        if (!hash.equals(rows.getFirst().get("payload_hash"))) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        }
        return "APPLIED".equals(rows.getFirst().get("state"));
    }

    @Transactional
    public void apply(String eventId, String hash, String type, List<Rendered> notifications, Instant now) {
        insert(eventId, hash, type, "APPLIED", null, now);
        var row = jdbc.queryForMap("SELECT payload_hash, state FROM notification_inbox WHERE event_id = ? FOR UPDATE", eventId);
        if (!hash.equals(row.get("payload_hash"))) { throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED); }
        store.create(notifications);
        jdbc.update("UPDATE notification_inbox SET state='APPLIED', last_error_code=NULL WHERE event_id=?", eventId);
    }

    @Transactional
    public void failed(String eventId, String hash, String type, String code, Instant now) {
        insert(eventId, hash, type, "FAILED_REVIEW", code, now);
    }

    private void insert(String eventId, String hash, String type, String state, String code, Instant now) {
        jdbc.update("""
                INSERT INTO notification_inbox(event_id,payload_hash,event_type,state,last_error_code,received_at)
                VALUES (?,?,?,?,?,?) ON CONFLICT DO NOTHING
                """, eventId, hash, type, state, code, Timestamp.from(now));
    }
}
