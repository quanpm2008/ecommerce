# Roadmap: Ecommerce — Order, Payment và Finance

## Overview

Phạm vi và mục tiêu 5 phase đã được người dùng duyệt. Giữ phạm vi ở ba module hiện hữu và các hợp đồng do chúng sở hữu. Thảo luận và lập kế hoạch theo chế độ interactive. **Phase 1 đạt 3A và E1 BUY_NOW với Identity/Product thật ngày 22/11/2026. Phase 2 đã xác minh Wave 1–4 với bằng chứng G3-A qua HTTP mock. Hoàn tất 9/9 plan và đạt 4A với HTTP mock.**

Trước khi lập nhiệm vụ cho mỗi phase, kiểm tra AS-IS từ code, migration, test và cấu hình liên quan. Tận dụng hành vi đã có và đã được xác minh. Với hành vi lệch đặc tả, ghi chênh lệch và cách kiểm chứng thay đổi. Quyết định nghiệp vụ còn mở phải được chốt trước nhiệm vụ phụ thuộc.

## Phases

- [x] **Phase 1: Giữ hàng và tạo checkout BUY_NOW** - Order tạo được phiên giữ hàng từ sản phẩm hợp lệ, giữ đúng chủ và chứng minh chống trùng cũng như chống bán vượt tồn.
- [x] **Phase 2: Thanh toán, hết hạn và tiền chờ** - Từ một checkout có thể đi đến PAID và tiền chờ hoặc hết hạn, nhả hàng và hoàn đúng một lần nếu đã thu. (completed 2026-11-22)
- [x] **Phase 3: Giao hàng, hủy đơn và quyết toán** - Đơn đã thanh toán đi qua fulfillment; hủy và xác nhận có hiệu ứng Refund hoặc Settlement trên đúng đơn. (completed 2026-11-24)
- [ ] **Phase 4: Tra cứu, ví và đối soát** - Khách và quản trị tra cứu được trạng thái, Seller rút được tiền khả dụng và Finance đối chiếu được sổ tiền.
- [ ] **Phase 5: Biên CART và nghĩa vụ đang xử lý** - Ba service cung cấp đúng phần tích hợp do mình sở hữu khi Cart và Identity kết nối.

## Phase Details

### Phase 1: Giữ hàng và tạo checkout BUY_NOW
**Goal**: Order tạo được phiên giữ hàng từ sản phẩm hợp lệ, giữ đúng chủ và chứng minh chống trùng cũng như chống bán vượt tồn.
**Depends on**: Không có phase trước. Identity/Product là phụ thuộc tích hợp. Finance dùng mock hoa hồng 500 bps cho Phase 1; API thật thuộc FIN-01 Phase 2. Quyết định provision gồm chi tiết HTTP, Preview/Pay và replay đã duyệt ở Context D-12–D-18. Đồng bộ điều khoản nguồn trước nhiệm vụ phụ thuộc.
**Requirements**: ORD-01, ORD-02, ORD-03, ORD-04
**Success Criteria** (what must be TRUE):
  1. Provision lặp chỉ có một dòng tồn. Seller không chỉnh tồn xuống dưới lượng đã giữ (ORD-01).
  2. BUY_NOW xem trước đúng sản phẩm, không thay đổi giỏ, kiểm tra lại giá và địa chỉ khi pay (ORD-02, ORD-03).
  3. Bấm lặp không sinh phiên mới. Hai khách tranh món cuối không giữ vượt tồn và checkout thiếu một dòng rollback đủ (ORD-03, ORD-04).
**Plans**: 9/9 kế hoạch đã kiểm chứng. Order đạt 50/50 hồi quy, E1 đạt 5/5. [Báo cáo Wave 7](phases/01-gi-h-ng-v-t-o-checkout-buy-now/01-05-SUMMARY.md)
**Wave 1**
- [x] 01-01-PLAN.md — Tra tồn qua HTTP và PostgreSQL thật

**Wave 2** *(blocked on Wave 1 completion)*
- [x] 01-02-PLAN.md — Provision Inventory và replay theo productId
- [x] 01-06-PLAN.md — Quyền hiện hành và contract HTTP Identity/Product

**Wave 3** *(blocked on Wave 2 completion)*
- [x] 01-03-PLAN.md — Preview BUY_NOW và quote 5 phút
- [x] 01-07-PLAN.md — Seller chỉnh tồn và tra lịch sử

**Wave 4** *(blocked on Wave 3 completion)*
- [x] 01-04-PLAN.md — Pay và commit transaction O1

**Wave 5** *(blocked on Wave 4 completion)*
- [x] 01-08-PLAN.md — Replay và chống tạo checkout lặp

**Wave 6** *(blocked on Wave 5 completion)*
- [x] 01-09-PLAN.md — Tranh tồn, rollback nhiều dòng và nghiệm thu 3A

