# Bản đồ tài liệu ecommerce

## Bắt đầu từ kết quả cần sửa

Đọc hồ sơ đúng task dưới đây, các quy tắc/ngoại lệ của nó và bằng chứng hiện
trạng; chỉ mở section nguồn được dẫn khi cần chi tiết contract. Không cần đọc
tuần tự toàn bộ business/. Baseline là SAD v1.1 §7.1/D.1, được người dùng chấp
nhận ngày 11/11/2026; nguồn gốc vẫn giữ nguyên.

| Việc cần làm | Đọc trước | Đọc thêm khi chạm biên |
|---|---|---|
| Auth, phiên, User/Seller, hồ sơ, địa chỉ, Admin/ban | [identity](requirements/identity.md) | Quyền/thuật ngữ, Order.has-paid-order, Finance/Notification |
| Product, search, media, tồn hiển thị | [catalog](requirements/catalog.md) | G-06 ở Identity; Inventory tại Order |
| Cart, BUY_NOW, quote/pay, kho, giao/hủy/nhận, deadline | [checkout-orders](requirements/checkout-orders.md) | Payment, Finance, Identity, Notification |
| Simulator/Payment/Refund, Admin retry | [payments](requirements/payments.md) | Order state và Finance pending |
| Settlement, ví, rút tiền, rounding, đối soát | [finance](requirements/finance.md) | Payment/Order nguồn, Identity ban |
| Email demo, event/delivery/retry | [notifications](requirements/notifications.md) | Producer và Identity contact/reset secret |
| Library, package, DB owner, test/local run | [engineering](engineering.md) | POM/config/code/test và runbook hiện có |
| Phạm vi, thuật ngữ, mã, phân loại bằng chứng | [requirements README](requirements/README.md) | Coverage khi có xung đột/TBD |
| Kiểm tra nguồn, độ phủ, phần chưa chuyển | [coverage](requirements/coverage.md) | Section nguồn được dẫn, không tự chọn policy |

## Authority và nguồn

- [AGENTS](../AGENTS.md) và [WORKFLOW](WORKFLOW.md): cách làm việc, phạm vi phê
  duyệt, planning/validation và completion.
- [SAD](../business/Software-Architecture.md) §7.1/D.1: quyết định và thứ tự
  ưu tiên nguồn; phụ lục/DELTA kỹ thuật chưa tự thành contract chính thức.
- [FRD](../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md): use case,
  quyền lợi/ngoại lệ và nghiệm thu; [Database](../business/database.md): entity,
  field/bất biến/state machine; [API](../business/api.md): giao tiếp/DTO/lỗi;
  [System Architecture](../business/system-architecture.md): thành phần/boundary.
- Code/migrations, [Identity OpenAPI](../identity-service/openapi.json), test,
  POM/Compose chứng minh hiện trạng, không tự thay yêu cầu nguồn.
- [README](../README.md) và [Identity RUN](../identity-service/RUN.md): vận hành
  hiện có; một số mô tả cũ được ghi rõ ở engineering, chưa được sửa tại nguồn.
- [Identity progress](../identity-service/identity-service.md),
  [checklist](../identity-service/identity-service-checklist.md),
  [review backlog](../identity-service/should.md): ghi nhận implementation và
  phát hiện lịch sử; checkbox không thay kết quả test mới.

## Khi thay đổi

Cập nhật hồ sơ sở hữu yêu cầu, AC và link code/API/schema/test của phần chạm;
cập nhật coverage khi source hoặc trạng thái chuyển đổi đổi. Giữ mã ổn định,
phân biệt yêu cầu/hiện trạng/đề xuất/câu hỏi mở. Code task thay đổi behavior
phải kiểm chứng theo WORKFLOW; tài liệu hoàn tất không có nghĩa AT runtime pass.

[Plans](plans/README.md) lưu một kế hoạch khi cần tiếp nối nhiều lượt.
[Decisions](decisions/README.md) chỉ chứa quyết định thực sự được chấp nhận;
không sao chép ADR nguồn thành quyết định mới. [Templates](templates/) là cấu
trúc tùy chọn, không là authority về nghiệp vụ hoặc lệnh vận hành.
