package com.example.identity_service.customer;

import com.example.identity_service.common.security.Caller;
import com.example.identity_service.common.web.ApiResponse;
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

@RestController
@RequestMapping("/api/v1/identity/customers/me/addresses")
@PreAuthorize("hasAuthority('customer:profile:own')")
@RequiredArgsConstructor
public class AddressController {

	private final AddressService addresses;

	@GetMapping
	ApiResponse<List<Address>> list(@AuthenticationPrincipal Jwt jwt) {
		return ApiResponse.of(addresses.list(Caller.of(jwt)), Map.of("maxAddresses", addresses.maxAddresses()));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	ApiResponse<Address> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AddressRequest body) {
		return ApiResponse.of(addresses.create(Caller.of(jwt), body));
	}

	@PutMapping("/{addressId}")
	ApiResponse<Address> update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID addressId,
			@Valid @RequestBody AddressRequest body) {
		return ApiResponse.of(addresses.update(Caller.of(jwt), addressId, body));
	}

	@DeleteMapping("/{addressId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID addressId) {
		addresses.delete(Caller.of(jwt), addressId);
	}

}
