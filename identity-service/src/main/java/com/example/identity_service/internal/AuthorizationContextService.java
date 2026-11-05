package com.example.identity_service.internal;

import com.example.identity_service.account.CustomerProfile;
import com.example.identity_service.common.error.ApiException;
import com.example.identity_service.common.error.ErrorCode;
import com.example.identity_service.customer.CustomerRepository;
import com.example.identity_service.customer.CustomerStatus;
import com.example.identity_service.rbac.PermissionResolver;
import com.example.identity_service.seller.SellerApprovalStatus;
import com.example.identity_service.seller.SellerRepository;
import com.example.identity_service.seller.SellerStatus;
import com.example.identity_service.session.AccessTokenDecoderConfig;
import com.example.identity_service.session.AuthSessionRepository;
import com.example.identity_service.session.TokenClaims;
import com.example.identity_service.session.TokenProperties;
import com.example.identity_service.user.User;
import com.example.identity_service.user.UserRepository;
import com.example.identity_service.user.UserStatus;
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
				.map(c -> new CustomerAccess(c.getStatus(), CustomerProfile.accessModeOf(c),
						c.getStatus() == CustomerStatus.BANNED))
				.orElse(null);
		SellerAccess seller = sellers.findById(userId)
				.map(s -> new SellerAccess(s.getApprovalStatus(), s.getStatus(), s.canOperate()))
				.orElse(null);
		return new AuthorizationContext(userId, user.getStatus(), user.getAuthzVersion(),
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
	 * {@code roles} and {@code permissions} are empty unless the session is active and the user ACTIVE. Profile
	 * statuses are always reported; ownership checks stay with the calling service.
	 */
	public record AuthorizationContext(UUID userId, UserStatus userStatus, long authzVersion, List<String> roles,
			List<String> permissions, CustomerAccess customerProfile, SellerAccess sellerProfile,
			boolean sessionActive) {
	}

	public record CustomerAccess(CustomerStatus status, CustomerProfile.AccessMode accessMode,
			boolean paymentBlocked) {
	}

	/** {@code canOperate}: APPROVED and ACTIVE; selling also needs the SELLER role, which is in {@code roles}. */
	public record SellerAccess(SellerApprovalStatus approvalStatus, SellerStatus status, boolean canOperate) {
	}

}
