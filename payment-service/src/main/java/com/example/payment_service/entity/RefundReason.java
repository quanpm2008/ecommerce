package com.example.payment_service.entity;

public enum RefundReason {
    CUSTOMER_CANCEL,
    SELLER_REJECT,
    SELLER_CANCEL,
    SELLER_TIMEOUT,
    LATE_PAYMENT
}
