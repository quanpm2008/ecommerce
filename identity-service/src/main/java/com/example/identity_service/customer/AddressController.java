package com.example.identity_service.customer;

import com.example.identity_service.common.openapi.ErrorCodes;
import com.example.identity_service.common.security.Caller;
import com.example.identity_service.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.example.identity_service.common.error.ErrorCode.ADDRESS_LIMIT_EXCEEDED;
import static com.example.identity_service.common.error.ErrorCode.INVALID_REGION;
import static com.example.identity_service.common.error.ErrorCode.NOT_FOUND;
import static com.example.identity_service.common.error.ErrorCode.REGION_SOURCE_UNAVAILABLE;

@Tag(name = "Addresses", description = "The caller's address book, at most 5 addresses (UC-A06, DB §3.1.4)")
@RestController
@RequestMapping("/api/v1/identity/customers/me/addresses")
@PreAuthorize("hasAuthority('customer:profile:own')")
@RequiredArgsConstructor
public class AddressController {

	private final AddressService addresses;

	@Operation(summary = "My addresses", description = "Last used first, then never-used ones by creation; the first "
			+ "is the default. `meta.maxAddresses` is the limit. Region names are null while the region source is "
			+ "down. A BANNED customer can read but not change the book.")
	@GetMapping
	ApiResponse<List<Address>> list(@AuthenticationPrincipal Jwt jwt) {
		return ApiResponse.of(addresses.list(Caller.of(jwt)), Map.of("maxAddresses", addresses.maxAddresses()));
	}

	@Operation(summary = "Add an address", description = "Codes come from GET /regions; the ward must belong to the "
			+ "province.")
	@ErrorCodes({ ADDRESS_LIMIT_EXCEEDED, INVALID_REGION, REGION_SOURCE_UNAVAILABLE })
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	ApiResponse<Address> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AddressRequest body) {
		return ApiResponse.of(addresses.create(Caller.of(jwt), body));
	}

	@Operation(summary = "Replace an address", description = "Another customer's address is 404.")
	@ErrorCodes({ NOT_FOUND, INVALID_REGION, REGION_SOURCE_UNAVAILABLE })
	@PutMapping("/{addressId}")
	ApiResponse<Address> update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID addressId,
			@Valid @RequestBody AddressRequest body) {
		return ApiResponse.of(addresses.update(Caller.of(jwt), addressId, body));
	}

	@Operation(summary = "Delete an address", description = "An address already used by a checkout is only hidden, so "
			+ "orders keep their reference. The last address may be deleted too.")
	@ErrorCodes(NOT_FOUND)
	@DeleteMapping("/{addressId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID addressId) {
		addresses.delete(Caller.of(jwt), addressId);
	}

}
