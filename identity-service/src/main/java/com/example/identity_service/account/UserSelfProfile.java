package com.example.identity_service.account;

import com.example.identity_service.user.AuthMethod;
import com.example.identity_service.user.UserStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * API {@code UserSelfProfile}: the same shape for every role. Roles and permissions are current values for the FE to
 * pick screens; the backend checks them again on every call. Never contains credentials.
 */
public record UserSelfProfile(UUID userId, String email, AuthMethod authMethod, String fullName, String phoneNumber,
		UserStatus userStatus, List<String> roles, List<String> permissions, long authzVersion,
		CustomerProfile customerProfile, SellerSelfProfile sellerProfile, Instant createdAt) {
}
