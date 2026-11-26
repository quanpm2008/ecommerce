package com.example.simulator;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.example.simulator.SimulatorModels.FaultMode;
import com.example.simulator.SimulatorModels.PaymentRecord;
import com.example.simulator.SimulatorModels.RefundAttemptAbsence;
import com.example.simulator.SimulatorModels.RefundAttemptRecord;
import com.example.simulator.SimulatorModels.SnapshotState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public final class JsonStateStore {
    private final Path stateFile;
    private final ObjectMapper mapper;
    private final Map<UUID, PaymentRecord> paymentsById = new LinkedHashMap<>();
    private final Map<String, UUID> paymentIdsByToken = new LinkedHashMap<>();
    private final Map<AttemptKey, RefundAttemptRecord> refundsByAttempt = new LinkedHashMap<>();
    private final Map<AttemptKey, RefundAttemptAbsence> closedMissingAttempts = new LinkedHashMap<>();
    private FaultMode nextPaymentFault = FaultMode.NONE;
    private FaultMode nextRefundFault = FaultMode.NONE;
    private long lateSuccessDelayMs = 5000L;

    public JsonStateStore(Path stateFile) {
        this.stateFile = stateFile;
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        load();
    }

    public synchronized PaymentRecord findPayment(UUID paymentId) {
        return paymentsById.get(paymentId);
    }

    public synchronized PaymentRecord findPaymentByToken(String token) {
        UUID paymentId = paymentIdsByToken.get(token);
        return paymentId == null ? null : paymentsById.get(paymentId);
    }

    public synchronized void savePayment(PaymentRecord record) {
        paymentsById.put(record.paymentId, record);
        paymentIdsByToken.put(record.token, record.paymentId);
        persist();
    }

    public synchronized RefundAttemptRecord findRefundAttempt(UUID refundId, UUID attemptId) {
        return refundsByAttempt.get(new AttemptKey(refundId, attemptId));
    }

    public synchronized List<RefundAttemptRecord> findRefundsByRefundId(UUID refundId) {
        List<RefundAttemptRecord> list = new ArrayList<>();
        for (RefundAttemptRecord attempt : refundsByAttempt.values()) {
            if (refundId.equals(attempt.refundId)) {
                list.add(attempt);
            }
        }
        return list;
    }

    public synchronized List<RefundAttemptRecord> findRefundsByPaymentId(UUID paymentId) {
        List<RefundAttemptRecord> list = new ArrayList<>();
        for (RefundAttemptRecord attempt : refundsByAttempt.values()) {
            if (paymentId.equals(attempt.paymentId)) {
                list.add(attempt);
            }
        }
        return list;
    }

    public synchronized void saveRefundAttempt(RefundAttemptRecord record) {
        refundsByAttempt.put(new AttemptKey(record.refundId, record.attemptId), record);
        persist();
    }

    public synchronized boolean isAttemptClosedMissing(UUID refundId, UUID attemptId) {
        return closedMissingAttempts.containsKey(new AttemptKey(refundId, attemptId));
    }

    public synchronized RefundAttemptAbsence recordOrGetClosedMissingAttempt(
            UUID refundId, UUID attemptId, java.time.Instant completedAt, String errorCode) {
        AttemptKey key = new AttemptKey(refundId, attemptId);
        RefundAttemptAbsence existing = closedMissingAttempts.get(key);
        if (existing != null) {
            return existing;
        }
        RefundAttemptAbsence created = new RefundAttemptAbsence(
                refundId,
                attemptId,
                "refund-" + attemptId,
                completedAt,
                errorCode
        );
        closedMissingAttempts.put(key, created);
        persist();
        return created;
    }

    public synchronized FaultMode consumeNextPaymentFault() {
        FaultMode fault = this.nextPaymentFault;
        this.nextPaymentFault = FaultMode.NONE;
        persist();
        return fault;
    }

    public synchronized FaultMode consumeNextRefundFault() {
        FaultMode fault = this.nextRefundFault;
        this.nextRefundFault = FaultMode.NONE;
        persist();
        return fault;
    }

    public synchronized void configureScenario(FaultMode paymentFault, FaultMode refundFault, Long delayMs) {
        if (paymentFault != null) {
            this.nextPaymentFault = paymentFault;
        }
        if (refundFault != null) {
            this.nextRefundFault = refundFault;
        }
        if (delayMs != null && delayMs > 0) {
            this.lateSuccessDelayMs = delayMs;
        }
        persist();
    }

    public synchronized long lateSuccessDelayMs() {
        return lateSuccessDelayMs;
    }

    public synchronized SnapshotState snapshot() {
        return new SnapshotState(
                new ArrayList<>(paymentsById.values()),
                new ArrayList<>(refundsByAttempt.values()),
                new ArrayList<>(closedMissingAttempts.values()),
                nextPaymentFault,
                nextRefundFault,
                lateSuccessDelayMs
        );
    }

    private void load() {
        if (stateFile == null || !Files.exists(stateFile)) {
            return;
        }
        try {
            byte[] bytes = Files.readAllBytes(stateFile);
            if (bytes.length == 0) {
                return;
            }
            SnapshotState loaded = mapper.readValue(bytes, SnapshotState.class);
            if (loaded.payments() != null) {
                for (PaymentRecord payment : loaded.payments()) {
                    paymentsById.put(payment.paymentId, payment);
                    if (payment.token != null) {
                        paymentIdsByToken.put(payment.token, payment.paymentId);
                    }
                }
            }
            if (loaded.refunds() != null) {
                for (RefundAttemptRecord attempt : loaded.refunds()) {
                    refundsByAttempt.put(new AttemptKey(attempt.refundId, attempt.attemptId), attempt);
                }
            }
            if (loaded.closedMissingAttempts() != null) {
                for (RefundAttemptAbsence absence : loaded.closedMissingAttempts()) {
                    closedMissingAttempts.put(new AttemptKey(absence.refundId(), absence.attemptId()), absence);
                }
            }
            if (loaded.nextPaymentFault() != null) {
                this.nextPaymentFault = loaded.nextPaymentFault();
            }
            if (loaded.nextRefundFault() != null) {
                this.nextRefundFault = loaded.nextRefundFault();
            }
            if (loaded.lateSuccessDelayMs() > 0) {
                this.lateSuccessDelayMs = loaded.lateSuccessDelayMs();
            }
        } catch (IOException ignored) {
        }
    }

    private void persist() {
        if (stateFile == null) {
            return;
        }
        try {
            Path parent = stateFile.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temp = Files.createTempFile(parent, "sim-state-", ".tmp");
            byte[] bytes = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(snapshot());
            Files.write(temp, bytes);
            try {
                Files.move(temp, stateFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException fallback) {
                Files.move(temp, stateFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
        }
    }

    private record AttemptKey(UUID refundId, UUID attemptId) {
    }
}
