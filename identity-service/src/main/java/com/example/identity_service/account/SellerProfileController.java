package com.example.identity_service.account;

import com.example.identity_service.common.openapi.ErrorCodes;
import com.example.identity_service.common.security.Caller;
import com.example.identity_service.common.web.ApiResponse;
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

import static com.example.identity_service.common.error.ErrorCode.MEDIA_INVALID;
import static com.example.identity_service.common.error.ErrorCode.PHONE_ALREADY_USED;

@Tag(name = "Seller shop", description = "The caller's own shop, for approved sellers")
@RestController
@RequestMapping("/api/v1/identity/sellers/me")
@RequiredArgsConstructor
public class SellerProfileController {

	private final SellerAccountService sellerAccounts;

	@Operation(summary = "Update my shop", description = "APPROVED and ACTIVE sellers only (403 otherwise). The shop "
			+ "name and email never change here. For a logo, get an upload URL from POST /media/upload-urls, upload "
			+ "the file, then send its objectKey.")
	@ErrorCodes({ PHONE_ALREADY_USED, MEDIA_INVALID })
	@io.swagger.v3.oas.annotations.parameters.RequestBody(
			content = @Content(schema = @Schema(implementation = UpdateShopRequest.class)))
	@PatchMapping
	@PreAuthorize("hasAuthority('seller:profile:own')")
	ApiResponse<SellerSelfProfile> update(@AuthenticationPrincipal Jwt jwt, @RequestBody Map<String, Object> body) {
		return ApiResponse.of(sellerAccounts.updateProfile(Caller.of(jwt), body));
	}

	@Schema(description = "Send only the fields to change; any other field is rejected. null clears description or "
			+ "logo.")
	record UpdateShopRequest(@Schema(maxLength = 2000, nullable = true) String description,
			@Schema(description = "Key returned by POST /media/upload-urls, after the upload", nullable = true,
					example = "seller-logos/5d2a.../7f3a.png") String logoObjectKey,
			@Schema(example = "0912345678") String contactPhone) {
	}

}
