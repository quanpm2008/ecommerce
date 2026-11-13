# payment-service — Kế hoạch & Tiến độ triển khai

Service thanh toán và hoàn tiền giả lập (Payment Service). Sở hữu dữ liệu `Payment`, `Refund` và `outbox_events`. Baseline theo **SAD v1.1 §7.1/D.1**, [Database §3.4, §8.1-8.4](../../business/database.md), [API §6.5, §7.1-7.3](../../business/api.md) và [docs/requirements/payments.md](../../docs/requirements/payments.md).

---

## 1. Mục tiêu & Ranh giới (Scope & Boundary)

### Scope
- **UC-B05**: Khách hàng thanh toán một lần cho phiên checkout (`POST /api/v1/payments`), tra cứu trạng thái thanh toán theo phiên (`GET /api/v1/payments/by-checkout/{checkoutSessionId}`).
- **UC-E08**: Admin quản lý các vấn đề thanh toán (`GET /api/v1/payments/admin/issues`), xem thanh toán và danh sách hoàn tiền theo checkout session, danh sách hoàn tiền (`GET /api/v1/payments/admin/refunds`), đánh dấu đã xử lý (`mark-handled`).
- **UC-G02**: Tra cứu lại kết quả hoàn tiền từ bộ giả lập (`POST /api/v1/payments/admin/refunds/{refundId}/query`) và thử lại hoàn tiền an toàn (`POST /api/v1/payments/admin/refunds/{refundId}/retry`).
- **Bộ giả lập (Simulator)**:
  - Cài đặt `SimulatedGateway` nội bộ theo interface `PaymentGateway` (`charge`, `refund`, `query`).
  - Giai đoạn 1: kịch bản mặc định `SUCCESS`.
  - Thiết kế sẵn sàng cho Giai đoạn 2: kịch bản `FAIL`, `TIMEOUT`, `LATE_SUCCESS`, `REFUND_FAIL`.

### Data Ownership & Boundaries
- **Sở hữu**: Bảng `payments`, `refunds`, `outbox_events` trong schema `payment_service`.
- **Ranh giới nghiêm ngặt**:
  - Không ghi trực tiếp trạng thái `Order.status` hay `Settlement` của Finance.
  - Không tạo Foreign Key hoặc JOIN xuyên schema/database.
  - Không dùng distributed transaction; chỉ dùng local transaction.
  - Phát event miền (`payment.succeeded`, `payment.failed`, `refund.succeeded`) qua Transactional Outbox để các service khác tiêu thụ.

---

## 2. Quy tắc nghiệp vụ cốt lõi

1. **REQ-PAY-001**: Simulator là implementation `PaymentGateway` nội bộ; không mở public callback ra ngoài. Vẫn xây dựng luồng `PENDING`, đối soát và phục hồi ngay từ giai đoạn 1.
2. **REQ-PAY-002**: Lưu ý định `PENDING` trước khi gọi simulator ngoài transaction; sau khi có kết quả mới mở transaction cập nhật trạng thái + Outbox event. Timeout không tự kết luận `FAILED`; query/retry dùng cùng idempotency key, không tạo Payment thứ hai cho cùng checkout.
3. **REQ-PAY-003**:
   - Hoàn theo đơn (`ORDER`): gắn với `(payment_id, order_id)`.
   - Hoàn toàn bộ (`FULL_CHECKOUT`): gắn với `payment_id` khi checkout session đóng nhưng thanh toán thành công muộn.
   - Khóa bản ghi Payment (`SELECT FOR UPDATE`) để đảm bảo tổng số tiền hoàn không vượt quá số tiền đã thanh toán (`total_refunded <= payment.amount_vnd`).
   - Nếu `FAILED`, retry tái dùng cùng bản ghi / cùng idempotency key.
4. **REQ-PAY-004**:
   - `Payment.status`: chỉ `PENDING -> SUCCESS / FAILED`. Trạng thái hoàn tiền (`refundState`) được tính toán từ các `Refund SUCCESS`, không lưu cột riêng trong bảng `payments`.
   - `Refund.status`: `PENDING -> SUCCESS / FAILED`. Cho phép retry an toàn `FAILED -> PENDING`.
5. **G-09 & G-11**:
   - Chống lặp (Idempotency Key): Thanh toán gắn với `checkout_session_id`; Hoàn tiền gắn với `(payment_id, order_id)` hoặc key hoàn cả phiên.
   - Trùng key + trùng payload -> trả kết quả cũ. Trùng key + khác payload -> trả `409 Conflict`.
   - Cập nhật trạng thái dùng conditional update trong local transaction.

---

## 3. Kiến trúc kỹ thuật & Cấu hình

- **Công nghệ**: Java 21, Spring Boot 4.1.2, Spring Data JPA, Hibernate, PostgreSQL 16, Flyway Migration.
- **Datasource & Schema**:
  - Schema PostgreSQL: `payment_service`.
  - Quản lý schema bằng Flyway (`src/main/resources/db/migration/V1__*.sql`).
  - Hibernate cấu hình `ddl-auto=validate`.
- **API Response Envelope**:
  - Thành công: `{"data": ...}`.
  - Thất bại: `{"error": {"code": "...", "message": "...", "details": [...]}}`.
  - Hỗ trợ `X-Request-Id` (echo MDC, tracing).

---

## 4. Các giai đoạn triển khai (Roadmap)

- **Giai đoạn 1**: Thiết lập dependencies (`pom.xml`), cấu hình datasource, flyway, validation trong `application.properties`.
- **Giai đoạn 2**: Thiết kế schema Flyway `V1__init_payment_schema.sql`, tạo JPA Entities, Repositories và Enums.
- **Giai đoạn 3**: Triển khai `PaymentGateway`, `SimulatedGateway`, `PaymentService`, `RefundService`, Transaction boundaries và Outbox.
- **Giai đoạn 4**: Xây dựng DTOs, Exception Handlers, `PaymentController` (Customer) và `PaymentAdminController` (Admin).
- **Giai đoạn 5**: Viết bộ test toàn diện (Unit test, Integration test, WebMvc test, Concurrency test), chạy `mvn verify` và nghiệm thu.
