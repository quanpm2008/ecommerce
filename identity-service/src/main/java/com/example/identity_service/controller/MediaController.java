package com.example.identity_service.controller;

import com.example.identity_service.client.MediaStorage;
import com.example.identity_service.service.Avatars;
import com.example.identity_service.service.AccountService;
import com.example.identity_service.config.ErrorCodes;
import com.example.identity_service.security.Caller;
import com.example.identity_service.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import static com.example.identity_service.exception.ErrorCode.ACCOUNT_BANNED;
import static com.example.identity_service.exception.ErrorCode.MEDIA_INVALID;
import static com.example.identity_service.exception.ErrorCode.SERVICE_UNAVAILABLE;

/**
 * Presigned upload URLs for the caller's avatar (API §3.9, §6.1 #13): one {@code AVATAR} file, for any account that
 * may edit its profile (a seller's avatar is its shop logo). The client PUTs the file itself, then sends the object
 * key to {@code PATCH /users/me}, which checks what was actually uploaded.
 */
@Tag(name = "Media", description = "Presigned uploads for the account's avatar (a seller's shop logo)")
@RestController
@RequestMapping("/api/v1/identity/media")
@RequiredArgsConstructor
public class MediaController {

	private final AccountService accounts;

	private final Avatars avatars;

	private final MediaStorage storage;

	@Operation(summary = "Get an avatar upload URL", description = "One `AVATAR` file: JPG, PNG or WebP up to 2 MB. PUT "
			+ "the file to `uploadUrl` within 10 minutes with the same Content-Type and size, then send `objectKey` to "
			+ "PATCH /users/me. A banned account cannot upload.")
	@ErrorCodes({ MEDIA_INVALID, ACCOUNT_BANNED, SERVICE_UNAVAILABLE })
	@PostMapping("/upload-urls")
	@PreAuthorize("hasAuthority('account:update:own')")
	ApiResponse<List<MediaStorage.PresignedUpload>> uploadUrls(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody UploadRequest body) {
		UUID userId = accounts.editableAccount(Caller.of(jwt)).getUserId();
		FileSpec file = body.files().getFirst();
		String objectKey = avatars.newObjectKey(userId, file.purpose(), file.mimeType(), file.sizeBytes());
		return ApiResponse.of(List.of(storage.presignUpload(objectKey, file.mimeType(), file.sizeBytes())));
	}

	record UploadRequest(@NotNull @Size(min = 1, max = 1) List<@Valid FileSpec> files) {
	}

	record FileSpec(@NotBlank @Size(max = 255) String fileName, @NotBlank String mimeType, long sizeBytes,
			@NotBlank String purpose) {
	}

}
