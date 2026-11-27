package com.example.identity_service.service;

import com.example.identity_service.exception.ApiException;
import com.example.identity_service.exception.ErrorCode;
import com.example.identity_service.repository.CustomerRepository;
import com.example.identity_service.entity.CustomerStatus;
import com.example.identity_service.entity.SellerApprovalStatus;
import com.example.identity_service.repository.SellerRepository;
import com.example.identity_service.entity.SellerStatus;
import com.example.identity_service.security.AccessTokenDecoderConfig;
import com.example.identity_service.repository.AuthSessionRepository;
import com.example.identity_service.security.TokenClaims;
import com.example.identity_service.config.TokenProperties;
import com.example.identity_service.entity.AccessMode;
import com.example.identity_service.entity.AccountStatus;
import com.example.identity_service.entity.AccountType;
import com.example.identity_service.entity.User;
import com.example.identity_service.repository.UserRepository;
import com.example.identity_service.entity.UserStatus;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The current authorization context behind a user's access token (API §7.1), for the gateway and every protected
 * service. The token itself must be valid (signature, {@code exp}, {@code iss}, {@code aud}) and its
 * {@code authzVersion} current, otherwise 401 ({@code TOKEN_EXPIRED}, {@code TOKEN_STALE}, {@code UNAUTHENTICATED})
 * for the caller to pass on. A revoked session or a disabled user is reported ({@code sessionActive},
 * {@code userStatus}) with no roles or permissions, so a caller that only reads the permissions still fails closed.
 */
@Service
public class AuthorizationContextService {

	private final JwtDecoder tokens;

	private final UserRepository users;

	private final AuthSessionRepository sessions;

	private final CustomerRepository customers;

	private final SellerRepository sellers;

	private final PermissionResolver permissions;

	AuthorizationContextService(RSAKey signingKey, TokenProperties tokenProperties, Clock clock, UserRepository users,
			AuthSessionRepository sessions, CustomerRepository customers, SellerRepository sellers,
			PermissionResolver permissions) throws JOSEException {
		this.tokens = AccessTokenDecoderConfig.decoder(signingKey, tokenProperties, clock);
		this.users = users;
		this.sessions = sessions;
		this.customers = customers;
		this.sellers = sellers;
		this.permissions = permissions;
	}

	@Transactional(readOnly = true)
	public AuthorizationContext of(String accessToken) {
		Jwt jwt = decode(accessToken);
		UUID userId = uuid(jwt.getSubject());
		UUID sessionId = uuid(jwt.getClaimAsString(TokenClaims.SESSION_ID));
		if (!(jwt.getClaims().get(TokenClaims.AUTHZ_VERSION) instanceof Number version)) {
			throw new ApiException(ErrorCode.UNAUTHENTICATED);
		}
		User user = users.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
		if (version.longValue() < user.getAuthzVersion()) {
			throw new ApiException(ErrorCode.TOKEN_STALE);
		}
		if (version.longValue() > user.getAuthzVersion()) {
			throw new ApiException(ErrorCode.UNAUTHENTICATED);
		}
		boolean sessionActive = sessions.findById(sessionId)
				.filter(session -> session.getUserId().equals(userId) && !session.isRevoked()).isPresent();
		boolean usable = sessionActive && user.isActive();
		CustomerAccess customer = customers.findById(userId)
				.map(c -> new CustomerAccess(c.getStatus(), c.getStatus() == CustomerStatus.BANNED))
				.orElse(null);
		SellerAccess seller = sellers.findById(userId)
				.map(s -> new SellerAccess(s.getApprovalStatus(), s.getStatus(), s.canOperate()))
				.orElse(null);
		boolean profileBanned = customer != null && customer.status() == CustomerStatus.BANNED
				|| seller != null && seller.status() == SellerStatus.BANNED;
		return new AuthorizationContext(userId, user.getAccountType(), AccountStatus.of(user, profileBanned),
				user.getStatus(), user.getAccessMode(), user.getBannedAt(), user.getDisabledAt(), user.getRestoreUntil(),
				user.getAuthzVersion(),
				usable ? permissions.activeRolesOf(user).stream().map(Enum::name).sorted().toList() : List.of(),
				usable ? List.copyOf(permissions.permissionsOf(user)) : List.of(), customer, seller, sessionActive);
	}

	private Jwt decode(String accessToken) {
		if (accessToken == null || accessToken.isBlank()) {
			throw new ApiException(ErrorCode.UNAUTHENTICATED);
		}
		try {
			return tokens.decode(accessToken);
		}
		catch (JwtValidationException ex) {
			boolean expired = ex.getErrors().stream().map(OAuth2Error::getErrorCode)
					.anyMatch(AccessTokenDecoderConfig.TOKEN_EXPIRED::equals);
			throw new ApiException(expired ? ErrorCode.TOKEN_EXPIRED : ErrorCode.UNAUTHENTICATED);
		}
		catch (JwtException ex) {
			throw new ApiException(ErrorCode.UNAUTHENTICATED);
		}
	}

	private static UUID uuid(String value) {
		try {
			return UUID.fromString(value);
		}
		catch (IllegalArgumentException | NullPointerException ex) {
			throw new ApiException(ErrorCode.UNAUTHENTICATED);
		}
	}

	/**
	 * API §7.1 {@code authorization-context}. {@code roles} and {@code permissions} are empty unless the session is
	 * active and the user ACTIVE; for a BANNED account they are already cut to its {@code accessMode}'s allowlist
	 * (G-14). Profile statuses are always reported; ownership checks stay with the calling service.
	 */
	public record AuthorizationContext(UUID userId, AccountType accountType, AccountStatus accountStatus,
			UserStatus userStatus, AccessMode accessMode, Instant bannedAt, Instant disabledAt, Instant restoreUntil,
			long authzVersion, List<String> roles, List<String> permissions, CustomerAccess customerProfile,
			SellerAccess sellerProfile, boolean sessionActive) {
	}

	public record CustomerAccess(CustomerStatus status, boolean paymentBlocked) {
	}

	/** {@code canOperate}: APPROVED and ACTIVE; selling also needs the SELLER role, which is in {@code roles}. */
	public record SellerAccess(SellerApprovalStatus approvalStatus, SellerStatus status, boolean canOperate) {
	}

}
