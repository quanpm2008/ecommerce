package com.example.identity_service.account;

import com.example.identity_service.common.security.Caller;
import com.example.identity_service.common.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/identity/sellers/me")
@RequiredArgsConstructor
public class SellerProfileController {

	private final SellerAccountService sellerAccounts;

	@PatchMapping
	@PreAuthorize("hasAuthority('seller:profile:own')")
	ApiResponse<SellerSelfProfile> update(@AuthenticationPrincipal Jwt jwt, @RequestBody Map<String, Object> body) {
		return ApiResponse.of(sellerAccounts.updateProfile(Caller.of(jwt), body));
	}

}
