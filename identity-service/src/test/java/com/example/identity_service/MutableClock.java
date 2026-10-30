package com.example.identity_service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * A clock tests can move forward. Starts at the real current time so tokens look realistic.
 */
public class MutableClock extends Clock {

	private Instant now = Instant.now();

	public void advance(Duration duration) {
		now = now.plus(duration);
	}

	public void reset() {
		now = Instant.now();
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

}
