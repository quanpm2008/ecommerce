package com.example.identity_service.account;

import com.example.identity_service.common.error.ApiException;
import com.example.identity_service.common.error.ErrorCode;
import com.example.identity_service.common.security.Caller;
import com.example.identity_service.common.web.StrictBody;
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
 * The caller's own account (UC-A06): profile, name and phone.
 */
@Service
@RequiredArgsConstructor
public class AccountService {

	static final Set<String> UPDATABLE_FIELDS = Set.of("fullName", "phoneNumber");

	private final UserRepository users;

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

}
