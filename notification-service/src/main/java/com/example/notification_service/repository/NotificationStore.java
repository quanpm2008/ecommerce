package com.example.notification_service.repository;

import com.example.notification_service.common.error.ApiException;
import com.example.notification_service.common.error.ErrorCode;
import com.example.notification_service.model.NotificationData.CreationResult;
import com.example.notification_service.model.NotificationData.Page;
import com.example.notification_service.model.NotificationData.Reference;
import com.example.notification_service.model.NotificationData.Rendered;
import com.example.notification_service.model.NotificationData.View;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class NotificationStore {
    private static final Duration RETENTION = Duration.ofDays(30);
    // Apply the inbox window before unread filtering or pagination.
    private static final String VISIBLE = """
            WITH recent AS (
                SELECT notification_id, audience_context, notification_type, title, content,
                       reference_type, reference_id, reference_code, is_read, created_at, deleted_at
                FROM notifications
                WHERE recipient_user_id = ? AND channel = 'IN_APP'
                  AND created_at >= ?
                ORDER BY created_at DESC, notification_id DESC
                LIMIT 50
            ), visible AS (
                SELECT * FROM recent WHERE deleted_at IS NULL
            )
            """;
    private static final RowMapper<View> VIEW_MAPPER = NotificationStore::view;

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public NotificationStore(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public CreationResult create(List<Rendered> batch) {
        if (batch.isEmpty()) {
            return new CreationResult(List.of(), false);
        }
        // All callers acquire unique-key locks in the same order, including multi-recipient events.
        List<Rendered> ordered = batch;
        if (batch.size() > 1) {
            ordered = new ArrayList<>(batch);
            ordered.sort(Comparator.comparing(Rendered::dedupeKey));
        }
        Map<String, UUID> ids = new HashMap<>(batch.size());
        boolean created = false;
        for (Rendered item : ordered) {
            Reference reference = item.reference();
            int inserted = jdbc.update("""
                    INSERT INTO notifications
                        (notification_id, dedupe_key, payload_hash, recipient_user_id,
                         audience_context, notification_type, channel, title, content,
                         reference_type, reference_id, reference_code, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, 'IN_APP', ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (dedupe_key) DO NOTHING
                    """, item.notificationId(), item.dedupeKey(), item.payloadHash(), item.recipientUserId(),
                    item.audienceContext(), item.type(), item.title(), item.content(),
                    reference == null ? null : reference.type(), reference == null ? null : reference.id(),
                    reference == null ? null : reference.code(), Timestamp.from(item.createdAt()));
            if (inserted == 1) {
                created = true;
                ids.put(item.dedupeKey(), item.notificationId());
            } else {
                // A separate statement sees the committed conflicting insert under READ COMMITTED.
                UUID previous = jdbc.queryForObject("""
                        SELECT notification_id, payload_hash FROM notifications WHERE dedupe_key = ?
                        """, (rs, row) -> {
                            if (!item.payloadHash().equals(rs.getString("payload_hash"))) {
                                throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
                            }
                            return rs.getObject("notification_id", UUID.class);
                        }, item.dedupeKey());
                ids.put(item.dedupeKey(), previous);
            }
        }
        return new CreationResult(batch.stream().map(item -> ids.get(item.dedupeKey())).toList(), created);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page list(UUID owner, boolean unreadOnly, int page, int size, Instant now) {
        Timestamp cutoff = Timestamp.from(now.minus(RETENTION));
        String filter = unreadOnly ? " WHERE NOT is_read" : "";
        long total = jdbc.queryForObject(VISIBLE + "SELECT count(*) FROM visible" + filter,
                Long.class, owner, cutoff);
        List<View> items = jdbc.query(VISIBLE + "SELECT * FROM visible" + filter
                        + " ORDER BY created_at DESC, notification_id DESC LIMIT ? OFFSET ?",
                VIEW_MAPPER, owner, cutoff, size, ((long) page - 1) * size);
        return new Page(items, total, page, size);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID owner, Instant now) {
        return jdbc.queryForObject(VISIBLE + "SELECT count(*) FROM visible WHERE NOT is_read",
                Long.class, owner, Timestamp.from(now.minus(RETENTION)));
    }

    @Transactional
    public void read(UUID owner, UUID id, Instant now) {
        int updated = jdbc.update(VISIBLE + """
                UPDATE notifications SET is_read = TRUE
                WHERE notification_id = ? AND recipient_user_id = ? AND deleted_at IS NULL
                  AND notification_id IN (SELECT notification_id FROM visible)
                """, owner, Timestamp.from(now.minus(RETENTION)), id, owner);
        if (updated == 0) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
    }

    @Transactional
    public void readAll(UUID owner, Instant now) {
        jdbc.update(VISIBLE + """
                UPDATE notifications SET is_read = TRUE
                WHERE recipient_user_id = ? AND deleted_at IS NULL AND NOT is_read
                  AND notification_id IN (SELECT notification_id FROM visible)
                """, owner, Timestamp.from(now.minus(RETENTION)), owner);
    }

    @Transactional
    public void delete(UUID owner, UUID id) {
        int updated = jdbc.update("""
                UPDATE notifications
                SET deleted_at = COALESCE(deleted_at, ?), is_read = TRUE,
                    title = NULL, content = NULL,
                    reference_type = NULL, reference_id = NULL, reference_code = NULL
                WHERE notification_id = ? AND recipient_user_id = ? AND channel = 'IN_APP'
                """, Timestamp.from(clock.instant()), id, owner);
        if (updated == 0) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
    }

    @Transactional
    public int purgeExpired(Instant now) {
        return jdbc.update("""
                WITH ranked AS (
                    SELECT notification_id, created_at, deleted_at,
                           row_number() OVER (
                               PARTITION BY recipient_user_id
                               ORDER BY created_at DESC, notification_id DESC
                           ) AS position
                    FROM notifications
                    WHERE channel = 'IN_APP'
                ), expired AS (
                    SELECT notification_id FROM ranked
                    WHERE deleted_at IS NULL AND (created_at < ? OR position > 50)
                )
                UPDATE notifications
                SET deleted_at = COALESCE(deleted_at, ?), is_read = TRUE,
                    title = NULL, content = NULL,
                    reference_type = NULL, reference_id = NULL, reference_code = NULL
                WHERE channel = 'IN_APP'
                  AND (notification_id IN (SELECT notification_id FROM expired)
                       OR (deleted_at IS NOT NULL AND (title IS NOT NULL OR content IS NOT NULL
                           OR reference_type IS NOT NULL OR reference_id IS NOT NULL
                           OR reference_code IS NOT NULL)))
                """, Timestamp.from(now.minus(RETENTION)), Timestamp.from(now));
    }

    private static View view(ResultSet rs, int row) throws SQLException {
        String referenceType = rs.getString("reference_type");
        Reference reference = referenceType == null ? null : new Reference(referenceType,
                rs.getObject("reference_id", UUID.class), rs.getString("reference_code"));
        return new View(rs.getObject("notification_id", UUID.class), rs.getString("audience_context"),
                rs.getString("notification_type"), rs.getString("title"), rs.getString("content"),
                reference, rs.getBoolean("is_read"), rs.getTimestamp("created_at").toInstant());
    }
}
