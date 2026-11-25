package com.example.order_service.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Component
public class ProductBatchClient {
	private final RestClient product;
	private final String serviceToken;

	public ProductBatchClient(@Value("${order.product.base-url:http://localhost:8087}") String baseUrl,
			@Value("${order.product.service-token:}") String serviceToken) {
		var requests = new SimpleClientHttpRequestFactory();
		requests.setConnectTimeout(Duration.ofSeconds(2));
		requests.setReadTimeout(Duration.ofSeconds(2));
		this.product = RestClient.builder().baseUrl(baseUrl).requestFactory(requests).build();
		this.serviceToken = serviceToken;
	}

	public List<ProductInfo> getProducts(List<UUID> productIds) {
		if (serviceToken.isBlank()) {
			throw new ProductBatchUnavailable();
		}
		var request = new BatchRequest(List.copyOf(productIds));
		BatchResponse response;
		try {
			response = product.post().uri("/internal/v1/products/batch")
					.header("X-Service-Token", serviceToken).body(request).retrieve().body(BatchResponse.class);
		} catch (RestClientException upstream) {
			throw new ProductBatchUnavailable();
		}
		if (response == null || response.data() == null) {
			throw new ProductBatchUnavailable();
		}
		var requested = new HashSet<>(request.productIds());
		for (ProductInfo item : response.data()) {
			if (item == null || !requested.remove(item.productId()) || item.sellerId() == null
					|| item.productName() == null || item.priceVnd() == null || item.sellable() == null
					|| item.media() == null || !item.media().isArray()) {
				throw new ProductBatchUnavailable();
			}
		}
		return List.copyOf(response.data());
	}

	private record BatchRequest(List<UUID> productIds) {
	}

	private record BatchResponse(List<ProductInfo> data) {
	}

	public record ProductInfo(UUID productId, UUID sellerId, Long priceVnd, String productName, String coverImageUrl,
			JsonNode media, String status, Boolean sellable) {
	}

	public static final class ProductBatchUnavailable extends RuntimeException {
		private ProductBatchUnavailable() {
			super("Không thể xác nhận dữ liệu từ Product");
		}
	}
}
