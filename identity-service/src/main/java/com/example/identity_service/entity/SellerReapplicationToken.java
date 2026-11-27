package com.example.identity_service.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Single-use proof that a REJECTED seller owns the account's email (API §6.1 #30/#31). Only the hash is stored.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "seller_reapplication_tokens")
public class SellerReapplicationToken {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID tokenId;

	private UUID sellerId;

	private String tokenHash;

	private Instant expiresAt;

	private Instant usedAt;

	@CreationTimestamp
	private Instant createdAt;

	public SellerReapplicationToken(UUID sellerId, String tokenHash, Instant expiresAt) {
		this.sellerId = sellerId;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
	}

	public boolean isUsable(Instant now) {
		return usedAt == null && now.isBefore(expiresAt);
	}

	public void markUsed(Instant now) {
		usedAt = now;
	}

}
