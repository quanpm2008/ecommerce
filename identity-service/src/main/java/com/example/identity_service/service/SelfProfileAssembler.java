package com.example.identity_service.service;

import com.example.identity_service.dto.CustomerProfile;
import com.example.identity_service.dto.SellerSelfProfile;
import com.example.identity_service.dto.UserSelfProfile;
import com.example.identity_service.entity.Customer;
import com.example.identity_service.repository.CustomerAddressRepository;
import com.example.identity_service.repository.CustomerRepository;
import com.example.identity_service.client.MediaStorage;
import com.example.identity_service.entity.Seller;
import com.example.identity_service.entity.SellerApplication;
import com.example.identity_service.repository.SellerApplicationRepository;
import com.example.identity_service.repository.SellerRepository;
import com.example.identity_service.entity.CustomerStatus;
import com.example.identity_service.entity.SellerStatus;
import com.example.identity_service.entity.AccountStatus;
import com.example.identity_service.entity.User;
import com.example.identity_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Builds {@link UserSelfProfile} from current database state, never from token contents.
 */
@Component
@RequiredArgsConstructor
public class SelfProfileAssembler {

	private final UserRepository users;

	private final CustomerRepository customers;

	private final CustomerAddressRepository addresses;

	private final SellerRepository sellers;

	private final SellerApplicationRepository applications;

	private final PermissionResolver permissions;

	private final MediaStorage media;

	@Transactional(readOnly = true)
	public UserSelfProfile of(UUID userId) {
		User user = users.findById(userId).orElseThrow();
		CustomerProfile customer = customers.findById(userId).map(this::customer).orElse(null);
		SellerSelfProfile seller = sellers.findById(userId).map(this::seller).orElse(null);
		boolean profileBanned = customer != null && customer.status() == CustomerStatus.BANNED
				|| seller != null && seller.status() == SellerStatus.BANNED;
		return new UserSelfProfile(user.getUserId(), user.getAccountType(), user.getEmail(), user.getAuthMethod(),
				user.getFullName(), user.getPhoneNumber(), user.getAvatarObjectKey(), avatarUrl(user),
				AccountStatus.of(user, profileBanned), user.getStatus(), user.getBannedAt(), user.getDisabledAt(),
				user.getRestoreUntil(), permissions.activeRolesOf(user).stream().map(Enum::name).sorted().toList(),
				permissions.permissionsOf(user).stream().toList(), user.getAuthzVersion(), user.getAccessMode(),
				customer, seller, user.getCreatedAt());
	}


	/** {@code profileComplete} = the user has a phone number and at least one address. */
	public CustomerProfile customer(Customer customer) {
		return CustomerProfile.of(customer, customer.getUser().getPhoneNumber() != null
				&& addresses.countByCustomerId(customer.getCustomerId()) > 0);
	}

	public SellerSelfProfile seller(Seller seller) {
		return SellerSelfProfile.of(seller, avatarUrl(seller.getUser()),
				applications.findFirstBySellerIdOrderBySubmissionNoDesc(seller.getSellerId())
						.map(SellerApplication::getApplicationId).orElse(null));
	}

	/** A time-limited read URL of the account's avatar (for a seller, the shop logo), or null. */
	public String avatarUrl(User user) {
		return user.getAvatarObjectKey() == null ? null : media.readUrl(user.getAvatarObjectKey());
	}

}
