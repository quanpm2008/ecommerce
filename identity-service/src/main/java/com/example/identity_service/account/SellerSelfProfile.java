package com.example.identity_service.account;

import com.example.identity_service.seller.Seller;
import com.example.identity_service.seller.SellerApprovalStatus;
import com.example.identity_service.seller.SellerStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * API {@code SellerSelfProfile}: the owner's view, including review and ban reasons. {@code logoUrl} stays null
 * until logo upload exists (step 4).
 */
public record SellerSelfProfile(UUID sellerId, String sellerName, String description, String logoUrl,
		String contactPhone, SellerApprovalStatus approvalStatus, SellerStatus status, String rejectionReason,
		String banReason, Instant registeredAt, Instant reviewedAt) {

	static SellerSelfProfile of(Seller seller) {
		return new SellerSelfProfile(seller.getSellerId(), seller.getSellerName(), seller.getDescription(), null,
				seller.getContactPhone(), seller.getApprovalStatus(), seller.getStatus(), seller.getRejectionReason(),
				seller.getBanReason(), seller.getRegisteredAt(), seller.getReviewedAt());
	}

}
