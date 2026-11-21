package com.example.identity_service.auth;

import com.example.identity_service.seller.SellerApprovalStatus;
import com.example.identity_service.user.AccountType;

import java.util.UUID;

/**
 * 201 of {@code POST /auth/register-seller} (API §6.1): the new SELLER account and its PENDING application. No
 * session: the seller signs in only once approved.
 */
public record SellerRegistration(UUID userId, AccountType accountType, SellerProfile sellerProfile,
		UUID applicationId) {

	public record SellerProfile(UUID sellerId, SellerApprovalStatus approvalStatus, UUID latestApplicationId) {
	}

	static SellerRegistration of(UUID sellerId, UUID applicationId) {
		return new SellerRegistration(sellerId, AccountType.SELLER,
				new SellerProfile(sellerId, SellerApprovalStatus.PENDING, applicationId), applicationId);
	}

}
