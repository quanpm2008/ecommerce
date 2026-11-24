---
phase: 04-tra-c-u-v-v-i-so-t
plan: "05"
wave: 3
requirements-completed: [PAY-07]
completed: 2026-11-24
---

# Kết quả 04-05

Payment công khai `mark-handled` và gọi Order bằng OpenFeign, chuyển ngữ cảnh Admin. Order kiểm cả hai cấp quyền, cập nhật cờ/note và audit cùng transaction. Hàng chờ gộp cờ khoản thu từ Order và ưu tiên review. V10 tạo bảng audit của Order.

Bằng chứng: `CollectionReviewPostgresTests` (1) và `PaymentAdminReviewHttpTests` (1) đạt, gồm mock sai quyền và rollback audit. [Log Order](../../../order-service/target/phase4-verify-r2.log), [Log Payment](../../../payment-service/target/phase4-verify-r2.log). Identity thật còn thiếu grant annotate.
