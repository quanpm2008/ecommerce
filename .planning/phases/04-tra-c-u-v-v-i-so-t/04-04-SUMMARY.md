---
phase: 04-tra-c-u-v-v-i-so-t
plan: "04"
wave: 2
requirements-completed: [FIN-04]
completed: 2026-11-24
---

# Kết quả 04-04

Tái dùng khóa ví, ledger và Withdrawal. API ví/Settlement/giao dịch/biên nhận đọc theo quyền. Finance kiểm ledger trước lần rút mới, ghi Wallet, Withdrawal, ledger và outbox trong một transaction. Relay dùng Kafka với ID ổn định. Flyway V4 thêm cờ khóa ví độc lập và outbox rút tiền.

Bằng chứng: `WithdrawalPostgresTests` (3) và `FinanceReadPostgresTests` (1) đạt trên PostgreSQL thật + Identity HTTP mock. [Log Finance](../../../finance-service/target/phase4-verify.log).
