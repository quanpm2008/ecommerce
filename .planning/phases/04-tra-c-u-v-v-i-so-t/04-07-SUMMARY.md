---
phase: 04-tra-c-u-v-v-i-so-t
plan: "07"
wave: 4
requirements-completed: [ORD-09, PAY-07, FIN-04, FIN-05]
completed: 2026-11-24
---

# Kết quả 04-07

Chạy preflight, Phase 2, Phase 3 và Phase 4 trên ba JAR build từ workspace, PostgreSQL 16, Kafka 4.4.0 và ElasticMQ 1.7.1. Cả **4/4** bài đạt, 0 fail, 0 error, 0 skip. Lượt cuối kết thúc lúc 15:11:57 ngày 24/11/2026. [Log](../../../order-service/target/phase4-e2e-final.log)

Phase 4 kiểm một checkout hai Seller, tra cứu đúng chủ, audit khoản thu, rút đồng thời cùng key và `withdrawal.completed` trên Kafka. Finance lấy đủ ba nguồn thật của ba JAR. Khi dừng Payment, đối soát trả `INCOMPLETE` và giữ chênh lệch chưa biết.

Identity, Product và provider trong bài tích hợp hồi quy trên dùng HTTP mock theo hợp đồng. Lượt bổ sung chạy bốn ứng dụng thật và xác minh riêng Admin annotate, Admin chỉ đọc và Seller BANNED rút hết 9.500đ. Notification được kiểm tại event Kafka. [Bằng chứng Identity](04-IDENTITY-EVIDENCE.md)

## Task Commits

1. **Task 1: Snapshot mã và migration của bốn backend dùng để xác minh Phase 4** - `e8904d1` (nhánh local `verify/phase4-20261124`).
