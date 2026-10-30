package com.example.identity_service.outbox;

import java.util.UUID;

/**
 * Who caused an event (API §7.2). Audit data only: consumers never derive permissions from it.
 */
public record EventActor(UUID userId, Kind kind, Context context) {

	public enum Kind {
		USER, SERVICE
	}

	public enum Context {
		CUSTOMER, SELLER, ADMIN, ACCOUNT
	}

	public static EventActor user(UUID userId, Context context) {
		return new EventActor(userId, Kind.USER, context);
	}

}
