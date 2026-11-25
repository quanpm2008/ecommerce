package com.example.payment_service.dto;

public record PaymentSummaryResponse(
        Long collectedVnd,
        Long refundedVnd,
        Long pendingRefundVnd,
        long pendingPaymentCount,
        long failedRefundCount,
        boolean isSimulated
) {
}
