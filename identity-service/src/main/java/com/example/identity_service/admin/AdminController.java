package com.example.identity_service.admin;

import com.example.identity_service.common.security.Caller;
import com.example.identity_service.common.web.ApiResponse;
import com.example.identity_service.customer.CustomerStatus;
import com.example.identity_service.seller.SellerApprovalStatus;
import com.example.identity_service.seller.SellerStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Admin API of Identity (API §6.1, UC-E01, E03, E06). Each endpoint checks its own permission; the acting admin is
 * always the token's {@code sub}.
 */
@RestController
@RequestMapping("/api/v1/identity/admin")
@RequiredArgsConstructor
public class AdminController {

	private final AdminDirectory directory;

	private final SellerReviewService reviews;

	private final ModerationService moderation;

	@GetMapping("/customers")
	@PreAuthorize("hasAuthority('operations:read:any')")
	ApiResponse<List<AdminViews.CustomerAdminView>> customers(@RequestParam(required = false) String q,
			@RequestParam(required = false) CustomerStatus status, Pageable pageable) {
		return ApiResponse.page(directory.customers(q, status, pageable));
	}

	@GetMapping("/customers/{customerId}")
	@PreAuthorize("hasAuthority('operations:read:any')")
	ApiResponse<AdminViews.CustomerAdminView> customer(@PathVariable UUID customerId) {
		return ApiResponse.of(directory.customer(customerId));
	}

	@GetMapping("/sellers")
	@PreAuthorize("hasAuthority('operations:read:any')")
	ApiResponse<List<AdminViews.SellerAdminView>> sellers(@RequestParam(required = false) String q,
			@RequestParam(required = false) SellerApprovalStatus approvalStatus,
			@RequestParam(required = false) SellerStatus status, Pageable pageable) {
		return ApiResponse.page(directory.sellers(q, approvalStatus, status, pageable));
	}

	@GetMapping("/sellers/{sellerId}")
	@PreAuthorize("hasAuthority('operations:read:any')")
	ApiResponse<AdminViews.SellerAdminView> seller(@PathVariable UUID sellerId) {
		return ApiResponse.of(directory.seller(sellerId));
	}

	@PostMapping("/sellers/{sellerId}/approve")
	@PreAuthorize("hasAuthority('seller:approve')")
	ApiResponse<AdminViews.SellerAdminView> approve(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sellerId) {
		return ApiResponse.of(reviews.approve(Caller.of(jwt).userId(), sellerId));
	}

	@PostMapping("/sellers/{sellerId}/reject")
	@PreAuthorize("hasAuthority('seller:approve')")
	ApiResponse<AdminViews.SellerAdminView> reject(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sellerId,
			@Valid @RequestBody RejectRequest body) {
		return ApiResponse.of(reviews.reject(Caller.of(jwt).userId(), sellerId, body.reason().strip()));
	}

	@PostMapping("/ban/{userId}")
	@PreAuthorize("hasAuthority('account:moderate')")
	ApiResponse<ModerationService.BanResult> ban(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId,
			@Valid @RequestBody BanRequest body) {
		return ApiResponse.of(moderation.ban(Caller.of(jwt).userId(), userId, body.scope(), body.reason()));
	}

	@PostMapping("/unban/{userId}")
	@PreAuthorize("hasAuthority('account:moderate')")
	ApiResponse<ModerationService.UnbanResult> unban(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId,
			@Valid @RequestBody UnbanRequest body) {
		return ApiResponse.of(moderation.unban(Caller.of(jwt).userId(), userId, body.scope(), body.reason()));
	}

	record RejectRequest(@NotBlank @Size(max = 1000) String reason) {
	}

	record BanRequest(@NotNull ModerationScope scope, @NotBlank @Size(max = 1000) String reason) {
	}

	record UnbanRequest(@NotNull ModerationScope scope, @Size(max = 1000) String reason) {
	}

}
