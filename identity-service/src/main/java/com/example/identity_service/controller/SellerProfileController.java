package com.example.identity_service.controller;

import com.example.identity_service.dto.SellerSelfProfile;
import com.example.identity_service.service.SellerAccountService;
import com.example.identity_service.security.Caller;
import com.example.identity_service.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;


@Tag(name = "Seller shop", description = "The caller's own shop, for approved sellers")
@RestController
@RequestMapping("/api/v1/identity/sellers/me")
@RequiredArgsConstructor
public class SellerProfileController {

	private final SellerAccountService sellerAccounts;

	@Operation(summary = "Update my shop", description = "APPROVED and ACTIVE sellers only (403 otherwise). Only the "
			+ "description: the shop name, phone and logo are the account's fullName, phoneNumber and avatar "
			+ "(PATCH /users/me).")
	@io.swagger.v3.oas.annotations.parameters.RequestBody(
			content = @Content(schema = @Schema(implementation = UpdateShopRequest.class)))
	@PatchMapping
	@PreAuthorize("hasAuthority('seller:profile:own')")
	ApiResponse<SellerSelfProfile> update(@AuthenticationPrincipal Jwt jwt, @RequestBody Map<String, Object> body) {
		return ApiResponse.of(sellerAccounts.updateProfile(Caller.of(jwt), body));
	}

	@Schema(description = "Only description; any other field is rejected. null clears it.")
	record UpdateShopRequest(@Schema(maxLength = 2000, nullable = true) String description) {
	}

}
