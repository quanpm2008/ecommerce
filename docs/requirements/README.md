# Quy ước nghiệp vụ và cách đọc

## Baseline và trạng thái

Ngày 11/11/2026, người dùng chấp nhận SAD v1.1 §7.1 và quy tắc ưu tiên D.1
làm baseline, đồng thời duyệt cây tài liệu này. Đây là tổ chức lại tài liệu,
không xác nhận code đã đáp ứng yêu cầu. Ba nguồn chi tiết mới trong
`docs/requirements/` là [FRD](FRD.md), [API](api_specs.md) và
[Database](database.md). Thêm tham chiếu không thay đổi baseline hoặc
phê duyệt thêm các đề xuất kỹ thuật.

Theo [SAD D.1](software-architecture.md): quyết định xác nhận và
phụ lục hiệu chỉnh được áp dụng trước nội dung cũ cùng chủ đề; FRD sở hữu nghiệp
vụ, Database sở hữu cấu trúc/bất biến, API sở hữu contract. SAD §7.1 ghi ADR-01…06
đã chấp nhận; chi tiết đề xuất trong phụ lục A hoặc DELTA vẫn cần review contract.
Baseline chấp nhận không tự phê duyệt mọi tên bảng/job/header/endpoint trong SAD.
Xung đột còn lại vào [coverage](coverage.md#cau-hoi), không chọn nguồn thuận tiện.

## Nguồn chi tiết ngày 16/11/2026

| Tài liệu | Phiên bản | Tra cứu |
|---|---|---|
| [FRD](FRD.md) | v5.5 | Actor, use case, luồng chính/ngoại lệ, AC và quyết định |
| [API](api_specs.md) | v1.1 | Quy ước giao tiếp, quyền, endpoint, DTO, lỗi, API nội bộ và sự kiện |
| [Database](database.md) | v5.1 | Entity/field, ownership, ràng buộc, G/WD, state machine và dòng tiền |

Phần ban theo nghĩa vụ/J7, rút số dư nhỏ, HIDDEN/provision và trigger Order sau thu đã được hiệu chỉnh theo [Thẻ 15](coverage.md#the-15), quyết định được duyệt ngày 17/11/2026. Các tham số được cập nhật theo Thẻ 16 như phần dưới; ngoài hai phạm vi này, hồ sơ và bảng truy vết vẫn có nguồn/số dòng baseline 11/11/2026, chưa xác nhận đồng bộ toàn bộ. Không tự suy ra code đã được cập nhật.

Các giá trị trong [bảng tham số](parameters.md) lấy từ Thẻ 16 và xác nhận của người dùng ngày 18–19/11/2026, thay phần khác giá trị hoặc còn để ngỏ trong nguồn cũ cùng chủ đề. Hoa hồng chung là 5%; khách đã thanh toán tự mở chu kỳ thử lại hoàn tiền khi hết lượt, Admin chỉ xem tiến độ. P-37 chốt tìm theo tên sản phẩm và tên cửa hàng không phân biệt dấu tiếng Việt, không phân biệt hoa thường. P-01 vẫn dùng tạm 15 phút; không thay N-09 hoặc N-11. Những thay đổi này không tự mở rộng phần Should/Could hoặc thay các quyết định kiến trúc khác.

[Quyết định 0003 ngày 23/11/2026](../decisions/0003-mot-phien-refresh-tren-user.md) thay phần quản lý phiên: mỗi User có một phiên hiện hành, lưu hash refresh token, thời hạn và session_id ngay trên User thay AuthSession; login mới kết thúc phiên trước. Giữ cookie HttpOnly/Origin theo 0001 và quyền G-14. Tài liệu đã cập nhật yêu cầu; code/migration/Supabase chưa chuyển sang thiết kế này.

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
| Auth, Seller, quyền, hồ sơ, địa chỉ, ban | [identity](identity.md) | Order/Finance obligations, Notification |
| Product, search, media, tồn hiển thị | [catalog](catalog.md) | Identity G-06, Order Inventory, checkout |
| Cart/BUY_NOW, preview/pay, tồn, hủy/giao/nhận, deadline | [checkout-orders](checkout-orders.md) | Payment, Finance, Identity, Notification |
| Charge/refund/simulator, khách thử lại hoàn tiền | [payments](payments.md) | Order CLOSED/PAID, Finance pending, Notification |
| Settlement, ví, rounding, withdrawal, đối soát | [finance](finance.md) | Payment/Order nguồn, Identity, Notification |
| In-app Must; email tùy chọn triển khai sau; event/dedupe | [notifications](notifications.md) | Event producer, Identity contact/secret |
| Library, package, ownership, validation | [engineering](../engineering.md) | POM/config/test thực tế; không dùng thiết kế làm hiện trạng |
| Giá trị tham số, giới hạn và lịch thử lại | [parameters](parameters.md) | Hồ sơ nghiệp vụ và contract theo service |
| Kiến trúc phần mềm/hệ thống, quyết định và boundary | [SAD](software-architecture.md), [System Architecture](system-architecture.md) | SAD §7.1/D.1 và coverage khi có xung đột |

## Thuật ngữ chung

Nguồn: FRD “Từ điển định danh” và “Giải nghĩa công thức”, Database §3/8,
[SAD D.3](software-architecture.md).

| Thuật ngữ | Ý nghĩa cần giữ |
|---|---|
| User | Một tài khoản/credential thuộc đúng accountType CUSTOMER/SELLER/ADMIN; JWT sub=user_id, tối đa một role hiệu lực |
| Customer / Seller | Hồ sơ shared PK=User.id của tài khoản đúng loại, không chồng lấp trên cùng User; Admin là tài khoản riêng |
| Phiên đăng nhập / refresh token | Mỗi User một session_id hiện hành cùng hash/hạn refresh token; login mới thay phiên cũ. Token nguyên văn chỉ trong cookie HttpOnly, không nằm trong DTO User. Theo quyết định 0003, chưa triển khai |
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

Nguồn: [API §3.2.1](api_specs.md), [Database §3.1/5.7/G-14](database.md),
[SAD §6.1–6.4](software-architecture.md), [Thẻ 15](coverage.md#the-15).
Một SPA có vùng Customer/Seller/Admin; tài khoản và phiên độc lập theo accountType, không hợp quyền nhiều vai trò. Không tin owner/permission/header do browser tự khai.
API internal phải xác thực service và allowlist; user token không thay service credential.
Idempotency replay cũng phải kiểm tra quyền hiện hành trước khi trả dữ liệu.

| Vai trò | Permission được seed | Điều kiện |
| :---- | :---- | :---- |
| CUSTOMER | customer:profile:own; cart:manage:own; checkout:create:own; payment:create:own; refund:retry:own; order:read:own; order:cancel:own; order:confirm:own; review:write:own; notification:read:own | Đúng accountType/owner; ACTIVE mua bình thường, BANNED chỉ nghĩa vụ hiện tại theo G-14. Chỉ khách đã thanh toán được mở chu kỳ thử lại Refund của mình khi hết lượt tự động; DISABLED không truy cập API bảo vệ. |
| SELLER | seller:profile:own; product:manage:own; inventory:manage:own; order:fulfill:own; wallet:read:own; withdrawal:create:own; notification:read:own | Chỉ cấp khi duyệt; đúng accountType/owner, APPROVED/ACTIVE bán bình thường; BANNED chỉ đơn/tiền nghĩa vụ theo allowlist, không rút pending; DISABLED chặn truy cập. |
| ADMIN | seller:approve; account:moderate; product:moderate; category:manage; operations:read:any; operations:annotate; notification:read:own | Admin nội bộ; không tự duyệt; audit thao tác quản trị. Hoàn tiền chỉ được xem tiến độ, không thử lại thay khách, sửa số dư hoặc xác nhận tay đã hoàn. `operations:annotate` chỉ ghi chú xử lý khoản thu, không đổi kết quả hoàn tiền. |

Seller PENDING/REJECTED không login; chỉ Seller APPROVED được cấm. Xem [G-14](identity.md#g-14) và [WD-09](finance.md#wd-09) về nghĩa vụ hiện tại, hạn khôi phục và ngoại lệ rút toàn bộ available dưới 10.000đ. J7/login/refresh kiểm tra lại nghĩa vụ, không dùng snapshot lịch sử đã thanh toán; nguồn lỗi không suy ra đã hết nghĩa vụ.

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
