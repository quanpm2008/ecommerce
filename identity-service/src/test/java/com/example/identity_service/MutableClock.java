package com.example.identity_service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * A clock tests can move forward. Starts at the real current time so tokens look realistic, truncated to
 * microseconds like PostgreSQL timestamps: otherwise the driver's rounding of stored times makes boundary checks
 * (e.g. "expired exactly at expires_at") flaky.
 */
public class MutableClock extends Clock {

	private Instant now = currentMicros();

	public void advance(Duration duration) {
		now = now.plus(duration);
	}

	public void reset() {
		now = currentMicros();
	}

	@Override
	public Instant instant() {
		return now;
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return this;
	}


	private static Instant currentMicros() {
		return Instant.now().truncatedTo(ChronoUnit.MICROS);
	}

}
