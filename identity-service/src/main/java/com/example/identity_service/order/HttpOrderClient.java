package com.example.identity_service.order;

import com.example.identity_service.common.error.ApiException;
import com.example.identity_service.common.error.ErrorCode;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.util.UUID;

/**
 * {@code GET /internal/v1/orders/customers/{id}/has-paid-order} → {@code { data: { hasPaidOrder, evaluatedAt } }}.
 * Any failure, including a missing answer, is 503: a ban must never guess. Service credentials for internal calls
 * are not defined yet (step 8), so the request carries none.
 */
@Slf4j
@Component
class HttpOrderClient implements OrderClient {

	private final OrderProperties properties;

	private final RestClient http;

	HttpOrderClient(OrderProperties properties) {
		this.properties = properties;
		JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(properties.timeout()).build());
		requests.setReadTimeout(properties.timeout());
		this.http = RestClient.builder().requestFactory(requests).build();
	}

	@Override
	public boolean hasPaidOrder(UUID customerId) {
		try {
			Envelope response = http.get()
					.uri(properties.baseUrl() + "/internal/v1/orders/customers/{id}/has-paid-order", customerId)
					.retrieve().body(Envelope.class);
			if (response == null || response.data() == null || response.data().hasPaidOrder() == null) {
				throw new RestClientException("No hasPaidOrder in the response");
			}
			return response.data().hasPaidOrder();
		}
		catch (RestClientException | IllegalArgumentException ex) {
			log.warn("Order has-paid-order failed for customer {}: {}", customerId, ex.getMessage());
			throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "Order service is not available, nothing was changed");
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Envelope(PaidOrder data) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record PaidOrder(Boolean hasPaidOrder) {
	}

}
