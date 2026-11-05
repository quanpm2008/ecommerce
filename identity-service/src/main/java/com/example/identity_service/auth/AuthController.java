package com.example.identity_service.auth;

import com.example.identity_service.account.UserSelfProfile;
import com.example.identity_service.common.openapi.ErrorCodes;
import com.example.identity_service.common.security.Caller;
import com.example.identity_service.common.web.ApiResponse;
import com.example.identity_service.common.web.RateLimitProperties;
import com.example.identity_service.common.web.RateLimiter;
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
import static com.example.identity_service.common.error.ErrorCode.PASSWORD_POLICY_VIOLATION;
import static com.example.identity_service.common.error.ErrorCode.PHONE_ALREADY_USED;
import static com.example.identity_service.common.error.ErrorCode.RATE_LIMITED;
import static com.example.identity_service.common.error.ErrorCode.RESET_TOKEN_INVALID;
import static com.example.identity_service.common.error.ErrorCode.SELLER_NAME_ALREADY_USED;
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

	private final AuthService auth;

	private final PasswordService passwords;

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

	@Operation(summary = "Register a seller", description = "Creates the user and a PENDING seller application, with "
			+ "no role until an admin approves it. Rate-limited per IP.")
	@ErrorCodes({ EMAIL_ALREADY_USED, PHONE_ALREADY_USED, SELLER_NAME_ALREADY_USED, PASSWORD_POLICY_VIOLATION,
			RATE_LIMITED })
	@PostMapping("/register-seller")
	@ResponseStatus(HttpStatus.CREATED)
	ApiResponse<UserSelfProfile> registerSeller(@Valid @RequestBody AuthRequests.RegisterSeller body,
			HttpServletRequest request) {
		rateLimiter.check("register", request.getRemoteAddr(), limits.register());
		return ApiResponse.of(auth.registerSeller(body));
	}

	@Operation(summary = "Sign in", description = "One sign-in for every role. Returns an access token (15 min), a "
			+ "refresh token and the current profile. Unknown email and wrong password give the same error.")
	@ErrorCodes({ INVALID_CREDENTIALS, WRONG_AUTH_METHOD, ACCOUNT_DISABLED })
	@PostMapping("/login")
	ApiResponse<AuthTokens> login(@Valid @RequestBody AuthRequests.Login body) {
		return ApiResponse.of(auth.login(body));
	}

	@Operation(summary = "Refresh tokens", description = "Rotates the refresh token: use the new one next time. "
			+ "Reusing an old refresh token revokes the whole session.")
	@ErrorCodes(INVALID_REFRESH_TOKEN)
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
