package com.example.identity_service.auth;

import com.example.identity_service.account.SelfProfileAssembler;
import com.example.identity_service.account.UserSelfProfile;
import com.example.identity_service.common.error.ApiException;
import com.example.identity_service.common.error.ErrorCode;
import com.example.identity_service.common.security.Caller;
import com.example.identity_service.common.security.OpaqueTokens;
import com.example.identity_service.customer.Customer;
import com.example.identity_service.customer.CustomerRepository;
import com.example.identity_service.outbox.EventActor;
import com.example.identity_service.outbox.OutboxWriter;
import com.example.identity_service.rbac.RoleAssignments;
import com.example.identity_service.rbac.RoleCode;
import com.example.identity_service.seller.Seller;
import com.example.identity_service.seller.SellerApprovalStatus;
import com.example.identity_service.seller.SellerRepository;
import com.example.identity_service.session.IssuedTokens;
import com.example.identity_service.session.SessionService;
import com.example.identity_service.user.AuthMethod;
import com.example.identity_service.user.PhoneNumbers;
import com.example.identity_service.user.User;
import com.example.identity_service.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registration, login, refresh and logout (API §6.1, UC-A01–A03, A07). One login for every role: the request never
 * names a role, and the profile shows whatever the user currently has.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

	private final UserRepository users;

	private final CustomerRepository customers;

	private final SellerRepository sellers;

	private final RoleAssignments roleAssignments;

	private final SessionService sessions;

	private final SelfProfileAssembler profiles;

	private final OutboxWriter outbox;

	private final PasswordEncoder passwordEncoder;

	/** Compared against when the email is unknown, so a miss costs as much time as a wrong password. */
	private final String unknownUserHash = "{bcrypt}" + new BCryptPasswordEncoder().encode(OpaqueTokens.generate());

	/** User LOCAL + Customer + CUSTOMER role in one transaction. No tokens: the client logs in afterwards. */
	@Transactional
	public UserSelfProfile register(AuthRequests.Register request) {
		NewPasswords.check(request.password(), request.confirmPassword(), "password");
		String email = User.normalizeEmail(request.email());
		String phone = PhoneNumbers.normalize(request.phoneNumber())
				.orElseThrow(() -> ApiException.forField(ErrorCode.VALIDATION_ERROR, "phoneNumber", "INVALID_PHONE"));
		if (users.existsByEmail(email)) {
			throw ApiException.forField(ErrorCode.EMAIL_ALREADY_USED, "email", "DUPLICATE");
		}
		if (users.existsByPhoneNumber(phone)) {
			throw ApiException.forField(ErrorCode.PHONE_ALREADY_USED, "phoneNumber", "DUPLICATE");
		}
		User user = saveNewUser(User.local(email, passwordEncoder.encode(request.password()),
				request.fullName().strip(), phone));
		customers.save(new Customer(user));
		roleAssignments.grant(user, RoleCode.CUSTOMER, null);
		return profiles.of(user.getUserId());
	}

	/**
	 * User LOCAL + Seller PENDING in one transaction, with no SELLER role and no wallet until an admin approves.
	 * The user's own phone stays empty; {@code contactPhone} belongs to the shop.
	 */
	@Transactional
	public UserSelfProfile registerSeller(AuthRequests.RegisterSeller request) {
		NewPasswords.check(request.password(), request.confirmPassword(), "password");
		String email = User.normalizeEmail(request.email());
		String sellerName = request.sellerName().strip();
		String contactPhone = PhoneNumbers.normalize(request.contactPhone())
				.orElseThrow(() -> ApiException.forField(ErrorCode.VALIDATION_ERROR, "contactPhone", "INVALID_PHONE"));
		if (users.existsByEmail(email)) {
			throw ApiException.forField(ErrorCode.EMAIL_ALREADY_USED, "email", "DUPLICATE");
		}
		if (sellers.existsActiveSellerName(sellerName)) {
			throw ApiException.forField(ErrorCode.SELLER_NAME_ALREADY_USED, "sellerName", "DUPLICATE");
		}
		if (sellers.existsActiveContactPhone(contactPhone)) {
			throw ApiException.forField(ErrorCode.PHONE_ALREADY_USED, "contactPhone", "DUPLICATE");
		}
		User user = saveNewUser(User.local(email, passwordEncoder.encode(request.password()),
				request.fullName().strip(), null));
		try {
			sellers.saveAndFlush(new Seller(user, sellerName, contactPhone, null));
		}
		catch (DataIntegrityViolationException ex) {
			throw UniqueConflicts.translate(ex);
		}
		outbox.write("seller.registered", user.getUserId(), EventActor.user(user.getUserId(), EventActor.Context.SELLER),
				Map.of("userId", user.getUserId(), "sellerId", user.getUserId(),
						"approvalStatus", SellerApprovalStatus.PENDING, "authzVersion", user.getAuthzVersion()));
		return profiles.of(user.getUserId());
	}

	/**
	 * Unknown email and wrong password give the same {@code INVALID_CREDENTIALS}. A disabled user is only told so
	 * after the password matched. Restricted profiles (PENDING, REJECTED, BANNED) still log in.
	 */
	public AuthTokens login(AuthRequests.Login request) {
		Optional<User> found = users.findByEmail(User.normalizeEmail(request.email()));
		if (found.isEmpty()) {
			passwordEncoder.matches(request.password(), unknownUserHash);
			throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
		}
		User user = found.get();
		if (user.getAuthMethod() != AuthMethod.LOCAL) {
			throw new ApiException(ErrorCode.WRONG_AUTH_METHOD, ErrorCode.WRONG_AUTH_METHOD.defaultMessage(),
					List.of(Map.of("authMethod", user.getAuthMethod().name())));
		}
		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
		}
		if (!user.isActive()) {
			throw new ApiException(ErrorCode.ACCOUNT_DISABLED);
		}
		return withProfile(sessions.start(user));
	}

	/** Roles and permissions in the response are read again, never copied from the old token. */
	public AuthTokens refresh(String refreshToken) {
		return withProfile(sessions.refresh(refreshToken));
	}

	public void logout(Caller caller, String refreshToken) {
		sessions.logout(caller.userId(), caller.sessionId(), refreshToken);
	}

	private AuthTokens withProfile(IssuedTokens tokens) {
		return AuthTokens.of(tokens, profiles.of(tokens.userId()));
	}

	private User saveNewUser(User user) {
		try {
			return users.saveAndFlush(user);
		}
		catch (DataIntegrityViolationException ex) {
			throw UniqueConflicts.translate(ex);
		}
	}

}
