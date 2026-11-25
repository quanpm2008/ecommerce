package com.example.notification_service.service;

import com.example.notification_service.repository.NotificationStore;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Reads enforce expiry independently; this worker removes old content but never dedupe tombstones. */
@Component
@ConditionalOnProperty(name = "notification.retention.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationRetention {
    private final NotificationStore store;
    private final Clock clock;

    public NotificationRetention(NotificationStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${notification.retention.interval:PT1H}")
    public void purge() {
        store.purgeExpired(clock.instant());
    }
}
