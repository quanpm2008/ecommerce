# Giỏ, Mua ngay, checkout và vòng đời đơn

Baseline: SAD v1.1 §7.1/D.1, được người dùng chấp nhận ngày 11/11/2026.

Đồng bộ phần quyền bị cấm và invalid-after-capture ngày 18/11/2026 theo [Thẻ 15](coverage.md#the-15), [FRD UC-B03/B06–10/D02](FRD.md), [API §7.1](api_specs.md), [Database §8.2B/D](database.md). Các phần còn lại giữ baseline; chưa xác minh runtime.
[Quy ước/thuật ngữ](README.md) · [Coverage và câu hỏi](coverage.md) · [Kỹ thuật/validation](../engineering.md).

Giới hạn giỏ/Mua ngay, lý do hủy/từ chối, cảnh báo deadline và thử lại hoàn tiền/email theo [tham số đã chốt](parameters.md) từ Thẻ 16. Giữ baseline quote/pay và local transaction; các khoảng chờ thử lại không thay lịch chạy job hay timeout.

Đọc đúng UC dưới đây, quy tắc bổ sung và hàng AC liên quan; chỉ mở mục contract nguồn khi cần DTO/cột/endpoint chi tiết. Yêu cầu TO-BE không chứng minh code đã làm đúng.

[UC-B03](#uc-b03) · [UC-B04](#uc-b04) · [UC-B06](#uc-b06) · [UC-B07](#uc-b07) · [UC-B08](#uc-b08) · [UC-B09](#uc-b09) · [UC-B10](#uc-b10) · [UC-D02](#uc-d02) · [UC-G03](#uc-g03)

## Phạm vi sửa và bằng chứng hiện trạng

Khảo sát tĩnh ngày 11/11/2026; mọi test nêu dưới đây **chưa chạy**.

| Phạm vi | Code owner hiện có | Test đã tìm thấy | Giới hạn |
|---|---|---|---|
| Cart B03 | [cart-service/src/main/java/com/example/cart_service/CartServiceApplication.java](../../cart-service/src/main/java/com/example/cart_service/CartServiceApplication.java) | [cart-service/src/test/java/com/example/cart_service/CartServiceApplicationTests.java](../../cart-service/src/test/java/com/example/cart_service/CartServiceApplicationTests.java) | Chỉ scaffold, chưa có Cart/schema/consumer. |
| Order/Inventory B04/B06–10/D02/G03 | [order-service/src/main/java/com/example/order_service/OrderServiceApplication.java](../../order-service/src/main/java/com/example/order_service/OrderServiceApplication.java) | [order-service/src/test/java/com/example/order_service/OrderServiceApplicationTests.java](../../order-service/src/test/java/com/example/order_service/OrderServiceApplicationTests.java) | Chỉ scaffold; chưa tìm thấy endpoint checkout, kho, state machine/job/migration. |

Khi lập task, lần theo caller/consumer và artifacts trong cùng hàng, rồi xem dependency liên service dưới đây; không đổi các source gốc để che lệch implementation.

## Contract và dữ liệu cần tra cứu

- [§3.3/3.6, §8.2A/B/H](../../business/database.md): field, required/conditional, unique/FK/CHECK và ownership. Bảng nguồn là thiết kế, không khẳng định migration tồn tại.
- [§7.1–7.3](../../business/api.md): provider/consumer allowlist, event payload và jobs. [§7.2](software-architecture.md): DELTA cần review.
- Public paths bên dưới là phần sau `/api/v1`; internal dùng `/internal/v1`. Base URL mẫu trong nguồn không chứng minh cổng service hiện tại. Tên path là khóa contract ổn định; quyền ở bảng vẫn phải kết hợp trạng thái/ownership.
- **Bảng contract nguồn, chưa phải bằng chứng triển khai.** Endpoint Should vẫn giữ tham chiếu; luồng checkout cũ và stats/dashboard bị baseline hiệu chỉnh không được triển khai nguyên xi. DTO, body/error examples và required/optional giữ tại đúng mục nguồn, không suy ra từ tên endpoint.

[§6.3 / danh sách endpoint](../../business/api.md), L1562–1571

| \# | Method | Path | Mô tả | Quyền | UC |
| :---- | :---- | :---- | :---- | :---- | :---- |
| 1 | GET | /cart | Xem giỏ hàng | cart:manage:own | B03 |
| 2 | GET | /cart/summary | Số lượng trên icon giỏ | cart:manage:own | B03 |
| 3 | POST | /cart/items | Thêm vào giỏ | cart:manage:own | B03 |
| 4 | PATCH | /cart/items/{cartItemId} | Đổi số lượng | cart:manage:own | B03 |
| 5 | DELETE | /cart/items/{cartItemId} | Xóa dòng khỏi giỏ | cart:manage:own | B03 |

[§6.4 / danh sách endpoint](../../business/api.md), L1706–1732

| \# | Method | Path | Mô tả | Quyền | UC |
| :---- | :---- | :---- | :---- | :---- | :---- |
| 1 | POST | /orders/checkout/preview | Xem trước trang Thanh toán | checkout:create:own | B04, B10 |
| 2 | POST | /orders/checkout-sessions | Đặt hàng: tạo phiên thanh toán và N đơn | checkout:create:own | B04, B10 |
| 3 | GET | /orders/checkout-sessions/current | Phiên đang chờ thanh toán (nếu có) | order:read:own | B04, B05 |
| 4 | GET | /orders/checkout-sessions/{checkoutSessionId} | Chi tiết phiên thanh toán | order:read:own | B05, B07 |
| 5 | POST | /orders/checkout-sessions/{checkoutSessionId}/cancel | Hủy cả phiên trước khi thanh toán | order:cancel:own | B08 |
| 6 | GET | /orders/me | Đơn mua của tôi | order:read:own | B07 |
| 7 | GET | /orders/me/{orderId} | Chi tiết đơn (Customer) | order:read:own | B05, B07, B08, B09, C01 |
| 8 | POST | /orders/me/{orderId}/cancel | Khách hủy đơn đã thanh toán | order:cancel:own | B08 |
| 9 | POST | /orders/me/{orderId}/confirm-receipt | Đã nhận hàng | order:confirm:own | B09 |
| 10 | GET | /orders/seller/orders | Đơn của cửa hàng tôi | order:fulfill:own | D02 |
| 11 | GET | /orders/seller/orders/{orderId} | Chi tiết đơn (Seller) | order:fulfill:own | D02, D04 |
| 12 | POST | /orders/seller/orders/{orderId}/start-packing | Bắt đầu đóng gói | order:fulfill:own | D02 |
| 13 | POST | /orders/seller/orders/{orderId}/mark-delivered | Xác nhận đã giao | order:fulfill:own | D02 |
| 14 | POST | /orders/seller/orders/{orderId}/reject | Từ chối giao hàng (Must) | order:fulfill:own | D02, G02 |
| 15 | GET | /orders/seller/inventory | Tồn kho sản phẩm của tôi | inventory:manage:own | D03 |
| 16 | PUT | /orders/seller/inventory/{productId} | Cập nhật tồn kho | inventory:manage:own | D03 |
| 17 | GET | /orders/seller/inventory/{productId}/movements | Lịch sử biến động kho | inventory:manage:own | D03 |
| 18 | GET | /orders/seller/stats | Kết quả kinh doanh theo kỳ | order:fulfill:own | D04 |
| 19 | GET | /orders/seller/dashboard | Khối "Việc cần làm" của Seller | order:fulfill:own | D02, D03 |
| 20 | GET | /orders/admin/orders | Tra cứu đơn toàn hệ thống | operations:read:any | E08, E01 |
| 21 | GET | /orders/admin/orders/{orderId} | Chi tiết đơn (Admin) | operations:read:any | E08 |
| 22 | GET | /orders/admin/stats | Tiến độ đơn và hàng chờ (tổng quan Admin) | operations:read:any | E02 |

## Quy tắc bổ sung và source hiệu lực

| Mã nguồn | Quy tắc đã xác nhận | Nguồn |
|---|---|---|
| <a id="g-03"></a>G-03 | Giá trong đơn là snapshot lúc tạo đơn; sửa giá sau đó không ảnh hưởng đơn đã tạo. | [§8.1](../../business/database.md), L955 |
| <a id="g-04"></a>G-04 | Tồn kho khả dụng = on_hand_quantity − reserved_quantity. Trước thanh toán, phiên hết hạn/thất bại/hủy nhả toàn bộ giữ chỗ của N đơn. Sau thanh toán chung thành công, trừ kho một lần theo từng dòng của mọi đơn. Order hủy hợp lệ chỉ hoàn tồn kho của chính Order đó, một lần. | [§8.1](../../business/database.md), L956 |
| <a id="g-08"></a>G-08 | Mỗi Customer tối đa một CheckoutSession AWAITING_PAYMENT. Một checkout sinh 1 CheckoutSession, tối đa 1 Payment và N Order (N = số Seller, mỗi Seller đúng một Order). Yêu cầu đồng thời và submit lặp không tạo thêm CheckoutSession/Order/Payment. | [§8.1](../../business/database.md), L960 |
| <a id="g-16"></a>G-16 | "Mua ngay" (UC-B10) dùng chung mọi quy tắc của checkout từ giỏ (đăng nhập, SĐT, địa chỉ, giữ hàng, snapshot giá, G-08, G-09, G-14). Khác biệt duy nhất: checkout_source = BUY_NOW, chỉ 1 sản phẩm với số lượng chọn trên trang sản phẩm (1–99 và không vượt tồn kho khả dụng), và giỏ không bị đọc, thêm hay trừ ở bất kỳ bước nào. | [Tham số P-10](parameters.md); baseline [§8.1](../../business/database.md), L968 |

Quy tắc chung G-09/G-11 ở [engineering](../engineering.md); G-10/G-13/G-15 ở [quy ước chung](README.md).

<a id="obligation-access"></a>
### Quyền giới hạn theo nghĩa vụ hiện tại

Không dùng lịch sử từng thanh toán để cấp quyền. Order AWAITING_PAYMENT/PAID/PACKING/DELIVERED còn nghĩa vụ; CONFIRMED/CANCELLED/PAYMENT_FAILED/EXPIRED không tự giữ phiên nhưng Payment PENDING, SUCCESS chưa apply, Refund PENDING/FAILED chưa giải quyết (kể cả FULL_CHECKOUT của phiên đóng) vẫn giữ nghĩa vụ. Order tổng hợp đúng owner qua `GET /internal/v1/orders/accounts/{userId}/obligations?accountType=CUSTOMER|SELLER` cho Identity, xác minh Payment/Refund qua API nội bộ, không join DB; nguồn lỗi không có nghĩa là zero.

BANNED Customer chỉ xem đơn/phiên, payment/refund gắn nghĩa vụ của mình, hủy/xác nhận khi state machine cho phép; được mở chu kỳ thử lại Refund khi hết lượt tự động và đủ điều kiện P-RF-01, kể cả accessMode OBLIGATIONS_READ_ONLY. Không giỏ, checkout, thu mới, review hoặc toàn bộ lịch sử terminal. AWAITING_PAYMENT không có nút thanh toán; J1/J2/J2b vẫn chạy. BANNED Seller chỉ xem/xử lý đơn tồn và tiền cần giải quyết, không nhận đơn mới, sửa Product/tồn hay xem thống kê chung. accessMode OPEN_ORDERS_ONLY/ORDERS_AND_WITHDRAWAL không thay kiểm tra ownership/state; chỉ nghĩa vụ hệ thống dùng OBLIGATIONS_READ_ONLY. DISABLED không login; nghĩa vụ đến muộn do Identity/J7 mở lại BANNED giới hạn, không tự unban. J7 không hủy đơn; ban sau thanh toán không đổi state/deadline hay chặn J3/J4/Payment/Refund. Xem [Identity](identity.md) và [Finance](finance.md).

| Mã | Điều khoản | Nguồn / phân loại |
|---|---|---|
| <a id="req-ord-001"></a>REQ-ORD-001 | Preview chỉ đọc, không ghi DB/Order/Reservation. quoteToken có chữ ký gắn Customer, source, hàng/số lượng/giá đã xem, cartItemId khi CART, tổng/hạn/format version; không chứa địa chỉ/PII. TTL và các mã lỗi quote mới thuộc DELTA-01, chưa phải API đã triển khai. | Yêu cầu baseline; [§5.2, ADR-03, DELTA-01](software-architecture.md), L499–520 |
| <a id="req-ord-002"></a>REQ-ORD-002 | Pay xác minh quote và dữ liệu hiện hành, tự tính tiền; không tin amount client. Giá/tập hàng đổi phải Preview và xác nhận lại trước O1/charge. Snapshot Order gồm giá, Seller, hoa hồng chung 5% (500 bps), địa chỉ cùng mã/tên địa giới hai cấp tại lúc đặt và source; không tra lại Category hay sửa đơn cũ khi tỷ lệ đổi. HTTP không tạo snapshot nguyên tử xuyên DB. | Baseline [§5.1–5.2](software-architecture.md), L444–520; [P-COM-01/P-26](parameters.md) |
| <a id="req-ord-003"></a>REQ-ORD-003 | O1 lưu checkout/Order/Reservation và ý định charge cùng local transaction; HTTP sang Payment sau commit. SUCCESS riêng Payment chưa đủ UI thành công: O3 phải áp dụng PAID/kho/Inbox/Outbox trước. paid_at lấy completedAt chính thức, không lấy thời điểm consumer nhận event. | Yêu cầu baseline; [§5.1](software-architecture.md), L444–496 |
| <a id="req-ord-004"></a>REQ-ORD-004 | Inventory chính thức thuộc Order: available=on_hand−reserved, on_hand≥reserved≥0. Giữ q: reserved+q; commit: on_hand−q/reserved−q; đóng trước thu: reserved−q; hủy đơn đã thu: on_hand+q đúng một lần; late success CLOSED không đổi kho. | Yêu cầu baseline; [§6.5](software-architecture.md), L632–664 |
| <a id="req-ord-005"></a>REQ-ORD-005 | CART giữ snapshot cartItemId/product/quantity/rowVersion. checkout.paid giảm min(snapshot.quantity,current.quantity) trên đúng cartItemId còn tồn tại; dòng xóa-thêm lại giữ nguyên; version tăng không tự bỏ toàn bộ giảm. BUY_NOW không đọc/ghi Cart. | Đề xuất kỹ thuật; [A.5 — phương án chi tiết cần contract test](software-architecture.md), L889–906 |
| <a id="req-ord-006"></a>REQ-ORD-006 | CheckoutSession dùng partial unique một AWAITING_PAYMENT/Customer và unique(Customer,key); Order unique(checkout,Seller); source_cart_id/cart_snapshot bắt buộc khi CART, NULL khi BUY_NOW; địa chỉ là reference, không FK xuyên service. | Yêu cầu baseline; [§3.6.1–3.6.2](../../business/database.md), L493–550 |
| <a id="req-ord-007"></a>REQ-ORD-007 | Seller due=paid_at+7 ngày; Customer due=delivered_at của riêng Order+7 ngày. Cảnh báo đơn sắp hết hạn khi thời gian xử lý còn không quá 24 giờ trước hạn 7 ngày (P-36). Không kéo dài deadline do job chạy chậm; không từ DELIVERED/CONFIRMED sang CANCELLED. Hai bộ đếm độc lập, không trạng thái đang giao. | Baseline [§8.2B; AT-26](../../business/database.md), L984–998; [B.2 / AT-26](software-architecture.md); [P-36](parameters.md) |

### Chuyển trạng thái checkout và Order

[Database §8.2A/B](database.md); phần invalid-after-capture theo [Thẻ 15](coverage.md#the-15), cách bắt đầu tạo ở pay áp dụng SAD §5.1. Bảng diễn tả chuyển trạng thái, không định nghĩa endpoint mới.

**A. CheckoutSession** — kết quả thanh toán áp dụng đồng thời cho cả N Order của phiên.

| TỪ | SỰ KIỆN | SANG |
| :---- | :---- | :---- |
| Chưa có | Khách checkout hợp lệ, giữ đủ toàn bộ hàng | AWAITING_PAYMENT; tạo N Order AWAITING_PAYMENT |
| AWAITING_PAYMENT | Payment SUCCESS, toàn bộ kiểm tra hợp lệ | PAID; mọi Order → PAID; ghi số dư chờ cho từng Order (8.3) |
| AWAITING_PAYMENT | Payment FAILED rõ ràng | CLOSED (close_reason = PAYMENT_FAILED); mọi Order → PAYMENT_FAILED; nhả toàn bộ hàng |
| AWAITING_PAYMENT | Hết hạn giữ hàng, không có Payment PENDING (J1), hoặc Payment PENDING quá P-04 (J2b) | CLOSED (close_reason = EXPIRED); mọi Order → EXPIRED; nhả toàn bộ hàng |
| AWAITING_PAYMENT | Khách hủy, không có Payment PENDING | CLOSED (close_reason = CANCELLED_BY_CUSTOMER); mọi Order → CANCELLED; nhả hàng; không hoàn tiền |
| CLOSED | Payment SUCCESS đến muộn | Giữ CLOSED; tạo Refund FULL_CHECKOUT; không chuyển Order nào sang xử lý, không trừ kho, không ghi bút toán ví |

**B. Order** — trạng thái xử lý của từng đơn sau thanh toán. Không có trạng thái "đang giao".

| TỪ | AI / SỰ KIỆN | SANG |
| :---- | :---- | :---- |
| PAID (chờ xử lý) | Seller bắt đầu chuẩn bị hàng | PACKING (đang đóng gói) |
| PAID | Khách hủy hợp lệ (trước khi Seller đóng gói) | CANCELLED → Refund; Settlement PENDING_REFUND |
| PAID hoặc PACKING | Seller chọn "Từ chối giao hàng", nhập lý do | CANCELLED → Refund; Settlement PENDING_REFUND |
| PAID hoặc PACKING | 7 ngày kể từ thanh toán mà Seller chưa xác nhận đã giao/từ chối (J4) | CANCELLED → Refund; Settlement PENDING_REFUND |
| PAID hoặc PACKING | Order Service khi áp dụng Payment SUCCESS qua payment.succeeded hoặc đối soát dùng chung handler, chứng minh Seller/Product không hợp lệ tại thời điểm giao dịch/thu tiền; không phải ban sau capture | CANCELLED riêng Order đủ điều kiện → Refund; Settlement PENDING_REFUND; Payment giữ SUCCESS |
| PACKING | Seller chọn "Xác nhận đã giao" trước hạn 7 ngày | DELIVERED (hiển thị: Đã giao — chờ khách xác nhận) |
| DELIVERED | Khách chọn "Đã nhận hàng", hoặc 7 ngày kể từ delivered_at khách không phản hồi (J3) | CONFIRMED → Settlement SETTLED; số dư chờ → khả dụng |

Mốc 7 ngày thứ nhất: từ paid_at (thời điểm thanh toán chung) đến hạn Seller xác nhận đã giao hoặc từ chối (seller_action_due_at). Mốc thứ hai: từ delivered_at của riêng Order đến hạn khách xác nhận (customer_confirm_due_at). Hai bộ đếm độc lập, tính riêng từng Order. Không có chuyển trạng thái từ DELIVERED hoặc CONFIRMED sang CANCELLED — đây là cơ sở để tiền khả dụng không bao giờ phải thu hồi. CONFIRMED, CANCELLED, PAYMENT_FAILED, EXPIRED là trạng thái kết thúc; không tái mở đơn khi nhận thanh toán trễ.

Order xác minh điều kiện giao dịch qua Identity/Product ngoài transaction giữ khóa theo [SAD §5.1](software-architecture.md). Không suy ra invalid tại capture chỉ từ trạng thái Seller hiện tại khi event đến trễ. Thiếu bằng chứng hoặc lỗi nguồn giữ công việc bền vững để xác minh/retry, không hủy/hoàn vì timeout. Áp dụng đủ checkout.paid/order.cancelled để Payment tạo Refund và Finance giữ pending/PENDING_REFUND đến Refund SUCCESS; replay không nhân đôi SOLD/RESTOCK/Refund/ledger. Không quét hủy mọi đơn Seller và không hủy DELIVERED/CONFIRMED; J2/J5/J7 không sở hữu quyết định invalid này.

## Acceptance criteria để triển khai/kiểm chứng

Các AC sau là diễn giải kiểm chứng từ điều khoản cùng mã UC/REQ/G/WD; chưa là kết quả test. Tham số TBD chỉ kiểm tra biên sau khi giá trị được chốt. Mỗi dòng gộp một tình huống; triển khai test phải tách assertions và cả nhánh lỗi của UC.

| Mã AC | Liên kết yêu cầu | Given | When | Then |
|---|---|---|---|---|
| <a id="ac-ord-001"></a>AC-ORD-001 | UC-B03; G-16; REQ-ORD-005 | Cart đổi số lượng hoặc xóa-thêm lại sau snapshot; dòng có 98/99 đơn vị, giỏ có 49/50 dòng | Thêm cùng sản phẩm, thêm sản phẩm khác, đổi số lượng; replay checkout.paid rồi checkout BUY_NOW | Mỗi dòng 1–99, tối đa 50 sản phẩm khác nhau; thêm cùng sản phẩm cộng vào dòng hiện có nếu không vượt 99, không tạo dòng mới. Từ chối đơn vị thứ 100 hoặc dòng thứ 51. Giảm đúng cartItemId/lượng snapshot còn tồn tại một lần; giữ dòng mới/tăng thêm; BUY_NOW không đọc/đổi Cart; failure không giảm; checkout vẫn kiểm tra tồn thực tế. |
| <a id="ac-ord-002"></a>AC-ORD-002 | UC-B04; G-08/09 | 3 Seller, tất cả dòng hợp lệ, Customer đủ điều kiện | Pay đồng thời cùng key/khác key; thử thiếu một dòng | Một checkout, 3 Order, ≤1 Payment; thiếu dòng rollback toàn O1; hash khác cùng key 409; không tạo phiên chờ thứ hai. |
| <a id="ac-ord-003"></a>AC-ORD-003 | UC-B06; REQ-ORD-003; Thẻ 15 O1–O3 | Payment SUCCESS; một Order invalid được chứng minh, Order Seller khác hợp lệ; hoặc Seller ban sau capture/event trễ; hoặc Identity/Product lỗi; hoặc checkout CLOSED | Order nhận payment.succeeded/đối soát chung handler, poll/replay | Payment giữ SUCCESS; chỉ invalid Order PAID/PACKING hủy/hoàn, đủ checkout.paid/order.cancelled và pending đến Refund SUCCESS; đơn khác tiếp tục. Ban sau capture không tự hủy, không suy invalid từ status hiện tại; lỗi/thiếu bằng chứng retry bền vững. O3 chưa PAID UI đang xác nhận; SOLD/RESTOCK/Refund/ledger đúng một lần. CLOSED chỉ FULL_CHECKOUT, không mở lại/kho/Settlement. |
| <a id="ac-ord-004"></a>AC-ORD-004 | UC-B07; Thẻ 15 B1–B3 | Hai Seller cùng checkout, Customer khác; BANNED có PAID/DELIVERED, chỉ CONFIRMED, hoặc CANCELLED + Refund FAILED | List/detail và hủy/xác nhận bằng phiên giới hạn | Owner chỉ thấy nghĩa vụ của mình, không toàn bộ lịch sử/giỏ/pay/review; PAID được hủy và DELIVERED xác nhận theo rule; Refund FAILED vẫn xem nghĩa vụ đến khi giải quyết, chỉ CONFIRMED không giữ phiên. N Order độc lập/liên kết checkout; UUID không orderCode/trackingNumber; Payment PENDING khóa pay/cancel. |
| <a id="ac-ord-005"></a>AC-ORD-005 | UC-B08; UC-D02; P-21a/P-21b | Checkout chưa thu hoặc Order PAID/PACKING; lý do hợp lệ, ghi chú rỗng/500/501 ký tự | Customer hủy hoặc Seller từ chối lặp, tranh start-packing/J4 | Chưa thu hủy cả phiên nếu không PENDING; Customer chỉ hủy PAID; giữ đúng enum P-21a/P-21b. Ghi chú tối đa 500 ký tự; Customer OTHER bắt buộc ghi chú, Seller luôn bắt buộc; thiếu/vượt giới hạn không hủy đơn. Đúng một RESTOCK/nghĩa vụ refund, không đổi đơn khác. |
| <a id="ac-ord-006"></a>AC-ORD-006 | UC-B09; UC-D02; UC-G03; REQ-ORD-007 | Order PACKING/DELIVERED và hai due_at độc lập; thời gian xử lý còn 24 giờ hoặc 24 giờ +1 giây | Seller xem cảnh báo, giao/từ chối cạnh J4; Customer xác nhận cạnh J3 | Cảnh báo sắp hết hạn tại còn đúng 24 giờ trở xuống, chưa cảnh báo tại 24 giờ +1 giây; không đổi hạn 7 ngày. Một chuyển hợp lệ; sau hạn Seller không lách do job chậm; J3 một CONFIRMED; không hủy DELIVERED/CONFIRMED; một settlement release. |
| <a id="ac-ord-007"></a>AC-ORD-007 | UC-B10; G-16; P-10 | Cart có X×3; BUY_NOW chọn 1, 99 hoặc 100 đơn vị, tồn đủ hoặc thiếu | Preview/rời trang, rồi xác nhận pay | Chỉ cho 1–99 và không vượt tồn thực tế; 100 bị từ chối dù đủ tồn, không tạo dòng giỏ. Preview không ghi/giữ; pay một OrderItem; Cart vẫn X×3; chưa login quay lại rồi yêu cầu bấm tiếp, không tự đặt đơn. |
| <a id="ac-ord-008"></a>AC-ORD-008 | UC-G03; P-RF-01/P-19 | Refund ORDER/FULL_CHECKOUT lỗi rõ hoặc PENDING/timeout; email lỗi tạm thời/vĩnh viễn/token hết hạn | J5/J6 tra cứu, thử lại; Customer bấm thử lại Refund sau hết lượt | Mỗi chu kỳ hoàn và mỗi email có một lần đầu, tối đa 3 lần thử lại chờ 1/5/15 phút từ lần lỗi trước; Refund chưa rõ chỉ tra cứu, không tiêu lượt/gửi lệnh hoàn mới. Hết lượt Refund thông báo khách, chỉ khách đúng chủ mở chu kỳ mới cùng Refund/số tiền/khóa chống trùng, đặt lại bộ đếm giữ lịch sử; Admin không làm thay. Email tôn trọng Retry-After, dừng lỗi vĩnh viễn/token hết hạn hoặc hết lượt, giữ cùng thông báo/khóa chống trùng và không đổi trạng thái nghiệp vụ. Chỉ Refund SUCCESS mới ghi đã hoàn/giảm pending. |

### AT nguồn thuộc hồ sơ này

Các AT bao phủ nhiều service; hồ sơ này là đầu mối đọc, không có nghĩa chỉ sửa một module. Đọc owner khác từ coverage khi task chạm biên. **Chưa có bằng chứng chạy AT.**

| Mã | Tình huống | Bằng chứng cần có | Nguồn |
|---|---|---|---|
| <a id="at-01"></a>AT-01 | 3 Seller, nhiều sản phẩm | 1 checkout, 3 Order, 1 Payment; tổng tiền khớp; Seller chỉ thấy đơn mình | [B.2](software-architecture.md), L982 |
| <a id="at-02"></a>AT-02 | Preview nhiều lần/rời trang | Không Order/Reservation/Payment; có quote hợp lệ, không giữ hàng | [B.2](software-architecture.md), L983 |
| <a id="at-03"></a>AT-03 | Giá đổi sau Preview, quote bị sửa/hết hạn | Bị chặn trước O1/charge; Preview và xác nhận lại; không tin amount client | [B.2](software-architecture.md), L984 |
| <a id="at-04"></a>AT-04 | Double click/cùng key/khác payload | Một workflow; hash khác trả 409; khác key đồng thời vẫn tối đa một phiên chờ | [B.2](software-architecture.md), L985 |
| <a id="at-05"></a>AT-05 | Hai Customer mua lượng tồn cuối | Không oversell; bên thiếu rollback toàn bộ các dòng | [B.2](software-architecture.md), L986 |
| <a id="at-06"></a>AT-06 | Crash sau O1, trước gọi Payment | Công việc tiếp tục sau restart; một Payment logic | [B.2](software-architecture.md), L987 |
| <a id="at-08"></a>AT-08 | SUCCESS đã lưu nhưng event/ack mất | Replay/đối soát áp dụng một SOLD/Order; UI chỉ thành công sau Order PAID | [B.2](software-architecture.md), L989 |
| <a id="at-12"></a>AT-12 | Customer hủy tranh PACKING/J4/Seller từ chối | Một chuyển hợp lệ; đúng RESTOCK và nghĩa vụ Refund một lần | [B.2](software-architecture.md), L993 |
| <a id="at-16"></a>AT-16 | Customer xác nhận cùng J3 | Một CONFIRMED và một SETTLEMENT_RELEASED; due_at đúng 7 ngày | [B.2](software-architecture.md), L997 |
| <a id="at-19"></a>AT-19 | Giỏ thay đổi/xóa-thêm lại, BUY_NOW | Giảm đúng dòng snapshot còn tồn tại; giữ dòng mới; BUY_NOW không gọi/đổi Cart | [B.2](software-architecture.md), L1000 |
| <a id="at-26"></a>AT-26 | Hai deadline 7 ngày, sát ranh giới | Seller hết hạn không giao lách do job chậm; Customer auto-confirm đúng mốc từng đơn | [B.2](software-architecture.md), L1007 |

## Chênh lệch, đề xuất và câu hỏi còn mở

- **DELTA-01/02/03/04/05:** quote/pay, Cart snapshot internal, Payment charge, close-expired và durable work chưa đồng bộ contract. Đường dẫn xuất hiện trong SAD là thiết kế/đề xuất, chưa phải endpoint chạy được.
- Bảng API nguồn còn create-session rồi FE gọi Payment, cùng endpoint stats/dashboard; khi sửa phải dùng baseline §5.1/ADR-05 và review delta, không triển khai nguyên luồng cũ.
- Nguồn FRD B04 có “mã riêng” nhưng B07/API C-06 đã bỏ mã hiển thị; dùng UUID. B10 dẫn nhầm DB §3.1.3, quy tắc địa chỉ nằm §3.1.4.
- P-10, P-21a/P-21b, P-36, P-RF-01 và P-19 đã chốt tại [parameters.md](parameters.md). P-01 vẫn tạm 15 phút, chưa chốt; N-06, quote TTL, timeout/lease và cấu hình kỹ thuật chưa được quyết định riêng vẫn còn mở. Hai mốc 7 ngày đã chốt; lịch chạy job không phải khoảng chờ thử lại 1/5/15 phút.

## Đặc tả UC và ngoại lệ


<a id="uc-b03"></a>
## UC-B03 — Quản lý giỏ hàng

Nguồn: [§6 / UC-B03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L561–594. Vai trò: Khách hàng (đã đăng nhập) · Ưu tiên: M.

Các điều khoản dưới đây là yêu cầu nguồn đã đối chiếu baseline; nhánh Google/in-app ghi Should không thuộc DoD Must. Mã clause giữ ổn định khi di chuyển tài liệu.

| Mã điều khoản | Loại | Yêu cầu / điều kiện / ngoại lệ | Nguồn hiệu lực |
|---|---|---|---|
| <a id="uc-b03.goal-01"></a>UC-B03.GOAL-01 | Mục tiêu | Khách chọn và điều chỉnh các sản phẩm trước khi đặt hàng. | [UC-B03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L565 |
| <a id="uc-b03.pre-01"></a>UC-B03.PRE-01 | Điều kiện | Đã đăng nhập. | [UC-B03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L567 |
| <a id="uc-b03.main-01"></a>UC-B03.MAIN-01 | Luồng chính | Khách thêm hàng vào giỏ và điều chỉnh số lượng 1–99 mỗi dòng, tối đa 50 dòng sản phẩm khác nhau. Thêm lại cùng sản phẩm thì cộng vào dòng hiện có nếu tổng không vượt 99; vượt giới hạn bị từ chối, không tạo dòng thứ hai cho cùng sản phẩm. Checkout vẫn kiểm tra tồn kho thực tế. | [FRD UC-B03](FRD.md); [Tham số P-10](parameters.md) |
| <a id="uc-b03.main-02"></a>UC-B03.MAIN-02 | Luồng chính | Hệ thống hiển thị giá, số lượng có thể mua và nhóm các dòng theo từng cửa hàng; mỗi nhóm sẽ trở thành một đơn riêng khi đặt hàng. | [UC-B03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L573 |
| <a id="uc-b03.main-03"></a>UC-B03.MAIN-03 | Luồng chính | Khách chọn các sản phẩm cần mua và bấm “Thanh toán”. | [UC-B03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L575 |
| <a id="uc-b03.main-04"></a>UC-B03.MAIN-04 | Luồng chính | Khi lần thanh toán chung thành công, hệ thống giảm đúng các sản phẩm đã mua trong giỏ, kể cả nếu sau checkout khách đã tăng số lượng. | [UC-B03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L577 |
| <a id="uc-b03.alt-01"></a>UC-B03.ALT-01 | Ngoại lệ | Không đủ/hết hàng: không cho chọn để thanh toán; khách điều chỉnh lại số lượng. | [UC-B03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L581 |
| <a id="uc-b03.alt-02"></a>UC-B03.ALT-02 | Ngoại lệ | Sản phẩm ngừng bán: bị loại khi mở giỏ và có thông báo. | [UC-B03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L583 |
| <a id="uc-b03.alt-03"></a>UC-B03.ALT-03 | Ngoại lệ | Giá thay đổi: dùng giá tại lúc xác nhận đặt hàng. | [UC-B03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L585 |
| <a id="uc-b03.alt-04"></a>UC-B03.ALT-04 | Ngoại lệ | Thanh toán thất bại, chưa rõ hoặc hết hạn: không xóa các mặt hàng đã chọn. | [UC-B03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L587 |
| <a id="uc-b03.alt-05"></a>UC-B03.ALT-05 | Ngoại lệ | Đơn tạo qua “Mua ngay” (UC-B10): giỏ không bị đọc hay trừ, kể cả khi giỏ đang chứa cùng sản phẩm. | [UC-B03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L589 |
| <a id="uc-b03.alt-06"></a>UC-B03.ALT-06 | Ngoại lệ | Customer BANNED không xem/quản lý giỏ hay thanh toán mới, bất kể đã từng trả tiền; chỉ quyền nghĩa vụ tại mục obligation-access. | [FRD UC-B03/E03](FRD.md); [Thẻ 15](coverage.md#the-15) |
| <a id="uc-b03.post-01"></a>UC-B03.POST-01 | Hậu điều kiện | Một giỏ nhiều cửa hàng được thanh toán một lần và tách thành các đơn riêng theo cửa hàng; giỏ không bị trừ lại khi nhận xác nhận thanh toán trùng. | [UC-B03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L593 |

<a id="uc-b04"></a>
## UC-B04 — Đặt hàng và tách đơn theo cửa hàng

Nguồn: [§6 / UC-B04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L595–632. Vai trò: Khách hàng · Ưu tiên: M.

Các điều khoản dưới đây là yêu cầu nguồn đã đối chiếu baseline; nhánh Google/in-app ghi Should không thuộc DoD Must. Mã clause giữ ổn định khi di chuyển tài liệu.

| Mã điều khoản | Loại | Yêu cầu / điều kiện / ngoại lệ | Nguồn hiệu lực |
|---|---|---|---|
| <a id="uc-b04.goal-01"></a>UC-B04.GOAL-01 | Mục tiêu | Khách đặt hàng các sản phẩm thuộc một hoặc nhiều cửa hàng; hệ thống tách thành một đơn riêng cho mỗi cửa hàng. | [UC-B04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L599 |
| <a id="uc-b04.pre-01"></a>UC-B04.PRE-01 | Điều kiện | Đăng nhập, có số điện thoại, có địa chỉ nhận hàng và chọn tối thiểu một sản phẩm còn đủ hàng. | [UC-B04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L601 |
| <a id="uc-b04.main-01"></a>UC-B04.MAIN-01 | Luồng chính | Khách chọn “Mua hàng” từ giỏ (hoặc “Mua ngay” từ trang sản phẩm — UC-B10); hệ thống hiển thị sản phẩm chia theo từng cửa hàng, mỗi cửa hàng tương ứng một đơn dự kiến. | [UC-B04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L605 |
| <a id="uc-b04.main-02"></a>UC-B04.MAIN-02 | Luồng chính | Hệ thống hiển thị tổng tiền từng đơn, tổng tiền cần thanh toán (tổng các đơn) và phí vận chuyển bằng 0. | [UC-B04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L607 |
| <a id="uc-b04.main-03"></a>UC-B04.MAIN-03 | Luồng chính | Khách chọn một địa chỉ cho toàn bộ N đơn và xác nhận Thanh toán. Preview chỉ đọc; bước pay mới tạo checkout, giữ tồn và lưu ý định charge. | [UC-B04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L609; **baseline thay thế/hiệu chỉnh:** [§5.1](software-architecture.md), L444–496 |
| <a id="uc-b04.main-04"></a>UC-B04.MAIN-04 | Luồng chính | Hệ thống kiểm tra lại giá, người bán, khả năng bán hàng và tồn kho của tất cả sản phẩm cùng lúc. | [UC-B04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L611 |
| <a id="uc-b04.main-05"></a>UC-B04.MAIN-05 | Luồng chính | Khi pay hợp lệ, local transaction O1 của Order giữ đủ tất cả dòng, tạo một CheckoutSession, N Order theo Seller và lưu ý định gọi Payment bền vững; thiếu một dòng rollback toàn bộ. | [UC-B04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L613; **baseline thay thế/hiệu chỉnh:** [§5.1](software-architecture.md), L444–496 |
| <a id="uc-b04.main-06"></a>UC-B04.MAIN-06 | Luồng chính | Order chưa PAID thì UI hiển thị đang xác nhận; chỉ báo đặt hàng thành công sau O3 áp dụng PAID. Không triển khai luồng tạo phiên công khai rồi browser tự gọi charge cũ như baseline mới. | [UC-B04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L615; **baseline thay thế/hiệu chỉnh:** [§5.1, DELTA-01/03](software-architecture.md), L444–496 |
| <a id="uc-b04.alt-01"></a>UC-B04.ALT-01 | Ngoại lệ | Thiếu địa chỉ/số điện thoại: yêu cầu hoàn thiện hồ sơ. | [UC-B04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L619 |
| <a id="uc-b04.alt-02"></a>UC-B04.ALT-02 | Ngoại lệ | Một mặt hàng hết hàng hoặc không hợp lệ: không tạo bất kỳ đơn nào (không tách một phần); thông báo mặt hàng cần sửa. | [UC-B04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L621 |
| <a id="uc-b04.alt-03"></a>UC-B04.ALT-03 | Ngoại lệ | Đã có phiên checkout chờ thanh toán: hướng dẫn thanh toán hoặc hủy phiên cũ trước. | [UC-B04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L623 |
| <a id="uc-b04.alt-04"></a>UC-B04.ALT-04 | Ngoại lệ | Gửi lại cùng yêu cầu: trả về cùng phiên thanh toán và các đơn đã tạo; thay nội dung nhưng dùng lại khóa xác nhận: từ chối xung đột (409). | [UC-B04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L625 |
| <a id="uc-b04.alt-05"></a>UC-B04.ALT-05 | Ngoại lệ | Hai khách tranh món cuối: chỉ một người giữ thành công. | [UC-B04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L627 |
| <a id="uc-b04.post-01"></a>UC-B04.POST-01 | Hậu điều kiện | Một checkout có một CheckoutSession, N Order theo N Seller và tối đa một Payment; Order được định danh bằng orderId, không có mã hiển thị riêng. | [UC-B04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L629; **baseline thay thế/hiệu chỉnh:** [§5.1; API C-06](software-architecture.md), L444–496; [§9 / C-06](../../business/api.md), L2883 |
| <a id="uc-b04.note-01"></a>UC-B04.NOTE-01 | Ràng buộc | Không cho thanh toán riêng từng đơn trong cùng một lần checkout; không hủy lẻ một đơn trước khi thanh toán (khách hủy cả phiên rồi đặt lại). | [UC-B04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L631 |

<a id="uc-b06"></a>
## UC-B06 — Xác nhận đơn sau thanh toán

Nguồn hiệu lực: [FRD §6 / UC-B06](FRD.md), [Database §8.2B](database.md), [SAD §5.1](software-architecture.md), [Thẻ 15](coverage.md#the-15). Vai trò: Hệ thống · Ưu tiên: M.

Các điều khoản dưới đây là yêu cầu nguồn đã đối chiếu baseline; nhánh Google/in-app ghi Should không thuộc DoD Must. Mã clause giữ ổn định khi di chuyển tài liệu.

| Mã điều khoản | Loại | Yêu cầu / điều kiện / ngoại lệ | Nguồn hiệu lực |
|---|---|---|---|
| <a id="uc-b06.goal-01"></a>UC-B06.GOAL-01 | Mục tiêu | Ghi nhận thanh toán chung thành công, chuyển từng đơn cho đúng cửa hàng xử lý và ghi số dư chờ quyết toán. | [UC-B06](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L675 |
| <a id="uc-b06.pre-01"></a>UC-B06.PRE-01 | Điều kiện | Hệ thống nhận được xác nhận thanh toán hợp lệ cho phiên checkout. | [UC-B06](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L677 |
| <a id="uc-b06.main-01"></a>UC-B06.MAIN-01 | Luồng chính | Hệ thống kiểm tra lại tình trạng phiên, các đơn, sản phẩm và hàng đã giữ. | [UC-B06](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L681 |
| <a id="uc-b06.main-02"></a>UC-B06.MAIN-02 | Luồng chính | Hệ thống trừ số hàng đã mua đúng một lần, ghi nhận phiên đã thanh toán, chuyển từng Order sang “Chờ xử lý” và đặt hạn 7 ngày cho Seller của từng đơn. | [UC-B06](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L683 |
| <a id="uc-b06.main-03"></a>UC-B06.MAIN-03 | Luồng chính | Hệ thống tạo Settlement “chờ quyết toán” cho từng Order theo tổng tiền của đơn và ghi bút toán PAYMENT_RECEIVED: Sᵢ vào số dư chờ của ví Seller, Cᵢ vào số dư chờ của ví nền tảng (Database RBAC v5.0 mục 8.3). Số dư chờ hiển thị cho Seller nhưng chưa được rút. | [UC-B06](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L685 |
| <a id="uc-b06.main-04"></a>UC-B06.MAIN-04 | Luồng chính | Nếu nguồn_checkout = giỏ_hàng, hệ thống giảm phần đã mua trong giỏ (mua_ngay: không động tới giỏ); gửi khách một thông báo thanh toán liệt kê các đơn theo cửa hàng và gửi mỗi Seller thông báo về đơn mới của mình. | [UC-B06](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L687 |
| <a id="uc-b06.alt-01"></a>UC-B06.ALT-01 | Ngoại lệ | Order khi áp dụng Payment SUCCESS qua payment.succeeded/đối soát chung handler xác minh điều kiện giao dịch qua Identity/Product ngoài transaction giữ khóa. Chứng minh invalid tại thu tiền thì chỉ hủy Order PAID/PACKING đủ điều kiện, hoàn kho/tiền đúng Order; Payment giữ SUCCESS, đủ checkout.paid/order.cancelled; Finance giữ pending/PENDING_REFUND đến Refund SUCCESS. Seller ban sau capture không là lý do hủy; trạng thái hiện tại không chứng minh thời điểm ban. Lỗi nguồn/thiếu bằng chứng giữ retry bền vững, không coi invalid; replay không nhân đôi hiệu ứng. | [FRD UC-B06](FRD.md); [Database §8.2B](database.md); [SAD §5.1](software-architecture.md); [Thẻ 15](coverage.md#the-15) |
| <a id="uc-b06.alt-02"></a>UC-B06.ALT-02 | Ngoại lệ | Thông báo thanh toán thành công đến sau khi phiên đã hết hạn/hủy: hoàn toàn bộ tiền, không khôi phục đơn, không trừ kho, không tạo Settlement hay bút toán ví. | [UC-B06](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L693 |
| <a id="uc-b06.alt-03"></a>UC-B06.ALT-03 | Ngoại lệ | Xác nhận thanh toán trùng hoặc xử lý đồng thời: không tạo thêm khoản thu, trừ kho, Settlement, bút toán ví hay thông báo. | [UC-B06](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L695 |
| <a id="uc-b06.post-01"></a>UC-B06.POST-01 | Hậu điều kiện | Lần checkout đã thanh toán một lần; mỗi Order được theo dõi riêng về hàng hóa và tiền; tiền của Seller nằm ở số dư chờ, chưa rút được ở bước này. | [UC-B06](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L697 |

<a id="uc-b07"></a>
## UC-B07 — Theo dõi trạng thái và lịch sử đơn

Nguồn: [§6 / UC-B07](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L699–728. Vai trò: Khách hàng · Ưu tiên: M.

Các điều khoản dưới đây là yêu cầu nguồn đã đối chiếu baseline; nhánh Google/in-app ghi Should không thuộc DoD Must. Mã clause giữ ổn định khi di chuyển tài liệu.

| Mã điều khoản | Loại | Yêu cầu / điều kiện / ngoại lệ | Nguồn hiệu lực |
|---|---|---|---|
| <a id="uc-b07.goal-01"></a>UC-B07.GOAL-01 | Mục tiêu | Khách theo dõi từng đơn độc lập theo cửa hàng. | [UC-B07](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L703 |
| <a id="uc-b07.pre-01"></a>UC-B07.PRE-01 | Điều kiện | Khách đã đăng nhập và mở “Đơn hàng của tôi”. | [UC-B07](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L705 |
| <a id="uc-b07.main-01"></a>UC-B07.MAIN-01 | Luồng chính | Hệ thống liệt kê mỗi Order một dòng: tên cửa hàng, sản phẩm, tổng tiền đơn, trạng thái; các đơn cùng một phiên thanh toán được liên kết để khách biết chúng được trả cùng lần. Đơn và phiên định danh bằng ID, không có mã hiển thị riêng. | [UC-B07](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L709 |
| <a id="uc-b07.main-02"></a>UC-B07.MAIN-02 | Luồng chính | Khách mở đơn để xem mặt hàng, địa chỉ nhận, trạng thái, lịch sử và khoản hoàn. Khi hết 3 lần thử lại tự động mà Refund vẫn thất bại rõ, thông báo cho khách đã thanh toán và cho khách đúng chủ bấm “Thử lại hoàn tiền” để mở chu kỳ mới cùng Refund/số tiền/khóa chống trùng; không có thao tác Admin làm thay. Khoản FULL_CHECKOUT xem theo phiên thanh toán tương ứng. | [FRD UC-B07](FRD.md); [P-RF-01](parameters.md) |
| <a id="uc-b07.main-03"></a>UC-B07.MAIN-03 | Luồng chính | Đơn ở trạng thái đã_giao hiển thị “Đã giao — chờ khách xác nhận”; khách có thể chọn “Đã nhận hàng”. | [UC-B07](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L713 |
| <a id="uc-b07.main-04"></a>UC-B07.MAIN-04 | Luồng chính | Khách có thể chọn “Hủy đơn” nếu đơn đủ điều kiện và xem số tiền đang hoàn của đơn đó. | [UC-B07](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L715 |
| <a id="uc-b07.alt-01"></a>UC-B07.ALT-01 | Ngoại lệ | Phiên chưa thanh toán: các đơn hiển thị “Chờ thanh toán” với hạn chung và nút thanh toán chung; thanh toán pending: tạm khóa thanh toán/hủy. | [UC-B07](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L719 |
| <a id="uc-b07.alt-02"></a>UC-B07.ALT-02 | Ngoại lệ | Các đơn cùng lần thanh toán ở các trạng thái khác nhau (ví dụ một đơn đã hủy, một đơn đã giao): hiển thị độc lập. | [UC-B07](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L721 |
| <a id="uc-b07.alt-03"></a>UC-B07.ALT-03 | Ngoại lệ | BANNED Customer chỉ xem đơn/phiên và payment/refund còn nghĩa vụ đúng chủ, không toàn bộ lịch sử kết thúc; được hủy/xác nhận nếu state cho phép và thử lại hoàn tiền theo P-RF-01, không thu mới. Refund FAILED của Order CANCELLED vẫn giữ nghĩa vụ; chỉ CONFIRMED không giữ phiên. | [FRD UC-B07/E03](FRD.md); [Thẻ 15](coverage.md#the-15); [P-RF-01](parameters.md) |
| <a id="uc-b07.alt-04"></a>UC-B07.ALT-04 | Ngoại lệ | Không tìm thấy đơn hoặc không có quyền: không hiển thị dữ liệu của người khác. | [UC-B07](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L725 |
| <a id="uc-b07.post-01"></a>UC-B07.POST-01 | Hậu điều kiện | Mỗi cửa hàng là một đơn có trạng thái, hoàn tiền và xác nhận nhận hàng riêng; các đơn cùng lần checkout liên kết qua phiên thanh toán chung. | [UC-B07](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L727 |

<a id="uc-b08"></a>
## UC-B08 — Hủy đơn

Nguồn: [§6 / UC-B08](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L729–762. Vai trò: Khách hàng · Ưu tiên: M.

Các điều khoản dưới đây là yêu cầu nguồn đã đối chiếu baseline; nhánh Google/in-app ghi Should không thuộc DoD Must. Mã clause giữ ổn định khi di chuyển tài liệu.

| Mã điều khoản | Loại | Yêu cầu / điều kiện / ngoại lệ | Nguồn hiệu lực |
|---|---|---|---|
| <a id="uc-b08.goal-01"></a>UC-B08.GOAL-01 | Mục tiêu | Khách hủy một đơn mà không ảnh hưởng các đơn khác được thanh toán cùng lần. | [UC-B08](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L733 |
| <a id="uc-b08.pre-01"></a>UC-B08.PRE-01 | Điều kiện | Đơn được chọn đã thanh toán và chưa bắt đầu đóng gói (chờ_xử_lý), hoặc cả phiên checkout chưa thanh toán. | [UC-B08](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L735 |
| <a id="uc-b08.main-01"></a>UC-B08.MAIN-01 | Luồng chính | Khách mở đơn cần hủy và chọn “Hủy đơn”. | [UC-B08](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L739 |
| <a id="uc-b08.main-02"></a>UC-B08.MAIN-02 | Luồng chính | Hệ thống kiểm tra trạng thái của đơn và hiển thị tác động, số tiền dự kiến hoàn nếu đã thanh toán. | [UC-B08](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L741 |
| <a id="uc-b08.main-03"></a>UC-B08.MAIN-03 | Luồng chính | Khách chọn lý do P-21a: CHANGED_MIND, ORDERED_BY_MISTAKE, FOUND_BETTER_PRICE, ADDRESS_CHANGE hoặc OTHER; nhập ghi chú tối đa 500 ký tự, bắt buộc khi OTHER, rồi xác nhận hủy. Không đổi enum API sang nhãn giao diện. | [API lý do hủy](api_specs.md); [Tham số P-21a](parameters.md) |
| <a id="uc-b08.main-04"></a>UC-B08.MAIN-04 | Luồng chính | Nếu hợp lệ, hệ thống chuyển Order sang “Đã hủy”, hoàn lại hàng của đơn (nếu đã trừ kho), chuyển Settlement của đơn sang chờ_hoàn (phần số dư chờ của Seller và nền tảng gắn với đơn bị khóa, chỉ bị trừ khi hoàn tiền thành công — UC-G02), tạo khoản hoàn bằng tổng tiền đơn và thông báo cho khách và Seller. | [UC-B08](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L745 |
| <a id="uc-b08.main-05"></a>UC-B08.MAIN-05 | Luồng chính | Các đơn khác cùng lần thanh toán vẫn giao, xác nhận nhận hàng và quyết toán bình thường. | [UC-B08](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L747 |
| <a id="uc-b08.alt-01"></a>UC-B08.ALT-01 | Ngoại lệ | Phiên chưa thanh toán và không có khoản đang xác minh: thao tác hủy áp dụng cho toàn bộ phiên — hệ thống báo trước sẽ hủy tất cả N đơn của lần checkout, nhả mọi lượng hàng đã giữ; không hoàn tiền vì chưa thu. | [UC-B08](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L751 |
| <a id="uc-b08.alt-02"></a>UC-B08.ALT-02 | Ngoại lệ | Thanh toán đang chờ xác minh: không cho hủy cho tới khi biết kết quả. | [UC-B08](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L753 |
| <a id="uc-b08.alt-03"></a>UC-B08.ALT-03 | Ngoại lệ | Đơn đã đóng gói, đã giao hoặc đã xác nhận: khách không thể hủy, hệ thống giải thích lý do. | [UC-B08](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L755 |
| <a id="uc-b08.alt-04"></a>UC-B08.ALT-04 | Ngoại lệ | Khách nhấn hủy lặp: chỉ một lần hoàn kho, một lần chuyển Settlement sang chờ_hoàn và một nghĩa vụ hoàn tiền cho đơn. | [UC-B08](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L757 |
| <a id="uc-b08.alt-05"></a>UC-B08.ALT-05 | Ngoại lệ | BANNED Customer còn nghĩa vụ được hủy đúng đơn/phiên của mình khi state cho phép; không dựa lịch sử từng trả tiền và không thanh toán mới. | [FRD UC-B08/E03](FRD.md); [Thẻ 15](coverage.md#the-15) |
| <a id="uc-b08.post-01"></a>UC-B08.POST-01 | Hậu điều kiện | Hủy/hoàn tiền theo từng đơn trên một Payment chung; tiền hoàn lấy từ phần nền tảng đang tạm giữ của đơn, không trừ số dư khả dụng của Seller; tổng tiền hoàn không vượt khoản đã thanh toán và không thay đổi các đơn khác. | [UC-B08](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L761 |

<a id="uc-b09"></a>
## UC-B09 — Xác nhận đã nhận hàng

Nguồn: [§6 / UC-B09](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L763–794. Vai trò: Khách hàng, Hệ thống · Ưu tiên: M.

Các điều khoản dưới đây là yêu cầu nguồn đã đối chiếu baseline; nhánh Google/in-app ghi Should không thuộc DoD Must. Mã clause giữ ổn định khi di chuyển tài liệu.

| Mã điều khoản | Loại | Yêu cầu / điều kiện / ngoại lệ | Nguồn hiệu lực |
|---|---|---|---|
| <a id="uc-b09.goal-01"></a>UC-B09.GOAL-01 | Mục tiêu | Xác nhận đã nhận hàng của một đơn sau khi người bán báo đã giao. | [UC-B09](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L767 |
| <a id="uc-b09.pre-01"></a>UC-B09.PRE-01 | Điều kiện | Đơn đang ở trạng thái “Đã giao — chờ khách xác nhận”. | [UC-B09](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L769 |
| <a id="uc-b09.main-01"></a>UC-B09.MAIN-01 | Luồng chính | Sau khi Seller bấm “Xác nhận đã giao”, hệ thống hiển thị “Đã giao — chờ khách xác nhận” và bắt đầu tính thời hạn 7 ngày riêng cho đơn đó. | [UC-B09](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L773 |
| <a id="uc-b09.main-02"></a>UC-B09.MAIN-02 | Luồng chính | Khách mở đơn và bấm “Đã nhận hàng”. | [UC-B09](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L775 |
| <a id="uc-b09.main-03"></a>UC-B09.MAIN-03 | Luồng chính | Hệ thống chuyển đơn sang “Đã xác nhận”, ghi nhận thời điểm hoàn tất. | [UC-B09](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L777 |
| <a id="uc-b09.main-04"></a>UC-B09.MAIN-04 | Luồng chính | Hệ thống quyết toán đơn (UC-G04): chốt hoa hồng và khoản người bán được nhận, chuyển khoản người bán được nhận từ số dư chờ sang số dư khả dụng của ví Seller. Hệ thống không tự động chi tiền; Seller chủ động rút (UC-D05). | [UC-B09](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L779 |
| <a id="uc-b09.main-05"></a>UC-B09.MAIN-05 | Luồng chính | Các đơn khác cùng lần thanh toán tiếp tục quy trình riêng, không chờ nhau. | [UC-B09](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L781 |
| <a id="uc-b09.alt-01"></a>UC-B09.ALT-01 | Ngoại lệ | Khách không phản hồi trong 7 ngày kể từ lúc Seller xác nhận đã giao: hệ thống tự chuyển đơn sang “Đã xác nhận” (J3), không dùng trạng thái “Đang giao”. | [UC-B09](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L785 |
| <a id="uc-b09.alt-02"></a>UC-B09.ALT-02 | Ngoại lệ | Khách và hệ thống xác nhận đồng thời: chỉ ghi nhận một kết quả, một lần quyết toán và một bút toán SETTLEMENT_RELEASED. | [UC-B09](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L787 |
| <a id="uc-b09.alt-03"></a>UC-B09.ALT-03 | Ngoại lệ | Đơn bị hủy/đang hoàn tiền: không được xác nhận nhận hoặc chốt hoa hồng. | [UC-B09](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L789 |
| <a id="uc-b09.alt-04"></a>UC-B09.ALT-04 | Ngoại lệ | BANNED Customer được xác nhận DELIVERED đúng chủ trong quyền xử lý nghĩa vụ; không mở quyền xem toàn bộ lịch sử/review sau xác nhận. | [FRD UC-B09/E03](FRD.md); [Thẻ 15](coverage.md#the-15) |
| <a id="uc-b09.post-01"></a>UC-B09.POST-01 | Hậu điều kiện | Sau xác nhận bởi khách hoặc tự động, chỉ đơn đó đạt “Đã xác nhận”, được quyết toán và tiền của Seller trở thành khả dụng; đơn đã xác nhận không thể bị hủy hay hoàn. | [UC-B09](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L793 |

<a id="uc-b10"></a>
## UC-B10 — Mua ngay từ trang sản phẩm

Nguồn: [§6 / UC-B10](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L795–838. Vai trò: Khách hàng · Ưu tiên: M.

Các điều khoản dưới đây là yêu cầu nguồn đã đối chiếu baseline; nhánh Google/in-app ghi Should không thuộc DoD Must. Mã clause giữ ổn định khi di chuyển tài liệu.

| Mã điều khoản | Loại | Yêu cầu / điều kiện / ngoại lệ | Nguồn hiệu lực |
|---|---|---|---|
| <a id="uc-b10.goal-01"></a>UC-B10.GOAL-01 | Mục tiêu | Khách mua ngay một sản phẩm từ trang chi tiết mà không cần thêm vào giỏ rồi vào giỏ để thanh toán. | [UC-B10](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L799 |
| <a id="uc-b10.pre-01"></a>UC-B10.PRE-01 | Điều kiện | Khách đã đăng nhập, có số điện thoại và địa chỉ nhận hàng; sản phẩm bán được và tồn kho khả dụng ≥ 1; không có CheckoutSession nào đang chờ thanh toán; tài khoản được phép thanh toán. | [UC-B10](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L801 |
| <a id="uc-b10.main-01"></a>UC-B10.MAIN-01 | Luồng chính | Tại trang chi tiết sản phẩm (UC-B01), khách chọn số lượng và bấm “Mua ngay”. | [UC-B10](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L805 |
| <a id="uc-b10.main-02"></a>UC-B10.MAIN-02 | Luồng chính | Kiểm tra số lượng 1–99 và không vượt tồn kho khả dụng; mở trang Thanh toán chỉ với sản phẩm này dưới cửa hàng tương ứng (1 đơn dự kiến), nhãn “Mua ngay — không qua giỏ hàng”. Không tạo dòng giỏ. | [FRD UC-B10](FRD.md); [Tham số P-10](parameters.md) |
| <a id="uc-b10.main-03"></a>UC-B10.MAIN-03 | Luồng chính | Khách chọn địa chỉ chung; quy tắc mặc định tham chiếu Database §3.1.4 (FRD ghi nhầm §3.1.3). Chưa pay thì chưa tạo Order/Reservation. | [UC-B10](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L809; **baseline thay thế/hiệu chỉnh:** [§5.1](software-architecture.md), L444–496 |
| <a id="uc-b10.main-04"></a>UC-B10.MAIN-04 | Luồng chính | Khi xác nhận Thanh toán, O1 kiểm tra lại quote/giá/Seller/tồn, tạo một CheckoutSession BUY_NOW, một Order, một OrderItem và công việc charge bền vững. BUY_NOW không gọi Cart. | [UC-B10](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L811; **baseline thay thế/hiệu chỉnh:** [§5.1](software-architecture.md), L444–496 |
| <a id="uc-b10.main-05"></a>UC-B10.MAIN-05 | Luồng chính | Sau khi thanh toán thành công (UC-B06), đơn được xử lý như mọi đơn khác; giỏ hàng giữ nguyên. | [UC-B10](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L813 |
| <a id="uc-b10.alt-01"></a>UC-B10.ALT-01 | Ngoại lệ | Khách chưa đăng nhập: chuyển sang đăng nhập, xong quay lại đúng trang sản phẩm với số lượng đã chọn; khách bấm “Mua ngay” lại để tiếp tục (không tự đặt hàng). | [UC-B10](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L817 |
| <a id="uc-b10.alt-02"></a>UC-B10.ALT-02 | Ngoại lệ | Sản phẩm hết hàng hoặc ngừng bán: nút “Mua ngay” bị vô hiệu; nếu trạng thái đổi trong lúc khách ở trang Thanh toán thì “Đặt hàng” báo lỗi và không tạo phiên. | [UC-B10](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L819 |
| <a id="uc-b10.alt-03"></a>UC-B10.ALT-03 | Ngoại lệ | Số lượng ngoài 1–99: báo giới hạn Mua ngay; vượt tồn kho khả dụng: báo “Chỉ còn X”. Không cho tiếp tục, không tạo dòng giỏ hoặc phiên/đơn. | [FRD UC-B10](FRD.md); [Tham số P-10](parameters.md) |
| <a id="uc-b10.alt-04"></a>UC-B10.ALT-04 | Ngoại lệ | Đã có phiên checkout chờ thanh toán (từ giỏ hoặc mua ngay): hướng dẫn thanh toán hoặc hủy phiên cũ trước, như UC-B04. | [UC-B10](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L823 |
| <a id="uc-b10.alt-05"></a>UC-B10.ALT-05 | Ngoại lệ | Thiếu số điện thoại/địa chỉ: yêu cầu hoàn thiện hồ sơ (UC-A06), rồi quay lại trang sản phẩm. | [UC-B10](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L825 |
| <a id="uc-b10.alt-06"></a>UC-B10.ALT-06 | Ngoại lệ | Rời trang trước khi xác nhận Thanh toán: chỉ có Preview, chưa có phiên/đơn/giữ tồn; không coi việc mở trang hoặc bấm một bước xem trước là O1. | [UC-B10](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L827; **baseline thay thế/hiệu chỉnh:** [§5.1–5.2](software-architecture.md), L444–520 |
| <a id="uc-b10.alt-07"></a>UC-B10.ALT-07 | Ngoại lệ | Bấm “Đặt hàng” lặp: cùng khóa chống lặp trả về cùng phiên và đơn (G-09). | [UC-B10](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L829 |
| <a id="uc-b10.alt-08"></a>UC-B10.ALT-08 | Ngoại lệ | Customer BANNED/DISABLED không Mua ngay hay thanh toán mới, bất kể lịch sử trả tiền; public browsing không cấp quyền authenticated mua sắm. | [FRD UC-B10/E03](FRD.md); [Thẻ 15](coverage.md#the-15) |
| <a id="uc-b10.alt-09"></a>UC-B10.ALT-09 | Ngoại lệ | Giỏ đang có cùng sản phẩm: không gộp, không trừ; giỏ không đổi. | [UC-B10](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L833 |
| <a id="uc-b10.post-01"></a>UC-B10.POST-01 | Hậu điều kiện | Một lần “Mua ngay” tạo tối đa 1 CheckoutSession (nguồn_checkout = mua_ngay), 1 Order, 1 OrderItem và 1 Payment; mọi quy tắc thanh toán, hủy, hoàn, quyết toán áp dụng như đơn tạo từ giỏ; giỏ hàng không thay đổi. | [UC-B10](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L835 |
| <a id="uc-b10.note-01"></a>UC-B10.NOTE-01 | Ràng buộc | Không thêm sản phẩm khác vào phiên mua ngay; muốn mua nhiều sản phẩm thì dùng giỏ hàng. Đổi số lượng: quay lại trang sản phẩm. | [UC-B10](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L837 |

<a id="uc-d02"></a>
## UC-D02 — Chuẩn bị, xác nhận giao hoặc từ chối giao đơn

Nguồn: [§6 / UC-D02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L911–944. Vai trò: Người bán · Ưu tiên: M (bao gồm từ chối giao hàng và xử lý quá hạn).

Các điều khoản dưới đây là yêu cầu nguồn đã đối chiếu baseline; nhánh Google/in-app ghi Should không thuộc DoD Must. Mã clause giữ ổn định khi di chuyển tài liệu.

| Mã điều khoản | Loại | Yêu cầu / điều kiện / ngoại lệ | Nguồn hiệu lực |
|---|---|---|---|
| <a id="uc-d02.goal-01"></a>UC-D02.GOAL-01 | Mục tiêu | Người bán xử lý các đơn của cửa hàng mình và bắt buộc chọn giao hoặc từ chối giao trong 7 ngày. | [UC-D02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L915 |
| <a id="uc-d02.pre-01"></a>UC-D02.PRE-01 | Điều kiện | Seller APPROVED, ACTIVE hoặc BANNED có allowlist xử lý nghĩa vụ; chỉ thao tác đơn tồn đúng chủ theo state/deadline. Ban sau capture không tự hủy đơn hay đóng băng tiền. | [FRD UC-D02/E03](FRD.md); [Thẻ 15](coverage.md#the-15) |
| <a id="uc-d02.main-01"></a>UC-D02.MAIN-01 | Luồng chính | Người bán mở “Đơn cần xử lý”, chỉ thấy Order của mình cùng mặt hàng/thông tin người nhận. Cảnh báo sắp hết hạn khi còn không quá 24 giờ trước hạn xử lý 7 ngày; cảnh báo không gia hạn deadline. | [FRD UC-D02](FRD.md); [Tham số P-36](parameters.md) |
| <a id="uc-d02.main-02"></a>UC-D02.MAIN-02 | Luồng chính | Người bán bắt đầu chuẩn bị hàng; hệ thống chuyển đơn sang “Đang đóng gói”. | [UC-D02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L923 |
| <a id="uc-d02.main-03"></a>UC-D02.MAIN-03 | Luồng chính | Trong tối đa 7 ngày từ thời điểm thanh toán thành công, Seller phải chọn một trong hai hành động: “Xác nhận đã giao” hoặc “Từ chối giao hàng”. | [UC-D02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L925 |
| <a id="uc-d02.main-04"></a>UC-D02.MAIN-04 | Luồng chính | Nếu Seller xác nhận đã giao, hệ thống chuyển đơn thẳng sang “Đã giao” và hiển thị cho khách “Chờ xác nhận”; không tồn tại bước “Đang giao”. | [UC-D02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L927 |
| <a id="uc-d02.main-05"></a>UC-D02.MAIN-05 | Luồng chính | Seller từ chối phải chọn DEFECTIVE, OUT_OF_STOCK_ACTUAL, CANNOT_DELIVER hoặc OTHER (P-21b), luôn nhập ghi chú, tối đa 500 ký tự. Hợp lệ mới hủy đơn, hoàn kho, chuyển Settlement sang chờ_hoàn và khởi tạo hoàn đúng tổng tiền đơn; số dư chờ chỉ bị trừ khi hoàn SUCCESS. Giữ nguyên enum API. | [API lý do từ chối](api_specs.md); [Tham số P-21b](parameters.md) |
| <a id="uc-d02.alt-01"></a>UC-D02.ALT-01 | Ngoại lệ | Seller không xác nhận đã giao và cũng không từ chối trong 7 ngày kể từ khi thanh toán thành công: hệ thống tự hủy đơn và hoàn tiền (J4, Must). | [UC-D02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L933 |
| <a id="uc-d02.alt-02"></a>UC-D02.ALT-02 | Ngoại lệ | Seller từ chối khi hàng lỗi/hỏng/không đủ điều kiện: phải có lý do; không cần khách xác nhận để kích hoạt hủy/hoàn tiền. | [UC-D02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L935 |
| <a id="uc-d02.alt-03"></a>UC-D02.ALT-03 | Ngoại lệ | Đơn đã xác nhận “Đã giao”: Seller không được từ chối/hủy lại; chờ khách xác nhận hoặc J3 tự xác nhận sau 7 ngày kể từ lúc giao. | [UC-D02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L937 |
| <a id="uc-d02.alt-04"></a>UC-D02.ALT-04 | Ngoại lệ | Tác vụ quá hạn trùng với Seller bấm giao/từ chối: chỉ một kết quả được ghi nhận, không hoàn hai lần. | [UC-D02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L939 |
| <a id="uc-d02.alt-05"></a>UC-D02.ALT-05 | Ngoại lệ | Seller không thấy và không thao tác được đơn của cửa hàng khác, kể cả đơn được thanh toán cùng lần với đơn của mình. | [UC-D02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L941 |
| <a id="uc-d02.post-01"></a>UC-D02.POST-01 | Hậu điều kiện | Từ chối giao hàng là chức năng Must; quá 7 ngày không trả lời cũng hủy/hoàn tiền bắt buộc. Mọi tác động chỉ nằm trong đơn của Seller đó. | [UC-D02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L943 |

<a id="uc-g03"></a>
## UC-G03 — Tự động xử lý đơn/giao dịch quá hạn

Nguồn: [§6 / UC-G03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1353–1382. Vai trò: Hệ thống · Ưu tiên: M.

Các điều khoản dưới đây là yêu cầu nguồn đã đối chiếu baseline; nhánh Google/in-app ghi Should không thuộc DoD Must. Mã clause giữ ổn định khi di chuyển tài liệu.

| Mã điều khoản | Loại | Yêu cầu / điều kiện / ngoại lệ | Nguồn hiệu lực |
|---|---|---|---|
| <a id="uc-g03.goal-01"></a>UC-G03.GOAL-01 | Mục tiêu | Xử lý quá hạn thanh toán, hai thời hạn 7 ngày độc lập của người bán và khách trên từng đơn, và các giao dịch tiền chưa rõ kết quả. | [UC-G03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1357 |
| <a id="uc-g03.pre-01"></a>UC-G03.PRE-01 | Điều kiện | Tác vụ định kỳ theo N-06 được kích hoạt. | [UC-G03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1359 |
| <a id="uc-g03.main-01"></a>UC-G03.MAIN-01 | Luồng chính | J1: phiên checkout hết hạn giữ hàng mà chưa có khoản thanh toán đang chờ xác minh → đóng phiên và tất cả các đơn của phiên, giải phóng toàn bộ giữ chỗ. | [UC-G03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1363 |
| <a id="uc-g03.main-02"></a>UC-G03.MAIN-02 | Luồng chính | J2/J2b: tra cứu Payment chưa rõ; nếu quá P-04 vẫn không có kết quả thì đóng phiên và các đơn, tiếp tục theo dõi để hoàn toàn bộ nếu tiền đến muộn. | [UC-G03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1365 |
| <a id="uc-g03.main-03"></a>UC-G03.MAIN-03 | Luồng chính | J4 (Must): với mỗi Order, nếu quá 7 ngày kể từ thanh toán thành công mà Seller chưa xác nhận “Đã giao” hoặc “Từ chối giao hàng”, hệ thống tự hủy đơn và hoàn tiền đơn đó (Settlement chờ_hoàn; số dư chờ bị trừ khi hoàn thành công — UC-G02). | [UC-G03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1367 |
| <a id="uc-g03.main-04"></a>UC-G03.MAIN-04 | Luồng chính | J3 (Must): Order đã_giao nhưng khách không chọn “Đã nhận hàng” trong 7 ngày kể từ lúc Seller xác nhận đã giao → tự chuyển sang “Đã xác nhận” và quyết toán đơn (UC-G04), tiền Seller chuyển sang khả dụng. | [UC-G03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1369 |
| <a id="uc-g03.main-05"></a>UC-G03.MAIN-05 | Luồng chính | J5 tra cứu khoản hoàn chưa rõ, chỉ thử lại khi thất bại rõ; mỗi chu kỳ một lần đầu và tối đa 3 lần thử lại chờ 1/5/15 phút. Hết lượt thì thông báo khách, chỉ khách đúng chủ mở chu kỳ mới, không Admin. J6 gửi lại email lỗi tạm thời tối đa 3 lần sau lần đầu, chờ 1/5/15 phút; tôn trọng Retry-After, dừng lỗi vĩnh viễn/token hết hạn/hết lượt. Cả hai giữ cùng đối tượng nghiệp vụ và khóa chống trùng; xem chi tiết dưới. | [P-RF-01/P-19](parameters.md); [FRD UC-G03](FRD.md) |
| <a id="uc-g03.alt-01"></a>UC-G03.ALT-01 | Ngoại lệ | Seller bấm giao/từ chối ngay khi J4 chạy: chỉ một chuyển trạng thái thắng, không vừa giao vừa hủy. | [UC-G03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1375 |
| <a id="uc-g03.alt-02"></a>UC-G03.ALT-02 | Ngoại lệ | Khách xác nhận nhận đúng lúc J3 chạy: chỉ một lần ghi nhận, một lần quyết toán và một bút toán SETTLEMENT_RELEASED. | [UC-G03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1377 |
| <a id="uc-g03.alt-03"></a>UC-G03.ALT-03 | Ngoại lệ | Thanh toán chờ kết quả không được coi là thất bại chỉ vì chưa phản hồi; đối soát trước khi nhả hàng. | [UC-G03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1379 |
| <a id="uc-g03.post-01"></a>UC-G03.POST-01 | Hậu điều kiện | J4 hủy và hoàn tiền khi Seller im lặng quá 7 ngày là bắt buộc; J3 tự xác nhận nhận hàng sau 7 ngày kể từ “Đã giao”; mỗi đơn được tính hạn riêng; không có trạng thái “Đang giao”. | [UC-G03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1381 |

### J5/J6 — Tra cứu và thử lại

- **J5/P-RF-01:** mỗi chu kỳ một lần thực hiện đầu và tối đa 3 lần thử lại, chờ 1, 5, 15 phút tính từ lần thất bại trước. Chỉ gửi lại lệnh khi lần trước thất bại rõ; PENDING hoặc timeout thì tiếp tục tra cứu, không tính là lần thử lại. Không dùng khoảng 21 phút ước tính để kết luận giao dịch chưa rõ là thất bại.
- Hết 3 lần vẫn thất bại thì dừng tự động và thông báo cho khách đã thanh toán. Khách bấm **Thử lại hoàn tiền** mở chu kỳ mới cho cùng Refund, số tiền và mã chống trùng; đặt lại bộ đếm chu kỳ nhưng giữ toàn bộ lịch sử. Áp dụng cả ORDER và FULL_CHECKOUT; kiểm tra đúng chủ kể cả phiên giới hạn nghĩa vụ. Admin không xử lý thay khách. Chỉ ghi đã hoàn và giảm số dư chờ khi cổng xác nhận SUCCESS.
- **J6/P-19:** mỗi email một lần gửi đầu và tối đa 3 lần gửi lại, chờ 1, 5, 15 phút từ lần thất bại trước. Chỉ thử lại lỗi tạm thời như timeout, mất kết nối, lỗi nhà cung cấp hoặc giới hạn tần suất; không gửi trước Retry-After nếu có. Dừng ngay với lỗi vĩnh viễn (ví dụ địa chỉ nhận không hợp lệ), liên kết/token hết hạn hoặc hết lượt.
- Gửi lại dùng cùng thông báo và khóa chống trùng, không tạo email nghiệp vụ mới. Hết lượt ghi nhận gửi thất bại, không đổi trạng thái đơn/thanh toán/hoàn tiền/tài khoản. Cơ chế email demo và thời gian giữ bí mật của demo chưa chốt; không áp giới hạn in-app 30 ngày cho token/reset URL.
- Lịch kích hoạt J5/J6, timeout và khóa worker là quyết định kỹ thuật riêng; không dùng các khoảng chờ trên làm chu kỳ chạy job. Nguồn: [tham số đã chốt](parameters.md).
