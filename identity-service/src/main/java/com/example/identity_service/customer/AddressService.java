package com.example.identity_service.customer;

import com.example.identity_service.common.error.ApiException;
import com.example.identity_service.common.error.ErrorCode;
import com.example.identity_service.common.security.Caller;
import com.example.identity_service.region.RegionCatalog;
import com.example.identity_service.user.PhoneNumbers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * The caller's address book (UC-A06, DB §3.1.4). Every lookup is scoped to {@code customerId = sub}, so another
 * customer's address is simply not found (404). Reading needs a Customer profile; changing it needs an ACTIVE one.
 */
@Service
@RequiredArgsConstructor
public class AddressService {

	private final CustomerRepository customers;

	private final CustomerAddressRepository addresses;

	private final RegionCatalog regions;

	private final AddressProperties properties;

	private final Clock clock;

	@Transactional(readOnly = true)
	public List<Address> list(Caller caller) {
		requireCustomer(caller);
		List<CustomerAddress> book = addresses.findBook(caller.userId());
		UUID defaultId = book.isEmpty() ? null : book.getFirst().getAddressId();
		return book.stream().map(address -> view(address, defaultId)).toList();
	}

	public int maxAddresses() {
		return properties.maxPerCustomer();
	}

	@Transactional
	public Address create(Caller caller, AddressRequest request) {
		Customer customer = customers.findLockedById(caller.userId()).orElseThrow(AddressService::noCustomer);
		requireActive(customer);
		if (addresses.countByCustomerIdAndDeletedAtIsNull(customer.getCustomerId()) >= properties.maxPerCustomer()) {
			throw new ApiException(ErrorCode.ADDRESS_LIMIT_EXCEEDED);
		}
		String phone = recipientPhone(request);
		regions.requireWardInProvince(request.provinceCode(), request.wardCode());
		CustomerAddress address = addresses.saveAndFlush(new CustomerAddress(customer.getCustomerId(),
				request.recipientName().strip(), phone, request.provinceCode(), request.wardCode(),
				request.addressDetail().strip()));
		return view(address, defaultIdOf(customer.getCustomerId()));
	}

	@Transactional
	public Address update(Caller caller, UUID addressId, AddressRequest request) {
		Customer customer = customers.findLockedById(caller.userId()).orElseThrow(AddressService::noCustomer);
		requireActive(customer);
		CustomerAddress address = owned(caller, addressId);
		String phone = recipientPhone(request);
		regions.requireWardInProvince(request.provinceCode(), request.wardCode());
		address.update(request.recipientName().strip(), phone, request.provinceCode(), request.wardCode(),
				request.addressDetail().strip());
		addresses.flush();
		return view(address, defaultIdOf(customer.getCustomerId()));
	}

	/** Deleting the last address is allowed. An address already used by a checkout is only hidden (G-13). */
	@Transactional
	public void delete(Caller caller, UUID addressId) {
		Customer customer = customers.findLockedById(caller.userId()).orElseThrow(AddressService::noCustomer);
		requireActive(customer);
		CustomerAddress address = owned(caller, addressId);
		if (address.mustBeKept()) {
			address.markDeleted(clock.instant());
		}
		else {
			addresses.delete(address);
		}
	}

	private CustomerAddress owned(Caller caller, UUID addressId) {
		return addresses.findByAddressIdAndCustomerIdAndDeletedAtIsNull(addressId, caller.userId())
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
	}

	private UUID defaultIdOf(UUID customerId) {
		List<CustomerAddress> book = addresses.findBook(customerId);
		return book.isEmpty() ? null : book.getFirst().getAddressId();
	}

	private Address view(CustomerAddress address, UUID defaultId) {
		RegionCatalog.Names names = regions.names(address.getProvinceCode(), address.getWardCode())
				.orElse(new RegionCatalog.Names(null, null));
		return new Address(address.getAddressId(), address.getRecipientName(), address.getRecipientPhone(),
				address.getProvinceCode(), names.provinceName(), address.getWardCode(), names.wardName(),
				address.getAddressDetail(), address.getLastUsedAt(), address.getAddressId().equals(defaultId),
				address.getCreatedAt());
	}

	private void requireCustomer(Caller caller) {
		if (!customers.existsById(caller.userId())) {
			throw noCustomer();
		}
	}

	private static void requireActive(Customer customer) {
		if (customer.getStatus() != CustomerStatus.ACTIVE) {
			throw new ApiException(ErrorCode.FORBIDDEN, "A banned customer cannot change the address book");
		}
	}

	private static String recipientPhone(AddressRequest request) {
		return PhoneNumbers.normalize(request.recipientPhone())
				.orElseThrow(() -> ApiException.forField(ErrorCode.VALIDATION_ERROR, "recipientPhone", "INVALID_PHONE"));
	}

	private static ApiException noCustomer() {
		return new ApiException(ErrorCode.FORBIDDEN, "A Customer profile is required");
	}

}
