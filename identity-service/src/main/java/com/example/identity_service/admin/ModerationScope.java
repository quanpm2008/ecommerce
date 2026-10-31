package com.example.identity_service.admin;

/**
 * What a ban or unban applies to (API §6.1). Always explicit, never inferred from the user's only role.
 */
public enum ModerationScope {
	USER, CUSTOMER, SELLER
}
