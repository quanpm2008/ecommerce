package com.example.identity_service.entity;

/**
 * Login status of the whole account (DB §3.1.1); Customer and Seller restrictions are separate.
 */
public enum UserStatus {
	ACTIVE, DISABLED
}
