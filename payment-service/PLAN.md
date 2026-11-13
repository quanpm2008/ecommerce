# Execution Plan: Payment Service Implementation

Date: 2026-11-13

## Status

Active

## Outcome

Xây dựng và hoàn thiện `payment-service` đáp ứng đầy đủ yêu cầu nghiệp vụ và kỹ thuật theo Baseline SAD v1.1 §7.1/D.1 và [docs/requirements/payments.md](../docs/requirements/payments.md), bao gồm:
1. Schema và migration Flyway chuẩn cho bảng `payments`, `refunds`, và `outbox_events` (không vi phạm ownership).
2. Tích hợp `PaymentGateway` nội bộ (`SimulatedGateway`) theo 2 giai đoạn (Giai đoạn 1 SUCCESS; kiến trúc sẵn sàng cho Giai đoạn 2).
3. Triển khai API khách hàng (UC-B05: thanh toán một lần theo checkout session, tra cứu trạng thái thanh toán).
4. Triển khai API quản trị và xử lý sự cố (UC-E08, UC-G02: quản lý vấn đề thanh toán, danh sách/chi tiết hoàn tiền, tra cứu/thử lại hoàn tiền, đánh dấu đã xử lý).
5. Đảm bảo toàn vẹn dữ liệu: khóa chống lặp (Idempotency Key G-09), gọi simulator ngoài transaction và cập nhật có điều kiện (G-11), kiểm soát tổng hoàn không vượt thu (`total_refunded <= amount_vnd`), Transactional Outbox phát event bất đồng bộ.
6. Bộ test toàn diện: unit test state machine, integration test WebMvc contract, Flyway migration và test kịch bản idempotency/concurrency.

## Context

- **Authority Baseline**: [SAD v1.1 §7.1/D.1](../business/Software-Architecture.md), [Database §3.4, §8.1-8.4](../business/database.md), [API §6.5, §7.1-7.3](../business/api.md).
- **Hồ sơ nghiệp vụ**: [docs/requirements/payments.md](../docs/requirements/payments.md) (UC-B05, UC-E08, UC-G02, REQ-PAY-001..004).
- **Tiến độ & Checklist nội bộ**: [payment-service.md](payment-service.md) và [payment-service-checklist.md](payment-service-checklist.md).
- **Quy ước kỹ thuật**: [docs/engineering.md](../docs/engineering.md) (Java 21, Spring Boot, PostgreSQL, Flyway, Local Transaction, Outbox pattern).
- **Hiện trạng repository**: `payment-service` hiện chỉ là scaffold khởi tạo, chưa có entity, migration, logic thanh toán/hoàn tiền hay test nghiệp vụ.

## Scope

In scope:
- **Cấu hình & Dependencies**: Kích hoạt Spring Data JPA, Flyway migration, PostgreSQL driver, Bean Validation, Testcontainers trong `pom.xml` và `application.properties`.
- **Database Schema (Flyway V1)**:
  - Bảng `payments`: `payment_id`, `checkout_session_id`, `customer_id`, `amount_vnd`, `currency`, `provider`, `environment`, `idempotency_key`, `status`, `provider_transaction_id`, `error_code`, `created_at`, `completed_at`.
  - Bảng `refunds`: `refund_id`, `payment_id`, `order_id`, `scope`, `amount_vnd`, `reason`, `status`, `idempotency_key`, `provider_refund_id`, `retry_count`, `needs_manual_review`, `manual_note`, `created_at`, `completed_at`.
  - Bảng `outbox_events`: lưu sự kiện miền (`payment.succeeded`, `payment.failed`, `refund.succeeded`, v.v.) cùng transaction nội bộ.
- **Simulator & Gateway Abstraction**:
  - Interface `PaymentGateway` (`charge`, `refund`, `query`).
  - Cài đặt `SimulatedGateway` nội bộ (kịch bản mặc định SUCCESS cho giai đoạn 1, hỗ trợ mở rộng FAIL/TIMEOUT/LATE_SUCCESS/REFUND_FAIL cho giai đoạn 2).
