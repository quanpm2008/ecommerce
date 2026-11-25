package com.example.payment_service.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bộ giả lập thanh toán nội bộ SimulatedGateway theo đặc tả MVP.
 *
 * Tài liệu căn cứ:
 * - Tài liệu PDF: "Thiết kế bộ giả lập thanh toán nội bộ (SimulatedGateway)"
 * - docs/requirements/api_specs.md §6.5.1
 * - docs/requirements/database.md §3.4 / §6 / §8.4 (G-15)
 */
@Component
public class SimulatedGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(SimulatedGateway.class);

    // Tiền tố chuẩn SIM- theo tài liệu PDF Trang 2 & Trang 4
    public static final String SIMULATOR_PREFIX = "SIM-";
    // Tiền tố cho giao dịch thanh toán theo ví dụ tại api_specs.md §6.5
    public static final String CHARGE_PREFIX = "SIM-TX-";

    private final String defaultScenarioConfig;

    // Lưu vết theo ID giao dịch phục vụ query()
    private final Map<String, SimulatedTransactionRecord> recordsById = new ConcurrentHashMap<>();

    // Lưu vết theo IdempotencyKey để đảm bảo: cùng key luôn trả cùng kết quả và cùng mã giao dịch (PDF Trang 2)
    private final Map<String, SimulatedTransactionRecord> recordsByIdempotencyKey = new ConcurrentHashMap<>();

    public SimulatedGateway(@Value("${payment.simulator.scenario:SUCCESS}") String defaultScenarioConfig) {
        this.defaultScenarioConfig = defaultScenarioConfig;
    }

    /**
     * Thu tiền một lần cho CheckoutSession (charge).
     */
    @Override
    public ChargeResult charge(ChargeCommand cmd) {
        Objects.requireNonNull(cmd, "ChargeCommand cannot be null");

        // 1. Kiểm tra tính Idempotent: nếu key đã được xử lý trước đó, trả lại kết quả cũ
        SimulatedTransactionRecord existing = recordsByIdempotencyKey.get(cmd.idempotencyKey());
        if (existing != null) {
            log.info("[SimulatedGateway] Idempotent hit for charge key={}, returning existing txId={}",
                    cmd.idempotencyKey(), existing.id());
            return toChargeResult(existing);
        }

        // 2. Xác định kịch bản: ưu tiên kịch bản chỉ định từ client (dev/staging), fallback về cấu hình
        SimulatorScenario scenario = resolveChargeScenario(cmd.scenario());

        // 3. Sinh mã giao dịch với tiền tố SIM-TX-
        String txId = generateId(CHARGE_PREFIX);

        log.info("[SimulatedGateway] Executing charge: key={}, checkoutSessionId={}, amountVnd={}, scenario={}, txId={}",
                cmd.idempotencyKey(), cmd.checkoutSessionId(), cmd.amountVnd(), scenario, txId);

        // 4. Xử lý kết quả theo từng kịch bản
        SimulatedTransactionRecord record = switch (scenario) {
            case FAIL -> new SimulatedTransactionRecord(
                    txId, GatewayStatus.FAILED, "SIM_PAYMENT_REJECTED",
                    cmd.amountVnd(), cmd.idempotencyKey(), false, Instant.now()
            );
            case TIMEOUT -> new SimulatedTransactionRecord(
                    txId, GatewayStatus.PENDING, null,
                    cmd.amountVnd(), cmd.idempotencyKey(), false, Instant.now()
            );
            case LATE_SUCCESS -> new SimulatedTransactionRecord(
                    txId, GatewayStatus.PENDING, null,
                    cmd.amountVnd(), cmd.idempotencyKey(), true, Instant.now()
            );
            default -> new SimulatedTransactionRecord( // Kịch bản SUCCESS (Giai đoạn 1)
                    txId, GatewayStatus.SUCCESS, null,
                    cmd.amountVnd(), cmd.idempotencyKey(), false, Instant.now()
            );
        };

        saveRecord(record);
        return toChargeResult(record);
    }

    /**
     * Hoàn tiền theo Order hoặc toàn bộ checkout (refund).
     */
    @Override
    public RefundResult refund(RefundCommand cmd) {
        Objects.requireNonNull(cmd, "RefundCommand cannot be null");

        // 1. Kiểm tra tính Idempotent: nếu key đã được xử lý trước đó, trả lại kết quả cũ
        SimulatedTransactionRecord existing = recordsByIdempotencyKey.get(cmd.idempotencyKey());
        if (existing != null) {
            log.info("[SimulatedGateway] Idempotent hit for refund key={}, returning existing refundId={}",
                    cmd.idempotencyKey(), existing.id());
            return toRefundResult(existing);
        }

        // 2. Xác định kịch bản: Giai đoạn 1 luôn SUCCESS; Giai đoạn 2 đọc cấu hình REFUND_FAIL
        SimulatorScenario scenario = resolveRefundScenario();

        // 3. Sinh mã hoàn tiền với tiền tố SIM- theo tài liệu PDF
        String refundId = generateId(SIMULATOR_PREFIX);

        log.info("[SimulatedGateway] Executing refund: key={}, originalTxId={}, amountVnd={}, scenario={}, refundId={}",
                cmd.idempotencyKey(), cmd.originalProviderTransactionId(), cmd.amountVnd(), scenario, refundId);

        SimulatedTransactionRecord record = (scenario == SimulatorScenario.REFUND_FAIL)
                ? new SimulatedTransactionRecord(refundId, GatewayStatus.FAILED, "SIM_REFUND_REJECTED",
                cmd.amountVnd(), cmd.idempotencyKey(), false, Instant.now())
                : new SimulatedTransactionRecord(refundId, GatewayStatus.SUCCESS, null,
                cmd.amountVnd(), cmd.idempotencyKey(), false, Instant.now());

        saveRecord(record);
        return toRefundResult(record);
    }

    /**
     * Tra cứu trạng thái giao dịch thu hoặc hoàn tiền (query).
     */
    @Override
    public QueryResult query(String providerTransactionOrRefundId) {
        Objects.requireNonNull(providerTransactionOrRefundId, "providerTransactionOrRefundId cannot be null");

        log.info("[SimulatedGateway] Querying status for id={}", providerTransactionOrRefundId);

        SimulatedTransactionRecord record = recordsById.get(providerTransactionOrRefundId);
        if (record != null) {
            // Xử lý kịch bản LATE_SUCCESS: khi tra cứu, trạng thái chuyển từ PENDING sang SUCCESS
            if (record.isLateSuccess() && record.status() == GatewayStatus.PENDING) {
                record = record.withStatus(GatewayStatus.SUCCESS);
                saveRecord(record);
                log.info("[SimulatedGateway] LATE_SUCCESS transitioned to SUCCESS for id={}", providerTransactionOrRefundId);
            }
            return QueryResult.of(record.status(), providerTransactionOrRefundId, record.errorCode());
        }

        // Fallback: nếu chưa lưu trong bộ nhớ nhưng có tiền tố SIM- hợp lệ
        if (providerTransactionOrRefundId.startsWith(SIMULATOR_PREFIX)) {
            return QueryResult.of(GatewayStatus.SUCCESS, providerTransactionOrRefundId, null);
        }

        return QueryResult.of(GatewayStatus.FAILED, providerTransactionOrRefundId, "TRANSACTION_NOT_FOUND");
    }

    private void saveRecord(SimulatedTransactionRecord record) {
        recordsById.put(record.id(), record);
        recordsByIdempotencyKey.put(record.idempotencyKey(), record);
    }

    private ChargeResult toChargeResult(SimulatedTransactionRecord record) {
        return switch (record.status()) {
            case SUCCESS -> ChargeResult.success(record.id());
            case FAILED -> ChargeResult.failed(record.errorCode(), record.id());
            case PENDING -> ChargeResult.pending(record.id());
        };
    }

    private RefundResult toRefundResult(SimulatedTransactionRecord record) {
        return switch (record.status()) {
            case SUCCESS -> RefundResult.success(record.id());
            case FAILED -> RefundResult.failed(record.errorCode(), record.id());
            case PENDING -> RefundResult.pending(record.id());
        };
    }

    private SimulatorScenario resolveChargeScenario(SimulatorScenario cmdScenario) {
        if (cmdScenario != null) {
            return cmdScenario;
        }
        return parseConfigScenario();
    }

    private SimulatorScenario resolveRefundScenario() {
        return parseConfigScenario();
    }

    private SimulatorScenario parseConfigScenario() {
        if (defaultScenarioConfig == null || defaultScenarioConfig.isBlank()) {
            return SimulatorScenario.SUCCESS;
        }
        try {
            return SimulatorScenario.valueOf(defaultScenarioConfig.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.warn("[SimulatedGateway] Unknown scenario '{}', fallback to SUCCESS", defaultScenarioConfig);
            return SimulatorScenario.SUCCESS;
        }
    }

    private String generateId(String prefix) {
        String raw = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        return prefix + raw;
    }

    public record SimulatedTransactionRecord(
            String id,
            GatewayStatus status,
            String errorCode,
            Long amountVnd,
            String idempotencyKey,
            boolean isLateSuccess,
            Instant createdAt
    ) {
        public SimulatedTransactionRecord withStatus(GatewayStatus newStatus) {
            return new SimulatedTransactionRecord(id, newStatus, null, amountVnd, idempotencyKey, isLateSuccess, createdAt);
        }
    }
}
