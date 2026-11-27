package com.example.identity_service.service;

import com.example.identity_service.config.SellerReapplicationProperties;
import com.example.identity_service.dto.AuthRequests;
import com.example.identity_service.exception.ApiException;
import com.example.identity_service.exception.ErrorCode;
import com.example.identity_service.security.OpaqueTokens;
import com.example.identity_service.messaging.EventActor;
import com.example.identity_service.messaging.OutboxWriter;
import com.example.identity_service.entity.Seller;
import com.example.identity_service.entity.SellerApplication;
import com.example.identity_service.repository.SellerApplicationRepository;
import com.example.identity_service.entity.SellerApprovalStatus;
import com.example.identity_service.entity.SellerReapplicationToken;
import com.example.identity_service.repository.SellerReapplicationTokenRepository;
import com.example.identity_service.repository.SellerRepository;
import com.example.identity_service.entity.AccountType;
import com.example.identity_service.entity.AuthMethod;
import com.example.identity_service.entity.User;
import com.example.identity_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A REJECTED seller applies again without signing in (UC-A02.ALT-04, API §6.1 #30/#31): first proves the account's
 * email with a single-use token, then submits a new SellerApplication on the same seller id. The rejected application
 * keeps its row and reason. Tokens are secrets: never log them.
 */
@Service
@RequiredArgsConstructor
public class SellerReapplicationService {

	private final UserRepository users;

	private final SellerRepository sellers;

	private final SellerApplicationRepository applications;

	private final SellerReapplicationTokenRepository tokens;

	private final ShopNames shopNames;

	private final OutboxWriter outbox;

	private final SellerReapplicationProperties properties;

	private final Clock clock;

	/**
	 * The caller learns nothing (always the same 202): only an ACTIVE LOCAL seller account whose latest application was
	 * REJECTED gets a link, through the {@code seller.reapplication_requested} event.
	 */
	@Transactional
	public void challenge(String email) {
		users.findByEmail(User.normalizeEmail(email))
				.filter(user -> user.getAccountType() == AccountType.SELLER && user.getAuthMethod() == AuthMethod.LOCAL
						&& user.isActive())
				.filter(user -> sellers.findById(user.getUserId())
						.map(seller -> seller.getApprovalStatus() == SellerApprovalStatus.REJECTED).orElse(false))
				.ifPresent(this::sendLink);
	}

	/**
	 * Uses the token once and adds the next PENDING application with the new shop name ({@code fullName}, unique among
	 * PENDING/APPROVED sellers) and phone. Only a REJECTED seller may: PENDING or APPROVED → 409
	 * {@code INVALID_STATE_TRANSITION}. No role or session is granted.
	 */
	@Transactional
	public Result submit(AuthRequests.SellerReapplication request) {
		Instant now = clock.instant();
		SellerReapplicationToken token = tokens.findByTokenHash(OpaqueTokens.hash(request.verificationToken()))
				.filter(candidate -> candidate.isUsable(now))
				.orElseThrow(() -> ApiException.forField(ErrorCode.VALIDATION_ERROR, "verificationToken",
						"INVALID_OR_EXPIRED"));
		User user = users.findLockedById(token.getSellerId()).orElseThrow();
		Seller seller = sellers.findById(user.getUserId()).orElseThrow();
		if (seller.getApprovalStatus() != SellerApprovalStatus.REJECTED) {
			throw ApiException.currentStatus(ErrorCode.INVALID_STATE_TRANSITION, seller.getApprovalStatus());
		}
		String shopName = request.fullName().strip();
		String phone = PhoneNumbers.normalize(request.phoneNumber())
				.orElseThrow(() -> ApiException.forField(ErrorCode.VALIDATION_ERROR, "phoneNumber", "INVALID_PHONE"));
		if (users.existsByPhoneNumberAndUserIdNot(phone, user.getUserId())) {
			throw ApiException.forField(ErrorCode.PHONE_ALREADY_USED, "phoneNumber", "DUPLICATE");
		}
		shopNames.reserve(shopName, user.getUserId());
		user.rename(shopName);
		user.changePhoneNumber(phone);
		try {
			users.flush();
		}
		catch (DataIntegrityViolationException ex) {
			throw UniqueConflicts.translate(ex);
		}
		int next = applications.findFirstBySellerIdOrderBySubmissionNoDesc(user.getUserId())
				.map(previous -> previous.getSubmissionNo() + 1).orElse(1);
		String description = request.description() == null || request.description().isBlank() ? null
				: request.description().strip();
		seller.reapply(description, now);
		SellerApplication application = applications.save(new SellerApplication(user, next, description, now));
		token.markUsed(now);
		outbox.write("seller.registered", user.getUserId(), EventActor.user(user.getUserId(), EventActor.Context.SELLER),
				Map.of("userId", user.getUserId(), "sellerId", user.getUserId(),
						"approvalStatus", SellerApprovalStatus.PENDING, "authzVersion", user.getAuthzVersion()));
		return new Result(user.getUserId(), application.getApplicationId(), SellerApprovalStatus.PENDING);
	}

	private void sendLink(User user) {
		String token = OpaqueTokens.generate();
		Instant expiresAt = clock.instant().plus(properties.ttl());
		tokens.save(new SellerReapplicationToken(user.getUserId(), OpaqueTokens.hash(token), expiresAt));
		String verificationUrl = UriComponentsBuilder.fromUriString(properties.linkBase())
				.queryParam("token", token)
				.build()
				.toUriString();
		outbox.write("seller.reapplication_requested", user.getUserId(),
				new EventActor(null, EventActor.Kind.USER, EventActor.Context.SELLER),
				Map.of("userId", user.getUserId(), "sellerId", user.getUserId(), "verificationUrl", verificationUrl,
						"expiresAt", expiresAt.toString()));
	}

	/** 201 of #31: the new application on the existing seller. */
	public record Result(UUID sellerId, UUID applicationId, SellerApprovalStatus approvalStatus) {
	}

}
