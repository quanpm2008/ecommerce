package com.example.identity_service.rbac;

import java.util.Set;

/**
 * Permission codes used in code. Only the baseline policy is defined here; role permissions live in the database seed.
 */
public final class Permissions {

	public static final String ACCOUNT_READ_OWN = "account:read:own";

	public static final String ACCOUNT_UPDATE_OWN = "account:update:own";

	/**
	 * Granted to every ACTIVE account on top of its role (API §3.2.1): reading and updating the own account. Nothing
	 * else: applying to sell goes through public registration, never through an existing account.
	 */
	public static final Set<String> BASELINE = Set.of(ACCOUNT_READ_OWN, ACCOUNT_UPDATE_OWN);

	private Permissions() {
	}

}
