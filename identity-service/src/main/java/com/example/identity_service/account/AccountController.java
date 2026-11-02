package com.example.identity_service.account;

import com.example.identity_service.auth.AuthRequests;
import com.example.identity_service.auth.PasswordService;
import com.example.identity_service.common.openapi.ErrorCodes;
import com.example.identity_service.common.security.Caller;
import com.example.identity_service.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import static com.example.identity_service.common.error.ErrorCode.INVALID_STATE_TRANSITION;
import static com.example.identity_service.common.error.ErrorCode.PASSWORD_INCORRECT;
import static com.example.identity_service.common.error.ErrorCode.PASSWORD_POLICY_VIOLATION;
import static com.example.identity_service.common.error.ErrorCode.PASSWORD_SAME_AS_OLD;
import static com.example.identity_service.common.error.ErrorCode.PHONE_ALREADY_USED;
import static com.example.identity_service.common.error.ErrorCode.SELLER_NAME_ALREADY_USED;

/**
 * Self-service on the caller's own account ({@code /users/me}); the user is always the token's {@code sub}.
 */
@Tag(name = "Account", description = "The caller's own account, customer profile and seller application (UC-A01, A06)")
@RestController
@RequestMapping("/api/v1/identity/users/me")
@RequiredArgsConstructor
public class AccountController {

	private final PasswordService passwords;

	private final AccountService accounts;

	private final SellerAccountService sellerAccounts;

	@Operation(summary = "My profile", description = "Current roles, permissions, customer and seller profiles.")
	@GetMapping
	@PreAuthorize("hasAuthority('account:read:own')")
	ApiResponse<UserSelfProfile> me(@AuthenticationPrincipal Jwt jwt) {
		return ApiResponse.of(accounts.me(Caller.of(jwt)));
	}

	@Operation(summary = "Update my name or phone", description = "Only the listed fields; any other field is a 400 "
			+ "with issue UNKNOWN_FIELD and nothing changes.")
	@ErrorCodes(PHONE_ALREADY_USED)
	@io.swagger.v3.oas.annotations.parameters.RequestBody(
			content = @Content(schema = @Schema(implementation = UpdateMeRequest.class)))
	@PatchMapping
	@PreAuthorize("hasAuthority('account:update:own')")
	ApiResponse<UserSelfProfile> update(@AuthenticationPrincipal Jwt jwt, @RequestBody Map<String, Object> body) {
		return ApiResponse.of(accounts.update(Caller.of(jwt), body));
	}

	/** Baseline policy: any ACTIVE user may add their own Customer profile. Body is ignored. */
	@Operation(summary = "Add a customer profile to my account", description = "201 when created (grants CUSTOMER, "
			+ "so refresh the token afterwards); 200 with the unchanged profile if it already exists. 403 if an admin "
			+ "revoked CUSTOMER.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Created",
			useReturnTypeSchema = true)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Already existed",
			useReturnTypeSchema = true)
	@PostMapping("/customer-profile")
	@PreAuthorize("isAuthenticated()")
	ResponseEntity<ApiResponse<UserSelfProfile>> createCustomerProfile(@AuthenticationPrincipal Jwt jwt) {
		AccountService.Result result = accounts.createCustomerProfile(Caller.of(jwt));
		return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
				.body(ApiResponse.of(result.profile()));
	}

	@Operation(summary = "Apply to sell", description = "201 for a first application, 200 when resubmitting after "
			+ "REJECTED (same seller id). Stays PENDING with no SELLER role until an admin approves.")
	@ErrorCodes({ INVALID_STATE_TRANSITION, SELLER_NAME_ALREADY_USED, PHONE_ALREADY_USED })
	@io.swagger.v3.oas.annotations.parameters.RequestBody(
			content = @Content(schema = @Schema(implementation = SellerApplicationRequest.class)))
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Submitted",
			useReturnTypeSchema = true)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Resubmitted",
			useReturnTypeSchema = true)
	@PostMapping("/seller-application")
	@PreAuthorize("hasAuthority('seller:application:own')")
	ResponseEntity<ApiResponse<SellerSelfProfile>> submitSellerApplication(@AuthenticationPrincipal Jwt jwt,
			@RequestBody Map<String, Object> body) {
		SellerAccountService.Result result = sellerAccounts.submit(Caller.of(jwt), body);
		return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
				.body(ApiResponse.of(result.profile()));
	}

	@Operation(summary = "My seller application", description = "`data` is null when I never applied.")
	@GetMapping("/seller-application")
	@PreAuthorize("hasAuthority('seller:application:own')")
	ApiResponse<SellerSelfProfile> sellerApplication(@AuthenticationPrincipal Jwt jwt) {
		return ApiResponse.of(sellerAccounts.application(Caller.of(jwt)).orElse(null));
	}

	@Operation(summary = "Change my password", description = "Email accounts only. Signs out every session, including "
			+ "this one.")
	@ErrorCodes({ PASSWORD_INCORRECT, PASSWORD_SAME_AS_OLD, PASSWORD_POLICY_VIOLATION })
	@PutMapping("/password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("hasAuthority('account:update:own')")
	void changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AuthRequests.ChangePassword body) {
		passwords.change(Caller.of(jwt), body);
	}

	@Schema(description = "Send only the fields to change; any other field is rejected.")
	record UpdateMeRequest(@Schema(maxLength = 150, example = "Le Thi C") String fullName,
			@Schema(description = "Vietnamese mobile, 0xxxxxxxxx or +84xxxxxxxxx", example = "0912345678")
			String phoneNumber) {
	}

	@Schema(description = "Exactly these fields; userId, email, role or status are rejected.")
	record SellerApplicationRequest(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 160, example = "Cua Hang An")
			String sellerName,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "0912345678") String contactPhone,
			@Schema(maxLength = 2000) String description) {
	}

}
