package com.example.identity_service.entity;

/**
 * Selling restriction; a BANNED seller keeps the SELLER role but cannot sell or withdraw.
 */
public enum SellerStatus {
	ACTIVE, BANNED
}