- **Domain & Application Logic**:
  - `Payment`: State transition `PENDING -> SUCCESS / FAILED`, tính toán `refundState` dẫn xuất từ các `Refund SUCCESS`.
  - `Refund`: Hoàn `ORDER` (gắn với `order_id`) và `FULL_CHECKOUT` (cho session đóng nhưng nhận thanh toán muộn). Cơ chế khóa `Payment` và kiểm tra ràng buộc `total_refunded <= amount_vnd`.
  - Idempotency handler (G-09): Cache/DB check theo `idempotency_key`, cùng payload trả kết quả cũ, khác payload trả 409 Conflict.
  - Transaction boundary (G-11 & REQ-PAY-002): Ghi nhận ý định `PENDING` -> gọi Gateway ngoài transaction -> cập nhật kết quả + ghi Outbox trong transaction.
- **REST Endpoints & Contract**:
  - `POST /api/v1/payments`: Tạo thanh toán cho checkout session.
  - `GET /api/v1/payments/by-checkout/{checkoutSessionId}`: Lấy trạng thái thanh toán.
  - `GET /api/v1/payments/admin/issues`: Danh sách vấn đề cần xử lý.
  - `GET /api/v1/payments/admin/payments/by-checkout/{checkoutSessionId}`: Xem thanh toán và danh sách hoàn tiền.
  - `GET /api/v1/payments/admin/refunds`: Danh sách các khoản hoàn tiền.
  - `POST /api/v1/payments/admin/refunds/{refundId}/query`: Tra cứu lại kết quả hoàn tiền từ gateway.
  - `POST /api/v1/payments/admin/refunds/{refundId}/retry`: Thử lại lệnh hoàn tiền thất bại an toàn.
  - `POST /api/v1/payments/admin/checkout-sessions/{checkoutSessionId}/mark-handled`: Đánh dấu đã xử lý đối soát.
  - Chuẩn response envelope: `data` (thành công), `error` (thất bại), hỗ trợ `X-Request-Id`.
- **Validation & Test Suite**:
  - Unit tests: domain rules, state machines, validation, idempotency logic.
  - Integration tests: WebMvc contract tests, DB integration tests với Flyway/PostgreSQL (hoặc H2 Postgres mode/Testcontainers), concurrency locking tests.

Out of scope:
- Tích hợp cổng thanh toán bên thứ ba thực tế (VNPay, MoMo, Stripe...) - MVP dùng bộ giả lập `SimulatedGateway`.
- Tự động thay đổi trạng thái của Order/Settlement (thuộc quyền sở hữu của Order Service và Finance Service, giao tiếp qua event Outbox/Kafka).
- CQRS/Event Sourcing toàn hệ thống hoặc hạ tầng Redis/distributed lock không có trong baseline.

## Approach

Kế hoạch thực hiện tuần tự qua 5 giai đoạn:

1. **Giai đoạn 1: Thiết lập nền tảng dự án & Dependencies**
   - Bổ sung cấu hình `pom.xml`: Spring Data JPA, Flyway, PostgreSQL, Validation, MapStruct (nếu cần), Test dependencies.
   - Cấu hình `application.properties` (datasource, flyway, JPA validate/none, logging).
   - Kiểm tra build `mvn clean test-compile` qua được.

2. **Giai đoạn 2: Thiết kế Schema, Migration & Entities**
   - Viết Flyway migration script `V1__init_payment_schema.sql` cho schema `payment_service` (bảng `payments`, `refunds`, `outbox_events`, các ràng buộc CHECK, INDEX, UNIQUE).
   - Tạo JPA Entities, Repositories và Enums tương ứng.
   - Viết Integration test kiểm tra Flyway migration chạy sạch trên database.

3. **Giai đoạn 3: Core Domain, Payment Gateway & Transaction Flow**
   - Định nghĩa `PaymentGateway` interface và triển khai `SimulatedGateway` (hỗ trợ kịch bản SUCCESS, cấu hình linh hoạt).
   - Triển khai `PaymentService`:
     - Luồng tạo payment (Idempotency G-09, lưu ý định PENDING -> gọi gateway ngoài TX -> cập nhật SUCCESS/FAILED + Outbox event).
     - Tra cứu trạng thái payment theo `checkoutSessionId`.
   - Triển khai `RefundService`:
     - Tạo nghĩa vụ hoàn (khóa Payment, kiểm tra `total_refunded + new_refund <= payment.amount_vnd`).
     - Query & Retry cho refund thất bại (G-09, G-11).
   - Unit tests và concurrency test cho logic tính tổng hoàn tiền.

