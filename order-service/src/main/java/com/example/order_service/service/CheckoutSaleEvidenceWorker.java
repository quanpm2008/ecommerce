package com.example.order_service.service;

import com.example.order_service.client.IdentitySaleEvidenceClient;
import com.example.order_service.client.ProductSaleEvidenceClient;
import com.example.order_service.repository.CheckoutSaleEvidenceRepository;
import com.example.order_service.repository.CheckoutSaleEvidenceRepository.Work;
import feign.FeignException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
@ConditionalOnProperty("order.kafka.bootstrap-servers")
public class CheckoutSaleEvidenceWorker {
	public enum Decision { ELIGIBLE, INELIGIBLE, UNKNOWN }

	private final CheckoutSaleEvidenceRepository work;
	private final CheckoutOutcomeService outcomes;
	private final IdentitySaleEvidenceClient identity;
	private final ProductSaleEvidenceClient product;
	private final String identityCredential;
	private final String productCredential;

	CheckoutSaleEvidenceWorker(CheckoutSaleEvidenceRepository work, CheckoutOutcomeService outcomes,
			IdentitySaleEvidenceClient identity, ProductSaleEvidenceClient product,
			@Value("${order.identity.service-token:}") String identityCredential,
			@Value("${order.product.service-token:}") String productCredential) {
		this.work = work;
		this.outcomes = outcomes;
		this.identity = identity;
		this.product = product;
		this.identityCredential = identityCredential;
		this.productCredential = productCredential;
	}

	@Scheduled(initialDelayString = "${order.checkout.evidence.poll-delay-ms:1000}",
			fixedDelayString = "${order.checkout.evidence.poll-delay-ms:1000}")
	public void poll() {
		for (int i = 0; i < 5 && runOnce(); i++) {
			// Bound the work of this scheduler tick.
		}
	}

	public boolean runOnce() {
		var next = work.claim(UUID.randomUUID());
		if (next.isEmpty()) {
			return false;
		}
		var claim = next.orElseThrow();
		var products = outcomes.evidenceSnapshot(claim.checkoutId());
		var decision = products.isEmpty() ? Decision.UNKNOWN : readEvidence(claim, products);
		outcomes.applyEvidence(claim, decision);
		return true;
	}

	private Decision readEvidence(Work claim, Map<UUID, UUID> products) {
		if (identityCredential.isBlank() || productCredential.isBlank()) {
			return Decision.UNKNOWN;
		}
		var sellers = Set.copyOf(products.values());
		var completedAt = claim.completedAt().toString();
		try {
			var identityEnvelope = identity.read(identityCredential, new IdentitySaleEvidenceClient.Request(
					completedAt, sellers.stream().sorted().toList()));
			var sellerDecision = sellerDecision(identityEnvelope, sellers, claim.completedAt());
			if (sellerDecision == Decision.UNKNOWN) {
				return Decision.UNKNOWN;
			}
			var productEnvelope = product.read(productCredential, new ProductSaleEvidenceClient.Request(
					completedAt, products.keySet().stream().sorted().toList()));
			var productDecision = productDecision(productEnvelope, products, claim.completedAt());
			if (productDecision == Decision.UNKNOWN) {
				return Decision.UNKNOWN;
			}
			return sellerDecision == Decision.INELIGIBLE || productDecision == Decision.INELIGIBLE
					? Decision.INELIGIBLE : Decision.ELIGIBLE;
		} catch (FeignException | IllegalArgumentException | DateTimeParseException malformedOrUnavailable) {
			return Decision.UNKNOWN;
		}
	}

	private static Decision sellerDecision(IdentitySaleEvidenceClient.Envelope envelope, Set<UUID> expected, Instant at) {
		var data = envelope == null ? null : envelope.data();
		if (data == null || !sameTimestamp(data.completedAt(), at) || data.items() == null
				|| data.items().size() != expected.size()) {
			return Decision.UNKNOWN;
		}
		var seen = new HashSet<UUID>();
		Decision result = Decision.ELIGIBLE;
		for (var item : data.items()) {
			if (item == null || item.sellerId() == null || !expected.contains(item.sellerId()) || !seen.add(item.sellerId())) {
				return Decision.UNKNOWN;
			}
			var decision = parseDecision(item.decision());
			if (decision == Decision.UNKNOWN) {
				return Decision.UNKNOWN;
			}
			if (decision == Decision.INELIGIBLE) {
				result = Decision.INELIGIBLE;
			}
		}
		return result;
	}

	private static Decision productDecision(ProductSaleEvidenceClient.Envelope envelope, Map<UUID, UUID> expected, Instant at) {
		var data = envelope == null ? null : envelope.data();
		if (data == null || !sameTimestamp(data.completedAt(), at) || data.items() == null
				|| data.items().size() != expected.size()) {
			return Decision.UNKNOWN;
		}
		var seen = new HashSet<UUID>();
		Decision result = Decision.ELIGIBLE;
		for (var item : data.items()) {
			if (item == null || item.productId() == null || !expected.containsKey(item.productId()) || !seen.add(item.productId())) {
				return Decision.UNKNOWN;
			}
			var decision = parseDecision(item.decision());
			UUID seller = expected.get(item.productId());
			if (decision == Decision.UNKNOWN || (item.sellerId() == null && decision == Decision.ELIGIBLE)
					|| (item.sellerId() != null && !seller.equals(item.sellerId()))) {
				return Decision.UNKNOWN;
			}
			if (decision == Decision.INELIGIBLE) {
				result = Decision.INELIGIBLE;
			}
		}
		return result;
	}

	private static Decision parseDecision(String decision) {
		return decision == null ? Decision.UNKNOWN : Decision.valueOf(decision);
	}

	private static boolean sameTimestamp(String value, Instant expected) {
		return value != null && expected.equals(Instant.parse(value));
	}
}
