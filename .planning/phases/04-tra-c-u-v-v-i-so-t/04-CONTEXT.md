# Phase 4 — Tra cứu, ví và đối soát

**Ngày chốt:** 2026-11-24  
**Trạng thái:** Quyết định nghiên cứu đã được duyệt để lập kế hoạch

<domain>
## Phạm vi

- **ORD-09:** Customer, Seller và Admin tra cứu đơn theo quyền. Hiển thị trạng thái, hạn thao tác và thông tin hoàn phù hợp.
- **PAY-07:** Customer tra cứu thu và hoàn theo checkout, kể cả FULL_CHECKOUT có `orderId=null`. Admin xem bất thường và ghi chú khoản thu theo quyền.
- **FIN-04:** Finance cung cấp ví, Settlement, lịch sử bút toán và rút tiền giả lập. Withdrawal, số dư, ledger và sự kiện theo luồng giao dịch đã đặc tả.
- **FIN-05:** Admin đối soát thu, hoàn, số dư và khoản chờ từ Order, Payment và Finance. Báo cáo phục vụ vận hành.

Phạm vi triển khai gồm Order, Payment và Finance. Identity, Product, Notification, Cart và provider là các biên tích hợp.
</domain>

<decisions>
## Quyết định đã duyệt

### Danh sách bất thường cho Admin

- **D-01 — 1A.** Dựa vào tình trạng xử lý thực tế. `PAYMENT_PENDING` cảnh báo chậm tạo link khi Payment còn `PENDING`, checkout còn mở, chưa có link hợp lệ và đã qua ít nhất **2 phút từ lúc tạo Payment**. Checkout còn mở và có link hợp lệ chờ khách thanh toán là luồng bình thường. Chỉ thể hiện webhook chậm khi có bằng chứng phù hợp từ nguồn sở hữu.
- **D-02 — Ngưỡng cảnh báo Refund** `REFUND_STUCK` cảnh báo khi **attempt hiện tại vẫn `PENDING` sau ít nhất 30 phút kể từ lúc tạo attempt**. Hai ngưỡng chỉ phục vụ hiển thị. Hạn checkout, trạng thái giao dịch, lịch J2/J5 và số lượt thử theo quy tắc hiện hành.
- **D-03 — Trạng thái hiển thị và quyền Admin** Giữ `issueType` của API §6.5. Refund `FAILED` còn lượt hiển thị **Đang chờ thử lại tự động**, kèm lịch lần tiếp theo nếu có. Refund `FAILED` hết lượt hiển thị **Chờ khách thử lại**. Nếu có cờ chờ đối soát vì kết quả provider mâu thuẫn, ưu tiên **Tạm dừng hoàn — chờ đối soát**, kể cả khi đã hết lượt. Admin chỉ xem Refund. `mark-handled` áp dụng cho ghi chú hoặc gỡ cờ đối soát khoản thu, có audit theo hợp đồng.

### Rút tiền Seller

- **D-04 — 2A.** Trong giao dịch rút, Finance đối chiếu số dư ví với tổng delta bút toán của chính ví. Ví lệch bút toán trả `409 WALLET_INCONSISTENT`. Chênh lệch tạm trong báo cáo liên service do sự kiện đang xử lý không tự chặn rút khi ví và bút toán Finance khớp và yêu cầu đáp ứng quyền cùng số dư. Giữ WD-03–WD-05 và WD-09, kể cả ngưỡng 10.000đ, ngoại lệ rút toàn bộ số dư dương dưới ngưỡng cho Seller BANNED đủ quyền, và chống rút trùng.

### Đối soát dành cho Admin

- **D-05 — 3A.** Trả HTTP 200 với `COMPLETE` khi đủ dữ liệu và cân, `DIFFERENCE` khi đủ dữ liệu và quan sát thấy lệch, hoặc `INCOMPLETE` khi thiếu nguồn. Giữ `balanced` để tương thích. `INCOMPLETE` trả `balanced=false`, `differenceVnd=null` và chỉ rõ nguồn thiếu. Trả `checkedAt` và mốc đọc từng nguồn có dữ liệu. `DIFFERENCE` phản ánh lần kiểm tra hiện tại. Đối soát chỉ đọc và không phát sinh bút toán điều chỉnh.

