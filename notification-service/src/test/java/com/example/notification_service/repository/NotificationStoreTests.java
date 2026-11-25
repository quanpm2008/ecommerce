package com.example.notification_service.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.notification_service.IntegrationTestSupport;
import com.example.notification_service.common.error.ApiException;
import com.example.notification_service.common.error.ErrorCode;
import com.example.notification_service.model.NotificationData.CreationResult;
import com.example.notification_service.model.NotificationData.Reference;
import com.example.notification_service.model.NotificationData.Rendered;
import com.example.notification_service.model.NotificationData.View;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class NotificationStoreTests extends IntegrationTestSupport {
    private static final Instant NOW = Instant.parse("2026-11-25T12:00:00Z");
    private static final UUID OWNER = UUID.fromString("81000000-0000-4000-8000-000000000001");
    private static final UUID OTHER = UUID.fromString("81000000-0000-4000-8000-000000000002");

    @Autowired
    private NotificationStore store;

    @Test
    void deletedNotificationRetainsReplayIdentityAndPayloadConflict() {
        Rendered first = rendered(1, "event-1", "a", NOW);
        assertThat(store.create(List.of(first)).created()).isTrue();
        store.delete(OWNER, first.notificationId());
        store.delete(OWNER, first.notificationId());

        CreationResult replay = store.create(List.of(rendered(2, "event-1", "a", NOW)));
        assertThat(replay.created()).isFalse();
        assertThat(replay.notificationIds()).containsExactly(first.notificationId());
        assertThat(store.list(OWNER, false, 1, 20, NOW).items()).isEmpty();
        assertThat(jdbc.queryForMap("SELECT title, content, reference_id, is_read FROM notifications"))
                .containsEntry("title", null).containsEntry("content", null)
                .containsEntry("reference_id", null).containsEntry("is_read", true);
        ApiException conflict = assertThrows(ApiException.class,
                () -> store.create(List.of(rendered(3, "event-1", "b", NOW))));
        assertThat(conflict.code()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications", Long.class)).isEqualTo(1);
    }

    @Test
    void conflictingBatchRollsBackEveryInsertedRecipient() {
        store.create(List.of(rendered(1, "z-existing", "a", NOW)));
        ApiException conflict = assertThrows(ApiException.class,
                () -> store.create(List.of(rendered(2, "a-new", "b", NOW),
                        rendered(3, "z-existing", "c", NOW))));
        assertThat(conflict.code()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        assertThat(jdbc.queryForList("SELECT dedupe_key FROM notifications", String.class))
                .containsExactly("z-existing");
    }

    @Test
    void concurrentIdenticalRequestsCommitOnceWithoutAbortedUniqueConflict() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return store.create(List.of(rendered(1, "same-key", "a", NOW)));
            });
            var second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return store.create(List.of(rendered(2, "same-key", "a", NOW)));
            });
            try {
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            CreationResult one = first.get(20, TimeUnit.SECONDS);
            CreationResult two = second.get(20, TimeUnit.SECONDS);
            assertThat(one.notificationIds()).isEqualTo(two.notificationIds());
            assertThat(one.created()).isNotEqualTo(two.created());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications", Long.class)).isEqualTo(1);
    }

    @Test
    void concurrentMultiRecipientBatchesUseStableDedupeLockOrder() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Rendered> forward = List.of(rendered(1, "a-key", "a", NOW),
                rendered(2, "b-key", "b", NOW));
        List<Rendered> reverse = List.of(rendered(3, "b-key", "b", NOW),
                rendered(4, "a-key", "a", NOW));
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return store.create(forward);
            });
            var second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return store.create(reverse);
            });
            try {
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            CreationResult one = first.get(20, TimeUnit.SECONDS);
            CreationResult two = second.get(20, TimeUnit.SECONDS);
            assertThat(one.notificationIds()).containsExactlyElementsOf(two.notificationIds().reversed());
            assertThat(one.created()).isNotEqualTo(two.created());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications", Long.class)).isEqualTo(2);
    }

    @Test
    void unreadFilterPaginationAndReadOperationsUseTheSameLatestFifty() {
        store.create(IntStream.rangeClosed(1, 51)
                .mapToObj(i -> rendered(i, "event-" + i, "a", NOW)).toList());
        jdbc.update("UPDATE notifications SET is_read = TRUE WHERE notification_id <> ?", id(1));

        assertThat(store.list(OWNER, true, 1, 20, NOW).items()).isEmpty();
        assertThat(store.list(OWNER, true, 1, 20, NOW).total()).isZero();
        assertThat(store.unreadCount(OWNER, NOW)).isZero();
        var page = store.list(OWNER, false, 2, 20, NOW);
        assertThat(page.total()).isEqualTo(50);
        assertThat(page.items()).extracting(View::notificationId)
                .containsExactlyElementsOf(IntStream.range(0, 20)
                        .mapToObj(i -> id(31 - i)).toList());
        assertThat(assertThrows(ApiException.class, () -> store.read(OWNER, id(1), NOW)).code())
                .isEqualTo(ErrorCode.NOT_FOUND);
        store.read(OWNER, id(51), NOW);
        store.read(OWNER, id(51), NOW);
        store.readAll(OWNER, NOW);
        assertThat(jdbc.queryForObject("SELECT is_read FROM notifications WHERE notification_id = ?",
                Boolean.class, id(1))).isFalse();
    }

    @Test
    void ownerAndAgeRestrictionsApplyBeforeAnyRetentionRun() {
        Instant cutoff = NOW.minus(Duration.ofDays(30));
        store.create(List.of(rendered(1, "boundary", "a", cutoff),
                rendered(2, "expired", "b", cutoff.minusMillis(1))));
        assertThat(store.list(OWNER, false, 1, 20, NOW).items()).extracting(View::notificationId)
                .containsExactly(id(1));
        assertThat(store.unreadCount(OWNER, NOW)).isEqualTo(1);
        assertThat(store.list(OTHER, false, 1, 20, NOW).items()).isEmpty();
        assertThat(store.unreadCount(OTHER, NOW)).isZero();
        assertThat(assertThrows(ApiException.class, () -> store.read(OWNER, id(2), NOW)).code())
                .isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(assertThrows(ApiException.class, () -> store.read(OTHER, id(1), NOW)).code())
                .isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(assertThrows(ApiException.class, () -> store.delete(OTHER, id(1))).code())
                .isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(assertThrows(ApiException.class, () -> store.delete(OWNER, id(99))).code())
                .isEqualTo(ErrorCode.NOT_FOUND);
        store.readAll(OTHER, NOW);
        assertThat(store.unreadCount(OWNER, NOW)).isEqualTo(1);
        store.readAll(OWNER, NOW);
        assertThat(jdbc.queryForObject("SELECT is_read FROM notifications WHERE notification_id = ?",
                Boolean.class, id(2))).isFalse();
    }

    @Test
    void retentionScrubsExpiredAndOverLimitPayloadButPreservesDedupeForever() {
        store.create(IntStream.rangeClosed(1, 52)
                .mapToObj(i -> rendered(i, "event-" + i, "a", NOW)).toList());
        store.create(List.of(rendered(53, "expired", "b", NOW.minus(Duration.ofDays(31)))));
        assertThat(store.purgeExpired(NOW)).isEqualTo(3);
        assertThat(store.purgeExpired(NOW)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM notifications
                WHERE deleted_at IS NOT NULL AND is_read AND title IS NULL AND content IS NULL
                  AND reference_type IS NULL AND reference_id IS NULL AND reference_code IS NULL
                """, Long.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications", Long.class)).isEqualTo(53);
        store.delete(OWNER, id(52));
        CreationResult replay = store.create(List.of(rendered(99, "event-1", "a", NOW)));
        assertThat(replay.created()).isFalse();
        assertThat(replay.notificationIds()).containsExactly(id(1));
        assertThat(store.list(OWNER, false, 1, 100, NOW).items()).hasSize(49);
        assertThat(store.list(OWNER, false, 1, 100, NOW).items()).extracting(View::notificationId)
                .doesNotContain(id(1), id(2), id(52), id(53));
        assertThat(assertThrows(ApiException.class,
                () -> store.create(List.of(rendered(99, "event-1", "c", NOW)))).code())
                .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED);
    }

    @Test
    void deletionDoesNotPullAnOlderNotificationIntoTheLatestFifty() {
        store.create(IntStream.rangeClosed(1, 51)
                .mapToObj(i -> rendered(i, "event-" + i, "a", NOW)).toList());
        store.delete(OWNER, id(51));
        assertThat(store.list(OWNER, false, 1, 100, NOW).items()).hasSize(49)
                .extracting(View::notificationId).doesNotContain(id(1), id(51));
        assertThat(store.unreadCount(OWNER, NOW)).isEqualTo(49);
        assertThat(assertThrows(ApiException.class, () -> store.read(OWNER, id(1), NOW)).code())
                .isEqualTo(ErrorCode.NOT_FOUND);
        store.readAll(OWNER, NOW);
        assertThat(jdbc.queryForObject("SELECT is_read FROM notifications WHERE notification_id = ?",
                Boolean.class, id(1))).isFalse();
        assertThat(store.purgeExpired(NOW)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT content FROM notifications WHERE notification_id = ?",
                String.class, id(1))).isNull();
    }

    @Test
    void concurrentDifferentPayloadsReturnConflictWithoutCreatingAnotherRow() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var requests = IntStream.rangeClosed(1, 2).mapToObj(sequence -> executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    store.create(List.of(rendered(sequence, "same-key", sequence == 1 ? "a" : "b", NOW)));
                    return "CREATED";
                } catch (ApiException exception) {
                    return exception.code().name();
                }
            })).toList();
            try {
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            assertThat(List.of(requests.get(0).get(20, TimeUnit.SECONDS),
                    requests.get(1).get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("CREATED", "IDEMPOTENCY_KEY_REUSED");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications", Long.class)).isEqualTo(1);
    }

    private static Rendered rendered(int sequence, String key, String hash, Instant createdAt) {
        return new Rendered(id(sequence), key, hash.repeat(64), OWNER, "CUSTOMER", "ORDER_DELIVERED",
                "Đơn hàng đã giao", "Đơn hàng của bạn đã được giao.",
                new Reference("ORDER", UUID.fromString("82000000-0000-4000-8000-000000000001"), null),
                createdAt);
    }

    private static UUID id(int sequence) {
        return new UUID(0L, sequence);
    }
}
