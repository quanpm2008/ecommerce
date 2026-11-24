# Requirements: Ecommerce — Order, Payment và Finance

Ngày 20/11/2026. Phạm vi, mục tiêu Roadmap và PLAN.md Phase 1 đã được duyệt. Mỗi yêu cầu có một phase và nguồn theo điều khoản.

## v1 Requirements

### Order

- [x] **ORD-01**: Provision và tra tồn theo productId qua API nội bộ. Đã có tồn và đúng Seller thì trả `200` và giữ nguyên tồn. Seller dùng API cập nhật riêng có kiểm tra ownership và version.
  - Nghiệm thu: Provision lặp hoặc đồng thời không tạo dòng thứ hai hoặc ghi đè tồn, kể cả lượng gửi lại khác. Sai Seller bị từ chối; điều chỉnh dưới reserved hoặc version cũ bị từ chối. [Điều chỉnh Phase 1 D-12](phases/01-gi-h-ng-v-t-o-checkout-buy-now/01-CONTEXT.md#quyết-định-được-duyệt-sau-khi-xem-planmd)
  - Nguồn: [docs/requirements/api_specs.md · §6.4, §7.1](../docs/requirements/api_specs.md) · [docs/requirements/database.md · §3.6.4–6](../docs/requirements/database.md) · [docs/requirements/coverage.md · Thẻ 15, mục 2](../docs/requirements/coverage.md)
- [x] **ORD-02**: Preview BUY_NOW hoặc CART là chỉ đọc; xác minh quyền mua, địa chỉ và dữ liệu Product/Seller; chỉ BUY_NOW được nghiệm thu ở pha này.
  - Nghiệm thu: BUY_NOW chỉ dùng một mặt hàng, không đọc/ghi giỏ. Địa chỉ mặc định và tên Seller lấy qua hai API Identity đã duyệt (D-13). Đổi giá hoặc tập hàng phải xem trước và xác nhận lại.
  - Nguồn: [docs/requirements/FRD.md · UC-B04, UC-B10](../docs/requirements/FRD.md) · [docs/requirements/api_specs.md · §6.4 POST /orders/checkout/preview](../docs/requirements/api_specs.md) · [docs/requirements/parameters.md · P-10, P-26](../docs/requirements/parameters.md)
- [x] **ORD-03**: Pay xác minh quote, địa chỉ và tính lại tổng. Tạo phiên, đơn theo Seller, snapshot, giữ hàng và công việc bền vững trong một transaction.
  - Nghiệm thu: Pay mới dùng quote 5 phút, mã lỗi và cách so khớp D-14/D-15. Replay cùng nội dung và đúng chủ trả phiên cũ theo trạng thái hiện tại và snapshot, kể cả địa chỉ gốc đã xóa (D-16). Khác nội dung bị từ chối; không sinh phiên chờ thứ hai.
  - Nguồn: [docs/requirements/FRD.md · UC-B04](../docs/requirements/FRD.md) · [docs/requirements/api_specs.md · §3.7, §6.4 POST /orders/checkout/pay](../docs/requirements/api_specs.md) · [docs/requirements/database.md · §3.6.1, §8.4](../docs/requirements/database.md)
- [x] **ORD-04**: Chống oversell theo tồn khả dụng và bất biến on_hand ≥ reserved ≥ 0.
  - Nghiệm thu: Hai khách tranh món cuối chỉ một khách giữ được; thiếu một dòng rollback toàn bộ; không dựa vào tồn hiển thị.
  - Nguồn: [docs/requirements/checkout-orders.md · REQ-ORD-004, AT-05](../docs/requirements/checkout-orders.md) · [docs/requirements/software-architecture.md · §6.5](../docs/requirements/software-architecture.md) · [docs/requirements/database.md · §3.6.5](../docs/requirements/database.md)
- [x] **ORD-05**: Order áp dụng kết quả trước hạn dưới cùng khóa phiên; PAID trừ kho một lần và phát checkout.paid. Thất bại xác định trước hạn đóng phiên và nhả giữ.
  - Nghiệm thu: Đơn chỉ được xem đã thanh toán sau Order PAID; thất bại trước hạn không có SOLD/Settlement; kết quả ở đúng hạn chạy nhánh hết hạn.
  - Nguồn: [docs/requirements/FRD.md · UC-B05, UC-B06](../docs/requirements/FRD.md) · [docs/requirements/database.md · §8.2A](../docs/requirements/database.md) · [docs/requirements/software-architecture.md · §5.1–5.2](../docs/requirements/software-architecture.md)
- [x] **ORD-06**: Dùng chung hạn 15 phút cho link và giữ hàng; SQS ExpireCheckout kích hoạt đóng phiên, nhả hàng và ghi checkout.closed cùng transaction.
  - Nghiệm thu: Lệnh đến sớm, muộn, trùng và kết quả cạnh tranh cùng khóa không nhân đôi tác động; không đợi Payment để nhả hàng khi xử lý tới hạn.
  - Nguồn: [docs/decisions/0001-provider-checkout.md · Quyết định](../docs/decisions/0001-provider-checkout.md) · [docs/requirements/api_specs.md · §7.2–7.3, J1](../docs/requirements/api_specs.md) · [docs/requirements/software-architecture.md · §A.1](../docs/requirements/software-architecture.md)
- [x] **ORD-07**: Áp dụng đúng chủ đơn và state khi Seller đóng gói, giao, từ chối và khi khách hủy hoặc xác nhận nhận hàng; chạy J3/J4 theo hạn từng đơn.
  - Nghiệm thu: Khách hủy trước PACKING, xác nhận từ DELIVERED; Seller được từ chối theo trạng thái; hai mốc 7 ngày độc lập. Job và request cạnh tranh chỉ tạo một chuyển trạng thái.
  - Nguồn: [docs/requirements/FRD.md · UC-B08, UC-B09, UC-D02, UC-G03](../docs/requirements/FRD.md) · [docs/requirements/api_specs.md · §6.4, §7.3](../docs/requirements/api_specs.md) · [docs/requirements/checkout-orders.md · AC-ORD-005/006](../docs/requirements/checkout-orders.md)
- [x] **ORD-08**: Khi có bằng chứng Seller/Product không hợp lệ tại thời điểm thu, chỉ hủy đơn liên quan sau khi áp dụng thanh toán hợp lệ.
  - Nghiệm thu: Ban sau khi đã thu không tự kích hoạt hủy. Lỗi nguồn giữ việc xác minh lại. Đơn khác tiếp tục và nhánh checkout đã đóng vẫn đi theo FULL_CHECKOUT.
  - Nguồn: [docs/requirements/coverage.md · Thẻ 15, mục 3](../docs/requirements/coverage.md) · [docs/requirements/database.md · §8.2B](../docs/requirements/database.md) · [docs/requirements/checkout-orders.md · AC-ORD-003](../docs/requirements/checkout-orders.md)
- [ ] **ORD-09**: Cho Customer/Seller/Admin tra cứu đơn đúng quyền và phạm vi, gồm trạng thái, hạn xử lý và thông tin hoàn tiền phù hợp.
  - Nghiệm thu: Customer/Seller không thấy đơn khác chủ; Seller thấy cảnh báo theo hạn; Admin chỉ xem theo quyền. Đơn hủy có thể trả trạng thái đang chờ tạo Refund.
  - Nguồn: [docs/requirements/FRD.md · UC-B07, UC-D02, UC-E08](../docs/requirements/FRD.md) · [docs/requirements/api_specs.md · §6.4](../docs/requirements/api_specs.md) · [docs/requirements/parameters.md · P-36](../docs/requirements/parameters.md)
- [ ] **ORD-10**: Hỗ trợ nguồn CART đã chọn theo chủ giỏ và snapshot khi biên DELTA-02 được chốt; phát checkout.paid để Cart áp dụng phần đã mua.
  - Nghiệm thu: Không lấy dòng của khách khác hoặc đụng Cart trong BUY_NOW. Dữ liệu Cart đổi được xử lý theo contract được duyệt; Cart là bên phụ thuộc.
  - Nguồn: [docs/requirements/FRD.md · UC-B04, UC-B06, UC-B10](../docs/requirements/FRD.md) · [docs/requirements/coverage.md · DELTA-02](../docs/requirements/coverage.md) · [docs/requirements/api_specs.md · §6.3, §7.2](../docs/requirements/api_specs.md)
- [ ] **ORD-11**: Cung cấp API nghĩa vụ hiện tại cho Identity dựa trên Order, Payment và Refund, giữ đúng quyền của tài khoản có nghĩa vụ.
  - Nghiệm thu: Bao gồm hoàn chưa giải quyết và bằng chứng thu chưa áp dụng; khi nguồn lỗi không kết luận hết nghĩa vụ. Không thay client Identity trong phạm vi này.
  - Nguồn: [docs/requirements/coverage.md · Thẻ 15, mục 1](../docs/requirements/coverage.md) · [docs/requirements/api_specs.md · §3.2, §7.1.2](../docs/requirements/api_specs.md) · [docs/requirements/checkout-orders.md · AC-ORD-004](../docs/requirements/checkout-orders.md)

### Payment

- [x] **PAY-01**: Một Payment và một giao dịch provider cho mỗi checkout; tạo hoặc lấy cùng link, hạn và số tiền, qua contract provider và Order.
  - Nghiệm thu: Mất phản hồi tạo link, hai request hoặc bấm xác nhận lặp vẫn chỉ có một giao dịch; UI nhận link khi sẵn sàng hoặc trạng thái đang chờ.
  - Nguồn: [docs/requirements/FRD.md · UC-B05](../docs/requirements/FRD.md) · [docs/requirements/api_specs.md · §6.4 pay, §6.5.1, §7.1](../docs/requirements/api_specs.md) · [docs/requirements/database.md · §3.4.1, §8.4](../docs/requirements/database.md) · [docs/decisions/0001-provider-checkout.md · Quyết định](../docs/decisions/0001-provider-checkout.md)
- [x] **PAY-02**: Xác thực webhook, đối chiếu tham chiếu/số tiền/VND, lưu kết quả và Outbox trước ACK, xử lý kết quả tra cứu thu khi luồng còn mở.
  - Nghiệm thu: Webhook sai hoặc xung đột không cập nhật dữ liệu; webhook/tra cứu/replay cùng kết quả chỉ tạo một hiệu ứng; timeout riêng lẻ không thành FAILED.
  - Nguồn: [docs/requirements/api_specs.md · §6.5 webhook, §6.5.1, §7.3](../docs/requirements/api_specs.md) · [docs/requirements/database.md · §8.4](../docs/requirements/database.md) · [docs/requirements/payments.md · AC-PAY-001](../docs/requirements/payments.md)
- [x] **PAY-03**: Nhận checkout.closed thì lưu dấu đóng theo phiên kể cả chưa có Payment, dừng cấp link, tạo giao dịch và tự động tra cứu khoản thu.
  - Nghiệm thu: Checkout đóng trước tạo link hoặc sau khi đã lưu SUCCESS không mở lại luồng; webhook SUCCESS hợp lệ vẫn được xử lý.
  - Nguồn: [docs/decisions/0001-provider-checkout.md · Quyết định](../docs/decisions/0001-provider-checkout.md) · [docs/requirements/api_specs.md · §6.5.1, §7.3 J2](../docs/requirements/api_specs.md) · [docs/requirements/database.md · §8.4](../docs/requirements/database.md)
- [x] **PAY-04**: Tạo và thực hiện đúng một FULL_CHECKOUT khi Order đã đóng và có bằng chứng thu thành công.
  - Nghiệm thu: Cả hai thứ tự sự kiện, đồng thời và replay chỉ tạo một nghĩa vụ và một hoàn thành công tại provider; không có SOLD hoặc Settlement.
  - Nguồn: [docs/decisions/0001-provider-checkout.md · Quyết định](../docs/decisions/0001-provider-checkout.md) · [docs/requirements/FRD.md · UC-G02](../docs/requirements/FRD.md) · [docs/requirements/payments.md · AC-PAY-003, AT-11](../docs/requirements/payments.md) · [docs/requirements/database.md · §8.4](../docs/requirements/database.md)
- [x] **PAY-05**: Refund ORDER bằng tổng đúng đơn hủy hợp lệ sau thu. Khóa Payment để bảo toàn tổng nghĩa vụ, kể cả FAILED còn được thử lại, và tách ORDER/FULL_CHECKOUT.
  - Nghiệm thu: Đơn hủy được hoàn một lần; đơn khác vẫn xử lý. Tổng hoàn không vượt thu và hai phạm vi không cùng tồn tại cho một Payment.
  - Nguồn: [docs/requirements/FRD.md · UC-G02](../docs/requirements/FRD.md) · [docs/requirements/database.md · §3.4.2, §8.4](../docs/requirements/database.md) · [docs/requirements/payments.md · AC-PAY-002](../docs/requirements/payments.md)
- [x] **PAY-06**: J5 chỉ tra cứu khi kết quả lần hoàn chưa rõ. Một lần thực hiện đầu và tối đa ba retry sau 1/5/15 phút với lỗi xác định. Chỉ Customer đúng chủ mở chu kỳ mới khi hết lượt.
  - Nghiệm thu: Giữ Refund, số tiền và mã nghĩa vụ; mỗi lần thử có định danh; hai tab chỉ mở một chu kỳ tương ứng; giữ lịch sử, Admin không làm thay.
  - Nguồn: [docs/requirements/parameters.md · P-RF-01](../docs/requirements/parameters.md) · [docs/requirements/api_specs.md · §6.5 retry, §6.5.1, §7.3 J5](../docs/requirements/api_specs.md) · [docs/requirements/payments.md · AC-PAY-004/005/006](../docs/requirements/payments.md)
- [ ] **PAY-07**: Cho khách tra cứu thu/hoàn đúng checkout, kể cả FULL_CHECKOUT không có orderId; cho Admin xem khoản bất thường và ghi chú theo quyền.
  - Nghiệm thu: Không trả dữ liệu khác chủ; Admin không retry thay khách, không sửa kết quả tài chính; tổng thu/hoàn phục vụ Finance đối soát.
  - Nguồn: [docs/requirements/FRD.md · UC-E08, UC-G02](../docs/requirements/FRD.md) · [docs/requirements/api_specs.md · §6.5, §7.1 Payment totals](../docs/requirements/api_specs.md) · [docs/requirements/payments.md · AC-PAY-004](../docs/requirements/payments.md)
- [ ] **PAY-08**: Cung cấp trạng thái và nghĩa vụ thu/hoàn để Order tổng hợp, kể cả FULL_CHECKOUT của checkout đã đóng.
  - Nghiệm thu: Dấu đóng luồng thu không tự đồng nghĩa nghĩa vụ bằng 0. Dữ liệu nguồn thiếu được phân biệt với không có nghĩa vụ.
  - Nguồn: [docs/requirements/api_specs.md · §7.1, §7.1.2](../docs/requirements/api_specs.md) · [docs/requirements/database.md · §3.1.1, §8.4](../docs/requirements/database.md) · [docs/requirements/coverage.md · Thẻ 15, mục 1](../docs/requirements/coverage.md)

### Finance

- [x] **FIN-01**: Cung cấp hoa hồng chung 500 bps và ghi Settlement PENDING theo từng Order từ checkout.paid, tính phần Seller và hoa hồng theo snapshot.
  - Nghiệm thu: Mỗi đơn một Settlement; làm tròn nửa lên một lần trên tổng đơn; replay không ghi tiền hai lần; FULL_CHECKOUT không ghi ví.
  - Nguồn: [docs/requirements/api_specs.md · §6.6, §7.1 commission-rate, §7.2](../docs/requirements/api_specs.md) · [docs/requirements/parameters.md · P-COM-01](../docs/requirements/parameters.md) · [docs/requirements/finance.md · REQ-FIN-001/002, AC-FIN-005](../docs/requirements/finance.md)
- [x] **FIN-02**: Từ order.confirmed, chuyển tiền chờ thành khả dụng đúng một lần. Với đơn hủy, chuyển PENDING_REFUND và chỉ giảm pending khi nhận Refund ORDER thành công.
  - Nghiệm thu: Hai đơn cùng Payment có thể quyết toán và hoàn riêng. Không ghi trùng ledger, không giảm available/đã rút khi hoàn.
  - Nguồn: [docs/requirements/FRD.md · UC-G02, UC-G04](../docs/requirements/FRD.md) · [docs/requirements/database.md · §8.2I, §8.3](../docs/requirements/database.md) · [docs/requirements/finance.md · AC-FIN-003, AT-15](../docs/requirements/finance.md)
- [x] **FIN-03**: Kiểm soát Inbox và giữ công việc khi event đến trước Settlement hoặc ví cần thiết.
  - Nghiệm thu: Replay và sự kiện đến sai thứ tự không mất tác động, không tạo số dư âm và xử lý được sau khi phụ thuộc xuất hiện.
  - Nguồn: [docs/requirements/finance.md · REQ-FIN-002, AC-FIN-003, AT-14](../docs/requirements/finance.md) · [docs/requirements/software-architecture.md · §5.3, §5.5, §6.6](../docs/requirements/software-architecture.md)
- [ ] **FIN-04**: Cung cấp ví, Settlement, lịch sử ledger và rút tiền; ghi Withdrawal và ledger một lần trong transaction.
  - Nghiệm thu: Không rút tiền chờ hoặc vượt available; hai yêu cầu đồng thời không làm âm ví; Seller BANNED đủ điều kiện được rút toàn bộ số dư dương nhỏ theo ngoại lệ đã duyệt.
  - Nguồn: [docs/requirements/FRD.md · UC-D05](../docs/requirements/FRD.md) · [docs/requirements/database.md · §8.3 WD-01–11](../docs/requirements/database.md) · [docs/requirements/coverage.md · Thẻ 15, mục 1](../docs/requirements/coverage.md) · [docs/requirements/finance.md · AC-FIN-002](../docs/requirements/finance.md)
- [ ] **FIN-05**: Đối soát tổng thu, hoàn, số dư và khoản chờ theo nguồn của Order/Payment/Finance; hỗ trợ tra cứu vận hành đúng quyền.
  - Nghiệm thu: Phát hiện chênh lệch; thiếu nguồn không báo cân bằng và không tự sửa sổ. Không đưa dashboard phân tích đã hoãn vào pha này.
  - Nguồn: [docs/requirements/FRD.md · UC-D04, UC-E08](../docs/requirements/FRD.md) · [docs/requirements/finance.md · REQ-FIN-004, AC-FIN-001](../docs/requirements/finance.md) · [docs/requirements/software-architecture.md · §6.6, §7.1](../docs/requirements/software-architecture.md)
- [ ] **FIN-06**: Cung cấp API nghĩa vụ Seller cho Identity dựa trên Settlement, Wallet và tác động tài chính còn chờ.
  - Nghiệm thu: Nhận diện pending, available và hiệu ứng chưa áp dụng; lỗi/404 bất thường không trả về nghĩa vụ 0. J7 vẫn là dependency bên Identity.
  - Nguồn: [docs/requirements/api_specs.md · §7.1, §7.1.2](../docs/requirements/api_specs.md) · [docs/requirements/finance.md · AC-FIN-003/004](../docs/requirements/finance.md) · [docs/requirements/coverage.md · Thẻ 15, mục 1](../docs/requirements/coverage.md)

### Tích hợp giữa ba service

- [x] **INT-01**: Ghi thay đổi và Outbox cùng transaction cục bộ. Phát sau commit; bên nhận xử lý idempotent theo event và nghiệp vụ.
  - Nghiệm thu: Relay mất ACK và phát lại không nhân đôi kho, Payment, Refund hoặc Settlement. SQS chỉ nhận lệnh của Order; kết quả nghiệp vụ đi qua Kafka.
  - Nguồn: [docs/requirements/software-architecture.md · §4.3, §6.5, §A.1](../docs/requirements/software-architecture.md) · [docs/requirements/api_specs.md · §7.2–7.3](../docs/requirements/api_specs.md) · [docs/requirements/database.md · §8.4](../docs/requirements/database.md) · [docs/decisions/0001-provider-checkout.md · Quyết định](../docs/decisions/0001-provider-checkout.md)

## Out of Scope

Service khác chỉ xuất hiện dưới dạng hợp đồng và dependency. Xem [Project](PROJECT.md) và [báo cáo xung đột](intel/INGEST-CONFLICTS.md).

## Traceability

| Requirement | Phase | Status |
| --- | --- | --- |
| ORD-01 | Phase 1 | Đạt 3A và E1 BUY_NOW |
| ORD-02 | Phase 1 | Đạt 3A và E1 BUY_NOW |
| ORD-03 | Phase 1 | Đạt 3A và E1 BUY_NOW |
| ORD-04 | Phase 1 | Đạt 3A |
| PAY-01 | Phase 2 | Complete |
| PAY-02 | Phase 2 | Complete |
| ORD-05 | Phase 2 | Complete |
| ORD-06 | Phase 2 | Complete |
| PAY-03 | Phase 2 | Complete |
| PAY-04 | Phase 2 | Complete |
| INT-01 | Phase 2 | Complete |
| FIN-01 | Phase 2 | Complete |
| ORD-07 | Phase 3 | Complete |
| ORD-08 | Phase 3 | Complete |
| PAY-05 | Phase 3 | Complete |
| PAY-06 | Phase 3 | Complete |
| FIN-02 | Phase 3 | Complete |
| FIN-03 | Phase 3 | Complete |
| ORD-09 | Phase 4 | Pending |
| PAY-07 | Phase 4 | Pending |
| FIN-04 | Phase 4 | Pending |
| FIN-05 | Phase 4 | Pending |
| ORD-10 | Phase 5 | Pending |
| ORD-11 | Phase 5 | Pending |
| PAY-08 | Phase 5 | Pending |
| FIN-06 | Phase 5 | Pending |

**Coverage:** 26/26 yêu cầu đã có phase. Trạng thái `Pending` thể hiện chưa nghiệm thu.
