package com.example.identity_service.admin;

import com.example.identity_service.common.openapi.ErrorCodes;
import com.example.identity_service.common.security.Caller;
import com.example.identity_service.common.web.ApiResponse;
import com.example.identity_service.customer.CustomerStatus;
import com.example.identity_service.seller.SellerApprovalStatus;
import com.example.identity_service.seller.SellerStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import static com.example.identity_service.common.error.ErrorCode.ALREADY_PROCESSED;
import static com.example.identity_service.common.error.ErrorCode.INVALID_STATE_TRANSITION;
import static com.example.identity_service.common.error.ErrorCode.NOT_FOUND;
import static com.example.identity_service.common.error.ErrorCode.SELF_APPROVAL_FORBIDDEN;
import static com.example.identity_service.common.error.ErrorCode.SERVICE_UNAVAILABLE;

/**
 * Admin API of Identity (API §6.1, UC-E01, E03, E06). Each endpoint checks its own permission; the acting admin is
 * always the token's {@code sub}.
 */
@Tag(name = "Admin", description = "Customer and seller lists, seller review and scoped bans (UC-E01, E03, E06)")
@RestController
@RequestMapping("/api/v1/identity/admin")
@RequiredArgsConstructor
public class AdminController {

	private final AdminDirectory directory;

	private final SellerReviewService reviews;

	private final ModerationService moderation;

	@Operation(summary = "List customers", description = "`q` matches name, email or phone (case-insensitive).")
	@Parameter(name = "page", in = ParameterIn.QUERY, schema = @Schema(type = "integer", defaultValue = "1",
			minimum = "1"))
	@Parameter(name = "size", in = ParameterIn.QUERY, schema = @Schema(type = "integer", defaultValue = "20",
			minimum = "1", maximum = "100"))
	@Parameter(name = "sort", in = ParameterIn.QUERY, description = "`createdAt` (default, newest first) or "
			+ "`fullName`, optionally `,asc` / `,desc`", schema = @Schema(type = "string", example = "fullName,asc"))
	@GetMapping("/customers")
	@PreAuthorize("hasAuthority('operations:read:any')")
	ApiResponse<List<AdminViews.CustomerAdminView>> customers(@RequestParam(required = false) String q,
			@RequestParam(required = false) CustomerStatus status, @Parameter(hidden = true) Pageable pageable) {
		return ApiResponse.page(directory.customers(q, status, pageable));
	}

	@Operation(summary = "A customer", description = "Order counts come from Order (GET /orders/admin/orders).")
	@ErrorCodes(NOT_FOUND)
	@GetMapping("/customers/{customerId}")
	@PreAuthorize("hasAuthority('operations:read:any')")
	ApiResponse<AdminViews.CustomerAdminView> customer(@PathVariable UUID customerId) {
		return ApiResponse.of(directory.customer(customerId));
	}

	@Operation(summary = "List sellers, including applications", description = "`q` matches shop name, email or "
			+ "contact phone. The review queue is `approvalStatus=PENDING&sort=registeredAt,asc`.")
	@Parameter(name = "page", in = ParameterIn.QUERY, schema = @Schema(type = "integer", defaultValue = "1",
			minimum = "1"))
	@Parameter(name = "size", in = ParameterIn.QUERY, schema = @Schema(type = "integer", defaultValue = "20",
			minimum = "1", maximum = "100"))
	@Parameter(name = "sort", in = ParameterIn.QUERY, description = "`registeredAt` (default, newest first) or "
			+ "`sellerName`, optionally `,asc` / `,desc`", schema = @Schema(type = "string",
					example = "registeredAt,asc"))
	@GetMapping("/sellers")
	@PreAuthorize("hasAuthority('operations:read:any')")
	ApiResponse<List<AdminViews.SellerAdminView>> sellers(@RequestParam(required = false) String q,
			@RequestParam(required = false) SellerApprovalStatus approvalStatus,
			@RequestParam(required = false) SellerStatus status, @Parameter(hidden = true) Pageable pageable) {
		return ApiResponse.page(directory.sellers(q, approvalStatus, status, pageable));
	}

	@Operation(summary = "A seller", description = "Wallet summary comes from Finance (GET /finance/admin/wallets).")
	@ErrorCodes(NOT_FOUND)
	@GetMapping("/sellers/{sellerId}")
	@PreAuthorize("hasAuthority('operations:read:any')")
	ApiResponse<AdminViews.SellerAdminView> seller(@PathVariable UUID sellerId) {
		return ApiResponse.of(directory.seller(sellerId));
	}

	@Operation(summary = "Approve a seller application", description = "PENDING only. Grants SELLER, bumps "
			+ "`authzVersion`, writes the audit row and `seller.approved` (Finance creates the wallet) in one "
			+ "transaction.")
	@ErrorCodes({ NOT_FOUND, ALREADY_PROCESSED, SELF_APPROVAL_FORBIDDEN })
	@PostMapping("/sellers/{sellerId}/approve")
	@PreAuthorize("hasAuthority('seller:approve')")
	ApiResponse<AdminViews.SellerAdminView> approve(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sellerId) {
		return ApiResponse.of(reviews.approve(Caller.of(jwt).userId(), sellerId));
	}

	@Operation(summary = "Reject a seller application", description = "PENDING only. The account (and customer "
			+ "profile) stays; the shop name and phone become free; the user may apply again.")
	@ErrorCodes({ NOT_FOUND, ALREADY_PROCESSED, SELF_APPROVAL_FORBIDDEN })
	@PostMapping("/sellers/{sellerId}/reject")
	@PreAuthorize("hasAuthority('seller:approve')")
	ApiResponse<AdminViews.SellerAdminView> reject(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sellerId,
			@Valid @RequestBody RejectRequest body) {
		return ApiResponse.of(reviews.reject(Caller.of(jwt).userId(), sellerId, body.reason().strip()));
	}

	@Operation(summary = "Ban a user, customer or seller", description = """
			`scope` is required:
			- `USER`: disables the account and signs out every session.
			- `CUSTOMER`: G-14. Asks Order whether the customer has a paid order: yes → `PAYMENT_ONLY` (keeps old \
			orders, no new payments), no → `CUSTOMER_SCOPE` (no customer features). Order unavailable → 503, nothing \
			changed.
			- `SELLER`: blocks selling and withdrawals; approval and role are kept.

			The same ban again is a 200 with nothing written; another reason while banned is 409. Users with ADMIN \
			can't be banned (403).""")
	@ErrorCodes({ NOT_FOUND, INVALID_STATE_TRANSITION, SERVICE_UNAVAILABLE })
	@PostMapping("/ban/{userId}")
	@PreAuthorize("hasAuthority('account:moderate')")
	ApiResponse<ModerationService.BanResult> ban(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId,
			@Valid @RequestBody BanRequest body) {
		return ApiResponse.of(moderation.ban(Caller.of(jwt).userId(), userId, body.scope(), body.reason()));
	}

	@Operation(summary = "Lift a ban in one scope", description = "Only that scope; never grants SELLER to an "
			+ "unapproved seller. Already ACTIVE → 200 with nothing written.")
	@ErrorCodes(NOT_FOUND)
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
