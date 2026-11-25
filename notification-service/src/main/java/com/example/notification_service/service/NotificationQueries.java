package com.example.notification_service.service;

import com.example.notification_service.common.error.ApiException;
import com.example.notification_service.common.error.ErrorCode;
import com.example.notification_service.model.NotificationData.Page;
import com.example.notification_service.repository.NotificationStore;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** In-app use cases: validate pagination and apply the same retention instant to each operation. */
@Service
public class NotificationQueries {
    private final NotificationStore store;
    private final Clock clock;

    public NotificationQueries(NotificationStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public Page list(UUID owner, boolean unreadOnly, int page, int size) {
        if (page < 1) {
            throw ApiException.forField(ErrorCode.VALIDATION_ERROR, "page", "MIN");
        }
        if (size < 1 || size > 100) {
            throw ApiException.forField(ErrorCode.VALIDATION_ERROR, "size", "RANGE");
        }
        return store.list(owner, unreadOnly, page, size, clock.instant());
    }

    public long unreadCount(UUID owner) {
        return store.unreadCount(owner, clock.instant());
    }

    public void read(UUID owner, UUID id) {
        store.read(owner, id, clock.instant());
    }

    public void readAll(UUID owner) {
        store.readAll(owner, clock.instant());
    }

    public void delete(UUID owner, UUID id) {
        store.delete(owner, id);
    }
}
