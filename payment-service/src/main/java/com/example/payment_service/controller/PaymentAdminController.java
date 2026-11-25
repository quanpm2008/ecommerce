package com.example.payment_service.controller;

import com.example.payment_service.dto.*;
import com.example.payment_service.entity.Payment;
import com.example.payment_service.entity.PaymentStatus;
import com.example.payment_service.entity.Refund;
import com.example.payment_service.entity.RefundStatus;
import com.example.payment_service.exception.PaymentNotFoundException;
import com.example.payment_service.repository.PaymentRepository;
import com.example.payment_service.repository.RefundRepository;
import com.example.payment_service.service.PaymentService;
import com.example.payment_service.service.RefundService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/v1/payments/admin")
public class PaymentAdminController {

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final PaymentService paymentService;
    private final RefundService refundService;
    private final Map<UUID, String> manualNotes = new ConcurrentHashMap<>();

    public PaymentAdminController(PaymentRepository paymentRepository,
                                  RefundRepository refundRepository,
                                  PaymentService paymentService,
                                  RefundService refundService) {
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.paymentService = paymentService;
        this.refundService = refundService;
    }

    @GetMapping("/issues")
    public ResponseEntity<ApiResponse<List<PaymentIssueItem>>> getIssues(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        List<PaymentIssueItem> items = new ArrayList<>();

        if (type == null || "PAYMENT_PENDING".equalsIgnoreCase(type)) {
            List<Payment> pendingPayments = paymentRepository.findByStatus(PaymentStatus.PENDING);
            for (Payment p : pendingPayments) {
                items.add(new PaymentIssueItem(
                        "PAYMENT_PENDING",
                        p.getPaymentId(),
                        null,
                        p.getCheckoutSessionId(),
                        null,
                        p.getAmountVnd(),
                        p.getCreatedAt(),
                        null
                ));
            }
        }

        if (type == null || "REFUND_FAILED".equalsIgnoreCase(type) || "REFUND_STUCK".equalsIgnoreCase(type)) {
            List<Refund> failedRefunds = refundRepository.findByStatus(RefundStatus.FAILED);
            for (Refund r : failedRefunds) {
                Payment p = paymentRepository.findById(r.getPaymentId()).orElse(null);
                UUID checkoutId = p != null ? p.getCheckoutSessionId() : null;
                String issueType = r.isCustomerRetryRequired() ? "REFUND_FAILED" : "REFUND_STUCK";
                items.add(new PaymentIssueItem(
                        issueType,
                        r.getPaymentId(),
                        r.getRefundId(),
                        checkoutId,
                        r.getOrderId(),
                        r.getAmountVnd(),
                        r.getCreatedAt(),
                        r.getRetryCount()
                ));
            }
        }

        int totalItems = items.size();
        int totalPages = (int) Math.ceil((double) totalItems / size);
        int fromIndex = Math.min((page - 1) * size, totalItems);
        int toIndex = Math.min(fromIndex + size, totalItems);
        List<PaymentIssueItem> pagedItems = items.subList(fromIndex, toIndex);

        return ResponseEntity.ok(ApiResponse.of(pagedItems, PageMeta.of(page, size, totalItems, totalPages)));
    }

    @GetMapping("/payments/by-checkout/{checkoutSessionId}")
    public ResponseEntity<ApiResponse<PaymentDetailResponse>> getPaymentDetail(
            @PathVariable UUID checkoutSessionId) {

        Payment payment = paymentRepository.findByCheckoutSessionId(checkoutSessionId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found for checkout session: " + checkoutSessionId));

        List<Refund> refunds = refundRepository.findByPaymentId(payment.getPaymentId());
        List<RefundResponse> refundResponses = refunds.stream()
                .map(refundService::toRefundResponse)
                .toList();

        String note = manualNotes.get(checkoutSessionId);
        PaymentDetailResponse detail = new PaymentDetailResponse(
                paymentService.toPaymentResponse(payment),
                refundResponses,
                note
        );
        return ResponseEntity.ok(ApiResponse.of(detail));
    }

    @GetMapping("/refunds")
    public ResponseEntity<ApiResponse<List<RefundResponse>>> getAdminRefunds(
            @RequestParam(required = false) RefundStatus status,
            @RequestParam(required = false) UUID orderId,
            @RequestParam(required = false) UUID paymentId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(Math.max(0, page - 1), size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Refund> refundPage;

        if (paymentId != null) {
            refundPage = refundRepository.findByPaymentId(paymentId, pageable);
        } else if (orderId != null) {
            refundPage = refundRepository.findByOrderId(orderId, pageable);
        } else if (status != null) {
            refundPage = refundRepository.findByStatus(status, pageable);
        } else {
            refundPage = refundRepository.findAll(pageable);
        }

        List<RefundResponse> content = refundPage.getContent().stream()
                .map(refundService::toRefundResponse)
                .toList();

        PageMeta meta = PageMeta.of(page, size, refundPage.getTotalElements(), refundPage.getTotalPages());
        return ResponseEntity.ok(ApiResponse.of(content, meta));
    }

    @PostMapping("/checkout-sessions/{checkoutSessionId}/mark-handled")
    public ResponseEntity<ApiResponse<MarkHandledResponse>> markHandled(
            @PathVariable UUID checkoutSessionId,
            @Valid @RequestBody MarkHandledRequest request) {

        manualNotes.put(checkoutSessionId, request.note());
        MarkHandledResponse response = new MarkHandledResponse(checkoutSessionId, request.note(), Instant.now());
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<PaymentSummaryResponse>> getSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        Long collectedVnd = paymentRepository.sumCollectedAmount();
        Long refundedVnd = refundRepository.sumRefundedAmount();
        Long pendingRefundVnd = refundRepository.sumPendingRefundAmount();
        long pendingPaymentCount = paymentRepository.countByStatus(PaymentStatus.PENDING);
        long failedRefundCount = refundRepository.countByStatus(RefundStatus.FAILED);

        PaymentSummaryResponse summary = new PaymentSummaryResponse(
                collectedVnd != null ? collectedVnd : 0L,
                refundedVnd != null ? refundedVnd : 0L,
                pendingRefundVnd != null ? pendingRefundVnd : 0L,
                pendingPaymentCount,
                failedRefundCount,
                true
        );
        return ResponseEntity.ok(ApiResponse.of(summary));
    }
}
