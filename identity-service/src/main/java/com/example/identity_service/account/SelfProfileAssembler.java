package com.example.identity_service.account;

import com.example.identity_service.customer.CustomerAddressRepository;
import com.example.identity_service.customer.CustomerRepository;
import com.example.identity_service.rbac.PermissionResolver;
import com.example.identity_service.seller.SellerRepository;
import com.example.identity_service.user.User;
import com.example.identity_service.user.UserRepository;
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

	private final PermissionResolver permissions;

	@Transactional(readOnly = true)
	public UserSelfProfile of(UUID userId) {
		User user = users.findById(userId).orElseThrow();
		CustomerProfile customer = customers.findById(userId)
				.map(c -> CustomerProfile.of(c, user.getPhoneNumber() != null
						&& addresses.countByCustomerIdAndDeletedAtIsNull(userId) > 0))
				.orElse(null);
		SellerSelfProfile seller = sellers.findById(userId).map(SellerSelfProfile::of).orElse(null);
		return new UserSelfProfile(user.getUserId(), user.getEmail(), user.getAuthMethod(), user.getFullName(),
				user.getPhoneNumber(), user.getStatus(),
				permissions.activeRolesOf(user).stream().map(Enum::name).sorted().toList(),
				permissions.permissionsOf(user).stream().toList(), user.getAuthzVersion(), customer, seller,
				user.getCreatedAt());
	}

}
