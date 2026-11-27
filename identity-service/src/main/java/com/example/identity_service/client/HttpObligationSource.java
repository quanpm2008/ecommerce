package com.example.identity_service.client;

import com.example.identity_service.config.ObligationProperties;
import com.example.identity_service.dto.AccountObligations;
import com.example.identity_service.exception.ApiException;
import com.example.identity_service.exception.ErrorCode;
import com.example.identity_service.entity.AccountType;
import com.example.identity_service.entity.User;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * {@link ObligationSource} over HTTP (API §7.1). Both answers come in the {@code data} envelope; a missing field is as
 * bad as no answer.
 */
@Slf4j
@Component
class HttpObligationSource implements ObligationSource {

	private final ObligationProperties properties;

	private final RestClient http;

	private final Clock clock;

	HttpObligationSource(ObligationProperties properties, Clock clock) {
		this.properties = properties;
		this.clock = clock;
		JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(properties.timeout()).build());
		requests.setReadTimeout(properties.timeout());
		RestClient.Builder builder = RestClient.builder().requestFactory(requests);
		if (StringUtils.hasText(properties.serviceToken())) {
			builder.defaultHeader("X-Service-Token", properties.serviceToken());
		}
		this.http = builder.build();
	}

	@Override
	public AccountObligations of(User account) {
		UUID userId = account.getUserId();
		Orders orders = fetch("Order", userId, () -> http.get()
				.uri(base(properties.orderBaseUrl()) + "/internal/v1/orders/accounts/{userId}/obligations?accountType={type}",
						userId, account.getAccountType().name())
				.retrieve().body(OrdersEnvelope.class), OrdersEnvelope::data);
		if (orders.hasOpenOrders() == null || orders.hasPendingPayment() == null || orders.hasUnresolvedRefund() == null) {
			throw unavailable("Order", userId, "incomplete answer");
		}
		if (account.getAccountType() != AccountType.SELLER) {
			return new AccountObligations(orders.hasOpenOrders(), orders.hasPendingPayment(),
					orders.hasUnresolvedRefund(), false, 0, 0, evaluatedAt(orders.evaluatedAt()));
		}
		Finance finance = fetch("Finance", userId, () -> http.get()
				.uri(base(properties.financeBaseUrl()) + "/internal/v1/finance/sellers/{sellerId}/obligations", userId)
				.retrieve().body(FinanceEnvelope.class), FinanceEnvelope::data);
		if (finance.hasPendingFinance() == null || finance.pendingBalanceVnd() == null
				|| finance.availableBalanceVnd() == null || finance.pendingBalanceVnd() < 0
				|| finance.availableBalanceVnd() < 0) {
			throw unavailable("Finance", userId, "incomplete or inconsistent answer");
		}
		return new AccountObligations(orders.hasOpenOrders(), orders.hasPendingPayment(), orders.hasUnresolvedRefund(),
				finance.hasPendingFinance(), finance.pendingBalanceVnd(), finance.availableBalanceVnd(),
				evaluatedAt(orders.evaluatedAt()));
	}

	private <E, T> T fetch(String source, UUID userId, java.util.function.Supplier<E> call,
			java.util.function.Function<E, T> data) {
		try {
			E envelope = call.get();
			T body = envelope == null ? null : data.apply(envelope);
			if (body == null) {
				throw unavailable(source, userId, "no data");
			}
			return body;
		}
		catch (RestClientException | IllegalArgumentException ex) {
			throw unavailable(source, userId, ex.getMessage());
		}
	}

	private Instant evaluatedAt(Instant reported) {
		return reported != null ? reported : clock.instant();
	}

	private static String base(String url) {
		if (!StringUtils.hasText(url)) {
			throw new IllegalArgumentException("not configured");
		}
		return url.replaceAll("/+$", "");
	}

	private static ApiException unavailable(String source, UUID userId, String why) {
		log.warn("{} obligations unavailable for account {}: {}", source, userId, why);
		return new ApiException(ErrorCode.SERVICE_UNAVAILABLE,
				"The account's obligations cannot be verified right now; nothing was changed");
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record OrdersEnvelope(Orders data) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Orders(Boolean hasOpenOrders, Boolean hasPendingPayment, Boolean hasUnresolvedRefund, Instant evaluatedAt) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record FinanceEnvelope(Finance data) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Finance(Boolean hasPendingFinance, Long pendingBalanceVnd, Long availableBalanceVnd, Instant evaluatedAt) {
	}

}
