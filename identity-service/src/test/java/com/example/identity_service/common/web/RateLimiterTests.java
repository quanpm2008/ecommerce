package com.example.identity_service.common.web;

import com.example.identity_service.MutableClock;
import com.example.identity_service.common.error.ApiException;
import com.example.identity_service.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimiterTests {

	private final MutableClock clock = new MutableClock();

	private final RateLimiter limiter = new RateLimiter(clock);

	private final RateLimitProperties.Limit twoPerMinute = new RateLimitProperties.Limit(2, Duration.ofMinutes(1));

	@Test
	void blocksOnceTheLimitIsExceededWithinTheWindow() {
		limiter.check("register", "10.0.0.1", twoPerMinute);
		limiter.check("register", "10.0.0.1", twoPerMinute);

		assertThatThrownBy(() -> limiter.check("register", "10.0.0.1", twoPerMinute))
				.isInstanceOfSatisfying(ApiException.class,
						ex -> assertThat(ex.code()).isEqualTo(ErrorCode.RATE_LIMITED));
	}

	@Test
	void countsKeysAndBucketsSeparately() {
		limiter.check("register", "10.0.0.1", twoPerMinute);
		limiter.check("register", "10.0.0.1", twoPerMinute);

		assertThatCode(() -> limiter.check("register", "10.0.0.2", twoPerMinute)).doesNotThrowAnyException();
		assertThatCode(() -> limiter.check("forgot-password", "10.0.0.1", twoPerMinute)).doesNotThrowAnyException();
	}

	@Test
	void aNewWindowStartsAfterTheOldOneEnds() {
		limiter.check("register", "10.0.0.1", twoPerMinute);
		limiter.check("register", "10.0.0.1", twoPerMinute);
		clock.advance(Duration.ofMinutes(1));

		assertThatCode(() -> limiter.check("register", "10.0.0.1", twoPerMinute)).doesNotThrowAnyException();
	}

}
