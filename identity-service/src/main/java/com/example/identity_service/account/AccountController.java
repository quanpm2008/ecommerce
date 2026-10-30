package com.example.identity_service.account;

import com.example.identity_service.auth.AuthRequests;
import com.example.identity_service.auth.PasswordService;
import com.example.identity_service.common.security.Caller;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service on the caller's own account ({@code /users/me}); the user is always the token's {@code sub}.
 */
@RestController
@RequestMapping("/api/v1/identity/users/me")
@RequiredArgsConstructor
public class AccountController {

	private final PasswordService passwords;

	@PutMapping("/password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("hasAuthority('account:update:own')")
	void changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AuthRequests.ChangePassword body) {
		passwords.change(Caller.of(jwt), body);
	}

}
