package com.example.identity_service.user;

/**
 * Login status of the whole account (DB §3.1.1); Customer and Seller restrictions are separate.
 */
public enum UserStatus {
	ACTIVE, DISABLED
}
