package com.example.identity_service.internal;

import com.example.identity_service.common.error.ApiException;
import com.example.identity_service.common.error.ErrorCode;
import com.example.identity_service.customer.Customer;
import com.example.identity_service.customer.CustomerAddress;
import com.example.identity_service.customer.CustomerAddressRepository;
import com.example.identity_service.customer.CustomerRepository;
import com.example.identity_service.customer.CustomerStatus;
import com.example.identity_service.rbac.PermissionResolver;
import com.example.identity_service.rbac.RoleCode;
import com.example.identity_service.rbac.UserRoleRepository;
import com.example.identity_service.region.RegionCatalog;
import com.example.identity_service.seller.Seller;
import com.example.identity_service.seller.SellerApprovalStatus;
import com.example.identity_service.seller.SellerRepository;
import com.example.identity_service.seller.SellerStatus;
import com.example.identity_service.user.User;
import com.example.identity_service.user.UserRepository;
import com.example.identity_service.user.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * What other services read from Identity (API §7.1), always the current state with no caching. Ids come from the
 * calling service, which took them from a verified token or a trusted event.
 */
@Service
@RequiredArgsConstructor
public class InternalQueries {

	private final UserRepository users;

	private final CustomerRepository customers;

	private final CustomerAddressRepository addresses;

	private final SellerRepository sellers;

	private final UserRoleRepository userRoles;

	private final PermissionResolver permissions;

	private final RegionCatalog regions;

	private final Clock clock;

	/**
	 * One entry per known id, in request order; unknown ids are left out. {@code sellable} is G-06: User ACTIVE,
	 * Seller APPROVED + ACTIVE and the SELLER role held. No PII.
	 */
	@Transactional(readOnly = true)
	public List<SellerStatusView> sellerStatuses(List<UUID> sellerIds) {
		Map<UUID, Seller> found = sellers.findWithUserByIdIn(sellerIds).stream()
				.collect(Collectors.toMap(Seller::getSellerId, Function.identity()));
		Set<UUID> roleHolders = found.isEmpty() ? Set.of()
				: userRoles.findHoldersOfActiveRole(found.keySet(), RoleCode.SELLER);
		return sellerIds.stream().distinct().filter(found::containsKey).map(id -> {
			Seller seller = found.get(id);
			User user = seller.getUser();
			boolean roleActive = roleHolders.contains(id);
			return new SellerStatusView(id, user.getUserId(), user.getStatus(), seller.getApprovalStatus(),
					seller.getStatus(), roleActive, user.isActive() && seller.canOperate() && roleActive);
		}).toList();
	}

	/**
	 * Whether the customer may check out now: User ACTIVE with the CUSTOMER role, Customer ACTIVE (any ban blocks new
	 * payments, G-14), a phone number and at least one address.
	 */
	@Transactional(readOnly = true)
	public CheckoutEligibility checkoutEligibility(UUID customerId) {
		Customer customer = customers.findById(customerId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Customer not found"));
		User user = customer.getUser();
		boolean customerRole = permissions.activeRolesOf(user).contains(RoleCode.CUSTOMER);
		boolean paymentBlocked = customer.getStatus() == CustomerStatus.BANNED;
		boolean hasPhone = user.getPhoneNumber() != null;
		long addressCount = addresses.countByCustomerIdAndDeletedAtIsNull(customerId);
		boolean canCheckout = user.isActive() && customerRole && !paymentBlocked && hasPhone && addressCount > 0;
		return new CheckoutEligibility(user.getUserId(), user.getStatus(), customer.getStatus(), paymentBlocked,
				hasPhone, addressCount, canCheckout, user.getAuthzVersion());
	}

	/** A complete snapshot for an order: another customer's or a deleted address is 404; names are required. */
	@Transactional(readOnly = true)
	public AddressSnapshot address(UUID customerId, UUID addressId) {
		CustomerAddress address = ownedAddress(customerId, addressId);
		RegionCatalog.Names names = regions.requireNames(address.getProvinceCode(), address.getWardCode());
		return new AddressSnapshot(address.getAddressId(), address.getCustomerId(), address.getRecipientName(),
				address.getRecipientPhone(), address.getProvinceCode(), names.provinceName(), address.getWardCode(),
				names.wardName(), address.getAddressDetail());
	}

	/** After a checkout commits: {@code lastUsedAt} = now, once per checkout session; retries change nothing. */
	@Transactional
	public AddressTouch touch(UUID customerId, UUID addressId, String checkoutSessionId) {
		CustomerAddress address = ownedAddress(customerId, addressId);
		boolean first = addresses.recordTouch(addressId, checkoutSessionId) == 1;
		if (first) {
			address.markUsed(clock.instant());
		}
		return new AddressTouch(address.getAddressId(), address.getLastUsedAt(), first);
	}

	@Transactional(readOnly = true)
	public Contact contact(UUID userId) {
		User user = users.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found"));
		return new Contact(user.getUserId(), user.getEmail(), user.getFullName());
	}

	private CustomerAddress ownedAddress(UUID customerId, UUID addressId) {
		return addresses.findByAddressIdAndCustomerIdAndDeletedAtIsNull(addressId, customerId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Address not found"));
	}

	public record SellerStatusView(UUID sellerId, UUID userId, UserStatus userStatus,
			SellerApprovalStatus approvalStatus, SellerStatus status, boolean sellerRoleActive, boolean sellable) {
	}

	public record CheckoutEligibility(UUID userId, UserStatus userStatus, CustomerStatus customerStatus,
			boolean paymentBlocked, boolean hasPhone, long addressCount, boolean canCheckout, long authzVersion) {
	}

	public record AddressSnapshot(UUID addressId, UUID customerId, String recipientName, String recipientPhone,
			String provinceCode, String provinceName, String wardCode, String wardName, String addressDetail) {
	}

	/** {@code applied} is false when this checkout session had already touched the address. */
	public record AddressTouch(UUID addressId, Instant lastUsedAt, boolean applied) {
	}

	public record Contact(UUID userId, String email, String fullName) {
	}

}
