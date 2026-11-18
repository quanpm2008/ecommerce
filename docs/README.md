# Bản đồ tài liệu ecommerce

## Bắt đầu từ kết quả cần sửa

Đọc hồ sơ đúng task dưới đây, các quy tắc/ngoại lệ của nó và bằng chứng hiện
trạng; chỉ mở section nguồn được dẫn khi cần chi tiết contract. Không cần đọc
tuần tự toàn bộ tài liệu nguồn. Baseline là SAD v1.1 §7.1/D.1, được người dùng
chấp nhận ngày 11/11/2026; nội dung quyết định không đổi khi chuyển vị trí tài liệu.

| Việc cần làm | Đọc trước | Đọc thêm khi chạm biên |
|---|---|---|
| Auth, phiên, User/Seller, hồ sơ, địa chỉ, Admin/ban | [identity](requirements/identity.md) | Quyền/thuật ngữ, Order/Finance obligations, Notification |
| Product, search, media, tồn hiển thị | [catalog](requirements/catalog.md) | G-06 ở Identity; Inventory tại Order |
| Cart, BUY_NOW, quote/pay, kho, giao/hủy/nhận, deadline | [checkout-orders](requirements/checkout-orders.md) | Payment, Finance, Identity, Notification |
| Simulator/Payment/Refund, khách thử lại hoàn tiền | [payments](requirements/payments.md) | Order state và Finance pending |
| Settlement, ví, rút tiền, rounding, đối soát | [finance](requirements/finance.md) | Payment/Order nguồn, Identity ban |
| Email demo, event/delivery/retry | [notifications](requirements/notifications.md) | Producer và Identity contact/reset secret |
| Library, package, DB owner, test/local run | [engineering](engineering.md) | POM/config/code/test và runbook hiện có |
| Cấu trúc phần mềm, service/transaction, quyết định kiến trúc | [SAD](requirements/software-architecture.md) | [System Architecture](requirements/system-architecture.md); engineering và coverage |
| Use case, actor, ngoại lệ và AC tại nguồn | [FRD v5.5](requirements/FRD.md) | Hồ sơ theo task và coverage để đối chiếu baseline |
| Endpoint, DTO, mã lỗi và sự kiện tại nguồn | [API v1.1](requirements/api_specs.md) | FRD và Database; không thay OpenAPI triển khai |
| Entity, field, bất biến và state machine tại nguồn | [Database v5.1](requirements/database.md) | FRD, API và ownership trong engineering |
| Phạm vi, thuật ngữ, mã, phân loại bằng chứng | [requirements README](requirements/README.md) | Coverage khi có xung đột/TBD |
| Kiểm tra nguồn, độ phủ, phần chưa chuyển | [coverage](requirements/coverage.md) | Section nguồn được dẫn, không tự chọn policy |
| Giá trị tham số đã chốt: giới hạn, hoa hồng, thử lại, địa giới | [parameters](requirements/parameters.md) | Hồ sơ nghiệp vụ và contract tương ứng |

## Authority và nguồn

- [AGENTS](../AGENTS.md) và [WORKFLOW](WORKFLOW.md): cách làm việc, phạm vi phê
  duyệt, planning/validation và completion.
- [SAD](requirements/software-architecture.md) §7.1/D.1: quyết định và thứ tự
  ưu tiên nguồn; phụ lục/DELTA kỹ thuật chưa tự thành contract chính thức.
- [FRD v5.5](requirements/FRD.md): use case, quyền lợi/ngoại lệ và nghiệm thu;
  [Database v5.1](requirements/database.md): entity, field/bất biến/state machine;
  [API v1.1](requirements/api_specs.md): giao tiếp/DTO/lỗi;
  [System Architecture](requirements/system-architecture.md): thành phần/boundary.
- Ba nguồn trên mang phiên bản gốc ngày 16/11/2026; [Thẻ 15](requirements/coverage.md#the-15) cập nhật phần ban theo nghĩa vụ/J7, rút số dư nhỏ, HIDDEN/provision và trigger sau thu theo quyết định được duyệt ngày 17/11/2026. Các phần này ưu tiên hơn đoạn cũ cùng chủ đề.
- [Thẻ 16 và các xác nhận ngày 18/11/2026](requirements/parameters.md) chốt các giới hạn, hoa hồng chung 5%, thử lại hoàn tiền/email và danh mục địa giới tĩnh. Các quyết định này ưu tiên hơn đoạn cũ còn ghi dùng tạm hoặc chưa chốt cùng chủ đề; P-01 vẫn dùng tạm 15 phút, N-09/N-11/P-37 không đổi.
- Các phần ngoài phạm vi Thẻ 15 và Thẻ 16 của hồ sơ và coverage còn có nguồn/số dòng baseline 11/11/2026, chưa xác nhận đồng bộ toàn bộ. Đối chiếu authority trước khi code; tài liệu không chứng minh implementation.
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
