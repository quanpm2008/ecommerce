package com.example.identity_service.entity;

/**
 * The seeded roles, one per account type ({@code user.AccountType}). They are independent permission sets: no role
 * implies another, and an account never holds two.
 */
public enum RoleCode {
	CUSTOMER, SELLER, ADMIN
}