### Quy tắc kế thừa và nội dung cần xác minh

- Giữ quyền hiện hành và ownership. Dùng Spring Data JPA và Flyway. Lời gọi HTTP nội bộ đồng bộ mới dùng Spring Cloud OpenFeign.
- CR-01 về tái tạo bản bàn giao và tích hợp với hệ thống ngoài thật được theo dõi riêng theo báo cáo Phase 3.

### Quyết định sau Research

- **D-06 — Ghi chú đối soát khoản thu** Giữ endpoint công khai `mark-handled` ở Payment. Order sở hữu cờ và ghi chú khoản thu, cập nhật cùng audit `MARK_HANDLED` trong một transaction. Payment gọi qua OpenFeign với service credential và Bearer Admin đã kiểm tra. Order kiểm tra lại quyền hiện hành `operations:annotate` và chỉ chấp nhận Payment gọi. Dùng quyền đọc riêng khi lấy cờ khoản thu. Identity hiện thiếu grant annotate và chưa có bằng chứng cấp đúng quyền ví cho Seller BANNED. Dùng fixture Identity đúng contract để kiểm thử. Khi nguồn thật thiếu quyền, giữ từ chối và ghi rõ phần tích hợp chưa nghiệm thu.
- **D-07 — DTO và độ ưu tiên Admin issues** Thêm `displayReason` và `nextRetryAt` nullable vào Admin issues. Hiển thị một dòng cho mỗi Refund hoặc khoản thu. Cờ chờ đối soát ưu tiên `MANUAL_REVIEW`, sau đó là trạng thái FAILED hoặc attempt quá hạn tương ứng. Xác định loại cuối trước khi lọc và phân trang. Chỉ báo webhook chậm với bằng chứng trực tiếp đã lưu. Frontend ánh xạ mã lý do sang nhãn đã duyệt.
- **D-08 — Đối soát qua ba nguồn** Dùng ba lần đọc cục bộ có mốc thời gian. Payment trả tổng thu, tổng hoàn thành công, tổng đang chờ hoàn và phần FULL_CHECKOUT chưa SUCCESS. Order trả số lượng và tổng tiền phiên đã áp dụng PAID, dùng để diễn giải tiến độ, không cộng vào phương trình. Finance đọc ví và ledger của mình. `differenceVnd` là tổng thu trừ các ngăn tiền đã xác định. Tổng sàn bằng nhau mà ví lệch WD-11 vẫn là `DIFFERENCE` và ghi chi tiết ví lệch. Khi thiếu nguồn chỉ trả tổng từng nguồn đọc được, không thay số thiếu bằng 0. Chấp nhận chênh lệch tạm giữa các lần đọc và hiển thị timestamp để Admin diễn giải. Không tạo snapshot phân tán hoặc tự điều chỉnh sổ.
</decisions>

<canonical_refs>
## Tài liệu cần đọc trước khi lập kế hoạch

### Phạm vi và thứ tự ưu tiên
- `.planning/ROADMAP.md` — Phase 4 và tiêu chí thành công.
- `.planning/REQUIREMENTS.md` — ORD-09, PAY-07, FIN-04, FIN-05.
- `.planning/PROJECT.md` — phạm vi ba service.
- `docs/requirements/software-architecture.md` — §5.5–5.6, §6.6, §7.1 và §D.1, ADR-05.
- `docs/requirements/coverage.md` — Thẻ 15 và phạm vi các hiệu chỉnh.
- `docs/decisions/0002-order-payment-finance-jpa.md` và `docs/engineering.md` — JPA, Flyway và REQ-ENG-004.