**Wave 7**
- [x] 01-05-PLAN.md — Kiểm thử bổ sung Identity và Product thật

### Phase 2: Thanh toán, hết hạn và tiền chờ
**Goal**: Từ một checkout có thể đi đến PAID và tiền chờ hoặc hết hạn, nhả hàng và hoàn đúng một lần nếu đã thu.
**Depends on**: Mốc 3A của Phase 1 đã được chấp nhận để chuyển sang Phase 2. Dùng Kafka/SQS và provider adapter theo contract. Provider HTTP mock được duyệt cho dev/test. E1 Identity/Product cho BUY_NOW đã được nghiệm thu riêng ở Phase 1. `checkout.closed.closeReason` đã chốt `PAYMENT_FAILED` hoặc `EXPIRED` theo ADR 0001.
**Requirements**: PAY-01, PAY-02, ORD-05, ORD-06, PAY-03, PAY-04, INT-01, FIN-01
**Success Criteria** (what must be TRUE):
  1. Mất phản hồi hay xác nhận lặp vẫn dùng một link/giao dịch. Webhook sai không thay đổi trạng thái (PAY-01, PAY-02).
  2. Chỉ Order quyết định PAID dưới khóa trước hạn; tiền chờ được ghi đúng một lần từ checkout.paid (ORD-05, INT-01, FIN-01).
  3. Lệnh hết hạn và webhook cạnh tranh chỉ có một kết quả Order; hai thứ tự đóng/thu đều chỉ tạo một FULL_CHECKOUT khi đã thu (ORD-06, PAY-03, PAY-04).
**Plans**: 8/9 plan đã xác minh theo 6 wave đã duyệt. G1-A, G2-A và G3-A được ghi tại Context Phase 2 và ADR 0001. Nhánh PAID đã kiểm chứng với HTTP mock theo hợp đồng Seller/Product tại completedAt.

**Wave 1**
- [x] 02-01-PLAN.md — Kiểm tra baseline, đồng bộ 2A/3A và chốt G1 cho payable.
- [x] 02-02-PLAN.md — Adapter HTTP và fixture provider mock 1A.

**Wave 2**
- [x] 02-03-PLAN.md — Tạo hoặc lấy lại link, giữ một Payment và tra cứu J2.

**Wave 3**
- [x] 02-04-PLAN.md — Order tra cứu phiên và relay công việc tạo link, hết hạn.
- [x] 02-06-PLAN.md — Webhook thu, kết quả J2 và tiếp nhận checkout.closed.

**Wave 4**
- [x] 02-05-PLAN.md — PAID, PAYMENT_FAILED và J1 cùng khóa trước hạn.
- [x] 02-07-PLAN.md — Refund FULL_CHECKOUT, attempt/J5 và webhook refund.result.

**Wave 5**
- [x] 02-08-PLAN.md — Hoa hồng và Settlement PENDING, sổ tiền chờ trong Finance.

**Wave 6**
- [x] 02-09-PLAN.md — Nghiệm thu 4A qua HTTP, PostgreSQL, Kafka và ElasticMQ. Đạt 4A.

### Phase 3: Giao hàng, hủy đơn và quyết toán
**Goal**: Đơn đã thanh toán đi qua fulfillment; hủy và xác nhận có hiệu ứng Refund hoặc Settlement trên đúng đơn.
**Depends on**: Phase 2. Giữ thông báo và đối chiếu Identity/Product ở biên; không triển khai các hệ thống phụ thuộc.
**Requirements**: ORD-07, ORD-08, PAY-05, PAY-06, FIN-02, FIN-03
**Success Criteria** (what must be TRUE):
  1. Seller giao hoặc từ chối đúng trạng thái. Khách hủy trước PACKING, xác nhận từ DELIVERED; J3/J4 không nhân đôi hiệu ứng (ORD-07).
  2. Bằng chứng không hợp lệ lúc thu chỉ ảnh hưởng đúng đơn; ban sau thu hoặc lỗi nguồn không tự hủy (ORD-08).
  3. Refund ORDER chỉ hoàn số của đơn; timeout tra cứu cùng lần thử; khách đúng chủ mở một chu kỳ mới khi hết lượt (PAY-05, PAY-06).
  4. Finance xử lý quyết toán hoặc hoàn đúng đơn kể cả event đến sai thứ tự; không ghi trùng/sai tiền (FIN-02, FIN-03).
**Plans**: 7 kế hoạch đã thực thi và kiểm chứng theo 4A.
**Wave 1**
- [x] 03-01-PLAN.md — Xác minh baseline và đối chiếu hợp đồng.

