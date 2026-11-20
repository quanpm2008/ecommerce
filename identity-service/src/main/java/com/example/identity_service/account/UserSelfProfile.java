package com.example.identity_service.account;

import com.example.identity_service.user.AccessMode;
import com.example.identity_service.user.AccountStatus;
import com.example.identity_service.user.AccountType;
import com.example.identity_service.user.AuthMethod;
import com.example.identity_service.user.UserStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * API {@code UserSelfProfile}: the same shape for every account type, with the account's one role and at most one of
 * {@code customerProfile} / {@code sellerProfile}. Roles and permissions are current values for the FE to pick
 * screens; the backend checks them again on every call. Never contains credentials.
 */
public record UserSelfProfile(UUID userId, AccountType accountType, String email, AuthMethod authMethod,
		String fullName, String phoneNumber, String avatarObjectKey, String avatarUrl, AccountStatus accountStatus,
		UserStatus userStatus, Instant bannedAt, Instant disabledAt, Instant restoreUntil, List<String> roles,
		List<String> permissions, long authzVersion, AccessMode accessMode, CustomerProfile customerProfile,
		SellerSelfProfile sellerProfile, Instant createdAt) {
}
