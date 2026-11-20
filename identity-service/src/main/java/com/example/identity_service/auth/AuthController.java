package com.example.identity_service.auth;

import com.example.identity_service.account.UserSelfProfile;
import com.example.identity_service.common.openapi.ErrorCodes;
import com.example.identity_service.common.security.Caller;
import com.example.identity_service.common.web.ApiResponse;
import com.example.identity_service.common.web.RateLimitProperties;
import com.example.identity_service.common.web.RateLimiter;
import com.example.identity_service.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static com.example.identity_service.common.error.ErrorCode.ACCOUNT_DISABLED;
import static com.example.identity_service.common.error.ErrorCode.EMAIL_ALREADY_USED;
import static com.example.identity_service.common.error.ErrorCode.INVALID_CREDENTIALS;
import static com.example.identity_service.common.error.ErrorCode.INVALID_REFRESH_TOKEN;
import static com.example.identity_service.common.error.ErrorCode.INVALID_STATE_TRANSITION;
import static com.example.identity_service.common.error.ErrorCode.PASSWORD_POLICY_VIOLATION;
import static com.example.identity_service.common.error.ErrorCode.PHONE_ALREADY_USED;
import static com.example.identity_service.common.error.ErrorCode.RATE_LIMITED;
import static com.example.identity_service.common.error.ErrorCode.RESET_TOKEN_INVALID;
import static com.example.identity_service.common.error.ErrorCode.SELLER_NAME_ALREADY_USED;
import static com.example.identity_service.common.error.ErrorCode.SELLER_PENDING_APPROVAL;
import static com.example.identity_service.common.error.ErrorCode.SELLER_REJECTED;
import static com.example.identity_service.common.error.ErrorCode.WRONG_AUTH_METHOD;

/**
 * Public auth endpoints, plus logout which needs the caller's access token. Rate limits are per client IP, as seen
 * through the gateway's forwarded headers.
 */
@Tag(name = "Auth", description = "Register, sign in, refresh, sign out and reset passwords (UC-A01–A05, A07)")
@RestController
@RequestMapping(AuthController.PATH)
@RequiredArgsConstructor
public class AuthController {

	public static final String PATH = "/api/v1/identity/auth";

	static final String FORGOT_PASSWORD_MESSAGE = "If the email is valid, password reset instructions have been sent.";

	static final String REAPPLICATION_MESSAGE = "If the email belongs to a rejected seller, a link to apply again has "
			+ "been sent.";

	private final AuthService auth;

	private final PasswordService passwords;

	private final SellerReapplicationService reapplications;

	private final RateLimiter rateLimiter;

	private final RateLimitProperties limits;

	@Operation(summary = "Register a customer", description = "Creates the user, a Customer profile and the CUSTOMER "
			+ "role. Returns the profile without tokens: sign in next. Rate-limited per IP.")
	@ErrorCodes({ EMAIL_ALREADY_USED, PHONE_ALREADY_USED, PASSWORD_POLICY_VIOLATION, RATE_LIMITED })
	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	ApiResponse<UserSelfProfile> register(@Valid @RequestBody AuthRequests.Register body, HttpServletRequest request) {
		rateLimiter.check("register", request.getRemoteAddr(), limits.register());
		return ApiResponse.of(auth.register(body));
	}

	@Operation(summary = "Register a seller", description = "Creates a SELLER account (its own email), a PENDING seller "
			+ "profile and the first application. `fullName` is the shop name (unique among pending and approved "
			+ "sellers) and `phoneNumber` its contact phone. No role, session or wallet until an admin approves; the "
			+ "seller cannot sign in before. Rate-limited per IP.")
	@ErrorCodes({ EMAIL_ALREADY_USED, PHONE_ALREADY_USED, SELLER_NAME_ALREADY_USED, PASSWORD_POLICY_VIOLATION,
			RATE_LIMITED })
	@PostMapping("/register-seller")
	@ResponseStatus(HttpStatus.CREATED)
	ApiResponse<SellerRegistration> registerSeller(@Valid @RequestBody AuthRequests.RegisterSeller body,
			HttpServletRequest request) {
		rateLimiter.check("register", request.getRemoteAddr(), limits.register());
		return ApiResponse.of(auth.registerSeller(body));
	}

