package com.example.order_service.service;

import com.example.order_service.client.PaymentCheckoutClient;
import com.example.order_service.dto.response.CheckoutResponse;
import com.example.order_service.exception.BuyNowPayRejected;
import com.example.order_service.repository.CheckoutReplayRepository;
import com.example.order_service.repository.CheckoutSessionJpaRepository;
import feign.FeignException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class CheckoutPaymentReadService {
	private final CheckoutReplayRepository checkouts;
	private final CheckoutSessionJpaRepository sessions;
	private final PaymentCheckoutClient payments;
	private final String credential;

	CheckoutPaymentReadService(CheckoutReplayRepository checkouts, CheckoutSessionJpaRepository sessions,
			PaymentCheckoutClient payments, @Value("${order.payment.service-token:}") String credential) {
		this.checkouts = checkouts;
		this.sessions = sessions;
		this.payments = payments;
		this.credential = credential;
	}

	public CheckoutResponse current(UUID customer) {
		return checkouts.current(customer).map(this::withPayment).orElse(null);
	}

	public CheckoutResponse detail(UUID checkoutId, UUID customer) {
		return withPayment(checkouts.owned(checkoutId, customer).orElseThrow(CheckoutPaymentReadService::notFound));
	}

	public CheckoutReplayRepository.Payable payable(UUID checkoutId) {
		return checkouts.payable(checkoutId).orElseThrow(CheckoutPaymentReadService::notFound);
	}

	public CheckoutResponse replay(CheckoutResponse snapshot) {
		if ("AWAITING_PAYMENT".equals(snapshot.status())
				&& !sessions.canExposeLink(snapshot.checkoutSessionId(), snapshot.expiresAt())) {
			return snapshot;
		}
		return withPayment(snapshot);
	}

	private CheckoutResponse withPayment(CheckoutResponse snapshot) {
		if (credential.isBlank()) {
			throw unavailable();
		}
		var checkoutId = snapshot.checkoutSessionId();
		PaymentCheckoutClient.Envelope envelope;
		try {
			envelope = payments.read(checkoutId, credential);
		} catch (FeignException failure) {
			throw unavailable();
		}
		if (envelope == null) {
			throw unavailable();
		}
		var payment = envelope.data();
		if (payment == null) {
			return snapshot;
		}
		if (!checkoutId.equals(payment.checkoutSessionId()) || payment.paymentId() == null || payment.status() == null) {
			throw unavailable();
		}
		String link = null;
		if ("AWAITING_PAYMENT".equals(snapshot.status()) && payment.status() == PaymentCheckoutClient.Status.PENDING
				&& payment.collectionClosedAt() == null && payment.expiresAt() != null
				&& payment.paymentUrl() != null && !payment.paymentUrl().isBlank()
				&& sessions.canExposeLink(checkoutId, payment.expiresAt())) {
			link = payment.paymentUrl();
		}
		return new CheckoutResponse(checkoutId, snapshot.source(), snapshot.status(), snapshot.closeReason(),
				payment.status().name(), link, snapshot.totalAmountVnd(), snapshot.shippingFeeVnd(),
				snapshot.expiresAt(), snapshot.paidAt(), snapshot.shippingAddress(), snapshot.orders(),
				link == null ? List.of() : List.of("OPEN_PAYMENT_LINK"), payment.isSimulated());
	}

	private static BuyNowPayRejected unavailable() {
		return new BuyNowPayRejected(503, "SERVICE_UNAVAILABLE");
	}

	private static BuyNowPayRejected notFound() {
		return new BuyNowPayRejected(404, "NOT_FOUND");
	}
}
