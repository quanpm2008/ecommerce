package com.example.payment_service.dto;

import java.util.List;

public record PaymentDetailResponse(
        PaymentResponse payment,
        List<RefundResponse> refunds,
        String manualNote
) {
}
