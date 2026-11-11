# Thanh toán giả lập và hoàn tiền

Baseline: SAD v1.1 §7.1/D.1, được người dùng chấp nhận ngày 11/11/2026.
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
- [§7.1–7.3](../../business/api.md): provider/consumer allowlist, event payload và jobs. [§7.2](../../business/Software-Architecture.md): DELTA cần review.
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
| 6 | POST | /payments/admin/refunds/{refundId}/query | Tra cứu lại kết quả hoàn tại bộ giả lập | refund:retry | E08, G02 |
| 7 | POST | /payments/admin/refunds/{refundId}/retry | Thử lại hoàn tiền an toàn | refund:retry | E08, G02 |
| 8 | POST | /payments/admin/checkout-sessions/{checkoutSessionId}/mark-handled | Đánh dấu đã xử lý trường hợp đối soát | refund:retry | E08 |
| 9 | GET | /payments/admin/summary | Số liệu thu / hoàn | operations:read:any | E02 |

## Quy tắc bổ sung và source hiệu lực

Quy tắc chung G-09/G-11 ở [engineering](../engineering.md); G-10/G-13/G-15 ở [quy ước chung](README.md).

| Mã | Điều khoản | Nguồn / phân loại |
|---|---|---|
| <a id="req-pay-001"></a>REQ-PAY-001 | Payment/Refund thuộc Payment; simulator là implementation PaymentGateway(charge/refund/query) nội bộ, không callback public/provider ngoài. Giai đoạn 1 SUCCESS; giai đoạn 2 FAIL/TIMEOUT/LATE_SUCCESS/REFUND_FAIL. Vẫn xây đường PENDING, đối soát và phục hồi từ giai đoạn 1. | Yêu cầu baseline; [§8.4](../../business/database.md), L1042–1055 |
| <a id="req-pay-002"></a>REQ-PAY-002 | Lưu ý định trước khi gọi simulator ngoài transaction; lưu kết quả+Outbox sau gọi. Timeout không kết luận FAILED, query/retry cùng nghĩa vụ/key, không tạo Payment thứ hai theo checkout. | Yêu cầu baseline; [§5.1–5.2](../../business/Software-Architecture.md), L444–520 |
| <a id="req-pay-003"></a>REQ-PAY-003 | Refund ORDER theo (Payment,Order), tối đa một nghĩa vụ/đơn; FULL_CHECKOUT theo Payment/scope khi CLOSED nhận success muộn, loại trừ ORDER. Khóa và kiểm tra tổng hoàn không vượt thu; FAILED retry cùng bản ghi/key. | Yêu cầu baseline; [§3.4.2, G-09](../../business/database.md), L369–391 |
| <a id="req-pay-004"></a>REQ-PAY-004 | Payment chỉ PENDING→SUCCESS/FAILED; refundState của Payment tính từ Refund SUCCESS, không thêm partially_refunded/refunded làm Payment.status. Refund PENDING→SUCCESS/FAILED, retry FAILED→PENDING an toàn. | Yêu cầu baseline; [§8.2F/G](../../business/database.md), L999–1009 |
| <a id="req-pay-005"></a>REQ-PAY-005 | Hết hạn giữ hàng không đủ để nhả khi charge có thể đang chạy. Đóng phiên và payment.succeeded phải tranh cùng khóa nguồn Order; success sau CLOSED tạo FULL_CHECKOUT, không mở lại, không SOLD/Settlement. Cách close-expired/worker là DELTA-04/05 cần review. | Yêu cầu baseline; [§5.2, A.1, DELTA-04](../../business/Software-Architecture.md), L499–520; [A.1 — phương án cơ chế](../../business/Software-Architecture.md), L803–832; [§7.2 / DELTA-04](../../business/Software-Architecture.md), L727–737 |

## Acceptance criteria để triển khai/kiểm chứng

