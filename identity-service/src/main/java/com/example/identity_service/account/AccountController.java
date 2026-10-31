package com.example.identity_service.account;

import com.example.identity_service.auth.AuthRequests;
import com.example.identity_service.auth.PasswordService;
import com.example.identity_service.common.security.Caller;
import com.example.identity_service.common.web.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Self-service on the caller's own account ({@code /users/me}); the user is always the token's {@code sub}.
 */
@RestController
@RequestMapping("/api/v1/identity/users/me")
@RequiredArgsConstructor
public class AccountController {

	private final PasswordService passwords;

	private final AccountService accounts;

	private final SellerAccountService sellerAccounts;

	@GetMapping
	@PreAuthorize("hasAuthority('account:read:own')")
	ApiResponse<UserSelfProfile> me(@AuthenticationPrincipal Jwt jwt) {
		return ApiResponse.of(accounts.me(Caller.of(jwt)));
	}

	@PatchMapping
	@PreAuthorize("hasAuthority('account:update:own')")
	ApiResponse<UserSelfProfile> update(@AuthenticationPrincipal Jwt jwt, @RequestBody Map<String, Object> body) {
		return ApiResponse.of(accounts.update(Caller.of(jwt), body));
	}

	/** Baseline policy: any ACTIVE user may add their own Customer profile. Body is ignored. */
	@PostMapping("/customer-profile")
	@PreAuthorize("isAuthenticated()")
	ResponseEntity<ApiResponse<UserSelfProfile>> createCustomerProfile(@AuthenticationPrincipal Jwt jwt) {
		AccountService.Result result = accounts.createCustomerProfile(Caller.of(jwt));
		return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
				.body(ApiResponse.of(result.profile()));
	}

	@PostMapping("/seller-application")
	@PreAuthorize("hasAuthority('seller:application:own')")
	ResponseEntity<ApiResponse<SellerSelfProfile>> submitSellerApplication(@AuthenticationPrincipal Jwt jwt,
			@RequestBody Map<String, Object> body) {
		SellerAccountService.Result result = sellerAccounts.submit(Caller.of(jwt), body);
		return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
				.body(ApiResponse.of(result.profile()));
	}

	@GetMapping("/seller-application")
	@PreAuthorize("hasAuthority('seller:application:own')")
	ApiResponse<SellerSelfProfile> sellerApplication(@AuthenticationPrincipal Jwt jwt) {
		return ApiResponse.of(sellerAccounts.application(Caller.of(jwt)).orElse(null));
	}

	@PutMapping("/password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("hasAuthority('account:update:own')")
	void changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AuthRequests.ChangePassword body) {
		passwords.change(Caller.of(jwt), body);
	}

}
