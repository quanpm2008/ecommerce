package com.example.payment_service.controller;

import com.example.payment_service.client.SimulatorScenario;
import com.example.payment_service.dto.CustomerRefundRetryRequest;
import com.example.payment_service.entity.*;
import com.example.payment_service.exception.GlobalExceptionHandler;
import com.example.payment_service.service.PaymentService;
import com.example.payment_service.service.RefundService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private PaymentService paymentService;

    @Mock
    private RefundService refundService;

    @InjectMocks
    private PaymentController paymentController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(paymentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createPayment_whenMissingIdempotencyKey_shouldReturn400() throws Exception {
        UUID sessionId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"checkoutSessionId\":\"" + sessionId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    void createPayment_whenSuccess_shouldReturn201() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        String idempotencyKey = "key-create-1";

        Payment payment = new Payment(sessionId, customerId, 750000L, "VND", "SIMULATOR", "DEV", idempotencyKey);
        payment.markSuccess("SIM-TX-12345", null);

        when(paymentService.processPayment(eq(sessionId), any(), eq(750000L), eq(idempotencyKey), any()))
                .thenReturn(payment);
        when(paymentService.toPaymentResponse(payment)).thenCallRealMethod();

        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", idempotencyKey)
                        .header("X-User-Id", customerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"checkoutSessionId\":\"" + sessionId + "\",\"simulatorScenario\":\"SUCCESS\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.providerTransactionId").value("SIM-TX-12345"));
    }

    @Test
    void getPaymentByCheckout_whenFound_shouldReturn200() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Payment payment = new Payment(sessionId, customerId, 750000L, "VND", "SIMULATOR", "DEV", "key-get-1");
        payment.setPaymentId(UUID.randomUUID());
        payment.markSuccess("SIM-TX-888", null);

        when(paymentService.getPaymentByCheckoutSession(eq(sessionId), eq(customerId)))
                .thenReturn(Optional.of(payment));
        when(paymentService.toPaymentResponse(payment)).thenCallRealMethod();

        mockMvc.perform(get("/api/v1/payments/by-checkout/{checkoutSessionId}", sessionId)
                        .header("X-User-Id", customerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentId").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));
    }

    @Test
    void getRefundsByCheckout_shouldReturn200() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        Refund refund = Refund.createOrderRefund(paymentId, orderId, 250000L, RefundReason.CUSTOMER_CANCEL, "ref-key-1");
        refund.setRefundId(UUID.randomUUID());
        when(refundService.getRefundsByCustomer(eq(sessionId), eq(customerId)))
                .thenReturn(List.of(refund));
        when(refundService.toRefundResponse(refund)).thenCallRealMethod();

        mockMvc.perform(get("/api/v1/payments/refunds")
                        .param("checkoutSessionId", sessionId.toString())
                        .header("X-User-Id", customerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].amountVnd").value(250000))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"));
    }

    @Test
    void retryRefund_shouldReturn202() throws Exception {
        UUID refundId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String idempotencyKey = "retry-key-1";

        Refund refund = Refund.createOrderRefund(paymentId, orderId, 250000L, RefundReason.CUSTOMER_CANCEL, "ref-key-1");
        refund.setRefundId(refundId);
        refund.markFailed("SIM-REF-FAIL", null);
        refund.markCustomerRetryRequired();

        when(refundService.openNewRetryCycleByCustomer(eq(refundId), eq(0), any(), eq(idempotencyKey)))
                .thenReturn(refund);
        when(refundService.toRefundResponse(refund)).thenCallRealMethod();

        CustomerRefundRetryRequest body = new CustomerRefundRetryRequest(0);

        mockMvc.perform(post("/api/v1/payments/refunds/{refundId}/retry", refundId)
                        .header("Idempotency-Key", idempotencyKey)
                        .header("X-User-Id", customerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.refundId").isNotEmpty());
    }
}
