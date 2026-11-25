package com.example.order_service.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;

@Component
public class FinanceCommissionClient {
	private final RestClient finance;
	private final String token;

	public FinanceCommissionClient(@Value("${order.finance.base-url:http://localhost:8086}") String baseUrl,
			@Value("${order.finance.service-token:}") String token) {
		var requests = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
		requests.setReadTimeout(Duration.ofSeconds(2));
		this.finance = RestClient.builder().baseUrl(baseUrl).requestFactory(requests).build();
		this.token = token;
	}

	public int commissionBps() {
		if (!token.isBlank()) {
			try {
				var response = finance.get().uri("/internal/v1/finance/commission-rate").header("X-Service-Token", token)
						.retrieve().body(CommissionResponse.class);
				if (response != null && response.data() != null && Integer.valueOf(500).equals(response.data().commissionBps())) {
					return response.data().commissionBps();
				}
			} catch (RestClientException upstream) {
				throw new Unavailable();
			}
		}
		throw new Unavailable();
	}

	private record CommissionResponse(Commission data) {
	}

	private record Commission(Integer commissionBps) {
	}

	public static final class Unavailable extends RuntimeException {
	}
}
