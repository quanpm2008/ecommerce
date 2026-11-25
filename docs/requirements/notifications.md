# Thông báo trong ứng dụng và email

Baseline: SAD v1.1 §7.1/D.1, được người dùng chấp nhận ngày 11/11/2026.
[Quy ước/thuật ngữ](README.md) · [Coverage và câu hỏi](coverage.md) · [Kỹ thuật/validation](../engineering.md).

Theo yêu cầu người dùng ngày 25/11/2026, **in-app là Must; email thông báo nghiệp vụ không bắt buộc, triển khai sau**. Quyết định này thay riêng mức ưu tiên kênh trong baseline và các đoạn cũ cùng chủ đề; không thay các luồng email xác minh/đặt lại mật khẩu của Identity, không đưa token/resetUrl vào in-app. [Coverage](coverage.md#notification-channels) ghi phạm vi thay thế. Giữ P-22: tối đa 50 thông báo gần nhất/30 ngày; P-19 áp dụng khi triển khai gửi email, không là điều kiện hoàn thành in-app.

Đọc đúng UC dưới đây, quy tắc bổ sung và hàng AC liên quan; chỉ mở mục contract nguồn khi cần DTO/cột/endpoint chi tiết. Yêu cầu TO-BE không chứng minh code đã làm đúng.

[UC-G01](#uc-g01)

## Phạm vi sửa và bằng chứng hiện trạng

Cập nhật implementation ngày 25/11/2026: Notification là một ứng dụng Layered (`controller → service → repository`, dữ liệu ở `model`), một module Maven/process/schema. Kiểm chứng bên dưới chạy trên hạ tầng cô lập, không sửa database đang dùng hoặc xác nhận toàn bộ checkout end-to-end.

| Phạm vi | Code owner hiện có | Test đã tìm thấy | Giới hạn |
|---|---|---|---|
| G01 in-app | [controller](../../notification-service/src/main/java/com/example/notification_service/controller/NotificationController.java), [service](../../notification-service/src/main/java/com/example/notification_service/service/NotificationApplication.java), [repository](../../notification-service/src/main/java/com/example/notification_service/repository/NotificationStore.java), [migration V1](../../notification-service/src/main/resources/db/migration/V1__notification.sql) | [HTTP](../../notification-service/src/test/java/com/example/notification_service/NotificationEndpointsTests.java), [persistence](../../notification-service/src/test/java/com/example/notification_service/repository/NotificationStoreTests.java), [Kafka](../../notification-service/src/test/java/com/example/notification_service/NotificationKafkaTests.java) | 9 persistence + 5 HTTP + 2 Kafka test đã pass. HTTP dùng Identity fixture; Identity recipient API kiểm tra riêng, chưa chạy toàn stack. Email nghiệp vụ không triển khai. |
| Nguồn event Identity | [identity-service/src/main/java/com/example/identity_service/outbox/UnconfiguredEventPublisher.java](../../identity-service/src/main/java/com/example/identity_service/outbox/UnconfiguredEventPublisher.java) | [identity-service/src/test/java/com/example/identity_service/outbox/OutboxRelayTests.java](../../identity-service/src/test/java/com/example/identity_service/outbox/OutboxRelayTests.java) | Publisher chưa cấu hình; Outbox PENDING không đồng nghĩa Notification đã nhận/gửi. |
| Kiểm tra người nhận | [Identity recipient lookup](../../identity-service/src/main/java/com/example/identity_service/internal/InternalController.java) | [InternalQueriesTests](../../identity-service/src/test/java/com/example/identity_service/internal/InternalQueriesTests.java), OpenApiDocsTests | 9 test đã pass; endpoint chỉ trả userId/accountType, không phụ thuộc email. |

Khi lập task, lần theo caller/consumer và artifacts trong cùng hàng, rồi xem dependency liên service dưới đây; không đổi các source gốc để che lệch implementation.

## Contract và dữ liệu cần tra cứu

- [Database §3.7](database.md): field, ownership và tombstone/dedupe; migration implementation được dẫn ở bảng trên.
- [API §6.7/7.1.1/7.2](api_specs.md): endpoint, producer/type allowlist, templateData và snapshot event đã được người dùng chọn ngày 25/11/2026. Producer bổ sung dữ liệu thiếu, không để Notification đọc chéo DB hoặc tự đoán người nhận.
- Public paths bên dưới là phần sau `/api/v1`; internal dùng `/internal/v1`. Base URL mẫu trong nguồn không chứng minh cổng service hiện tại. Tên path là khóa contract ổn định; quyền ở bảng vẫn phải kết hợp trạng thái/ownership.
- **Bảng contract nguồn, chưa phải bằng chứng triển khai.** Các endpoint in-app dưới đây thuộc Must theo quyết định 25/11/2026. DTO, body/error examples và required/optional giữ tại đúng mục nguồn, không suy ra từ tên endpoint.

[§6.7 / danh sách endpoint](../../business/api.md), L2685–2694

| \# | Method | Path | Mô tả | Quyền | UC |
| :---- | :---- | :---- | :---- | :---- | :---- |
| 1 | GET | /notifications/me | Thông báo trong ứng dụng (Must) | notification:read:own | G01 |
| 2 | GET | /notifications/me/unread-count | Số chưa đọc (chuông) | notification:read:own | G01 |
| 3 | POST | /notifications/me/{notificationId}/read | Đánh dấu đã đọc | notification:read:own | G01 |
| 4 | POST | /notifications/me/read-all | Đánh dấu tất cả đã đọc | notification:read:own | G01 |
| 5 | DELETE | /notifications/me/{notificationId} | Xóa thông báo in-app của mình | notification:read:own | G01 |

## Quy tắc bổ sung và source hiệu lực

Quy tắc chung G-09/G-11 ở [engineering](../engineering.md); G-10/G-13/G-15 ở [quy ước chung](README.md).

| Mã | Điều khoản | Nguồn / phân loại |
|---|---|---|
| <a id="req-not-001"></a>REQ-NOT-001 | In-app là Must: tạo thông báo và khóa chống trùng cùng transaction, cho người nhận xem danh sách/số chưa đọc, đánh dấu đã đọc từng bản/tất cả và xóa thông báo của mình. Email nghiệp vụ là tùy chọn, triển khai sau; không cần EmailAdapter hay worker email để hoàn thành in-app. Khi triển khai email, lưu delivery/dedupe trước gửi và gọi adapter ngoài transaction; lỗi thông báo không rollback hay đổi trạng thái Order, Payment, Refund, Withdrawal hoặc tài khoản. | [Quyết định 25/11/2026](coverage.md#notification-channels); [API §6.7](api_specs.md); [P-19](parameters.md#gửi-lại-email--p-19) |
| <a id="req-not-002"></a>REQ-NOT-002 | Nhận event/internal API allowlist, chỉ lấy contact qua Identity REST khi cần email; không public API cho browser tạo thông báo. recipientUserId là tài khoản đúng accountType CUSTOMER/SELLER/ADMIN độc lập, không gửi gộp vai trò cùng người; không lộ đơn/tiền tài khoản khác. ACCOUNT_BANNED/UNBANNED/USER_DISABLED/ENABLED phải mô tả đúng accountStatus và accessMode: user.enabled do nghĩa vụ muộn chỉ mở BANNED giới hạn, không nói “đã gỡ cấm”; DISABLED nêu restoreUntil nếu có. | [Thẻ 15](coverage.md#the-15); [API §7.1.1/7.2](api_specs.md) |
| <a id="req-not-003"></a>REQ-NOT-003 | ResetUrl/token là secret nội bộ; không log hoặc đưa vào in-app/log vận hành chung. Cách xem email reset ở demo phải giới hạn môi trường/quyền; retention và cơ chế truy cập cụ thể chưa chốt. | Yêu cầu baseline; [§6.3; A.6.4](software-architecture.md), L616–622 |
| <a id="req-not-004"></a>REQ-NOT-004 | Khi triển khai email, giữ P-19: mỗi email có lần gửi đầu và tối đa 3 lần gửi lại, chờ 1, 5, 15 phút từ lần thất bại trước; chỉ gửi lại lỗi tạm thời như timeout, mất kết nối, lỗi nhà cung cấp hoặc giới hạn tần suất. Có Retry-After thì không gửi trước thời điểm nhà cung cấp yêu cầu. Dừng ngay với lỗi vĩnh viễn như địa chỉ nhận không hợp lệ hoặc link/token hết hạn; hết lượt thì ghi nhận gửi thất bại, không rollback nghiệp vụ. Mọi lần dùng cùng thông báo và khóa chống trùng, không tạo email nghiệp vụ mới chỉ vì thử lại. | [P-19](parameters.md#gửi-lại-email--p-19) |
| <a id="req-not-005"></a>REQ-NOT-005 | P-22: in-app (Must) hiển thị tối đa 50 thông báo gần nhất của mỗi tài khoản, giữ nội dung 30 ngày. Giới hạn số lượng, hết hạn hoặc thao tác xóa in-app không xóa khóa chống trùng; replay không khôi phục thông báo đã xóa/hết hạn. Nếu đã triển khai email, không xóa email hoặc lịch sử gửi lại. Không áp dụng 30 ngày này cho email demo, resetUrl/token hoặc bí mật khác. | [P-22](parameters.md) |
| <a id="req-not-006"></a>REQ-NOT-006 | Refund ORDER hoặc FULL_CHECKOUT hết 3 lần thử lại mà vẫn FAILED phải thông báo Customer chủ Payment và hướng dẫn khách bấm Thử lại hoàn tiền để mở chu kỳ mới. Không gửi quyền xử lý này cho Admin hoặc Seller; không thông báo đã hoàn trước Refund SUCCESS. Nội dung phân biệt hết lượt tự động với kết quả PENDING/chưa rõ; chu kỳ mới giữ nguyên Refund/số tiền/key và lịch sử. | [P-RF-01](parameters.md#thử-lại-hoàn-tiền--p-rf-01); [UC-G02](payments.md#uc-g02) |

## Acceptance criteria để triển khai/kiểm chứng

Các AC sau là diễn giải kiểm chứng từ điều khoản cùng mã UC/REQ/G/WD; chưa là kết quả test. Tham số TBD chỉ kiểm tra biên sau khi giá trị được chốt. Mỗi dòng gộp một tình huống; triển khai test phải tách assertions và cả nhánh lỗi của UC.

| Mã AC | Liên kết yêu cầu | Given | When | Then |
|---|---|---|---|---|
| <a id="ac-not-001"></a>AC-NOT-001 | UC-G01; REQ-NOT-001/002 | checkout.paid nhiều Seller, tài khoản Customer/Seller độc lập; chưa triển khai email | Xử lý rồi replay event; người nhận xem danh sách/số chưa đọc, đọc từng bản/tất cả và xóa | Customer nhận in-app cho checkout của mình, mỗi Seller chỉ đơn của mình; không trùng theo recipient/account context. Trạng thái đọc/xóa chỉ tác động bản của mình; tài khoản khác không đọc/sửa được. In-app hoạt động không cần adapter/contact email và không rollback tiền/đơn khi xử lý thông báo lỗi. |
| <a id="ac-not-002"></a>AC-NOT-002 | REQ-NOT-003 | password.reset_requested chứa secret | Xem log/màn vận hành/in-app và thử client tự tạo email | Không lộ resetUrl/token; internal có allowlist; không public API đọc mọi email; retention và quyền xem demo phải được review. |
| <a id="ac-not-003"></a>AC-NOT-003 | REQ-NOT-002; UC-E03 | Tài khoản DISABLED có nghĩa vụ đến muộn, hoặc Admin unban đúng hạn | Nhận user.enabled với BANNED, rồi *.unbanned với ACTIVE; replay event | Thông báo đầu chỉ mở quyền xử lý nghĩa vụ, không khôi phục mua/bán; thông báo sau ghi gỡ cấm. DISABLED nêu hạn khôi phục của đợt cấm; không gia hạn do gửi lại và không lộ dữ liệu tài khoản khác. |
| <a id="ac-not-004"></a>AC-NOT-004 | REQ-NOT-001/004; UC-G01.ALT-01 | Khi triển khai email: lần đầu lỗi tạm thời; nhà cung cấp có/không có Retry-After | Worker gửi lại và gặp lỗi tiếp | Không quá 3 lần gửi lại, chờ 1/5/15 phút từ thất bại trước, không sớm hơn Retry-After; cùng notification/dedupe key. Hết lượt ghi gửi thất bại, không đổi trạng thái tài khoản/đơn/thanh toán/hoàn/rút tiền hay khả năng đọc in-app. |
| <a id="ac-not-005"></a>AC-NOT-005 | REQ-NOT-003/004 | Khi triển khai email: địa chỉ nhận lỗi vĩnh viễn hoặc link/token đã hết hạn trước lần gửi lại | Worker xét gửi lại | Dừng ngay, không dùng hết lượt cho lỗi không thể thử lại; không lộ secret vào log/in-app, không gia hạn token hay áp retention 30 ngày cho email demo. |
| <a id="ac-not-006"></a>AC-NOT-006 | REQ-NOT-005 | Một tài khoản có hơn 50 thông báo, có nội dung quá 30 ngày; có hoặc chưa có email | Xem/xóa thông báo, chạy retention rồi replay sự kiện | Chỉ tối đa 50 thông báo gần nhất còn trong 30 ngày; nội dung quá hạn không còn hiển thị. Dedupe key không bị xóa do giới hạn in-app; replay không khôi phục bản đã xóa/hết hạn hay làm lộ dữ liệu tài khoản khác. Nếu có email, email và lịch sử gửi lại không bị xóa. |
| <a id="ac-not-007"></a>AC-NOT-007 | REQ-NOT-006; UC-G02 | Refund ORDER/FULL_CHECKOUT hết 3 lần thử lại đều FAILED hoặc vẫn PENDING | Phát thông báo kết quả xử lý | FAILED hết lượt: báo đúng Customer chủ Payment, hướng dẫn mở chu kỳ mới cùng Refund/amount/key; không giao thao tác cho Admin/Seller. PENDING: báo đang xác nhận, không báo thất bại/hết lượt chỉ theo thời gian, không báo đã hoàn trước SUCCESS. |

### AT nguồn thuộc hồ sơ này

Các AT bao phủ nhiều service; hồ sơ này là đầu mối đọc, không có nghĩa chỉ sửa một module. Đọc owner khác từ coverage khi task chạm biên. **Chưa có bằng chứng chạy AT.**

| Mã | Tình huống | Bằng chứng cần có | Nguồn |
|---|---|---|---|
| <a id="at-22"></a>AT-22 | Kafka/Notification lỗi rồi khôi phục | Outbox không mất; backlog tạo in-app đúng người nhận, không trùng, không rollback giao dịch; kiểm tra đọc/xóa và giới hạn P-22 không mất dedupe. Email chỉ kiểm tra khi triển khai. | [B.2](software-architecture.md); [quyết định 25/11/2026](coverage.md#notification-channels) |

## Chênh lệch, đề xuất và câu hỏi còn mở

- Chưa có Kafka trong Compose được khảo sát hoặc publisher Identity thật; không thể tuyên bố sự kiện đã giao.
- P-19 đã chốt lần gửi đầu + tối đa 3 lần gửi lại sau 1/5/15 phút; P-22 chỉ áp dụng nội dung in-app 50 thông báo/30 ngày. N-06, cách truy cập email demo, thời gian giữ email và secret vẫn cần quyết định riêng. Dedupe email ở nghiệp vụ khác với bảo đảm exactly-once của provider; adapter thật cần quyết định riêng.
- Các endpoint `/notifications/me` và dữ liệu in-app thuộc DoD Must. Email nghiệp vụ, adapter và J6 có thể triển khai sau; các AC email chỉ áp dụng khi triển khai kênh này. Luồng xác minh/đặt lại mật khẩu của Identity giữ nguyên, secret không xuất hiện trong in-app.
- Cách chạy/config và ví dụ gọi API: [DEVELOPMENT — Notification Service](../../DEVELOPMENT.md#notification-service--in-app). `sh mvnw clean test` đã pass 15 test; lần kiểm tra Kafka sau khi bổ sung recovery/privacy pass 2 test (16 test hành vi khác nhau tổng cộng). Identity: `mvn -Dtest=InternalQueriesTests,OpenApiDocsTests test` pass 9 test và sinh lại OpenAPI.
- Lỗi template/event được giữ FAILED_REVIEW với hash/mã lỗi, không giữ payload. Lỗi phụ thuộc/DB không được xác nhận offset; worker Kafka thử lại và chỉ log topic/partition/offset/lần thử, không log nội dung.

## Đặc tả UC và ngoại lệ


<a id="uc-g01"></a>
## UC-G01 — Thông báo

Nguồn lịch sử: [§6 / UC-G01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1287–1316. Vai trò: Hệ thống → Khách hàng, Người bán · Ưu tiên hiện hành: M (in-app); email nghiệp vụ tùy chọn, triển khai sau theo [quyết định 25/11/2026](coverage.md#notification-channels).

Các điều khoản giữ mã ổn định; mức ưu tiên kênh dùng quyết định mới, không dùng mức M (email)/S (in-app) trong nguồn lịch sử.

| Mã điều khoản | Loại | Yêu cầu / điều kiện / ngoại lệ | Nguồn hiệu lực |
|---|---|---|---|
| <a id="uc-g01.goal-01"></a>UC-G01.GOAL-01 | Mục tiêu | Thông báo về lần thanh toán chung, các sự kiện riêng của từng đơn và các sự kiện ví/rút tiền của Seller. | [UC-G01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1291 |
| <a id="uc-g01.pre-01"></a>UC-G01.PRE-01 | Điều kiện | Có một sự kiện nghiệp vụ ảnh hưởng khách hoặc Seller. | [UC-G01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1293 |
| <a id="uc-g01.main-01"></a>UC-G01.MAIN-01 | Luồng chính | Khi thanh toán chung thành công, hệ thống gửi khách một thông báo gồm phiên thanh toán, tổng tiền và danh sách đơn theo cửa hàng; mỗi Seller nhận thông báo đơn mới của mình. | [UC-G01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1297 |
| <a id="uc-g01.main-02"></a>UC-G01.MAIN-02 | Luồng chính | Khi một đơn được xác nhận đã giao, bị từ chối, bị hủy, đã hoàn hoặc đã được xác nhận nhận, hệ thống gửi thông báo gắn đơn và tên cửa hàng; với đơn đã xác nhận, Seller được báo số tiền vừa chuyển sang số dư khả dụng. Refund ORDER/FULL_CHECKOUT hết lượt tự động vẫn FAILED phải báo Customer chủ Payment để khách mở chu kỳ mới, không giao Admin thử lại và không báo đã hoàn trước SUCCESS. | [UC-G01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1299; [P-RF-01](parameters.md#thử-lại-hoàn-tiền--p-rf-01) |
| <a id="uc-g01.main-03"></a>UC-G01.MAIN-03 | Luồng chính | Khi Seller quá 7 ngày chưa xác nhận giao/từ chối hoặc khách quá 7 ngày chưa xác nhận, hệ thống thông báo kết quả tự động của đơn. | [UC-G01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1301 |
| <a id="uc-g01.main-04"></a>UC-G01.MAIN-04 | Luồng chính | Khi Seller rút tiền thành công, hệ thống gửi biên nhận gồm mã giao dịch rút, số tiền và số dư khả dụng còn lại. | [UC-G01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1303 |
| <a id="uc-g01.main-05"></a>UC-G01.MAIN-05 | Luồng chính | In-app là Must, gồm danh sách, số chưa đọc, đánh dấu đã đọc và xóa của chính tài khoản. Hiển thị tối đa 50 thông báo gần nhất, giữ nội dung 30 ngày theo P-22; xóa/hết hạn không làm mất khóa chống trùng. Email nghiệp vụ không bắt buộc, triển khai sau; nếu có thì không bị xóa theo retention in-app và không tuyên bố email thật đã giao chỉ từ adapter demo. | [Quyết định 25/11/2026](coverage.md#notification-channels); [P-22](parameters.md) |
| <a id="uc-g01.alt-01"></a>UC-G01.ALT-01 | Ngoại lệ | Khi triển khai email: lỗi tạm thời sau lần gửi đầu cho tối đa 3 lần gửi lại, chờ 1/5/15 phút từ thất bại trước và không sớm hơn Retry-After. Lỗi vĩnh viễn hoặc link/token hết hạn thì dừng ngay; hết lượt ghi gửi thất bại. Dùng cùng thông báo/dedupe key; không đổi trạng thái nghiệp vụ. | [P-19](parameters.md#gửi-lại-email--p-19) |
| <a id="uc-g01.alt-02"></a>UC-G01.ALT-02 | Ngoại lệ | Cùng sự kiện bị xử lý lại: không gửi thông báo trùng cho cùng người nhận. | [UC-G01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1311 |
| <a id="uc-g01.alt-03"></a>UC-G01.ALT-03 | Ngoại lệ | Thông báo hoàn tiền, số dư ví và rút tiền có nhãn “Giả lập — không phát sinh tiền thật”. | [UC-G01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1313 |
| <a id="uc-g01.post-01"></a>UC-G01.POST-01 | Hậu điều kiện | Khách phân biệt một lần thanh toán với từng đơn giao/hủy/hoàn tiền; Seller chỉ nhận thông báo về đơn và ví của mình. | [UC-G01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1315 |
