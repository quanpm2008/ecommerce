package com.example.identity_service.dto;

import com.example.identity_service.entity.Seller;
import java.util.UUID;

/**
 * What anyone may see of a shop (API §6.1 SellerPublic): the shop name ({@code users.full_name}), description and logo
 * (the account's avatar); no email, phone, approval or ban data.
 */
public record SellerPublic(UUID sellerId, String sellerName, String description, String logoUrl) {

	public static SellerPublic of(Seller seller, String logoUrl) {
		return new SellerPublic(seller.getSellerId(), seller.getUser().getFullName(), seller.getDescription(), logoUrl);
	}

}
