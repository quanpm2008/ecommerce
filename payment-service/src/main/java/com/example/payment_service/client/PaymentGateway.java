package com.example.payment_service.client;

public interface PaymentGateway {

    ChargeResult charge(ChargeCommand chargeCommand);

    RefundResult refund(RefundCommand refundCommand);

    QueryResult query(String providerTransactionOrRefundId);
}

