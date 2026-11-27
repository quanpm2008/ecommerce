package com.example.identity_service.service;

import com.example.identity_service.client.GoogleIdTokens;
import com.example.identity_service.dto.AuthRequests;
import com.example.identity_service.dto.GoogleAuthTokens;
import com.example.identity_service.dto.AuthTokens;
import com.example.identity_service.dto.SellerRegistration;
import com.example.identity_service.dto.UserSelfProfile;
import com.example.identity_service.exception.ApiException;
import com.example.identity_service.exception.ErrorCode;
import com.example.identity_service.security.Caller;
import com.example.identity_service.security.OpaqueTokens;
import com.example.identity_service.entity.Customer;
import com.example.identity_service.repository.CustomerRepository;
import com.example.identity_service.messaging.EventActor;
import com.example.identity_service.messaging.OutboxWriter;
import com.example.identity_service.entity.RoleCode;
import com.example.identity_service.entity.Seller;
import com.example.identity_service.entity.SellerApplication;
import com.example.identity_service.repository.SellerApplicationRepository;
import com.example.identity_service.entity.SellerApprovalStatus;
import com.example.identity_service.repository.SellerRepository;
import com.example.identity_service.dto.IssuedTokens;
import com.example.identity_service.entity.AccountType;
import com.example.identity_service.entity.AuthMethod;
import com.example.identity_service.entity.User;
import com.example.identity_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registration, login, refresh and logout (API §6.1, UC-A01–A03, A07). Every role is its own account: login names the
 * account type of the web app, and a correct password for an account of another type is the same 401 as a wrong one.
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

	private final SignInPolicy signIn;

	private final SellerApplicationRepository applications;

	private final ShopNames shopNames;

	private final Clock clock;

	private final GoogleIdTokens google;

	private final TransactionTemplate tx;

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
		User user = saveNewUser(User.local(AccountType.CUSTOMER, email, passwordEncoder.encode(request.password()),
				request.fullName().strip(), phone));
		customers.save(new Customer(user));
		roleAssignments.grant(user, RoleCode.CUSTOMER, null);
		return profiles.of(user.getUserId());
	}

	/**
	 * A SELLER account (LOCAL) + Seller PENDING + its first SellerApplication, in one transaction, with no SELLER role,
	 * session or wallet until an admin approves (API §6.1). {@code fullName} is the shop name, unique among PENDING and
	 * APPROVED sellers; {@code phoneNumber} is the account's (and the shop's) phone, unique across all accounts.
	 */
	@Transactional
	public SellerRegistration registerSeller(AuthRequests.RegisterSeller request) {
		NewPasswords.check(request.password(), request.confirmPassword(), "password");
		String email = User.normalizeEmail(request.email());
		String shopName = request.fullName().strip();
		String phone = PhoneNumbers.normalize(request.phoneNumber())
				.orElseThrow(() -> ApiException.forField(ErrorCode.VALIDATION_ERROR, "phoneNumber", "INVALID_PHONE"));
		if (users.existsByEmail(email)) {
			throw ApiException.forField(ErrorCode.EMAIL_ALREADY_USED, "email", "DUPLICATE");
		}
		if (users.existsByPhoneNumber(phone)) {
			throw ApiException.forField(ErrorCode.PHONE_ALREADY_USED, "phoneNumber", "DUPLICATE");
		}
		User user = saveNewUser(User.local(AccountType.SELLER, email, passwordEncoder.encode(request.password()),
				shopName, phone));
		shopNames.reserve(shopName, user.getUserId());
		Instant now = clock.instant();
		sellers.save(new Seller(user, null, now));
		SellerApplication application = applications.save(new SellerApplication(user, 1, null, now));
		outbox.write("seller.registered", user.getUserId(), EventActor.user(user.getUserId(), EventActor.Context.SELLER),
				Map.of("userId", user.getUserId(), "sellerId", user.getUserId(),
						"approvalStatus", SellerApprovalStatus.PENDING, "authzVersion", user.getAuthzVersion()));
		return SellerRegistration.of(user.getUserId(), application.getApplicationId());
	}

	/**
	 * Unknown email, an account of another type and a wrong password all give the same {@code INVALID_CREDENTIALS}
	 * (and cost the same time). A seller not yet approved, or a disabled account, is only told so after the password
	 * matched ({@link SignInPolicy}); a banned account is reconciled with its obligations first (late obligation →
	 * limited session; none → 403 {@code ACCOUNT_DISABLED}; source down → 503).
	 */
	public AuthTokens.Issued login(AuthRequests.Login request) {
		Optional<User> found = users.findByEmail(User.normalizeEmail(request.email()))
				.filter(user -> user.getAccountType() == request.accountType());
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
		return startSession(user);
	}

	/**
	 * Google sign-in for customers (API §6.1, UC-A01.ALT-01/04/05, UC-A03.ALT-06). A known Google subject signs in to
	 * its account; otherwise a new CUSTOMER account (GOOGLE, no password, no phone) is created with Customer + role
	 * CUSTOMER. An email already used by a password account is never linked: 409 {@code WRONG_AUTH_METHOD} with the
	 * method to use. Then the same rules as {@link #login}: ban reconciliation, disabled, one session per user.
	 */
	public GoogleAuthTokens.Issued google(String idToken) {
		GoogleIdTokens.GoogleIdentity identity = google.verify(idToken);
		Optional<User> known = users.findByGoogleSubject(identity.subject());
		boolean created = known.isEmpty();
		User user = known.orElseGet(() -> createGoogleCustomer(identity));
		return GoogleAuthTokens.of(startSession(user), created);
	}

	private User createGoogleCustomer(GoogleIdTokens.GoogleIdentity identity) {
		String email = User.normalizeEmail(identity.email());
		users.findByEmail(email).ifPresent(existing -> {
			// A Google account with this email but another subject is not the same Google account either.
			if (existing.getAuthMethod() != AuthMethod.GOOGLE) {
				throw new ApiException(ErrorCode.WRONG_AUTH_METHOD, ErrorCode.WRONG_AUTH_METHOD.defaultMessage(),
						List.of(Map.of("authMethod", existing.getAuthMethod().name())));
			}
			throw new ApiException(ErrorCode.GOOGLE_AUTH_FAILED);
		});
		try {
			return tx.execute(status -> {
				User user = saveNewUser(User.google(email, identity.subject(), fullNameOf(identity, email)));
				customers.save(new Customer(user));
				roleAssignments.grant(user, RoleCode.CUSTOMER, null);
				return user;
			});
		}
		catch (DataIntegrityViolationException | ApiException raced) {
			// Two first sign-ins of one Google account at once: the other one created it.
			return users.findByGoogleSubject(identity.subject()).orElseThrow(() -> raced);
		}
	}

	/** Google's {@code name}, else the email's local part; at most 150 characters (DB §3.1.1). */
	private static String fullNameOf(GoogleIdTokens.GoogleIdentity identity, String email) {
		String name = identity.name() == null || identity.name().isBlank() ? email.substring(0, email.indexOf('@'))
				: identity.name().strip();
		return name.length() > 150 ? name.substring(0, 150) : name;
	}

	/** After the credentials are proven: sign-in rules, ban reconciliation (G-14), then a new session. */
	private AuthTokens.Issued startSession(User user) {
		signIn.check(user);
		signIn.reconcile(user.getUserId());
		user = users.findById(user.getUserId()).orElseThrow();
		if (!user.isActive()) {
			throw new ApiException(ErrorCode.ACCOUNT_DISABLED);
		}
		return withProfile(sessions.start(user));
	}

	/**
	 * Roles and permissions in the response are read again, never copied from the old token; the sign-in rules are
	 * checked again before the token rotates.
	 */
	public AuthTokens.Issued refresh(String refreshToken) {
		sessions.ownerOf(refreshToken).ifPresent(signIn::reconcile);
		return withProfile(sessions.refresh(refreshToken, signIn::check));
	}

	public void logout(Caller caller, String refreshToken) {
		sessions.logout(caller.userId(), caller.sessionId(), refreshToken);
	}

	private AuthTokens.Issued withProfile(IssuedTokens tokens) {
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
