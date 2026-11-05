package com.example.identity_service.auth;

import com.example.identity_service.common.error.ApiException;
import com.example.identity_service.common.error.ErrorCode;
import com.example.identity_service.common.security.Caller;
import com.example.identity_service.common.security.OpaqueTokens;
import com.example.identity_service.outbox.EventActor;
import com.example.identity_service.outbox.OutboxWriter;
import com.example.identity_service.session.SessionService;
import com.example.identity_service.user.AuthMethod;
import com.example.identity_service.user.User;
import com.example.identity_service.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/**
 * Forgot, reset and change password (API §6.1, UC-A04, A05). Every successful change revokes all of the user's
 * sessions. Reset links are secrets: never log them.
 */
@Service
@RequiredArgsConstructor
public class PasswordService {

	private final UserRepository users;

	private final PasswordResetTokenRepository resetTokens;

	private final SessionService sessions;

	private final OutboxWriter outbox;

	private final PasswordEncoder passwordEncoder;

	private final PasswordResetProperties properties;

	private final Clock clock;

	/**
	 * G-05: the caller learns nothing, whether the email is unknown, a Google account or disabled. Only an ACTIVE
	 * LOCAL user gets a link, sent through the {@code password.reset_requested} event.
	 */
	@Transactional
	public void forgot(String email) {
		users.findByEmail(User.normalizeEmail(email))
				.filter(user -> user.getAuthMethod() == AuthMethod.LOCAL && user.isActive())
				.ifPresent(this::sendResetLink);
	}

	/** Single use: marks the link used, sets the password and revokes every session. */
	@Transactional
	public void reset(AuthRequests.ResetPassword request) {
		Instant now = clock.instant();
		PasswordResetToken token = resetTokens.findByTokenHash(OpaqueTokens.hash(request.token()))
				.filter(t -> t.isUsable(now))
				.orElseThrow(() -> new ApiException(ErrorCode.RESET_TOKEN_INVALID));
		User user = users.findById(token.getUserId())
				.filter(u -> u.getAuthMethod() == AuthMethod.LOCAL)
				.orElseThrow(() -> new ApiException(ErrorCode.RESET_TOKEN_INVALID));
		NewPasswords.check(request.newPassword(), request.confirmPassword(), "newPassword");
		token.markUsed(now);
		user.changePassword(passwordEncoder.encode(request.newPassword()));
		sessions.revokeAllOf(user.getUserId());
	}

	/** LOCAL accounts only; also ends the caller's current session, so the client must log in again. */
	@Transactional
	public void change(Caller caller, AuthRequests.ChangePassword request) {
		User user = users.findById(caller.userId()).orElseThrow();
		if (user.getAuthMethod() != AuthMethod.LOCAL) {
			throw new ApiException(ErrorCode.FORBIDDEN, "Accounts that sign in with Google have no password");
		}
		if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
			throw ApiException.forField(ErrorCode.PASSWORD_INCORRECT, "currentPassword", "INCORRECT");
		}
		NewPasswords.check(request.newPassword(), request.confirmPassword(), "newPassword");
		if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
			throw ApiException.forField(ErrorCode.PASSWORD_SAME_AS_OLD, "newPassword", "SAME_AS_OLD");
		}
		user.changePassword(passwordEncoder.encode(request.newPassword()));
		sessions.revokeAllOf(user.getUserId());
	}

	private void sendResetLink(User user) {
		String token = OpaqueTokens.generate();
		Instant expiresAt = clock.instant().plus(properties.ttl());
		resetTokens.save(new PasswordResetToken(user.getUserId(), OpaqueTokens.hash(token), expiresAt));
		String resetUrl = UriComponentsBuilder.fromUriString(properties.linkBase())
				.queryParam("token", token)
				.build()
				.toUriString();
		outbox.write("password.reset_requested", user.getUserId(),
				new EventActor(null, EventActor.Kind.USER, EventActor.Context.ACCOUNT),
				Map.of("userId", user.getUserId(), "resetUrl", resetUrl, "expiresAt", expiresAt.toString()));
	}

}