	@Operation(summary = "Ask to apply again as a seller", description = "For a REJECTED seller: emails a single-use "
			+ "link (30 minutes) to the account's address. Always the same 202, whatever the email. Rate-limited per IP "
			+ "and per email.")
	@ErrorCodes(RATE_LIMITED)
	@PostMapping("/seller-reapplication/challenge")
	@ResponseStatus(HttpStatus.ACCEPTED)
	ApiResponse<Map<String, String>> sellerReapplicationChallenge(
			@Valid @RequestBody AuthRequests.SellerReapplicationChallenge body, HttpServletRequest request) {
		rateLimiter.check("seller-reapplication", request.getRemoteAddr(), limits.sellerReapplication());
		rateLimiter.check("seller-reapplication-email", User.normalizeEmail(body.email()),
				limits.sellerReapplication());
		reapplications.challenge(body.email());
		return ApiResponse.of(Map.of("message", REAPPLICATION_MESSAGE));
	}

	@Operation(summary = "Apply again as a seller", description = "With the emailed `verificationToken` (single use): "
			+ "a new PENDING application on the same seller, with the new shop name and phone. The rejected application "
			+ "stays as it was. No session: the seller signs in once approved. A seller that is not REJECTED → 409. "
			+ "Rate-limited per IP.")
	@ErrorCodes({ INVALID_STATE_TRANSITION, PHONE_ALREADY_USED, SELLER_NAME_ALREADY_USED, RATE_LIMITED })
	@PostMapping("/seller-reapplication")
	@ResponseStatus(HttpStatus.CREATED)
	ApiResponse<SellerReapplicationService.Result> sellerReapplication(
			@Valid @RequestBody AuthRequests.SellerReapplication body, HttpServletRequest request) {
		rateLimiter.check("seller-reapplication", request.getRemoteAddr(), limits.sellerReapplication());
		return ApiResponse.of(reapplications.submit(body));
	}

	@Operation(summary = "Sign in", description = "Signs in to one account: `accountType` is the web app's channel "
			+ "(CUSTOMER, SELLER or ADMIN). Returns an access token (15 min), a refresh token and the current profile. "
			+ "Unknown email, wrong password and an account of another type all give the same error. A seller signs in "
			+ "only once approved.")
	@ErrorCodes({ INVALID_CREDENTIALS, WRONG_AUTH_METHOD, SELLER_PENDING_APPROVAL, SELLER_REJECTED, ACCOUNT_DISABLED })
	@PostMapping("/login")
	ApiResponse<AuthTokens> login(@Valid @RequestBody AuthRequests.Login body) {
		return ApiResponse.of(auth.login(body));
	}

	@Operation(summary = "Refresh tokens", description = "Rotates the refresh token: use the new one next time. "
			+ "Reusing an old refresh token revokes the whole session.")
	@ErrorCodes({ INVALID_REFRESH_TOKEN, SELLER_PENDING_APPROVAL, SELLER_REJECTED })
	@PostMapping("/refresh")
	ApiResponse<AuthTokens> refresh(@Valid @RequestBody AuthRequests.RefreshToken body) {
		return ApiResponse.of(auth.refresh(body.refreshToken()));
	}

	@Operation(summary = "Sign out", description = "Revokes the session of the given refresh token if it belongs to "
			+ "the caller; anything else is a silent 204.")
	@PreAuthorize("isAuthenticated()")
	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void logout(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AuthRequests.RefreshToken body) {
		auth.logout(Caller.of(jwt), body.refreshToken());
	}

	@Operation(summary = "Request a password reset email", description = "Always the same 202, whether or not the "
			+ "email exists. Rate-limited per IP.")
	@ErrorCodes(RATE_LIMITED)
	@PostMapping("/password/forgot")
	@ResponseStatus(HttpStatus.ACCEPTED)
	ApiResponse<Map<String, String>> forgotPassword(@Valid @RequestBody AuthRequests.ForgotPassword body,
			HttpServletRequest request) {
		rateLimiter.check("forgot-password", request.getRemoteAddr(), limits.forgotPassword());
		passwords.forgot(body.email());
		return ApiResponse.of(Map.of("message", FORGOT_PASSWORD_MESSAGE));
	}

	@Operation(summary = "Reset the password with the emailed token", description = "The token works once, for 30 "
			+ "minutes. Signs out every session.")
	@ErrorCodes({ RESET_TOKEN_INVALID, PASSWORD_POLICY_VIOLATION })
	@PostMapping("/password/reset")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void resetPassword(@Valid @RequestBody AuthRequests.ResetPassword body) {
		passwords.reset(body);
	}

}
