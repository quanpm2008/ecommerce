package com.example.identity_service.service;

import com.example.identity_service.dto.UserSelfProfile;
import com.example.identity_service.exception.ApiException;
import com.example.identity_service.exception.ErrorCode;
import com.example.identity_service.security.Caller;
import com.example.identity_service.controller.StrictBody;
import com.example.identity_service.repository.CustomerRepository;
import com.example.identity_service.entity.CustomerStatus;
import com.example.identity_service.entity.SellerApprovalStatus;
import com.example.identity_service.repository.SellerRepository;
import com.example.identity_service.entity.SellerStatus;
import com.example.identity_service.entity.AccountType;
import com.example.identity_service.entity.User;
import com.example.identity_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The caller's own account (UC-A06, API §6.1 {@code /users/me}): profile, name, phone and avatar.
 */
@Service
@RequiredArgsConstructor
public class AccountService {

	static final Set<String> UPDATABLE_FIELDS = Set.of("fullName", "phoneNumber", "avatarObjectKey");

	private final UserRepository users;

	private final CustomerRepository customers;

	private final SellerRepository sellers;

	private final Avatars avatars;

	private final SelfProfileAssembler profiles;

	public UserSelfProfile me(Caller caller) {
		return profiles.of(caller.userId());
	}

	/**
	 * PATCH allowlist: {@code fullName}, {@code phoneNumber}, {@code avatarObjectKey} (null removes the avatar).
	 * Anything else, null included for the first two, is a 400. A seller's {@code fullName} is the shop name: it
	 * cannot change while the application is PENDING or APPROVED (409 {@code SELLER_NAME_LOCKED}, G-02). Only an
	 * ACTIVE account edits its profile ({@link #editableAccount}).
	 */
	@Transactional
	public UserSelfProfile update(Caller caller, Map<String, Object> body) {
		StrictBody fields = StrictBody.of(body, UPDATABLE_FIELDS);
		User user = editableAccount(caller);
		if (fields.has("fullName")) {
			String fullName = fields.text("fullName", 150);
			if (!fullName.equals(user.getFullName()) && holdsShopName(user)) {
				throw ApiException.forField(ErrorCode.SELLER_NAME_LOCKED, "fullName", "LOCKED");
			}
			user.rename(fullName);
		}
		if (fields.has("phoneNumber")) {
			String phone = PhoneNumbers.normalize(fields.text("phoneNumber", 30))
					.orElseThrow(() -> ApiException.forField(ErrorCode.VALIDATION_ERROR, "phoneNumber", "INVALID_PHONE"));
			if (users.existsByPhoneNumberAndUserIdNot(phone, user.getUserId())) {
				throw ApiException.forField(ErrorCode.PHONE_ALREADY_USED, "phoneNumber", "DUPLICATE");
			}
			user.changePhoneNumber(phone);
		}
		if (fields.has("avatarObjectKey")) {
			String key = fields.nullableText("avatarObjectKey", 512);
			if (key != null && !Objects.equals(key, user.getAvatarObjectKey())) {
				avatars.requireUploadedAvatar(user.getUserId(), key);
			}
			user.changeAvatar(key);
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
	 * The caller's account if it may edit its profile or upload an avatar: a banned Customer or Seller keeps only its
	 * obligations (API §3.2, §6.1), so 403 {@code ACCOUNT_BANNED}.
	 */
	@Transactional(readOnly = true)
	public User editableAccount(Caller caller) {
		User user = users.findById(caller.userId()).orElseThrow();
		boolean banned = switch (user.getAccountType()) {
			case CUSTOMER -> customers.findById(user.getUserId())
					.map(customer -> customer.getStatus() == CustomerStatus.BANNED).orElse(false);
			case SELLER -> sellers.findById(user.getUserId())
					.map(seller -> seller.getStatus() == SellerStatus.BANNED).orElse(false);
			case ADMIN -> false;
		};
		if (banned) {
			throw new ApiException(ErrorCode.ACCOUNT_BANNED);
		}
		return user;
	}

	private boolean holdsShopName(User user) {
		return user.getAccountType() == AccountType.SELLER && sellers.findById(user.getUserId())
				.map(seller -> seller.getApprovalStatus() != SellerApprovalStatus.REJECTED).orElse(false);
	}

}
