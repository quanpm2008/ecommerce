package com.example.identity_service.controller;

import com.example.identity_service.dto.AuthRequests;
import com.example.identity_service.dto.AuthTokens;
import com.example.identity_service.dto.GoogleAuthTokens;
import com.example.identity_service.dto.SellerRegistration;
import com.example.identity_service.service.AuthService;
import com.example.identity_service.service.PasswordService;
import com.example.identity_service.service.SellerReapplicationService;
import com.example.identity_service.dto.UserSelfProfile;
import com.example.identity_service.config.ErrorCodes;
import com.example.identity_service.security.Caller;
import com.example.identity_service.dto.ApiResponse;
import com.example.identity_service.config.RateLimitProperties;
import com.example.identity_service.service.RateLimiter;
import com.example.identity_service.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.example.identity_service.exception.ApiException;
import com.example.identity_service.exception.ErrorCode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static com.example.identity_service.exception.ErrorCode.ACCOUNT_DISABLED;
import static com.example.identity_service.exception.ErrorCode.EMAIL_ALREADY_USED;
import static com.example.identity_service.exception.ErrorCode.GOOGLE_AUTH_FAILED;
import static com.example.identity_service.exception.ErrorCode.INVALID_CREDENTIALS;
import static com.example.identity_service.exception.ErrorCode.INVALID_REFRESH_TOKEN;
import static com.example.identity_service.exception.ErrorCode.INVALID_STATE_TRANSITION;
import static com.example.identity_service.exception.ErrorCode.PASSWORD_POLICY_VIOLATION;
import static com.example.identity_service.exception.ErrorCode.PHONE_ALREADY_USED;
import static com.example.identity_service.exception.ErrorCode.RATE_LIMITED;
import static com.example.identity_service.exception.ErrorCode.RESET_TOKEN_INVALID;
import static com.example.identity_service.exception.ErrorCode.SELLER_NAME_ALREADY_USED;
import static com.example.identity_service.exception.ErrorCode.SELLER_PENDING_APPROVAL;
import static com.example.identity_service.exception.ErrorCode.SELLER_REJECTED;
import static com.example.identity_service.exception.ErrorCode.SERVICE_UNAVAILABLE;
import static com.example.identity_service.exception.ErrorCode.WRONG_AUTH_METHOD;

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

	private final RefreshCookies cookies;

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
			+ "(CUSTOMER, SELLER or ADMIN). Returns an access token (15 min) and the current profile; the refresh token is "
			+ "set as the HttpOnly `refresh_token` cookie (Path /api/v1/identity/auth), never in the body. "
			+ "Unknown email, wrong password and an account of another type all give the same error. A seller signs in "
			+ "only once approved. Limited per client IP; failed attempts never lock the account (P-18).")
	@ErrorCodes({ INVALID_CREDENTIALS, WRONG_AUTH_METHOD, SELLER_PENDING_APPROVAL, SELLER_REJECTED, ACCOUNT_DISABLED,
			RATE_LIMITED })
	@PostMapping("/login")
	ResponseEntity<ApiResponse<AuthTokens>> login(@Valid @RequestBody AuthRequests.Login body,
			HttpServletRequest request) {
		rateLimiter.check("login", request.getRemoteAddr(), limits.login());
		return withCookie(auth.login(body));
	}

	@Operation(summary = "Sign in with Google (customers)", description = "Takes the `idToken` from Google Identity "
			+ "Services. A known Google account signs in; a new one becomes a CUSTOMER account (no password, no phone: "
			+ "the customer adds a phone before checkout) and `isNewAccount` is true. An email already registered with a "
			+ "password is never linked (409, `details[0].authMethod`). Same session, ban and cookie rules as login; "
			+ "limited per client IP together with login.")
	@ErrorCodes({ GOOGLE_AUTH_FAILED, WRONG_AUTH_METHOD, ACCOUNT_DISABLED, RATE_LIMITED, SERVICE_UNAVAILABLE })
	@PostMapping("/google")
	ResponseEntity<ApiResponse<GoogleAuthTokens>> google(@Valid @RequestBody AuthRequests.GoogleSignIn body,
			HttpServletRequest request) {
		rateLimiter.check("login", request.getRemoteAddr(), limits.login());
		GoogleAuthTokens.Issued issued = auth.google(body.idToken());
		return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookies.issue(issued.refreshToken()).toString())
				.body(ApiResponse.of(issued.body()));
	}

	@Operation(summary = "Refresh tokens", description = "Reads only the `refresh_token` cookie (no body) and needs an "
			+ "`Origin` the server trusts (403 otherwise). Rotates the token: the response sets the new cookie. Reusing "
			+ "an old refresh token revokes the whole session.")
	@ErrorCodes({ INVALID_REFRESH_TOKEN, SELLER_PENDING_APPROVAL, SELLER_REJECTED })
	@PostMapping("/refresh")
	ResponseEntity<ApiResponse<AuthTokens>> refresh(
			@CookieValue(name = RefreshCookies.NAME, required = false) String refreshToken, HttpServletRequest request) {
		cookies.requireTrustedOrigin(request);
		if (refreshToken == null || refreshToken.isBlank()) {
			throw new ApiException(ErrorCode.INVALID_REFRESH_TOKEN);
		}
		return withCookie(auth.refresh(refreshToken));
	}

	@Operation(summary = "Sign out", description = "Revokes the session of the `refresh_token` cookie if it belongs to "
			+ "the caller, then clears the cookie (same name and path, Max-Age 0). Needs a trusted `Origin`. Anything "
			+ "else is a silent 204.")
	@PreAuthorize("isAuthenticated()")
	@PostMapping("/logout")
	ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt,
			@CookieValue(name = RefreshCookies.NAME, required = false) String refreshToken, HttpServletRequest request) {
		cookies.requireTrustedOrigin(request);
		if (refreshToken != null && !refreshToken.isBlank()) {
			auth.logout(Caller.of(jwt), refreshToken);
		}
		return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear().toString()).build();
	}

	private ResponseEntity<ApiResponse<AuthTokens>> withCookie(AuthTokens.Issued issued) {
		return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookies.issue(issued.refreshToken()).toString())
				.body(ApiResponse.of(issued.body()));
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
