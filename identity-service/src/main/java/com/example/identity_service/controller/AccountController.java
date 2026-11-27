package com.example.identity_service.controller;

import com.example.identity_service.dto.UserSelfProfile;
import com.example.identity_service.service.AccountService;
import com.example.identity_service.dto.AuthRequests;
import com.example.identity_service.service.PasswordService;
import com.example.identity_service.config.ErrorCodes;
import com.example.identity_service.security.Caller;
import com.example.identity_service.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static com.example.identity_service.exception.ErrorCode.ACCOUNT_BANNED;
import static com.example.identity_service.exception.ErrorCode.MEDIA_INVALID;
import static com.example.identity_service.exception.ErrorCode.PASSWORD_INCORRECT;
import static com.example.identity_service.exception.ErrorCode.PASSWORD_POLICY_VIOLATION;
import static com.example.identity_service.exception.ErrorCode.PASSWORD_SAME_AS_OLD;
import static com.example.identity_service.exception.ErrorCode.PHONE_ALREADY_USED;
import static com.example.identity_service.exception.ErrorCode.SELLER_NAME_LOCKED;

/**
 * Self-service on the caller's own account ({@code /users/me}); the user is always the token's {@code sub}.
 */
@Tag(name = "Account", description = "The caller's own account (UC-A04, A06)")
@RestController
@RequestMapping("/api/v1/identity/users/me")
@RequiredArgsConstructor
public class AccountController {

	private final PasswordService passwords;

	private final AccountService accounts;

	@Operation(summary = "My profile", description = "Current roles, permissions, customer and seller profiles.")
	@GetMapping
	@PreAuthorize("hasAuthority('account:read:own')")
	ApiResponse<UserSelfProfile> me(@AuthenticationPrincipal Jwt jwt) {
		return ApiResponse.of(accounts.me(Caller.of(jwt)));
	}

	@Operation(summary = "Update my name, phone or avatar", description = "Only the listed fields; any other field is a "
			+ "400 with issue UNKNOWN_FIELD and nothing changes. For a seller, fullName is the shop name and cannot "
			+ "change once applied (SELLER_NAME_LOCKED), and the avatar is the shop logo. For an avatar, get an upload "
			+ "URL from POST /media/upload-urls, upload the file, then send its objectKey. A banned account cannot edit "
			+ "its profile.")
	@ErrorCodes({ PHONE_ALREADY_USED, SELLER_NAME_LOCKED, MEDIA_INVALID, ACCOUNT_BANNED })
	@io.swagger.v3.oas.annotations.parameters.RequestBody(
			content = @Content(schema = @Schema(implementation = UpdateMeRequest.class)))
	@PatchMapping
	@PreAuthorize("hasAuthority('account:update:own')")
	ApiResponse<UserSelfProfile> update(@AuthenticationPrincipal Jwt jwt, @RequestBody Map<String, Object> body) {
		return ApiResponse.of(accounts.update(Caller.of(jwt), body));
	}

	@Operation(summary = "Change my password", description = "Email accounts only. Signs out every session, including "
			+ "this one.")
	@ErrorCodes({ PASSWORD_INCORRECT, PASSWORD_SAME_AS_OLD, PASSWORD_POLICY_VIOLATION })
	@PutMapping("/password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("hasAuthority('account:update:own')")
	void changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AuthRequests.ChangePassword body) {
		passwords.change(Caller.of(jwt), body);
	}

	@Schema(description = "Send only the fields to change; any other field is rejected.")
	record UpdateMeRequest(@Schema(maxLength = 150, example = "Le Thi C") String fullName,
			@Schema(description = "Vietnamese mobile, 0xxxxxxxxx or +84xxxxxxxxx", example = "0912345678")
			String phoneNumber,
			@Schema(description = "Key returned by POST /media/upload-urls, after the upload; null removes the avatar",
					nullable = true, example = "avatars/5d2a.../7f3a.png") String avatarObjectKey) {
	}

}
