package com.example.identity_service.auth;

import com.example.identity_service.account.UserSelfProfile;
import com.example.identity_service.common.security.Caller;
import com.example.identity_service.common.web.ApiResponse;
import com.example.identity_service.common.web.RateLimitProperties;
import com.example.identity_service.common.web.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public auth endpoints, plus logout which needs the caller's access token. Rate limits are per client IP, as seen
 * through the gateway's forwarded headers.
 */
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

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	ApiResponse<UserSelfProfile> register(@Valid @RequestBody AuthRequests.Register body, HttpServletRequest request) {
		rateLimiter.check("register", request.getRemoteAddr(), limits.register());
		return ApiResponse.of(auth.register(body));
	}

	@PostMapping("/register-seller")
	@ResponseStatus(HttpStatus.CREATED)
	ApiResponse<UserSelfProfile> registerSeller(@Valid @RequestBody AuthRequests.RegisterSeller body,
			HttpServletRequest request) {
		rateLimiter.check("register", request.getRemoteAddr(), limits.register());
		return ApiResponse.of(auth.registerSeller(body));
	}

	@PostMapping("/login")
	ApiResponse<AuthTokens> login(@Valid @RequestBody AuthRequests.Login body) {
		return ApiResponse.of(auth.login(body));
	}

	@PostMapping("/refresh")
	ApiResponse<AuthTokens> refresh(@Valid @RequestBody AuthRequests.RefreshToken body) {
		return ApiResponse.of(auth.refresh(body.refreshToken()));
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void logout(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AuthRequests.RefreshToken body) {
		auth.logout(Caller.of(jwt), body.refreshToken());
	}

	@PostMapping("/password/forgot")
	@ResponseStatus(HttpStatus.ACCEPTED)
	ApiResponse<Map<String, String>> forgotPassword(@Valid @RequestBody AuthRequests.ForgotPassword body,
			HttpServletRequest request) {
		rateLimiter.check("forgot-password", request.getRemoteAddr(), limits.forgotPassword());
		passwords.forgot(body.email());
		return ApiResponse.of(Map.of("message", FORGOT_PASSWORD_MESSAGE));
	}

	@PostMapping("/password/reset")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void resetPassword(@Valid @RequestBody AuthRequests.ResetPassword body) {
		passwords.reset(body);
	}

}
