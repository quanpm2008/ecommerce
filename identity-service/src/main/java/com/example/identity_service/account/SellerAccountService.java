package com.example.identity_service.account;

import com.example.identity_service.common.error.ApiException;
import com.example.identity_service.common.error.ErrorCode;
import com.example.identity_service.common.security.Caller;
import com.example.identity_service.common.web.StrictBody;
import com.example.identity_service.media.SellerLogos;
import com.example.identity_service.seller.Seller;
import com.example.identity_service.seller.SellerRepository;
import com.example.identity_service.user.PhoneNumbers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

/**
 * The caller's own shop profile (UC-A06). The seller id is always the caller's user id.
 */
@Service
@RequiredArgsConstructor
public class SellerAccountService {

	static final Set<String> PROFILE_FIELDS = Set.of("description", "logoObjectKey", "contactPhone");

	private static final int DESCRIPTION_MAX = 2000;

	private final SellerRepository sellers;

	private final SellerLogos logos;

	private final SelfProfileAssembler profiles;

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

}
