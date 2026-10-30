package com.example.identity_service.common.web;

import com.example.identity_service.common.error.ApiException;
import com.example.identity_service.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-window limiter kept in memory, so each instance counts separately. Good enough for the MVP; the gateway is
 * expected to add a shared limit.
 */
@Component
@RequiredArgsConstructor
public class RateLimiter {

	private static final int CLEANUP_THRESHOLD = 10_000;

	private final Clock clock;

	private final Map<String, Window> windows = new ConcurrentHashMap<>();

	/** Counts one request for {@code key} in {@code bucket}; throws {@code RATE_LIMITED} once over the limit. */
	public void check(String bucket, String key, RateLimitProperties.Limit limit) {
		Instant now = clock.instant();
		Window window = windows.compute(bucket + '|' + key, (k, current) -> current == null || current.isOver(now)
				? new Window(now.plus(limit.window()), 1)
				: new Window(current.endsAt(), current.count() + 1));
		if (windows.size() > CLEANUP_THRESHOLD) {
			windows.values().removeIf(w -> w.isOver(now));
		}
		if (window.count() > limit.limit()) {
			throw new ApiException(ErrorCode.RATE_LIMITED);
		}
	}

	private record Window(Instant endsAt, int count) {

		boolean isOver(Instant now) {
			return !now.isBefore(endsAt);
		}

	}

}
