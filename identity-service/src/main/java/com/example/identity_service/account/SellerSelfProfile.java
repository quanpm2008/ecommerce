package com.example.identity_service.account;

import com.example.identity_service.seller.Seller;
import com.example.identity_service.seller.SellerApprovalStatus;
import com.example.identity_service.seller.SellerStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * API {@code SellerSelfProfile}: the owner's view, including review and ban reasons. The shop name and phone are the
 * account's {@code fullName} / {@code phoneNumber}; {@code logoUrl} and {@code avatarUrl} are the same time-limited
 * read URL of the account's avatar, never the object key.
 */
public record SellerSelfProfile(UUID sellerId, String description, String logoUrl, String avatarUrl,
		SellerApprovalStatus approvalStatus, SellerStatus status, String rejectionReason, String banReason,
		Instant registeredAt, Instant reviewedAt, UUID latestApplicationId) {

	static SellerSelfProfile of(Seller seller, String avatarUrl, UUID latestApplicationId) {
		return new SellerSelfProfile(seller.getSellerId(), seller.getDescription(), avatarUrl, avatarUrl,
				seller.getApprovalStatus(), seller.getStatus(), seller.getRejectionReason(), seller.getBanReason(),
				seller.getRegisteredAt(), seller.getReviewedAt(), latestApplicationId);
	}

}
