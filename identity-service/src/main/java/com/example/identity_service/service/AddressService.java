package com.example.identity_service.service;

import com.example.identity_service.config.AddressProperties;
import com.example.identity_service.dto.Address;
import com.example.identity_service.dto.AddressRequest;
import com.example.identity_service.entity.Customer;
import com.example.identity_service.entity.CustomerAddress;
import com.example.identity_service.entity.CustomerStatus;
import com.example.identity_service.repository.CustomerAddressRepository;
import com.example.identity_service.repository.CustomerRepository;
import com.example.identity_service.exception.ApiException;
import com.example.identity_service.exception.ErrorCode;
import com.example.identity_service.security.Caller;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * The caller's address book (UC-A06, DB §3.1.4). Every lookup is scoped to {@code customerId = sub}, so another
 * customer's address is simply not found (404). It needs an ACTIVE Customer profile: a banned customer keeps only its
 * obligations and gets 403 {@code ACCOUNT_BANNED}, reading included (API §3.2). At most
 * {@code identity.address.max-per-customer} addresses (3, P-09), also under concurrent requests (the customer row is
 * locked); a deleted address is removed and frees its place.
 */
@Service
@RequiredArgsConstructor
public class AddressService {

	private final CustomerRepository customers;

	private final CustomerAddressRepository addresses;

	private final RegionCatalog regions;

	private final AddressProperties properties;


	@Transactional(readOnly = true)
	public List<Address> list(Caller caller) {
		requireActive(customers.findById(caller.userId()).orElseThrow(AddressService::noCustomer));
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
		if (addresses.countByCustomerId(customer.getCustomerId()) >= properties.maxPerCustomer()) {
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

	/** Removes the address, the last one included; orders keep their own snapshot (P-09, G-13). */
	@Transactional
	public void delete(Caller caller, UUID addressId) {
		Customer customer = customers.findLockedById(caller.userId()).orElseThrow(AddressService::noCustomer);
		requireActive(customer);
		addresses.delete(owned(caller, addressId));
	}

	private CustomerAddress owned(Caller caller, UUID addressId) {
		return addresses.findByAddressIdAndCustomerId(addressId, caller.userId())
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

	private static void requireActive(Customer customer) {
		if (customer.getStatus() != CustomerStatus.ACTIVE) {
			throw new ApiException(ErrorCode.ACCOUNT_BANNED);
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
