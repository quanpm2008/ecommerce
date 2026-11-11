# Quy ước nghiệp vụ và cách đọc

## Baseline và trạng thái

Ngày 11/11/2026, người dùng chấp nhận SAD v1.1 §7.1 và quy tắc ưu tiên D.1
làm baseline, đồng thời duyệt cây tài liệu này. Đây là tổ chức lại tài liệu,
không xác nhận code đã đáp ứng yêu cầu. Năm nguồn trong `business/` được giữ nguyên.

Theo [SAD D.1](../../business/Software-Architecture.md): quyết định xác nhận và
phụ lục hiệu chỉnh được áp dụng trước nội dung cũ cùng chủ đề; FRD sở hữu nghiệp
vụ, Database sở hữu cấu trúc/bất biến, API sở hữu contract. SAD §7.1 ghi ADR-01…06
đã chấp nhận; chi tiết đề xuất trong phụ lục A hoặc DELTA vẫn cần review contract.
Baseline chấp nhận không tự phê duyệt mọi tên bảng/job/header/endpoint trong SAD.
Xung đột còn lại vào [coverage](coverage.md#cau-hoi), không chọn nguồn thuận tiện.

| Trục | Nhãn | Ý nghĩa |
|---|---|---|
| Yêu cầu | Đã xác nhận | Nguồn còn hiệu lực theo baseline, không phải kết quả runtime |
| Yêu cầu | Đề xuất kỹ thuật | Cách thực hiện cần review; có thể đổi nếu giữ cùng bất biến/AT |
| Yêu cầu | Câu hỏi mở / mâu thuẫn | Chưa đủ authority để chọn hoặc gán giá trị |
| Hiện trạng | Có code / có test | Quan sát đường dẫn và assertions, chưa có bằng chứng đã chạy |
| Hiện trạng | Chưa tìm thấy | Không tìm thấy implementation tương ứng trong tree khảo sát |
| Kiểm chứng | Đã kiểm chứng | Chỉ dùng với lệnh/kết quả/phiên bản và môi trường cụ thể |

## Chọn hồ sơ theo task

| Task | Đọc | Biên có thể phải đọc thêm |
|---|---|---|
| Auth, Seller, quyền, hồ sơ, địa chỉ, ban | [identity](identity.md) | Order.has-paid-order, Notification, Finance tạo ví |
| Product, search, media, tồn hiển thị | [catalog](catalog.md) | Identity G-06, Order Inventory, checkout |
| Cart/BUY_NOW, preview/pay, tồn, hủy/giao/nhận, deadline | [checkout-orders](checkout-orders.md) | Payment, Finance, Identity, Notification |
| Charge/refund/simulator, Admin retry | [payments](payments.md) | Order CLOSED/PAID, Finance pending, Notification |
| Settlement, ví, rounding, withdrawal, đối soát | [finance](finance.md) | Payment/Order nguồn, Identity, Notification |
| Email demo/event/delivery | [notifications](notifications.md) | Event producer, Identity contact/secret |
| Library, package, ownership, validation | [engineering](../engineering.md) | POM/config/test thực tế; không dùng thiết kế làm hiện trạng |

## Thuật ngữ chung

Nguồn: FRD “Từ điển định danh” và “Giải nghĩa công thức”, Database §3/8,
[SAD D.3](../../business/Software-Architecture.md).

| Thuật ngữ | Ý nghĩa cần giữ |
|---|---|
| User | Một danh tính/credential chung; JWT sub=user_id; Admin là role |
| Customer / Seller | Hồ sơ cùng shared PK=User.id, có thể chồng lấp; có hồ sơ không tự có mọi quyền |
| Role / Permission / ownership | Quyền hiện hành của role, điều kiện trạng thái và tài nguyên thuộc sub cùng cần hợp lệ |
| CheckoutSession | Một lần thu chung, có N Order theo Seller; không là một Order lớn |
| Order / OrderItem | Đơn của một Seller / dòng hàng snapshot; không FulfillmentGroup, SellerOrder, ParentOrder |
| CART / BUY_NOW | Hai nguồn dùng cùng checkout; BUY_NOW không đọc/ghi Cart |
| Preview / quoteToken | Xem giá chỉ đọc / bằng chứng có chữ ký cho giá đã xem; không cam kết còn hàng |
| Inventory / Reservation | Tồn chính thức tại Order / giữ trước thu; available=on_hand−reserved |
| Payment / Refund | Khoản thu chung / hoàn từng Order hoặc FULL_CHECKOUT cho success muộn; nguồn sự thật ở Payment |
| Settlement | Phân bổ/trạng thái tiền của một Order, thuộc Finance; không là Payment |
| pending / available / total_withdrawn | Tiền chưa được rút / đã đủ điều kiện rút / tổng đã rút; không trộn số dư thời điểm với doanh số theo kỳ |
| WalletTransaction | Bút toán append-only với business key; không sửa/xóa để chữa lệch |
| Withdrawal | Rút tiền giả lập tức thời, không duyệt, không Payout hoặc state chờ |
| PAID / PACKING / DELIVERED / CONFIRMED | Chờ xử lý / đóng gói / đã giao chờ khách / đã xác nhận; không trạng thái đang giao |
| Outbox / Inbox | Event lưu cùng transaction nguồn / dấu xử lý cùng transaction consumer; nhận message chưa đủ chứng minh effect đã áp dụng |
| Idempotency / business dedupe | Lặp request hoặc cùng nghĩa vụ không sinh thêm effect; eventId riêng chưa đủ chống trùng tiền/kho |
| Đối soát | Hỏi nguồn chính thức để tiếp tục/phát hiện lệch; không sửa tay cho cân |
| P-xx / N-xx / TBD | Tham số và NFR; giá trị dev hoặc ví dụ không là policy đã chốt |
| Must / Should / Could | Must có đặc tả/AC đầy đủ trong phạm vi hồ sơ; hai mức sau chỉ giữ ranh giới và nguồn |

## Quyền chung

Nguồn: [API §3.2.1](../../business/api.md), [Database §3.1.9/5.7](../../business/database.md),
[SAD §6.1–6.4](../../business/Software-Architecture.md).
Một SPA có vùng Customer/Seller/Admin theo ADR-02, không tạo tài khoản riêng
theo vùng UI. Không tin owner/permission/header do browser tự khai.
API internal phải xác thực service và allowlist; user token không thay service credential.
Idempotency replay cũng phải kiểm tra quyền hiện hành trước khi trả dữ liệu.

| Vai trò | Permission được seed | Điều kiện |
| :---- | :---- | :---- |
| CUSTOMER | customer:profile:own; cart:manage:own; checkout:create:own; payment:create:own; order:read:own; order:cancel:own; order:confirm:own; review:write:own; notification:read:own | User ACTIVE, hồ sơ của sub; Customer ACTIVE khi mua mới, G-14 cho đơn cũ. |
| SELLER | seller:profile:own; product:manage:own; inventory:manage:own; order:fulfill:own; wallet:read:own; withdrawal:create:own; notification:read:own | Chỉ cấp khi duyệt; User ACTIVE, Seller APPROVED/ACTIVE, sellerId=sub. |
| ADMIN | seller:approve; account:moderate; product:moderate; category:manage; operations:read:any; refund:retry; notification:read:own | Admin nội bộ; không tự duyệt; audit mọi thao tác, không sửa số dư hoặc xác nhận tay đã hoàn. |

User ACTIVE chưa có role có policy self-service và tạo Customer lần đầu theo
API §3.2.1; ADMIN/SELLER không ngầm có CUSTOMER. Xem G-14 ở identity.md cho ban
từng phạm vi, đặc biệt đơn đã trả tiền và snapshot tại thời điểm cấm.

## Quy tắc xuyên hồ sơ

| Mã nguồn | Quy tắc đã xác nhận | Nguồn |
|---|---|---|
| <a id="g-10"></a>G-10 | Tiền tệ VND, số nguyên (P-17). Phí vận chuyển = 0; giá niêm yết đã gồm thuế. | [§8.1](../../business/database.md), L962 |
| <a id="g-13"></a>G-13 | Không xóa vật lý dữ liệu đã được đơn hàng tham chiếu (sản phẩm, Seller…). Ngoại lệ: CustomerAddress xóa vật lý (hard delete) vì Order đã lưu snapshot địa chỉ. WalletTransaction chỉ được thêm, không sửa/xóa. | [§8.1](../../business/database.md), L965 |
| <a id="g-15"></a>G-15 | Mọi thanh toán, hoàn tiền, số dư ví, hoa hồng, giao dịch rút tiền trong MVP là giả lập, do **bộ giả lập thanh toán nội bộ** xử lý (mục 8.4); không có nhà cung cấp bên ngoài. Nhãn "Giả lập — không phát sinh tiền thật" xuất hiện trên màn hình/email liên quan; không nhập thông tin thẻ, tài khoản ngân hàng hay chuyển khoản thật. | [§8.1](../../business/database.md), L967 |

## Mã và dẫn nguồn

- Giữ mã gốc UC/G/WD/P/N/AT/ADR/DELTA/C/J; luôn kèm tài liệu/section vì mã cũ
  có thể trùng nghĩa giữa các hồ sơ. Không tái sử dụng mã đã loại bỏ.
- Clause chưa có mã dùng `UC-A01.MAIN-01`, `UC-A01.ALT-01`…; bổ sung giữ mã
  đã cấp, không đánh lại số khi chèn hoặc di chuyển. `REQ-IDN-001`, `REQ-ORD-001`…
  là điều khoản bổ sung từ nguồn; `AC-IDN-001`… là diễn giải kiểm chứng, không thêm policy.
- Mỗi clause UC dẫn dòng gốc; hàng bị hiệu chỉnh có cả FRD cũ và SAD hiệu lực.
  Metadata mục tiêu/điều kiện/hậu điều kiện cũng được giữ. Các câu bổ sung dẫn
  file và section, không chỉ một link chung cho cả tài liệu.
- Phần nguồn ghi `Lx–y` là dòng tại hash trong coverage; khi nguồn thay đổi cần
  review lại, không coi số dòng là định danh bền vững. Mục/UC/mã là khóa tra cứu.
- DTO/data dictionary/HTTP examples đầy đủ ở mục contract nguồn; bảng đường
  dẫn trong hồ sơ không chứng minh endpoint đã chạy hoặc toàn bộ field đều required.
- Thông tin chưa xác nhận không được đổi thành yêu cầu chỉ vì code/test có nó.
  Các AT và AC chưa chạy phải giữ nhãn chưa kiểm chứng.