4. **Giai đoạn 4: API Controllers, DTOs & Error Envelopes**
   - Tạo DTOs request/response tuân theo contract tại `business/api.md` §6.5.
   - Triển khai Global Exception Handler và RequestId filter chuẩn hóa response envelope (`data`, `error`).
   - Xây dựng các Controller: `PaymentController` (Customer) và `PaymentAdminController` (Admin/Operations).
   - Viết WebMvc contract integration tests cho tất cả endpoints.

5. **Giai đoạn 5: Tinh chỉnh, Kiểm chứng toàn diện (Validation) & Tài liệu**
   - Chạy toàn bộ test suite (`mvn clean verify`).
   - Cập nhật tài liệu: bổ sung thông tin hiện trạng vào [docs/requirements/payments.md](../docs/requirements/payments.md) nếu có thay đổi contract/bằng chứng kiểm thử.
   - Đóng gói bằng chứng kiểm thử vào Result.

## Risks And Recovery

- **Rủi ro 1: Race condition khi hoàn tiền đồng thời dẫn đến tổng hoàn vượt quá số tiền đã thanh toán**.
  - *Giảm thiểu*: Dùng Pessimistic Locking (`SELECT FOR UPDATE`) trên bản ghi `Payment` tương ứng trước khi tính tổng các `Refund` đã tạo.
- **Rủi ro 2: Treo kết nối DB khi gọi Gateway ngoài Transaction**.
  - *Giảm thiểu*: Tách rõ ràng transaction: Transaction 1 (lưu PENDING) kết thúc -> gọi Gateway (kèm timeout) -> Transaction 2 (cập nhật kết quả + ghi Outbox).
- **Rủi ro 3: Sai lệch hợp đồng API so với Baseline**.
  - *Giảm thiểu*: Đối chiếu từng field, status code và error format theo đúng `business/api.md` §6.5 và `business/database.md` §3.4.
- **Phục hồi (Recovery)**:
  - Nếu gặp lỗi hoặc migration thất bại, rollback từng bước bằng Git, không xóa file ngoài phạm vi `payment-service`.

## Progress

- [ ] Giai đoạn 1: Thiết lập nền tảng dự án & Dependencies (`pom.xml`, cấu hình).
- [ ] Giai đoạn 2: Thiết kế Schema, Flyway Migration & JPA Entities.
- [ ] Giai đoạn 3: Core Domain, `SimulatedGateway`, Transaction Boundaries & Outbox.
- [ ] Giai đoạn 4: REST Controllers (UC-B05, UC-E08, UC-G02), DTOs & Exception Handling.
- [ ] Giai đoạn 5: Test suite hoàn chỉnh (`mvn verify`), đối soát hợp đồng và nghiệm thu.

## Decisions

- 2026-11-13: Khởi tạo Execution Plan và lưu trữ trực tiếp bên trong `payment-service/` để đảm bảo tính độc lập, tách biệt của module.
- 2026-11-13: Simulator nội bộ triển khai qua interface `PaymentGateway` để cách ly hoàn toàn tầng hạ tầng với tầng domain nghiệp vụ, tuân thủ REQ-PAY-001.

## Validation

- Focused proof: Unit tests cho `Payment` state transitions, `Refund` limits calculation, `SimulatedGateway` scenarios.
- Integration or end-to-end proof: WebMvc test cho các endpoints UC-B05, UC-E08, UC-G02; Spring Boot Context / DB migration test.
- Repository-required checks: Build Maven thành công (`mvn clean test`), không vi phạm ranh giới bảng và ownership dữ liệu.

## Result

*(Sẽ hoàn tất và ghi nhận bằng chứng sau khi triển khai và kiểm chứng toàn bộ)*
