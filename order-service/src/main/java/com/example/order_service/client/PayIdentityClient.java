package com.example.order_service.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

@Component
public class PayIdentityClient {
	private static final Logger LOG = LoggerFactory.getLogger(PayIdentityClient.class);
	private static final String ADDRESS = "/internal/v1/identity/customers/{id}/addresses/{addressId}";
	private final RestClient identity;
	private final String token;

	public PayIdentityClient(@Value("${order.identity.base-url:http://localhost:8083}") String baseUrl,
			@Value("${order.identity.service-token:}") String token) {
		var requests = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
		requests.setReadTimeout(Duration.ofSeconds(2));
		this.identity = RestClient.builder().baseUrl(baseUrl).requestFactory(requests).build();
		this.token = token;
	}

	public ShippingAddress address(UUID customerId, UUID addressId) {
		if (token.isBlank()) {
			throw new Unavailable();
		}
		try {
			var body = identity.get().uri(ADDRESS, customerId, addressId).header("X-Service-Token", token)
					.retrieve().body(AddressResponse.class);
			if (body != null && body.data() != null && body.data().complete()) {
				return body.data();
			}
		} catch (RestClientResponseException failure) {
			if (failure.getStatusCode().value() == 404) {
				throw new AddressNotFound();
			}
			throw new Unavailable();
		} catch (RestClientException failure) {
			throw new Unavailable();
		}
		throw new Unavailable();
	}

	public void touch(UUID customerId, UUID addressId, UUID sessionId) {
		try {
			identity.post().uri(ADDRESS + "/touch", customerId, addressId).header("X-Service-Token", token)
					.body(Map.of("checkoutSessionId", sessionId)).retrieve().toBodilessEntity();
		} catch (RestClientResponseException upstream) {
			LOG.warn("Address touch failed after checkout commit: session={}, status={}", sessionId, upstream.getStatusCode().value());
		} catch (RestClientException upstream) {
			LOG.warn("Address touch unavailable after checkout commit: session={}", sessionId);
		}
	}

	private record AddressResponse(ShippingAddress data) {
	}

	public record ShippingAddress(String recipientName, String recipientPhone, String provinceCode, String provinceName,
			String wardCode, String wardName, String addressDetail) {
		boolean complete() {
			return Stream.of(recipientName, recipientPhone, provinceCode, provinceName, wardCode, wardName, addressDetail)
					.allMatch(value -> Objects.nonNull(value) && !value.isBlank());
		}
	}

	public static final class AddressNotFound extends RuntimeException {
	}

	public static final class Unavailable extends RuntimeException {
	}
}
