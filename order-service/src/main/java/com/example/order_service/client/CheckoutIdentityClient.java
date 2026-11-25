package com.example.order_service.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
public class CheckoutIdentityClient {
	private final RestClient identity;
	private final String serviceToken;

	public CheckoutIdentityClient(@Value("${order.identity.base-url:http://localhost:8083}") String baseUrl,
			@Value("${order.identity.service-token:}") String serviceToken) {
		var requests = new SimpleClientHttpRequestFactory();
		requests.setConnectTimeout(Duration.ofSeconds(2));
		requests.setReadTimeout(Duration.ofSeconds(2));
		this.identity = RestClient.builder().baseUrl(baseUrl).requestFactory(requests).build();
		this.serviceToken = serviceToken;
	}

	public Eligibility eligibility(UUID customerId) {
		Eligibility data = read(internal("/internal/v1/identity/customers/{id}/checkout-eligibility", customerId),
				new ParameterizedTypeReference<Envelope<Eligibility>>() {});
		if (!customerId.equals(data.userId()) || data.userStatus() == null || data.customerStatus() == null
				|| data.paymentBlocked() == null || data.hasPhone() == null || data.addressCount() == null
				|| data.addressCount() < 0 || data.canCheckout() == null || data.authzVersion() == null) {
			throw new Unavailable();
		}
		return data;
	}

	public boolean sellerIsSellable(UUID sellerId) {
		List<SellerStatus> statuses = read(internal("/internal/v1/identity/sellers/status?ids={id}", sellerId),
				new ParameterizedTypeReference<Envelope<List<SellerStatus>>>() {});
		if (statuses.size() != 1 || statuses.getFirst() == null || !sellerId.equals(statuses.getFirst().sellerId())
				|| statuses.getFirst().sellable() == null) {
			throw new Unavailable();
		}
		return statuses.getFirst().sellable();
	}

	public Address defaultAddress(String bearer) {
		List<Address> addresses = read(identity.get().uri("/api/v1/identity/customers/me/addresses")
				.headers(headers -> headers.setBearerAuth(bearer)),
				new ParameterizedTypeReference<Envelope<List<Address>>>() {});
		if (addresses.stream().anyMatch(address -> address == null || address.isDefault() == null)) {
			throw new Unavailable();
		}
		List<Address> defaults = addresses.stream().filter(Address::isDefault).toList();
		if (addresses.isEmpty()) {
			return null;
		}
		if (defaults.size() != 1 || !defaults.getFirst().complete()) {
			throw new Unavailable();
		}
		return defaults.getFirst();
	}

	public Seller seller(UUID sellerId) {
		Seller seller = read(identity.get().uri("/api/v1/identity/sellers/{id}", sellerId),
				new ParameterizedTypeReference<Envelope<Seller>>() {});
		if (!sellerId.equals(seller.sellerId()) || seller.sellerName() == null || seller.sellerName().isBlank()) {
			throw new Unavailable();
		}
		return seller;
	}

	private RestClient.RequestHeadersSpec<?> internal(String uri, UUID id) {
		if (serviceToken.isBlank()) {
			throw new Unavailable();
		}
		return identity.get().uri(uri, id).header("X-Service-Token", serviceToken);
	}

	private <T> T read(RestClient.RequestHeadersSpec<?> request, ParameterizedTypeReference<Envelope<T>> type) {
		try {
			Envelope<T> body = request.retrieve().body(type);
			if (body != null && body.data() != null) {
				return body.data();
			}
		} catch (RestClientException error) {
			throw new Unavailable();
		}
		throw new Unavailable();
	}

	private record Envelope<T>(T data) {
	}

	public record Eligibility(UUID userId, String userStatus, String customerStatus, Boolean paymentBlocked,
			Boolean hasPhone, Integer addressCount, Boolean canCheckout, Long authzVersion) {
	}

	private record SellerStatus(UUID sellerId, Boolean sellable) {
	}

	public record Seller(UUID sellerId, String sellerName) {
	}

	public record Address(UUID addressId, String recipientName, String recipientPhone, String provinceCode,
			String provinceName, String wardCode, String wardName, String addressDetail, Instant lastUsedAt,
			Boolean isDefault, Instant createdAt) {
		boolean complete() {
			return addressId != null && createdAt != null && java.util.stream.Stream.of(recipientName, recipientPhone,
					provinceCode, provinceName, wardCode, wardName, addressDetail).allMatch(Objects::nonNull);
		}
	}

	public static class Unavailable extends RuntimeException {
	}
}
