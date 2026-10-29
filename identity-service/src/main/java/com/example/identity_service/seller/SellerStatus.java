package com.example.identity_service.seller;

/**
 * Selling restriction; a BANNED seller keeps the SELLER role but cannot sell or withdraw.
 */
public enum SellerStatus {
	ACTIVE, BANNED
}