**Wave 2** *(blocked on Wave 1 completion)*
- [x] 03-02-PLAN.md — Hủy, giao, xác nhận, chi tiết đơn và J3/J4.
- [x] 03-04-PLAN.md — Refund ORDER và bảo vệ giới hạn trên Payment.
- [x] 03-06-PLAN.md — Quyết toán và hoàn theo từng đơn trong Finance.

**Wave 3** *(blocked on Wave 2 completion)*
- [x] 03-03-PLAN.md — Bằng chứng lúc thu và hủy đúng đơn, sau 03-02.
- [x] 03-05-PLAN.md — Customer mở lại chu kỳ hoàn, sau 03-04.

**Wave 4** *(blocked on Wave 3 completion)*
- [x] 03-07-PLAN.md — Nghiệm thu Phase 3 và hồi quy Phase 2.

### Phase 4: Tra cứu, ví và đối soát
**Goal**: Khách và quản trị tra cứu được trạng thái, Seller rút được tiền khả dụng và Finance đối chiếu được sổ tiền.
**Depends on**: Phase 3. Đối soát đọc các nguồn do Order/Payment sở hữu.
**Requirements**: ORD-09, PAY-07, FIN-04, FIN-05
**Success Criteria** (what must be TRUE):
  1. Customer và Seller chỉ tra đúng chủ, Admin xem theo quyền và không can thiệp vào kết quả hoàn (ORD-09, PAY-07).
  2. Hai yêu cầu rút đồng thời không tạo số dư âm hay ghi ledger lặp; giữ ngoại lệ rút số dư nhỏ đã duyệt (FIN-04).
  3. Thiếu nguồn đối soát được báo không đủ dữ liệu; chênh lệch không tự sửa sổ (FIN-05).
**Plans**: 7/7 plans executed
**Wave 1**
- [x] 04-01-PLAN.md — Đối chiếu AS-IS và đồng bộ hợp đồng Phase 4.

**Wave 2** *(blocked on Wave 1 completion)*
- [x] 04-02-PLAN.md — Tra cứu đơn Customer, Seller và Admin.
- [x] 04-03-PLAN.md — Tra cứu Payment Customer và Admin issues.
- [x] 04-04-PLAN.md — Ví Seller, rút tiền và biên nhận bền vững.

**Wave 3** *(blocked on Wave 2 completion)*
- [x] 04-05-PLAN.md — Ghi chú đối soát khoản thu và audit.
- [x] 04-06-PLAN.md — Ba nguồn đối soát và tra cứu Admin Finance.

**Wave 4** *(blocked on Wave 3 completion)*
- [x] 04-07-PLAN.md — Kiểm chứng ba service và các biên thật.

### Phase 5: Biên CART và nghĩa vụ đang xử lý
**Goal**: Ba service cung cấp đúng phần tích hợp do mình sở hữu khi Cart và Identity kết nối.
**Depends on**: Phase 2 và Phase 3 cho các nhánh tương ứng. CART có thể chuẩn bị sau Phase 1 khi DELTA-02 đã chốt, và kiểm chứng vòng mua hàng sau Phase 2. Nghĩa vụ dùng trạng thái thu/hoàn từ Phase 2 và Refund ORDER, Settlement từ Phase 3. Identity tự sở hữu J7, login và refresh. Hai nhánh có thể lập nhiệm vụ độc lập và không cần chờ Phase 4.
**Requirements**: ORD-10, ORD-11, PAY-08, FIN-06
**Success Criteria** (what must be TRUE):
  1. Checkout CART chỉ đọc dòng thuộc chủ giỏ; checkout.paid đủ dữ liệu đã chốt để Cart áp dụng (ORD-10).
  2. Identity nhận được nghĩa vụ Order/Payment/Refund còn hiệu lực từ đúng bên sở hữu, kể cả FULL_CHECKOUT (ORD-11, PAY-08).
  3. API Finance phân biệt không có nghĩa vụ với nguồn lỗi; phản ánh pending, available và hiệu ứng chưa áp dụng (FIN-06).
**Plans**: TBD

## Progress

| Phase | Plans Complete | Status | Completed |
| --- | --- | --- | --- |
| 1. Giữ hàng và tạo checkout BUY_NOW | 9/9 | Complete (3A và E1) | 2026-11-22 |
| 2. Thanh toán, hết hạn và tiền chờ | 9/9 | Complete    | 2026-11-22 |
| 3. Giao hàng, hủy đơn và quyết toán | 7/7 | Complete    | 2026-11-24 |
| 4. Tra cứu, ví và đối soát | 7/7 | In Progress | — |
| 5. Biên CART và nghĩa vụ đang xử lý | 0/TBD | Not started | — |

Điều khoản nguồn tại [Requirements](REQUIREMENTS.md). Điều kiện tích hợp chưa chốt tại [báo cáo xung đột](intel/INGEST-CONFLICTS.md).
