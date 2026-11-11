# Thông báo và email demo

Baseline: SAD v1.1 §7.1/D.1, được người dùng chấp nhận ngày 11/11/2026.
[Quy ước/thuật ngữ](README.md) · [Coverage và câu hỏi](coverage.md) · [Kỹ thuật/validation](../engineering.md).

Đọc đúng UC dưới đây, quy tắc bổ sung và hàng AC liên quan; chỉ mở mục contract nguồn khi cần DTO/cột/endpoint chi tiết. Yêu cầu TO-BE không chứng minh code đã làm đúng.

[UC-G01](#uc-g01)

## Phạm vi sửa và bằng chứng hiện trạng

Khảo sát tĩnh ngày 11/11/2026; mọi test nêu dưới đây **chưa chạy**.

| Phạm vi | Code owner hiện có | Test đã tìm thấy | Giới hạn |
|---|---|---|---|
| G01 | [notification-service/src/main/java/com/example/notification_service/NotificationServiceApplication.java](../../notification-service/src/main/java/com/example/notification_service/NotificationServiceApplication.java) | [notification-service/src/test/java/com/example/notification_service/NotificationServiceApplicationTests.java](../../notification-service/src/test/java/com/example/notification_service/NotificationServiceApplicationTests.java) | Chỉ scaffold; chưa delivery/email demo/consumer/migration nghiệp vụ. |
| Nguồn event Identity | [identity-service/src/main/java/com/example/identity_service/outbox/UnconfiguredEventPublisher.java](../../identity-service/src/main/java/com/example/identity_service/outbox/UnconfiguredEventPublisher.java) | [identity-service/src/test/java/com/example/identity_service/outbox/OutboxRelayTests.java](../../identity-service/src/test/java/com/example/identity_service/outbox/OutboxRelayTests.java) | Publisher chưa cấu hình; Outbox PENDING không đồng nghĩa Notification đã nhận/gửi. |

Khi lập task, lần theo caller/consumer và artifacts trong cùng hàng, rồi xem dependency liên service dưới đây; không đổi các source gốc để che lệch implementation.

## Contract và dữ liệu cần tra cứu

- [§3.7, §6](../../business/database.md): field, required/conditional, unique/FK/CHECK và ownership. Bảng nguồn là thiết kế, không khẳng định migration tồn tại.
- [§7.1–7.3](../../business/api.md): provider/consumer allowlist, event payload và jobs. [§7.2](../../business/Software-Architecture.md): DELTA cần review.
- Public paths bên dưới là phần sau `/api/v1`; internal dùng `/internal/v1`. Base URL mẫu trong nguồn không chứng minh cổng service hiện tại. Tên path là khóa contract ổn định; quyền ở bảng vẫn phải kết hợp trạng thái/ownership.
- **Bảng contract nguồn, chưa phải bằng chứng triển khai.** Endpoint Should vẫn giữ tham chiếu; luồng checkout cũ và stats/dashboard bị baseline hiệu chỉnh không được triển khai nguyên xi. DTO, body/error examples và required/optional giữ tại đúng mục nguồn, không suy ra từ tên endpoint.

[§6.7 / danh sách endpoint](../../business/api.md), L2685–2694

| \# | Method | Path | Mô tả | Quyền | UC |
| :---- | :---- | :---- | :---- | :---- | :---- |
| 1 | GET | /notifications/me | Thông báo trong ứng dụng (Should) | notification:read:own | G01 |
| 2 | GET | /notifications/me/unread-count | Số chưa đọc (chuông) | notification:read:own | G01 |
| 3 | POST | /notifications/me/{notificationId}/read | Đánh dấu đã đọc | notification:read:own | G01 |
| 4 | POST | /notifications/me/read-all | Đánh dấu tất cả đã đọc | notification:read:own | G01 |
| 5 | DELETE | /notifications/me/{notificationId} | Xóa thông báo in-app của mình | notification:read:own | G01 |

## Quy tắc bổ sung và source hiệu lực

Quy tắc chung G-09/G-11 ở [engineering](../engineering.md); G-10/G-13/G-15 ở [quy ước chung](README.md).

| Mã | Điều khoản | Nguồn / phân loại |
|---|---|---|
| <a id="req-not-001"></a>REQ-NOT-001 | Email demo là Must qua EmailAdapter; không khẳng định gửi thư thật. Lưu delivery/dedupe trước gửi, gọi adapter ngoài transaction; lỗi email không rollback Order/Withdrawal. In-app là Should. | Yêu cầu baseline; [§3.7](../../business/Software-Architecture.md), L303–318 |
| <a id="req-not-002"></a>REQ-NOT-002 | Nhận event hoặc internal API allowlist, lấy contact qua Identity REST; không public API cho browser tạo email. Phân biệt recipientUserId và ngữ cảnh Customer/Seller của cùng User; không lộ đơn/tiền Seller khác. | Yêu cầu baseline; [§3.7; API §7.1.1](../../business/Software-Architecture.md), L303–318; [§7.1.1](../../business/api.md), L2780–2789 |
| <a id="req-not-003"></a>REQ-NOT-003 | ResetUrl/token là secret nội bộ; không log hoặc đưa vào in-app/log vận hành chung. Cách xem email reset ở demo phải giới hạn môi trường/quyền; retention và cơ chế truy cập cụ thể chưa chốt. | Yêu cầu baseline; [§6.3; A.6.4](../../business/Software-Architecture.md), L616–622 |

## Acceptance criteria để triển khai/kiểm chứng

Các AC sau là diễn giải kiểm chứng từ điều khoản cùng mã UC/REQ/G/WD; chưa là kết quả test. Tham số TBD chỉ kiểm tra biên sau khi giá trị được chốt. Mỗi dòng gộp một tình huống; triển khai test phải tách assertions và cả nhánh lỗi của UC.

| Mã AC | Liên kết yêu cầu | Given | When | Then |
|---|---|---|---|---|
| <a id="ac-not-001"></a>AC-NOT-001 | UC-G01; REQ-NOT-001/002 | checkout.paid nhiều Seller, User đa vai trò | Gửi qua demo adapter, replay event, adapter lỗi | Customer một ngữ cảnh checkout, Seller đúng đơn mình; dedupe đúng ngữ cảnh; retry không rollback tiền/đơn, không tuyên bố đã gửi thư thật. |
| <a id="ac-not-002"></a>AC-NOT-002 | REQ-NOT-003 | password.reset_requested chứa secret | Xem log/màn vận hành/in-app và thử client tự tạo email | Không lộ resetUrl/token; internal có allowlist; không public API đọc mọi email; retention và quyền xem demo phải được review. |

### AT nguồn thuộc hồ sơ này

Các AT bao phủ nhiều service; hồ sơ này là đầu mối đọc, không có nghĩa chỉ sửa một module. Đọc owner khác từ coverage khi task chạm biên. **Chưa có bằng chứng chạy AT.**

| Mã | Tình huống | Bằng chứng cần có | Nguồn |
|---|---|---|---|
| <a id="at-22"></a>AT-22 | Kafka/Notification lỗi rồi khôi phục | Outbox không mất; email demo không rollback giao dịch; backlog xử lý được | [B.2](../../business/Software-Architecture.md), L1003 |

## Chênh lệch, đề xuất và câu hỏi còn mở

- Chưa có Kafka trong Compose được khảo sát hoặc publisher Identity thật; không thể tuyên bố sự kiện đã giao.
- P-19/N-06, retry/backoff/retention và quyền xem email demo chưa chốt. Dedupe email ở nghiệp vụ khác với bảo đảm exactly-once của provider; adapter thật cần quyết định riêng.
- Các endpoint `/notifications/me` là contract Should in-app, không là DoD Must email demo. Không tạo bảng Notification in-app chỉ để làm demo email.

## Đặc tả UC và ngoại lệ


<a id="uc-g01"></a>
## UC-G01 — Thông báo

Nguồn: [§6 / UC-G01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1287–1316. Vai trò: Hệ thống → Khách hàng, Người bán · Ưu tiên: M (email) / S (in-app).

Các điều khoản dưới đây là yêu cầu nguồn đã đối chiếu baseline; nhánh Google/in-app ghi Should không thuộc DoD Must. Mã clause giữ ổn định khi di chuyển tài liệu.

| Mã điều khoản | Loại | Yêu cầu / điều kiện / ngoại lệ | Nguồn hiệu lực |
|---|---|---|---|
| <a id="uc-g01.goal-01"></a>UC-G01.GOAL-01 | Mục tiêu | Thông báo về lần thanh toán chung, các sự kiện riêng của từng đơn và các sự kiện ví/rút tiền của Seller. | [UC-G01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1291 |
| <a id="uc-g01.pre-01"></a>UC-G01.PRE-01 | Điều kiện | Có một sự kiện nghiệp vụ ảnh hưởng khách hoặc Seller. | [UC-G01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1293 |
| <a id="uc-g01.main-01"></a>UC-G01.MAIN-01 | Luồng chính | Khi thanh toán chung thành công, hệ thống gửi khách một thông báo gồm phiên thanh toán, tổng tiền và danh sách đơn theo cửa hàng; mỗi Seller nhận thông báo đơn mới của mình. | [UC-G01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1297 |
| <a id="uc-g01.main-02"></a>UC-G01.MAIN-02 | Luồng chính | Khi một đơn được xác nhận đã giao, bị từ chối, bị hủy, đã hoàn hoặc đã được xác nhận nhận, hệ thống gửi thông báo gắn đơn và tên cửa hàng; với đơn đã xác nhận, Seller được báo số tiền vừa chuyển sang số dư khả dụng. | [UC-G01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1299 |
| <a id="uc-g01.main-03"></a>UC-G01.MAIN-03 | Luồng chính | Khi Seller quá 7 ngày chưa xác nhận giao/từ chối hoặc khách quá 7 ngày chưa xác nhận, hệ thống thông báo kết quả tự động của đơn. | [UC-G01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1301 |
| <a id="uc-g01.main-04"></a>UC-G01.MAIN-04 | Luồng chính | Khi Seller rút tiền thành công, hệ thống gửi biên nhận gồm mã giao dịch rút, số tiền và số dư khả dụng còn lại. | [UC-G01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1303 |
| <a id="uc-g01.main-05"></a>UC-G01.MAIN-05 | Luồng chính | Email demo bất đồng bộ là Must qua DemoEmailAdapter; không tuyên bố email thật đã giao. In-app là Should, chưa bắt buộc trong MVP. | [UC-G01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1305; **baseline thay thế/hiệu chỉnh:** [§3.7](../../business/Software-Architecture.md), L303–318 |
| <a id="uc-g01.alt-01"></a>UC-G01.ALT-01 | Ngoại lệ | Gửi email lỗi: ghi nhận và thử lại tối đa P-19, không làm thay đổi trạng thái nghiệp vụ. | [UC-G01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1309 |
| <a id="uc-g01.alt-02"></a>UC-G01.ALT-02 | Ngoại lệ | Cùng sự kiện bị xử lý lại: không gửi thông báo trùng cho cùng người nhận. | [UC-G01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1311 |
| <a id="uc-g01.alt-03"></a>UC-G01.ALT-03 | Ngoại lệ | Thông báo hoàn tiền, số dư ví và rút tiền có nhãn “Giả lập — không phát sinh tiền thật”. | [UC-G01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1313 |
| <a id="uc-g01.post-01"></a>UC-G01.POST-01 | Hậu điều kiện | Khách phân biệt một lần thanh toán với từng đơn giao/hủy/hoàn tiền; Seller chỉ nhận thông báo về đơn và ví của mình. | [UC-G01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1315 |
