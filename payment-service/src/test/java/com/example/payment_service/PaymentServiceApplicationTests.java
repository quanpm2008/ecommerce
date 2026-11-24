package com.example.payment_service;

import com.example.payment_service.entity.Payment;
import com.example.payment_service.entity.Refund;
import com.example.payment_service.repository.PaymentRepository;
import com.example.payment_service.repository.RefundRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class PaymentServiceApplicationTests {
	@Autowired
	private PaymentRepository payments;

	@Autowired
	private RefundRepository refunds;

	@Autowired
	private EntityManager entities;

	@Test
	void persistsPaymentLinkAndCountsFailedRefundObligations() {
		var key = UUID.randomUUID().toString();
		var expiry = Instant.now().truncatedTo(ChronoUnit.SECONDS).plusSeconds(900);
		var payment = new Payment(UUID.randomUUID(), UUID.randomUUID(), 2000L, "VND", "DEMO_PROVIDER", "TEST", key);
		payment.setExpiresAt(expiry);
		payment.setPaymentUrl("https://provider.example.invalid/checkout/test");
		payment.markSuccess("charge-" + key, Instant.now());
		payments.saveAndFlush(payment);

		var refund = Refund.createFullCheckoutRefund(payment.getPaymentId(), 2000L, "refund-" + key);
		refund.markFailed(null, Instant.now());
		refunds.saveAndFlush(refund);
		entities.clear();

		var stored = payments.findById(payment.getPaymentId()).orElseThrow();
		assertThat(stored.getExpiresAt()).isEqualTo(expiry);
		assertThat(stored.getPaymentUrl()).isEqualTo("https://provider.example.invalid/checkout/test");
		assertThat(refunds.sumRefundAmountByPaymentId(stored.getPaymentId())).isEqualTo(2000L);
		assertThat(refunds.findById(refund.getRefundId()).orElseThrow().getRetryCycle()).isZero();
	}
}
