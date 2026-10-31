package com.example.identity_service.account;

import com.example.identity_service.common.error.ApiException;
import com.example.identity_service.common.error.ErrorCode;
import com.example.identity_service.common.security.Caller;
import com.example.identity_service.common.web.StrictBody;
import com.example.identity_service.customer.Customer;
import com.example.identity_service.customer.CustomerRepository;
import com.example.identity_service.rbac.RoleAssignments;
import com.example.identity_service.rbac.RoleCode;
import com.example.identity_service.rbac.UserRoleRepository;
import com.example.identity_service.user.PhoneNumbers;
import com.example.identity_service.user.User;
import com.example.identity_service.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

/**
 * The caller's own account (UC-A01, A06): profile, name and phone, and adding a Customer profile.
 */
@Service
@RequiredArgsConstructor
public class AccountService {

	static final Set<String> UPDATABLE_FIELDS = Set.of("fullName", "phoneNumber");

	private final UserRepository users;

	private final CustomerRepository customers;

	private final UserRoleRepository userRoles;

	private final RoleAssignments roleAssignments;

	private final SelfProfileAssembler profiles;

	public UserSelfProfile me(Caller caller) {
		return profiles.of(caller.userId());
	}

	/** PATCH allowlist: {@code fullName}, {@code phoneNumber}. Anything else, null included, is a 400. */
	@Transactional
	public UserSelfProfile update(Caller caller, Map<String, Object> body) {
		StrictBody fields = StrictBody.of(body, UPDATABLE_FIELDS);
		User user = users.findById(caller.userId()).orElseThrow();
		if (fields.has("fullName")) {
			user.rename(fields.text("fullName", 150));
		}
		if (fields.has("phoneNumber")) {
			String phone = PhoneNumbers.normalize(fields.text("phoneNumber", 30))
					.orElseThrow(() -> ApiException.forField(ErrorCode.VALIDATION_ERROR, "phoneNumber", "INVALID_PHONE"));
			if (users.existsByPhoneNumberAndUserIdNot(phone, user.getUserId())) {
				throw ApiException.forField(ErrorCode.PHONE_ALREADY_USED, "phoneNumber", "DUPLICATE");
			}
			user.changePhoneNumber(phone);
		}
		try {
			users.flush();
		}
		catch (DataIntegrityViolationException ex) {
			throw ApiException.forField(ErrorCode.PHONE_ALREADY_USED, "phoneNumber", "DUPLICATE");
		}
		return profiles.of(user.getUserId());
	}

	/**
	 * Adds Customer ACTIVE + CUSTOMER role (baseline policy). An existing Customer is returned as is, even BANNED; a
	 * CUSTOMER role an admin revoked is never granted back here.
	 */
	@Transactional
	public Result createCustomerProfile(Caller caller) {
		User user = users.findById(caller.userId()).orElseThrow();
		if (customers.existsById(user.getUserId())) {
			return new Result(profiles.of(user.getUserId()), false);
		}
		if (userRoles.wasRevoked(user.getUserId(), RoleCode.CUSTOMER)) {
			throw new ApiException(ErrorCode.FORBIDDEN, "The CUSTOMER role was revoked by an administrator");
		}
		customers.save(new Customer(user));
		roleAssignments.grant(user, RoleCode.CUSTOMER, null);
		return new Result(profiles.of(user.getUserId()), true);
	}

	public record Result(UserSelfProfile profile, boolean created) {
	}

}