Các AC sau là diễn giải kiểm chứng từ điều khoản cùng mã UC/REQ/G/WD; chưa là kết quả test. Tham số TBD chỉ kiểm tra biên sau khi giá trị được chốt. Mỗi dòng gộp một tình huống; triển khai test phải tách assertions và cả nhánh lỗi của UC.

| Mã AC | Liên kết yêu cầu | Given | When | Then |
|---|---|---|---|---|
| <a id="ac-pay-001"></a>AC-PAY-001 | UC-B05; G-09/11 | Một checkout và simulator SUCCESS/FAIL/TIMEOUT | Charge/retry sau mất response | ≤1 Payment logic; query cùng key; timeout giữ PENDING; FAIL rõ mới đóng N Order, nhả hold, không giảm Cart/tạo ví. |
| <a id="ac-pay-002"></a>AC-PAY-002 | UC-G02; REQ-PAY-003 | Hai Order trên cùng Payment, một bị hủy | Refund rồi callback/retry lặp | Hoàn đúng Order, không vượt thu, không trừ available/withdrawn; pending giảm chỉ khi SUCCESS tại Finance; đơn khác không đổi. |
| <a id="ac-pay-003"></a>AC-PAY-003 | UC-G02; AT-11 | Checkout CLOSED trước SUCCESS muộn | Nhận kết quả/replay | Một Refund FULL_CHECKOUT bằng tiền đã thu, không kèm ORDER refund, không SOLD/Settlement/mở lại đơn. |
| <a id="ac-pay-004"></a>AC-PAY-004 | UC-E08 | Refund lỗi/chưa rõ, Admin có refund:retry | Query trước retry cùng key; thử nhập tay đã hoàn/sửa ví | Giữ nghĩa vụ và audit; không hoàn trùng; không có API sửa tay số dư hoặc state thành công để bỏ qua simulator. |

### AT nguồn thuộc hồ sơ này

Các AT bao phủ nhiều service; hồ sơ này là đầu mối đọc, không có nghĩa chỉ sửa một module. Đọc owner khác từ coverage khi task chạm biên. **Chưa có bằng chứng chạy AT.**

| Mã | Tình huống | Bằng chứng cần có | Nguồn |
|---|---|---|---|
| <a id="at-07"></a>AT-07 | Payment nhận nhưng response mất | Query/retry cùng key; không charge lần hai, không tự tạo checkout mới | [B.2](../../business/Software-Architecture.md), L988 |
| <a id="at-09"></a>AT-09 | FAILED xác định | N Order PAYMENT_FAILED, nhả toàn bộ hold; Cart không đổi; không ví | [B.2](../../business/Software-Architecture.md), L990 |
| <a id="at-10"></a>AT-10 | Timeout/PENDING và J1 chạy đồng thời dispatch | Không nhả chỉ vì đọc NONE cũ; không thu mới; phục hồi qua J2/J2b | [B.2](../../business/Software-Architecture.md), L991 |
| <a id="at-11"></a>AT-11 | CLOSED rồi LATE_SUCCESS | Một FULL_CHECKOUT; không mở lại, không SOLD/Settlement; hoàn được theo dõi tới cuối | [B.2](../../business/Software-Architecture.md), L992 |
| <a id="at-13"></a>AT-13 | Refund timeout/FAIL/retry lặp | Cùng nghĩa vụ/key; tổng hoàn không vượt thu; pending chỉ giảm khi SUCCESS | [B.2](../../business/Software-Architecture.md), L994 |

## Chênh lệch, đề xuất và câu hỏi còn mở

- **DELTA-03/04:** không tự thêm public charge/callback hoặc close-expired trước khi contract được review. Kiểu service token JWT cụ thể trong phụ lục SAD vẫn đề xuất.
- FRD G02 mô tả Refund/Settlement cùng transaction; baseline ownership/local transaction tách Payment và Finance. Đã hiệu chỉnh clause liên quan dưới đây, không giả định 2PC.
- P-02/P-03/P-04, retry Refund, P-SIM-01 chưa chốt. Giai đoạn 1 SUCCESS không cho phép bỏ đường phục hồi. Context-load test không chứng minh simulator.

