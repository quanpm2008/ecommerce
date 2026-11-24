# Phase 4 — Tra cứu, ví và đối soát

Ngày nghiên cứu **24/11/2026** · GSD Research · Phạm vi **Order, Payment và Finance**

## Kết luận

- **ORD-09** có thể tiếp tục từ chi tiết đơn và kiểm tra quyền hiện có. Phần thiếu chính là danh sách Customer/Seller, tra cứu Admin và các projection đúng phạm vi. Dùng dữ liệu Order đã chụp khi mua để trình bày đơn cũ.
- **PAY-07** có thể dùng lại truy vấn Payment nội bộ, danh sách Refund Customer và dữ liệu attempt/work. Bổ sung tra cứu Payment của Customer và phần Admin. Hai ngưỡng cảnh báo chỉ phục vụ hiển thị.
- **FIN-04** đã có entity, bảng, ledger và khóa ví. Phần thiếu là API Seller và transaction rút tiền, kèm một sự kiện `withdrawal.completed` bền vững cho mỗi lần rút thành công.
- **FIN-05** có công thức và quy tắc phân loại kết quả. Cần cụ thể hóa dữ liệu nguồn trước khi xây dựng bộ tổng hợp. Admin có thể đọc và thấy nguồn thiếu, độ lệch hoặc ví lệch; quyền ghi chú chỉ áp dụng cho đối soát khoản thu.

## Nguồn và phạm vi

