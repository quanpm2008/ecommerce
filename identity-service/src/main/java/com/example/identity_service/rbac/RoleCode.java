package com.example.identity_service.rbac;

/**
 * The seeded roles. They are independent permission sets: no role implies another, and ADMIN cannot buy or sell.
 */
public enum RoleCode {
	CUSTOMER, SELLER, ADMIN
}
