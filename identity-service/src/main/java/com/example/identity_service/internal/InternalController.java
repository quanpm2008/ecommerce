package com.example.identity_service.internal;

import com.example.identity_service.common.error.ApiException;
import com.example.identity_service.common.error.ErrorCode;
import com.example.identity_service.common.web.ApiResponse;
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

/**
 * Identity's internal API (API §7.1). Callers authenticate with {@code X-Service-Token}
 * ({@link InternalSecurityConfig}); each endpoint allows only the consumers the spec lists, as
 * {@code service:<spring.application.name>} authorities. Catalog is {@code product-service}.
 */
@RestController
@RequestMapping("/internal/v1/identity")
@RequiredArgsConstructor
public class InternalController {

	private static final int MAX_SELLER_IDS = 100;

	private final AuthorizationContextService authorizationContexts;

	private final InternalQueries queries;

	/** The user's access token comes as {@code Authorization: Bearer ...}, next to the service's own token. */
	@PostMapping("/auth/authorization-context")
	@PreAuthorize("hasAnyAuthority('service:gateway-service', 'service:cart-service', 'service:order-service', "
			+ "'service:payment-service', 'service:finance-service', 'service:notification-service', "
			+ "'service:product-service')")
	ApiResponse<AuthorizationContextService.AuthorizationContext> authorizationContext(
			@RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
		String token = authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)
				? authorization.substring(7).strip() : null;
		return ApiResponse.of(authorizationContexts.of(token));
	}

	@GetMapping("/sellers/status")
	@PreAuthorize("hasAnyAuthority('service:product-service', 'service:cart-service', 'service:order-service', "
			+ "'service:finance-service')")
	ApiResponse<List<InternalQueries.SellerStatusView>> sellerStatuses(@RequestParam List<UUID> ids) {
		if (ids.isEmpty() || ids.size() > MAX_SELLER_IDS) {
			throw ApiException.forField(ErrorCode.VALIDATION_ERROR, "ids", "SIZE");
		}
		return ApiResponse.of(queries.sellerStatuses(ids));
	}

	@GetMapping("/customers/{customerId}/checkout-eligibility")
	@PreAuthorize("hasAnyAuthority('service:order-service', 'service:payment-service')")
	ApiResponse<InternalQueries.CheckoutEligibility> checkoutEligibility(@PathVariable UUID customerId) {
		return ApiResponse.of(queries.checkoutEligibility(customerId));
	}

	@GetMapping("/customers/{customerId}/addresses/{addressId}")
	@PreAuthorize("hasAuthority('service:order-service')")
	ApiResponse<InternalQueries.AddressSnapshot> address(@PathVariable UUID customerId, @PathVariable UUID addressId) {
		return ApiResponse.of(queries.address(customerId, addressId));
	}

	@PostMapping("/customers/{customerId}/addresses/{addressId}/touch")
	@PreAuthorize("hasAuthority('service:order-service')")
	ApiResponse<InternalQueries.AddressTouch> touch(@PathVariable UUID customerId, @PathVariable UUID addressId,
			@Valid @RequestBody TouchRequest body) {
		return ApiResponse.of(queries.touch(customerId, addressId, body.checkoutSessionId().strip()));
	}

	@GetMapping("/users/{userId}/contact")
	@PreAuthorize("hasAuthority('service:notification-service')")
	ApiResponse<InternalQueries.Contact> contact(@PathVariable UUID userId) {
		return ApiResponse.of(queries.contact(userId));
	}

	record TouchRequest(@NotBlank @Size(max = 100) String checkoutSessionId) {
	}

}
