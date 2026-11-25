package com.example.payment_service.controller;

import com.example.payment_service.dto.MarkHandledRequest;
import com.example.payment_service.entity.*;
import com.example.payment_service.exception.GlobalExceptionHandler;
import com.example.payment_service.repository.PaymentRepository;
import com.example.payment_service.repository.RefundRepository;
import com.example.payment_service.service.PaymentService;
import com.example.payment_service.service.RefundService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PaymentAdminControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private PaymentService paymentService;

    @Mock
    private RefundService refundService;

    @InjectMocks
    private PaymentAdminController paymentAdminController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(paymentAdminController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getIssues_shouldReturn200() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Payment p = new Payment(sessionId, customerId, 500000L, "VND", "SIMULATOR", "DEV", "k1");
        p.setPaymentId(UUID.randomUUID());

        when(paymentRepository.findByStatus(PaymentStatus.PENDING)).thenReturn(List.of(p));
        when(refundRepository.findByStatus(RefundStatus.FAILED)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/payments/admin/issues"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.meta.totalItems").value(1));
    }

    @Test
    void getPaymentDetail_shouldReturn200() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        Payment p = new Payment(sessionId, customerId, 500000L, "VND", "SIMULATOR", "DEV", "k1");
        p.setPaymentId(paymentId);
        p.markSuccess("SIM-TX-DETAIL", null);

        when(paymentRepository.findByCheckoutSessionId(sessionId)).thenReturn(Optional.of(p));
        when(refundRepository.findByPaymentId(paymentId)).thenReturn(List.of());
        when(paymentService.toPaymentResponse(p)).thenCallRealMethod();

        mockMvc.perform(get("/api/v1/payments/admin/payments/by-checkout/{checkoutSessionId}", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.payment.paymentId").value(paymentId.toString()))
                .andExpect(jsonPath("$.data.refunds").isArray());
    }

    @Test
    void getAdminRefunds_shouldReturn200() throws Exception {
        UUID paymentId = UUID.randomUUID();
        Refund r = Refund.createFullCheckoutRefund(paymentId, 300000L, "ref-k1");
        r.setRefundId(UUID.randomUUID());

        when(refundRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(r)));
        when(refundService.toRefundResponse(r)).thenCallRealMethod();

        mockMvc.perform(get("/api/v1/payments/admin/refunds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].amountVnd").value(300000))
                .andExpect(jsonPath("$.meta.page").value(1));
    }

    @Test
    void markHandled_shouldReturn200() throws Exception {
        UUID sessionId = UUID.randomUUID();
        MarkHandledRequest body = new MarkHandledRequest("Verified manually by Admin");

        mockMvc.perform(post("/api/v1/payments/admin/checkout-sessions/{checkoutSessionId}/mark-handled", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checkoutSessionId").value(sessionId.toString()))
                .andExpect(jsonPath("$.data.manualNote").value("Verified manually by Admin"));
    }

    @Test
    void getSummary_shouldReturn200() throws Exception {
        when(paymentRepository.sumCollectedAmount()).thenReturn(1500000L);
        when(refundRepository.sumRefundedAmount()).thenReturn(200000L);
        when(refundRepository.sumPendingRefundAmount()).thenReturn(50000L);
        when(paymentRepository.countByStatus(PaymentStatus.PENDING)).thenReturn(2L);
        when(refundRepository.countByStatus(RefundStatus.FAILED)).thenReturn(1L);

        mockMvc.perform(get("/api/v1/payments/admin/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.collectedVnd").value(1500000))
                .andExpect(jsonPath("$.data.refundedVnd").value(200000))
                .andExpect(jsonPath("$.data.pendingRefundVnd").value(50000))
                .andExpect(jsonPath("$.data.pendingPaymentCount").value(2))
                .andExpect(jsonPath("$.data.failedRefundCount").value(1))
                .andExpect(jsonPath("$.data.isSimulated").value(true));
    }
}
