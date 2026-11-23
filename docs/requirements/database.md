# Thiết kế dữ liệu và phân quyền RBAC cho hệ thống Ecommerce

**Phiên bản:** v5.1 (16/11/2026)

MÔ HÌNH TÀI KHOẢN CUSTOMER / SELLER / ADMIN ĐỘC LẬP — RBAC MỘT VAI TRÒ MỖI TÀI KHOẢN

**Phiên bản 5.1 — 16/11/2026:** tài khoản CUSTOMER, SELLER, ADMIN độc lập, mỗi User tối đa một role hiệu lực; Seller PENDING/REJECTED không đăng nhập, chỉ Seller APPROVED được cấm. Giữ users.avatar\_object\_key chung cho ảnh đại diện/logo, UUID Order/CheckoutSession và withdrawal\_code. **Cập nhật đặc tả 17/11/2026 theo [Thẻ 15](coverage.md#the-15):** vòng đời cấm theo nghĩa vụ, khôi phục trong 30 ngày từ DISABLED, rút toàn bộ available dưới ngưỡng cho BANNED, tạo Product HIDDEN trước provision và trigger invalid-after-capture. Các phần này thay thế policy cũ tương ứng, không tuyên bố code/migration đã triển khai và không đổi quy tắc Payment/Refund/Settlement không liên quan.

**Cập nhật đặc tả 23/11/2026:** [quyết định 0003](../decisions/0003-mot-phien-refresh-tren-user.md) chuyển phiên hiện hành từ AuthSession sang User, mỗi tài khoản tối đa một phiên. Chỉ lưu hash refresh token; giữ cookie HttpOnly và lịch sử chống dùng lại token. Đây là yêu cầu mới, chưa áp dụng vào code/migration hoặc database Supabase.

## Mục lục

- [2 Bản đồ entity theo service](#2-bản-đồ-entity-theo-service)
- [3 Entity Data Dictionary chi tiết](#3-entity-data-dictionary-chi-tiết)
  - [3.1 Identity và Seller với User và RBAC](#31-identity-và-seller-với-user-và-rbac)
  - [3.2 Product](#32-product)
  - [3.3 Cart](#33-cart)
  - [3.4 Payment](#34-payment)
  - [3.5 Finance](#35-finance)
  - [3.6 Order Inventory Fulfillment](#36-order-inventory-fulfillment)
  - [3.7 Notification](#37-notification)
  - [3.8 Reporting](#38-reporting)
- [4 Quan hệ ownership và bất biến](#4-quan-hệ-ownership-và-bất-biến)
  - [4.1 Các bất biến phải kiểm thử](#41-các-bất-biến-phải-kiểm-thử)
  - [4.2 Quyền ghi dữ liệu](#42-quyền-ghi-dữ-liệu)
- [5 Luồng nghiệp vụ cập nhật](#5-luồng-nghiệp-vụ-cập-nhật)
  - [5.1 Đăng ký và phân quyền Seller](#51-đăng-ký-và-phân-quyền-seller)
  - [5.2 Tìm kiếm đơn giản trong database](#52-tìm-kiếm-đơn-giản-trong-database)
  - [5.3 Giỏ hàng, Mua ngay và checkout](#53-giỏ-hàng-mua-ngay-và-checkout)
  - [5.4 Use case Seller xem ví và rút tiền (UC-D05)](#54-use-case-seller-xem-ví-và-rút-tiền-uc-d05)
  - [5.5 Trạng thái Order và các job](#55-trạng-thái-order-và-các-job)
  - [5.6 Reporting tạm hoãn và bỏ chức năng báo cáo vi phạm](#56-reporting-tạm-hoãn-và-bỏ-chức-năng-báo-cáo-vi-phạm)
  - [5.7 Hợp đồng API và thực thi phân quyền](#57-hợp-đồng-api-và-thực-thi-phân-quyền)
  - [5.8 Migration dữ liệu và kiểm thử RBAC](#58-migration-dữ-liệu-và-kiểm-thử-rbac)
- [6 Bảng kỹ thuật tối thiểu](#6-bảng-kỹ-thuật-tối-thiểu)
- [7 Đối chiếu thay đổi và điểm cần xác nhận](#7-đối-chiếu-thay-đổi-và-điểm-cần-xác-nhận)
  - [7.1 Các quyết định còn mở](#71-các-quyết-định-còn-mở)
- [8 Quy tắc toàn cục, state machine và dòng tiền (chuyển từ FRD mục 4)](#8-quy-tắc-toàn-cục-state-machine-và-dòng-tiền-chuyển-từ-frd-mục-4)
  - [8.1 Quy tắc toàn cục (G-01 đến G-17)](#81-quy-tắc-toàn-cục-g-01-đến-g-17)
  - [8.2 State machine](#82-state-machine)
  - [8.3 Dòng tiền giả lập, hoa hồng và quy tắc ví (WD-01 đến WD-11)](#83-dòng-tiền-giả-lập-hoa-hồng-và-quy-tắc-ví-wd-01-đến-wd-11)
  - [8.4 Bộ giả lập thanh toán nội bộ và lộ trình hai giai đoạn](#84-bộ-giả-lập-thanh-toán-nội-bộ-và-lộ-trình-hai-giai-đoạn)

## 2 Bản đồ entity theo service

| SERVICE | ENTITY | SỐ LƯỢNG |
| :---- | :---- | :---- |
| Identity và Seller | User, Customer, Seller, SellerApplication, CustomerAddress, Role, Permission, UserRole, RolePermission | 9 |
| Product | Category, Product, Review | 3 |
| Cart | Cart, CartItem | 2 |
| Payment | Payment, Refund | 2 |
| Finance | Settlement, Wallet, WalletTransaction, Withdrawal | 4 |
| Order Inventory Fulfillment | CheckoutSession, Order, OrderItem, Inventory, Reservation, StockMovement | 6 |
| Notification | Notification | 1 |
| Reporting | OrderReportProjection, FinancialReportProjection, InventoryReportProjection | 3 |

Tổng cộng 30 entity: Identity 9, Product 3, Cart 2, Payment 2, Finance 4, Order Inventory Fulfillment 6, Notification 1 và Reporting 3\. Bốn bảng RBAC được tính trong mô hình Identity; bảng kỹ thuật mục 6 không tính vào tổng. Giữ 8 service và Reporting tạm hoãn; Search không là service riêng trong MVP.

## 3 Entity Data Dictionary chi tiết

Tên field dùng snake\_case tiếng Anh. Mỗi entity có mục đích, danh sách trường và ràng buộc. Các nhóm trường thời gian hoặc snapshot có thể tách thành cột riêng khi triển khai migration.

### 3.1 Identity và Seller với User và RBAC

Identity sở hữu User và RBAC trong PostgreSQL. Mỗi User (user\_id/email/phiên) là một tài khoản riêng thuộc loại CUSTOMER, SELLER hoặc ADMIN. Customer/Seller dùng shared PK/FK nhưng không thể cùng tồn tại dưới một user\_id; ADMIN không có hai hồ sơ đó. API kiểm tra vai trò hiện hành, loại tài khoản, trạng thái và ownership. Cấu trúc chung của bảng users không đồng nghĩa đăng nhập/định danh dùng chung nhiều vai trò.

Một User có tối đa một role đang hiệu lực; người dùng nhiều vai trò cần nhiều tài khoản và email độc lập. CUSTOMER được cấp khi tạo User+Customer; SELLER chỉ cấp sau duyệt; ADMIN chỉ do quy trình nội bộ trên tài khoản riêng. Không có gộp quyền, không tạo hồ sơ Customer cho User SELLER hoặc ngược lại.

Giá trị nghiệp vụ đã chốt theo [parameters.md](parameters.md), thay các giá trị cũ cùng chủ đề trong đặc tả; đây là yêu cầu triển khai, không phải migration đã chạy. P-01 vẫn tạm 15 phút chưa chốt; N-09/N-11 và phạm vi Reporting/Should/Could không đổi. P-37 chốt ngày 19/11/2026: tìm tên sản phẩm và tên cửa hàng không phân biệt dấu tiếng Việt và hoa thường, theo §5.2.

#### 3.1.1 User

Bảng users chứa credential riêng cho mỗi tài khoản Customer/Seller/Admin; không có credential trùng tại các bảng hồ sơ.

| FIELD | TYPE | REQ | KEY | GIẢI THÍCH VÀ NGHIỆP VỤ |
| :---- | :---- | :---- | :---- | :---- |
| user\_id | UUID | Có | PK | Định danh duy nhất của một tài khoản; JWT sub và các actor/recipient của tài khoản tương ứng. Một người có nhiều vai trò phải có nhiều user\_id khác nhau. |
| email | VARCHAR(254) | Có | UQ | Chuẩn hóa trim và lowercase trước lưu; UNIQUE(email), không đổi email trong MVP. |
| auth\_method | ENUM | Có | CHECK | LOCAL / GOOGLE; GOOGLE chỉ cho tài khoản Customer trong MVP; Seller/Admin dùng LOCAL. |
| password\_hash | TEXT | ĐK | CHECK | LOCAL bắt buộc; GOOGLE để NULL. Chỉ lưu hash mật khẩu. |
| google\_subject | VARCHAR(255) | ĐK | UQ | GOOGLE bắt buộc và duy nhất khi khác NULL; LOCAL để NULL. |
| full\_name | VARCHAR(150) | Có | — | Họ tên của tài khoản tương ứng; với User Seller là tên cửa hàng. Không dùng chung họ tên giữa các tài khoản Customer/Seller/Admin. |
| phone\_number | VARCHAR(20) | Không | UQ\* | Chuẩn hóa định dạng; unique khi có. Customer phải bổ sung trước checkout; bắt buộc khi nộp hồ sơ Seller và là số liên hệ của cửa hàng (không có sellers.contact\_phone). |
| status | ENUM | Có | CHECK | ACTIVE / DISABLED; là một phần của cặp trạng thái tài khoản bên dưới, không phải trục khóa độc lập. DISABLED chặn phiên bảo vệ sau khi đã đánh giá nghĩa vụ đến muộn theo G-14. |
| disabled\_reason | TEXT | ĐK | — | Bắt buộc khi DISABLED, dùng lý do của đợt cấm hiện hành; không biểu diễn lệnh khóa riêng. |
| banned\_at | TIMESTAMPTZ | Không | — | Đầu đợt cấm hiện hành; không đổi khi cấm lặp hoặc J7 chạy lặp. |
| disabled\_at | TIMESTAMPTZ | Không | — | Lần đầu chuyển DISABLED trong cùng đợt cấm. |
| restore\_until | TIMESTAMPTZ | Không | — | disabled\_at + 30 ngày theo UTC; NULL khi đợt cấm chưa từng DISABLED. |
| authz\_version | BIGINT | Có | CHECK | Mặc định 1, tăng khi quyền hoặc trạng thái truy cập đổi để vô hiệu quyền trong token cũ. |
| refresh\_token\_hash | TEXT | ĐK | UQ | Hash refresh token hiện hành; NULL khi chưa có phiên hoặc đã thu hồi. Không lưu token nguyên văn. |
| refresh\_token\_expires\_at | TIMESTAMPTZ | ĐK | — | Hạn refresh token hiện hành theo UTC; kiểm tra trước mỗi lần refresh. TTL P-06 vẫn chưa chốt. |
| session\_id | UUID | ĐK | UQ | Mã phiên đăng nhập hiện hành; JWT sessionId phải khớp. Login thành công tạo mã mới, refresh giữ nguyên mã. Không phải CheckoutSession. |
| created\_at, updated\_at | TIMESTAMPTZ | Có | — | Dấu thời gian danh tính. |

Ràng buộc và các trường mới trong users (v5.1): account\_type VARCHAR(16) NOT NULL CHECK (account\_type IN ('CUSTOMER','SELLER','ADMIN')) — loại tài khoản không đổi sau tạo; avatar\_object\_key TEXT NULL — ảnh đại diện của tài khoản, với Seller đồng thời là logo cửa hàng. Không dùng sellers.logo\_object\_key; migration chuyển ảnh cũ vào users.avatar\_object\_key. UNIQUE(email chuẩn hóa) toàn hệ thống, Google chỉ dành cho Customer. Không gộp LOCAL/GOOGLE, không dùng một user\_id cho nhiều loại tài khoản; Customer/Seller shared PK/FK chỉ nối đến User đúng account\_type. Seller REJECTED giữ User/email, chỉ nộp lại qua xác minh email với SellerApplication MỚI, không tạo thêm User trùng email. Tên cửa hàng chỉ kiểm tra trùng Seller PENDING/APPROVED.

**Một phiên trên User:** ba trường `refresh_token_hash`, `refresh_token_expires_at`, `session_id` cùng NULL hoặc cùng có giá trị, được ràng buộc bằng CHECK; hai khóa UNIQUE cho phép nhiều User chưa có phiên. Login hợp lệ thay toàn bộ bộ ba trong một transaction, nên thiết bị đăng nhập trước mất cả quyền refresh và quyền dùng access token ở lần xác minh phiên tiếp theo. Không giữ phiên hiệu lực song song trong AuthSession. Login sai hoặc nguồn nghĩa vụ lỗi không được thay phiên đang có.

Refresh đọc cookie HttpOnly `refresh_token`, kiểm tra Origin, hash, hạn và quyền/nghĩa vụ hiện hành. Trong cùng transaction, ghi dấu hash cũ vào `rotated_refresh_tokens` rồi thay hash mới, giữ `session_id`, cập nhật hạn bằng thời điểm refresh thành công cộng TTL refresh đang cấu hình. Chỉ lưu hash, không trả hash/token qua hồ sơ User, API quản trị, log hoặc event.

`rotated_refresh_tokens` chỉ lưu dấu chống reuse, không cấp quyền. Token đã xoay của phiên hiện hành bị gửi lại → xóa bộ ba trên User, commit việc thu hồi rồi trả 401 INVALID_REFRESH_TOKEN. Token của phiên cũ đã bị login thay thế chỉ bị từ chối, không được thu hồi phiên mới. Giữ dấu toàn bộ chuỗi trong khi phiên còn hiệu lực, kể cả khi refresh gia hạn. Hai refresh cùng token được tuần tự hóa: một lần xoay thành công, lần còn lại là reuse và thu hồi chuỗi, không có khoảng chấp nhận token cũ.

Logout giữ ANY_AUTH (session own): xác minh Bearer hợp lệ và Origin, rồi đối chiếu cookie hiện tại hoặc dấu đã xoay với cả user_id/sessionId từ Bearer và `users.session_id` hiện hành trước khi xóa bộ ba. Cookie phiên cũ không ảnh hưởng phiên mới. Sau khi qua xác thực Bearer/Origin, cookie thiếu/không biết/không khớp vẫn trả 204 và clear cookie nhưng không thu hồi phiên khác. Thiếu/sai/hết hạn Bearer hoặc Bearer phiên đã bị thay trả 401; sai Origin trả 403, không thay dữ liệu. Đổi/reset mật khẩu hoặc chuyển DISABLED xóa bộ ba cùng transaction cập nhật User. Mọi login/refresh/logout/thu hồi phiên phải khóa hoặc cập nhật có điều kiện trên cùng User và kiểm tra lại hash/sessionId trước ghi; HTTP hỏi nghĩa vụ nằm ngoài transaction giữ khóa. Chi tiết tại [0003](../decisions/0003-mot-phien-refresh-tren-user.md).

**Một vòng đời cấm, không có scope USER/CUSTOMER/SELLER.** `accountStatus` là giá trị suy ra, không thêm cột hay enum “chỉ rút tiền”; `approval_status` của Seller vẫn riêng. Admin có `account:moderate` chỉ cấm tài khoản Customer hoặc Seller APPROVED đã chọn, không cấm ADMIN, không tác động tài khoản khác của cùng người và không có thao tác khóa khẩn cấp vượt nghĩa vụ.

| accountStatus suy ra | users.status | customers.status hoặc sellers.status |
| :---- | :---- | :---- |
| ACTIVE | ACTIVE | ACTIVE |
| BANNED | ACTIVE | BANNED |
| DISABLED | DISABLED | BANNED |

Với CUSTOMER/SELLER, chỉ ba cặp trên hợp lệ; ADMIN không có hồ sơ và không tham gia vòng đời UC-E03. Identity là nơi duy nhất chuyển cặp trạng thái, trong cùng local transaction với `authz_version`, AdminAuditLog và Outbox. `ban_reason` của hồ sơ giữ đến khi unban; `disabled_reason` phản ánh cùng lý do khi DISABLED. Không ghi một nửa cặp trạng thái.

Bất biến mốc của đợt cấm: ACTIVE có `banned_at = disabled_at = restore_until = NULL` và không còn lý do cấm hiện hành. BANNED luôn có `banned_at`; trước lần DISABLED đầu tiên, `disabled_at`/`restore_until` đều NULL, còn sau khi mở lại vì nghĩa vụ muộn thì giữ cả hai mốc cũ. DISABLED có đủ ba mốc và `restore_until = disabled_at + 30 ngày` theo UTC. Thời hạn chỉ giới hạn lệnh gỡ khi trạng thái hiện tại là DISABLED; BANNED do nghĩa vụ muộn vẫn được gỡ bất kỳ lúc nào, dù mốc cũ đã qua.

**Nghĩa vụ hiện tại (G-14):**

- Order chưa kết thúc: `AWAITING_PAYMENT`, `PAID`, `PACKING`, `DELIVERED`. `CONFIRMED`, `CANCELLED`, `PAYMENT_FAILED`, `EXPIRED` không tự giữ phiên chỉ vì lịch sử đơn; Payment/Refund/Finance liên quan vẫn tính riêng.
- Payment `PENDING`, `SUCCESS` chưa áp dụng xong vào Order, hoặc hoàn tiền muộn chưa kết thúc đều giữ nghĩa vụ. Refund `PENDING` hoặc `FAILED` chưa giải quyết vẫn tính kể cả Order CANCELLED; bao gồm `FULL_CHECKOUT` khi CheckoutSession đã đóng.
- Seller còn Settlement `PENDING`/`PENDING_REFUND`, hiệu ứng tài chính chưa áp dụng, `pendingBalanceVnd > 0` hoặc `availableBalanceVnd > 0` đều còn nghĩa vụ. Kiểm tra cả trạng thái/hiệu ứng, không chỉ tiền: Settlement bằng 0 hoặc event đang chờ không được bỏ qua. Không rút pending.
- Timeout, nguồn lỗi, dữ liệu thiếu/không nhất quán không phải xác minh “không có nghĩa vụ”; không chuyển DISABLED hoặc giả số dư bằng 0.

Identity đọc nghĩa vụ trước khi commit ban qua các **contract đặc tả** tại [API §7.1/7.1.2](api_specs.md#71-internal-api-đồng-bộ): `GET /internal/v1/orders/accounts/{userId}/obligations?accountType=CUSTOMER|SELLER` (consumer duy nhất Identity), trả `{hasOpenOrders,openOrderIds,hasPendingPayment,hasUnresolvedRefund,evaluatedAt,orderStateVersion}`; Order tổng hợp đúng owner/CheckoutSession và xác minh Payment/Refund qua API Payment, không join DB. Seller đọc thêm `GET /internal/v1/finance/sellers/{sellerId}/obligations`, trả `{hasPendingFinance,pendingBalanceVnd,availableBalanceVnd,evaluatedAt,financeStateVersion}`; Finance kiểm tra cả settlement/hiệu ứng chưa áp dụng. Đây không phải xác nhận endpoint code tồn tại. 404 của tài khoản/ví đáng lẽ tồn tại hoặc lỗi nguồn khiến Identity trả 503, không ghi ban một phần.

**Chuyển trạng thái và khôi phục:** ban ACTIVE → BANNED nếu còn nghĩa vụ, ngược lại → DISABLED khi mọi nguồn xác minh hết. Lập `banned_at` ở đầu đợt; lần đầu DISABLED lập `disabled_at` và `restore_until`. Ban lặp/J7 lặp không đổi mốc hoặc phát sự kiện lặp. J7 — **Đối soát trạng thái tài khoản bị cấm**, owner Identity, đánh giá định kỳ cả BANNED và DISABLED: BANNED → DISABLED khi hết nghĩa vụ; DISABLED → BANNED khi nghĩa vụ đến muộn, chỉ mở quyền giới hạn, tuyệt đối không tự unban. Khi lại DISABLED, giữ mốc đầu của cùng đợt cấm. Không chuyển ACTIVE bằng J7; không đặt SLA/chu kỳ số học mới.

Gỡ BANNED bất kỳ lúc nào, kể cả quá 30 ngày nếu chưa từng DISABLED. Gỡ DISABLED khi `now <= restore_until`; sau hạn trả 409 `ACCOUNT_RESTORE_WINDOW_EXPIRED`, không xóa tài khoản/dữ liệu/tiền hoặc đổi trạng thái Order. ACTIVE unban thành công idempotent. Unban hợp lệ không cần nguồn Order/Finance, nhưng kiểm tra local account/Seller; chuyển về ACTIVE, xóa các mốc/lý do hiện hành, giữ lịch sử trong audit. Đợt cấm mới sau unban có mốc mới. Unban không phục hồi đơn hủy, không sửa Product HIDDEN/REMOVED, không cấp quyền Seller chưa duyệt; thời hạn này độc lập với khôi phục Product.

HTTP liên service ngoài transaction giữ khóa; trước commit kiểm tra version và đợt cấm còn hiện hành để J7 không ghi đè Admin unban. Login/refresh dùng cùng đánh giá, kể cả DISABLED phải kiểm tra nghĩa vụ đến muộn trước khi từ chối `ACCOUNT_DISABLED`. Nguồn lỗi: job giữ trạng thái và retry an toàn; request cần xác minh trả 503, không cấp quyền rộng hơn. Snapshot giữa các service không nguyên tử; nghĩa vụ/hiệu ứng đến trễ phải được giữ bền vững và mở lại quyền giới hạn, không mất tiền.

**Quyền suy ra, không phải trạng thái lưu mới:** ACTIVE dùng `FULL` nếu đủ role/approval; DISABLED hoặc Seller PENDING/REJECTED dùng `LOGIN_BLOCKED`. BANNED Customer có đơn tồn hoặc Refund FAILED hết lượt cần Customer thử lại dùng `OPEN_ORDERS_ONLY`; quyền `refund:retry:own` chỉ trên Refund của Payment đúng chủ, kể cả FULL_CHECKOUT/phiên đóng. Seller có đơn và available dùng `ORDERS_AND_WITHDRAWAL`, chỉ đơn `OPEN_ORDERS_ONLY`, chỉ available `WITHDRAWAL_ONLY`. Chỉ còn nghĩa vụ hệ thống/pending không có tác vụ đơn/rút/retry dùng `OBLIGATIONS_READ_ONLY`. Mỗi request tính lại allowlist/ownership/state.

BANNED Customer chỉ xem đơn/phiên và Payment/Refund gắn nghĩa vụ của mình, được hủy/xác nhận nếu state machine cho phép; không thanh toán AWAITING_PAYMENT, dùng giỏ/checkout/review hay xem toàn bộ lịch sử kết thúc. J1/J2/J2b vẫn xử lý. BANNED Seller chỉ xem/xử lý đơn tồn và tiền liên quan: pending-only chỉ xem tiến độ; available-only chỉ ví/rút/biên nhận liên quan; không quản lý Product/Inventory, nhận đơn mới hay thống kê chung. Public browsing vẫn công khai, không mở quyền mua authenticated; không đọc Order người khác. Ngoài allowlist trả `ACCOUNT_BANNED`.

Tái sử dụng event `customer.banned/unbanned`, `seller.banned/unbanned`, `user.disabled/enabled` cùng envelope/version/dedupe; payload liên quan có `accountStatus`, `accessMode`, `bannedAt`, `disabledAt`, `restoreUntil`. `user.enabled` do nghĩa vụ đến muộn mang `accountStatus=BANNED`, không phải unban; chỉ lệnh unban phát `*.unbanned`. Consumer không tự hủy Order khi `user.disabled`, Finance không đóng băng available do ban; Payment/Refund/job vẫn chạy. Notification dùng type hiện có, phân biệt mở giới hạn với khôi phục đầy đủ và nêu deadline khi có.

#### 3.1.2 Customer

Customer kế thừa User qua customer\_id=user\_id, chỉ khi users.account\_type=CUSTOMER. Không được tạo Customer trên tài khoản SELLER/ADMIN, không chồng hồ sơ.

| FIELD | TYPE | REQ | KEY | GIẢI THÍCH VÀ NGHIỆP VỤ |
| :---- | :---- | :---- | :---- | :---- |
| customer\_id | UUID | Có | PK \+ FK | REFERENCES users(user\_id) ON DELETE RESTRICT; đồng thời là định danh dùng ở Cart, Order, Review. |
| status | ENUM | Có | CHECK | ACTIVE / BANNED; cùng users.status suy ra accountStatus theo 3.1.1. BANNED của hồ sơ giữ cả khi accountStatus DISABLED. |
| ban\_reason | TEXT | ĐK | — | Lý do đợt cấm theo danh sách P-21d; nhãn hoặc “Nhãn: mô tả”, 1–1.000 ký tự, Khác phải có mô tả không rỗng; Customer chỉ Gian lận/lừa đảo hoặc Khác. Giữ đến unban. |
| created\_at, updated\_at | TIMESTAMPTZ | Có | — | Thời điểm tạo và cập nhật hồ sơ Customer. |

Tạo User account\_type CUSTOMER, Customer và UserRole CUSTOMER nguyên tử. Chỉ accountStatus ACTIVE được mua bình thường. BANNED dùng quyền theo nghĩa vụ hiện tại tại 3.1.1/G-14, không dựa lịch sử từng thanh toán; hết nghĩa vụ được chuyển DISABLED. Order terminal nhưng Refund còn dở vẫn giữ nghĩa vụ, có thể chỉ còn `OBLIGATIONS_READ_ONLY`.

#### 3.1.3 Seller

Seller kế thừa User qua seller\_id=user\_id của tài khoản SELLER riêng. Tên cửa hàng \= users.full\_name, số liên hệ \= users.phone\_number, logo \= users.avatar\_object\_key (ảnh đại diện); không còn sellers.seller\_name/contact\_phone/(bỏ) logo\_object\_key — chuyển sang users.avatar\_object\_key.

| FIELD | TYPE | REQ | KEY | GIẢI THÍCH VÀ NGHIỆP VỤ |
| :---- | :---- | :---- | :---- | :---- |
| seller\_id | UUID | Có | PK \+ FK | REFERENCES users(user\_id) ON DELETE RESTRICT; định danh shop trong Product, Order, Wallet. |
| description | TEXT | Không | — | Mô tả shop. |
| (bỏ) logo\_object\_key — chuyển sang users.avatar\_object\_key | TEXT | Không | — | Không còn cột logo riêng ở sellers. Chuyển dữ liệu hiện có sang users.avatar\_object\_key rồi xóa cột cũ. |
| status | ENUM | Có | CHECK | ACTIVE / BANNED; chỉ Seller APPROVED được cấm; cùng users.status suy ra accountStatus theo 3.1.1. |
| ban\_reason | TEXT | ĐK | — | Lý do P-21d, nhãn hoặc “Nhãn: mô tả”, 1–1.000 ký tự; Khác phải có mô tả không rỗng, giữ khi BANNED/DISABLED đến unban. |
| approval\_status | ENUM | Có | CHECK | PENDING / APPROVED / REJECTED. Seller.approval\_status là trạng thái đơn đăng ký mới nhất; mỗi lần nộp có SellerApplication riêng, không chuyển REJECTED → PENDING trên cùng application. |
| rejection\_reason | TEXT | ĐK | — | Bắt buộc khi REJECTED, giữ riêng mỗi SellerApplication và audit. P-21d: Thông tin đăng ký không chính xác/không nhất quán; Thông tin gian hàng không phù hợp; Khác. Nhãn hoặc “Nhãn: mô tả”, 1–1.000 ký tự; Khác có mô tả không rỗng; không thêm KYC/giấy phép. |
| registered\_at, reviewed\_at | TIMESTAMPTZ | ĐK | — | Thời điểm nộp/xử lý hồ sơ mới nhất, lấy từ SellerApplication; không ghi đè lịch sử lần nộp cũ. |
| reviewed\_by\_user\_id | UUID | ĐK | FK | REFERENCES users(user\_id); Admin có seller:approve tại thời điểm duyệt, lưu audit. |
| created\_at, updated\_at | TIMESTAMPTZ | Có | — | Thời điểm tạo và chỉnh sửa hồ sơ. |

Mỗi tài khoản Seller có seller\_id=user\_id và mỗi lần nộp có SellerApplication BẤT BIẾN riêng: application\_id UUID PK; seller\_id UUID FK; submission\_no INT (UNIQUE seller\_id,submission\_no); status PENDING/APPROVED/REJECTED; snapshot\_full\_name, snapshot\_phone, description; submitted\_at, reviewed\_at, reviewed\_by\_user\_id, rejection\_reason. Chỉ một application PENDING tại một thời điểm (partial unique). Seller PENDING/REJECTED không đăng nhập. Đăng ký lại sau REJECTED cần xác minh quyền sở hữu email trên kênh public, giữ user\_id/email nhưng tạo application\_id mới với status PENDING; application cũ giữ REJECTED và lý do, không cập nhật lại. ADMIN chỉ duyệt application PENDING của Seller khác tài khoản Admin, cập nhật Seller APPROVED \+ cấp UserRole SELLER và phát outbox tạo Wallet đúng một lần. Không tự tạo Customer, không chấp nhận login Seller trước duyệt.

Quyền bán bình thường: accountStatus ACTIVE, account\_type SELLER, Seller APPROVED và UserRole SELLER hiện hành. BANNED còn nghĩa vụ đơn/thanh toán/hoàn/tài chính hoặc pending/available được phiên giới hạn theo 3.1.1, không chỉ kiểm tra đơn và available. Seller PENDING/REJECTED luôn chặn login dù chưa bị cấm. ADMIN là tài khoản riêng, giữ guard tự duyệt, audit và ownership.

- Seller mới PENDING không đăng nhập; nếu REJECTED thì nộp một SellerApplication MỚI, giữ lịch sử.
- Chỉ được cấm Seller APPROVED; PENDING/REJECTED không được cấm.
- Seller APPROVED/BANNED chỉ xử lý nghĩa vụ tồn theo accessMode; pending-only không rút, available được rút theo WD-03, kể cả ngoại lệ toàn bộ số dư nhỏ.
- Không tự hủy các đơn còn dở chỉ vì Seller bị cấm; J4 và các quy tắc hủy thông thường vẫn áp dụng.

P-21d cấm tài khoản dùng nhãn Gian lận/lừa đảo; Kinh doanh hàng cấm; Kinh doanh hàng giả/vi phạm sở hữu trí tuệ; Khác. Lý do kinh doanh chỉ cho Seller; Customer dùng Gian lận/lừa đảo hoặc Khác. Ba danh sách quản trị độc lập theo [parameters.md](parameters.md), lưu reason TEXT nhãn hoặc “Nhãn: mô tả”, tổng 1–1.000 ký tự; Khác có mô tả không rỗng sau trim. Không thêm enum/reasonCode/reasonNote và không áp giới hạn 500 của P-21a/b.

#### 3.1.4 CustomerAddress

Sổ địa chỉ cho Customer. Mỗi Order lưu snapshot nên sửa hoặc xóa địa chỉ gốc không đổi đơn cũ.

| FIELD | TYPE | REQ | KEY | GIẢI THÍCH VÀ NGHIỆP VỤ |
| :---- | :---- | :---- | :---- | :---- |
| address\_id | UUID | Có | PK | Mã địa chỉ. |
| customer\_id | UUID | Có | FK | Tham chiếu Customer.customer\_id cùng service. |
| recipient\_name | VARCHAR(150) | Có | — | Tên người nhận. |
| recipient\_phone | VARCHAR(20) | Có | — | Số điện thoại người nhận. |
| province\_code | VARCHAR(32) | Có | — | Mã tỉnh/thành từ danh mục địa giới P-26. |
| ward\_code | VARCHAR(32) | Có | — | Mã phường/xã hợp lệ trong tỉnh/thành. |
| address\_detail | VARCHAR(300) | Có | — | Địa chỉ chi tiết. |
| last\_used\_at | TIMESTAMPTZ | Không | — | Chọn địa chỉ gần nhất khi checkout. |
| created\_at | TIMESTAMPTZ | Có | — | Xác định địa chỉ đầu tiên nếu chưa dùng lần nào. |

**Ràng buộc:** Chỉ Customer sở hữu qua customer\_id. Tối đa 3 địa chỉ hiện có (P-09), kiểm tra nguyên tử kể cả thêm đồng thời; xóa hẳn không còn tính giới hạn. Địa giới P-26 hai cấp: tỉnh/thành và phường/xã/đặc khu thuộc tỉnh/thành, không quận/huyện. Identity seed danh mục tĩnh trong DB gồm code/name, quan hệ thuộc tỉnh và phiên bản; API danh mục, validation ward thuộc province và đọc tên dùng cùng dữ liệu, không RegionProvider ngoài. Nguồn/phiên bản/thời điểm dữ liệu phải ghi khi chuẩn bị seed, dataset cụ thể chưa chọn. Địa chỉ chỉ thêm/sửa/xóa tại hồ sơ, không sửa ở checkout; hard delete không đổi Order đã snapshot. Mặc định chọn last\_used\_at mới nhất, chưa dùng thì created\_at nhỏ nhất; cho xóa địa chỉ cuối cùng. Một checkout dùng một địa chỉ cho N đơn; sau commit cập nhật last\_used\_at.

**Đối chiếu:** UC-A06 và UC-B04 kế thừa.

#### 3.1.5 Role

Danh mục vai trò chuẩn hóa; MVP seed CUSTOMER, SELLER, ADMIN và không mở CRUD vai trò công khai.

| FIELD | TYPE | REQ | KEY | GIẢI THÍCH VÀ NGHIỆP VỤ |
| :---- | :---- | :---- | :---- | :---- |
| role\_id | UUID | Có | PK | Định danh vai trò. |
| code | VARCHAR(50) | Có | UQ | CUSTOMER / SELLER / ADMIN, bất biến trong MVP. |
| name, description | VARCHAR / TEXT | Có / Không | — | Tên hiển thị và ý nghĩa tập quyền. |
| created\_at | TIMESTAMPTZ | Có | — | Thời điểm seed. |

#### 3.1.6 Permission

Một quyền là một hành động có phạm vi tài nguyên. Phạm vi own phải được kiểm tra tại service sở hữu dữ liệu.

| FIELD | TYPE | REQ | KEY | GIẢI THÍCH VÀ NGHIỆP VỤ |
| :---- | :---- | :---- | :---- | :---- |
| permission\_id | UUID | Có | PK | Định danh quyền. |
| code | VARCHAR(100) | Có | UQ | Ví dụ cart:manage:own, order:fulfill:own, seller:approve. |
| description | TEXT | Có | — | Hành động và điều kiện sở hữu; không dùng tên màn hình làm quyền. |
| created\_at | TIMESTAMPTZ | Có | — | Thời điểm seed. |

#### 3.1.7 UserRole

Giữ bảng UserRole cho RBAC nhưng mỗi user\_id chỉ có tối đa một vai trò đang hiệu lực. Thêm UNIQUE(user\_id) WHERE revoked\_at IS NULL; role đang hiệu lực phải phù hợp users.account\_type, không gắn CUSTOMER/ADMIN vào tài khoản SELLER. Seller PENDING/REJECTED chưa được cấp SELLER role hiệu lực.

| FIELD | TYPE | REQ | KEY | GIẢI THÍCH VÀ NGHIỆP VỤ |
| :---- | :---- | :---- | :---- | :---- |
| user\_id | UUID | Có | PK \+ FK | REFERENCES users(user\_id). |
| role\_id | UUID | Có | PK \+ FK | REFERENCES roles(role\_id); PK(user\_id,role\_id), UNIQUE(user\_id) WHERE revoked\_at IS NULL; role hiệu lực phù hợp account\_type. |
| granted\_by\_user\_id | UUID | Không | FK | REFERENCES users(user\_id); NULL cho bootstrap/đăng ký hệ thống. |
| granted\_at | TIMESTAMPTZ | Có | — | Ngày cấp quyền. |
| revoked\_at | TIMESTAMPTZ | Không | — | NULL là còn hiệu lực; cấp lại cập nhật cùng bản ghi, audit giữ lịch sử. |

#### 3.1.8 RolePermission

RolePermission vẫn nhiều-nhiều; một User chỉ nhận quyền của một vai trò hiện hành, không hợp nhất quyền đa vai trò.

| FIELD | TYPE | REQ | KEY | GIẢI THÍCH VÀ NGHIỆP VỤ |
| :---- | :---- | :---- | :---- | :---- |
| role\_id | UUID | Có | PK \+ FK | REFERENCES roles(role\_id). |
| permission\_id | UUID | Có | PK \+ FK | REFERENCES permissions(permission\_id); PK(role\_id, permission\_id). |
| created\_at | TIMESTAMPTZ | Có | — | Ngày gán quyền trong seed/migration có kiểm soát. |

#### 3.1.9 Ma trận quyền và quan hệ kế thừa

users 1 → 0..1 Customer HOẶC 0..1 Seller (không đồng thời), theo users.account\_type; ADMIN chỉ có User \+ UserRole ADMIN. Seller 1 → N SellerApplication (mỗi lần nộp hồ sơ mới, lưu lịch sử). UserRole tối đa một role đang hiệu lực trên mỗi user\_id; role ↔ permission vẫn N:N. Không có User đa vai trò.

| PERMISSION | CUSTOMER | SELLER | ADMIN | ĐIỀU KIỆN |
| :---- | :---- | :---- | :---- | :---- |
| account:read:own / account:update:own | Có | Có | Có | Tự phục vụ đầy đủ chỉ khi accountStatus ACTIVE, lấy sub; BANNED chỉ đọc context/chi tiết tối thiểu phục vụ nghĩa vụ theo allowlist G-14, không cấp quyền sửa hồ sơ chung; không sửa role/status/email. |
| customer:profile:own | Có | — | — | Hồ sơ Customer của sub, accountStatus ACTIVE; không mở quyền hồ sơ chung cho phiên giới hạn. |
| cart:manage:own / checkout:create:own / payment:create:own | Có | — | — | Customer ACTIVE, đúng account\_type CUSTOMER, giỏ/checkout thuộc sub; Customer BANNED không được thực hiện. |
| order:read:own / order:cancel:own / order:confirm:own / refund:retry:own | Có | — | — | Customer đúng chủ nghĩa vụ; BANNED xem/hủy/xác nhận theo state và retry đúng Refund FAILED hết lượt qua OPEN_ORDERS_ONLY; Payment/Refund chỉ chờ hệ thống thì chỉ đọc. Không giỏ/thu mới/review/lịch sử ngoài nghĩa vụ. |
| review:write:own | Có | — | — | Order CONFIRMED, Customer ACTIVE; Customer BANNED không được tạo đánh giá mới. |
| Đăng ký tài khoản Seller / nộp SellerApplication (PUBLIC, không cấp permission) | — | PUBLIC | — | Tạo tài khoản Seller mới và đăng ký lại sau REJECTED đi qua API PUBLIC có kiểm tra email, không phải quyền của UserRole. Seller PENDING/REJECTED không có session để gọi self-service; tài khoản Customer/Admin không được tự cấp Seller. |
| seller:profile:own / product:manage:own / inventory:manage:own | — | Có | — | Chỉ Seller APPROVED/ACTIVE quản lý shop và tồn kho; khi BANNED không product/inventory, chỉ có nghiệp vụ hoàn tất đơn đang dở/rút khả dụng theo G-14. |
| order:fulfill:own | — | Có | — | Seller APPROVED/ACTIVE; Seller APPROVED/BANNED chỉ được xử lý các Order chưa hoàn tất đã tồn tại, đúng seller\_id; không thao tác ngoài allowlist. |
| wallet:read:own / withdrawal:create:own | — | Có | — | Ví đúng owner\_id; Seller APPROVED/ACTIVE sử dụng bình thường; Seller APPROVED/BANNED có phiên giới hạn vẫn được xem ví và rút available theo WD-01/WD-09; pending không rút. |
| seller:approve / account:moderate / product:moderate | — | — | Có | Admin dùng tài khoản riêng; luôn kiểm tra quyền và ghi audit. Cấm Seller chỉ khi APPROVED; không cho tự duyệt cùng user\_id. |
| category:manage / operations:read:any / operations:annotate | — | — | Có | Đọc Refund có lọc PII, không gọi gateway query/retry; annotate chỉ ghi chú đối soát khoản THU, không đổi Refund/cờ Customer, số dư hoặc Payment. |
| notification:read:own | Có | Có | Có | recipient\_user\_id = sub; không đọc thông báo User khác; phiên giới hạn chỉ thông báo liên quan nghĩa vụ/trạng thái tài khoản theo allowlist. |

Policy account:read/update:own chỉ trong phạm vi account\_type và trạng thái hợp lệ. seller:application:own không phải quyền cấp cho mọi User ACTIVE: chỉ Seller onboarding hoặc luồng xác minh email đăng ký mới sau REJECTED. Không có hợp quyền nhiều role. `accessMode`/permission không thay ownership và state machine; `OBLIGATIONS_READ_ONLY` không cho tác vụ đơn hoặc rút. Seller/Customer BANNED chỉ truy cập endpoint/bản ghi phục vụ nghĩa vụ theo 3.1.1/G-14/WD-09; public Product/Seller lọc theo G-06.

### 3.2 Product

Product Service (tên cũ: Catalog) sở hữu danh mục, sản phẩm, media dạng JSONB và review. Tìm kiếm sản phẩm nằm tại service này. Data store PostgreSQL và object storage cho file media.

#### 3.2.1 Category

Danh mục phẳng một cấp; bắt buộc seed đúng 10 ngành hàng P-21e kể cả chưa làm màn quản trị Should. category\_id là UUID, số 01–10 chỉ thứ tự trong tài liệu; Category không có tỷ lệ hoa hồng riêng.

| FIELD | TYPE | REQ | KEY | GIẢI THÍCH VÀ NGHIỆP VỤ |
| :---- | :---- | :---- | :---- | :---- |
| category\_id | UUID | Có | PK | Mã danh mục. |
| category\_name | VARCHAR(150) | Có | UQ | Tên hiển thị duy nhất. |
| status | ENUM | Có | CHECK | VISIBLE / HIDDEN. |
| created\_at, updated\_at | TIMESTAMPTZ | Có | — | Ngày tạo và thay đổi. |

**Ràng buộc:** Không xóa danh mục đang được Product tham chiếu. Quản trị danh mục động là SHOULD theo bản nguồn.

| STT | Ngành hàng | Ví dụ sản phẩm |
| :---- | :---- | :---- |
| 01 | Thời trang (Fashion) | Quần áo, giày dép, túi xách |
| 02 | Điện thoại & Phụ kiện (Phones & Accessories) | Điện thoại, ốp lưng, sạc |
| 03 | Máy tính & Thiết bị điện tử (Electronics) | Laptop, tai nghe, chuột |
| 04 | Nhà cửa & Đời sống (Home & Living) | Nội thất, đồ gia dụng, trang trí |
| 05 | Sắc đẹp & Chăm sóc cá nhân (Beauty & Personal Care) | Mỹ phẩm, skincare, chăm sóc tóc |
| 06 | Sức khỏe (Health) | Dụng cụ chăm sóc sức khỏe, thiết bị y tế thông thường |
| 07 | Mẹ & Bé (Mother & Baby) | Đồ chơi trẻ em, quần áo trẻ em, đồ dùng em bé |
| 08 | Thực phẩm & Đồ uống (Food & Beverage) | Đồ ăn đóng gói, bánh kẹo, đồ uống |
| 09 | Sách & Văn phòng phẩm (Books & Stationery) | Sách, bút, vở, dụng cụ học tập |
| 10 | Thể thao & Ngoài trời (Sports & Outdoors) | Dụng cụ tập gym, đồ thể thao, camping |

**Đối chiếu:** UC-B01, UC-D01 và UC-E07 kế thừa.

#### 3.2.2 Product

Sản phẩm thuộc một Seller, một giá hiện tại, không có SKU hoặc ProductVariant. Tồn kho chính thức thuộc Order Inventory Fulfillment.

| FIELD | TYPE | REQ | KEY | GIẢI THÍCH VÀ NGHIỆP VỤ |
| :---- | :---- | :---- | :---- | :---- |
| product\_id | UUID | Có | PK | Mã sản phẩm. |
| seller\_id | UUID | Có | Ref | Người bán sở hữu; tham chiếu Identity/Seller. |
| category\_id | UUID | Có | FK | Danh mục sản phẩm trong Product Service. |
| product\_name | VARCHAR(255) | Có | — | Tên hiển thị và tìm kiếm. |
| description | TEXT | Không | CHECK | Mô tả tối đa 5.000 ký tự (P-08.5). |
| price\_vnd | BIGINT | Có | CHECK | Giá hiện tại \> 0; một Product/một giá. |
| status | ENUM | Có | CHECK | SELLING / HIDDEN / REMOVED. |
| removal\_reason | TEXT | Không | — | P-21d khi REMOVED: Sản phẩm bị cấm; Hàng giả/vi phạm sở hữu trí tuệ; Nội dung sai lệch/không phù hợp; Đăng trùng lặp/spam; Khác. Nhãn hoặc “Nhãn: mô tả”, 1–1.000 ký tự, Khác có mô tả không rỗng. |
| removed\_at | TIMESTAMPTZ | Không | — | Mốc xét điều kiện khôi phục 30 ngày. |
| media | JSONB | Có | — | Mảng metadata có thứ tự: object\_key, media\_type IMAGE/VIDEO, sort\_order, is\_cover; không lưu binary hoặc URL ký tạm thời. |
| created\_at, updated\_at | TIMESTAMPTZ | Có | — | Dấu thời gian đồng bộ và audit. |

Ràng buộc: price\_vnd >0; chỉ Product thỏa G-06 được mua. Media 1–9 tệp, tối đa 2 video (P-08.3); mỗi phần tử có loại/object\_key/thứ tự/MIME/dung lượng/thời lượng video, backend kiểm tra file thực. Dung lượng/định dạng/thời lượng giữ trạng thái riêng, chưa chốt bởi P-08.3. Ảnh bìa là IMAGE đầu tiên, chỉ video dùng placeholder; MVP không chuyển mã. Điểm là trung bình cộng Review, hiển thị 1 chữ số thập phân (P-12), không lưu cột riêng; tên reviewer che một phần. Seller chỉ sửa sản phẩm mình. Tìm theo product\_name không phân biệt dấu tiếng Việt và hoa thường: chuẩn hóa cả từ khóa và tên trước LIKE theo §5.2 (P-37, chốt 19/11/2026); lọc danh mục/khoảng giá, mặc định 20 sản phẩm/trang mới nhất (P-11).

Tạo mới UC-D01 sau validation/media: ghi Product HIDDEN cùng công việc provision bền vững trong local transaction; sau commit gọi `POST /internal/v1/inventory`. Order provision idempotent theo productId. Chỉ khi Inventory xác nhận và Seller/Product còn hợp lệ mới áp dụng trạng thái Seller yêu cầu. Timeout giữ một Product HIDDEN, retry cùng productId và Idempotency-Key, không tạo Product mới hay rollback giả xuyên DB. Seller bị ban hoặc Product REMOVED trong lúc chờ thì hoàn tất Inventory không tự bật SELLING. Lỗi validation/media trước chấp nhận tạo không lưu Product; câu “không lưu dở dang” không áp dụng cho Product hợp lệ HIDDEN đang đợi tồn. Cập nhật Product hiện có không tạo lại/provision Product mới. Dùng cơ chế [SAD §5.7/A.6](software-architecture.md), không thêm trạng thái, endpoint hoặc job provision thứ hai.

**Đối chiếu:** UC-B01, UC-D01 và UC-E04 kế thừa; tìm kiếm DB theo note.

#### 3.2.3 Review

Customer đã nhận hàng có thể đánh giá sản phẩm; giữ SHOULD theo bản nguồn.

| FIELD | TYPE | REQ | KEY | GIẢI THÍCH VÀ NGHIỆP VỤ |
| :---- | :---- | :---- | :---- | :---- |
| review\_id | UUID | Có | PK | Mã đánh giá. |
| customer\_id | UUID | Có | Ref | Tài khoản đã mua sản phẩm. |
| product\_id | UUID | Có | FK | Product được đánh giá. |
| order\_item\_id | UUID | Có | Ref | OrderItem làm căn cứ đã mua/đã xác nhận nhận. |
| rating | SMALLINT | Có | CHECK | Số nguyên 1–5. |
| content | TEXT | Không | CHECK | Tùy chọn, tối đa 1.000 ký tự (P-08.5). |
| media\_metadata | JSONB | Không | CHECK | Tối đa 5 tệp, tối đa 1 video (P-08.3); không tạo entity media mới. |
| created\_at, updated\_at | TIMESTAMPTZ | Có | — | Ngày đánh giá/sửa. |

**Ràng buộc: UNIQUE(customer\_id, product\_id). Cần review:write:own, hồ sơ Customer của sub và OrderItem thuộc Order CONFIRMED của Customer, product\_id khớp. Chỉ có SELLER không đủ quyền review; User có CUSTOMER vẫn được đánh giá các đơn mua hợp lệ. Rating 1–5; nội dung và media tùy chọn; ẩn review khi Product không hiển thị.**

**Đối chiếu:** UC-C01 và UC-B09 kế thừa.

### 3.3 Cart

Lưu ý định mua của Customer đã đăng nhập; không giữ tồn kho và không chốt giá. Data store PostgreSQL. Theo FRD UC-B03, khách phải đăng nhập mới được thêm vào giỏ, nên **không có giỏ của khách vãng lai** và không có hợp nhất giỏ khi đăng nhập (bỏ từ bản 4.6).

#### 3.3.1 Cart

Một giỏ cho mỗi Customer.

| FIELD | TYPE | REQ | KEY | GIẢI THÍCH VÀ NGHIỆP VỤ |
| :---- | :---- | :---- | :---- | :---- |
| cart\_id | UUID | Có | PK | Định danh giỏ. |
| customer\_id | UUID | Có | UQ \+ Ref | Customer sở hữu giỏ; mỗi Customer đúng một giỏ. |
| row\_version | BIGINT | Có | — | Chống cập nhật mất dữ liệu khi nhiều tab cùng thao tác. |
| created\_at, updated\_at | TIMESTAMPTZ | Có | — | Thời điểm tạo và chỉnh sửa. |

**Ràng buộc UNIQUE(customer\_id). Chỉ tài khoản USER account\_type=CUSTOMER có Customer ACTIVE, role CUSTOMER và customer\_id=sub mới quản lý giỏ. Tài khoản SELLER/ADMIN không dùng giỏ; cần tạo tài khoản Customer riêng để mua. Không tạo giỏ khách vãng lai.**

**Đối chiếu:** UC-B03 (điều kiện "Đã đăng nhập"), UC-B01; BRD 5.1.

#### 3.3.2 CartItem

Dòng sản phẩm với số lượng Customer muốn mua.

| FIELD | TYPE | REQ | KEY | GIẢI THÍCH VÀ NGHIỆP VỤ |
| :---- | :---- | :---- | :---- | :---- |
| cart\_item\_id | UUID | Có | PK | Mã dòng giỏ. |
| cart\_id | UUID | Có | FK | Giỏ chủ. |
| product\_id | UUID | Có | Ref | Product thuộc Product Service. |
| quantity | INTEGER | Có | CHECK | 1–99 đơn vị (P-10). |
| created\_at, updated\_at | TIMESTAMPTZ | Có | — | Dùng xử lý nhiều thao tác cập nhật. |

**Ràng buộc:** UNIQUE(cart\_id, product\_id), quantity 1–99; tối đa 50 sản phẩm khác nhau/giỏ. Kiểm tra giới hạn nguyên tử kể cả thêm đồng thời; cùng sản phẩm cộng vào dòng hiện có, tổng không vượt 99, không tạo dòng mới. Chỉ đúng owner thao tác. Hết hàng không biểu diễn bằng quantity 0; giá/tồn kiểm tra lại checkout. Mua ngay tối đa 99 của một sản phẩm, không tạo/đọc/trừ dòng giỏ (P-10).

**Trạng thái dòng giỏ** (không lưu DB, tính mỗi lần xem giỏ; giỏ hiển thị các dòng nhóm theo cửa hàng, mỗi nhóm sẽ thành một Order khi checkout):

| TRẠNG THÁI | ĐIỀU KIỆN | CHỌN ĐỂ THANH TOÁN? |
| :---- | :---- | :---- |
| IN\_STOCK (còn hàng) | Sản phẩm bán được (G-06) và tồn kho khả dụng ≥ số lượng | Có |
| INSUFFICIENT\_STOCK (không đủ hàng) | 0 \< khả dụng \< số lượng; hiển thị "Chỉ còn X" | Không, đến khi khách giảm số lượng |
| OUT\_OF\_STOCK (hết hàng) | Khả dụng \= 0 | Không |
| Ngừng bán | Sản phẩm không còn bán được | Tự xóa khỏi giỏ khi mở giỏ, kèm thông báo |

**Đối chiếu:** UC-B03, UC-B04 và UC-B06 kế thừa; xem mục 5.3.

### 3.4 Payment

Sở hữu một khoản thu chung cho CheckoutSession và nghĩa vụ hoàn về Customer. Data store PostgreSQL. Không có PaymentAllocation vì mỗi Order đã mang số tiền riêng bất biến trong cùng checkout. Thanh toán và hoàn tiền đi qua interface PaymentGateway; trong MVP cài đặt là bộ giả lập thanh toán nội bộ (mục 8.4), không có nhà cung cấp bên ngoài.c

#### 3.4.1 Payment

Một Payment logic cho toàn bộ lượt checkout, dù có nhiều Seller và nhiều Order.

| FIELD | TYPE | REQ | KEY | GIẢI THÍCH VÀ NGHIỆP VỤ |
| :---- | :---- | :---- | :---- | :---- |
| payment\_id | UUID | Có | PK | Mã giao dịch thanh toán. |
| checkout\_session\_id | UUID | Có | UQ \+ Ref | Một Payment logic trên một CheckoutSession. |
| customer\_id | UUID | Có | Ref | Customer sở hữu checkout. |
| amount\_vnd | BIGINT | Có | CHECK | Bằng CheckoutSession.total\_amount\_vnd và tổng Order.total\_amount\_vnd; \> 0\. |
| currency | CHAR(3) | Có | CHECK | VND. |
| provider, environment | VARCHAR | Có | — | SIMULATOR (bộ giả lập nội bộ) trong MVP; nếu sau MVP tích hợp cổng thật thì ghi mã cổng/môi trường thực, xác minh capture và đối soát trước khi phát Payment SUCCESS. |
| idempotency\_key | VARCHAR(128) | Có | UQ | Chống tạo khoản thu trùng cho một checkout. |
| status | ENUM | Có | CHECK | PENDING / SUCCESS / FAILED. |
| provider\_transaction\_id | VARCHAR(255) | Không | UQ\* | Mã giao dịch do bộ giả lập (hoặc cổng thật) trả về. |
| error\_code | VARCHAR(100) | Không | — | Mã lỗi khi có kết quả thất bại. |
| created\_at, completed\_at | TIMESTAMPTZ | ĐK | — | Ngày tạo và thời điểm có kết quả. |

**Ràng buộc:** Payment lấy tập Order bất biến từ API của Order service, xác minh customer và tổng tiền trước khi tạo. Cấm Customer hoặc Seller gửi số tiền tùy ý. Khi SUCCESS, phát sự kiện theo checkout\_session\_id để xử lý toàn bộ Order cùng lượt mua. Callback trùng không trừ kho hoặc gửi sự kiện nghiệp vụ lặp. FAILED rõ ràng kết thúc lượt checkout; mua lại tạo checkout mới. PENDING không được xem là FAILED. Trạng thái hoàn của Payment (chưa hoàn / hoàn một phần / hoàn toàn bộ) tính từ các Refund SUCCESS, không lưu cột riêng. Không tạo PaymentAttempt trong phạm vi này. Với tiền thật, Payment SUCCESS chỉ phát sau khi provider xác nhận đã thu/capture hợp lệ; Finance không suy ra tiền đã về tài khoản ngân hàng chỉ từ cờ này.

**Đối chiếu:** UC-B05, UC-B06 và UC-G02/G03 kế thừa; quan hệ N Order dùng chung Payment theo note.

#### 3.4.2 Refund

Hoàn toàn bộ tiền một Order bị hủy sau thanh toán, hoặc hoàn toàn bộ checkout khi nhận tiền muộn sau khi lượt checkout đã đóng.

| FIELD | TYPE | REQ | KEY | GIẢI THÍCH VÀ NGHIỆP VỤ |
| :---- | :---- | :---- | :---- | :---- |
| refund\_id | UUID | Có | PK | Mã nghĩa vụ hoàn. |
| payment\_id | UUID | Có | FK | Khoản thu chung gốc. |
| order\_id | UUID | ĐK | Ref \+ UQ\* | Bắt buộc với scope ORDER, NULL với FULL\_CHECKOUT; một nghĩa vụ hoàn hủy trên mỗi Order. |
| scope | ENUM | Có | CHECK | ORDER / FULL\_CHECKOUT. |
| amount\_vnd | BIGINT | Có | CHECK | ORDER: bằng Order.total\_amount\_vnd; FULL\_CHECKOUT: bằng Payment.amount\_vnd; \> 0\. |
| reason | ENUM | Có | CHECK | CUSTOMER\_CANCEL / SELLER\_REJECT / SELLER\_CANCEL (Order bị hủy hợp lệ vì Order đã chứng minh điều kiện giao dịch không hợp lệ khi áp dụng Payment SUCCESS; không phải Seller bị cấm sau capture) / SELLER\_TIMEOUT / LATE\_PAYMENT. Giữ nguyên literal enum. |
| status | ENUM | Có | CHECK | PENDING / SUCCESS / FAILED. |
| idempotency\_key | VARCHAR(128) | Có | UQ | Giữ nguyên qua retry của cùng nghĩa vụ hoàn. |
| provider\_refund\_id | VARCHAR(255) | Không | UQ\* | Mã hoàn do bộ giả lập (hoặc cổng thật) trả về. |
| retry\_count | INTEGER | Có | CHECK | DEFAULT 0, CHECK 0..3; số retry tự động đã phát trong chu kỳ, không gồm lần đầu/query. Tối đa 4 lần thực hiện/chu kỳ, chờ 1/5/15 phút từ lỗi trước; PENDING/timeout chỉ query. Reset khi Customer mở chu kỳ mới; lịch sử mọi chu kỳ giữ trong WorkerExecutionLog/Outbox. |
| retry\_cycle | INTEGER | Có | CHECK | DEFAULT 0, CHECK >=0; Customer mở chu kỳ bằng CAS expectedRetryCycle, tăng một lần, không tạo Refund/key mới. |
| customer\_retry\_required | BOOLEAN | Có | — | DEFAULT false; hết 3 retry vẫn FAILED →true và REFUND_RETRY_REQUIRED dedupe refundId + retryCycle. Customer chủ Payment mở chu kỳ mới reset count/cờ và ghi công việc bền vững trong cùng local transaction; thay cột xử lý tay Refund, không Admin. |
| created\_at, completed\_at | TIMESTAMPTZ | ĐK | — | Thời điểm tạo và kết quả. |

**Ràng buộc:** Payment phải SUCCESS trước khi phát lệnh hoàn. Xác minh Order thuộc đúng checkout của Payment; không hoàn theo Order từ checkout khác. FULL\_CHECKOUT chỉ dùng khi checkout đóng trước khi ghi nhận thu thành công, và loại trừ mọi nghĩa vụ ORDER cho cùng Payment. Khóa Payment để tạo nghĩa vụ hoàn nguyên tử; tổng các nghĩa vụ hoàn đã tạo kể cả FAILED chờ retry không vượt amount\_vnd. Retry tái dùng bản ghi, không mở nghĩa vụ mới. Timeout phải tra cứu trước retry. Không hỗ trợ hoàn một phần của một Order.

**Đối chiếu:** UC-B07/B08/G01/G02/G03; UC-E08 chỉ đọc. P-RF-01 áp dụng ORDER/FULL_CHECKOUT, Customer chủ Payment mới mở chu kỳ, không nhận số tiền/key gateway từ client. J5 vẫn query/áp SUCCESS muộn; khoảng 21 phút chỉ ước tính, không deadline. Không có Admin query/retry hoặc đánh dấu đã hoàn.

### 3.5 Finance

Finance quản lý quyền hưởng tiền của từng Order, số dư ví, lịch sử biến động và các giao dịch rút tiền. Gồm 4 entity: Settlement, Wallet, WalletTransaction, Withdrawal. Triển khai PostgreSQL. Theo FRD v5.2, rút tiền là thao tác giả lập hoàn tất ngay trong Finance; MVP không gọi provider chi trả bên ngoài, nên không có Payout, không có trạng thái chờ chi và không có ngăn tiền "đang rút". Payment/Refund và Order do service tương ứng sở hữu; Finance không cập nhật dữ liệu gốc của service khác. Ví là số dư nghĩa vụ nội bộ, không phải số dư ngân hàng.

#### 3.5.1 Settlement

Một Settlement cho một Order thuộc một Seller. Sau Payment SUCCESS hợp lệ, ghi nhận số tiền Seller và hoa hồng ở trạng thái chờ; chỉ chuyển thành quyền rút khi Order CONFIRMED.

| FIELD | TYPE | REQ | KEY | GIẢI THÍCH VÀ NGHIỆP VỤ |
| :---- | :---- | :---- | :---- | :---- |
| settlement\_id | UUID | Có | PK | Định danh quyết toán theo Order. |
| order\_id | UUID | Có | UQ \+ Ref | Một Settlement cho một Order của đúng một Seller (Commerce). |
| payment\_id | UUID | Có | Ref | Khoản Payment chung của CheckoutSession (Payment Service). |
| seller\_id | UUID | Có | Ref | Seller có quyền nhận tiền; kiểm tra sở hữu Order. |
| gross\_amount\_vnd | BIGINT | Có | CHECK | Giá trị Order đã chụp tại checkout; \> 0\. |
| commission\_bps\_snapshot | INTEGER | Có | CHECK | Sao chép snapshot Order, P-COM-01 chốt 500 bps = 5% mọi Seller/ngành hàng; Finance không tra Category/tính lại đơn cũ. |
| commission\_amount\_vnd | BIGINT | Có | CHECK | Hoa hồng tính từ gross theo tỷ lệ snapshot; ghi nhận ở ngăn chờ trước CONFIRMED. |
| seller\_amount\_vnd | BIGINT | Có | CHECK | Tiền thuộc Seller \= gross − commission; từ 0 đến gross. |
| status | ENUM | Có | CHECK | PENDING / PENDING\_REFUND / SETTLED / REFUNDED. |
| created\_at, settled\_at | TIMESTAMPTZ | ĐK | — | Thời điểm ghi nhận chờ và thời điểm quyết toán khi Order CONFIRMED. |

**Ràng buộc:** UNIQUE(order\_id). G \= Order.total\_amount\_vnd; C \= floor((G × r \+ 5000\) / 10000), S \= G − C, với r là bps. Khi PENDING, C và S là các khoản đang chờ, chưa được rút. PENDING\_REFUND khóa quyền chuyển khả dụng; REFUNDED khi nhận Refund SUCCESS. SETTLED chỉ được ghi khi Order CONFIRMED, không có nghĩa vụ hoàn đang chờ. Tạo Settlement và ghi WalletTransaction idempotent theo order\_id / payment\_id.

**Đối chiếu:** UC-B09, UC-D04, UC-D05 và UC-G04 trong FRD v5.2; Settlement là nguồn giải thích tiền chờ/quyết toán theo từng Order.

#### 3.5.2 Wallet

Một ví nội bộ cho mỗi Seller đã duyệt và đúng một ví nền tảng (seed) để ghi hoa hồng. Ví có ba ngăn (mục 8.3): chờ quyết toán (không được rút), khả dụng (được rút) và đã rút. Không có ngăn "đang rút" hay "reserved", vì rút tiền giả lập hoàn tất ngay trong một transaction. Chỉ ví SELLER được dùng cho Withdrawal.

| FIELD | TYPE | REQ | KEY | GIẢI THÍCH VÀ NGHIỆP VỤ |
| :---- | :---- | :---- | :---- | :---- |
| wallet\_id | UUID | Có | PK | Định danh ví số dư nội bộ. |
| owner\_type | ENUM | Có | CHECK | SELLER / PLATFORM. |
| owner\_id | UUID | ĐK | Ref | seller\_id khi SELLER; NULL khi PLATFORM. |
| currency | CHAR(3) | Có | CHECK | VND; một ví cho mỗi chủ thể và loại tiền. |
| pending\_balance\_vnd | BIGINT | Có | CHECK | Ngăn chờ quyết toán, \>= 0\. Seller: Σ seller\_amount\_vnd của các Settlement ở trạng thái PENDING và PENDING\_REFUND (gồm đơn đã hủy đang chờ hoàn tiền). Nền tảng: Σ commission\_amount\_vnd của cùng tập Settlement. Không được rút, không ứng trước. |
| available\_balance\_vnd | BIGINT | Có | CHECK | Ngăn khả dụng, \>= 0\. Seller: số tiền được phép rút. Nền tảng: hoa hồng đã chốt (chỉ ghi sổ, không rút trong MVP). |
| total\_withdrawn\_vnd | BIGINT | Có | CHECK | Ngăn đã rút, \>= 0; bằng Σ Withdrawal.amount\_vnd của ví. Luôn \= 0 với ví PLATFORM. |
| row\_version | BIGINT | Có | CHECK | Khóa lạc quan, chống lost update khi nhiều lần rút hoặc sự kiện cập nhật ví cùng lúc. |
| created\_at, updated\_at | TIMESTAMPTZ | Có | — | Thời điểm khởi tạo và thay đổi số dư. |

**Ràng buộc: UNIQUE(owner\_id, currency) WHERE owner\_type \= SELLER; UNIQUE(currency) WHERE owner\_type \= PLATFORM. Ví Seller được tạo với mọi số dư bằng 0 khi Admin duyệt Seller. Mọi số dư luôn \>= 0\. Không tự sửa số dư: mỗi thay đổi phải ghi WalletTransaction trong cùng transaction, và các trường số dư luôn bằng tổng delta của WalletTransaction của ví. Việc chuyển pending sang available chỉ theo Settlement SETTLED (Order CONFIRMED), không vì Payment SUCCESS. Refund không bao giờ trừ available hoặc total\_withdrawn của Seller (WD-06). Seller APPROVED/BANNED vẫn được rút số dư khả dụng theo phiên giới hạn, không tự đóng băng available do cấm SELLER; pending chỉ được chuyển sang khả dụng theo trạng thái Settlement và không được rút trước quyết toán (WD-09). Đối chiếu từng Seller (WD-11): Σ seller\_amount của Settlement SETTLED \= available\_balance\_vnd \+ total\_withdrawn\_vnd; Σ seller\_amount của Settlement PENDING và PENDING\_REFUND \= pending\_balance\_vnd.**

Pending/available và Settlement PENDING/PENDING\_REFUND/hiệu ứng chưa áp dụng giữ nghĩa vụ G-14 kể cả số tiền bằng 0. DTO Wallet (không phải cột số dư mới) giữ `minWithdrawalVnd=10000`, thêm `allowBelowMinimumFullWithdrawal: boolean`; ngoại lệ WD-03 thỏa thì `canWithdraw=true`, `withdrawDisabledReason=null`, UI khóa amount ở toàn bộ available. Không giả số dư 0 khi nguồn lỗi.

**Đối chiếu: UC-D05, UC-G04, UC-E08; quy tắc WD-01…WD-11 ở mục 8.3.**

#### 3.5.3 WalletTransaction

Nhật ký append-only mô tả vì sao pending, available hoặc total\_withdrawn thay đổi. Đây là sổ vận hành đơn giản theo delta, không phải hệ thống kế toán kép.

| FIELD | TYPE | REQ | KEY | GIẢI THÍCH VÀ NGHIỆP VỤ |
| :---- | :---- | :---- | :---- | :---- |
| wallet\_transaction\_id | UUID | Có | PK | Định danh bản ghi biến động số dư; chỉ thêm mới. |
| wallet\_id | UUID | Có | FK | Ví có số dư thay đổi, cùng Finance database. |
| settlement\_id | UUID | ĐK | FK | Bắt buộc với PAYMENT\_RECEIVED / SETTLEMENT\_RELEASED / REFUND\_DEBIT. |
| withdrawal\_id | UUID | ĐK | FK | Bắt buộc với WITHDRAWAL\_COMPLETED; NULL với các loại khác. |
| reference\_type | ENUM | Có | CHECK | PAYMENT / ORDER / REFUND / WITHDRAWAL. |
| reference\_id | UUID | Có | Ref | Định danh nghiệp vụ tương ứng, không FK sang database khác. |
| transaction\_type | ENUM | Có | CHECK | PAYMENT\_RECEIVED / SETTLEMENT\_RELEASED / REFUND\_DEBIT / WITHDRAWAL\_COMPLETED. |
| pending\_delta\_vnd | BIGINT | Có | — | Số tăng/giảm pending; có thể âm hoặc bằng 0\. |
| available\_delta\_vnd | BIGINT | Có | — | Số tăng/giảm available; có thể âm hoặc bằng 0\. |
| withdrawn\_delta\_vnd | BIGINT | Có | — | Số tăng total\_withdrawn; \> 0 chỉ với WITHDRAWAL\_COMPLETED, bằng 0 với các loại khác. |
| business\_event\_key | VARCHAR(160) | Có | UQ\* | UNIQUE(wallet\_id, business\_event\_key); xử lý sự kiện lặp đúng một lần. Ví dụ: settlement:{order\_id}:{wallet\_id}, withdrawal:{withdrawal\_id}. |
| created\_at | TIMESTAMPTZ | Có | — | Thời điểm phát sinh; không sửa/xóa record đã ghi. |

| TRANSACTION\_TYPE | PHÁT SINH KHI | VÍ SELLER | VÍ NỀN TẢNG |
| :---- | :---- | :---- | :---- |
| PAYMENT\_RECEIVED | Payment SUCCESS, cho từng Order | pending \+S | pending \+C |
| SETTLEMENT\_RELEASED | Order CONFIRMED (Settlement SETTLED) | pending −S; available \+S | pending −C; available \+C |
| REFUND\_DEBIT | Refund SUCCESS của Order bị hủy sau thanh toán (Settlement REFUNDED) | pending −S | pending −C |
| WITHDRAWAL\_COMPLETED | Seller rút số tiền A (hoàn tất ngay) | available −A; withdrawn \+A | — |

**Ràng buộc:** Mỗi sự kiện nghiệp vụ chỉ sinh một thay đổi cho một ví (UNIQUE(wallet\_id, business\_event\_key)). Dấu \+ tăng, dấu − giảm. Nếu S \= 0 (r \= 10.000 bps) thì không ghi dòng cho ví Seller; tương tự C \= 0 với ví nền tảng. Không sửa/xóa lịch sử; xử lý sai bằng giao dịch bù có dấu ngược. Ghi dòng thay đổi và UPDATE Wallet trong cùng transaction; ngăn số dư âm sau cập nhật. Không có bút toán giữ chỗ (reserve), giải phóng (release) hay hoàn tất chi trả (payout) vì MVP không có lệnh chi bất đồng bộ.

**Đối chiếu:** UC-D05, UC-G04; thay thế bản ghi WalletEntry tối thiểu trước đây ở FRD mục 4.19 bằng các delta cụ thể phù hợp Wallet 3 ngăn (chờ / khả dụng / đã rút).

#### 3.5.4 Withdrawal

Một lần rút tiền giả lập đã thành công của Seller, theo số tiền chọn từ available của ví. Lần rút không còn gắn với một Settlement vì số dư có thể đến từ nhiều Order. Theo FRD v5.2 và mục 8.2J, 8.3, Withdrawal không có trạng thái: một lần rút hoặc thành công ngay (tạo Withdrawal và bút toán trong cùng transaction), hoặc bị từ chối vì không hợp lệ và không để lại bản ghi hay thay đổi số dư nào. Không có Payout, không có provider chi trả, không cần Admin duyệt.

| FIELD | TYPE | REQ | KEY | GIẢI THÍCH VÀ NGHIỆP VỤ |
| :---- | :---- | :---- | :---- | :---- |
| withdrawal\_id | UUID | Có | PK | Mã giao dịch rút tiền. |
| withdrawal\_code | VARCHAR(32) | Có | UQ | Mã hiển thị cho Seller trên biên nhận và lịch sử. |
| wallet\_id | UUID | Có | FK | Ví SELLER nguồn; không rút từ ví PLATFORM. |
| seller\_id | UUID | Có | Ref | Lấy từ ngữ cảnh xác thực, không nhận từ client. |
| amount\_vnd | BIGINT | Có | CHECK | Số nguyên VND dương, không vượt available tại thời điểm rút; điều kiện tối thiểu/ngoại lệ toàn bộ số dư nhỏ theo WD-03. Không đặt CHECK tối thiểu 10.000 vô điều kiện làm mất ngoại lệ BANNED. |
| available\_before\_vnd | BIGINT | Có | CHECK | Ảnh chụp số dư khả dụng ngay trước khi rút, để hiển thị lịch sử và đối soát. |
| available\_after\_vnd | BIGINT | Có | CHECK | \\= available\_before\_vnd − amount\_vnd; \>= 0\. |
| destination | VARCHAR(100) | Có | — | Cố định "Ví ngoài giả lập". Không lưu số tài khoản ngân hàng, thẻ hay thông tin thanh toán thật. |
| idempotency\_key | VARCHAR(128) | Có | UQ\* | UNIQUE(seller\_id, idempotency\_key); bấm rút lặp hoặc gửi lại không trừ ví lần hai. |
| request\_hash | VARCHAR(128) | Có | — | Cùng key nhưng payload khác (số tiền khác) → từ chối 409\. |
| withdrawn\_at | TIMESTAMPTZ | Có | — | Thời điểm rút thành công (commit transaction). |

Ràng buộc: users.status ACTIVE, account\_type=SELLER, UserRole SELLER hiệu lực, Seller APPROVED; accountStatus ACTIVE hoặc BANNED có allowlist rút hợp lệ, đúng ví owner\_id=sub (WD-01/WD-09). PENDING/REJECTED hoặc accountStatus DISABLED không rút; nghĩa vụ đến muộn phải được Identity đánh giá để chuyển lại BANNED trước cấp quyền. Kiểm tra lại quyền/số dư và WD-03 trong luồng giao dịch hiện hành. Trong một local transaction (WD-04), available ≥ amount; available −A, total\_withdrawn +A, tạo Withdrawal và WalletTransaction WITHDRAWAL\_COMPLETED, ghi outbox; lỗi rollback toàn bộ. Giữ concurrency/idempotency, không thêm trạng thái Withdrawal. Rút một phần dưới ngưỡng trả 422 `WITHDRAWAL_BELOW_MINIMUM`; BANNED có 0 < available < 10.000 được rút đúng toàn bộ, ACTIVE không được ngoại lệ.

**Đối chiếu: UC-D05, UC-G01, UC-E08; mục 8.2J và 8.3 (WD-01 đến WD-11). Nếu sau MVP thay rút tiền giả lập bằng cổng chi trả thật (bất đồng bộ, có thể lỗi hoặc timeout) thì phải bổ sung trạng thái giao dịch rút, ngăn "đang rút" để giữ tiền trong lúc chờ và cơ chế đối soát/retry — ngoài phạm vi tài liệu này.**

### 3.6 Order Inventory Fulfillment

Sở hữu CheckoutSession, các Order theo Seller, dòng hàng, tồn kho và Reservation trong cùng PostgreSQL. Không có SellerOrder, FulfillmentGroup hoặc ParentOrder riêng.

#### 3.6.1 CheckoutSession

Đại diện một lần checkout của Customer; nhóm các Order để có một Payment chung và lưu kết quả idempotent.

| FIELD | TYPE | REQ | KEY | GIẢI THÍCH VÀ NGHIỆP VỤ |
| :---- | :---- | :---- | :---- | :---- |
| checkout\_session\_id | UUID | Có | PK | Mã lượt checkout, không phải mã một Order. |
| customer\_id | UUID | Có | Ref | Customer đang đăng nhập. |
| checkout\_source | ENUM | Có | CHECK | CART (từ giỏ hàng) / BUY\_NOW (Mua ngay, UC-B10). |
| source\_cart\_id | UUID | ĐK | Ref | Bắt buộc khi CART; NULL khi BUY\_NOW. |
| cart\_snapshot | JSONB | ĐK | — | Bắt buộc khi CART: các dòng được chọn (cart\_item\_id, product\_id, quantity, row\_version) để giảm đúng lượng sau thanh toán. NULL khi BUY\_NOW. |
| idempotency\_key | VARCHAR(128) | Có | UQ cùng customer | UNIQUE(customer\_id, idempotency\_key). |
| request\_hash | VARCHAR(128) | Có | — | Hash payload chuẩn hóa; key cũ với payload khác phải bị từ chối. |
| total\_amount\_vnd | BIGINT | Có | CHECK | Tổng tiền các Order trong checkout, \> 0; bất biến sau khi tạo. |
| currency | CHAR(3) | Có | CHECK | VND. |
| status | ENUM | Có | CHECK | AWAITING\_PAYMENT / PAID / CLOSED (state machine ở 8.2A). |
| payment\_id | UUID | Không | UQ\* \+ Ref | Payment chung khi đã biết; nguồn trạng thái tiền thuộc Payment service. |
| close\_reason | ENUM | ĐK | CHECK | Bắt buộc khi CLOSED: PAYMENT\_FAILED / EXPIRED / CANCELLED\_BY\_CUSTOMER. |
| address\_id | UUID | Có | Ref | Địa chỉ chọn tại checkout; snapshot người nhận được sao chép vào từng Order. Không FK: địa chỉ có thể bị xóa vật lý sau đó (3.1.4). |
| paid\_at | TIMESTAMPTZ | ĐK | — | Thời điểm thanh toán chung thành công; mốc tính hạn 7 ngày của Seller cho từng Order. |
| needs\_manual\_review, manual\_note | BOOLEAN / TEXT | Không | — | Chỉ ghi chú đối soát khoản THU chung (UC-E08), không Refund FULL_CHECKOUT/cờ chờ Customer; Refund thuộc Payment. |
| created\_at, expires\_at | TIMESTAMPTZ | Có | — | Thời điểm tạo và mốc kiểm tra hết hạn; P-01 vẫn tạm 15 phút chưa chốt. |

**Ràng buộc: Chỉ Customer ACTIVE tạo checkout; cần số điện thoại và địa chỉ hợp lệ. Chụp giá, danh sách dòng hàng, Seller và tỷ lệ hoa hồng trước transaction tạo toàn bộ Order cùng Reservation. UNIQUE(checkout\_session\_id, seller\_id) tại Order bảo đảm mỗi Seller một Order. Tạo checkout, Order, OrderItem và giữ kho thành công toàn bộ hoặc rollback toàn bộ. Tối đa một CheckoutSession AWAITING\_PAYMENT cho mỗi Customer (G-08). BUY\_NOW: đúng 1 Order chứa đúng 1 OrderItem, không đọc và không trừ giỏ ở bất kỳ bước nào (G-16). Trước khi thanh toán, MVP không cho hủy lẻ hay thanh toán riêng một Order; khách hủy cả phiên. Callback thu tiền muộn khi CLOSED không mở lại checkout; hoàn FULL\_CHECKOUT. PAID giữ nguyên dù sau đó một số Order bị hủy và hoàn tiền.**

**Đối chiếu:** UC-B04, B05, B06, B10 và UC-G03.

#### 3.6.2 Order

Một đơn của đúng một Seller trong CheckoutSession. Customer có thể mua nhiều Seller một lần, thanh toán chung và theo dõi từng Order độc lập.

| FIELD | TYPE | REQ | KEY | GIẢI THÍCH VÀ NGHIỆP VỤ |
| :---- | :---- | :---- | :---- | :---- |
| order\_id | UUID | Có | PK | Mã đơn theo Seller. |
| checkout\_session\_id | UUID | Có | FK | Nhiều Order cùng tham chiếu một CheckoutSession. |
| seller\_id | UUID | Có | Ref | Seller duy nhất của đơn. |
| customer\_id | UUID | Có | Ref | Customer phải trùng CheckoutSession. |
| total\_amount\_vnd | BIGINT | Có | CHECK | Tổng line\_total\_vnd của OrderItem trong chính Order này; \> 0\. |
| currency | CHAR(3) | Có | CHECK | VND; giữ quy ước giá đã gồm thuế và phí vận chuyển 0 từ bản nguồn. |
| commission\_bps\_snapshot | INTEGER | Có | CHECK | Chốt tại checkout: 500 bps = 5% chung mọi Seller/ngành hàng (P-COM-01), truyền Finance; không chỉ dùng test. |
| recipient\_name, recipient\_phone | VARCHAR | Có | — | Snapshot người nhận. |
| province\_code, ward\_code | VARCHAR(32) | Có | — | Snapshot mã địa giới nhận hàng. |
| province\_name, ward\_name | VARCHAR(150) | Có | — | Snapshot tên tỉnh/thành và phường/xã/đặc khu từ danh mục tĩnh seed DB Identity cùng mã khi checkout; không tra lại đổi đơn cũ. |
| address\_detail | VARCHAR(300) | Có | — | Snapshot địa chỉ chi tiết. |
| status | ENUM | Có | CHECK | AWAITING\_PAYMENT (chờ thanh toán) / PAYMENT\_FAILED (thanh toán thất bại) / EXPIRED (hết hạn) / PAID (chờ xử lý) / PACKING (đang đóng gói) / DELIVERED (đã giao — chờ khách xác nhận) / CONFIRMED (đã xác nhận) / CANCELLED (đã hủy). State machine ở 8.2B. |
| cancel\_reason\_code | VARCHAR(50) | ĐK | — | Mã lý do hủy theo enum nghiệp vụ (CustomerCancelReason P-21a, SellerRejectReason P-21b); bắt buộc khi Customer hủy hoặc Seller từ chối, NULL khi hệ thống hủy. |
| cancel\_reason\_note | VARCHAR(500) | ĐK | — | Ghi chú chi tiết; bắt buộc khi Seller từ chối hoặc khi mã là OTHER. |
| cancelled\_by\_type, cancelled\_by | ENUM / UUID | ĐK | Ref cho actor | Loại tác nhân CUSTOMER / SELLER / SYSTEM / ADMIN và id tác nhân (NULL nếu hệ thống hủy). |
| paid\_at, packing\_at, delivered\_at, confirmed\_at, cancelled\_at | TIMESTAMPTZ | ĐK | — | Mốc bắt buộc tương ứng với chuyển trạng thái; packing\_at khi Seller bắt đầu đóng gói. |
| confirmed\_by | ENUM | ĐK | — | CUSTOMER hoặc SYSTEM (J3); bắt buộc khi CONFIRMED. |
| seller\_action\_due\_at, customer\_confirm\_due\_at | TIMESTAMPTZ | ĐK | — | Lần lượt paid\_at \+ 7 ngày và delivered\_at \+ 7 ngày. |
| needs\_manual\_review, manual\_note | BOOLEAN / TEXT | Không | — | Chỉ ghi chú đối soát nghiệp vụ đơn/khoản thu, không đưa Refund vào hàng chờ Admin hoặc thay cờ chờ Customer của Payment. |
| created\_at, updated\_at | TIMESTAMPTZ | Có | — | Dấu thời gian vòng đời đơn. |

**Ràng buộc:** UNIQUE(checkout\_session\_id, seller\_id); không unique riêng checkout\_session\_id. Mọi OrderItem có cùng Seller với Order. Một Payment success cập nhật tất cả Order đang chờ trong checkout và commit Reservation bằng một local transaction. Trạng thái giao, từ chối, hủy và nhận hàng độc lập theo Order; không tạo trạng thái giao chung để ghi đè các đơn khác. Khóa Order khi chuyển trạng thái để ngăn CONFIRMED và CANCELLED đồng thời. Khách chỉ hủy được Order ở PAID (chưa đóng gói); Seller từ chối được ở PAID hoặc PACKING; không có chuyển trạng thái từ DELIVERED/CONFIRMED sang CANCELLED. Hiển thị DELIVERED là "Đã giao — chờ khách xác nhận"; không có trạng thái "đang giao".

**Đối chiếu:** UC-B04 đến B09 và các luồng Seller kế thừa; thay toàn bộ vai trò SellerOrder cũ.

#### 3.6.3 OrderItem

Snapshot sản phẩm trong một Order; sửa tên hoặc giá tại Product Service không đổi lịch sử mua.

| FIELD | TYPE | REQ | KEY | GIẢI THÍCH VÀ NGHIỆP VỤ |
| :---- | :---- | :---- | :---- | :---- |
| order\_item\_id | UUID | Có | PK | Mã dòng hàng đã mua. |
| order\_id | UUID | Có | FK | Order chứa dòng hàng; FK nội bộ. |
| product\_id | UUID | Có | Ref | Tham chiếu Product Service. |
| product\_name\_snapshot | VARCHAR(255) | Có | — | Tên sản phẩm tại checkout. |
| cover\_media\_snapshot | TEXT | Có | — | Ảnh bìa tại checkout. |
| unit\_price\_vnd\_snapshot | BIGINT | Có | CHECK | Đơn giá chụp lại từ Product Service. |
| quantity | INTEGER | Có | CHECK | Số lượng \>= 1\. |
| line\_total\_vnd | BIGINT | Có | CHECK | unit\_price × quantity; có thể là generated. |
| source\_cart\_item\_id | UUID | Không | Ref | Dòng giỏ gốc để giảm đúng số lượng đã mua sau thanh toán; NULL với đơn Mua ngay. |

**Ràng buộc:** Product phải thuộc Seller của Order. quantity \> 0 và unit\_price\_vnd\_snapshot \> 0\. line\_total\_vnd \= unit\_price\_vnd\_snapshot × quantity. Không hủy riêng một dòng hàng trong MVP. Snapshot không bị sửa sau khi đã tạo checkout.

**Đối chiếu: UC-B04, UC-B06 và UC-B08 kế thừa.**

#### 3.6.4 Inventory

Nguồn tồn kho duy nhất cho checkout và Seller điều chỉnh tồn. Số khả dụng trừ phần đang giữ.

| FIELD | TYPE | REQ | KEY | GIẢI THÍCH VÀ NGHIỆP VỤ |
| :---- | :---- | :---- | :---- | :---- |
| product\_id | UUID | Có | PK \+ Ref | Một bản ghi tồn kho trên Product. |
| on\_hand\_quantity | INTEGER | Có | CHECK | Tồn thực tế \>= 0\. |
| reserved\_quantity | INTEGER | Có | CHECK | Tổng số đang giữ \>= 0\. |
| updated\_at | TIMESTAMPTZ | Có | — | Mốc điều chỉnh/giữ/trừ kho. |
| row\_version | BIGINT | Có | — | Có thể dùng cho optimistic concurrency. |

**Ràng buộc:** available \= on\_hand\_quantity − reserved\_quantity \>= 0\. Reserve bằng transaction có điều kiện, không dựa vào cache hoặc kết quả search. HELD tăng reserved; Payment success giảm cả on\_hand và reserved. RELEASED chỉ giảm reserved. Seller chỉ cập nhật tồn của sản phẩm mình và không được làm on\_hand thấp hơn reserved.

**Đối chiếu:** UC-B04/B06 và UC-D03 kế thừa.

#### 3.6.5 Reservation

Giữ hàng theo OrderItem trong thời gian chờ Payment chung.

| FIELD | TYPE | REQ | KEY | GIẢI THÍCH VÀ NGHIỆP VỤ |
| :---- | :---- | :---- | :---- | :---- |
| reservation\_id | UUID | Có | PK | Mã giữ hàng. |
| order\_item\_id | UUID | Có | UQ \+ FK | Một bản ghi giữ cho một dòng OrderItem. |
| product\_id | UUID | Có | FK \+ Ref | FK đến Inventory.product\_id cùng service; đồng thời là ID Product của Product Service. |
| quantity | INTEGER | Có | CHECK | Số lượng giữ \> 0\. |
| status | ENUM | Có | CHECK | HELD / COMMITTED / RELEASED. |
| expires\_at | TIMESTAMPTZ | Có | — | Hạn giữ/đối soát tùy trạng thái Payment. |
| created\_at, resolved\_at | TIMESTAMPTZ | ĐK | — | Ngày tạo/giải quyết hold. |

**Ràng buộc:** Tạo giữ hàng nguyên tử cho toàn bộ CheckoutSession. Hạn expires\_at là mốc chạy kiểm tra, không tự động nhả khi Payment chưa rõ kết quả. Payment FAILED rõ ràng nhả giữ của tất cả Order cùng checkout. PENDING cần đối soát theo J2/J2b và P-04 trước khi đóng; khóa CheckoutSession để xử lý tranh chấp callback và job hết hạn.

**Đối chiếu:** UC-B04/B05/B06 và UC-G03 kế thừa.

#### 3.6.6 StockMovement

Nhật ký thay đổi on\_hand để kiểm tra lệch và chống trừ hoặc hoàn trùng.

| FIELD | TYPE | REQ | KEY | GIẢI THÍCH VÀ NGHIỆP VỤ |
| :---- | :---- | :---- | :---- | :---- |
| stock\_movement\_id | UUID | Có | PK | Mã biến động. |
| product\_id | UUID | Có | Ref | Sản phẩm bị thay đổi số lượng. |
| movement\_type | ENUM | Có | CHECK | IMPORT / MANUAL\_ADJUST / SOLD / RESTOCK. |
| quantity\_delta | INTEGER | Có | CHECK | Số tăng/giảm on\_hand, khác 0\. |
| order\_item\_id | UUID | Không | FK | Có với biến động do Order. |
| actor\_id | UUID | Không | Ref | User.user\_id của Seller thao tác; NULL khi hệ thống, phân biệt bằng actor\_type. |
| business\_event\_key | VARCHAR(128) | Có | UQ | Chống ghi trừ/hoàn trùng cho cùng sự kiện. |
| created\_at | TIMESTAMPTZ | Có | — | Ngày ghi nhận biến động. |
| actor\_type | ENUM | Có | CHECK | USER / SYSTEM; USER bắt buộc actor\_id \= sub, SYSTEM lưu tên job trong metadata audit. |

**Ràng buộc:** quantity\_delta khác 0\. HELD/RELEASED chỉ đổi reserved, không tạo biến động on\_hand. SOLD khi Payment success; RESTOCK đúng một lần khi Order đã trả tiền bị hủy trước hoàn tất. Tiền thu muộn sau CLOSED không tạo SOLD hoặc RESTOCK vì hàng đã được nhả. Các ngoại lệ hàng hỏng hoặc trả hàng chưa nằm trong MVP.

**Đối chiếu: UC-D03, UC-B06, UC-B08 và UC-G02 kế thừa.**

### 3.7 Notification

Gửi email bất đồng bộ, tùy chọn in-app; không nằm trên đường xử lý đồng bộ của checkout. Data store queue và PostgreSQL log hoặc tương đương.

#### 3.7.1 Notification

Một thông báo cho một người nhận theo sự kiện, có chống gửi trùng và retry.

| FIELD | TYPE | REQ | KEY | GIẢI THÍCH VÀ NGHIỆP VỤ |
| :---- | :---- | :---- | :---- | :---- |
| notification\_id | UUID | Có | PK | Mã thông báo. |
| recipient\_user\_id | UUID | Có | Ref | User.user\_id duy nhất của người nhận; không định tuyến bằng loại tài khoản. |
| audience\_context | ENUM | Có | CHECK | CUSTOMER / SELLER / ACCOUNT; ngữ cảnh nội dung, không là bằng chứng cấp quyền. |
| notification\_type | VARCHAR(70) | Có | — | Payment, Order, Refund gồm REFUND_RETRY_REQUIRED, funds available, withdrawal, Seller approval/ban; enum theo API §4.2. |
| channel | ENUM | Có | CHECK | EMAIL (Must), IN\_APP (Should). |
| subject | VARCHAR(255) | Không | — | Tiêu đề email nếu có. |
| content | TEXT | Có | — | Nội dung hiển thị/gửi; tiền giả lập cần có nhãn rõ. |
| business\_reference\_id | UUID | Không | Ref | CheckoutSession, Order, Payment hoặc Withdrawal liên quan. |
| dedupe\_key | VARCHAR(160) | Có | UQ | Một event/kênh/người nhận chỉ gửi một lần logic. |
| status | ENUM | Có | CHECK | PENDING / SENT / FAILED. |
| retry\_count, sent\_at | INTEGER/TIMESTAMPTZ | ĐK | CHECK | EMAIL retry_count DEFAULT 0, 0..3 retry sau lần gửi đầu; query/delivery tracking không tự tạo email mới. |
| is\_read | BOOLEAN | ĐK | — | Chỉ dùng cho IN\_APP nếu Should được triển khai. |
| created\_at | TIMESTAMPTZ | Có | — | Ngày ghi nhận yêu cầu gửi. |

**Ràng buộc:** dedupe\_key theo event/kênh/recipient\_user\_id/audience\_context; mỗi User một account\_type, chỉ trả recipient=sub. REFUND_RETRY_REQUIRED từ Payment refund.failed có retryExhausted=true, gửi Customer chủ Payment với refundId/paymentId/checkoutSessionId/orderId|null/scope/amountVnd/retryCycle; dedupe refundId+retryCycle+recipient+kênh, email Must/in-app Should. Link chỉ mở đăng nhập/trạng thái/xác nhận, không tự retry. P-19: một lần gửi đầu + tối đa 3 retry, chờ 1/5/15 phút từ lỗi trước, chỉ lỗi tạm thời (timeout/mất kết nối/lỗi nhà cung cấp/rate limit), tôn trọng Retry-After. Lỗi vĩnh viễn/token hết hạn dừng ngay, hết lượt FAILED không đổi nghiệp vụ; dùng cùng thông báo/key. P-22: in-app giữ nội dung 30 ngày, chỉ 50 gần nhất; không xóa email/cắt retry/mất dedupe hoặc tạo lại thông báo đã xóa/hết hạn khi replay. Không lưu token/mật khẩu.

**Đối chiếu:** UC-G01 kế thừa; thêm sự kiện tiền vào khả dụng và biên nhận rút tiền thành công (gồm mã giao dịch, số tiền, số dư khả dụng còn lại).

### 3.8 Reporting

Chỉ sở hữu dữ liệu phục vụ dashboard; không nhận quyền cập nhật Order, Payment, Settlement, Product hoặc Inventory gốc.

**DATA STORE:** Read-model database (PostgreSQL)

#### 3.8.1 OrderReportProjection [READ MODEL]

Dữ liệu tổng hợp theo từng Order, lọc seller\_id cho Seller và quyền operations:read:any cho Admin; không tạo lại entity SellerOrder.

| FIELD | TYPE | REQ | KEY | GIẢI THÍCH / NGHIỆP VỤ |
| :---- | :---- | :---- | :---- | :---- |
| **order\_id** | UUID | Có | PK/KEY | Một record theo Order. |
| **seller\_id** | UUID | Có | INDEX | Lọc báo cáo người bán. |
| **customer\_id** | UUID | Có | — | Chỉ giữ nếu báo cáo vận hành thực sự cần; bảo vệ PII. |
| **subtotal\_vnd** | BIGINT | Có | — | Giá trị hàng của Order. |
| **order\_status** | VARCHAR(40) | Có | — | Trạng thái mới nhất của Order. |
| **paid\_at, confirmed\_at, cancelled\_at** | TIMESTAMPTZ | Không | — | Các mốc cho thống kê thời gian. |
| **projection\_updated\_at** | TIMESTAMPTZ | Có | — | Mốc cập nhật bản sao. |

**Ràng buộc: doanh số hoàn tất tính trên Order CONFIRMED theo đúng seller\_id; không nhân đôi Payment chung. Lọc ownership ở API reporting, không tin seller\_id do client cung cấp.**

Đối chiếu FRD: UC-D04; UC-E02; UC-B07.

#### 3.8.2 FinancialReportProjection [READ MODEL]

Bản sao các chỉ tiêu tiền từ Payment/Refund và Finance để dashboard tách thu, hoàn, hoa hồng, số dư khả dụng và đã rút.

| FIELD | TYPE | REQ | KEY | GIẢI THÍCH / NGHIỆP VỤ |
| :---- | :---- | :---- | :---- | :---- |
| **source\_event\_id** | VARCHAR(160) | Có | PK/UQ | Event/khoản tiền nguồn; chống ghi nhận lặp. |
| **source\_type** | ENUM | Có | CHECK | PAYMENT / REFUND / SETTLEMENT / WITHDRAWAL. |
| **order\_id** | UUID | Không | — | Order chung liên quan. |
| **seller\_id** | UUID | Không | INDEX | Lọc dashboard Seller. |
| **amount\_vnd** | BIGINT | Có | — | Giá trị phát sinh của chỉ tiêu. |
| **finance\_metric** | ENUM | Có | CHECK | COLLECTED / REFUNDED / COMMISSION / SELLER\_AVAILABLE / WITHDRAWN. |
| **occurred\_at** | TIMESTAMPTZ | Có | INDEX | Mốc nghiệp vụ cho kỳ báo cáo. |
| **projection\_updated\_at** | TIMESTAMPTZ | Có | — | Mốc nhận/cập nhật dữ liệu. |

**RÀNG BUỘC:** Chỉ tính thu khi Payment SUCCESS, hoàn khi Refund SUCCESS, đã rút theo từng Withdrawal (mỗi bản ghi là một lần rút đã thành công); số dư chờ/khả dụng là số dư tại thời điểm chốt, không cộng vô điều kiện với các dòng phát sinh trong kỳ. Số liệu chính thức phải đối chiếu service chủ sở hữu.

Đối chiếu FRD: UC-D04; UC-E02; giải nghĩa công thức và bảo toàn tiền FRD v4.1.

#### 3.8.3 InventoryReportProjection [READ MODEL]

Đọc tồn kho và danh sách hàng sắp hết cho dashboard mà không buộc reporting truy vấn trực tiếp Commerce DB.

| FIELD | TYPE | REQ | KEY | GIẢI THÍCH / NGHIỆP VỤ |
| :---- | :---- | :---- | :---- | :---- |
| **product\_id** | UUID | Có | PK/KEY | Định danh sản phẩm. |
| **seller\_id** | UUID | Có | INDEX | Định tuyến báo cáo theo Seller. |
| **product\_name** | VARCHAR(255) | Không | — | Tên snapshot từ Product Service. |
| **on\_hand\_quantity** | INTEGER | Có | — | Tồn thực tế theo Commerce. |
| **reserved\_quantity** | INTEGER | Có | — | Số còn đang giữ. |
| **available\_quantity** | INTEGER | Có | — | Số khả dụng từ nguồn Commerce. |
| **projection\_updated\_at** | TIMESTAMPTZ | Có | — | Thời điểm cập nhật read model. |

**RÀNG BUỘC:** Read model eventual consistency, không quyết định đủ tồn khi checkout. P-16 cảnh báo thấp khi available\_quantity ≤5. P-14 dùng GMT+7, tuần thứ Hai/quý lịch; P-15 bán chạy theo số lượng, chỉ trên 500 lượt, không tự đặt kỳ/trạng thái được tính. Reporting vẫn hoãn, không thành bắt buộc vì đã chốt ngưỡng.

Đối chiếu FRD: UC-E02; UC-D03.

## 4 Quan hệ ownership và bất biến

| QUAN HỆ | CARDINALITY VÀ QUY TẮC |
| :---- | :---- |
| Customer → CustomerAddress | 1 → 0..N; FK CustomerAddress.customer\_id → Customer.customer\_id. |
| Seller → Product | 1 → 0..N; tham chiếu logic qua service. |
| Cart → CartItem | 1 → 0..N; UNIQUE(cart\_id, product\_id). |
| CheckoutSession → Order | 1 → 1..N sau tạo thành công; UNIQUE(checkout\_session\_id, seller\_id). |
| CheckoutSession → Payment | 1 → 0..1; một Payment chung cho N Order. |
| Order → OrderItem | 1 → 1..N; mọi sản phẩm thuộc Seller của Order. |
| Payment → Refund | 1 → 0..N; từng ORDER hoặc một FULL\_CHECKOUT, không chồng lặp. |
| Order → Settlement | 1 → 0..1; tạo sau thu tiền thành công. |
| Seller → Wallet | 1 → 1 ví VND cho mỗi Seller; ví PLATFORM được giới hạn riêng; các số dư không âm. |
| Wallet → WalletTransaction | 1 → 0..N; append-only; số dư ví \= tổng delta. |
| Wallet → Withdrawal | 1 → 0..N; chỉ ví SELLER; mỗi Withdrawal là một lần rút đã thành công và có đúng một WalletTransaction WITHDRAWAL\_COMPLETED. |
| OrderItem → Reservation | 1 → 1 khi tạo checkout thành công; một lần giữ, sau đó COMMITTED hoặc RELEASED. |
| Product → Inventory | 1 → 0..1; phải có tồn hợp lệ trước khi cho checkout. |
| User → Customer HOẶC Seller (tài khoản riêng) | Mỗi User đúng một loại tài khoản. Chỉ Customer.customer\_id hoặc Seller.seller\_id bằng users.user\_id; không đồng thời. Admin User chỉ có role ADMIN. Seller có 1..N SellerApplication lưu từng lần nộp. |
| User → Role → Permission (một role/User) | User tối đa 1 role đang hiệu lực; Role ↔ Permission N:N; không hợp quyền đa vai trò. |
| User → Notification và audit | recipient\_user\_id/actor\_user\_id chỉ danh tính của tài khoản tương ứng; audience\_context không tạo thêm vai trò. |

### 4.1 Các bất biến phải kiểm thử

* CheckoutSession.total\_amount\_vnd \= tổng Order.total\_amount\_vnd trong checkout \= Payment.amount\_vnd. Mỗi Order.total\_amount\_vnd \= tổng OrderItem.line\_total\_vnd của đơn đó.

* Mỗi Seller xuất hiện tối đa một Order trong một checkout; mọi Order thuộc cùng Customer với CheckoutSession. Payment không unique theo từng Order.

* Một Payment SUCCESS chỉ trừ tồn một lần cho toàn bộ checkout. available không âm. Callback hoặc event lặp không tạo Settlement hoặc biến động kho lặp.

* Refund ORDER bằng tiền của đúng Order thuộc Payment. FULL\_CHECKOUT loại trừ hoàn từng Order. Nghĩa vụ hoàn đã tạo không vượt số tiền thu, kể cả bản ghi FAILED đang chờ retry.

* Order CANCELLED trước khi thu thành công không sinh Settlement hoặc quyền rút. Order đã thu rồi bị hủy tạo nghĩa vụ hoàn riêng, không hủy Order của Seller khác.

* Settlement SETTLED chỉ khi Order CONFIRMED; gross\_amount\_vnd \= commission\_amount\_vnd \+ seller\_amount\_vnd. Payment SUCCESS hợp lệ tạo Settlement PENDING và ghi tiền chờ vào Wallet; Order hủy trước xác nhận chuyển PENDING\_REFUND, Refund SUCCESS mới trừ pending. Không chuyển khả dụng trong khi đang hoàn.

* Một Seller có một Wallet theo loại tiền. Withdrawal chỉ rút số nguyên dương A ≤ available, thỏa WD-03 (ngưỡng 10.000 hoặc BANNED rút toàn bộ available dương dưới ngưỡng). Trong một transaction có điều kiện: available −A, total\_withdrawn +A, tạo Withdrawal và WalletTransaction WITHDRAWAL\_COMPLETED; thất bại rollback không có bản ghi. Rút đồng thời không vượt available; cùng idempotency\_key chỉ trừ ví một lần. Mọi số dư không âm, mọi thay đổi có WalletTransaction.

* Với mỗi Seller: Σ seller\_amount của Settlement SETTLED \= available\_balance\_vnd \+ total\_withdrawn\_vnd; Σ seller\_amount của Settlement PENDING và PENDING\_REFUND \= pending\_balance\_vnd; total\_withdrawn\_vnd \= Σ Withdrawal.amount\_vnd. Refund không bao giờ trừ available hoặc total\_withdrawn của Seller.

* Refund PENDING/timeout phải tra cứu bộ giả lập (hoặc cổng thật) trước retry và giữ nguyên idempotency\_key. WalletTransaction chống trùng theo business\_event\_key; event lặp không ghi bút toán lần hai.

* User ACTIVE có account\_type CUSTOMER, role CUSTOMER hiện hành và Customer ACTIVE mới được giỏ/checkout. Tài khoản SELLER/ADMIN không tự có quyền mua; muốn mua phải dùng tài khoản Customer riêng. Customer BANNED không checkout/thanh toán mới và chỉ xử lý đơn dở khi có điều kiện đăng nhập giới hạn G-14.

* Order chỉ chuyển trạng thái theo 8.2B; khách không hủy được Order từ PACKING trở đi; không Order nào đi từ DELIVERED/CONFIRMED sang CANCELLED.

UserRole và RolePermission giữ khóa kép, nhưng UNIQUE(user\_id) WHERE revoked\_at IS NULL và role phải phù hợp account\_type. Không cho cùng user\_id xuất hiện ở cả customers và sellers; ADMIN không có hai hồ sơ này. SellerApplication lưu lần nộp mới sau từ chối với application\_id riêng, không sửa bản ghi đã REJECTED. Quyền hiệu lực kiểm tra role, trạng thái, nghĩa vụ còn dở và ownership.

### 4.2 Quyền ghi dữ liệu

| SERVICE | DỮ LIỆU ĐƯỢC GHI | KHÔNG TỰ Ý GHI |
| :---- | :---- | :---- |
| Identity và Seller | User, Customer, Seller, SellerApplication, CustomerAddress, Role, Permission, UserRole, RolePermission, phiên xác thực/audit | Product, Order, Payment; service khác không ghi RBAC trực tiếp |
| Product | Category, Product, Review | Inventory, OrderItem |
| Cart | Cart, CartItem | Order, Reservation |
| Order Inventory Fulfillment | CheckoutSession, Order, OrderItem, Inventory, Reservation, StockMovement | Payment, Refund, Settlement, Wallet |
| Payment | Payment, Refund | Order.status, Settlement |
| Finance | Settlement, Wallet, WalletTransaction, Withdrawal | Payment.status, Refund.status, Order.status; không cập nhật trực tiếp dữ liệu giao dịch dịch vụ khác |
| Notification | Notification | Dữ liệu nghiệp vụ của service khác |

## 5 Luồng nghiệp vụ cập nhật

### 5.1 Đăng ký và phân quyền Seller

Mỗi vai trò sử dụng một tài khoản User riêng; một Auth API có thể dùng chung hạ tầng nhưng bắt buộc nhận accountType theo kênh và từ chối login sai vai trò với thông báo chung. Đăng ký Customer tạo User CUSTOMER \+ Customer \+ role CUSTOMER; đăng ký Seller tạo User SELLER \+ Seller \+ SellerApplication PENDING, chưa cấp quyền Seller hoặc login. Không hỗ trợ nộp hồ sơ Seller trên tài khoản Customer/ADMIN.

Admin có seller:approve duyệt SellerApplication PENDING: cập nhật APPROVED, gán duy nhất role SELLER, tăng authz\_version, ghi audit và outbox tạo ví idempotent. REJECTED không đăng nhập; muốn đăng ký lại phải qua bước xác minh email và tạo SellerApplication MỚI cho cùng seller\_id, giữ lịch sử, không sửa hồ sơ bị từ chối. Google chỉ cho tài khoản Customer.

Mọi API kiểm tra account\_type theo kênh, role duy nhất, cặp trạng thái G-14 và ownership. SELLER chỉ bán khi accountStatus ACTIVE và APPROVED; BANNED chỉ nghĩa vụ tồn theo allowlist kể cả pending-only. CUSTOMER chỉ mua khi accountStatus ACTIVE bằng tài khoản riêng; ADMIN dùng tài khoản riêng. Không dùng ẩn nút UI thay kiểm tra backend.

### 5.2 Tìm kiếm đơn giản trong database

Product Service tìm Product theo product\_name bằng truy vấn tham số hóa. Theo P-37 chốt ngày 19/11/2026, phải chuẩn hóa cả từ khóa và tên trước khi so khớp bằng LIKE: bỏ dấu tiếng Việt, đổi đ/Đ thành d và chuyển về chữ thường. ILIKE đơn thuần không đủ để đáp ứng yêu cầu không phân biệt dấu tiếng Việt. Ví dụ “dien thoai”, “Điện Thoại” và “ĐIỆN THOẠI” phải cho cùng kết quả. Chỉ chuẩn hóa để tìm kiếm, giữ nguyên tên lưu và hiển thị. Identity cung cấp API tìm Seller tương tự trên tên cửa hàng (users.full\_name của User có Seller hợp lệ); không join trực tiếp database giữa service. Escape ký tự % và \_ nếu từ khóa được hiểu là chuỗi literal. Giới hạn độ dài từ khóa, kích thước trang và sắp xếp ổn định để phân trang.

Kết quả Product phải áp dụng đầy đủ G-06, gồm User ACTIVE, quyền SELLER và Seller APPROVED, ACTIVE qua API sở hữu. Có thể xác minh theo lô và tiếp tục lấy trang để tránh đưa shop không hợp lệ vào kết quả. Tồn hiển thị lấy từ Order service khi cần; checkout luôn kiểm tra lại giá, Seller và tồn chính thức. Không triển khai full-text, gợi ý từ khóa, sửa lỗi chính tả, OpenSearch hoặc pipeline đồng bộ search trong MVP. Đây là yêu cầu triển khai tìm kiếm, không xác nhận code hoặc migration đã có.

### 5.3 Giỏ hàng, Mua ngay và checkout

1. Customer accountStatus ACTIVE phải đăng nhập mới thêm sản phẩm vào giỏ; khách vãng lai bấm "Thêm vào giỏ" hoặc "Mua ngay" được chuyển sang đăng nhập rồi quay lại trang sản phẩm. Phiên BANNED giới hạn không được dùng giỏ/Mua ngay/checkout theo G-14. Không có giỏ khách và không hợp nhất giỏ.

2. Hai lối vào checkout: từ giỏ (checkout\_source \= CART, Customer chọn các dòng còn hàng) hoặc nút "Mua ngay" trên trang sản phẩm (checkout\_source \= BUY\_NOW, đúng 1 sản phẩm với số lượng đã chọn, không đọc/ghi giỏ).

3. Tài khoản SELLER hoặc ADMIN không thể tạo giỏ/checkout; phải đăng nhập bằng tài khoản CUSTOMER độc lập.

4. Customer chọn địa chỉ (mặc định theo 3.1.4). Server lấy giá từ Product Service, Seller hợp lệ, tỷ lệ hoa hồng hiện hành và tồn kho. Trong local transaction, tạo CheckoutSession, nhóm theo seller\_id để tạo N Order, tạo OrderItem và giữ kho cho toàn bộ checkout. Thiếu hàng ở bất cứ dòng nào thì rollback cả lượt.

5. Payment tạo một khoản thu chung bằng tổng N Order. Khi SUCCESS, Order service khóa checkout, cập nhật PAID, commit Reservation và ghi SOLD cho toàn bộ Order trong một transaction; phát các event bằng outbox. Với CART, Cart chỉ giảm lượng đã mua trên các dòng còn tồn tại bằng snapshot; với BUY\_NOW không động tới giỏ. Cart và xử lý event idempotent; nếu khách đã xóa rồi thêm lại thì không giảm nhầm dòng mới.

   Việc áp dụng SUCCESS dùng chung handler tại 8.2B: Order xác minh điều kiện giao dịch qua Identity/Product ngoài transaction giữ khóa; lỗi nguồn giữ retry bền vững, không coi là invalid. Invalid đã chứng minh tại lúc thu chỉ hủy/hoàn Order PAID/PACKING đủ điều kiện, giữ Payment SUCCESS và đủ hiệu ứng `checkout.paid`/`order.cancelled`; ban Seller sau capture không tự hủy, kể cả event tới muộn. Phiên đã CLOSED vẫn theo nhánh FULL_CHECKOUT ở bước 6, không áp dụng lại nhánh PAID.

6. Thanh toán FAILED rõ ràng: đóng checkout (close\_reason PAYMENT\_FAILED), mọi Order → PAYMENT\_FAILED, nhả toàn bộ giữ kho, giỏ giữ nguyên. Thanh toán PENDING: đối soát theo cấu hình trước khi nhả. Hết hạn: close\_reason EXPIRED, mọi Order → EXPIRED. Tiền đến sau CLOSED: không mở lại đơn, tạo một Refund FULL\_CHECKOUT.

7. Sau khi đã thu, mỗi Seller xử lý Order của mình độc lập. Customer theo dõi danh sách Order theo checkout; hủy hoặc hoàn một Order không thay đổi tổng tiền gốc hay trạng thái thực hiện Order của Seller khác.

### 5.4 Use case Seller xem ví và rút tiền (UC-D05)

Theo FRD v5.2, Seller rút một số tiền bất kỳ từ số dư khả dụng của Wallet, thay vì rút toàn bộ Settlement theo từng Order. Finance sở hữu Settlement, Wallet, WalletTransaction và Withdrawal. Rút tiền là giao dịch giả lập **hoàn tất ngay**: không cần Admin duyệt, không có trạng thái chờ, không có Payout hay provider chi trả.

**Tiền điều kiện:** account\_type SELLER, role SELLER hiện hành, seller\_id=sub, Seller APPROVED, accountStatus ACTIVE hoặc BANNED có allowlist rút theo G-14. Ví đúng owner; amount là số nguyên dương ≤ available và thỏa WD-03. Không rút pending; kiểm tra lại quyền/số dư trong luồng giao dịch hiện hành.

1. Finance trả pending, available, total\_withdrawn kèm nhãn “Giả lập — không phát sinh tiền thật” và lịch sử liên quan nghĩa vụ theo quyền. Giữ `minWithdrawalVnd=10000`; `allowBelowMinimumFullWithdrawal=true` khi accountStatus BANNED và 0 < available < 10000. Khi đó `canWithdraw=true`, `withdrawDisabledReason=null`, UI khóa số tiền ở toàn bộ available; ACTIVE có available < 10.000 vẫn không rút được. Pending-only chỉ xem tiến độ.

2. Seller nhập A (hoặc xác nhận toàn bộ khi ngoại lệ) và xác nhận; FE gửi amount\_vnd và idempotency\_key. Đích nhận cố định Ví ngoài giả lập. Server lấy seller\_id=sub, kiểm tra Seller/ví sở hữu, không tin số dư hay owner do client gửi.

3. Finance kiểm tra trạng thái Seller và hạn mức, rồi trong **một** local transaction: cập nhật có điều kiện available \>= A; available −A, total\_withdrawn \+A; tạo Withdrawal (kèm available\_before/after); ghi WalletTransaction WITHDRAWAL\_COMPLETED và OutboxEvent withdrawal.completed.

4. Commit thành công: trả ngay kết quả "Rút tiền thành công" với mã giao dịch, số tiền và số dư khả dụng còn lại; Notification gửi biên nhận. Gửi lại cùng key/payload trả đúng giao dịch cũ; cùng key, số tiền khác → 409\.

5. Từ chối/rollback nếu dưới mức tối thiểu, vượt available, User DISABLED, Seller chưa duyệt/bị từ chối, không có phiên giới hạn hợp lệ hoặc sổ ví lệch. Seller APPROVED/BANNED KHÔNG bị từ chối chỉ vì trạng thái BANNED khi còn available đủ điều kiện; không tạo Withdrawal khi từ chối.

**Hậu điều kiện:** Mỗi lần rút hoặc thành công ngay với đúng một Withdrawal và một WalletTransaction, hoặc không để lại dấu vết nào. Tổng đã rút không vượt Σ seller\_amount đã quyết toán. Khi Order bị hủy trước CONFIRMED, phần tiền nằm ở pending của chính Order đó; Refund do Payment Service thực hiện không chạm tới available hay total\_withdrawn của Seller.

### 5.5 Trạng thái Order và các job

Bảng dưới tóm tắt; state machine đầy đủ ở 8.2.

| TỪ TRẠNG THÁI | SANG TRẠNG THÁI | ĐIỀU KIỆN VÀ TÁC ĐỘNG |
| :---- | :---- | :---- |
| AWAITING\_PAYMENT | PAID | Payment SUCCESS khi checkout còn hợp lệ; commit tồn toàn bộ checkout. |
| AWAITING\_PAYMENT | PAYMENT\_FAILED / EXPIRED / CANCELLED | Thanh toán thất bại rõ ràng / hết hạn giữ hàng hoặc quá P-04 / khách hủy cả phiên; nhả hold, không hoàn tiền vì chưa thu. |
| PAID | PACKING | Seller bắt đầu chuẩn bị hàng; từ đây khách không tự hủy được. |
| PAID | CANCELLED | Khách hủy hợp lệ, Seller từ chối, J4 quá hạn hoặc invalid-after-capture đã được Order chứng minh theo 8.2B; không hủy chỉ vì ban sau thanh toán. Restock một lần; Settlement PENDING\_REFUND; Refund đúng Order. |
| PACKING | CANCELLED | Seller từ chối, J4 quá hạn hoặc invalid-after-capture theo 8.2B; không hủy chỉ vì Seller ban sau thanh toán, vẫn cho xử lý theo phiên giới hạn. |
| PACKING | DELIVERED | Seller xác nhận đã giao trước hạn 7 ngày; đặt customer\_confirm\_due\_at \= delivered\_at \+ 7 ngày. |
| DELIVERED | CONFIRMED | Customer xác nhận nhận hoặc J3 hết hạn 7 ngày; Settlement SETTLED và Wallet pending → available, không tự chi. |
| CONFIRMED, CANCELLED, PAYMENT\_FAILED, EXPIRED | Không chuyển tiếp | Trạng thái kết thúc của MVP; không có trả hàng sau hoàn tất. |

J4 kiểm tra Order ở PAID hoặc PACKING chưa DELIVERED hoặc từ chối trong 7 ngày kể từ Payment success và hủy Order quá hạn. J3 xác nhận tự động sau 7 ngày từ DELIVERED nếu Customer chưa xác nhận. Các job khóa bản ghi và kiểm tra trạng thái hiện tại để không chạy chồng với thao tác người dùng. Trường hợp PAID chưa có Shipment hoặc trạng thái vận chuyển chi tiết là giới hạn chủ động của MVP.

P-36: sắp hết hạn khi thời gian xử lý còn không quá 24 giờ trước hạn 7 ngày; không đổi thời hạn hoặc phạm vi dashboard. J5 theo P-RF-01 tại 3.4.2, J6 theo P-19 tại 3.7; lịch chạy job khác khoảng chờ retry, không tự chốt lịch cloud. P-18 không khóa tạm vì đăng nhập sai; giữ rate limit/chống lạm dụng, độc lập ban Admin.

**J7 — Đối soát trạng thái tài khoản bị cấm**, owner Identity: định kỳ đánh giá BANNED/DISABLED và chuyển cặp trạng thái theo 3.1.1/G-14 khi đã xác minh các nguồn; không tự unban, không chạm ACTIVE, không hủy Order và không làm owner invalid-after-capture. Nguồn lỗi giữ trạng thái/retry; job kiểm tra version/đợt cấm trước commit, không ghi đè Admin unban và không gia hạn restore\_until.

### 5.6 Reporting tạm hoãn và bỏ chức năng báo cáo vi phạm

Reporting Service cùng OrderReportProjection, FinancialReportProjection và InventoryReportProjection vẫn tạm hoãn trong MVP. Chức năng Customer gửi báo cáo vi phạm sản phẩm/Seller (UC-C02), chức năng Admin tiếp nhận/xử lý báo cáo từ Customer (UC-E05) và entity ViolationReport/Report được loại bỏ khỏi phạm vi; không triển khai API, màn hình hoặc bảng nghiệp vụ cho các chức năng này. Đây là quyết định loại bỏ, không phải tạm hoãn.

Giữ các màn hình vận hành cần thiết: danh sách Order, tồn kho, Settlement, Wallet (chờ/khả dụng/đã rút), lịch sử WalletTransaction, lịch sử Withdrawal và lịch sử Payment/Refund. Mỗi màn hình đọc API service sở hữu và lọc đúng quyền; không đọc trực tiếp database service khác. Admin vẫn duyệt Seller, khóa tài khoản và gỡ sản phẩm theo quyền quản trị, mà không cần luồng tiếp nhận báo cáo từ Customer. Dashboard phân tích doanh số, biểu đồ, tổng hợp đa service và export report được hoãn; UC-D04/UC-E02 chỉ còn phần tra cứu vận hành nếu tương ứng.

### 5.7 Hợp đồng API và thực thi phân quyền

Hợp đồng API tham chiếu mục 3.1 và FRD v5.5. Giữ cơ sở xác thực Identity chung nhưng tách tài khoản Customer/Seller/Admin và buộc accountType theo kênh, không cho dùng credential của vai trò khác. Đường dẫn customer/seller/admin thể hiện nghiệp vụ; quyền kiểm tra ở backend. Các endpoint được mô tả ở đây phải đồng bộ cụ thể với tab API Spec.

| API | METHOD | CHỦ THỂ | QUYỀN | HÀNH VI |
| :---- | :---- | :---- | :---- | :---- |
| /auth/register | POST | Public | — | email, password, full\_name, thông tin Customer; tạo User \+ Customer \+ CUSTOMER. Từ chối role/status/permission từ client. |
| /auth/register-seller | POST | Public | — | Email mới tạo User LOCAL account\_type SELLER \+ Seller PENDING \+ SellerApplication PENDING. Email của tài khoản Customer/Admin không được dùng lại; Seller REJECTED nộp application MỚI qua luồng xác minh email không cần đăng nhập, trên User Seller cũ. |
| /auth/login | POST | Public | — | email + password + accountType theo kênh; sai vai trò trả lỗi chung, Seller PENDING/REJECTED không login. Đánh giá nghĩa vụ G-14 cả khi DISABLED; hợp lệ tạo session_id mới và thay hash/hạn trên User, trả access token JSON và refresh cookie HttpOnly. Phiên trước mất hiệu lực. |
| /auth/google | POST | Public | — | Should: xác minh ID token và Google subject; onboarding Customer, không gộp email trùng LOCAL. Thành công dùng cùng quy tắc một phiên trên User như login. |
| /auth/refresh | POST | Public | Cookie + Origin hợp lệ | Không body refreshToken. Kiểm tra hash/hạn/phiên trên User và đánh giá G-14 như login; xoay hash/dấu reuse trong một transaction, Set-Cookie mới, không trả refresh token JSON. |
| /auth/logout | POST | User | ANY_AUTH (session own), Bearer + cookie + Origin | Bearer phải hợp lệ và khớp phiên hiện hành; cookie phải cùng User/session mới thu hồi. Sau xác thực, clear cookie và trả 204 kể cả cookie thiếu/lạ/cũ, không thu hồi phiên khác. Bearer thiếu/sai/hết hạn/phiên bị thay trả 401; sai Origin 403 không thay dữ liệu. |
| /auth/password/forgot / /auth/password/reset | POST | Public | Token reset khi reset | Thông báo quên mật khẩu đồng nhất; reset User LOCAL, xóa hash/hạn/session_id hiện hành, không tự đăng nhập. |
| /users/me | GET/PATCH | User theo G-14 | account:\*:own | GET trả user\_id, accountType, accountStatus, trạng thái cặp, accessMode, bannedAt/disabledAt/restoreUntil, authzVersion, role/quyền và hồ sơ tương ứng; phiên giới hạn chỉ đọc context tối thiểu theo allowlist. PATCH chỉ ACTIVE: full\_name/phone\_number/avatarObjectKey; không sửa role/status/accountType/email, tên Seller PENDING/APPROVED khóa theo G-02. |
| /auth/seller-reapplication/challenge | POST | Public | — | Request email, phản hồi 202 đồng nhất, chỉ gửi token xác minh cho Seller REJECTED; không cần đăng nhập. |
| /auth/seller-reapplication | POST | Public | Verification token email | Tạo SellerApplication PENDING mới cho tài khoản Seller trước đó REJECTED, giữ application cũ, không cấp role/session; các tài khoản Customer/Admin không dùng endpoint này. |
| /admin/sellers/{id}/approve hoặc reject | POST | ADMIN | seller:approve | Chỉ approve/reject SellerApplication PENDING; chỉ Admin độc lập được duyệt. Approve lặp idempotent; ghi audit/version, không duyệt hồ sơ cũ REJECTED. |
| /identity/admin/ban/{userId} | POST | ADMIN | account:moderate | Body `{reason: string 1..1000}`, không scope (gửi scope → 400). Đọc nghĩa vụ trước commit, lỗi nguồn → 503 không ban một phần; chỉ Customer/Seller APPROVED, không ADMIN; cặp trạng thái/mốc/idempotency theo G-14. |
| /identity/admin/unban/{userId} | POST | ADMIN | account:moderate | Body `{reason?: string <=1000}`, không scope; ACTIVE idempotent, BANNED gỡ bất kỳ lúc nào, DISABLED chỉ now ≤ restoreUntil; sau hạn 409 ACCOUNT_RESTORE_WINDOW_EXPIRED. Unban hợp lệ không cần Order/Finance, vẫn kiểm tra account/Seller và local transaction. |
| /customer/\* / seller/\* / admin/\* | Theo nghiệp vụ | User | Permission cụ thể | Lọc ownership bằng sub; dùng ma trận 3.1.9 và state machine, không chỉ kiểm tra prefix URL. |
| /payments/refunds?checkoutSessionId= | GET | CUSTOMER | order:read:own | Đọc Refund ORDER/FULL_CHECKOUT của Payment đúng chủ kể cả phiên đóng, không gọi gateway; sai chủ 404, BANNED chỉ nghĩa vụ. |
| /payments/refunds/{refundId}/retry | POST | CUSTOMER | refund:retry:own | Idempotency-Key và expectedRetryCycle bắt buộc; FAILED + customer_retry_required=true, CAS mở một chu kỳ/công việc bền vững cùng local transaction; giữ Refund/số tiền/key gateway. Seller/Admin 403; snapshot cũ 409 REFUND_RETRY_NOT_SAFE. |

JWT sub=user\_id riêng của một account\_type; gồm iss/aud/iat/exp/jti/authz\_version/session\_id, có thể có accountType và role snapshot để UI nhận biết nhưng backend luôn xác minh quyền hiện hành. Không hỗ trợ một sub chứa nhiều role; Customer/Seller IDs chỉ có trên tài khoản loại tương ứng. Không xuất password\_hash, google\_subject, refresh token vào hồ sơ công khai.

Phiên được đối chiếu với `users.session_id` theo [0003](../decisions/0003-mot-phien-refresh-tren-user.md), không tra AuthSession ở thiết kế mới. Khi logout, reset hoặc login khác đã xóa/thay session_id, JWT cũ bị từ chối 401 dù chưa hết exp. Không dùng authz_version để thay cho kiểm tra phiên; version quyền vẫn giữ vai trò hiện có.

Ban/unban giữ envelope `data`, 200 trả `{userId,accountType,accountStatus,userStatus,customerStatus|null,sellerStatus|null,accessMode,bannedAt|null,disabledAt|null,restoreUntil|null,authzVersion}`; ban thêm `obligations={hasOpenOrders,hasPendingPayment,hasUnresolvedRefund,hasPendingFinance,pendingBalanceVnd,availableBalanceVnd}`. Customer có hai số dư 0 và hasPendingFinance=false. Giữ 401/403/404, 409 `INVALID_STATE_TRANSITION` cho Seller chưa duyệt; lỗi deadline không dùng lỗi Product. Các DTO Admin detail `CustomerAdminView`/`SellerAdminView` thêm `banImpact={evaluatedAt,resultingAccountStatus,obligations}` và trạng thái/mốc tài khoản; không endpoint preview mới. Preview chỉ snapshot cảnh báo; POST đọc lại và trả kết quả thực tế. Nguồn lỗi không hiển thị 0 giả, không cho xác nhận ban đến khi tải được. Auth/self/authorization-context mang các trạng thái/mốc/accessMode tương ứng, không chỉ response ban.

MVP mỗi API bảo vệ xác minh chữ ký/thuật toán cho phép, issuer, audience, hạn token, sau đó hỏi Identity qua kênh xác thực service để lấy trạng thái, authz\_version, quyền và hồ sơ hiện tại. Không cache quyết định quyền trong MVP; Identity lỗi thì từ chối truy cập bảo vệ với 503\. Version token cũ trả 401 và client refresh; thiếu quyền hoặc hồ sơ bị hạn chế trả 403\. Với tài nguyên không thuộc chủ thể trả 404 nhất quán. Không có token/hết hạn trả 401; xung đột unique/idempotency trả 409\.

Service sở hữu tải tài nguyên và kiểm tra customer\_id/seller\_id với sub từ token đã xác minh, không tin body/header X-User-Id do client gửi. API liệt kê luôn thêm bộ lọc ownership tại server. Trước checkout, thu tiền và rút tiền phải đọc trạng thái hiện hành; khóa và cập nhật có điều kiện trong transaction dữ liệu sở hữu. Không thể nguyên tử giữa Identity và Finance: lệnh đã được chấp thuận trước thời điểm khóa có thể hoàn tất; khóa ngăn các yêu cầu được kiểm tra sau thời điểm commit Identity. Callback và job dùng service credential, allowlist thao tác, idempotency và audit, không bị chặn hoàn tiền chỉ vì User bị khóa.

Thay role/status/ban/approval hoặc quyền phiên giới hạn phải tăng authz\_version, ghi audit; không cho User có hơn một role đang hiệu lực. Admin chỉ cấp nội bộ, không tự nâng quyền. Google chỉ CUSTOMER, không thể dùng Google tạo/cấp SELLER.

### 5.8 Migration dữ liệu và kiểm thử RBAC

1. Sao lưu và diễn tập trước migration. Dữ liệu User đa vai trò hiện có phải được tách thành các tài khoản Customer/Seller/Admin độc lập với user\_id/email khác nhau, bảo toàn ownership; yêu cầu người dùng cung cấp/xác nhận email riêng khi có trùng, không tự ghép/tự phát sinh credential có thể đăng nhập. Di chuyển tham chiếu qua mapping old\_user\_id \+ role → new\_user\_id.

2. Seed RBAC một role hiện hành/User, users.account\_type, users.avatar\_object\_key, SellerApplication. Giữ UUID của hồ sơ Customer/Seller nếu không xung đột; User đa vai trò phải tách ID và cập nhật toàn bộ FK/recipient/event theo role; giữ token cũ vô hiệu khi cutover.

3. Chuyển credential/tên/SĐT của tài khoản tương ứng sang users; Seller.logo\_object\_key → users.avatar\_object\_key; không tạo avatar và logo riêng. Đối chiếu email/phone unique; Customer/Seller không cùng users.user\_id. Seller REJECTED chuyển thành SellerApplication lịch sử, lần nộp mới tạo application\_id mới. AdminAccount chuyển thành User accountType ADMIN và role ADMIN.

4. Map notification recipient\_type \+ recipient\_id → recipient\_user\_id \+ audience\_context; map reset/session/audit/stock actor và idempotency sang user\_id. Khóa idempotency cũ giữ endpoint/ngữ cảnh để không tái thực hiện lệnh. Nếu đổi ID, cập nhật CustomerAddress, Cart, Review, CheckoutSession, Order, Payment, Seller Product, Settlement, Wallet, Withdrawal và các projection/event metadata có liên quan theo mapping của từng service.

5. Kiểm tra rowcount, FK, role tối đa một/User, profile không chồng lấp, ảnh chuyển đúng, SellerApplication PENDING duy nhất. Chuyển login sang accountType và buộc đăng nhập lại, thu hồi token/session cũ; thử nghiệm luồng Customer/Seller/Admin riêng trước khi hoàn tất migration, rollback phải nhất quán liên service.

**Chuyển phiên theo quyết định 0003 (chưa chạy):** thêm migration mới sau các migration đã áp dụng, bổ sung bộ ba nullable và CHECK/UNIQUE trên User. Thu hồi toàn bộ phiên cũ, yêu cầu đăng nhập lại; không chọn một AuthSession bất kỳ để giữ. Chuyển `rotated_refresh_tokens` sang tham chiếu User và mã phiên không có FK đến AuthSession; cập nhật mọi repository/validator/API/internal authorization-context sang User rồi bỏ AuthSession trong cùng đợt cutover. Không giữ hai nơi lưu phiên hiệu lực, không sửa migration cũ hoặc coi tài liệu này là đã sửa Supabase.

#### Bất biến RBAC cần kiểm thử

Một User chỉ có một account\_type và tối đa một role đang hiệu lực; Customer/Seller không dùng chung user\_id. Seller PENDING/REJECTED không login, REJECTED nộp hồ sơ MỚI sau xác minh email; approve lặp chỉ cấp 1 role và 1 ví; chỉ Seller APPROVED được cấm, không tự duyệt cùng tài khoản; avatar của Seller là logo.

Login sai accountType không tạo phiên. BANNED Customer/Seller chỉ vào tài nguyên nghĩa vụ đúng chủ theo 3.1.1; terminal Order không giữ phiên nếu không còn nghĩa vụ riêng. Pending/Refund FAILED/Payment SUCCESS chưa apply vẫn giữ quyền giới hạn phù hợp. DISABLED phải được đánh giá nghĩa vụ đến muộn trước từ chối; mọi list/detail/action kiểm tra allowlist hiện hành, ownership/state.

Seller BANNED không bán mới nhưng xử lý nghĩa vụ/rút available theo WD-03, không tự hủy Order hay đóng băng ví do ban. Hết nghĩa vụ mới DISABLED; User DISABLED không dùng API bảo vệ, nhưng callback Payment/Refund/job vẫn chạy. Token cũ không mở lại quyền FULL.

Token đã cấp trước revoke/ban/disable hoặc thay RolePermission không dùng tiếp quyền cũ; refresh chỉ nhận quyền hiện tại. Identity unavailable không cho phép truy cập. Reset password thu hồi phiên. Audit mọi thay đổi quyền/khóa; migration giữ tham chiếu và không tạo giao dịch tiền hay gửi thông báo lặp.

**Ca nghiệm thu một phiên trên User:** login B thay login A thì cả access/refresh A bị chặn; login sai không ảnh hưởng B. Refresh giữ session_id, thay hash và ghi dấu cũ nguyên tử; reuse thuộc B thu hồi B ngay cả khi response là 401, còn token A không được thu hồi B. Logout cookie cũ không xóa B; logout cookie B trả 204/clear cookie và access B bị chặn. Hai login, hai refresh, refresh–logout hoặc refresh–login đồng thời không được ghi lại phiên đã thu hồi/thay thế. Reset/đổi mật khẩu/DISABLED xóa đủ bộ ba. Các ca này là yêu cầu kiểm thử khi triển khai, chưa phải kết quả chạy.

**Ca đối chiếu đặc tả Thẻ 15** — bổ sung nghiệm thu cho [FRD](FRD.md) UC-A03/E03/D05/G03, UC-D01/B06/G02; đây là điều kiện phải kiểm thử, không phải kết quả runtime:

| Ca | Dữ liệu / tác động phải bảo toàn |
| :---- | :---- |
| B1 | Customer chỉ có CONFIRMED, không Payment/Refund dở → DISABLED, thu hồi phiên, lập disabledAt/restoreUntil; lịch sử không giữ ngoại lệ. |
| B2 | Customer còn PAID/DELIVERED → BANNED, chỉ đơn đúng chủ; hủy PAID/xác nhận DELIVERED khi hợp lệ, không giỏ/thu mới/review. |
| B3 | CANCELLED nhưng Refund FAILED → BANNED đọc nghĩa vụ; hết lượt retry thì OPEN_ORDERS_ONLY với refund:retry:own trên đúng nghĩa vụ. Chỉ DISABLED sau Refund SUCCESS và mọi nguồn hết nghĩa vụ. |
| B4 | Seller không đơn thao tác được, pending > 0 hoặc settlement effect chưa apply (kể cả số tiền 0) → BANNED/OBLIGATIONS_READ_ONLY, không rút pending; available đến sau cho rút theo quyền. |
| B5 | BANNED available=9.500: rút 9.500 thành công, rút 5.000 → 422 WITHDRAWAL_BELOW_MINIMUM; ACTIVE rút 9.500 vẫn bị từ chối. |
| B6 | Rút hết, các nguồn xác minh hết nghĩa vụ, J7 chạy hai lần → DISABLED một lần, không reset deadline/event/ledger. |
| B7 | Unban DISABLED đúng restoreUntil → 200 ACTIVE, sau 1 giây → 409 ACCOUNT_RESTORE_WINDOW_EXPIRED, dữ liệu/tiền nguyên vẹn. |
| B8 | BANNED hơn 30 ngày chưa từng DISABLED vẫn xử lý nghĩa vụ và unban bất kỳ lúc nào; cửa sổ chưa bắt đầu. |
| B9 | Order/Finance timeout → không giả zero/disable/ban một phần; J7 đồng thời Admin unban không ghi đè ACTIVE. |
| B10 | DISABLED rồi available xuất hiện muộn → BANNED giới hạn, không unban; giữ mốc đợt cấm và không gia hạn khi DISABLED lại. |
| B11 | Seller PENDING/REJECTED hoặc target ADMIN không được cấm qua UC-E03; approval/role không đổi. |
| P1/P2 | Provision timeout/replay cùng key → một Product HIDDEN/cùng productId; chỉ publish sau Inventory xác nhận nếu hợp lệ. Ban Seller hoặc REMOVED trong lúc chờ không bật SELLING; validation/media lỗi trước tạo không lưu Product. |
| O1 | Payment SUCCESS, một Order invalid đã chứng minh → Payment giữ SUCCESS, chỉ hủy/hoàn Order đủ điều kiện đó; đủ checkout.paid/order.cancelled, pending chỉ giảm khi Refund SUCCESS. |
| O2 | Seller ban sau capture, payment.succeeded đến chậm → không tự hủy/hoàn, không suy thời điểm invalid từ status hiện tại; giữ xử lý nghĩa vụ/J4. |
| O3 | Identity/Product lỗi hoặc replay → retry bền vững, không coi lỗi nguồn là invalid, không nhân đôi SOLD/RESTOCK/Refund/ledger. |

## 6 Bảng kỹ thuật tối thiểu

Các bảng sau hỗ trợ bảo mật và độ tin cậy, không tính vào 30 entity ở mục 2\. User và bốn bảng RBAC là dữ liệu Identity, không phải bảng kỹ thuật. Theo [0003](../decisions/0003-mot-phien-refresh-tren-user.md), phiên hiện hành nằm trên User; reset token và dấu refresh token đã xoay tham chiếu user_id.

| BẢN GHI | NƠI CẦN | TRƯỜNG TỐI THIỂU | MỤC ĐÍCH |
| :---- | :---- | :---- | :---- |
| OutboxEvent | Service phát event | event\_id, event\_type, aggregate\_id, payload, status, created\_at | Ghi event cùng local transaction để không mất sự kiện. |
| InboxEvent | Consumer quan trọng | message\_id, consumer\_name, processed\_at; UQ(message\_id, consumer\_name) | Chặn xử lý Payment, kho hoặc ghi bút toán ví lặp. |
| AdminAuditLog | Identity và quản trị từng service | actor\_user\_id, action, target\_type, target\_id, reason, before\_after, request\_id, happened\_at | actor\_user\_id tham chiếu User (FK nội bộ hoặc Ref liên service). Ghi APPROVE/REJECT/BAN/UNBAN/DISABLE/ENABLE/GRANT\_ROLE/REVOKE\_ROLE và mọi thao tác Admin; không ghi token/mật khẩu. |
| PasswordResetToken | Identity LOCAL auth | token\_hash UQ, user\_id FK User, expires\_at, used\_at | P-05 expires_at=tạo+30 phút, token chỉ dùng một lần; thông báo quên mật khẩu chung, không mở quyền Seller PENDING/REJECTED. |
| WorkerExecutionLog | Payment Finance và jobs | job\_key, reference\_id, status, retry\_count; Refund thêm retry\_cycle, attempted\_at, outcome, next\_retry\_at | Giữ lịch sử mọi chu kỳ/lần đầu/retry/query và lịch chờ J5; reset counter Refund không xóa lịch sử. Lưu công việc/Outbox cùng local transaction trước gọi gateway ngoài transaction. |
| IdempotencyRecord | API chống lặp | actor\_user\_id, operation\_context, endpoint, key, request\_hash, result\_ref, created\_at | UQ(actor\_user\_id, operation\_context, endpoint, key); ngữ cảnh CUSTOMER/SELLER/SERVICE không cấp quyền. Kiểm tra quyền trước trả kết quả cũ. |
| RotatedRefreshToken | Identity | token\_hash TEXT PK, user\_id UUID FK User, session\_id UUID, rotated\_at TIMESTAMPTZ | Dấu chống reuse của refresh token đã xoay; không chứa raw token, không phải phiên hiệu lực. Chỉ thu hồi khi cả User và session_id khớp phiên hiện hành; không FK đến AuthSession. Giữ đủ dấu của chuỗi khi phiên còn hiệu lực, kể cả sau gia hạn. |
| SimulatedTransaction | Payment (bộ giả lập, giai đoạn 2\) | sim\_ref PK, kind PAYMENT/REFUND, idempotency\_key, amount\_vnd, scenario, status, complete\_at, created\_at | Lưu giao dịch giả lập có kết quả đến sau (TIMEOUT, LATE\_SUCCESS) để J2/J5 tra cứu hoặc bộ giả lập tự gọi xử lý callback (8.4). |
| Không dùng LoginIdentifier | Identity | UNIQUE(User.email), UNIQUE(User.google\_subject) khi khác NULL | Xóa registry tách loại tài khoản sau migration. AdminAccount cũng chuyển thành User \+ UserRole ADMIN. |

AuthSession trong thiết kế trước được [0003](../decisions/0003-mot-phien-refresh-tren-user.md) thay bằng bộ ba trường User ở §3.1.1. Code/migration và Supabase hiện vẫn có `auth_sessions`; việc bỏ bảng chỉ được thực hiện trong task triển khai/cutover có kiểm chứng, không phải lần sửa tài liệu này.

## 7 Đối chiếu thay đổi và điểm cần xác nhận

| HẠNG MỤC | KẾT QUẢ HIỆN HÀNH BẢN 5.0 |
| :---- | :---- |
| Vai trò và đăng ký | User vẫn là credential schema chung nhưng tài khoản CUSTOMER/SELLER/ADMIN tách riêng bằng account\_type; mỗi User tối đa một role hiệu lực, Seller PENDING/REJECTED chưa có quyền login; SellerApplication lưu nhiều lần nộp. |
| OrderSeller và SellerOrder | Loại bỏ toàn bộ entity cũ; chuyển seller\_id, trạng thái, thời hạn và xử lý tiền sang Order. |
| Checkout và Payment | Bổ sung từ điển CheckoutSession còn thiếu; quan hệ 1 checkout có N Order và tối đa 1 Payment. Bỏ UQ sai trên Order.checkout\_session\_id. |
| Hoàn và quyết toán | Refund theo Order hoặc FULL\_CHECKOUT; Settlement theo Order. Finance ghi pending lúc Payment SUCCESS, chuyển available khi Order CONFIRMED và giảm pending sau Refund SUCCESS; không thêm PaymentAllocation. |
| Search | Hoãn service và projection; tìm tại DB của Product Service và Identity theo đối tượng. Chuẩn hóa cả từ khóa và tên trước LIKE theo §5.2 để không phân biệt dấu tiếng Việt và hoa thường (P-37, chốt 19/11/2026); ILIKE đơn thuần không đủ. |
| Reporting / báo cáo vi phạm | Reporting Service và ba projection vẫn tạm hoãn. Loại bỏ UC-C02 (Customer báo cáo Product/Seller), UC-E05 (Admin xử lý báo cáo từ Customer) và entity ViolationReport/Report; duy trì tra cứu vận hành qua API. |
| Cart (4.6) | Bỏ giỏ khách vãng lai theo FRD UC-B03: bỏ owner\_type, guest\_token\_hash, merged\_into\_cart\_id, expires\_at, trạng thái MERGED/EXPIRED và luồng merge; mỗi Customer đúng một giỏ. Thêm bảng trạng thái dòng giỏ (chuyển từ FRD 4.7). |
| Trạng thái Order (4.6) | Theo FRD: thêm PACKING, PAYMENT\_FAILED, EXPIRED; thêm packing\_at, confirmed\_by, cancelled\_by\_type, needs\_manual\_review. CheckoutSession.close\_reason thành enum PAYMENT\_FAILED / EXPIRED / CANCELLED\_BY\_CUSTOMER; thêm address\_id, paid\_at. |
| Mua ngay (4.6) | Thêm CheckoutSession.checkout\_source (CART / BUY\_NOW); source\_cart\_id và cart\_snapshot chỉ bắt buộc khi CART; OrderItem.source\_cart\_item\_id NULL với BUY\_NOW. |
| Tiếp nhận FRD mục 4 | Giữ quy tắc dữ liệu; địa chỉ 3.1.4, Identity/RBAC 3.1, bảng kỹ thuật mục 6\. AdminAccount chuyển vào User \+ ADMIN. |
| Bộ giả lập thanh toán (4.6) | Thanh toán/hoàn tiền qua PaymentGateway với bộ giả lập nội bộ, không có nhà cung cấp bên ngoài; provider \= SIMULATOR; thêm bảng kỹ thuật SimulatedTransaction; lộ trình 2 giai đoạn (8.4). |
| Hoàn tiền và số dư chờ (4.6) | Giữ quy tắc của bản 4.5: đơn bị hủy chỉ chuyển Settlement sang PENDING\_REFUND; số dư chờ chỉ bị trừ khi Refund SUCCESS (REFUND\_DEBIT). FRD v5.3 đã sửa các UC cho khớp. |
| Rút tiền (4.5) | Theo FRD v5.2: Withdrawal rút theo số tiền từ available của Wallet, hoàn tất tức thời trong một transaction, không có trạng thái. Bỏ entity Payout, bỏ reserved\_balance\_vnd và reserved\_delta\_vnd, bỏ các loại bút toán WITHDRAWAL\_RESERVED / PAYOUT\_COMPLETED / WITHDRAWAL\_RELEASED và reference\_type PAYOUT. Thêm Wallet.total\_withdrawn\_vnd, WalletTransaction.withdrawn\_delta\_vnd và withdrawal\_id, bút toán WITHDRAWAL\_COMPLETED; Withdrawal thêm withdrawal\_code, available\_before/after\_vnd, withdrawn\_at, đích nhận cố định "Ví ngoài giả lập". |
| Media và kiểm đếm | Giữ Product.media JSONB và 8 service; 30 entity bao gồm User và bốn bảng RBAC, Reporting tạm hoãn. |
| Tham chiếu và số mục | Đánh số lại liên tục, bỏ tham chiếu SellerOrder và allocation cũ trong quan hệ, ownership, bất biến và notification. |
| Đồng bộ danh sách xung đột (06/11/2026), đối chiếu Thẻ 15 (17/11/2026) | Giữ Product, full\_name/phone tại users, avatar/logo chung, UUID Order/CheckoutSession và withdrawal\_code. Policy cấm hiện hành theo 3.1.1/G-14, thay điều kiện chỉ có đơn/available bằng toàn bộ nghĩa vụ; chỉ Seller APPROVED được cấm. Các mô tả cũ không thay vòng đời và quyền theo Thẻ 15. |
| Customer và Seller | Bỏ credential trùng ở Customer/Seller; khóa con bằng User.user\_id. Token, reset, notification, audit và actor dùng user\_id thống nhất; cập nhật API và kế hoạch migration ở 5.7–5.8. |

### 7.1 Các quyết định còn mở

* Xác định thời điểm triển khai Reporting Service và các read model phân tích sau MVP, nếu có.

* P-10 đã chốt: 1–99 đơn vị/dòng, tối đa 50 sản phẩm khác nhau/giỏ; không còn quyết định mở về hai giới hạn này.

* MVP dùng bộ giả lập thanh toán nội bộ cho thanh toán và hoàn tiền; rút tiền là giao dịch giả lập tức thời. Nếu sau MVP chuyển rút tiền sang cổng chi trả thật, phải bổ sung trạng thái giao dịch rút, ngăn "đang rút", cơ chế đối soát/retry và chính sách đích nhận; nếu có đổi trả/chargeback sau Order CONFIRMED thì cần thêm thời gian tạm giữ và cơ chế xử lý số dư âm (WD-07, mục 8.3).

* P-COM-01 đã chốt chung 5%=500 bps và P-19/P-RF-01 đã chốt retry theo [parameters.md](parameters.md). P-37 chốt ngày 19/11/2026 cho tên sản phẩm và tên cửa hàng tại §5.2. Timeout đối soát P-04, lịch cloud và các tham số còn mở giữ riêng; N-09/N-11 không đổi.

* Email của Seller REJECTED giữ tại User Seller; đăng ký lại tạo SellerApplication mới cho cùng seller\_id sau xác minh email, không sửa hồ sơ cũ. Không tự chọn giá trị các tham số mở; các API/FRD phải đồng bộ tài khoản riêng và ngoại lệ ban/withdraw.

## 8 Quy tắc toàn cục, state machine và dòng tiền (chuyển từ FRD mục 4)

Mục 8 giữ quy tắc toàn cục, trạng thái và dòng tiền tiếp nhận từ FRD v5.3; Identity/RBAC v5.1 dùng tài khoản riêng. [Thẻ 15](coverage.md#the-15) thay phần cấm/gỡ cấm theo 3.1.1/G-14/8.2D, bổ sung ngoại lệ WD-03 và làm rõ trigger 8.2B; các quy tắc tiền/tồn và state machine vẫn bắt buộc sau kiểm tra quyền, không suy tính năng đã triển khai.

### 8.1 Quy tắc toàn cục (G-01 đến G-17)

| MÃ | QUY TẮC | NƠI ÁP DỤNG |
| :---- | :---- | :---- |
| G-01 | G-01: Email chuẩn hóa duy nhất cho mỗi tài khoản User; CUSTOMER/SELLER/ADMIN không chia sẻ user\_id/email. User chỉ có một account\_type và một role đang hiệu lực. Seller REJECTED giữ email/User, muốn đăng ký lại cần tạo SellerApplication mới sau xác minh email (không cập nhật application cũ); chỉ kiểm tra trùng tên shop với Seller PENDING/APPROVED. GOOGLE chỉ CUSTOMER; không tự gộp GOOGLE/LOCAL. | 3.1; 5.1; 5.8 |
| G-02 | Không đổi User.email; User có Seller PENDING/APPROVED không đổi full\_name (tên cửa hàng). Tên chung và mật khẩu chỉ cập nhật tại User qua API tự phục vụ. | 3.1; 5.7 |
| G-03 | Giá trong đơn là snapshot lúc tạo đơn; sửa giá sau đó không ảnh hưởng đơn đã tạo. | UC-B04, D01; 3.6.3 |
| G-04 | Tồn kho khả dụng \= on\_hand\_quantity − reserved\_quantity. Trước thanh toán, phiên hết hạn/thất bại/hủy nhả toàn bộ giữ chỗ của N đơn. Sau thanh toán chung thành công, trừ kho một lần theo từng dòng của mọi đơn. Order hủy hợp lệ chỉ hoàn tồn kho của chính Order đó, một lần. | UC-B04, B06, B08, D03; 3.6.4–3.6.6 |
| G-05 | Quên mật khẩu: luôn trả cùng một thông báo dù email có tồn tại hay không. | UC-A05 |
| G-06 | Sản phẩm bán được khi Product SELLING, Seller APPROVED và ACTIVE, User của Seller ACTIVE và UserRole SELLER chưa thu hồi. Product Service/search/checkout xác minh cùng policy, không chỉ tin status sản phẩm. | 3.1; 3.2.2; 5.7 |
| G-07 | Mật khẩu: tối thiểu 8 ký tự, có chữ hoa, chữ thường và chữ số. | UC-A01, A02, A04, A05 |
| G-08 | Mỗi Customer tối đa một CheckoutSession AWAITING\_PAYMENT. Một checkout sinh 1 CheckoutSession, tối đa 1 Payment và N Order (N \= số Seller, mỗi Seller đúng một Order). Yêu cầu đồng thời và submit lặp không tạo thêm CheckoutSession/Order/Payment. | UC-B04, B05, B10; 3.6.1 |
| G-09 | Khóa chống lặp: tạo checkout dùng khóa phiên; Payment dùng key theo CheckoutSession; Refund dùng key ổn định theo (payment\_id, order\_id) hoặc key hoàn toàn bộ phiên; Withdrawal dùng key do client gửi, duy nhất theo Seller; mỗi WalletTransaction có business\_event\_key duy nhất. Cùng key \+ cùng nội dung → trả kết quả cũ; cùng key, nội dung khác → 409\. Tổng hoàn không vượt số đã thu. | UC-B04, B05, B10, D05, G02; 3.4, 3.5, 3.6.1 |
| G-10 | Tiền tệ VND, số nguyên (P-17). Phí vận chuyển \= 0; giá niêm yết đã gồm thuế. | Toàn hệ thống |
| G-11 | Mọi chuyển trạng thái CheckoutSession, Order, Payment, Refund, Settlement, mọi giao dịch rút tiền, mọi bút toán ví và tác động tồn kho dùng cập nhật có điều kiện trong transaction. Gọi bộ giả lập thanh toán (hoặc cổng thật sau này) ngoài transaction và lưu ý định bền vững; callback/job chạy lặp không thu/hoàn trùng. | 3.4–3.6; mục 6 |
| G-12 | Mọi hành động của Admin ghi AdminAuditLog (mục 6). | UC-E01–E08 |
| G-13 | Không xóa vật lý dữ liệu đã được đơn hàng tham chiếu (sản phẩm, Seller…). Ngoại lệ: CustomerAddress xóa vật lý (hard delete) vì Order đã lưu snapshot địa chỉ. WalletTransaction chỉ được thêm, không sửa/xóa. | 3.1.4, 3.2.2, 3.5.3 |
| G-14 | Cấm một tài khoản Customer/Seller APPROVED, không scope/khóa độc lập, không cấm ADMIN hoặc ảnh hưởng tài khoản khác. accountStatus và cặp User/hồ sơ theo 3.1.1; còn nghĩa vụ Order/Payment/Refund/Finance/pending/available → BANNED với allowlist hiện hành; mọi nguồn xác minh hết → DISABLED. J7/login/refresh đánh giá cả nghĩa vụ đến muộn, không tự unban, giữ mốc đợt cấm; lỗi nguồn không giả zero, request cần xác minh → 503, job giữ trạng thái/retry. Unban BANNED bất kỳ lúc nào; DISABLED chỉ now ≤ restore_until, quá hạn → ACCOUNT_RESTORE_WINDOW_EXPIRED, không xóa dữ liệu/tiền. Không giỏ/thu mới/review/bán mới, không tự hủy Order hoặc đóng băng available do ban; ownership/state luôn kiểm tra. | 3.1.1; 5.5; 5.7; 5.8; FRD UC-A03/E03/G03 |
| G-15 | Mọi thanh toán, hoàn tiền, số dư ví, hoa hồng, giao dịch rút tiền trong MVP là giả lập, do bộ giả lập thanh toán nội bộ xử lý (mục 8.4); không có nhà cung cấp bên ngoài. Nhãn "Giả lập — không phát sinh tiền thật" xuất hiện trên màn hình/email liên quan; không nhập thông tin thẻ, tài khoản ngân hàng hay chuyển khoản thật. | UC-B05, D05, G01, G02 |
| G-16 | "Mua ngay" (UC-B10) dùng chung mọi quy tắc của checkout từ giỏ (đăng nhập, SĐT, địa chỉ, giữ hàng, snapshot giá, G-08, G-09, G-14). Khác biệt duy nhất: checkout\_source \= BUY\_NOW, chỉ 1 sản phẩm với số lượng chọn trên trang sản phẩm (≥ 1, ≤ tồn kho khả dụng, ≤ P-10), và giỏ hàng không bị đọc, thêm hay trừ ở bất kỳ bước nào. | UC-B10; 3.6.1 |
| G-17 | Seller chỉ được rút từ số dư khả dụng; số dư chờ quyết toán không được rút, ứng trước hay dùng để bù. Mọi số dư ví ≥ 0 tại mọi thời điểm. Refund không bao giờ trừ số dư khả dụng hay số đã rút của Seller. Chi tiết ở 8.3. | UC-D05, G02; 3.5 |

### 8.2 State machine

**A. CheckoutSession** — kết quả thanh toán áp dụng đồng thời cho cả N Order của phiên.

| TỪ | SỰ KIỆN | SANG |
| :---- | :---- | :---- |
| Chưa có | Khách checkout hợp lệ, giữ đủ toàn bộ hàng | AWAITING\_PAYMENT; tạo N Order AWAITING\_PAYMENT |
| AWAITING\_PAYMENT | Payment SUCCESS, toàn bộ kiểm tra hợp lệ | PAID; mọi Order → PAID; ghi số dư chờ cho từng Order (8.3) |
| AWAITING\_PAYMENT | Payment FAILED rõ ràng | CLOSED (close\_reason \= PAYMENT\_FAILED); mọi Order → PAYMENT\_FAILED; nhả toàn bộ hàng |
| AWAITING\_PAYMENT | Hết hạn giữ hàng, không có Payment PENDING (J1), hoặc Payment PENDING quá P-04 (J2b) | CLOSED (close\_reason \= EXPIRED); mọi Order → EXPIRED; nhả toàn bộ hàng |
| AWAITING\_PAYMENT | Khách hủy, không có Payment PENDING | CLOSED (close\_reason \= CANCELLED\_BY\_CUSTOMER); mọi Order → CANCELLED; nhả hàng; không hoàn tiền |
| CLOSED | Payment SUCCESS đến muộn | Giữ CLOSED; tạo Refund FULL\_CHECKOUT; không chuyển Order nào sang xử lý, không trừ kho, không ghi bút toán ví |

**B. Order** — trạng thái xử lý của từng đơn sau thanh toán. Không có trạng thái "đang giao".

| TỪ | AI / SỰ KIỆN | SANG |
| :---- | :---- | :---- |
| PAID (chờ xử lý) | Seller bắt đầu chuẩn bị hàng | PACKING (đang đóng gói) |
| PAID | Khách hủy hợp lệ (trước khi Seller đóng gói) | CANCELLED → Refund; Settlement PENDING\_REFUND |
| PAID hoặc PACKING | Seller chọn "Từ chối giao hàng", nhập lý do | CANCELLED → Refund; Settlement PENDING\_REFUND |
| PAID hoặc PACKING | 7 ngày kể từ thanh toán mà Seller chưa xác nhận đã giao/từ chối (J4) | CANCELLED → Refund; Settlement PENDING\_REFUND |
| PAID hoặc PACKING | **Order Service, khi áp dụng kết quả Payment SUCCESS qua payment.succeeded hoặc đường đối soát dùng chung handler**: chứng minh Seller/sản phẩm không hợp lệ theo điều kiện giao dịch tại lúc thu tiền; không phải ban sau thanh toán. Xem trình tự bên dưới và SAD §5.1. | Chỉ Order đủ điều kiện invalid → CANCELLED → Refund; Settlement PENDING\_REFUND |
| PACKING | Seller chọn "Xác nhận đã giao" trước hạn 7 ngày | DELIVERED (hiển thị: Đã giao — chờ khách xác nhận) |
| DELIVERED | Khách chọn "Đã nhận hàng", hoặc 7 ngày kể từ delivered\_at khách không phản hồi (J3) | CONFIRMED → Settlement SETTLED; số dư chờ → khả dụng |

Mốc 7 ngày thứ nhất: từ paid\_at (thời điểm thanh toán chung) đến hạn Seller xác nhận đã giao hoặc từ chối (seller\_action\_due\_at). Mốc thứ hai: từ delivered\_at của riêng Order đến hạn khách xác nhận (customer\_confirm\_due\_at). Hai bộ đếm độc lập, tính riêng từng Order. Không có chuyển trạng thái từ DELIVERED hoặc CONFIRMED sang CANCELLED — đây là cơ sở để tiền khả dụng không bao giờ phải thu hồi. CONFIRMED, CANCELLED, PAYMENT\_FAILED, EXPIRED là trạng thái kết thúc; không tái mở đơn khi nhận thanh toán trễ.

**Trigger invalid-after-capture (không tạo loại hủy mới):** Payment đã SUCCESS; Order xác minh điều kiện giao dịch qua Identity/Product ngoài transaction giữ khóa, rồi áp dụng cập nhật có điều kiện PAID/PACKING cho đúng Order được chứng minh invalid. Giữ Payment SUCCESS, bảo đảm đủ `checkout.paid`/`order.cancelled` để SOLD/RESTOCK, Refund và tài chính được áp dụng idempotent; Payment tạo Refund đúng một Order, Finance giữ pending/PENDING\_REFUND đến Refund SUCCESS. Order Seller khác hợp lệ không bị hủy/hoàn. Tham chiếu [SAD §5.1](software-architecture.md).

Lỗi nguồn/tạm mất kết nối không chứng minh invalid: giữ công việc bền vững để retry, không nhánh hủy/hoàn. Seller bị ban **sau capture** không phải lý do hủy; khi event đến trễ không suy thời điểm ban từ trạng thái hiện tại. Chưa có bằng chứng điều kiện giao dịch tại thời điểm thu thì giữ xác minh/retry. Replay không nhân đôi kho/Refund/ledger. Không quét hủy toàn bộ đơn Seller, không chuyển DELIVERED/CONFIRMED về CANCELLED, không dùng J7/J2/J5 làm owner quyết định này; J4 vẫn áp dụng nghiệp vụ quá hạn bình thường. Quy tắc late-payment FULL\_CHECKOUT tại 8.2A giữ nguyên.

**C–J. Các đối tượng khác**

| ĐỐI TƯỢNG | VÒNG ĐỜI |
| :---- | :---- |
| C. Seller duyệt | SellerApplication: NEW → PENDING → APPROVED/REJECTED. REJECTED là trạng thái kết thúc của từng lần nộp; muốn nộp lại tạo application\_id mới PENDING, giữ bản ghi REJECTED. Seller.approval\_status phản ánh lần mới nhất. Chỉ Seller APPROVED có thể bị cấm (status ACTIVE ⇄ BANNED), không cấm PENDING/REJECTED. Duyệt cấp role SELLER và tạo ví một lần. |
| D. Tài khoản và hồ sơ | Một cặp trạng thái do Identity chuyển nguyên tử: ACTIVE=(users ACTIVE, hồ sơ ACTIVE), BANNED=(users ACTIVE, hồ sơ BANNED), DISABLED=(users DISABLED, hồ sơ BANNED). Ban chọn BANNED/DISABLED theo nghĩa vụ; J7 chỉ BANNED ⇄ DISABLED trong đợt cấm; unban theo cửa sổ 3.1.1 đưa về ACTIVE. Không hai trục cấm độc lập; mọi chuyển tăng authz\_version, audit/Outbox cùng transaction. |
| E. Product | SELLING ⇄ HIDDEN (Seller tự ẩn); REMOVED do Admin gỡ; chỉ Admin khôi phục về HIDDEN trong 30 ngày kể từ removed\_at. |
| F. Payment | PENDING → SUCCESS / FAILED. Trạng thái hoàn của Payment (chưa hoàn / hoàn một phần / hoàn toàn bộ) là giá trị tính từ các Refund SUCCESS, không lưu thành trạng thái riêng. |
| G. Refund | PENDING → SUCCESS / FAILED; FAILED retry an toàn trong chu kỳ P-RF-01 (1 lần đầu +3 retry chờ 1/5/15 phút). Hết lượt customer_retry_required=true; Customer chủ Payment mở chu kỳ mới, retry_cycle+1/retry_count=0/cùng bản ghi-key →PENDING. Timeout chỉ query, vẫn áp SUCCESS muộn; không Admin. |
| H. Reservation | HELD → COMMITTED (thanh toán thành công) / RELEASED (phiên đóng). |
| I. Settlement | PENDING → SETTLED (Order CONFIRMED); PENDING → PENDING\_REFUND (Order bị hủy sau thanh toán) → REFUNDED (Refund SUCCESS). |
| J. Withdrawal | Không có state machine: hoặc thành công ngay (tạo Withdrawal và bút toán trong cùng transaction), hoặc bị từ chối và không để lại bản ghi. |

### 8.3 Dòng tiền giả lập, hoa hồng và quy tắc ví (WD-01 đến WD-11)

Với Order i: Gᵢ \= Order.total\_amount\_vnd; rᵢ \= commission\_bps\_snapshot, P-COM-01 chốt chung 500 bps (5%) lúc checkout cho mọi Seller/ngành hàng. Finance giữ snapshot, không tra Category/tính lại đơn cũ. Cᵢ \= floor((Gᵢ × rᵢ \+ 5.000) / 10.000), làm tròn nửa lên một lần trên tổng đơn; Sᵢ \= Gᵢ − Cᵢ, T \= ΣGᵢ. Ví dụ G=10.010 →C=501, S=9.509 VND.

| SỰ KIỆN | SETTLEMENT | VÍ SELLER | VÍ NỀN TẢNG |
| :---- | :---- | :---- | :---- |
| Payment SUCCESS | Tạo PENDING | pending \+Sᵢ (PAYMENT\_RECEIVED) | pending \+Cᵢ |
| Order CONFIRMED | → SETTLED, chốt Cᵢ, Sᵢ | pending −Sᵢ, available \+Sᵢ (SETTLEMENT\_RELEASED) | pending −Cᵢ, available (hoa hồng đã chốt) \+Cᵢ |
| Order CANCELLED sau thanh toán | → PENDING\_REFUND; tạo Refund Gᵢ | Chưa đổi số dư; phần Sᵢ được khóa, không thể chuyển sang khả dụng | Chưa đổi số dư |
| Refund SUCCESS của Order đó | → REFUNDED | pending −Sᵢ (REFUND\_DEBIT) | pending −Cᵢ |
| Seller rút A | — | available −A, total\_withdrawn \+A (WITHDRAWAL\_COMPLETED) | — |
| Thanh toán muộn khi phiên đã CLOSED | Không tạo Settlement | Không đổi | Không đổi; Refund FULL\_CHECKOUT \= Payment.amount\_vnd |

Hệ thống không tự động chi tiền khi đơn được xác nhận; Seller chủ động rút (UC-D05). Trong lúc đơn bị hủy đang chờ hoàn, phần tiền của đơn vẫn nằm trong số dư chờ của ví nhưng được đánh dấu chờ hoàn (Settlement PENDING\_REFUND); giao diện ví Seller nên tách riêng phần này để Seller không hiểu nhầm.

**Bảo toàn tiền tại cùng một thời điểm:** tổng Payment SUCCESS \= Σ Refund SUCCESS \+ Σ Refund FULL\_CHECKOUT chưa thành công \+ Σ pending (mọi ví Seller và ví nền tảng, gồm cả phần của đơn đang chờ hoàn) \+ hoa hồng đã chốt \+ Σ available Seller \+ Σ total\_withdrawn. Mỗi đồng tiền chỉ nằm ở một ngăn; không trộn số dư tại thời điểm với số phát sinh theo kỳ.

| MÃ | QUY TẮC VÍ VÀ RÚT TIỀN |
| :---- | :---- |
| WD-01 | Cần account\_type SELLER, role SELLER hiện hành, Seller APPROVED, ví owner\_id=seller\_id=sub. accountStatus ACTIVE hoặc BANNED có allowlist rút hợp lệ đều được rút available; DISABLED/PENDING/REJECTED/sai quyền không rút. Nghĩa vụ đến muộn phải qua đánh giá G-14 để mở BANNED giới hạn, không tự unban. |
| WD-02 | Chỉ rút số dư khả dụng; số dư chờ không được rút, không ứng trước, không cộng vào hạn mức rút. Tiền chỉ vào khả dụng khi Order CONFIRMED. |
| WD-03 | A là số nguyên VND dương, A ≤ available; hợp lệ nếu `A >= 10000` **hoặc** `(accountStatus == BANNED && 0 < availableBalanceVnd < 10000 && A == availableBalanceVnd)`. Giữ P-WD-01/minWithdrawalVnd=10000 và ngưỡng Seller ACTIVE; không rút một phần dưới ngưỡng (422 WITHDRAWAL_BELOW_MINIMUM), không rút pending, không giới hạn số lần rút/ngày. Kiểm tra lại quyền và số dư trong luồng giao dịch hiện hành. |
| WD-04 | Tức thời và nguyên tử: trong một transaction, cập nhật có điều kiện available ≥ A; available −A, total\_withdrawn \+A, tạo Withdrawal và WalletTransaction. Lỗi giữa chừng thì rollback toàn bộ; các lần rút đồng thời được tuần tự hóa. |
| WD-05 | Chống lặp: cùng idempotency\_key chỉ trừ ví một lần (G-09). |
| WD-06 | Hoàn tiền không chạm số dư khả dụng hay số đã rút: Refund luôn lấy từ phần chờ của đúng Order bị hủy. Mọi số dư ví ≥ 0; không phát sinh công nợ Seller với nền tảng trong MVP. |
| WD-07 | MVP không cho hủy/hoàn Order đã DELIVERED hoặc CONFIRMED. Nếu sau MVP bổ sung đổi trả sau nhận hàng thì phải thêm thời gian tạm giữ sau xác nhận và cơ chế xử lý số dư âm. |
| WD-08 | Admin không duyệt, không hủy, không đảo giao dịch rút; Admin chỉ xem ví và lịch sử rút (UC-E08). |
| WD-09 | Seller BANNED còn nghĩa vụ theo G-14 được phiên giới hạn: đơn tồn theo state, pending-only/hiệu ứng chưa apply chỉ xem (OBLIGATIONS_READ_ONLY), available cho xem ví/rút theo WD-03; available-only chỉ ví/rút/biên nhận liên quan. Không bán mới, không rút pending, không đóng băng available do ban. Mọi nguồn xác minh hết nghĩa vụ → DISABLED qua Identity/J7, không reset deadline; nghĩa vụ đến muộn mở BANNED giới hạn giữ mốc, không tự gỡ cấm. |
| WD-10 | Ví nền tảng: hoa hồng đã chốt chỉ ghi sổ để báo cáo; MVP không có chức năng rút tiền của nền tảng. |
| WD-11 | Với mỗi Seller: Σ seller\_amount của Settlement SETTLED \= available \+ total\_withdrawn; Σ seller\_amount của Settlement PENDING và PENDING\_REFUND \= pending; total\_withdrawn \= Σ Withdrawal.amount\_vnd. |

### 8.4 Bộ giả lập thanh toán nội bộ và lộ trình hai giai đoạn

MVP không dùng nhà cung cấp thanh toán bên ngoài. Payment Service gọi thanh toán và hoàn tiền qua interface PaymentGateway (charge, refund, query); cài đặt trong MVP là **SimulatedGateway** — bộ giả lập nội bộ trả về SUCCESS, FAILED hoặc PENDING theo kịch bản cấu hình. Khi cần cổng thật sau MVP, chỉ thêm một cài đặt mới của cùng interface; dữ liệu, trạng thái và API giữ nguyên.

| KỊCH BẢN | KẾT QUẢ BỘ GIẢ LẬP TRẢ VỀ | GIAI ĐOẠN |
| :---- | :---- | :---- |
| SUCCESS | Thanh toán / hoàn tiền thành công ngay | 1 (mặc định) |
| FAIL | Thanh toán bị từ chối rõ ràng | 2 |
| TIMEOUT | PENDING, không bao giờ tự có kết quả (kiểm thử J2/J2b) | 2 |
| LATE\_SUCCESS | PENDING, sau một khoảng cấu hình tự chuyển SUCCESS (kiểm thử thanh toán muộn, Refund FULL\_CHECKOUT) | 2 |
| REFUND\_FAIL | Refund FAILED; kiểm thử J5/P-RF-01 hết lượt rồi Customer chủ Payment mở chu kỳ mới, Admin chỉ đọc | 2 |

Giai đoạn 1 chỉ cần SUCCESS nhưng vẫn qua interface/trạng thái/event đầy đủ; giai đoạn 2 thêm nhánh không đổi luồng chính. Kịch bản chỉ chọn bằng cấu hình hoặc field dev/staging. Kết quả muộn lưu SimulatedTransaction (mục 6), đến bằng query J2/J5 hoặc hàm xử lý kết quả nội bộ Payment; Admin không query/retry gateway.

