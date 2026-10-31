package com.example.identity_service.seller;

import java.util.UUID;

/**
 * What anyone may see of a shop (API §5 SellerPublic): no email, phone, approval or ban data.
 */
public record SellerPublic(UUID sellerId, String sellerName, String description, String logoUrl) {

	static SellerPublic of(Seller seller, String logoUrl) {
		return new SellerPublic(seller.getSellerId(), seller.getSellerName(), seller.getDescription(), logoUrl);
	}

}
