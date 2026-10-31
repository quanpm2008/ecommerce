package com.example.identity_service.media;

import com.example.identity_service.account.SellerAccountService;
import com.example.identity_service.common.security.Caller;
import com.example.identity_service.common.web.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Presigned upload URLs for the caller's shop logo (API §3.9, §6.1). Only {@code SELLER_LOGO}, one file, and only
 * for an APPROVED, ACTIVE seller. The client PUTs the file itself, then sends the object key to
 * {@code PATCH /sellers/me}, which checks what was actually uploaded.
 */
@RestController
@RequestMapping("/api/v1/identity/media")
@RequiredArgsConstructor
public class MediaController {

	private final SellerAccountService sellerAccounts;

	private final SellerLogos logos;

	private final MediaStorage storage;

	@PostMapping("/upload-urls")
	@PreAuthorize("hasAuthority('seller:profile:own')")
	ApiResponse<List<MediaStorage.PresignedUpload>> uploadUrls(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody UploadRequest body) {
		UUID sellerId = sellerAccounts.operatingSeller(Caller.of(jwt)).getSellerId();
		FileSpec file = body.files().getFirst();
		String objectKey = logos.newObjectKey(sellerId, file.purpose(), file.mimeType(), file.sizeBytes());
		return ApiResponse.of(List.of(storage.presignUpload(objectKey, file.mimeType(), file.sizeBytes())));
	}

	record UploadRequest(@NotNull @Size(min = 1, max = 1) List<@Valid FileSpec> files) {
	}

	record FileSpec(@NotBlank @Size(max = 255) String fileName, @NotBlank String mimeType, long sizeBytes,
			@NotBlank String purpose) {
	}

}
