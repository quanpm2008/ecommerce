package com.example.identity_service.service;

import com.example.identity_service.dto.SellerSelfProfile;
import com.example.identity_service.exception.ApiException;
import com.example.identity_service.exception.ErrorCode;
import com.example.identity_service.security.Caller;
import com.example.identity_service.controller.StrictBody;
import com.example.identity_service.entity.Seller;
import com.example.identity_service.repository.SellerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

/**
 * The caller's own shop profile (UC-A06, API §6.1 {@code PATCH /sellers/me}). The seller id is always the caller's
 * user id. The shop name, phone and logo belong to the account ({@code PATCH /users/me}).
 */
@Service
@RequiredArgsConstructor
public class SellerAccountService {

	static final Set<String> PROFILE_FIELDS = Set.of("description");

	private static final int DESCRIPTION_MAX = 2000;

	private final SellerRepository sellers;

	private final SelfProfileAssembler profiles;

	/** PATCH allowlist: {@code description} only. Only an APPROVED, ACTIVE seller may edit the shop. */
	@Transactional
	public SellerSelfProfile updateProfile(Caller caller, Map<String, Object> body) {
		StrictBody fields = StrictBody.of(body, PROFILE_FIELDS);
		Seller seller = operatingSeller(caller);
		if (fields.has("description")) {
			seller.changeDescription(fields.nullableText("description", DESCRIPTION_MAX));
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

}
