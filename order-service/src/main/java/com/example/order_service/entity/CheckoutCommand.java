package com.example.order_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "checkout_outbox")
public class CheckoutCommand {
	@Id
	@Column(name = "command_id", nullable = false)
	private UUID commandId;

	@Column(name = "checkout_session_id", nullable = false)
	private UUID checkoutSessionId;

	@Column(name = "command_type", nullable = false, length = 40)
	private String commandType;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected CheckoutCommand() {
	}

	public CheckoutCommand(UUID commandId, UUID checkoutSessionId, String commandType, Instant expiresAt,
			Instant createdAt) {
		this.commandId = commandId;
		this.checkoutSessionId = checkoutSessionId;
		this.commandType = commandType;
		this.expiresAt = expiresAt;
		this.createdAt = createdAt;
	}

	public UUID getCommandId() {
		return commandId;
	}

	public UUID getCheckoutSessionId() {
		return checkoutSessionId;
	}

	public String getCommandType() {
		return commandType;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
