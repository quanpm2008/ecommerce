package com.example.identity_service.user;

import com.example.identity_service.rbac.RoleCode;

/**
 * The kind of account (API §3.2.1, DB §3.1.1), fixed when the account is created. A person with several roles has
 * several accounts, each with its own email and sessions; nothing is combined. Each type has exactly one role, of the
 * same name: CUSTOMER from registration, SELLER once an admin approves, ADMIN only through the bootstrap.
 */
public enum AccountType {

	CUSTOMER, SELLER, ADMIN;

	/** The only role an account of this type can hold. */
	public RoleCode role() {
		return RoleCode.valueOf(name());
	}

}
