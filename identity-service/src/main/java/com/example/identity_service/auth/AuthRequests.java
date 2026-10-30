package com.example.identity_service.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request bodies of the auth endpoints (API §6.1). Password rules (G-07) and phone format are checked in the
 * services so they can return their own error codes.
 */
public final class AuthRequests {

	private AuthRequests() {
	}

	public record Register(@NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 150) String fullName,
			@NotBlank String phoneNumber, @NotBlank String password, @NotBlank String confirmPassword) {
	}

	public record RegisterSeller(@NotBlank @Email @Size(max = 254) String email,
			@NotBlank @Size(max = 160) String sellerName, @NotBlank @Size(max = 150) String fullName,
			@NotBlank String contactPhone, @NotBlank String password, @NotBlank String confirmPassword) {
	}

	public record Login(@NotBlank String email, @NotBlank String password) {
	}

	/** Body of refresh and logout. */
	public record RefreshToken(@NotBlank String refreshToken) {
	}

	public record ForgotPassword(@NotBlank String email) {
	}

	public record ResetPassword(@NotBlank String token, @NotBlank String newPassword,
			@NotBlank String confirmPassword) {
	}

	public record ChangePassword(@NotBlank String currentPassword, @NotBlank String newPassword,
			@NotBlank String confirmPassword) {
	}

}