| Nguồn | Nội dung áp dụng |
| --- | --- |
| [Roadmap Phase 4](../../ROADMAP.md#phase-4-tra-cứu-ví-và-đối-soát), [Requirements](../../REQUIREMENTS.md), [Context D-01–D-05](04-CONTEXT.md) | Bốn yêu cầu và quyết định triển khai đã duyệt. |
| [SAD §6.6, §7.1 và D.1](../../../docs/requirements/software-architecture.md), [Coverage](../../../docs/requirements/coverage.md), [ADR 0001](../../../docs/decisions/0001-provider-checkout.md) | Ranh giới sở hữu dữ liệu, ưu tiên tài liệu và phạm vi các hiệu chỉnh đã duyệt, gồm Thẻ 15 về quyền theo nghĩa vụ. |
| [FRD UC-B05/B07, D04–D05, E02/E08](../../../docs/requirements/FRD.md), [API §3.2, §3.7, §6.4–6.6, §7.1 và §7.3](../../../docs/requirements/api_specs.md), [Database §6, §8.2B–8.3](../../../docs/requirements/database.md) | Quyền, API, audit, ledger, công thức và event. Xem thêm [Payment](../../../docs/requirements/payments.md) và [Finance](../../../docs/requirements/finance.md#wd-03) theo từng ca nghiệp vụ. |
| [ADR 0002 và REQ-ENG-004](../../../docs/engineering.md#req-eng-004), [Quy trình repo](../../../docs/WORKFLOW.md) | Spring Data JPA, entity và Flyway. HTTP nội bộ đồng bộ mới qua Spring Cloud OpenFeign. |
| [Bằng chứng Phase 3](../03-giao-h-ng-h-y-n-v-quy-t-to-n/03-4A-EVIDENCE.md) | Kết quả đã ghi nhận cho lifecycle, Refund ORDER và Settlement. Nghiên cứu này đối chiếu workspace hiện tại và đọc lại bằng chứng có sẵn. |

Identity cung cấp quyền hiện hành và trạng thái Seller. Notification nhận event tài chính. Hai service này là biên tích hợp. Báo cáo phân tích theo kỳ, chi tiền thật và các luồng Phase 5 nằm ngoài phạm vi.

## ORD-09 — Tra cứu đơn theo quyền

### AS-IS

- [OrderLifecycleController](../../../order-service/src/main/java/com/example/order_service/controller/OrderLifecycleController.java) đã có GET chi tiết Customer và Seller. [OrderLifecycleDetail](../../../order-service/src/main/java/com/example/order_service/service/OrderLifecycleDetail.java) kiểm tra chủ đơn, yêu cầu đơn đã thu tiền đối với Seller, trả các deadline đã lưu và lấy thông tin Refund cho đơn Customer bị hủy.
- [OrderAuthorization](../../../order-service/src/main/java/com/example/order_service/security/OrderAuthorization.java) đã kiểm tra quyền hiện hành và phiên hạn chế theo hành động. [PaymentCheckoutClient](../../../order-service/src/main/java/com/example/order_service/client/PaymentCheckoutClient.java) qua Feign đọc Payment/Refund. API nội bộ Refund hỗ trợ danh sách `orderIds`, đường chi tiết hiện dùng theo từng đơn.
- Chưa thấy GET danh sách đơn Customer/Seller và GET danh sách/chi tiết Admin trong controller hiện tại. Code xử lý đơn của Customer/Seller chưa chứng minh đường truy cập quản trị.

### TO-BE và cách tái sử dụng

- Giữ contract API §6.4. Áp dụng quyền, ownership và phạm vi phiên hạn chế vào truy vấn trước phân trang. Dùng projection riêng theo vai trò, nhất là khi một checkout có đơn của nhiều Seller. Sai chủ trả 404 theo contract. Admin có quyền `operations:read:any` với dữ liệu đã lọc phù hợp.
- Tái dùng các trường snapshot, deadline và phép ánh xạ trạng thái đã có. Dùng một lần đọc Refund theo lô khi một trang thực sự cần nhiều đơn. Xác minh bằng contract test cách trả kết quả khi Payment lỗi để tránh biến lỗi nguồn thành “không có Refund”.
- Giữ quyền đọc đơn và xử lý đơn của Seller BANNED theo đúng nghĩa vụ trong Thẻ 15. Bằng chứng cho quyền Customer/Seller trên API chi tiết cần được giữ khi mở rộng sang danh sách.

## PAY-07 — Tra cứu thanh toán, hoàn và hàng chờ Admin

### AS-IS

- [CheckoutPaymentInternalController](../../../payment-service/src/main/java/com/example/payment_service/controller/CheckoutPaymentInternalController.java) đã đọc Payment theo checkout và Refund nội bộ. [CustomerRefundController](../../../payment-service/src/main/java/com/example/payment_service/controller/CustomerRefundController.java) đã có danh sách Refund và retry, kể cả FULL_CHECKOUT của đúng Customer. Chưa thấy Customer GET Payment công khai, Admin issues, Admin Payment/Refund và `mark-handled`.
- [V3 collection work](../../../payment-service/src/main/resources/db/migration/V3__collection_work_and_checkout_guard.sql) có state công việc, guard và observation. [RefundAttempt](../../../payment-service/src/main/java/com/example/payment_service/entity/RefundAttempt.java), [RefundWorkRepository](../../../payment-service/src/main/java/com/example/payment_service/repository/RefundWorkRepository.java) và [V8 refund review](../../../payment-service/src/main/resources/db/migration/V8__refund_outcome_review.sql) cung cấp dữ liệu thời điểm attempt, lịch chạy và `review_required_at`.
- [Database §6](../../../docs/requirements/database.md) mô tả audit. Database đặt cờ/ghi chú đối soát khoản thu trên CheckoutSession, được ánh xạ trong [CheckoutSession của Order](../../../order-service/src/main/java/com/example/order_service/entity/CheckoutSession.java). [RBAC seed Identity](../../../identity-service/src/main/resources/db/migration/V2__seed_rbac.sql) có `operations:read:any`; chưa tìm thấy `operations:annotate` trong mã hoặc migration Identity hiện tại.

### TO-BE theo D-01–D-03

| Ca | Hiển thị và dữ liệu |
| --- | --- |
| D-01 · Payment đang PENDING, checkout còn mở, chưa có link hợp lệ, tuổi Payment ≥ 2 phút | `PAYMENT_PENDING`. Payment có link hợp lệ vẫn hiển thị trạng thái chờ thanh toán bình thường. Dùng thời điểm tạo Payment, kèm điều kiện checkout còn mở. |
| Webhook bị chậm | Chỉ hiển thị lý do này khi có bằng chứng trực tiếp. Thiếu link, thiếu webhook hoặc một mã giao dịch provider tự thân chưa đủ để kết luận provider chưa giao webhook. |
| D-02 · Attempt hiện tại vẫn PENDING ≥ 30 phút | `REFUND_STUCK`. Tính từ lần thử hiện tại. Lịch sử attempt cũ và các lượt tra cứu của J5 giữ nguyên. |
| D-03 · Refund FAILED còn lượt, hết lượt hoặc đang chờ đối soát mâu thuẫn | Còn lượt hiển thị đang chờ thử lại tự động và lịch tiếp theo nếu có. Hết lượt hiển thị chờ khách thử lại. Cờ `review_required_at` ưu tiên thông báo tạm dừng hoàn và tiếp tục khóa Customer retry, kể cả khi đã hết lượt. |

- Customer GET Payment xác minh checkout đúng chủ trước khi dùng response `data: null` cho phiên chưa có Payment theo API §6.5. Kết hợp quyền hiện hành từ [RefundCustomerAuthorization](../../../payment-service/src/main/java/com/example/payment_service/security/RefundCustomerAuthorization.java) với chủ checkout từ Order. [Truy vấn payable nội bộ hiện có](../../../order-service/src/main/java/com/example/order_service/repository/CheckoutReplayRepository.java#L72) trả `customerId` và trạng thái cả khi checkout đã đóng. Kiểm chứng bằng contract test khi tái dùng.
- Admin đọc theo `operations:read:any`. Endpoint issues giữ bốn giá trị `issueType` của API. Đọc từ trạng thái đã lưu; ngưỡng hiển thị giữ nguyên hạn checkout, retry budget, lịch J2/J5 và trạng thái giao dịch.
- `mark-handled` yêu cầu `operations:annotate`, note 1–1.000 ký tự và audit `MARK_HANDLED`. Ghi chú hoặc gỡ cờ của khoản thu. Refund, kết quả thu, số dư và lịch provider nằm ngoài quyền của hành động này.

## FIN-04 — Ví, sổ giao dịch và rút tiền giả lập

### AS-IS

- [FinanceCommissionController](../../../finance-service/src/main/java/com/example/finance_service/controller/FinanceCommissionController.java) hiện có endpoint commission-rate nội bộ. Chưa thấy controller Seller wallet/ledger/settlement/withdrawal hoặc Admin Finance.
- [V1 Finance](../../../finance-service/src/main/resources/db/migration/V1__finance_entities.sql) đã có Wallet, Withdrawal và WalletTransaction, `UNIQUE(seller_id,idempotency_key)` cho Withdrawal và `UNIQUE(wallet_id,business_event_key)` cho ledger. [WalletRepository](../../../finance-service/src/main/java/com/example/finance_service/repository/WalletRepository.java) có khóa ghi JPA. [PendingSettlementService](../../../finance-service/src/main/java/com/example/finance_service/service/PendingSettlementService.java) đã khóa ví và ghi ledger cho lifecycle Settlement. [Outbox hiện có](../../../finance-service/src/main/resources/db/migration/V3__settlement_lifecycle_inbox.sql) có schema dành riêng cho `funds.available`.

### TO-BE theo WD-03–WD-09 và D-04

- Đọc ví, ledger, Settlement và biên nhận đúng chủ/quyền. Dùng quyền và trạng thái mới nhất tại biên Identity, xem các chế độ của Seller BANNED theo WD-09. Pending-only chỉ xem trong phạm vi được cấp. Available-only được xem ví/rút/biên nhận liên quan nếu quyền hiện hành cho phép.
- Trong transaction rút, khóa ví Seller cùng cơ chế với cập nhật Settlement. Sau khi xác thực chủ/quyền, xử lý replay cùng khóa theo dữ liệu Withdrawal đã lưu để trả đúng biên nhận kể cả khi số dư hiện tại đã thay đổi. Khóa cũ khác số tiền trả `409 IDEMPOTENCY_KEY_REUSED`. Đối với lần rút mới, đối chiếu các ngăn của ví với tổng delta ledger dưới khóa và trả `409 WALLET_INCONSISTENT` khi lệch.
- Kiểm tra amount nguyên dương, số dư available, freeze độc lập và ngưỡng 10.000 VND. Ngoại lệ đã duyệt chỉ cho Seller BANNED đủ quyền rút hết available dương dưới ngưỡng. Giao dịch mới commit cùng lúc việc chuyển tiền từ available sang totalWithdrawn, Withdrawal, bút toán `WITHDRAWAL_COMPLETED` và outbox `withdrawal.completed`. API trả 201 lần đầu, 200 khi replay cùng nội dung. Lỗi giữa chừng rollback toàn bộ.
- Tái dùng mẫu relay bền vững và event contract API §7.3. Chọn cấu trúc outbox hỗ trợ payload Withdrawal của Finance, giữ luồng `funds.available` đang chạy. Sự kiện Kafka của Finance được phát sau commit, có định danh ổn định khi relay thử lại. Gọi Identity trước khi khóa ví để giới hạn thời gian khóa; báo cáo chênh lệch liên service không nằm trên đường ghi Withdrawal.

## FIN-05 — Đối soát và tra cứu vận hành

### AS-IS

Các nguồn cục bộ đã có Payment/Refund ở Payment; checkout/đơn ở Order; wallet/ledger/withdrawal/settlement ở Finance. API §7.1 nêu Payment `GET /internal/v1/payments/totals` cho Finance nhưng chưa thấy route triển khai. Chưa thấy hợp đồng aggregate Order → Finance tương ứng với FIN-05. Các bảng dữ liệu riêng chưa tạo thành báo cáo đối soát.

### TO-BE theo Database §8.3, SAD §6.6 và D-05

| Nguồn | Dữ kiện cần cho lần đọc |
| --- | --- |
| Payment | Tổng thu SUCCESS tính một lần mỗi Payment, Refund SUCCESS, nghĩa vụ FULL_CHECKOUT chưa SUCCESS. Tách rõ tổng “chờ hoàn” phục vụ hiển thị khỏi phần phải cộng trong phương trình. |
| Finance | Pending của các ví, hoa hồng đã chốt, available và totalWithdrawn của Seller. Kiểm tra WD-11 từng Seller bằng Settlement/Withdrawal và báo ví lệch theo contract. Đọc các số Finance từ một ảnh chụp nhất quán cục bộ. |
| Order | Bằng chứng Order đã áp dụng kết quả checkout để diễn giải khoản đang chuyển tiếp, theo FIN-05. Hợp đồng xác định chính xác chỉ số, cách đọc và ý nghĩa của dữ liệu Order còn cần chốt. |

Phương trình đã duyệt là tổng thu SUCCESS bằng tổng hoàn SUCCESS cộng FULL_CHECKOUT chưa SUCCESS, pending tất cả ví, hoa hồng đã chốt, available Seller và đã rút Seller. ORDER đang chờ hoàn đã nằm trong pending, nên cộng đúng một lần. Hoa hồng đã chốt chỉ tính một lần. Dùng số dư tại thời điểm đọc, cùng đơn vị nguyên VND.

- Đủ nguồn thì trả HTTP 200 với trạng thái `COMPLETE` hoặc `DIFFERENCE`, `balanced`, tổng và độ lệch theo các lần đọc được báo. Nếu có lag nhưng các nguồn vẫn đầy đủ và hợp lệ, độ lệch quan sát được có thể là tạm thời.
- Nguồn bắt buộc thiếu, timeout hoặc payload không dùng được thì trả 200 `INCOMPLETE`, `balanced=false`, `differenceVnd=null`, danh sách nguồn thiếu và thời điểm đọc nguồn có dữ liệu. Mọi trường hợp có `checkedAt`. Thời điểm khác nhau giữa các nguồn là thông tin cần để đọc báo cáo. Báo cáo là thao tác đọc.
- API tra cứu vận hành có thể lấy các projection phân trang trực tiếp từ JPA và giữ quyền Admin chỉ đọc. Mỗi service đọc dữ liệu mình sở hữu. Finance gọi nguồn tổng hợp nội bộ qua OpenFeign, không nối trực tiếp schema của service khác.

    ## Điểm cần giải quyết trước phần triển khai phụ thuộc

| Điểm | Phương án đưa vào bước lập kế hoạch |
| --- | --- |
| **Quyền và đường ghi `mark-handled`** | Giữ public API ở Payment theo §6.5 và cờ khoản thu ở Order theo Database. Đề xuất Payment ủy quyền ghi qua một internal command hợp đồng rõ ràng, để Order cập nhật cờ/note và ghi audit trong cùng transaction. Cần xác nhận đường ghi, nguồn cờ để Payment đọc và cách nhận được `operations:annotate` từ Identity. Tách phần đọc khỏi blocker này. Không thay quyền annotate bằng quyền Admin đọc. |
| **DTO Admin issues** | D-03 đã chốt nội dung hiển thị nhưng schema §6.5 chưa có trường diễn giải và lịch thử tiếp. Đề xuất mã lý do hiển thị và mốc retry nullable, giữ nguyên enum hiện tại. Xác nhận ưu tiên và cách lọc nếu một nghĩa vụ vừa khớp ngưỡng vừa có cờ review để tránh hiển thị trùng hoặc làm mất cảnh báo. |
| **Hợp đồng nguồn đối soát** | Chốt DTO Payment totals, bằng chứng tối thiểu từ Order và cách Finance xác định thiếu nguồn. Chốt tên trường D-05, chiều dấu `differenceVnd`, cách biểu diễn tổng từng phần và cách phân loại khi tổng toàn sàn bằng nhau nhưng WD-11 báo ví lệch. Đề xuất coi sai lệch ví là `DIFFERENCE` khi đủ nguồn và vẫn giữ độ lệch tổng riêng. Giữ trạng thái giao dịch và công thức đã duyệt. |

Các nhánh tra cứu Order/Payment có thể tiến hành theo contract đọc đã rõ. Ví và rút tiền chỉ phụ thuộc quyền Identity và transaction Finance, có thể lập kế hoạch độc lập với DTO đối soát. Bộ tổng hợp FIN-05 cần hợp đồng nguồn trước khi kiểm thử liên service. Giữ checkpoint tương ứng trong kế hoạch thay vì làm chậm toàn bộ Phase 4.

## Validation Architecture

| Yêu cầu | Bằng chứng cần ở bước triển khai |
| --- | --- |
| ORD-09 | PostgreSQL và HTTP contract test về phân trang đúng chủ, hai Seller trong một checkout, sai chủ, quyền Admin, phiên hạn chế, deadline, dữ liệu Refund và trường hợp nguồn Refund lỗi. Tái dùng cách dựng dữ liệu của `OrderLifecyclePostgresTests`. |
| PAY-07 | Tái dùng fixtures của `PaymentCollectionPostgresTests`, `CustomerRefundRetryPostgresTests` và các test FULL_CHECKOUT. Dùng đồng hồ kiểm thử tại dưới/đúng ngưỡng 2 và 30 phút, link hợp lệ, checkout đóng, attempt cũ, review ưu tiên. Kiểm chứng ownership kể cả Payment chưa tồn tại. Kiểm tra Admin thiếu quyền annotate bị từ chối, audit rollback cùng ghi chú khi lỗi và không đổi dòng tiền. |
| FIN-04 | PostgreSQL thật, giao dịch đồng thời với cùng key và khác key, rollback khi ghi ledger/outbox lỗi, replay sau lần rút khác, same-key khác amount, ví lệch, BANNED dưới ngưỡng, freeze độc lập và giới hạn available. Tái dùng fixture `SettlementLifecyclePostgresTests`; kiểm tra event `withdrawal.completed` ở biên broker, đúng payload và không phát thêm event nghiệp vụ do replay. |
| FIN-05 | HTTP mock theo contract nguồn và PostgreSQL thật cho aggregates Finance. Fixture cân, Refund ORDER đang nằm trong pending, FULL_CHECKOUT chưa SUCCESS, Refund SUCCESS, nguồn timeout/payload lỗi, ví lệch dù tổng bằng nhau, dữ liệu lệch giữa các thời điểm đọc. Xác minh các thao tác đối soát chỉ đọc. Sau kiểm thử contract, kiểm tra đường gọi trên các service thực với cùng fixture đã chọn. |

Phase 3 có mẫu PostgreSQL và kiểm thử qua broker trong `Phase3LocalBrokerIT`. Mock HTTP chứng minh consumer tuân thủ hợp đồng đã chốt; tích hợp thật có bằng chứng riêng. Research này chưa chạy bộ kiểm thử, chưa tạo PLAN và chưa thay đổi trạng thái nghiệm thu.
