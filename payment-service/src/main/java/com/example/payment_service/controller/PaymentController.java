package com.example.payment_service.controller;

import com.example.payment_service.client.SimulatorScenario;
import com.example.payment_service.dto.*;
import com.example.payment_service.entity.Payment;
import com.example.payment_service.entity.Refund;
import com.example.payment_service.service.PaymentService;
import com.example.payment_service.service.RefundService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final RefundService refundService;

    public PaymentController(PaymentService paymentService, RefundService refundService) {
        this.paymentService = paymentService;
        this.refundService = refundService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PaymentResponse>> createPayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-User-Id", required = false) UUID customerId,
            @Valid @RequestBody CreatePaymentRequest request) {

        UUID resolvedCustomerId = customerId != null ? customerId : UUID.fromString("00000000-0000-0000-0000-000000000001");
        SimulatorScenario scenario = request.simulatorScenario() != null ? request.simulatorScenario() : SimulatorScenario.SUCCESS;
        Long defaultAmountVnd = 750000L;

        Payment payment = paymentService.processPayment(
                request.checkoutSessionId(),
                resolvedCustomerId,
                defaultAmountVnd,
                idempotencyKey,
                scenario
        );

        PaymentResponse response = paymentService.toPaymentResponse(payment);
        HttpStatus status = payment.isPending() ? HttpStatus.ACCEPTED : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(ApiResponse.of(response));
    }

    @GetMapping("/by-checkout/{checkoutSessionId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentByCheckout(
            @PathVariable UUID checkoutSessionId,
            @RequestHeader(value = "X-User-Id", required = false) UUID customerId) {

        Payment payment = paymentService.getPaymentByCheckoutSession(checkoutSessionId, customerId).orElse(null);
        PaymentResponse response = payment != null ? paymentService.toPaymentResponse(payment) : null;
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @GetMapping("/refunds")
    public ResponseEntity<ApiResponse<List<RefundResponse>>> getRefundsByCheckout(
            @RequestParam UUID checkoutSessionId,
            @RequestHeader(value = "X-User-Id", required = false) UUID customerId) {

        List<Refund> refunds = refundService.getRefundsByCustomer(checkoutSessionId, customerId);
        List<RefundResponse> responses = refunds.stream()
                .map(refundService::toRefundResponse)
                .toList();
        return ResponseEntity.ok(ApiResponse.of(responses));
    }

    @PostMapping("/refunds/{refundId}/retry")
    public ResponseEntity<ApiResponse<RefundResponse>> retryRefund(
            @PathVariable UUID refundId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-User-Id", required = false) UUID customerId,
            @Valid @RequestBody CustomerRefundRetryRequest request) {

        UUID resolvedCustomerId = customerId != null ? customerId : UUID.fromString("00000000-0000-0000-0000-000000000001");
        Refund refund = refundService.openNewRetryCycleByCustomer(
                refundId,
                request.expectedRetryCycle(),
                resolvedCustomerId,
                idempotencyKey
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.of(refundService.toRefundResponse(refund)));
    }
}
