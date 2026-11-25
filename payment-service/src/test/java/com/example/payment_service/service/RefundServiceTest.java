package com.example.payment_service.service;

import com.example.payment_service.client.PaymentGateway;
import com.example.payment_service.client.RefundCommand;
import com.example.payment_service.client.RefundResult;
import com.example.payment_service.entity.*;
import com.example.payment_service.exception.RefundLimitExceededException;
import com.example.payment_service.exception.RefundRetryNotSafeException;
import com.example.payment_service.repository.OutboxEventRepository;
import com.example.payment_service.repository.PaymentRepository;
import com.example.payment_service.repository.RefundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private PaymentGateway paymentGateway;

    private RefundService refundService;

    @BeforeEach
    void setUp() {
        refundService = new RefundService(refundRepository, paymentRepository, outboxEventRepository, paymentGateway);
    }

    @Test
    void initiateRefund_whenTotalExceedsPaymentAmount_shouldThrowException() {
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Payment payment = new Payment(UUID.randomUUID(), UUID.randomUUID(), 100000L, "VND", "SIMULATOR", "DEV", "key-p");
        payment.setPaymentId(paymentId);
        payment.markSuccess("SIM-TX-1", Instant.now());

        when(refundRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(paymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));
        when(refundRepository.sumTotalRefundAmountByPaymentId(paymentId)).thenReturn(80000L);

        assertThatThrownBy(() -> refundService.initiateRefund(paymentId, orderId, RefundScope.ORDER, 30000L, RefundReason.CUSTOMER_CANCEL, "key-r"))
                .isInstanceOf(RefundLimitExceededException.class);
    }

    @Test
    void initiateRefund_whenValid_shouldCallGatewayAndMarkSuccess() {
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Payment payment = new Payment(UUID.randomUUID(), UUID.randomUUID(), 100000L, "VND", "SIMULATOR", "DEV", "key-p");
        payment.setPaymentId(paymentId);
        payment.markSuccess("SIM-TX-1", Instant.now());

        Refund refund = Refund.createOrderRefund(paymentId, orderId, 50000L, RefundReason.CUSTOMER_CANCEL, "key-r");
        refund.setRefundId(UUID.randomUUID());

        when(refundRepository.findByIdempotencyKey("key-r")).thenReturn(Optional.empty());
        when(paymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));
        when(refundRepository.sumTotalRefundAmountByPaymentId(paymentId)).thenReturn(0L);
        when(refundRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(refundRepository.saveAndFlush(any())).thenReturn(refund);
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(paymentGateway.refund(any(RefundCommand.class))).thenReturn(RefundResult.success("SIM-001"));
        when(refundRepository.findById(refund.getRefundId())).thenReturn(Optional.of(refund));

        Refund result = refundService.initiateRefund(paymentId, orderId, RefundScope.ORDER, 50000L, RefundReason.CUSTOMER_CANCEL, "key-r");

        assertThat(result.getStatus()).isEqualTo(RefundStatus.SUCCESS);
        assertThat(result.getProviderRefundId()).isEqualTo("SIM-001");
        verify(outboxEventRepository).save(any());
    }

    @Test
    void openNewRetryCycleByCustomer_whenSnapshotMismatch_shouldThrowException() {
        UUID refundId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        Payment payment = new Payment(UUID.randomUUID(), customerId, 100000L, "VND", "SIMULATOR", "DEV", "key-p");
        payment.setPaymentId(paymentId);
        payment.markSuccess("SIM-TX-1", Instant.now());

        Refund refund = Refund.createOrderRefund(paymentId, UUID.randomUUID(), 50000L, RefundReason.CUSTOMER_CANCEL, "key-r");
        refund.setRefundId(refundId);
        refund.markFailed("SIM-ERR", Instant.now());
        refund.markCustomerRetryRequired();

        when(refundRepository.findById(refundId)).thenReturn(Optional.of(refund));
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> refundService.openNewRetryCycleByCustomer(refundId, 5, customerId, "key-retry"))
                .isInstanceOf(RefundRetryNotSafeException.class);
    }

    @Test
    void openNewRetryCycleByCustomer_whenValid_shouldOpenNewCycle() {
        UUID refundId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        Payment payment = new Payment(UUID.randomUUID(), customerId, 100000L, "VND", "SIMULATOR", "DEV", "key-p");
        payment.setPaymentId(paymentId);
        payment.markSuccess("SIM-TX-1", Instant.now());

        Refund refund = Refund.createOrderRefund(paymentId, UUID.randomUUID(), 50000L, RefundReason.CUSTOMER_CANCEL, "key-r");
        refund.setRefundId(refundId);
        refund.markFailed("SIM-ERR", Instant.now());
        refund.markCustomerRetryRequired();

        when(refundRepository.findById(refundId)).thenReturn(Optional.of(refund));
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        Refund result = refundService.openNewRetryCycleByCustomer(refundId, 0, customerId, "key-retry");

        assertThat(result.getRetryCycle()).isEqualTo(1);
        assertThat(result.getRetryCount()).isZero();
        assertThat(result.isCustomerRetryRequired()).isFalse();
        assertThat(result.getStatus()).isEqualTo(RefundStatus.PENDING);
        verify(outboxEventRepository).save(any());
    }
}
