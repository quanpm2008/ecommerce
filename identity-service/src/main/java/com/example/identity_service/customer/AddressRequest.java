package com.example.identity_service.customer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of create and update (the same fields). Region codes are checked against the region source.
 */
public record AddressRequest(@NotBlank @Size(max = 150) String recipientName, @NotBlank String recipientPhone,
		@NotBlank @Size(max = 32) String provinceCode, @NotBlank @Size(max = 32) String wardCode,
		@NotBlank @Size(max = 300) String addressDetail) {
}
