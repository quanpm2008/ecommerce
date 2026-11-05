package com.example.identity_service.rbac;

import java.util.Set;

/**
 * Permission codes used in code. Only the baseline policy is defined here; role permissions live in the database seed.
 */
public final class Permissions {

	public static final String ACCOUNT_READ_OWN = "account:read:own";

	public static final String ACCOUNT_UPDATE_OWN = "account:update:own";

	public static final String SELLER_APPLICATION_OWN = "seller:application:own";

	/** Granted to every ACTIVE user without any role (API §3.2.1). Creating an own Customer is also baseline. */
	public static final Set<String> BASELINE = Set.of(ACCOUNT_READ_OWN, ACCOUNT_UPDATE_OWN, SELLER_APPLICATION_OWN);

	private Permissions() {
	}

}
