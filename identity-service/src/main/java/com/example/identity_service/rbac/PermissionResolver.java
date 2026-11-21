package com.example.identity_service.rbac;

import com.example.identity_service.customer.CustomerRepository;
import com.example.identity_service.customer.CustomerStatus;
import com.example.identity_service.seller.SellerRepository;
import com.example.identity_service.seller.SellerStatus;
import com.example.identity_service.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Effective permissions: the baseline policy plus the permissions of the account's one active role, read from the
 * database every time. A DISABLED user has none. Profile status (BANNED, PENDING, ...) and ownership are checked
 * separately by each endpoint.
 */
@Service
@RequiredArgsConstructor
public class PermissionResolver {

	private final UserRoleRepository userRoles;

	private final CustomerRepository customers;

	private final SellerRepository sellers;

	/**
	 * A BANNED account (profile banned, user still ACTIVE) keeps only {@link BannedAllowlist} for its current
	 * {@code accessMode}, never more than its role grants.
	 */
	@Transactional(readOnly = true)
	public SortedSet<String> permissionsOf(User user) {
		SortedSet<String> permissions = new TreeSet<>();
		if (!user.isActive()) {
			return permissions;
		}
		permissions.addAll(Permissions.BASELINE);
		permissions.addAll(userRoles.findActivePermissionCodes(user.getUserId()));
		if (profileBanned(user)) {
			permissions.retainAll(BannedAllowlist.of(user.getAccountType(), user.getAccessMode()));
		}
		return permissions;
	}

	/** Whether the account's Customer or Seller profile is banned (G-14), so a denial is {@code ACCOUNT_BANNED}. */
	@Transactional(readOnly = true)
	public boolean isBanned(UUID userId) {
		return customers.findById(userId).map(customer -> customer.getStatus() == CustomerStatus.BANNED).orElse(false)
				|| sellers.findById(userId).map(seller -> seller.getStatus() == SellerStatus.BANNED).orElse(false);
	}

	private boolean profileBanned(User user) {
		return switch (user.getAccountType()) {
			case CUSTOMER -> customers.findById(user.getUserId())
					.map(customer -> customer.getStatus() == CustomerStatus.BANNED).orElse(false);
			case SELLER -> sellers.findById(user.getUserId())
					.map(seller -> seller.getStatus() == SellerStatus.BANNED).orElse(false);
			case ADMIN -> false;
		};
	}

	@Transactional(readOnly = true)
	public Set<RoleCode> activeRolesOf(User user) {
		return userRoles.findActiveRoleCodes(user.getUserId());
	}

}
