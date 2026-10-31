package com.example.identity_service.account;

import com.example.identity_service.common.error.ApiException;
import com.example.identity_service.common.error.ErrorCode;
import com.example.identity_service.common.security.Caller;
import com.example.identity_service.common.web.StrictBody;
import com.example.identity_service.media.SellerLogos;
import com.example.identity_service.outbox.EventActor;
import com.example.identity_service.outbox.OutboxWriter;
import com.example.identity_service.seller.Seller;
import com.example.identity_service.seller.SellerApprovalStatus;
import com.example.identity_service.seller.SellerRepository;
import com.example.identity_service.user.PhoneNumbers;
import com.example.identity_service.user.User;
import com.example.identity_service.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The caller's own seller application and shop profile (UC-A02, A06). The seller id is always the caller's user id.
 */
@Service
@RequiredArgsConstructor
public class SellerAccountService {

	static final Set<String> APPLICATION_FIELDS = Set.of("sellerName", "contactPhone", "description");

	static final Set<String> PROFILE_FIELDS = Set.of("description", "logoObjectKey", "contactPhone");

	private static final int DESCRIPTION_MAX = 2000;

	private final UserRepository users;

	private final SellerRepository sellers;

	private final SellerLogos logos;

	private final OutboxWriter outbox;

	private final SelfProfileAssembler profiles;

	@Transactional(readOnly = true)
	public Optional<SellerSelfProfile> application(Caller caller) {
		return sellers.findById(caller.userId()).map(profiles::seller);
	}

	/**
	 * First application (201) or resubmission after REJECTED (200), always PENDING and without the SELLER role.
	 * PENDING or APPROVED → 409 {@code INVALID_STATE_TRANSITION}.
	 */
	@Transactional
	public Result submit(Caller caller, Map<String, Object> body) {
		StrictBody fields = StrictBody.of(body, APPLICATION_FIELDS);
		String sellerName = fields.text("sellerName", 160);
		String contactPhone = phone(fields);
		String description = fields.nullableText("description", DESCRIPTION_MAX);
		User user = users.findById(caller.userId()).orElseThrow();
		Optional<Seller> existing = sellers.findById(user.getUserId());
		if (existing.isPresent() && existing.get().getApprovalStatus() != SellerApprovalStatus.REJECTED) {
			throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
					ErrorCode.INVALID_STATE_TRANSITION.defaultMessage(),
					List.of(Map.of("currentStatus", existing.get().getApprovalStatus().name())));
		}
		if (sellers.existsActiveSellerName(sellerName)) {
			throw ApiException.forField(ErrorCode.SELLER_NAME_ALREADY_USED, "sellerName", "DUPLICATE");
		}
		if (sellers.existsActiveContactPhone(contactPhone)) {
			throw ApiException.forField(ErrorCode.PHONE_ALREADY_USED, "contactPhone", "DUPLICATE");
		}
		Seller seller = existing.map(rejected -> {
			rejected.resubmit(sellerName, contactPhone, description);
			return rejected;
		}).orElseGet(() -> sellers.save(new Seller(user, sellerName, contactPhone, description)));
		sellers.flush();
		outbox.write("seller.registered", user.getUserId(), EventActor.user(user.getUserId(), EventActor.Context.SELLER),
				Map.of("userId", user.getUserId(), "sellerId", user.getUserId(),
						"approvalStatus", SellerApprovalStatus.PENDING, "authzVersion", user.getAuthzVersion()));
		return new Result(profiles.seller(seller), existing.isEmpty());
	}

	/**
	 * PATCH allowlist: {@code description}, {@code logoObjectKey}, {@code contactPhone}; seller name and email never
	 * change. Only an APPROVED, ACTIVE seller may edit the shop.
	 */
	@Transactional
	public SellerSelfProfile updateProfile(Caller caller, Map<String, Object> body) {
		StrictBody fields = StrictBody.of(body, PROFILE_FIELDS);
		Seller seller = operatingSeller(caller);
		if (fields.has("description")) {
			seller.changeDescription(fields.nullableText("description", DESCRIPTION_MAX));
		}
		if (fields.has("contactPhone")) {
			String contactPhone = phone(fields);
			if (sellers.existsActiveContactPhoneOfAnotherSeller(contactPhone, seller.getSellerId())) {
				throw ApiException.forField(ErrorCode.PHONE_ALREADY_USED, "contactPhone", "DUPLICATE");
			}
			seller.changeContactPhone(contactPhone);
		}
		if (fields.has("logoObjectKey")) {
			String key = fields.nullableText("logoObjectKey", 512);
			if (key != null) {
				logos.requireUploadedLogo(seller.getSellerId(), key);
			}
			seller.changeLogo(key);
		}
		sellers.flush();
		return profiles.seller(seller);
	}

	/** The caller's seller profile, if it may run the shop; otherwise 403. */
	@Transactional(readOnly = true)
	public Seller operatingSeller(Caller caller) {
		return sellers.findById(caller.userId())
				.filter(Seller::canOperate)
				.orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN, "The shop is not approved or is banned"));
	}

	private static String phone(StrictBody fields) {
		return PhoneNumbers.normalize(fields.text("contactPhone", 30))
				.orElseThrow(() -> ApiException.forField(ErrorCode.VALIDATION_ERROR, "contactPhone", "INVALID_PHONE"));
	}

	public record Result(SellerSelfProfile profile, boolean created) {
	}

}