## Đặc tả UC và ngoại lệ


<a id="uc-b05"></a>
## UC-B05 — Thanh toán

Nguồn: [§6 / UC-B05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L633–670. Vai trò: Khách hàng, Hệ thống · Ưu tiên: M.

Các điều khoản dưới đây là yêu cầu nguồn đã đối chiếu baseline; nhánh Google/in-app ghi Should không thuộc DoD Must. Mã clause giữ ổn định khi di chuyển tài liệu.

| Mã điều khoản | Loại | Yêu cầu / điều kiện / ngoại lệ | Nguồn hiệu lực |
|---|---|---|---|
| <a id="uc-b05.goal-01"></a>UC-B05.GOAL-01 | Mục tiêu | Thanh toán một lần cho tất cả các đơn được tách từ cùng một lần checkout. | [UC-B05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L637 |
| <a id="uc-b05.pre-01"></a>UC-B05.PRE-01 | Điều kiện | Customer được phép thanh toán; kiểm tra dữ liệu hiện hành, địa chỉ, quote và idempotency tại pay; O1 mới tạo checkout/giữ tồn. Nếu tiếp tục workflow đã nhận thì dùng tham chiếu và ý định cũ, không thu mới. | [UC-B05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L639; **baseline thay thế/hiệu chỉnh:** [§5.1–5.2](../../business/Software-Architecture.md), L444–520 |
| <a id="uc-b05.main-01"></a>UC-B05.MAIN-01 | Luồng chính | Khách xem Preview và xác nhận Thanh toán qua Order; chi tiết pay/quote/internal charge thuộc DELTA-01/03 cần đồng bộ contract trước code. Không xem endpoint đề xuất là API đã tồn tại. | [UC-B05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L643; **baseline thay thế/hiệu chỉnh:** [§5.1, §7.2](../../business/Software-Architecture.md), L444–496 |
| <a id="uc-b05.main-02"></a>UC-B05.MAIN-02 | Luồng chính | Hệ thống hiển thị danh sách các đơn, tổng tiền cần thanh toán cùng nhãn “Giả lập — không phát sinh tiền thật”. | [UC-B05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L645 |
| <a id="uc-b05.main-03"></a>UC-B05.MAIN-03 | Luồng chính | Khách xác nhận; hệ thống gửi đúng một yêu cầu thanh toán chung tới bộ giả lập thanh toán nội bộ (Database RBAC v5.0 mục 8.4). | [UC-B05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L647 |
| <a id="uc-b05.main-04"></a>UC-B05.MAIN-04 | Luồng chính | Khi có xác nhận thành công, hệ thống ghi nhận phiên đã thanh toán; tất cả các Order chuyển sang “Chờ xử lý” và được giao cho Seller tương ứng. | [UC-B05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L649 |
| <a id="uc-b05.main-05"></a>UC-B05.MAIN-05 | Luồng chính | Từ đây mỗi đơn có tiến độ giao, hủy và hoàn tiền riêng. | [UC-B05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L651 |
| <a id="uc-b05.alt-01"></a>UC-B05.ALT-01 | Ngoại lệ | Khách bị cấm sau khi đã có đơn thanh toán thành công trước thời điểm cấm: nút thanh toán bị vô hiệu; API cũng từ chối thanh toán mới, không tự khóa các chức năng xem/nhận hàng. | [UC-B05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L655 |
| <a id="uc-b05.alt-02"></a>UC-B05.ALT-02 | Ngoại lệ | Thanh toán bị từ chối rõ ràng (giai đoạn 2, kịch bản FAIL): tất cả các đơn của phiên đóng ở “Thanh toán thất bại”, giải phóng hàng đang giữ, giữ giỏ và hướng dẫn đặt hàng lại. | [UC-B05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L657 |
| <a id="uc-b05.alt-03"></a>UC-B05.ALT-03 | Ngoại lệ | Chưa nhận kết quả/timeout P-02 (giai đoạn 2, kịch bản TIMEOUT/LATE_SUCCESS): hiển thị “Đang xác nhận thanh toán”, không cho thanh toán lại hoặc hủy phiên đến khi đối soát. | [UC-B05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L659 |
| <a id="uc-b05.alt-04"></a>UC-B05.ALT-04 | Ngoại lệ | Khách gửi lại hoặc mở hai tab: không tạo lần thu thứ hai; yêu cầu khác nội dung trả xung đột. | [UC-B05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L661 |
| <a id="uc-b05.alt-05"></a>UC-B05.ALT-05 | Ngoại lệ | Có hàng/Seller không còn hợp lệ ngay trước khi thu: dừng thanh toán và yêu cầu đặt hàng lại. | [UC-B05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L663 |
| <a id="uc-b05.alt-06"></a>UC-B05.ALT-06 | Ngoại lệ | Thanh toán thành công muộn sau khi phiên hết hạn/hủy: hoàn toàn bộ số tiền, không chuyển đơn nào sang xử lý. | [UC-B05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L665 |
| <a id="uc-b05.post-01"></a>UC-B05.POST-01 | Hậu điều kiện | Mỗi CheckoutSession tối đa một Payment; kết quả thanh toán áp dụng đồng thời cho N đơn; hoàn tiền về sau tính theo từng đơn. | [UC-B05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L667 |
| <a id="uc-b05.note-01"></a>UC-B05.NOTE-01 | Ràng buộc | Bộ giả lập thanh toán nội bộ chỉ mô phỏng, không phát sinh tiền thật; kết quả chưa rõ phải tiếp tục đối soát chứ không coi là thất bại. Giai đoạn 1 bộ giả lập luôn trả thành công nhưng luồng vẫn đi qua trạng thái chờ/thành công/thất bại như thiết kế đầy đủ; giai đoạn 2 bật thêm các kịch bản lỗi để kiểm thử các nhánh ngoại lệ. | [UC-B05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L669 |

<a id="uc-e08"></a>
## UC-E08 — Xem đơn và xử lý khoản tiền gặp sự cố

Nguồn: [§6 / UC-E08](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1231–1260. Vai trò: Quản trị viên · Ưu tiên: S (mở rộng) / M (xử lý hoàn tiền lỗi).

Các điều khoản dưới đây là yêu cầu nguồn đã đối chiếu baseline; nhánh Google/in-app ghi Should không thuộc DoD Must. Mã clause giữ ổn định khi di chuyển tài liệu.

| Mã điều khoản | Loại | Yêu cầu / điều kiện / ngoại lệ | Nguồn hiệu lực |
|---|---|---|---|
| <a id="uc-e08.goal-01"></a>UC-E08.GOAL-01 | Mục tiêu | Quản trị viên tra cứu và xử lý hoàn tiền bất thường theo từng đơn; tra cứu ví và lịch sử rút tiền của Seller. | [UC-E08](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1235 |
| <a id="uc-e08.pre-01"></a>UC-E08.PRE-01 | Điều kiện | Có quyền Admin; phát sinh khoản hoàn lỗi/chờ lâu hoặc cần đối chiếu ví. | [UC-E08](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1237 |
| <a id="uc-e08.main-01"></a>UC-E08.MAIN-01 | Luồng chính | Quản trị viên mở danh sách việc cần xử lý và lọc theo đơn, phiên thanh toán, mã giao dịch rút hoặc cửa hàng. | [UC-E08](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1241 |
| <a id="uc-e08.main-02"></a>UC-E08.MAIN-02 | Luồng chính | Hệ thống hiển thị Payment chung của lần checkout và các khoản hoàn/hoa hồng của từng đơn; với Seller: số dư ví, các bút toán và giao dịch rút (chỉ xem). | [UC-E08](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1243 |
| <a id="uc-e08.main-03"></a>UC-E08.MAIN-03 | Luồng chính | Quản trị viên tra cứu lại bộ giả lập thanh toán nội bộ đối với khoản hoàn chưa rõ hoặc thất bại (giai đoạn 2: kịch bản REFUND_FAIL/TIMEOUT); chỉ thử lại khi xác nhận an toàn (cùng idempotency_key). | [UC-E08](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1245 |
| <a id="uc-e08.main-04"></a>UC-E08.MAIN-04 | Luồng chính | Hệ thống cập nhật trạng thái; nếu hoàn tiền thành công thì ghi bút toán REFUND_DEBIT và chuyển Settlement sang đã_hoàn; ghi nhận lịch sử (AdminAuditLog) và giữ các nghĩa vụ chưa hoàn tất. | [UC-E08](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1247 |
| <a id="uc-e08.alt-01"></a>UC-E08.ALT-01 | Ngoại lệ | Một Payment có nhiều khoản hoàn của các đơn khác nhau: không cộng các khoản vượt tổng Payment; không hoàn trùng một đơn. | [UC-E08](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1251 |
| <a id="uc-e08.alt-02"></a>UC-E08.ALT-02 | Ngoại lệ | Seller banned còn số dư: Admin không rút thay; khi gỡ cấm Seller tự rút tiếp. | [UC-E08](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1253 |
| <a id="uc-e08.alt-03"></a>UC-E08.ALT-03 | Ngoại lệ | Không được tự nhập tay trạng thái đã hoàn, không sửa số dư ví, không tạo/hủy giao dịch rút hay bút toán thủ công. | [UC-E08](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1255 |
| <a id="uc-e08.alt-04"></a>UC-E08.ALT-04 | Ngoại lệ | Lệch giữa số dư ví và tổng bút toán: chỉ đánh dấu và tra cứu; điều chỉnh sổ nằm ngoài phạm vi MVP. | [UC-E08](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1257 |
| <a id="uc-e08.post-01"></a>UC-E08.POST-01 | Hậu điều kiện | Phần Must cho phép xử lý chính xác Refund lỗi theo từng đơn, bất kể các đơn khác cùng lần thanh toán đang ở trạng thái nào. Rút tiền là giao dịch tức thời nên không có lệnh rút lỗi cần xử lý. | [UC-E08](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1259 |

<a id="uc-g02"></a>
## UC-G02 — Hoàn tiền

Nguồn: [§6 / UC-G02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1317–1352. Vai trò: Hệ thống (Quản trị viên hỗ trợ thử lại) · Ưu tiên: M.

Các điều khoản dưới đây là yêu cầu nguồn đã đối chiếu baseline; nhánh Google/in-app ghi Should không thuộc DoD Must. Mã clause giữ ổn định khi di chuyển tài liệu.

| Mã điều khoản | Loại | Yêu cầu / điều kiện / ngoại lệ | Nguồn hiệu lực |
|---|---|---|---|
| <a id="uc-g02.goal-01"></a>UC-G02.GOAL-01 | Mục tiêu | Hoàn tiền giả lập theo từng đơn bị hủy, hoặc hoàn toàn bộ khi lần thanh toán chung không còn hợp lệ. | [UC-G02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1321 |
| <a id="uc-g02.pre-01"></a>UC-G02.PRE-01 | Điều kiện | Payment chung đã success; có Order bị hủy hợp lệ (chưa đã_giao), hoặc phiên checkout đã đóng và phát sinh khoản thu muộn. | [UC-G02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1323 |
| <a id="uc-g02.main-01"></a>UC-G02.MAIN-01 | Luồng chính | Hệ thống xác định đơn bị hủy và tổng tiền của đơn đó. | [UC-G02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1327 |
| <a id="uc-g02.main-02"></a>UC-G02.MAIN-02 | Luồng chính | Hệ thống chuyển Settlement sang chờ_hoàn (chưa đổi số dư ví; phần Sᵢ, Cᵢ của đơn bị khóa trong số dư chờ, không thể chuyển sang khả dụng) và ghi nhận khoản tiền phải hoàn của đơn; chỉ hoàn kho số lượng thuộc đơn, không ảnh hưởng đơn khác. | [UC-G02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1329 |
| <a id="uc-g02.main-03"></a>UC-G02.MAIN-03 | Luồng chính | Hệ thống gửi yêu cầu hoàn tiền tới bộ giả lập thanh toán nội bộ theo số tiền của đơn trên Payment chung, giữ nguyên mã tham chiếu cho những lần đối soát hoặc thử lại. | [UC-G02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1331 |
| <a id="uc-g02.main-04"></a>UC-G02.MAIN-04 | Luồng chính | Payment ghi Refund SUCCESS và Outbox trong local transaction; Finance nhận kết quả tin cậy, cập nhật Settlement REFUNDED, pending Seller −Sᵢ/PLATFORM −Cᵢ và REFUND_DEBIT trong transaction Finance. Order/Notification cập nhật riêng, có dedupe; không có transaction chung Refund–Settlement–Order. | [UC-G02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1333; **baseline thay thế/hiệu chỉnh:** [§5.3, §6.6](../../business/Software-Architecture.md), L522–539; [§6.6](../../business/Software-Architecture.md), L665–685 |
| <a id="uc-g02.alt-01"></a>UC-G02.ALT-01 | Ngoại lệ | Seller bắt buộc được phép từ chối giao hàng với lý do; khi từ chối hoặc không xác nhận giao/từ chối trong 7 ngày, hệ thống hủy đơn và hoàn tiền đúng đơn đó. | [UC-G02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1337 |
| <a id="uc-g02.alt-02"></a>UC-G02.ALT-02 | Ngoại lệ | Khách hủy đơn hợp lệ hoặc Seller bị cấm trước khi giao: hoàn tiền chỉ đơn tương ứng. | [UC-G02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1339 |
| <a id="uc-g02.alt-03"></a>UC-G02.ALT-03 | Ngoại lệ | Thanh toán được xác nhận thành công muộn khi phiên đã hết hạn/hủy: hoàn toàn bộ Payment; không đồng thời tạo hoàn theo đơn; không có bút toán ví. | [UC-G02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1341 |
| <a id="uc-g02.alt-04"></a>UC-G02.ALT-04 | Ngoại lệ | Bộ giả lập chưa trả kết quả hoặc trả lỗi (giai đoạn 2): giữ trạng thái đang hoàn, số dư chờ chưa bị trừ, tra cứu trước khi phát lại; lỗi rõ thì retry an toàn, quá hạn chuyển Admin. | [UC-G02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1343 |
| <a id="uc-g02.alt-05"></a>UC-G02.ALT-05 | Ngoại lệ | Nhiều callback/nhấn hủy trùng: mỗi đơn chỉ được hoàn tối đa một lần; tổng các khoản hoàn không vượt tiền đã thu. | [UC-G02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1345 |
| <a id="uc-g02.alt-06"></a>UC-G02.ALT-06 | Ngoại lệ | Không bao giờ trừ số dư khả dụng hoặc số tiền đã rút của Seller để hoàn tiền (WD-06). | [UC-G02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1347 |
| <a id="uc-g02.post-01"></a>UC-G02.POST-01 | Hậu điều kiện | Một Payment chung có thể có nhiều khoản Refund khác nhau theo đơn, hoặc đúng một Refund toàn phần trong trường hợp hoàn cả lần checkout; ví Seller không bao giờ âm vì hoàn tiền. | [UC-G02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1349 |
| <a id="uc-g02.note-01"></a>UC-G02.NOTE-01 | Ràng buộc | Hoàn tiền chỉ được coi là thành công khi bộ giả lập trả kết quả thành công cho Refund (giai đoạn 1 luôn thành công ngay). Cập nhật Refund, Settlement và bút toán ví của đơn tách khỏi việc gọi bộ giả lập để không gây lặp khi kết nối gián đoạn. | [UC-G02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1351 |