### Nghiệp vụ và hợp đồng
- `docs/requirements/FRD.md` — UC-B07, D04, D05, E02 và E08.
- `docs/requirements/api_specs.md` — §3.2, §3.7, §6.4–6.6 và §7.1. Đọc các endpoint Admin issues, mark-handled, withdrawal và reconciliation.
- `docs/requirements/database.md` — §3.4–3.5, §8.3 WD-01–WD-11 và §8.5.
- `docs/requirements/parameters.md` — P-01, P-02/P-03, P-WD-01 và P-RF-01.
- `docs/requirements/checkout-orders.md`, `docs/requirements/payments.md`, `docs/requirements/finance.md` và `docs/requirements/identity.md` — quy tắc và nghiệm thu theo vai trò.
- `docs/decisions/0001-provider-checkout.md` — provider demo, hết hạn và đối soát kết quả hoàn.
- `.planning/phases/02-thanh-to-n-h-t-h-n-v-ti-n-ch/02-CONTEXT.md` — lịch J2 và hành vi tra cứu checkout khi Payment lỗi.
- `.planning/phases/03-giao-h-ng-h-y-n-v-quy-t-to-n/03-CONTEXT.md` và `.planning/phases/03-giao-h-ng-h-y-n-v-quy-t-to-n/03-VERIFICATION.md` — hành vi đã nghiệm thu, giới hạn 4A và CR-01.
</canonical_refs>

<code_context>
## Hiện trạng từ code

- Order đã có chi tiết Customer/Seller trong `order-service/src/main/java/com/example/order_service/controller/OrderLifecycleController.java` và dựng phản hồi từ `order-service/src/main/java/com/example/order_service/service/OrderLifecycleDetail.java`. Dùng lại kiểm quyền và client tra cứu Refund khi mở rộng ORD-09.
- Payment đã có GET Refund của Customer và POST retry trong `payment-service/src/main/java/com/example/payment_service/controller/CustomerRefundController.java`. `payment-service/src/main/java/com/example/payment_service/controller/CheckoutPaymentInternalController.java` cung cấp trạng thái theo checkout và tra cứu Refund cho Order. `payment-service/src/main/java/com/example/payment_service/entity/RefundAttempt.java` lưu thời điểm tạo attempt. `payment-service/src/main/java/com/example/payment_service/repository/RefundWorkRepository.java` đọc dấu review và lịch chạy.
- Finance đã có entity Wallet, WalletTransaction và Withdrawal trong `finance-service/src/main/java/com/example/finance_service/entity/`. `finance-service/src/main/java/com/example/finance_service/service/PendingSettlementService.java` ghi tiền chờ, quyết toán và hoàn theo từng Order. `finance-service/src/main/java/com/example/finance_service/repository/WalletRepository.java` đã có mẫu khóa ví.
- Qua rà soát route, các API danh sách đơn và Admin Payment, API nghiệp vụ ví/rút và đối soát còn cần triển khai. API nội bộ tổng thu/hoàn dùng cho Finance cần đối chiếu hợp đồng §7.1 với code khi research.
</code_context>

<specifics>
## Tình huống dùng để kiểm chứng

- Ví hiển thị khả dụng 20.000đ và ledger chỉ cho thấy 18.000đ thì trả `WALLET_INCONSISTENT`. Ví và ledger khớp 20.000đ thì chênh lệch tạm do Kafka giữa các service được báo ở đối soát.
- Payment timeout khi đối soát thì trả `INCOMPLETE` và chỉ rõ Payment. Khi đủ dữ liệu và quan sát chênh 50.000đ thì báo `DIFFERENCE` tại lần kiểm tra.
- Refund đồng thời có cờ đối soát và đã hết lượt hiển thị nhãn chờ đối soát.
</specifics>

<deferred>
## Theo dõi ngoài Phase 4

Reporting phân tích, payout tiền thật và thay đổi trạng thái tài khoản theo nghĩa vụ của Phase 5 tiếp tục theo Roadmap. CR-01 và kiểm thử tích hợp các biên ngoài thật được ghi tại báo cáo xác minh Phase 3.
</deferred>
