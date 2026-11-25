# Thanh toán giả lập và hoàn tiền

Baseline: SAD v1.1 §7.1/D.1, được người dùng chấp nhận ngày 11/11/2026.

Quyền bị cấm/ngoại lệ rút và trigger hoàn tiền được đồng bộ ngày 18/11/2026 theo [Thẻ 15](coverage.md#the-15), [FRD UC-B05/E08/G02](FRD.md), [API](api_specs.md), [Database §8.2B/D](database.md). Không xác nhận runtime đã triển khai.

Lịch thử lại và quyền mở chu kỳ hoàn tiền theo [tham số đã chốt — P-RF-01](parameters.md#thử-lại-hoàn-tiền--p-rf-01). Quyết định này thay các mô tả cũ giao Admin thử lại; giữ baseline ownership và local transaction.
[Quy ước/thuật ngữ](README.md) · [Coverage và câu hỏi](coverage.md) · [Kỹ thuật/validation](../engineering.md).

Đọc đúng UC dưới đây, quy tắc bổ sung và hàng AC liên quan; chỉ mở mục contract nguồn khi cần DTO/cột/endpoint chi tiết. Yêu cầu TO-BE không chứng minh code đã làm đúng.

[UC-B05](#uc-b05) · [UC-E08](#uc-e08) · [UC-G02](#uc-g02)

## Phạm vi sửa và bằng chứng hiện trạng

Khảo sát tĩnh ngày 11/11/2026; mọi test nêu dưới đây **chưa chạy**.

| Phạm vi | Code owner hiện có | Test đã tìm thấy | Giới hạn |
|---|---|---|---|
| B05/G02/E08 | [payment-service/src/main/java/com/example/payment_service/PaymentServiceApplication.java](../../payment-service/src/main/java/com/example/payment_service/PaymentServiceApplication.java) | [payment-service/src/test/java/com/example/payment_service/PaymentServiceApplicationTests.java](../../payment-service/src/test/java/com/example/payment_service/PaymentServiceApplicationTests.java) | Chỉ scaffold; chưa Payment/Refund/simulator/worker/migration nghiệp vụ. |

Khi lập task, lần theo caller/consumer và artifacts trong cùng hàng, rồi xem dependency liên service dưới đây; không đổi các source gốc để che lệch implementation.

## Contract và dữ liệu cần tra cứu

- [§3.4, §8.2F/G, §8.4](../../business/database.md): field, required/conditional, unique/FK/CHECK và ownership. Bảng nguồn là thiết kế, không khẳng định migration tồn tại.
- [§7.1–7.3](../../business/api.md): provider/consumer allowlist, event payload và jobs. [§7.2](software-architecture.md): DELTA cần review.
- Public paths bên dưới là phần sau `/api/v1`; internal dùng `/internal/v1`. Base URL mẫu trong nguồn không chứng minh cổng service hiện tại. Tên path là khóa contract ổn định; quyền ở bảng vẫn phải kết hợp trạng thái/ownership.
- **Bảng contract nguồn, chưa phải bằng chứng triển khai.** Endpoint Should vẫn giữ tham chiếu; luồng checkout cũ và stats/dashboard bị baseline hiệu chỉnh không được triển khai nguyên xi. DTO, body/error examples và required/optional giữ tại đúng mục nguồn, không suy ra từ tên endpoint.

[§6.5 / danh sách endpoint](../../business/api.md), L2227–2240

| \# | Method | Path | Mô tả | Quyền | UC |
| :---- | :---- | :---- | :---- | :---- | :---- |
| 1 | POST | /payments | Thanh toán một lần cho phiên checkout | payment:create:own | B05 |
| 2 | GET | /payments/by-checkout/{checkoutSessionId} | Trạng thái thanh toán của phiên | order:read:own | B05, B07 |
| 3 | GET | /payments/admin/issues | Việc cần xử lý về tiền (Must) | operations:read:any | E08, E02 |
| 4 | GET | /payments/admin/payments/by-checkout/{checkoutSessionId} | Payment chung và các Refund | operations:read:any | E08 |
| 5 | GET | /payments/admin/refunds | Danh sách khoản hoàn | operations:read:any | E08 |
| 6 | GET | /payments/admin/summary | Số liệu thu / hoàn | operations:read:any | E02 |

## Quy tắc bổ sung và source hiệu lực

Quy tắc chung G-09/G-11 ở [engineering](../engineering.md); G-10/G-13/G-15 ở [quy ước chung](README.md).

| Mã | Điều khoản | Nguồn / phân loại |
|---|---|---|
| <a id="req-pay-001"></a>REQ-PAY-001 | Payment/Refund thuộc Payment; simulator là implementation PaymentGateway(charge/refund/query) nội bộ, không callback public/provider ngoài. Giai đoạn 1 SUCCESS; giai đoạn 2 FAIL/TIMEOUT/LATE_SUCCESS/REFUND_FAIL. Vẫn xây đường PENDING, đối soát và phục hồi từ giai đoạn 1. | Yêu cầu baseline; [§8.4](../../business/database.md), L1042–1055 |
| <a id="req-pay-002"></a>REQ-PAY-002 | Lưu ý định trước khi gọi simulator ngoài transaction; lưu kết quả+Outbox sau gọi. Timeout không kết luận FAILED, query/retry cùng nghĩa vụ/key, không tạo Payment thứ hai theo checkout. | Yêu cầu baseline; [§5.1–5.2](software-architecture.md), L444–520 |
| <a id="req-pay-003"></a>REQ-PAY-003 | Refund ORDER theo (Payment,Order), tối đa một nghĩa vụ/đơn; FULL_CHECKOUT theo Payment/scope khi CLOSED nhận success muộn, loại trừ ORDER. Khóa và kiểm tra tổng hoàn không vượt thu; FAILED retry cùng bản ghi/key. | Yêu cầu baseline; [§3.4.2, G-09](../../business/database.md), L369–391 |
| <a id="req-pay-004"></a>REQ-PAY-004 | Payment chỉ PENDING→SUCCESS/FAILED; refundState của Payment tính từ Refund SUCCESS, không thêm partially_refunded/refunded làm Payment.status. Refund PENDING→SUCCESS/FAILED, retry FAILED→PENDING an toàn. | Yêu cầu baseline; [§8.2F/G](../../business/database.md), L999–1009 |
| <a id="req-pay-005"></a>REQ-PAY-005 | Hết hạn giữ hàng không đủ để nhả khi charge có thể đang chạy. Đóng phiên và payment.succeeded phải tranh cùng khóa nguồn Order; success sau CLOSED tạo FULL_CHECKOUT, không mở lại, không SOLD/Settlement. Cách close-expired/worker là DELTA-04/05 cần review. | Yêu cầu baseline; [§5.2, A.1, DELTA-04](software-architecture.md), L499–520; [A.1 — phương án cơ chế](software-architecture.md), L803–832; [§7.2 / DELTA-04](software-architecture.md), L727–737 |
| <a id="req-pay-006"></a>REQ-PAY-006 | P-RF-01 áp dụng cho ORDER và FULL_CHECKOUT: mỗi chu kỳ có một lần thực hiện đầu và tối đa 3 lần thử lại, chờ lần lượt 1, 5, 15 phút từ lần thất bại trước. Chỉ FAILED rõ ràng mới gửi lại; PENDING/timeout tiếp tục query cùng nghĩa vụ/key, query không tính lượt. Hết 3 lần thử lại vẫn thất bại thì dừng tự động, thông báo Customer chủ Payment; chỉ khách này được mở chu kỳ mới cho cùng Refund, amount và idempotency_key. Reset bộ đếm chu kỳ, giữ toàn bộ lịch sử; Admin không query/thử lại hoặc mở chu kỳ thay khách. Payment chỉ ghi nhận đã hoàn khi cổng xác nhận SUCCESS; Finance giảm tiền chờ theo kết quả này trong transaction riêng. Khoảng 21 phút chưa gồm xử lý/độ trễ J5, không là timeout để kết luận FAILED. | [P-RF-01](parameters.md#thử-lại-hoàn-tiền--p-rf-01) |

## Acceptance criteria để triển khai/kiểm chứng

Các AC sau là diễn giải kiểm chứng từ điều khoản cùng mã UC/REQ/G/WD; chưa là kết quả test. Tham số TBD chỉ kiểm tra biên sau khi giá trị được chốt. Mỗi dòng gộp một tình huống; triển khai test phải tách assertions và cả nhánh lỗi của UC.

| Mã AC | Liên kết yêu cầu | Given | When | Then |
|---|---|---|---|---|
| <a id="ac-pay-001"></a>AC-PAY-001 | UC-B05; G-09/11 | Một checkout và simulator SUCCESS/FAIL/TIMEOUT | Charge/retry sau mất response | ≤1 Payment logic; query cùng key; timeout giữ PENDING; FAIL rõ mới đóng N Order, nhả hold, không giảm Cart/tạo ví. |
| <a id="ac-pay-002"></a>AC-PAY-002 | UC-G02; REQ-PAY-003 | Hai Order trên cùng Payment, một bị hủy | Refund rồi callback/retry lặp | Hoàn đúng Order, không vượt thu, không trừ available/withdrawn; pending giảm chỉ khi SUCCESS tại Finance; đơn khác không đổi. |
| <a id="ac-pay-003"></a>AC-PAY-003 | UC-G02; AT-11 | Checkout CLOSED trước SUCCESS muộn | Nhận kết quả/replay | Một Refund FULL_CHECKOUT bằng tiền đã thu, không kèm ORDER refund, không SOLD/Settlement/mở lại đơn. |
| <a id="ac-pay-004"></a>AC-PAY-004 | UC-E08/G02; REQ-PAY-006 | Refund lỗi/chưa rõ; Admin và Customer chủ Payment | Admin xem tiến độ; thử query/thử lại/mở chu kỳ thay khách hoặc nhập tay đã hoàn/sửa ví | Admin chỉ đọc; không có quyền hay API Admin thực hiện các thao tác này. Không hoàn trùng, không sửa tay số dư/state thành công để bỏ qua simulator. |
| <a id="ac-pay-005"></a>AC-PAY-005 | UC-G02; REQ-PAY-006 | Refund ORDER hoặc FULL_CHECKOUT; lần đầu FAILED rõ ràng | J5 thực hiện các lần thử lại; xen kẽ timeout/PENDING và query | Tối đa 3 lần thử lại, không sớm hơn 1/5/15 phút từ thất bại trước; query không tăng bộ đếm, không phát lại khi kết quả còn chưa rõ; cùng Refund/amount/key, pending chỉ giảm khi SUCCESS. |
| <a id="ac-pay-006"></a>AC-PAY-006 | UC-G02; REQ-PAY-006 | Cả 3 lần thử lại đều FAILED; có Customer chủ Payment và tài khoản khác | Hết lượt; khách bấm Thử lại hoàn tiền hoặc gửi lặp/hai tab | Dừng tự động và thông báo đúng khách đã thanh toán; chỉ chủ Payment được mở chu kỳ mới, kể cả theo allowlist nghĩa vụ khi bị cấm. Reset bộ đếm chu kỳ, giữ lịch sử/Refund/amount/key; thao tác lặp không tạo chu kỳ đồng thời hay nghĩa vụ hoàn mới; tài khoản khác/Admin không được mở thay. |

### AT nguồn thuộc hồ sơ này

Các AT bao phủ nhiều service; hồ sơ này là đầu mối đọc, không có nghĩa chỉ sửa một module. Đọc owner khác từ coverage khi task chạm biên. **Chưa có bằng chứng chạy AT.**

| Mã | Tình huống | Bằng chứng cần có | Nguồn |
|---|---|---|---|
| <a id="at-07"></a>AT-07 | Payment nhận nhưng response mất | Query/retry cùng key; không charge lần hai, không tự tạo checkout mới | [B.2](software-architecture.md), L988 |
| <a id="at-09"></a>AT-09 | FAILED xác định | N Order PAYMENT_FAILED, nhả toàn bộ hold; Cart không đổi; không ví | [B.2](software-architecture.md), L990 |
| <a id="at-10"></a>AT-10 | Timeout/PENDING và J1 chạy đồng thời dispatch | Không nhả chỉ vì đọc NONE cũ; không thu mới; phục hồi qua J2/J2b | [B.2](software-architecture.md), L991 |
| <a id="at-11"></a>AT-11 | CLOSED rồi LATE_SUCCESS | Một FULL_CHECKOUT; không mở lại, không SOLD/Settlement; hoàn được theo dõi tới cuối | [B.2](software-architecture.md), L992 |
| <a id="at-13"></a>AT-13 | Refund timeout/FAIL/retry lặp | Cùng nghĩa vụ/key; tổng hoàn không vượt thu; pending chỉ giảm khi SUCCESS | [B.2](software-architecture.md), L994 |

## Chênh lệch, đề xuất và câu hỏi còn mở

- **DELTA-03/04:** không tự thêm public charge/callback hoặc close-expired trước khi contract được review. Kiểu service token JWT cụ thể trong phụ lục SAD vẫn đề xuất.
- FRD G02 mô tả Refund/Settlement cùng transaction; baseline ownership/local transaction tách Payment và Finance. Đã hiệu chỉnh clause liên quan dưới đây, không giả định 2PC.
- P-02/P-03/P-04 và P-SIM-01 chưa chốt. P-RF-01 đã chốt tại [parameters.md](parameters.md#thử-lại-hoàn-tiền--p-rf-01); chu kỳ chạy J5 khác với lịch chờ 1/5/15 phút. Giai đoạn 1 SUCCESS không cho phép bỏ đường phục hồi. Context-load test không chứng minh simulator.

## Đặc tả UC và ngoại lệ


<a id="uc-b05"></a>
## UC-B05 — Thanh toán

Nguồn: [§6 / UC-B05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L633–670. Vai trò: Khách hàng, Hệ thống · Ưu tiên: M.

Các điều khoản giữ mã ổn định khi di chuyển tài liệu. Các nhánh Should không thuộc DoD Must; riêng in-app Notification đã chuyển thành Must theo [quyết định 25/11/2026](coverage.md#notification-channels), email nghiệp vụ tùy chọn triển khai sau.

| Mã điều khoản | Loại | Yêu cầu / điều kiện / ngoại lệ | Nguồn hiệu lực |
|---|---|---|---|
| <a id="uc-b05.goal-01"></a>UC-B05.GOAL-01 | Mục tiêu | Thanh toán một lần cho tất cả các đơn được tách từ cùng một lần checkout. | [UC-B05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L637 |
| <a id="uc-b05.pre-01"></a>UC-B05.PRE-01 | Điều kiện | Customer được phép thanh toán; kiểm tra dữ liệu hiện hành, địa chỉ, quote và idempotency tại pay; O1 mới tạo checkout/giữ tồn. Nếu tiếp tục workflow đã nhận thì dùng tham chiếu và ý định cũ, không thu mới. | [UC-B05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L639; **baseline thay thế/hiệu chỉnh:** [§5.1–5.2](software-architecture.md), L444–520 |
| <a id="uc-b05.main-01"></a>UC-B05.MAIN-01 | Luồng chính | Khách xem Preview và xác nhận Thanh toán qua Order; chi tiết pay/quote/internal charge thuộc DELTA-01/03 cần đồng bộ contract trước code. Không xem endpoint đề xuất là API đã tồn tại. | [UC-B05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L643; **baseline thay thế/hiệu chỉnh:** [§5.1, §7.2](software-architecture.md), L444–496 |
| <a id="uc-b05.main-02"></a>UC-B05.MAIN-02 | Luồng chính | Hệ thống hiển thị danh sách các đơn, tổng tiền cần thanh toán cùng nhãn “Giả lập — không phát sinh tiền thật”. | [UC-B05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L645 |
| <a id="uc-b05.main-03"></a>UC-B05.MAIN-03 | Luồng chính | Khách xác nhận; hệ thống gửi đúng một yêu cầu thanh toán chung tới bộ giả lập thanh toán nội bộ (Database RBAC v5.0 mục 8.4). | [UC-B05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L647 |
| <a id="uc-b05.main-04"></a>UC-B05.MAIN-04 | Luồng chính | Khi có xác nhận thành công, hệ thống ghi nhận phiên đã thanh toán; tất cả các Order chuyển sang “Chờ xử lý” và được giao cho Seller tương ứng. | [UC-B05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L649 |
| <a id="uc-b05.main-05"></a>UC-B05.MAIN-05 | Luồng chính | Từ đây mỗi đơn có tiến độ giao, hủy và hoàn tiền riêng. | [UC-B05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L651 |
| <a id="uc-b05.alt-01"></a>UC-B05.ALT-01 | Ngoại lệ | Customer BANNED không thanh toán mới, kể cả AWAITING_PAYMENT và bất kể lịch sử đã trả tiền. Chỉ xem payment/refund, hủy/xác nhận đúng chủ khi state và allowlist nghĩa vụ cho phép; không giỏ/checkout/review/toàn bộ lịch sử terminal. J1/J2/J2b và xử lý Payment/Refund vẫn chạy; SUCCESS chưa apply hoặc Refund PENDING/FAILED chưa giải quyết vẫn là nghĩa vụ. | [FRD UC-B05/E03](FRD.md); [Thẻ 15](coverage.md#the-15) |
| <a id="uc-b05.alt-02"></a>UC-B05.ALT-02 | Ngoại lệ | Thanh toán bị từ chối rõ ràng (giai đoạn 2, kịch bản FAIL): tất cả các đơn của phiên đóng ở “Thanh toán thất bại”, giải phóng hàng đang giữ, giữ giỏ và hướng dẫn đặt hàng lại. | [UC-B05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L657 |
| <a id="uc-b05.alt-03"></a>UC-B05.ALT-03 | Ngoại lệ | Chưa nhận kết quả/timeout P-02 (giai đoạn 2, kịch bản TIMEOUT/LATE_SUCCESS): hiển thị “Đang xác nhận thanh toán”, không cho thanh toán lại hoặc hủy phiên đến khi đối soát. | [UC-B05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L659 |
| <a id="uc-b05.alt-04"></a>UC-B05.ALT-04 | Ngoại lệ | Khách gửi lại hoặc mở hai tab: không tạo lần thu thứ hai; yêu cầu khác nội dung trả xung đột. | [UC-B05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L661 |
| <a id="uc-b05.alt-05"></a>UC-B05.ALT-05 | Ngoại lệ | Có hàng/Seller không còn hợp lệ ngay trước khi thu: dừng thanh toán và yêu cầu đặt hàng lại. | [UC-B05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L663 |
| <a id="uc-b05.alt-06"></a>UC-B05.ALT-06 | Ngoại lệ | Thanh toán thành công muộn sau khi phiên hết hạn/hủy: hoàn toàn bộ số tiền, không chuyển đơn nào sang xử lý. | [UC-B05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L665 |
| <a id="uc-b05.post-01"></a>UC-B05.POST-01 | Hậu điều kiện | Mỗi CheckoutSession tối đa một Payment; kết quả thanh toán áp dụng đồng thời cho N đơn; hoàn tiền về sau tính theo từng đơn. | [UC-B05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L667 |
| <a id="uc-b05.note-01"></a>UC-B05.NOTE-01 | Ràng buộc | Bộ giả lập thanh toán nội bộ chỉ mô phỏng, không phát sinh tiền thật; kết quả chưa rõ phải tiếp tục đối soát chứ không coi là thất bại. Giai đoạn 1 bộ giả lập luôn trả thành công nhưng luồng vẫn đi qua trạng thái chờ/thành công/thất bại như thiết kế đầy đủ; giai đoạn 2 bật thêm các kịch bản lỗi để kiểm thử các nhánh ngoại lệ. | [UC-B05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L669 |

<a id="uc-e08"></a>
## UC-E08 — Xem đơn và giám sát khoản tiền gặp sự cố

Nguồn: [§6 / UC-E08](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1231–1260; quyền hoàn tiền hiện hành theo [P-RF-01](parameters.md#thử-lại-hoàn-tiền--p-rf-01). Vai trò: Quản trị viên · Ưu tiên: S (mở rộng) / M (giám sát hoàn tiền lỗi).

Các điều khoản giữ mã ổn định khi di chuyển tài liệu. Các nhánh Should không thuộc DoD Must; riêng in-app Notification đã chuyển thành Must theo [quyết định 25/11/2026](coverage.md#notification-channels), email nghiệp vụ tùy chọn triển khai sau.

| Mã điều khoản | Loại | Yêu cầu / điều kiện / ngoại lệ | Nguồn hiệu lực |
|---|---|---|---|
| <a id="uc-e08.goal-01"></a>UC-E08.GOAL-01 | Mục tiêu | Quản trị viên tra cứu và giám sát hoàn tiền bất thường theo từng đơn hoặc cả checkout; tra cứu ví và lịch sử rút tiền của Seller, không thao tác hoàn tiền thay khách. | [UC-E08](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1235; [P-RF-01](parameters.md#thử-lại-hoàn-tiền--p-rf-01) |
| <a id="uc-e08.pre-01"></a>UC-E08.PRE-01 | Điều kiện | Có quyền Admin; phát sinh khoản hoàn lỗi/chờ lâu hoặc cần đối chiếu ví. | [UC-E08](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1237 |
| <a id="uc-e08.main-01"></a>UC-E08.MAIN-01 | Luồng chính | Quản trị viên mở danh sách việc cần xử lý và lọc theo đơn, phiên thanh toán, mã giao dịch rút hoặc cửa hàng. | [UC-E08](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1241 |
| <a id="uc-e08.main-02"></a>UC-E08.MAIN-02 | Luồng chính | Hệ thống hiển thị Payment chung của lần checkout và các khoản hoàn/hoa hồng của từng đơn; với Seller: số dư ví, các bút toán và giao dịch rút (chỉ xem). | [UC-E08](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1243 |
| <a id="uc-e08.main-03"></a>UC-E08.MAIN-03 | Luồng chính | Quản trị viên xem kết quả và lịch sử đối soát/thử lại do Payment ghi nhận; không gọi query, gửi lại lệnh hoàn hay mở chu kỳ thay khách. Payment/J5 đối soát kết quả chưa rõ; Customer chủ Payment mở chu kỳ mới sau khi hết lượt theo P-RF-01. | [P-RF-01](parameters.md#thử-lại-hoàn-tiền--p-rf-01) |
| <a id="uc-e08.main-04"></a>UC-E08.MAIN-04 | Luồng chính | Màn giám sát phản ánh kết quả Payment và Finance: Refund SUCCESS mới tạo hiệu ứng REFUND_DEBIT và Settlement REFUNDED theo các local transaction riêng. Giữ lịch sử mọi chu kỳ/lần thử và nghĩa vụ chưa hoàn tất; thao tác xem của Admin không làm đổi trạng thái hoàn tiền. | [UC-G02.MAIN-04](#uc-g02.main-04); [P-RF-01](parameters.md#thử-lại-hoàn-tiền--p-rf-01) |
| <a id="uc-e08.alt-01"></a>UC-E08.ALT-01 | Ngoại lệ | Một Payment có nhiều khoản hoàn của các đơn khác nhau: không cộng các khoản vượt tổng Payment; không hoàn trùng một đơn. | [UC-E08](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1251 |
| <a id="uc-e08.alt-02"></a>UC-E08.ALT-02 | Ngoại lệ | Seller BANNED còn available được tự rút theo allowlist, không đợi unban; Admin không rút thay. Pending chỉ được xem tiến độ, không rút và không đóng băng available chỉ vì ban. Giữ minWithdrawalVnd=10000; ngoại lệ duy nhất dưới ngưỡng là BANNED có 0 < availableBalanceVnd < 10000 và amountVnd bằng toàn bộ available (số nguyên dương). Wallet trả allowBelowMinimumFullWithdrawal=true, canWithdraw=true, withdrawDisabledReason=null khi thỏa; UI khóa amount toàn bộ available. Rút một phần dưới ngưỡng trả 422 WITHDRAWAL_BELOW_MINIMUM; ACTIVE không hưởng ngoại lệ. Kiểm tra lại quyền/số dư trong giao dịch debit+ledger+Withdrawal, giữ concurrency/idempotency. | [FRD UC-D05/E08](FRD.md); [Finance WD-03/09](finance.md); [API Wallet/withdrawals](api_specs.md); [Thẻ 15](coverage.md#the-15) |
| <a id="uc-e08.alt-03"></a>UC-E08.ALT-03 | Ngoại lệ | Không được tự nhập tay trạng thái đã hoàn, không sửa số dư ví, không tạo/hủy giao dịch rút hay bút toán thủ công. | [UC-E08](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1255 |
| <a id="uc-e08.alt-04"></a>UC-E08.ALT-04 | Ngoại lệ | Lệch giữa số dư ví và tổng bút toán: chỉ hiển thị cảnh báo và tra cứu; điều chỉnh sổ nằm ngoài phạm vi MVP. | [UC-E08](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1257 |
| <a id="uc-e08.post-01"></a>UC-E08.POST-01 | Hậu điều kiện | Phần Must cho phép giám sát Refund lỗi theo từng đơn hoặc cả checkout, bất kể các đơn khác cùng lần thanh toán đang ở trạng thái nào; Admin không thử lại thay khách. Rút tiền là giao dịch tức thời nên không có lệnh rút lỗi cần xử lý. | [UC-E08](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1259; [P-RF-01](parameters.md#thử-lại-hoàn-tiền--p-rf-01) |

<a id="uc-g02"></a>
## UC-G02 — Hoàn tiền

Nguồn: [§6 / UC-G02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1317–1352; [P-RF-01](parameters.md#thử-lại-hoàn-tiền--p-rf-01). Vai trò: Hệ thống; Customer chủ Payment mở chu kỳ mới sau khi hết lượt · Ưu tiên: M.

Các điều khoản giữ mã ổn định khi di chuyển tài liệu. Các nhánh Should không thuộc DoD Must; riêng in-app Notification đã chuyển thành Must theo [quyết định 25/11/2026](coverage.md#notification-channels), email nghiệp vụ tùy chọn triển khai sau.

| Mã điều khoản | Loại | Yêu cầu / điều kiện / ngoại lệ | Nguồn hiệu lực |
|---|---|---|---|
| <a id="uc-g02.goal-01"></a>UC-G02.GOAL-01 | Mục tiêu | Hoàn tiền giả lập theo từng đơn bị hủy, hoặc hoàn toàn bộ khi lần thanh toán chung không còn hợp lệ. | [UC-G02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1321 |
| <a id="uc-g02.pre-01"></a>UC-G02.PRE-01 | Điều kiện | Payment chung đã success; có Order bị hủy hợp lệ (chưa đã_giao), hoặc phiên checkout đã đóng và phát sinh khoản thu muộn. | [UC-G02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1323 |
| <a id="uc-g02.main-01"></a>UC-G02.MAIN-01 | Luồng chính | Hệ thống xác định đơn bị hủy và tổng tiền của đơn đó. | [UC-G02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1327 |
| <a id="uc-g02.main-02"></a>UC-G02.MAIN-02 | Luồng chính | Hệ thống chuyển Settlement sang chờ_hoàn (chưa đổi số dư ví; phần Sᵢ, Cᵢ của đơn bị khóa trong số dư chờ, không thể chuyển sang khả dụng) và ghi nhận khoản tiền phải hoàn của đơn; chỉ hoàn kho số lượng thuộc đơn, không ảnh hưởng đơn khác. | [UC-G02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1329 |
| <a id="uc-g02.main-03"></a>UC-G02.MAIN-03 | Luồng chính | Hệ thống gửi yêu cầu hoàn tới bộ giả lập cho ORDER hoặc FULL_CHECKOUT, giữ nguyên Refund, số tiền và mã chống trùng khi đối soát hoặc thử lại. Mỗi chu kỳ gồm lần đầu và tối đa 3 lần thử lại sau 1, 5, 15 phút tính từ thất bại trước; chỉ gửi lại khi FAILED rõ ràng. | [UC-G02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1331; [P-RF-01](parameters.md#thử-lại-hoàn-tiền--p-rf-01) |
| <a id="uc-g02.main-04"></a>UC-G02.MAIN-04 | Luồng chính | Payment ghi Refund SUCCESS và Outbox trong local transaction; Finance nhận kết quả tin cậy, cập nhật Settlement REFUNDED, pending Seller −Sᵢ/PLATFORM −Cᵢ và REFUND_DEBIT trong transaction Finance. Order/Notification cập nhật riêng, có dedupe; không có transaction chung Refund–Settlement–Order. | [UC-G02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1333; **baseline thay thế/hiệu chỉnh:** [§5.3, §6.6](software-architecture.md), L522–539; [§6.6](software-architecture.md), L665–685 |
| <a id="uc-g02.alt-01"></a>UC-G02.ALT-01 | Ngoại lệ | Seller bắt buộc được phép từ chối giao hàng với lý do; khi từ chối hoặc không xác nhận giao/từ chối trong 7 ngày, hệ thống hủy đơn và hoàn tiền đúng đơn đó. | [UC-G02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1337 |
| <a id="uc-g02.alt-02"></a>UC-G02.ALT-02 | Ngoại lệ | Chỉ Order hủy hợp lệ (khách hủy, Seller từ chối, J4 hoặc invalid giao dịch do Order chứng minh khi áp dụng Payment SUCCESS qua payment.succeeded/đối soát chung handler) tạo hoàn riêng đúng Order. Seller ban sau capture không tự hủy/hoàn; không suy invalid từ Seller hiện tại khi event trễ. Order xác minh Identity/Product ngoài transaction giữ khóa; lỗi/thiếu bằng chứng giữ retry bền vững, không coi invalid. Payment giữ SUCCESS; đủ checkout.paid/order.cancelled; Finance giữ pending/PENDING_REFUND đến Refund SUCCESS. Replay không nhân đôi kho/Refund/ledger; late payment của checkout CLOSED vẫn FULL_CHECKOUT theo nhánh riêng. | [FRD UC-G02/B06](FRD.md); [Database §8.2B](database.md); [SAD §5.1](software-architecture.md); [Thẻ 15](coverage.md#the-15) |
| <a id="uc-g02.alt-03"></a>UC-G02.ALT-03 | Ngoại lệ | Thanh toán được xác nhận thành công muộn khi phiên đã hết hạn/hủy: hoàn toàn bộ Payment; không đồng thời tạo hoàn theo đơn; không có bút toán ví. | [UC-G02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1341 |
| <a id="uc-g02.alt-04"></a>UC-G02.ALT-04 | Ngoại lệ | PENDING/timeout: tiếp tục query, không tính lượt thử lại, không kết luận FAILED theo thời gian đã trôi qua. FAILED rõ ràng mới gửi lại theo P-RF-01; hết 3 lần thử lại thì dừng tự động và thông báo Customer chủ Payment. Khách bấm Thử lại hoàn tiền để mở chu kỳ mới cho cùng Refund/amount/key, đặt lại bộ đếm nhưng giữ mọi lịch sử. Admin không thao tác thay khách; số dư chờ giữ nguyên tới SUCCESS. | [P-RF-01](parameters.md#thử-lại-hoàn-tiền--p-rf-01) |
| <a id="uc-g02.alt-05"></a>UC-G02.ALT-05 | Ngoại lệ | Nhiều callback/nhấn hủy trùng: mỗi đơn chỉ được hoàn tối đa một lần; tổng các khoản hoàn không vượt tiền đã thu. | [UC-G02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1345 |
| <a id="uc-g02.alt-06"></a>UC-G02.ALT-06 | Ngoại lệ | Không bao giờ trừ số dư khả dụng hoặc số tiền đã rút của Seller để hoàn tiền (WD-06). | [UC-G02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1347 |
| <a id="uc-g02.post-01"></a>UC-G02.POST-01 | Hậu điều kiện | Một Payment chung có thể có nhiều khoản Refund khác nhau theo đơn, hoặc đúng một Refund toàn phần trong trường hợp hoàn cả lần checkout; ví Seller không bao giờ âm vì hoàn tiền. | [UC-G02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1349 |
| <a id="uc-g02.note-01"></a>UC-G02.NOTE-01 | Ràng buộc | Hoàn tiền chỉ được coi là thành công khi bộ giả lập trả kết quả thành công cho Refund (giai đoạn 1 luôn thành công ngay). Cập nhật Refund, Settlement và bút toán ví của đơn tách khỏi việc gọi bộ giả lập để không gây lặp khi kết nối gián đoạn. | [UC-G02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1351 |
