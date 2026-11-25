package com.example.payment_service.service;

import com.example.payment_service.client.ChargeCommand;
import com.example.payment_service.client.ChargeResult;
import com.example.payment_service.client.PaymentGateway;
import com.example.payment_service.client.SimulatorScenario;
import com.example.payment_service.entity.Payment;
import com.example.payment_service.entity.PaymentStatus;
import com.example.payment_service.exception.IdempotencyConflictException;
import com.example.payment_service.repository.OutboxEventRepository;
import com.example.payment_service.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private PaymentGateway paymentGateway;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository, outboxEventRepository, paymentGateway);
    }

    @Test
    void processPayment_whenIdempotencyKeyExistsWithSamePayload_shouldReturnExisting() {
        UUID sessionId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        String key = "key-123";
        Payment existing = new Payment(sessionId, customerId, 100000L, "VND", "SIMULATOR", "DEV", key);
        existing.setPaymentId(UUID.randomUUID());

        when(paymentRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(existing));

        Payment result = paymentService.processPayment(sessionId, customerId, 100000L, key, SimulatorScenario.SUCCESS);

        assertThat(result).isSameAs(existing);
        verify(paymentGateway, never()).charge(any());
    }

    @Test
    void processPayment_whenIdempotencyKeyExistsWithDifferentPayload_shouldThrowException() {
        UUID sessionId1 = UUID.randomUUID();
        UUID sessionId2 = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        String key = "key-123";
        Payment existing = new Payment(sessionId1, customerId, 100000L, "VND", "SIMULATOR", "DEV", key);
        existing.setPaymentId(UUID.randomUUID());

        when(paymentRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> paymentService.processPayment(sessionId2, customerId, 100000L, key, SimulatorScenario.SUCCESS))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void processPayment_whenNew_shouldCallGatewayAndMarkSuccess() {
        UUID sessionId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        String key = "key-new";
        Payment payment = new Payment(sessionId, customerId, 500000L, "VND", "SIMULATOR", "DEV", key);
        payment.setPaymentId(UUID.randomUUID());

        when(paymentRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(paymentRepository.findByCheckoutSessionId(sessionId)).thenReturn(Optional.empty());
        when(paymentRepository.saveAndFlush(any())).thenReturn(payment);
        when(paymentGateway.charge(any(ChargeCommand.class))).thenReturn(ChargeResult.success("SIM-TX-001"));
        when(paymentRepository.findByIdForUpdate(payment.getPaymentId())).thenReturn(Optional.of(payment));

        Payment result = paymentService.processPayment(sessionId, customerId, 500000L, key, SimulatorScenario.SUCCESS);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(result.getProviderTransactionId()).isEqualTo("SIM-TX-001");
        verify(outboxEventRepository).save(any());
    }
}
