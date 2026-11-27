package com.example.identity_service.entity;

/**
 * Derived, never stored (API §3.2, DB §3.1.1): ACTIVE = user ACTIVE + profile ACTIVE; BANNED = user ACTIVE + profile
 * BANNED; DISABLED = user DISABLED + profile BANNED. An ADMIN has no profile and is ACTIVE or DISABLED by its user.
 */
public enum AccountStatus {

	ACTIVE, BANNED, DISABLED;

	public static AccountStatus of(User user, boolean profileBanned) {
		if (!user.isActive()) {
			return DISABLED;
		}
		return profileBanned ? BANNED : ACTIVE;
	}

}
