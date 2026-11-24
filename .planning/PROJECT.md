# Ecommerce — Order, Payment và Finance

## What This Is

Phát triển ba module hiện có của sàn nhiều người bán. Order điều phối checkout, Inventory và vòng đời đơn. Payment ghi nhận thu/hoàn giả lập. Finance quản lý Settlement và ví người bán.

## Core Value

Một phiên mua có thể được giữ hàng, thanh toán, giao hoặc hoàn đúng số tiền, một lần cho mỗi tác động nghiệp vụ.

## Requirements

### Validated

Đã xác minh cấu trúc source. Chưa có nghiệm thu đầu-cuối cho các yêu cầu milestone.

### Active

- [ ] Checkout/Inventory, thanh toán qua provider demo và hết hạn, fulfillment và Refund.
- [ ] Settlement, ví, rút tiền, đối soát và các API biên do ba service sở hữu.

### Out of Scope

- Triển khai Identity, Product, Cart, Notification, Gateway, Discovery hoặc ứng dụng provider thành các phase riêng.
- Phân tích kinh doanh đã hoãn, đổi trả sau `CONFIRMED`, giao dịch tiền thật và tạo lại module Maven hiện hữu.

## Context

Xem [kiểm kê AS-IS](intel/AS-IS.md). Đã quan sát Java/Maven, PostgreSQL, khai báo Kafka và ElasticMQ local. Provider mock chỉ phục vụ kiểm thử tại biên.

## Constraints

- Mỗi service ghi dữ liệu của mình và dùng transaction cục bộ. [Ràng buộc tổng hợp](intel/constraints.md)
- Quote BUY_NOW có TTL 5 phút. Cart contract còn mở. `checkout.closed.closeReason` dùng `PAYMENT_FAILED` hoặc `EXPIRED` theo ADR 0001.
- Phạm vi và mục tiêu Roadmap đã được duyệt. Phase 1 cần người dùng duyệt PLAN.md trước khi viết code hoặc thực thi.

## Key Decisions

| Quyết định | Căn cứ |
| --- | --- |
| D-01 · ADR 0001 · Checkout qua provider demo | [docs/decisions/0001-provider-checkout.md · Quyết định](../docs/decisions/0001-provider-checkout.md) · [docs/requirements/coverage.md · Quyết định provider checkout 20/11](../docs/requirements/coverage.md) |
| D-02 · Baseline SAD và thứ tự ưu tiên | [docs/requirements/software-architecture.md · §7.1, §D.1](../docs/requirements/software-architecture.md) · [docs/requirements/coverage.md · Baseline và giới hạn kết luận](../docs/requirements/coverage.md) |
| D-03 · Thẻ 15 · Nghĩa vụ, provision và kiểm tra sau thu | [docs/requirements/coverage.md · Thẻ 15, mục 1–3](../docs/requirements/coverage.md) · [docs/requirements/database.md · §3.1.1, §8.2B, §8.3](../docs/requirements/database.md) |
| D-04 · Thẻ 16 · Tham số liên quan ba service | [docs/requirements/coverage.md · Thẻ 16](../docs/requirements/coverage.md) · [docs/requirements/parameters.md · P-COM-01, P-10, P-21, P-RF-01](../docs/requirements/parameters.md) |
| D-05 · P-37 · Phạm vi tìm kiếm | [docs/requirements/coverage.md · Thẻ 16, P-37](../docs/requirements/coverage.md) · [docs/requirements/parameters.md · P-37](../docs/requirements/parameters.md) |

[Yêu cầu](REQUIREMENTS.md) · [Roadmap](ROADMAP.md) · [Intel GSD](intel/SYNTHESIS.md)
