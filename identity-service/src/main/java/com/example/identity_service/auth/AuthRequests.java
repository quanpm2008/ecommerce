package com.example.identity_service.auth;

import com.example.identity_service.user.AccountType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

	/** {@code fullName} is the shop name and {@code phoneNumber} its contact phone (DB §3.1.3). */
	public record RegisterSeller(@NotBlank @Email @Size(max = 254) String email,
			@NotBlank @Size(max = 150) String fullName, @NotBlank String phoneNumber, @NotBlank String password,
			@NotBlank String confirmPassword) {
	}

	public record SellerReapplicationChallenge(@NotBlank String email) {
	}

	/** A new application after REJECTED, proven by the emailed token (API §6.1 #31). */
	public record SellerReapplication(@NotBlank String verificationToken, @NotBlank @Size(max = 150) String fullName,
			@NotBlank String phoneNumber, @Size(max = 2000) String description) {
	}

	/** {@code accountType} is the web app's channel: an account of another type gets the generic 401. */
	public record Login(@NotBlank String email, @NotBlank String password, @NotNull AccountType accountType) {
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
