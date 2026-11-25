package com.example.payment_service.client;

import java.util.UUID;

import com.example.payment_service.client.ProviderContracts.CreatePaymentRequest;
import com.example.payment_service.client.ProviderContracts.CreatePaymentResponse;
import com.example.payment_service.client.ProviderContracts.CreateRefundRequest;
import com.example.payment_service.client.ProviderContracts.PaymentQueryResponse;
import com.example.payment_service.client.ProviderContracts.RefundResponse;

public interface PaymentGateway {
    CreatePaymentResponse createPayment(CreatePaymentRequest request);

    PaymentQueryResponse queryPayment(UUID paymentId);

    RefundResponse createRefund(CreateRefundRequest request);

    RefundResponse queryRefund(UUID refundId, UUID attemptId);
}

