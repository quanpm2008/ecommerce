package com.example.identity_service.internal;

import com.example.identity_service.common.error.ApiException;
import com.example.identity_service.common.error.ErrorCode;
import com.example.identity_service.common.openapi.ErrorCodes;
import com.example.identity_service.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import static com.example.identity_service.common.error.ErrorCode.INVALID_REGION;
import static com.example.identity_service.common.error.ErrorCode.NOT_FOUND;
import static com.example.identity_service.common.error.ErrorCode.REGION_SOURCE_UNAVAILABLE;

/**
 * Identity's internal API (API §7.1). Callers authenticate with {@code X-Service-Token}
 * ({@link InternalSecurityConfig}); each endpoint allows only the consumers the spec lists, as
 * {@code service:<spring.application.name>} authorities. Catalog is {@code product-service}.
 */
@Tag(name = "Internal", description = "Service-to-service API (API §7.1). Never routed by the gateway; each endpoint "
		+ "accepts only the services listed under Callers.")
@RestController
@RequestMapping("/internal/v1/identity")
@RequiredArgsConstructor
public class InternalController {

	private static final int MAX_SELLER_IDS = 100;

	private final AuthorizationContextService authorizationContexts;

	private final InternalQueries queries;

	/** The user's access token comes as {@code Authorization: Bearer ...}, next to the service's own token. */
	@Operation(summary = "Authorization context of a user token", description = "Send the user's access token as "
			+ "`Authorization: Bearer` plus your `X-Service-Token`. 401 (`TOKEN_EXPIRED`, `TOKEN_STALE`, "
			+ "`UNAUTHENTICATED`) is about the user's token: pass it on. A revoked session or disabled user is a 200 "
			+ "with `sessionActive: false` / `userStatus` and no roles or permissions.")
	@PostMapping("/auth/authorization-context")
	@PreAuthorize("hasAnyAuthority('service:gateway-service', 'service:cart-service', 'service:order-service', "
			+ "'service:payment-service', 'service:finance-service', 'service:notification-service', "
			+ "'service:product-service')")
	ApiResponse<AuthorizationContextService.AuthorizationContext> authorizationContext(
			@Parameter(hidden = true) @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false)
			String authorization) {
		String token = authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)
				? authorization.substring(7).strip() : null;
		return ApiResponse.of(authorizationContexts.of(token));
	}

	@Operation(summary = "Seller statuses", description = "1–100 ids, comma-separated. One entry per known id in "
			+ "request order; `sellable` is G-06. No personal data.")
	@GetMapping("/sellers/status")
	@PreAuthorize("hasAnyAuthority('service:product-service', 'service:cart-service', 'service:order-service', "
			+ "'service:finance-service')")
	ApiResponse<List<InternalQueries.SellerStatusView>> sellerStatuses(@RequestParam List<UUID> ids) {
		if (ids.isEmpty() || ids.size() > MAX_SELLER_IDS) {
			throw ApiException.forField(ErrorCode.VALIDATION_ERROR, "ids", "SIZE");
		}
		return ApiResponse.of(queries.sellerStatuses(ids));
	}

	@Operation(summary = "Can this customer check out?", description = "`canCheckout` = ACTIVE account with the "
			+ "CUSTOMER role, Customer not banned, a phone number and at least one address.")
	@ErrorCodes(NOT_FOUND)
	@GetMapping("/customers/{customerId}/checkout-eligibility")
	@PreAuthorize("hasAnyAuthority('service:order-service', 'service:payment-service')")
	ApiResponse<InternalQueries.CheckoutEligibility> checkoutEligibility(@PathVariable UUID customerId) {
		return ApiResponse.of(queries.checkoutEligibility(customerId));
	}

	@Operation(summary = "Address snapshot for an order", description = "Complete, with region names. Another "
			+ "customer's or a deleted address is 404.")
	@ErrorCodes({ NOT_FOUND, INVALID_REGION, REGION_SOURCE_UNAVAILABLE })
	@GetMapping("/customers/{customerId}/addresses/{addressId}")
	@PreAuthorize("hasAuthority('service:order-service')")
	ApiResponse<InternalQueries.AddressSnapshot> address(@PathVariable UUID customerId, @PathVariable UUID addressId) {
		return ApiResponse.of(queries.address(customerId, addressId));
	}

	@Operation(summary = "Mark an address used by a checkout", description = "Call after the checkout commits. Once "
			+ "per `checkoutSessionId`: retries return `applied: false` and change nothing.")
	@ErrorCodes(NOT_FOUND)
	@PostMapping("/customers/{customerId}/addresses/{addressId}/touch")
	@PreAuthorize("hasAuthority('service:order-service')")
	ApiResponse<InternalQueries.AddressTouch> touch(@PathVariable UUID customerId, @PathVariable UUID addressId,
			@Valid @RequestBody TouchRequest body) {
		return ApiResponse.of(queries.touch(customerId, addressId, body.checkoutSessionId().strip()));
	}

	@Operation(summary = "Where to email a user", description = "`{ userId, email, fullName }` only.")
	@ErrorCodes(NOT_FOUND)
	@GetMapping("/users/{userId}/contact")
	@PreAuthorize("hasAuthority('service:notification-service')")
	ApiResponse<InternalQueries.Contact> contact(@PathVariable UUID userId) {
		return ApiResponse.of(queries.contact(userId));
	}

	record TouchRequest(@NotBlank @Size(max = 100) String checkoutSessionId) {
	}

}
