# Checklist Triển khai payment-service

Tài liệu theo dõi tiến độ chi tiết từng hạng mục công việc bên trong `payment-service`.

---

## Giai đoạn 1: Nền tảng dự án & Dependencies
- [x] Bổ sung dependencies vào `payment-service/pom.xml`:
  - [x] `spring-boot-starter-data-jpa`
  - [x] `spring-boot-starter-flyway` + `flyway-database-postgresql`
  - [x] `spring-boot-starter-validation`
  - [x] `lombok` (optional compile-time)
  - [x] `mapstruct` + `lombok-mapstruct-binding`
  - [x] Test dependencies: `spring-boot-starter-data-jpa-test`, `testcontainers-postgresql`, `testcontainers-junit-jupiter`
- [x] Cấu hình `payment-service/src/main/resources/application.properties`:
  - [x] Datasource kết nối PostgreSQL (`schema=payment`, `search-path`)
  - [x] Flyway migration enabled
  - [x] Hibernate `ddl-auto=validate`
  - [x] Cấu hình logging `[requestId]`
  - [x] Cấu hình simulator scenario
- [x] Build kiểm thử: `mvn clean test-compile` thành công.

---

## Giai đoạn 2: Database Schema & JPA Entities
- [x] Tạo file migration `src/main/resources/db/migration/V1__init_payment_schema.sql`:
  - [x] Tạo schema `payment_service` (nếu chưa có)
  - [x] Bảng `payments` (PK, UQ `checkout_session_id`, UQ `idempotency_key`, CHECK status/amount/currency)
  - [x] Bảng `refunds` (PK, FK `payment_id`, UQ `order_id` cho ORDER scope, UQ `idempotency_key`, CHECK scope/status)
  - [x] Bảng `outbox_events` (lưu event id, aggregate type/id, type, payload jsonb, created_at, processed_at)
- [ ] Xây dựng domain entities trong package `com.example.payment_service.domain`:
  - [ ] `Payment`, `PaymentStatus`
  - [ ] `Refund`, `RefundScope`, `RefundReason`, `RefundStatus`
  - [ ] `OutboxEvent`
- [ ] Xây dựng Spring Data Repositories:
  - [ ] `PaymentRepository` (hỗ trợ findByCheckoutSessionId, findByIdempotencyKey, findWithLockById)
  - [ ] `RefundRepository` (hỗ trợ findByPaymentId, findByOrderId, sumRefundedAmountByPaymentId)
  - [ ] `OutboxEventRepository`
- [ ] Integration test: kiểm tra migration chạy sạch và JPA mapping khớp schema.

---

## Giai đoạn 3: Core Domain, Simulator & Transaction Boundaries
- [ ] Thiết kế Gateway Interface:
  - [ ] Interface `PaymentGateway` (`charge`, `refund`, `query`)
  - [ ] DTOs: `ChargeCommand`, `ChargeResult`, `RefundCommand`, `RefundResult`, `QueryResult`
- [ ] Triển khai `SimulatedGateway`:
  - [ ] Triển khai logic mặc định SUCCESS (Giai đoạn 1)
  - [ ] Sẵn sàng cấu hình chuyển đổi kịch bản (FAIL/TIMEOUT/REFUND_FAIL)
- [ ] Triển khai `PaymentService`:
  - [ ] Kiểm tra idempotency (G-09)
  - [ ] Lưu ý định `PENDING` trong transaction 1
  - [ ] Gọi `PaymentGateway.charge()` ngoài transaction (G-11)
  - [ ] Cập nhật kết quả `SUCCESS`/`FAILED` và ghi `outbox_events` trong transaction 2
  - [ ] Query trạng thái payment theo checkout session
- [ ] Triển khai `RefundService`:
  - [ ] Khóa bi quan (`SELECT FOR UPDATE`) trên Payment
  - [ ] Kiểm tra điều kiện: Payment đã SUCCESS, tổng tiền hoàn `total_refunded + new_amount <= payment.amount_vnd`
  - [ ] Tạo bản ghi `Refund` `PENDING`
  - [ ] Gọi `PaymentGateway.refund()` ngoài transaction
  - [ ] Cập nhật kết quả `SUCCESS`/`FAILED`
  - [ ] Thử lại hoàn tiền (`retry`) và tra cứu (`query`) an toàn (tái sử dụng bản ghi và idempotency key)
- [ ] Unit tests:
  - [ ] Test tính toán trạng thái dẫn xuất `refundState`
  - [ ] Test chặn hoàn tiền vượt quá số tiền thanh toán
  - [ ] Test xử lý trùng lặp idempotency key

---

## Giai đoạn 4: REST Controllers & Contracts
- [ ] DTOs Request/Response theo `business/api.md` §6.5:
  - [ ] `CreatePaymentRequest`, `PaymentResponse`
  - [ ] `RefundResponse`, `PaymentIssuesResponse`, `PaymentDetailWithRefundsResponse`
- [ ] Chuẩn hóa Envelope phản hồi & Exception Handling:
  - [ ] Wrapper `ApiResponse<T>` với format `{"data": ...}`
  - [ ] Error envelope format `{"error": {"code": "...", "message": "...", "details": [...]}}`
  - [ ] Global Exception Handler bắt validation, 404, 409 Conflict, 400 Bad Request
  - [ ] Filter gắn `X-Request-Id`
- [ ] Xây dựng Controllers:
  - [ ] `PaymentController` (Customer endpoints: `POST /api/v1/payments`, `GET /api/v1/payments/by-checkout/{checkoutSessionId}`)
  - [ ] `PaymentAdminController` (Admin endpoints: `/admin/issues`, `/admin/payments/by-checkout/{id}`, `/admin/refunds`, `/admin/refunds/{id}/query`, `/admin/refunds/{id}/retry`, `/admin/checkout-sessions/{id}/mark-handled`)
- [ ] WebMvc contract tests cho toàn bộ endpoints.

---

## Giai đoạn 5: Kiểm chứng toàn diện & Nghiệm thu
- [ ] Chạy toàn bộ test suite (`mvn clean verify`)
- [ ] Kiểm tra tính toàn vẹn và không vi phạm DB ownership
- [ ] Cập nhật trạng thái tiến độ vào `payment-service.md` và `docs/plans/active/payment-service.md`
