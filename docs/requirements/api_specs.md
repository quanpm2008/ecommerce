# Đặc tả API và phân quyền RBAC cho hệ thống Ecommerce

Luồng thanh toán hiện hành áp dụng [quyết định provider demo và SQS](../decisions/0001-provider-checkout.md), 20/11/2026.

Tài khoản Customer, Seller, Administrator độc lập; RBAC một vai trò/User

| Thông tin | Giá trị |
| :---- | :---- |
| Phiên bản | v1.1 — đồng bộ Database v5.1 và FRD v5.5, ngày 16/11/2026 |
| Ngày | 16/11/2026 |
| Tài liệu nguồn | FRD v5.5, FRD Conflicts (Quyết định mới), Database v5.1 và API v1.0. Quyết định \#15: dùng UUID orderId/checkoutSessionId, không mã hiển thị riêng hay mã vận đơn; giữ withdrawalCode. |
| Phạm vi | 7 service: Identity & Seller, Product, Cart, Order, Payment, Finance, Notification · API Gateway · 3 web app |
| Trạng thái | Bản API v1.1 cho FE/BE review và xuất OpenAPI: tài khoản tách vai trò, SellerApplication mới khi bị từ chối, đăng nhập giới hạn khi bị cấm, avatar dùng chung logo Seller, quyền rút Seller bị cấm. Chưa xác nhận backend đã triển khai. |

## Mục lục

- [1. Giới thiệu](#1-giới-thiệu)
  - [1.1 Mục đích](#11-mục-đích)
  - [1.2 Tài liệu nguồn](#12-tài-liệu-nguồn)
  - [1.3 Cách đọc](#13-cách-đọc)
- [2. Kiến trúc API](#2-kiến-trúc-api)
  - [2.1 Thành phần](#21-thành-phần)
  - [2.2 Định tuyến tại Gateway](#22-định-tuyến-tại-gateway)
  - [2.3 Truyền danh tính từ Gateway xuống service](#23-truyền-danh-tính-từ-gateway-xuống-service)
- [3. Quy ước chung](#3-quy-ước-chung)
  - [3.1 Base URL và versioning](#31-base-url-và-versioning)
  - [3.2 Xác thực](#32-xác-thực)
  - [3.3 Format response chuẩn (envelope)](#33-format-response-chuẩn-envelope)
  - [3.4 HTTP status code](#34-http-status-code)
  - [3.5 Phân trang, lọc, sắp xếp](#35-phân-trang-lọc-sắp-xếp)
  - [3.6 Quy ước dữ liệu](#36-quy-ước-dữ-liệu)
  - [3.7 Idempotency (chống gửi lặp)](#37-idempotency-chống-gửi-lặp)
  - [3.8 Cập nhật đồng thời (optimistic locking)](#38-cập-nhật-đồng-thời-optimistic-locking)
  - [3.9 Upload ảnh/video (presigned URL)](#39-upload-ảnhvideo-presigned-url)
  - [3.10 Header chuẩn](#310-header-chuẩn)
- [4. Danh mục mã lỗi và enum dùng chung](#4-danh-mục-mã-lỗi-và-enum-dùng-chung)
  - [4.1 Bảng mã lỗi](#41-bảng-mã-lỗi)
  - [4.2 Enum](#42-enum)
- [5. Ma trận Use case → Service](#5-ma-trận-use-case--service)
  - [5.1 Ownership dữ liệu (theo Database RBAC v5.0)](#51-ownership-dữ-liệu-theo-database-rbac-v50)
- [6. Đặc tả theo service](#6-đặc-tả-theo-service)
  - [6.1 Identity & Seller Service](#61-identity--seller-service)
  - [6.2 Product Service](#62-product-service)
  - [6.3 Cart Service](#63-cart-service)
  - [6.4 Order Service (Order Inventory Fulfillment)](#64-order-service-order-inventory-fulfillment)
  - [6.5 Payment Service](#65-payment-service)
  - [6.6 Finance Service](#66-finance-service)
  - [6.7 Notification Service](#67-notification-service)
- [7. API nội bộ và sự kiện giữa các service](#7-api-nội-bộ-và-sự-kiện-giữa-các-service)
  - [7.1 Internal API (đồng bộ)](#71-internal-api-đồng-bộ)
  - [7.2 Sự kiện (bất đồng bộ, qua Outbox)](#72-sự-kiện-bất-đồng-bộ-qua-outbox)
  - [7.3 Job nền](#73-job-nền)
- [8. Luồng tích hợp tiêu biểu](#8-luồng-tích-hợp-tiêu-biểu)
  - [8.1 Checkout nhiều cửa hàng và thanh toán](#81-checkout-nhiều-cửa-hàng-và-thanh-toán)
  - [8.2 Seller từ chối giao hàng](#82-seller-từ-chối-giao-hàng)
  - [8.3 Xác nhận nhận hàng và rút tiền](#83-xác-nhận-nhận-hàng-và-rút-tiền)
- [9. Mâu thuẫn tài liệu và điểm cần chốt](#9-mâu-thuẫn-tài-liệu-và-điểm-cần-chốt)
  - [9.1 Tham số ảnh hưởng tới API](#91-tham-số-ảnh-hưởng-tới-api)
  - [9.2 Giới hạn của tài liệu này](#92-giới-hạn-của-tài-liệu-này)
- [10. Quy trình sử dụng và nhật ký thay đổi](#10-quy-trình-sử-dụng-và-nhật-ký-thay-đổi)
  - [10.1 Quy trình đề xuất](#101-quy-trình-đề-xuất)
  - [10.2 Nhật ký thay đổi](#102-nhật-ký-thay-đổi)
  - [10.3 Đối chiếu comment của Bùi Minh Quang](#103-đối-chiếu-comment-của-bùi-minh-quang)
  - [10.4 Tác động lên schema và chuyển đổi API](#104-tác-động-lên-schema-và-chuyển-đổi-api)
  - [10.5 Kiểm thử chấp nhận RBAC và comment](#105-kiểm-thử-chấp-nhận-rbac-và-comment)


## 1. Giới thiệu

### 1.1 Mục đích

Tài liệu đặc tả hợp đồng giao tiếp (API contract) giữa Frontend (3 web app: Customer, Seller, Admin) và Backend (7 service), cùng các API nội bộ và sự kiện giữa các service. Mục tiêu: FE và BE code song song, FE có thể dựng mock server từ tài liệu này mà không cần chờ BE.

### 1.2 Tài liệu nguồn

| Tài liệu | Phiên bản | Dùng cho |
| :---- | :---- | :---- |
| FRD | FRD v5.5 — 16/11/2026 | Nguồn nghiệp vụ ưu tiên: Quyết định mới FRD Conflicts và FRD v5.5; dữ liệu/luồng khác giữ nguyên nếu không xung đột. |
| Database document | Database v5.1 — 16/11/2026 | User là schema credential chung nhưng mỗi tài khoản có accountType riêng, một role hiện hành và chỉ một hồ sơ Customer hoặc Seller. SellerApplication lưu từng lần nộp; users.avatar\_object\_key dùng làm avatar và logo Seller. |
| BRD | v3.0 — 19/10/2026 | Phạm vi MVP, Definition of Done. |
| Parameter Register | v3.0 — 19/10/2026 | Tham số P-xx, S-xx, P-SIM-01, job J1–J6, NFR N-xx. |
| UI Screens \+ Figma wireframe | v2.0 — 19/10/2026 | Danh sách màn hình, dữ liệu mỗi màn hình cần. |

API v1.1 đồng bộ FRD v5.5 và Database v5.1. Auth và RBAC của v1.0 theo User đa vai trò đã thay bằng tài khoản riêng; quyết định \#15 giữ nguyên UUID. Thay đổi breaking cần cập nhật OpenAPI, FE và BE đồng thời.

### 1.3 Cách đọc

* **Mục 2–4** là quy ước chung (Khối A): mọi service phải tuân theo. Chốt trước khi code.

* **Mục 5** là ma trận Use case → Service: dùng để phân công người phụ trách.

* **Mục 6** đặc tả từng service: tổng quan, bảng endpoint, DTO, chi tiết từng endpoint.

* **Mục 7–8** là API nội bộ, sự kiện và job nền giữa các service.

* PUBLIC không cần login; ANY\_AUTH là User ACTIVE với session hợp lệ; CUSTOMER/SELLER/ADMIN là accountType và vai trò độc lập, KHÔNG có User đa vai trò. INTERNAL yêu cầu service identity/allowlist. Mỗi API kiểm tra loại tài khoản, trạng thái, phạm vi phiên giới hạn, permission và ownership.

* Mọi path trong tài liệu được viết **tương đối với base URL** /api/v1 (public) hoặc /internal/v1 (nội bộ).

## 2. Kiến trúc API

### 2.1 Thành phần

| Thành phần | Vai trò |
| :---- | :---- |
| 3 Frontend | Customer web (gồm khách vãng lai), Seller web, Admin web — 3 tên miền riêng theo tài liệu UI Screens. |
| API Gateway | Định tuyến, kiểm tra JWT, loại bỏ header danh tính do client gửi, rate limit và CORS; service xác minh lại token và kiểm tra RBAC hiện hành. |
| 7 service | Identity & Seller · Product · Cart · Order (Order Inventory Fulfillment) · Payment · Finance · Notification. Mỗi service sở hữu database riêng (PostgreSQL), không join chéo DB. |
| Message broker \+ Outbox | Sự kiện bất đồng bộ giữa service (Mục 7.2). Mỗi service phát event qua bảng OutboxEvent, consumer chống lặp qua InboxEvent. |
| Object storage | Lưu ảnh/video; FE upload trực tiếp bằng presigned URL (N-08). |
| Provider demo | Ứng dụng HTTPS độc lập do nhóm xây. Tạo link, nhận xác nhận giả lập, xử lý hoàn và gửi webhook có chữ ký cho Payment (§6.5.1). |

### 2.2 Định tuyến tại Gateway

Route chính xác `POST /api/v1/payments/webhooks/demo-provider` đi thẳng tới Payment để xác thực chữ ký trên body gốc. Route dùng định danh provider thay cho JWT người dùng. Các route nội bộ tạo giao dịch và hoàn tiền giữ allowlist riêng.

| Prefix public (sau /api/v1) | Service | Prefix nội bộ (sau /internal/v1) |
| :---- | :---- | :---- |
| /identity/\*\* | Identity & Seller | /identity/\*\* |
| /categories/\*\*, /products/\*\*, /seller/products/\*\*, /admin/products/\*\*, /admin/categories/\*\*, /media/upload-urls | Product | /products/\*\* |
| /cart/\*\* | Cart | — |
| /orders/\*\* | Order (Order Inventory Fulfillment) | /orders/**, /inventory/** |
| /payments/\*\* | Payment | /payments/\*\* |
| /finance/\*\* | Finance | /finance/\*\* |
| /notifications/\*\* | Notification | /notifications/\*\* |

Đường dẫn public của Product Service (tên cũ: Catalog) không dùng prefix service: /categories, /products, /seller/products, /admin/products và /admin/categories. Endpoint media /media/upload-urls thuộc Product; /identity/media/upload-urls thuộc Identity. Internal Product dùng /internal/v1/products/\*\* (thay cho /internal/v1/catalog/\*\*). Prefix /admin hoặc /seller không thay thế kiểm tra permission và ownership.

### 2.3 Truyền danh tính từ Gateway xuống service

* Gateway và service xác minh JWT bằng thuật toán cho phép, chữ ký, iss, aud, exp và sessionId. sub \= User.user\_id; không dùng X-Actor-Type để chọn bảng tài khoản. Gateway xóa mọi X-User-Id/X-Actor-Id/X-Actor-Type/X-Permissions từ client, chuyển tiếp Bearer và X-Request-Id qua kênh nội bộ tin cậy.

* Mỗi service lấy sub từ JWT đã xác minh, gọi Identity authorization context để lấy trạng thái User, authzVersion, permissions và hồ sơ. Với API own, đối chiếu customerId/sellerId/recipientUserId của tài nguyên với sub; không tin owner ID trong body, query hay header client.

* MVP không cache quyền bảo vệ: Identity không sẵn sàng → 503 SERVICE\_UNAVAILABLE, không cho qua. Token authzVersion cũ → 401 TOKEN\_STALE để client refresh. Mọi API vẫn kiểm tra trạng thái nghiệp vụ và ownership; trước checkout/thu tiền/rút tiền kiểm tra quyền hiện hành, sau đó cập nhật có điều kiện trong transaction dữ liệu sở hữu.

* Gateway chặn mọi request từ bên ngoài vào /internal/\*\*.

## 3. Quy ước chung

### 3.1 Base URL và versioning

| Môi trường | Base URL (đề xuất) |
| :---- | :---- |
| Local | `http://localhost:8080/api/v1` |
| Dev / Staging | `https://api-dev.<domain>/api/v1` |
| Production (demo chấm đồ án) | `https://api.<domain>/api/v1` |
| Mock server cho FE | Sinh từ OpenAPI (Prism) — `http://localhost:4010/api/v1` |

Thay đổi không tương thích ngược (đổi tên field, đổi kiểu, bỏ endpoint) phải tăng version hoặc được cả FE và BE đồng ý trước, ghi vào changelog ở Mục 10\.

### 3.2 Xác thực

* Cơ chế: JWT Bearer. Header: Authorization: Bearer \<accessToken\>.

* Access token sống ngắn, refresh token sống dài (đề xuất 15 phút / 7 ngày theo **P-06 — chưa chốt**).

* Vẫn dùng POST /identity/auth/login trên hạ tầng Identity, nhưng FE phải gửi accountType CUSTOMER/SELLER/ADMIN đúng kênh web app. Mỗi vai trò có user\_id/email/session riêng; sai loại tài khoản dù đúng mật khẩu trả INVALID\_CREDENTIALS chung, không gợi ý đăng ký Seller; không đăng nhập dùng chung đa vai trò.

* Refresh: POST /identity/auth/refresh. Refresh token được xoay vòng (rotation): mỗi lần refresh trả token mới và vô hiệu token cũ.

* Refresh token thuộc đúng một tài khoản/accountType; login/refresh áp dụng cùng đánh giá nghĩa vụ G-14 bên dưới, kể cả DISABLED phải kiểm tra nghĩa vụ đến muộn trước khi từ chối. Seller PENDING/REJECTED không login; không hợp quyền giữa các tài khoản.

* JWT gồm sub, iss, aud, iat, exp, jti, sessionId, authzVersion, accountType và snapshot tối đa một role. Backend phải kiểm tra Identity hiện hành và allowlist phiên giới hạn, không tin claims để tự nâng quyền.

#### Quy tắc tài khoản bị cấm (G-14, N-05)

| Trường hợp | Đăng nhập | Hành vi API |
| :---- | :---- | :---- |
| Customer BANNED có nghĩa vụ đơn | Có, OPEN_ORDERS_ONLY | Chỉ xem đơn/phiên và Payment/Refund gắn nghĩa vụ đúng chủ; hủy/xác nhận theo state machine. AWAITING_PAYMENT không được thanh toán; không giỏ, checkout, review hoặc toàn bộ lịch sử terminal. |
| Customer BANNED chỉ còn Payment/Refund cần xử lý | OBLIGATIONS_READ_ONLY nếu chỉ chờ hệ thống; OPEN_ORDERS_ONLY nếu có Refund cần Customer retry | Xem đúng nghĩa vụ; Refund FAILED hết lượt cho refund:retry:own trên đúng chủ Payment, kể cả FULL_CHECKOUT/phiên đóng; không mở lịch sử/mua sắm. |
| Seller APPROVED/BANNED | Có khi còn nghĩa vụ | Có đơn và available: ORDERS_AND_WITHDRAWAL; chỉ đơn: OPEN_ORDERS_ONLY; chỉ available: WITHDRAWAL_ONLY; chỉ pending/hiệu ứng hệ thống: OBLIGATIONS_READ_ONLY. Chỉ xử lý đơn tồn/thông tin tiền, không sản phẩm, tồn kho, đơn mới hoặc thống kê chung; không rút pending. |
| Seller PENDING / REJECTED | Không | Không login dù chưa bị cấm; sau REJECTED đăng ký SellerApplication mới qua xác minh email PUBLIC. |
| DISABLED, đã xác minh không còn nghĩa vụ | Không, LOGIN_BLOCKED | Thu hồi phiên, ACCOUNT_DISABLED. Nghĩa vụ đến muộn mở lại BANNED giới hạn, không tự unban; lỗi nguồn trả 503, không đoán không có nghĩa vụ. |
| Một người sử dụng nhiều vai trò | Dùng các tài khoản độc lập | Mỗi tài khoản User chỉ một accountType và một role hiện hành; nhiều vai trò cần nhiều user\_id/email/session, không hợp quyền. |

Authority cho các clause Thẻ 15: [coverage §the-15](coverage.md#the-15), thay các quy tắc cũ tương ứng; đây là đặc tả, chưa xác minh implementation.

`accountStatus` suy ra, không thêm cột: ACTIVE = users.status ACTIVE + profile ACTIVE; BANNED = ACTIVE + BANNED; DISABLED = DISABLED + BANNED. Chỉ Identity chuyển cặp trạng thái trong cùng transaction với authzVersion, audit và Outbox; approvalStatus độc lập.

Nghĩa vụ hiện tại gồm Order AWAITING_PAYMENT/PAID/PACKING/DELIVERED; Payment PENDING, SUCCESS chưa apply vào Order hoặc hoàn muộn chưa kết thúc; Refund PENDING/FAILED chưa giải quyết kể cả Order/CheckoutSession đã đóng. CONFIRMED/CANCELLED/PAYMENT_FAILED/EXPIRED không tự giữ quyền nhưng nghĩa vụ Payment/Refund/Finance vẫn tính riêng. Seller thêm Settlement PENDING/PENDING_REFUND, hiệu ứng chưa apply (kể cả số tiền 0), pendingBalanceVnd > 0 hoặc availableBalanceVnd > 0. Không dùng lịch sử đã thanh toán.

Mọi request người dùng tính lại allowlist và ownership/state hiện hành; accessMode không cấp quyền trên Order người khác. Public browsing chỉ là công khai, không mở quyền mua sắm authenticated. J1, webhook và Refund tiếp tục xử lý.

#### 3.2.1 Mô hình RBAC và hợp đồng kiểm tra quyền

users chứa credential theo từng tài khoản độc lập, accountType CUSTOMER/SELLER/ADMIN. Customer và Seller dùng shared PK/FK nhưng không thể cùng user\_id; ADMIN không có hồ sơ Customer/Seller. UserRole có tối đa một role hiệu lực; Seller PENDING/REJECTED chưa có role SELLER và không login; Seller BANNED chỉ truy cập đơn tồn/rút available khi đủ điều kiện.

Policy nền theo accountType, không cấp seller:application:own cho mọi User ACTIVE. Seller mới/REJECTED nộp hồ sơ qua public registration/email verification, không tạo Seller profile trên tài khoản Customer/Admin. Các endpoint bảo vệ kiểm tra status, approval, nghĩa vụ còn dở, allowlist và ownership.

| Vai trò | Permission được seed | Điều kiện |
| :---- | :---- | :---- |
| CUSTOMER | customer:profile:own; cart:manage:own; checkout:create:own; payment:create:own; order:read:own; order:cancel:own; order:confirm:own; refund:retry:own; review:write:own; notification:read:own | ACTIVE đầy đủ; BANNED chỉ allowlist nghĩa vụ §3.2, gồm đọc/retry Refund của chính chủ khi đủ điều kiện, không cấp mọi permission seed. |
| SELLER | seller:profile:own; product:manage:own; inventory:manage:own; order:fulfill:own; wallet:read:own; withdrawal:create:own; notification:read:own | APPROVED/ACTIVE đầy đủ; BANNED theo accessMode §3.2: chỉ đơn/ví/biên nhận gắn nghĩa vụ, pending-only chỉ đọc; PENDING/REJECTED không login. |
| ADMIN | seller:approve; account:moderate; product:moderate; category:manage; operations:read:any; operations:annotate; notification:read:own | Admin chỉ đọc Refund, không query/retry gateway, không sửa số dư/xác nhận tay đã hoàn. operations:annotate chỉ ghi chú đối soát khoản thu; audit mọi thao tác. |

Với các API đọc checkout/payment của chính Customer, dùng order:read:own; API hủy checkout dùng order:cancel:own. Với Notification, notification:read:own bao gồm đọc/đánh dấu/xóa phần hiển thị của chính mình. API media kiểm tra purpose và quyền tương ứng. Không mở CRUD Role/Permission hoặc endpoint tự gán ADMIN trong MVP.

Mỗi đổi UserRole/trạng thái truy cập tăng authzVersion trong transaction Identity và ghi audit/outbox. Thay RolePermission bằng migration phải tăng version mọi User liên quan. Phiên đã thu hồi và token version cũ bị chặn; refresh luôn tải lại quyền, không phát lại quyền từ token cũ.

G-14 chỉ tác động tài khoản đã chọn, không các tài khoản khác của cùng người. Quyền hiện hành theo §3.2; không hứa transaction nguyên tử xuyên service. Job Payment/Refund tiếp tục bằng danh tính service.

#### 3.2.2 Ví dụ đăng nhập đơn vai trò

POST /api/v1/identity/auth/login với `{email,password,accountType:"CUSTOMER"}`.

200 data AuthTokens, profile có accountType CUSTOMER, roles ["CUSTOMER"], customerProfile và sellerProfile=null; accountStatus/mốc/accessMode theo DTO hiện hành, không hợp quyền.

### 3.3 Format response chuẩn (envelope)

Mọi response JSON dùng một trong hai dạng sau. FE chỉ cần một hàm xử lý chung.

**Thành công — một object:**

```json
{
  "data": { "productId": "8b1f...", "productName": "Áo thun basic", "priceVnd": 150000 }
}
```

**Thành công — danh sách có phân trang:**

```text
{
  "data": [ { ... }, { ... } ],
  "meta": { "page": 1, "size": 20, "totalItems": 135, "totalPages": 7 }
}
```

**Lỗi:**

```json
{
  "error": {
    "code": "INSUFFICIENT_STOCK",
    "message": "Một số sản phẩm không đủ hàng",
    "details": [
      { "field": "items[1].quantity", "productId": "a71c...", "issue": "AVAILABLE_LESS_THAN_REQUESTED", "availableQuantity": 2 }
    ],
    "requestId": "c0a8-..."
  }
}
```

* code là mã ổn định, FE dùng để quyết định hiển thị. message là tiếng Việt, chỉ để debug hoặc hiển thị mặc định.

* details dùng cho lỗi theo field (validation) hoặc theo dòng hàng (checkout, giỏ hàng).

* requestId trùng header X-Request-Id, dùng tra log khi báo lỗi.

* 204 No Content không có body.

### 3.4 HTTP status code

| Status | Khi dùng |
| :---- | :---- |
| 200 OK | Đọc dữ liệu; cập nhật thành công; gửi lại request idempotent đã xử lý. |
| 201 Created | Tạo mới tài nguyên (đăng ký, checkout, thanh toán, rút tiền, review...). |
| 202 Accepted | Đã nhận yêu cầu, kết quả chưa rõ (checkout đang chờ link hoặc kết quả từ provider; quên mật khẩu). |
| 204 No Content | Xóa thành công; đăng xuất; đánh dấu đã đọc. |
| 400 Bad Request | Sai định dạng / thiếu field / vi phạm validation (VALIDATION\_ERROR). |
| 401 Unauthorized | Chưa đăng nhập, token sai hoặc hết hạn (UNAUTHENTICATED, TOKEN\_EXPIRED, INVALID\_CREDENTIALS). |
| 403 Forbidden | Đã xác thực nhưng không đủ quyền hoặc ngoài allowlist phiên giới hạn: 403 FORBIDDEN/ACCOUNT\_BANNED; không cấp thêm quyền khi BANNED. |
| 404 Not Found | Không tồn tại **hoặc không thuộc quyền xem** của actor (không tiết lộ dữ liệu của người khác). |
| 409 Conflict | Xung đột trạng thái/dữ liệu: trùng email, sai trạng thái, idempotency key dùng lại với payload khác, version cũ, hết hàng. |
| 422 Unprocessable Entity | Dữ liệu hợp lệ về định dạng nhưng vi phạm quy tắc nghiệp vụ (rút dưới mức tối thiểu, sản phẩm không bán được...). |
| 429 Too Many Requests | Vượt rate limit (N-04). |
| 500 / 503 | Lỗi hệ thống / service phụ thuộc không sẵn sàng. |

### 3.5 Phân trang, lọc, sắp xếp

| Tham số | Kiểu | Mặc định | Ghi chú |
| :---- | :---- | :---- | :---- |
| page | int ≥ 1 | 1 | Trang bắt đầu từ 1\. |
| size | int 1–100 | 20 | P-11 chốt mặc định 20 sản phẩm/trang. |
| sort | string | tùy endpoint | Dạng field,asc\|desc, ví dụ priceVnd,asc. Chỉ nhận field được liệt kê trong từng endpoint. |
| q | string ≤ 100 ký tự | — | Từ khóa tìm kiếm; trường được tìm theo từng endpoint. P-37 chỉ áp dụng cho tìm kiếm công khai theo tên sản phẩm hoặc tên cửa hàng: không phân biệt dấu tiếng Việt và hoa thường (chốt 19/11/2026; C-08 đóng trong đặc tả). Với hai trường hợp này, chuẩn hóa cả q và tên để so khớp: bỏ dấu tiếng Việt, đổi đ/Đ thành d, chuyển về chữ thường; giữ nguyên tên lưu và hiển thị. Ví dụ “dien thoai”, “Điện Thoại” và “ĐIỆN THOẠI” cho cùng kết quả. Các endpoint tìm email, số điện thoại hoặc mã đơn giữ quy tắc riêng, không áp dụng P-37. |
| from, to | date (YYYY-MM-DD) | — | GMT+7 (P-14); tuần bắt đầu thứ Hai, quý theo lịch; to bao gồm cả ngày đó. |

### 3.6 Quy ước dữ liệu

| Loại | Quy ước | Ví dụ |
| :---- | :---- | :---- |
| Tên field JSON | camelCase tiếng Anh. DB dùng snake\_case; service tự map. | totalAmountVnd, sellerActionDueAt |
| ID | UUID v4 dạng chuỗi. | "3f2b9c1e-..." |
| Mã hiển thị | Đơn và phiên thanh toán không có mã hiển thị riêng: FE hiển thị orderId / checkoutSessionId (UUID; có thể rút gọn trên giao diện, tra cứu dùng UUID đầy đủ). Không có mã vận đơn. Chỉ giao dịch rút có withdrawalCode. | withdrawalCode: "RT-20261018-K2P9" |
| Thời gian | ISO 8601, luôn UTC với hậu tố Z. FE tự đổi sang giờ Việt Nam. | "2026-10-18T08:30:00Z" |
| Ngày (bộ lọc) | YYYY-MM-DD, hiểu theo GMT+7. | 2026-10-01 |
| Tiền | Số nguyên VND, field có hậu tố Vnd. Không dùng số thực. Phí vận chuyển luôn 0\. | "priceVnd": 150000 |
| Tỷ lệ hoa hồng | Số nguyên basis points, hậu tố Bps; P-COM-01 chốt chung 500 bps = 5% mọi Seller/ngành hàng. Checkout snapshot trên từng Order; Finance không tra Category/tính lại đơn cũ. | "commissionBps": 500 |
| Enum | UPPER\_SNAKE\_CASE tiếng Anh; FE tự map sang nhãn tiếng Việt (Mục 4.2). | "status": "DELIVERED" |
| Giá trị rỗng | Field không có giá trị trả null, không bỏ field. Mảng rỗng trả \[\]. | "cancellation": null |
| Nhãn giả lập | Response có tiền/thanh toán/hoàn/ví trả thêm "isSimulated": true; FE hiển thị nhãn "Giả lập — không phát sinh tiền thật" (G-15). |  |

### 3.7 Idempotency (chống gửi lặp)

* Các endpoint đánh dấu **Idempotency-Key: bắt buộc** yêu cầu header Idempotency-Key: \<uuid do FE sinh\>.

* FE sinh key **một lần cho một ý định** (ví dụ khi mở popup xác nhận đặt hàng) và dùng lại đúng key đó khi bấm lặp hoặc retry do mất mạng.

* Gửi lặp cùng key \+ payload sau khi kiểm tra quyền hiện hành → 200 với cùng kết quả nghiệp vụ; nếu tác vụ vẫn đang chờ thì 202\. Không tái tạo hiệu ứng. Header Idempotent-Replayed: true cho lần trả lại; lần tạo đầu giữ 201/202 theo endpoint.

* Cùng key \+ payload khác → 409 IDEMPOTENCY\_KEY\_REUSED.

* Phạm vi IdempotencyRecord: UNIQUE(actor\_user\_id, operation\_context, endpoint, key); operationContext CUSTOMER/SELLER/SERVICE do server xác định. UQ theo entity như UNIQUE(customer\_id, idempotency\_key) hoặc UNIQUE(seller\_id, idempotency\_key) vẫn giữ. Kiểm tra quyền/ownership trước khi trả kết quả đã lưu.

* Idempotency-Key bắt buộc khi tạo checkout, thanh toán, rút tiền, tạo sản phẩm và mở chu kỳ hoàn mới; productId giữ ổn định qua tạo Inventory, Refund giữ cùng key gateway qua các chu kỳ. Mọi request kiểm tra quyền trước xử lý/replay; chuyển trạng thái đơn idempotent theo trạng thái đích, không cần header.

### 3.8 Cập nhật đồng thời (optimistic locking)

* Tài nguyên có thể bị sửa từ nhiều nơi (tồn kho UC-D03) trả field version. Client gửi lại version khi cập nhật.

* Version không khớp → 409 VERSION\_CONFLICT, kèm dữ liệu mới nhất trong error.details\[0\].current để FE yêu cầu người dùng tải lại.

* Chuyển trạng thái đơn không cần version: server dùng cập nhật có điều kiện; nếu trạng thái đã đổi (ví dụ J4 vừa tự hủy) → 409 INVALID\_STATE\_TRANSITION kèm currentStatus.

### 3.9 Upload ảnh/video (presigned URL)

Mục đích AVATAR áp dụng cho tài khoản User; ảnh của Seller đồng thời là logo cửa hàng, lưu duy nhất ở users.avatar\_object\_key. Ngoài ra hỗ trợ PRODUCT\_MEDIA và REVIEW\_MEDIA. FE upload qua presigned URL rồi cập nhật avatarObjectKey tại PATCH /identity/users/me.

* **Bước 1: FE gọi POST .../media/upload-urls với danh sách file (tên, MIME, dung lượng, mục đích).**

* **Bước 2:** Server kiểm tra loại và dung lượng theo P-08, trả uploadUrl (PUT, hết hạn sau 10 phút) và objectKey cho từng file.

* **Bước 3:** FE PUT file trực tiếp lên uploadUrl với header Content-Type đúng MIME đã khai.

* **Bước 4:** FE gửi objectKey trong request tạo/sửa sản phẩm, review hoặc hồ sơ. Server kiểm tra object tồn tại, đúng chủ sở hữu, đúng loại thực tế (magic bytes) trước khi lưu.

* File upload nhưng không được gắn vào entity trong 24 giờ bị dọn (N-08).

* Response trả media dưới dạng url đọc được (CDN hoặc URL ký có hạn), không trả objectKey cho người xem công khai.

| Mục đích (purpose) | Loại cho phép | Giới hạn P-08 |
| :---- | :---- | :---- |
| AVATAR | Ảnh JPG/PNG/WebP (đề xuất, chưa chốt định dạng) | ≤2 MB (đề xuất, chưa chốt) |
| PRODUCT\_MEDIA | Ảnh JPG/PNG/WebP; video MP4 H.264/AAC (đề xuất, chưa chốt định dạng) | Đã chốt ≤9 tệp/sản phẩm, ≤2 video (P-08.3). Ảnh ≤5 MB; video ≤50 MB/60 giây vẫn là đề xuất riêng |
| REVIEW\_MEDIA | Như trên, chưa chốt định dạng | Đã chốt ≤5 tệp/review, ≤1 video (P-08.3); Review vẫn Should |

### 3.10 Header chuẩn

| Header | Chiều | Ghi chú |
| :---- | :---- | :---- |
| Authorization | Request | Bearer \<accessToken\> |
| Content-Type | Request/Response | application/json; charset=utf-8 |
| Idempotency-Key | Request | Xem 3.7 |
| X-Request-Id | Request (tùy chọn) / Response | Gateway sinh nếu FE không gửi; có trong mọi log. |
| Accept-Language | Request | Mặc định vi. MVP chỉ hỗ trợ tiếng Việt. |

## 4. Danh mục mã lỗi và enum dùng chung

### 4.1 Bảng mã lỗi

Mỗi service chỉ trả mã trong bảng này. Thêm mã mới phải cập nhật bảng trước.

| Mã lỗi (code) | HTTP | Ý nghĩa / khi nào |
| :---- | :---- | :---- |
| VALIDATION\_ERROR | 400 | Thiếu field, sai định dạng, vi phạm độ dài/khoảng giá trị. details liệt kê từng field. |
| PASSWORD\_POLICY\_VIOLATION | 400 | Mật khẩu \< 8 ký tự hoặc thiếu chữ hoa / chữ thường / chữ số (G-07). |
| UNAUTHENTICATED | 401 | Không có token hoặc token không hợp lệ. |
| TOKEN\_EXPIRED | 401 | Access token hết hạn → FE gọi refresh. |
| INVALID\_CREDENTIALS | 401 | Sai email/mật khẩu hoặc accountType không đúng kênh: luôn báo lỗi đăng nhập chung, không tiết lộ loại tài khoản. |
| INVALID\_REFRESH\_TOKEN | 401 | Refresh token sai, hết hạn hoặc đã bị thu hồi → FE đưa về trang đăng nhập. |
| FORBIDDEN | 403 | Actor không có quyền với endpoint/tài nguyên. |
| ACCOUNT\_BANNED | 403 | Thao tác ngoài allowlist nghĩa vụ hiện hành của tài khoản BANNED. |
| SELLER\_PENDING\_APPROVAL | 403 | Seller PENDING bị từ chối login và toàn bộ API Seller bảo vệ. |
| SELLER\_REJECTED | 403 | Seller REJECTED không được login; lý do từ chối qua email. Nộp lại phải là SellerApplication mới sau xác minh email. |
| CUSTOMER\_PAYMENT\_BLOCKED | 403 | Customer BANNED không checkout/thanh toán mới, kể cả AWAITING_PAYMENT; chỉ quyền nghĩa vụ G-14. |
| WRONG\_AUTH\_METHOD | 409 | Email đã đăng ký bằng phương thức khác (Google ↔ mật khẩu). details\[0\].authMethod \= phương thức đúng. |
| GOOGLE\_AUTH\_FAILED | 401 | Google không xác minh được email hoặc người dùng từ chối. |
| NOT\_FOUND | 404 | Tài nguyên không tồn tại hoặc không thuộc quyền xem. details\[0\].resource \= tên tài nguyên. |
| EMAIL\_ALREADY\_USED | 409 | Email đã gắn với tài khoản khác trong toàn hệ thống (G-01). |
| PHONE\_ALREADY\_USED | 409 | Số điện thoại đã được dùng. |
| SELLER\_NAME\_ALREADY\_USED | 409 | Tên cửa hàng (fullName của User) trùng với User khác có Seller PENDING/APPROVED. |
| SELLER\_NAME\_LOCKED | 409 | User có Seller PENDING/APPROVED không đổi fullName vì đó là tên cửa hàng (G-02). |
| RESET\_TOKEN\_INVALID | 400 | Link reset đã dùng hoặc hết 30 phút từ lúc tạo (P-05). |
| PASSWORD\_INCORRECT | 400 | Mật khẩu hiện tại không đúng (đổi mật khẩu). |
| PASSWORD\_SAME\_AS\_OLD | 400 | Mật khẩu mới trùng mật khẩu cũ. |
| ADDRESS\_LIMIT\_EXCEEDED | 422 | Vượt 3 địa chỉ hiện có/Customer (P-09); xóa hẳn không còn tính giới hạn. |
| INVALID\_REGION | 422 | Mã phường/xã không thuộc tỉnh/thành đã chọn. |
| IDEMPOTENCY\_KEY\_REUSED | 409 | Cùng Idempotency-Key nhưng payload khác lần đầu. |
| IDEMPOTENCY\_KEY\_REQUIRED | 400 | Thiếu header Idempotency-Key ở endpoint bắt buộc. |
| VERSION\_CONFLICT | 409 | Dữ liệu đã bị thay đổi ở nơi khác (tồn kho). Tải lại rồi thử lại. |
| INVENTORY_PROVISION_CONFLICT | 409 | Inventory đã tồn tại cho `productId` nhưng `sellerId` khác chủ đã lưu. Giữ nguyên dữ liệu. |
| CHECKOUT_QUOTE_INVALID | 400 | Quote sai định dạng, chữ ký, mục đích, phiên bản hoặc Customer. Trả thông báo chung. |
| CHECKOUT_QUOTE_EXPIRED | 409 | Quote hợp lệ đã hết hạn và chưa có phiên để trả lại. Yêu cầu Preview và xác nhận lại. |
| CHECKOUT_QUOTE_CHANGED | 409 | Giá hoặc tập hàng đã thay đổi trước khi tạo phiên. Yêu cầu Preview và xác nhận lại. |
| CHECKOUT_INTENT_CONFLICT | 409 | Key mới dùng ý định cũ với nội dung khác, hoặc key và ý định trỏ tới hai phiên khác nhau. |
| INVALID\_STATE\_TRANSITION | 409 | Trạng thái hiện tại không cho phép thao tác. details\[0\].currentStatus. |
| ALREADY\_PROCESSED | 409 | Đối tượng đã được người khác xử lý (Admin khác đã duyệt hồ sơ...). |
| MEDIA\_INVALID | 422 | Sai loại/dung lượng/thời lượng file, object không tồn tại hoặc không thuộc người gửi. |
| MEDIA\_REQUIRED | 422 | Sản phẩm cần tối thiểu 1 ảnh hoặc video. |
| CATEGORY\_NOT\_AVAILABLE | 422 | Danh mục bị ẩn hoặc không tồn tại khi tạo/sửa sản phẩm. |
| CATEGORY\_NAME\_DUPLICATE | 409 | Tên danh mục trùng. |
| CATEGORY\_IN\_USE | 409 | Không xóa được danh mục đã có sản phẩm. |
| PRODUCT\_REMOVED\_BY\_ADMIN | 409 | Seller sửa/bật bán sản phẩm đã bị Admin gỡ. |
| RESTORE\_WINDOW\_EXPIRED | 409 | Quá 30 ngày kể từ khi gỡ, không khôi phục được. |
| PRODUCT\_NOT\_SELLABLE | 422 | Sản phẩm không còn bán được (ẩn, gỡ, Seller bị cấm/chưa duyệt) — G-06. |
| INSUFFICIENT\_STOCK | 409 | Tồn kho khả dụng không đủ. details liệt kê từng sản phẩm và availableQuantity. |
| QUANTITY\_LIMIT\_EXCEEDED | 422 | Vượt 99 đơn vị/dòng hoặc 50 sản phẩm khác nhau/giỏ; mua ngay vượt 99 (P-10). |
| STOCK\_BELOW\_RESERVED | 422 | Tồn kho mới thấp hơn lượng đang giữ. details\[0\].minAllowed. |
| PROFILE\_INCOMPLETE | 422 | Thiếu số điện thoại hoặc chưa có địa chỉ nhận hàng trước checkout. details chỉ rõ thiếu gì. |
| CHECKOUT\_PENDING\_EXISTS | 409 | Customer đã có một phiên chờ thanh toán. details\[0\].checkoutSessionId. |
| CHECKOUT\_ITEMS\_INVALID | 409 | Một hoặc nhiều dòng hàng không hợp lệ khi đặt hàng; không tạo đơn nào. details theo dòng. |
| CHECKOUT\_NOT\_PAYABLE | 409 | Phiên đã hết hạn, đã hủy, đã thanh toán hoặc thanh toán thất bại. |
| PAYMENT\_IN\_PROGRESS | 409 | Đang có phiên chờ thanh toán khác: dùng link của phiên hiện tại hoặc chờ phiên hết hạn. |
| ORDER\_NOT\_CANCELLABLE | 409 | Đơn đã đóng gói / đã giao / đã xác nhận / đã hủy. |
| SELLER\_DEADLINE\_PASSED | 409 | Đã quá hạn 7 ngày phản hồi; hệ thống đang/đã tự hủy (J4). |
| REVIEW\_NOT\_ALLOWED | 403 | Không phải người mua, đơn chưa xác nhận nhận hàng hoặc đã hủy. |
| REVIEW\_ALREADY\_EXISTS | 409 | Đã có review cho cặp Customer–Product; không cho tạo lại hay sửa qua API trong MVP. |
| WITHDRAWAL\_BELOW\_MINIMUM | 422 | Rút dưới 10000 trừ BANNED rút toàn bộ available dương dưới 10000; rút một phần dưới ngưỡng vẫn lỗi. |
| INSUFFICIENT\_AVAILABLE\_BALANCE | 409 | Số tiền rút lớn hơn số dư khả dụng tại thời điểm xử lý. |
| WALLET\_FROZEN | 403 | WALLET\_FROZEN không được suy ra chỉ từ Seller BANNED; Seller APPROVED/BANNED vẫn được rút available khi đủ quyền WD-09. Ví lệch bút toán trả WALLET\_INCONSISTENT. |
| WALLET\_INCONSISTENT | 409 | Phát hiện lệch giữa số dư và tổng bút toán; tạm chặn rút, chờ Admin. |
| REFUND\_RETRY\_NOT\_SAFE | 409 | Refund không FAILED/chưa chờ Customer, expectedRetryCycle cũ, hoặc chưa xác minh an toàn; không mở chu kỳ mới. |
| RATE\_LIMITED | 429 | Quá số lần gọi cho phép. |
| INTERNAL\_ERROR | 500 | Lỗi không xác định. |
| SERVICE\_UNAVAILABLE | 503 | Service phụ thuộc không phản hồi; có thể thử lại. |
| TOKEN\_STALE | 401 | authzVersion token không còn hiện hành; refresh lấy quyền mới. |
| ACCOUNT\_DISABLED | 403 | User DISABLED chặn mọi endpoint bảo vệ và login của tài khoản, thu hồi phiên. Các tài khoản vai trò khác không bị tác động tự động. |
| ACCOUNT_RESTORE_WINDOW_EXPIRED | 409 | Unban DISABLED khi now > restoreUntil; không xóa dữ liệu/tiền hoặc đổi đơn. Không phải lỗi khôi phục Product. |
| PROFILE\_REQUIRED | 403 | Có permission nhưng thiếu hồ sơ nghiệp vụ tương ứng. |
| SELF\_APPROVAL\_FORBIDDEN | 403 | Admin không được duyệt hồ sơ Seller của chính mình. |
| NOTIFICATION\_TYPE\_NOT\_ALLOWED | 403 | Service producer không được tạo loại thông báo hoặc kênh này. |
| REGION\_SOURCE\_UNAVAILABLE | 503 | Danh mục địa giới seed trong DB Identity chưa sẵn sàng/không đọc được để xác minh (P-26). |

### 4.2 Enum

| Enum | Giá trị (nhãn hiển thị) | Nguồn |
| :---- | :---- | :---- |
| RoleCode | CUSTOMER, SELLER, ADMIN; một User chỉ một role hiệu lực phù hợp accountType. | DB 3.1.5–3.1.9 |
| AuthMethod | LOCAL, GOOGLE | DB 3.1.1 |
| ProfileStatus | ACTIVE / BANNED; chỉ ở Customer hoặc Seller | DB 3.1.2–3.1.3 |
| SellerApprovalStatus | PENDING / APPROVED / REJECTED | DB 3.1.3 |
| CategoryStatus | VISIBLE (Hiển thị), HIDDEN (Ẩn) | DB 3.2.1 |
| ProductStatus | SELLING (Đang bán), HIDDEN (Tạm ẩn — Seller tự ẩn), REMOVED (Đã gỡ — Admin) | DB 3.2.2 |
| StockStatus | IN\_STOCK, OUT\_OF\_STOCK (hiển thị trên thẻ/chi tiết sản phẩm) | UC-B01 |
| MediaType | IMAGE, VIDEO | DB 3.2.2 |
| CartLineStatus | IN\_STOCK (Còn hàng), INSUFFICIENT\_STOCK (Chỉ còn X), OUT\_OF\_STOCK (Hết hàng). Dòng ngừng bán bị loại khỏi giỏ và trả trong removedItems. | DB 3.3.2 |
| CheckoutSource | CART (từ giỏ), BUY\_NOW (mua ngay) | DB 3.6 |
| CheckoutSessionStatus | AWAITING\_PAYMENT (Chờ thanh toán), PAID (Đã thanh toán), PAYMENT\_FAILED (Thanh toán thất bại), EXPIRED (Hết hạn), CANCELLED (Đã hủy) | DB 3.6.1, 8.2A |
| OrderStatus | AWAITING\_PAYMENT (Chờ thanh toán), PAYMENT\_FAILED (Thanh toán thất bại), EXPIRED (Hết hạn), PAID (Chờ xử lý), PACKING (Đang đóng gói), DELIVERED (Đã giao — chờ khách xác nhận), CONFIRMED (Đã xác nhận), CANCELLED (Đã hủy) | DB 3.6.2, 8.2B |
| CancelledBy | CUSTOMER, SELLER, SYSTEM, ADMIN | DB 3.6 |
| CustomerCancelReason | CHANGED\_MIND, ORDERED\_BY\_MISTAKE, FOUND\_BETTER\_PRICE, ADDRESS\_CHANGE, OTHER (P-21a đã chốt) | parameters.md |
| SellerRejectReason | DEFECTIVE, OUT\_OF\_STOCK\_ACTUAL, CANNOT\_DELIVER, OTHER (P-21b đã chốt) | parameters.md |
| PaymentStatus | PENDING (Đang xác nhận), SUCCESS (Thành công), FAILED (Thất bại) | DB 3.4.1 |
| PaymentRefundState | NONE, PARTIALLY\_REFUNDED, REFUNDED (trường tính toán, tương ứng DB 3.4) | DB 3.4 |
| RefundScope | ORDER, FULL\_CHECKOUT | DB 3.4.2 |
| RefundReason | CUSTOMER\_CANCEL, SELLER\_REJECT, SELLER\_CANCEL (bằng chứng Seller hoặc Product cho thấy điều kiện giao dịch không hợp lệ tại thời điểm thu theo §7.1), SELLER\_TIMEOUT (J4), LATE\_PAYMENT. | DB 3.4.2 |
| RefundStatus | PENDING (Đang hoàn), SUCCESS (Đã hoàn), FAILED (Hoàn lỗi) | DB 3.4 |
| SettlementStatus | PENDING (Chờ quyết toán), SETTLED (Đã quyết toán), PENDING\_REFUND (Chờ hoàn), REFUNDED (Đã hoàn) | DB 3.5.1 |
| WalletOwnerType | SELLER, PLATFORM | DB 3.5, 8.3 |
| WalletTransactionType | PAYMENT\_RECEIVED (ghi chờ khi thanh toán), SETTLEMENT\_RELEASED (chờ → khả dụng khi đơn xác nhận), REFUND\_DEBIT (trừ chờ khi hoàn tiền thành công), WITHDRAWAL\_COMPLETED (rút tiền) | DB 3.5.3 |
| NotificationChannel | EMAIL, IN\_APP | DB 3.7 |
| NotificationType | CHECKOUT\_PAID, NEW\_ORDER, ORDER\_DELIVERED, ORDER\_REJECTED, ORDER\_CANCELLED, ORDER\_AUTO\_CANCELLED, ORDER\_CONFIRMED, ORDER\_AUTO\_CONFIRMED, REFUND\_SUCCEEDED, REFUND\_RETRY\_REQUIRED, FUNDS\_AVAILABLE, WITHDRAWAL\_COMPLETED, SELLER\_REGISTERED, SELLER\_APPROVED, SELLER\_REJECTED, ACCOUNT\_BANNED, ACCOUNT\_UNBANNED, USER\_DISABLED, USER\_ENABLED, PRODUCT\_REMOVED, PAYMENT\_FAILED, CHECKOUT\_EXPIRED | UC-G01; producer và Notification dùng cùng enum |
| AuditAction | APPROVE\_SELLER, REJECT\_SELLER, BAN, UNBAN, DISABLE\_USER, ENABLE\_USER, GRANT\_ROLE, REVOKE\_ROLE, REMOVE\_PRODUCT, RESTORE\_PRODUCT, MARK\_HANDLED, MANAGE\_CATEGORY | DB mục 6; Admin không query/retry Refund; worker/Customer lưu lịch sử thực thi riêng |
| UserStatus | ACTIVE / DISABLED | DB 3.1.1 |
| AccountStatus / AccessMode | ACTIVE / BANNED / DISABLED (suy ra); FULL / OPEN_ORDERS_ONLY / ORDERS_AND_WITHDRAWAL / WITHDRAWAL_ONLY / OBLIGATIONS_READ_ONLY / LOGIN_BLOCKED | G-14, coverage §the-15 |
| AudienceContext | CUSTOMER / SELLER / ACCOUNT; ngữ cảnh thông báo, không cấp quyền | DB 3.7 |

#### Tab trạng thái đơn trên giao diện (gợi ý cho FE)

| Màn hình | Tab | Query status |
| :---- | :---- | :---- |
| Customer — Đơn mua | Tất cả / Chờ thanh toán / Đang xử lý / Chờ xác nhận / Hoàn tất / Đã hủy | — / AWAITING\_PAYMENT / PAID,PACKING / DELIVERED / CONFIRMED / CANCELLED,PAYMENT\_FAILED,EXPIRED |
| Seller — Quản lý đơn | Tất cả / Chờ xử lý / Đang đóng gói / Đã giao / Đã xác nhận / Đã hủy | — / PAID / PACKING / DELIVERED / CONFIRMED / CANCELLED |

## 5. Ma trận Use case → Service

Mỗi use case có **một service owner** (service sở hữu dữ liệu chính mà use case tạo ra hoặc thay đổi). Người phụ trách service owner viết endpoint public và mô tả luồng; service phụ thuộc cung cấp internal API hoặc tiêu thụ event. Cột "Người phụ trách" để nhóm điền.

| UC | Tên | Actor | Owner | Phụ thuộc | Phụ trách |
| :---- | :---- | :---- | :---- | :---- | :---- |
| E06 | Đăng ký Customer (email; Google — S) | Customer | Identity | — |  |
| A02 | Đăng ký Seller | Seller | Identity | Notification |  |
| A03 | Đăng nhập (3 actor; Google — S) | Tất cả | Identity | Order (kiểm tra đã có đơn thanh toán khi cấm) |  |
| A04 | Đổi mật khẩu | Tất cả (local) | Identity | — |  |
| A05 | Quên mật khẩu | Tất cả (local) | Identity | Notification (email) |  |
| A06 | Cập nhật hồ sơ, sổ địa chỉ | Tất cả | Identity | — |  |
| A07 | Đăng xuất | Tất cả | Identity | — |  |
| B01 | Tìm kiếm, xem sản phẩm | Public | Product | Identity (Seller hợp lệ), Order (tồn kho khả dụng) |  |
| B02 | Tìm kiếm, xem người bán | Public | Identity | Product (sản phẩm của Seller) |  |
| B03 | Quản lý giỏ hàng | Customer | Cart | Product, Order (tồn kho) |  |
| B04 | Đặt hàng, tách đơn theo cửa hàng | Customer | Order | Cart, Product, Identity, Finance (tỷ lệ hoa hồng) |  |
| B05 | Thanh toán | Customer | Payment | Order, Identity |  |
| B06 | Xác nhận đơn sau thanh toán | System | Order | Payment (event), Cart, Finance, Notification |  |
| B07 | Theo dõi đơn | Customer | Order | Payment (trạng thái hoàn) |  |
| B08 | Hủy đơn | Customer | Order | Payment (Refund), Finance, Notification |  |
| B09 | Xác nhận đã nhận hàng | Customer | Order | Finance (quyết toán), Notification |  |
| B10 | Mua ngay | Customer | Order | Product, Identity |  |
| C01 | Đánh giá sản phẩm (S) | Customer | Product | Order (đủ điều kiện đánh giá) |  |
| D01 | Quản lý sản phẩm | Seller | Product | Order (tạo bản ghi tồn kho) |  |
| D02 | Đóng gói, giao hoặc từ chối | Seller | Order | Payment, Finance, Notification |  |
| D03 | Cập nhật tồn kho | Seller | Order | — |  |
| D04 | Kết quả kinh doanh, khoản được nhận | Seller | Order (số đơn) \+ Finance (tiền) | — (FE ghép 2 nguồn) |  |
| D05 | Ví và rút tiền | Seller | Finance | Identity (trạng thái Seller), Notification |  |
| E01 | Quản lý người dùng | Admin | Identity | Order (số đơn), Finance (tóm tắt ví) |  |
| E02 | Báo cáo tổng quan | Admin | Finance (tiền) \+ Order (tiến độ) \+ Payment (thu/hoàn) | — (FE ghép, Reporting hoãn) |  |
| E03 | Cấm / gỡ cấm | Admin | Identity | Order KHÔNG tự hủy PAID/PACKING chỉ vì Seller APPROVED bị cấm. Seller xử lý đơn dở bằng phiên giới hạn; J4 xử lý quá hạn. Finance cho rút available, không tự đóng băng vì cấm SELLER; Notification thông báo. |  |
| E04 | Gỡ / khôi phục sản phẩm | Admin | Product | Notification |  |
| E06 | Duyệt Seller | Admin | Identity | Finance (tạo ví), Notification |  |
| E07 | Quản lý danh mục (S) | Admin | Product | — |  |
| E08 | Xem đơn, xử lý tiền gặp sự cố | Admin | Payment (Refund) \+ Order (xem đơn) \+ Finance (ví) | — |  |
| F01 | Gợi ý sản phẩm liên quan (C) | Public | Product | Order (loại sản phẩm hết hàng) |  |
| G01 | Thông báo | System | Notification | Mọi service (event) |  |
| G02 | Hoàn tiền | System; Customer chủ Payment mở chu kỳ mới | Payment | Order (event hủy), Finance, Notification |  |
| G03 | Job quá hạn/đối soát (J1–J7) | System | Order (J1,J3,J4), Payment (J2,J5), Notification (J6), Identity (J7) | — | |
| G04 | Quyết toán, ghi có ví | System | Finance | Order (event CONFIRMED) |  |

### 5.1 Ownership dữ liệu (theo Database RBAC v5.0)

| Service | Entity sở hữu | Không tự ý ghi |
| :---- | :---- | :---- |
| Identity & Seller | User, Customer, Seller, SellerApplication, CustomerAddress, Role, Permission, UserRole, RolePermission, AuthSession, PasswordResetToken, AdminAuditLog | Product, Order, Payment; service khác không sửa RBAC |
| Product | Category, Product (gồm media JSONB), Review | Inventory, OrderItem |
| Cart | Cart, CartItem | Order, Reservation |
| Order | CheckoutSession, Order, OrderItem, Inventory, Reservation, StockMovement | Payment, Refund, Settlement, Wallet |
| Payment | Payment, Refund | Order.status, Settlement |
| Finance | Settlement, Wallet, WalletTransaction, Withdrawal | Payment, Refund, Order |
| Notification | Notification | Dữ liệu nghiệp vụ của service khác |

## 6. Đặc tả theo service

Mỗi service gồm: phạm vi, bảng endpoint, DTO và chi tiết endpoint. Các DTO chỉ liệt kê field; kiểu dữ liệu theo quy ước 3.6 và Database RBAC v5.0.

### 6.1 Identity & Seller Service

**Identity tách tài khoản CUSTOMER/SELLER/ADMIN; vòng đời ban theo §3.2, không cấm theo scope. SellerApplication mới sau từ chối; avatar User = logo Seller; audit nội bộ.**

#### Danh sách endpoint

| \# | Method | Path | Mô tả | Quyền | UC |
| :---- | :---- | :---- | :---- | :---- | :---- |
| 1 | POST | /identity/auth/register | Đăng ký Customer bằng email | PUBLIC | E06 |
| 2 | POST | /identity/auth/login | Đăng nhập đúng accountType, ba loại tài khoản độc lập | PUBLIC | A03 |
| 3 | POST | /identity/auth/google | Đăng ký / đăng nhập Customer bằng Google (Should) | PUBLIC | E06, A03 |
| 4 | POST | /identity/auth/register-seller | Đăng ký bán hàng | PUBLIC | A02 |
| 5 | POST | /identity/auth/refresh | Làm mới access token | PUBLIC | A03 |
| 6 | POST | /identity/auth/logout | Đăng xuất thiết bị hiện tại | ANY\_AUTH (session own) | A07 |
| 7 | POST | /identity/auth/password/forgot | Yêu cầu đặt lại mật khẩu | PUBLIC | A05 |
| 8 | POST | /identity/auth/password/reset | Đặt mật khẩu mới bằng liên kết | PUBLIC | A05 |
| 9 | PUT | /identity/users/me/password | Đổi mật khẩu | account:update:own (LOCAL) | A04 |
| 10 | GET | /identity/users/me | Hồ sơ User và các vai trò hiện hành | account:read:own | A06 |
| 11 | PATCH | /identity/users/me | Cập nhật thông tin chung của User | account:update:own | A06 |
| 12 | PATCH | /identity/sellers/me | Seller cập nhật hồ sơ người bán | seller:profile:own | A06 |
| 13 | POST | /identity/media/upload-urls | Xin URL upload logo Seller | seller:profile:own | A06 |
| 14 | GET | /identity/customers/me/addresses | Danh sách sổ địa chỉ | customer:profile:own | A06, B04 |
| 15 | POST | /identity/customers/me/addresses | Thêm địa chỉ | customer:profile:own | A06 |
| 16 | PUT | /identity/customers/me/addresses/{addressId} | Sửa địa chỉ | customer:profile:own | A06 |
| 17 | DELETE | /identity/customers/me/addresses/{addressId} | Xóa địa chỉ | customer:profile:own | A06 |
| 18 | GET | /identity/regions/provinces | Danh mục tỉnh/thành | PUBLIC | A06 |
| 19 | GET | /identity/regions/provinces/{provinceCode}/wards | Danh mục phường/xã/đặc khu của tỉnh | PUBLIC | A06 |
| 20 | GET | /identity/sellers | Tìm kiếm người bán | PUBLIC | B02 |
| 21 | GET | /identity/sellers/{sellerId} | Thông tin công khai của người bán | PUBLIC | B02 |
| 22 | GET | /identity/admin/customers | Danh sách Customer | operations:read:any | E01 |
| 23 | GET | /identity/admin/customers/{customerId} | Chi tiết Customer | operations:read:any | E01 |
| 24 | GET | /identity/admin/sellers | Danh sách Seller (gồm hồ sơ chờ duyệt) | operations:read:any | E01, E06 |
| 25 | GET | /identity/admin/sellers/{sellerId} | Chi tiết Seller | operations:read:any | E01, E06 |
| 26 | POST | /identity/admin/sellers/{sellerId}/approve | Duyệt hồ sơ Seller | seller:approve | E06 |
| 27 | POST | /identity/admin/sellers/{sellerId}/reject | Từ chối hồ sơ Seller | seller:approve | E06 |
| 28 | POST | /identity/admin/ban/{userId} | Cấm tài khoản theo nghĩa vụ | account:moderate | E03 |
| 29 | POST | /identity/admin/unban/{userId} | Gỡ cấm tài khoản theo thời hạn | account:moderate | E03 |
| 30 | POST | /identity/auth/seller-reapplication/challenge | Yêu cầu email xác minh để đăng ký lại Seller | PUBLIC (email verification) | A02 |
| 31 | POST | /identity/auth/seller-reapplication | Nộp SellerApplication mới sau REJECTED | PUBLIC (verificationToken) | A02 |
| 32 | GET | /identity/admin/sellers/{sellerId}/applications | Admin xem lịch sử các lần đăng ký Seller (SellerApplication) | operations:read:any (ADMIN) | E06 |

#### DTO

| DTO | Field |
| :---- | :---- |
| CustomerProfile | customerId (=userId CUSTOMER),status,banReason,accessMode (enum §4.2),profileComplete,createdAt |
| SellerSelfProfile | sellerId (= userId tài khoản SELLER), description, logoUrl và avatarUrl (cùng ảnh users.avatar\_object\_key), approvalStatus, status, rejectionReason, banReason, registeredAt, reviewedAt, latestApplicationId. |
| UserSelfProfile | userId,accountType,email,authMethod,fullName,phoneNumber,avatarObjectKey,avatarUrl,accountStatus,userStatus,bannedAt (nullable),disabledAt (nullable),restoreUntil (nullable),roles[] (tối đa một),permissions[],authzVersion,accessMode (enum §4.2),customerProfile (nullable),sellerProfile (nullable; không đồng thời),createdAt |
| SellerPublic | sellerId, sellerName (users.full\_name), description, logoUrl (từ users.avatar\_object\_key). Không có sellers.logo\_object\_key hay sellerName/contactPhone riêng trong hồ sơ lưu trữ. |
| SellerAdminView | {user:UserSummary,sellerProfile:SellerSelfProfile,reviewedByUserId,banImpact}; không credential/token |
| Address | addressId, recipientName, recipientPhone, provinceCode, provinceName, wardCode, wardName, addressDetail, lastUsedAt, isDefault theo DB 3.1.4, createdAt |
| AuthTokens | accessToken, accessTokenExpiresIn, refreshToken, tokenType=Bearer, accountType, accessMode, profile: UserSelfProfile. Backend phải kiểm tra quyền hiện tại, không tin quyền snapshot từ token. |
| UserSummary | userId,email,fullName,phoneNumber,accountType,accountStatus,userStatus,accessMode,bannedAt (nullable),disabledAt (nullable),restoreUntil (nullable),authzVersion; chỉ quản trị operations:read:any |
| CustomerAdminView | {user:UserSummary,customerProfile,banReason,banImpact}; không banHistory/credential |
| BanImpact | {evaluatedAt,resultingAccountStatus,obligations}; obligations = {hasOpenOrders,hasPendingPayment,hasUnresolvedRefund,hasPendingFinance,pendingBalanceVnd,availableBalanceVnd}; Customer có hasPendingFinance=false, hai số dư 0 |

Admin detail trả banImpact snapshot cho popup, không endpoint preview public mới. Nguồn lỗi không giả số 0, không cho xác nhận đến khi tải được; POST ban đọc lại, UI hiển thị kết quả thực tế nếu khác snapshot. Không khóa liên service. Popup bỏ scope, nêu loại tài khoản/lý do bắt buộc, từng nghĩa vụ/pending/available, trạng thái và quyền dự kiến; BANNED chưa bắt đầu hạn, DISABLED 30 ngày từ disabledAt. Detail hiển thị mốc/deadline, khóa gỡ quá hạn.

#### Chi tiết endpoint

##### POST /identity/auth/register — Đăng ký Customer bằng email

**Quyền: PUBLIC    UC: E06**

* Tạo User LOCAL accountType CUSTOMER \+ Customer \+ UserRole CUSTOMER nguyên tử; email unique toàn hệ thống. Không tạo profile/role Seller/Admin trên User Customer.

* Không xác minh email (P-23). Không tự đăng nhập; FE hiển thị popup thành công 1 giây rồi chuyển sang trang đăng nhập.

* Rate limit theo IP (N-04).

**Request body (application/json)**

| Field | Kiểu | Bắt buộc | Validation / ghi chú |
| :---- | :---- | :---- | :---- |
| email | string | ✓ | Định dạng email, ≤ 254 ký tự; chuẩn hóa chữ thường |
| fullName | string | ✓ | 1–150 ký tự |
| phoneNumber | string | ✓ | SĐT Việt Nam, chuẩn hóa dạng 0xxxxxxxxx |
| password | string | ✓ | G-07 |
| confirmPassword | string | ✓ | Trùng password (kiểm tra ở cả FE và BE) |

**Response thành công**

201 data: UserSelfProfile accountType=CUSTOMER, customerProfile có dữ liệu, sellerProfile=null; không tự đăng nhập.

```text
POST /api/v1/identity/auth/register
{ "email": "an.nguyen@gmail.com", "fullName": "Nguyễn Văn An",
  "phoneNumber": "0912345678", "password": "Matkhau123", "confirmPassword": "Matkhau123" }
```

```text
201 Created
{ "data": { "userId": "0b6f...", "email": "an.nguyen@gmail.com", "authMethod": "LOCAL",
            "fullName": "Nguyễn Văn An", "phoneNumber": "0912345678", "userStatus": "ACTIVE",
            "roles": ["CUSTOMER"], "customerProfile": { "customerId": "0b6f...", "status": "ACTIVE", "paymentBlocked": false },
            "sellerProfile": null, "authzVersion": 1, "createdAt": "2026-10-25T08:30:00Z" } }
```

**Lỗi nghiệp vụ**

* EMAIL\_ALREADY\_USED (409), PHONE\_ALREADY\_USED (409) — details chỉ rõ field trùng

* PASSWORD\_POLICY\_VIOLATION (400), VALIDATION\_ERROR (400)

##### POST /identity/auth/login — Đăng nhập theo accountType Customer / Seller / Admin

**Quyền: PUBLIC    UC: A03**

* Request bắt buộc {email,password,accountType:CUSTOMER|SELLER|ADMIN}; sai accountType dù đúng mật khẩu → 401 INVALID_CREDENTIALS chung. Login áp dụng đầy đủ G-14 §3.2, gồm pending/refund, nghĩa vụ muộn khi DISABLED, 503 khi nguồn lỗi; Seller PENDING/REJECTED không login.

* P-18: không khóa tạm tài khoản vì đăng nhập sai nhiều lần; vẫn giữ rate limit và biện pháp chống lạm dụng N-04. Cấm tài khoản bởi Admin là vòng đời riêng, không suy từ số lần sai mật khẩu.

**Request body (application/json): bắt buộc email, password, accountType (CUSTOMER\|SELLER\|ADMIN).**

| Field | Kiểu | Bắt buộc | Validation / ghi chú |
| :---- | :---- | :---- | :---- |
| email | string | ✓ |  |
| password | string | ✓ |  |

**Response thành công**

200 AuthTokens gồm accountType/accessMode, profile chỉ chứa hồ sơ đúng tài khoản, không có cả CustomerProfile và SellerProfile.

```text
200 OK
{ "data": { "accessToken": "eyJhbGciOi...", "accessTokenExpiresIn": 900, "refreshToken": "rt_7Hq...", "tokenType": "Bearer", "accountType": "SELLER", "accessMode": "FULL", "profile": { "userId": "0b6f...", "accountType": "SELLER", "roles": ["SELLER"], "permissions": ["order:fulfill:own", "withdrawal:create:own"], "authzVersion": 3, "customerProfile": null, "sellerProfile": { "sellerId": "0b6f...", "approvalStatus": "APPROVED", "status": "ACTIVE" } } } }
```

**Lỗi nghiệp vụ**

* INVALID\_CREDENTIALS (401)

* WRONG\_AUTH\_METHOD (409) — email đăng ký bằng Google

* ACCOUNT\_DISABLED (403) sau khi xác minh không có nghĩa vụ muộn; SELLER\_PENDING\_APPROVAL/SELLER\_REJECTED (403); SERVICE_UNAVAILABLE (503) khi chưa xác minh được nghĩa vụ. ACCOUNT_BANNED (403) dành cho thao tác ngoài allowlist, không thay ACCOUNT_DISABLED khi đã hết nghĩa vụ. Sai accountType trả INVALID\_CREDENTIALS (401).

##### POST /identity/auth/google — Đăng ký / đăng nhập Customer bằng Google (Should)

**Quyền: PUBLIC    UC: E06, A03**

* FE lấy idToken từ Google Identity Services rồi gửi lên. BE verify với Google, lấy email \+ họ tên.

* Google subject mới, email chưa dùng → tạo User GOOGLE accountType CUSTOMER \+ Customer \+ role CUSTOMER; không tạo Seller/Admin qua Google.

* Email đã đăng ký bằng mật khẩu → không gộp tài khoản.

**Request body (application/json)**

| Field | Kiểu | Bắt buộc | Validation / ghi chú |
| :---- | :---- | :---- | :---- |
| idToken | string | ✓ | Google ID token |

**Response thành công**

200 → data: AuthTokens \+ isNewAccount: boolean

**Lỗi nghiệp vụ**

* GOOGLE\_AUTH\_FAILED (401)

* WRONG\_AUTH\_METHOD (409)

* Customer BANNED login theo nghĩa vụ hiện tại G-14 §3.2; DISABLED phải kiểm tra nghĩa vụ muộn trước ACCOUNT_DISABLED.

##### POST /identity/auth/register-seller — Đăng ký bán hàng

**Quyền: PUBLIC    UC: A02**

* Lần đầu: tạo User LOCAL accountType SELLER \+ Seller PENDING \+ SellerApplication PENDING (applicationId mới), chưa cấp role SELLER/phiên/ví. Không chuyển tài khoản Customer/Admin thành Seller. Sau REJECTED: nộp SellerApplication MỚI trên User Seller cũ qua quy trình xác minh email công khai.

* Email unique tại User. Seller REJECTED vẫn giữ email/User nhưng không login; muốn đăng ký lại gọi POST /identity/auth/seller-reapplication/challenge (gửi token xác minh email, phản hồi chung) rồi POST /identity/auth/seller-reapplication với verificationToken \+ fullName \+ phoneNumber \+ description: tạo applicationId mới PENDING, cùng sellerId/email, giữ bản ghi REJECTED. Token một lần/có hạn, rate limit, không tiết lộ email/approvalStatus. Tên shop=users.full\_name, số liên hệ=users.phone\_number, logo=users.avatar\_object\_key.

* Phát event seller.registered để Notification gửi email xác nhận đã nhận hồ sơ.

**Request body (application/json)**

| Field | Kiểu | Bắt buộc | Validation / ghi chú |
| :---- | :---- | :---- | :---- |
| email | string | ✓ | Duy nhất toàn hệ thống |
| fullName | string | ✓ | Tên cửa hàng users.full\_name, dài 1–150 ký tự; không trùng Seller PENDING/APPROVED khác, không sửa khi PENDING/APPROVED. |
| phoneNumber | string | ✓ | SĐT Việt Nam, duy nhất toàn hệ thống (users.phone\_number); dùng làm số liên hệ cửa hàng |
| password | string | ✓ | G-07 |
| confirmPassword | string | ✓ |  |

**Response thành công**

201 data { userId, accountType:SELLER, sellerProfile:{ sellerId, approvalStatus:PENDING, latestApplicationId }, applicationId }; không trả session/token. Luồng reapplication thành công trả 201 applicationId mới trên Seller hiện có.

**Lỗi nghiệp vụ**

* EMAIL\_ALREADY\_USED, PHONE\_ALREADY\_USED, SELLER\_NAME\_ALREADY\_USED (409)

* PASSWORD\_POLICY\_VIOLATION (400)

##### POST /identity/auth/refresh — Làm mới access token

**Quyền: PUBLIC    UC: A03**

* Xoay vòng refresh token. Token cũ dùng lại sau khi đã xoay → thu hồi toàn bộ chuỗi token đó (phát hiện đánh cắp).

* Refresh dùng cùng G-14 §3.2, kiểm tra accountType/approval và Order/Payment/Refund/Finance kể cả DISABLED; nguồn lỗi 503 không mở rộng quyền.

**Request body (application/json)**

| Field | Kiểu | Bắt buộc | Validation / ghi chú |
| :---- | :---- | :---- | :---- |
| refreshToken | string | ✓ |  |

**Response thành công**

200 → data: AuthTokens với UserSelfProfile, roles và permissions hiện hành; không sao chép role từ access token cũ.

**Lỗi nghiệp vụ**

* INVALID\_REFRESH\_TOKEN (401)

* INVALID\_REFRESH\_TOKEN (401) nếu phiên không còn hiệu lực.

* ACCOUNT_DISABLED (403) sau đối soát không có nghĩa vụ muộn; SELLER_PENDING_APPROVAL/SELLER_REJECTED (403); SERVICE_UNAVAILABLE (503) khi nguồn nghĩa vụ lỗi. Không cấp token quyền rộng hơn khi chưa xác minh.

##### POST /identity/auth/logout — Đăng xuất thiết bị hiện tại

**Quyền: ANY\_AUTH (session own)    UC: A07**

* Thu hồi AuthSession gửi lên sau khi xác minh refresh token thuộc cùng User/session; token đã hết hạn/đã logout vẫn trả 204\. Không cho dùng token người khác để thu hồi phiên không thuộc mình.

**Request body (application/json)**

| Field | Kiểu | Bắt buộc | Validation / ghi chú |
| :---- | :---- | :---- | :---- |
| refreshToken | string | ✓ |  |

**Response thành công**

204

##### POST /identity/auth/password/forgot — Yêu cầu đặt lại mật khẩu

**Quyền: PUBLIC    UC: A05**

* **Luôn** trả cùng một response dù email tồn tại hay không, là tài khoản Google hay Seller chưa duyệt (G-05). Xem điểm C-09 về mâu thuẫn trong UC-A05.

* Nếu đủ điều kiện: tạo PasswordResetToken tham chiếu User.user\_id, chỉ lưu hash; gửi link trên origin reset tin cậy đã cấu hình, không chọn actor/redirect URL từ client. P-05 chốt hiệu lực 30 phút từ tạo, chỉ dùng một lần.

**Request body (application/json)**

| Field | Kiểu | Bắt buộc | Validation / ghi chú |
| :---- | :---- | :---- | :---- |
| email | string | ✓ |  |

**Response thành công**

202 → data: { message: "Nếu email hợp lệ, hướng dẫn đặt lại mật khẩu đã được gửi." }

**Lỗi nghiệp vụ**

* RATE\_LIMITED (429)

##### POST /identity/auth/password/reset — Đặt mật khẩu mới bằng liên kết

**Quyền: PUBLIC    UC: A05**

* Token dùng một lần, cập nhật password\_hash của User LOCAL và thu hồi mọi AuthSession; GOOGLE không tạo mật khẩu LOCAL qua reset.

**Request body (application/json)**

| Field | Kiểu | Bắt buộc | Validation / ghi chú |
| :---- | :---- | :---- | :---- |
| token | string | ✓ | Token trong liên kết email |
| newPassword | string | ✓ | G-07 |
| confirmPassword | string | ✓ |  |

**Response thành công**

204

**Lỗi nghiệp vụ**

* RESET\_TOKEN\_INVALID (400)

* PASSWORD\_POLICY\_VIOLATION (400)

##### PUT /identity/users/me/password — Đổi mật khẩu

**Quyền: account:update:own (LOCAL)    UC: A04**

* Thay mật khẩu User LOCAL; thu hồi mọi phiên của User, kể cả phiên hiện tại. Thành công trả 204, FE yêu cầu login lại.

* Tài khoản Google → 403 FORBIDDEN (FE ẩn chức năng này).

**Request body (application/json)**

| Field | Kiểu | Bắt buộc | Validation / ghi chú |
| :---- | :---- | :---- | :---- |
| currentPassword | string | ✓ |  |
| newPassword | string | ✓ | G-07, khác mật khẩu cũ |
| confirmPassword | string | ✓ |  |

**Response thành công**

204

**Lỗi nghiệp vụ**

* PASSWORD\_INCORRECT (400)

* PASSWORD\_SAME\_AS\_OLD (400)

* PASSWORD\_POLICY\_VIOLATION (400)

##### GET /identity/users/me — Hồ sơ của actor đang đăng nhập

**Quyền: account:read:own    UC: A06**

**Response thành công**

200 → data UserSelfProfile có accountType, avatarObjectKey, một role hiện hành, tối đa một CustomerProfile/SellerProfile và accessMode phù hợp trạng thái cấm.

##### PATCH /identity/users/me — Cập nhật thông tin User chung

**Quyền: account:update:own    UC: A06**

* PATCH allowlist fullName, phoneNumber, avatarObjectKey. Avatar lưu users.avatar\_object\_key; với Seller là logo cửa hàng. ObjectKey phải từ upload AVATAR do chủ tài khoản sở hữu. Không sửa email/authMethod/userId/accountType/role/status/authzVersion. Seller BANNED không sửa profile.

**Request body (application/json)**

| Field | Kiểu | Bắt buộc | Validation / ghi chú |
| :---- | :---- | :---- | :---- |
| fullName | string |  | 1–150 ký tự. Với User có Seller PENDING/APPROVED, fullName là tên cửa hàng và không đổi được (SELLER\_NAME\_LOCKED) |
| phoneNumber | string |  | SĐT Việt Nam, duy nhất. Không cần OTP (BRD 5.7). Với Seller đây cũng là số liên hệ cửa hàng |

**Response thành công**

200 → data: UserSelfProfile

**Lỗi nghiệp vụ**

* PHONE\_ALREADY\_USED (409)

* SELLER\_NAME\_LOCKED (409)

* VALIDATION\_ERROR (400) nếu gửi field ngoài allowlist.

##### PATCH /identity/sellers/me — Seller cập nhật hồ sơ người bán

**Quyền: seller:profile:own    UC: A06**

* Chỉ sửa description cho Seller APPROVED/ACTIVE. Tên cửa hàng và số điện thoại thuộc User; avatar của Seller chính là logo và được cập nhật qua PATCH /identity/users/me, không có cột logo riêng.

**Request body (application/json)**

| Field | Kiểu | Bắt buộc | Validation / ghi chú |
| :---- | :---- | :---- | :---- |
| description | string \| null |  | ≤ 2.000 ký tự |
| (KHÔNG DÙNG) logoObjectKey | — |  | Avatar/logo được cập nhật qua avatarObjectKey trên PATCH /identity/users/me; PATCH /identity/sellers/me chỉ nhận description. |

**Response thành công**

200 → data: SellerSelfProfile

**Lỗi nghiệp vụ**

* MEDIA\_INVALID (422)

##### POST /identity/media/upload-urls — Xin presigned URL upload AVATAR (ảnh đại diện, Seller dùng làm logo)

**Quyền: account:update:own trên đúng tài khoản/role, Seller APPROVED/ACTIVE; UC A06**

* Chỉ purpose AVATAR trên tài khoản được phép cập nhật hồ sơ; users.avatar\_object\_key dùng chung ảnh đại diện và logo Seller. Seller chờ duyệt/bị từ chối không được đăng nhập; Seller BANNED không được upload avatar khi chỉ có phiên giới hạn.

**Request body (application/json)**

| Field | Kiểu | Bắt buộc | Validation / ghi chú |
| :---- | :---- | :---- | :---- |
| files\[\] | array (1) | ✓ | {fileName,mimeType,sizeBytes,purpose:AVATAR}; cần account:update:own, không dùng seller:profile:own cho Customer/Admin. Seller BANNED không được upload ảnh khi chỉ có phiên giới hạn. |

**Response thành công**

200 → data: \[{ objectKey, uploadUrl, expiresAt }\]

**Lỗi nghiệp vụ**

* MEDIA\_INVALID (422)

##### GET /identity/customers/me/addresses — Danh sách sổ địa chỉ

**Quyền: customer:profile:own    UC: A06, B04**

* Địa chỉ của customerId \= sub; mặc định trước theo DB 3.1.4, rồi lastUsedAt giảm dần và createdAt tăng dần. Mọi chi tiết/sửa/xóa kiểm tra ownership.

**Response thành công**

200 → data: Address\[\], meta: { maxAddresses: 3 } (P-09).

##### POST /identity/customers/me/addresses — Thêm địa chỉ

**Quyền: customer:profile:own    UC: A06**

* P-09: tối đa 3 địa chỉ hiện có, kiểm tra cả yêu cầu đồng thời; địa chỉ hard delete không còn tính. P-26 kiểm tra mã/quan hệ bằng danh mục tĩnh seed DB Identity; cùng nguồn/version với API đọc.

**Request body (application/json)**

| Field | Kiểu | Bắt buộc | Validation / ghi chú |
| :---- | :---- | :---- | :---- |
| recipientName | string | ✓ | 1–150 ký tự |
| recipientPhone | string | ✓ | SĐT Việt Nam |
| provinceCode | string | ✓ | Mã tỉnh/thành (P-26) |
| wardCode | string | ✓ | Thuộc provinceCode |
| addressDetail | string | ✓ | 1–300 ký tự |

**Response thành công**

201 → data: Address

**Lỗi nghiệp vụ**

* ADDRESS\_LIMIT\_EXCEEDED (422)

* INVALID\_REGION (422)

##### PUT /identity/customers/me/addresses/{addressId} — Sửa địa chỉ

**Quyền: customer:profile:own    UC: A06**

* Body giống thêm mới. Không ảnh hưởng địa chỉ snapshot của các đơn đã tạo.

**Response thành công**

200 → data: Address

**Lỗi nghiệp vụ**

* NOT\_FOUND (404)

* INVALID\_REGION (422)

##### DELETE /identity/customers/me/addresses/{addressId} — Xóa địa chỉ

**Quyền: customer:profile:own    UC: A06**

* Cho phép xóa cả địa chỉ cuối cùng. Xóa vật lý (hard delete) theo DB 3.1.4; Order đã lưu snapshot nên không phụ thuộc, và địa chỉ đã xóa không còn tính vào giới hạn P-09.

**Response thành công**

204

**Lỗi nghiệp vụ**

* NOT\_FOUND (404)

##### GET /identity/regions/provinces — Danh mục tỉnh/thành từ DB

**Quyền: PUBLIC    UC: A06**

**Response thành công**

200 → data: \[{ code, name }\], meta: { sourceVersion, fetchedAt }. Identity đọc danh mục tĩnh seed trong DB; sourceVersion là phiên bản seed, fetchedAt là thời điểm đọc. Không gọi API địa giới ngoài.

##### GET /identity/regions/provinces/{provinceCode}/wards — Phường/xã/đặc khu theo tỉnh từ DB

**Quyền: PUBLIC    UC: A06**

**Response thành công**

200 → data: \[{ code, name, provinceCode }\], meta: { sourceVersion, fetchedAt }. Đọc từ cùng phiên bản seed DB với tỉnh/thành.

**Lỗi nghiệp vụ**

* INVALID\_REGION (422) khi dữ liệu seed hợp lệ xác nhận mã/quan hệ sai; REGION\_SOURCE\_UNAVAILABLE (503) khi dữ liệu seed chưa sẵn sàng/không đọc được để xác minh.

##### GET /identity/sellers — Tìm kiếm người bán

**Quyền: PUBLIC    UC: B02**

* Chỉ trả Seller thỏa G-06 về User ACTIVE, hồ sơ APPROVED/ACTIVE và role SELLER hiện hành. Tìm theo tên cửa hàng (users.full\_name), không phân biệt dấu tiếng Việt và hoa thường; chuẩn hóa cả q và tên theo §3.5 (P-37).

**Tham số (path / query)**

| Tên | Vị trí | Kiểu | Bắt buộc | Mô tả / ràng buộc |
| :---- | :---- | :---- | :---- | :---- |
| q | query | string |  | Tìm theo tên cửa hàng, không phân biệt dấu tiếng Việt và hoa thường; áp dụng chuẩn hóa tại §3.5 |
| page, size | query | int |  | Mặc định 1, 20 |

**Response thành công**

200 → data: SellerPublic\[\] \+ meta

##### GET /identity/sellers/{sellerId} — Thông tin công khai của người bán

**Quyền: PUBLIC    UC: B02**

* Dùng API PUBLIC này cho cả khách vãng lai, Customer, Seller và Admin khi xem trang shop. Chỉ trả SellerPublic; không lộ email, phone, lý do duyệt/cấm. Seller không hợp lệ theo G-06 →404. Sản phẩm lấy qua GET /products?sellerId=.

**Response thành công**

200 → data: SellerPublic

**Lỗi nghiệp vụ**

* NOT\_FOUND (404)

##### GET /identity/admin/customers — Danh sách Customer

**Quyền: operations:read:any    UC: E01**

**Tham số (path / query)**

| Tên | Vị trí | Kiểu | Bắt buộc | Mô tả / ràng buộc |
| :---- | :---- | :---- | :---- | :---- |
| q | query | string |  | Tìm theo tên, email, số điện thoại |
| status | query | ProfileStatus |  |  |
| page, size, sort | query |  |  | sort: createdAt, fullName |

**Response thành công**

200 → data: CustomerAdminView\[\] \+ meta

##### GET /identity/admin/customers/{customerId} — Chi tiết Customer

**Quyền: operations:read:any    UC: E01**

* Số đơn của khách lấy qua GET /orders/admin/orders?customerId= (FE gọi thêm).

**Response thành công**

200 → data CustomerAdminView theo DTO §6.1, gồm accountStatus/mốc/accessMode và banImpact hiện hành; không banHistory.

##### GET /identity/admin/sellers — Danh sách Seller (gồm hồ sơ chờ duyệt)

**Quyền: operations:read:any    UC: E01, E06**

**Tham số (path / query)**

| Tên | Vị trí | Kiểu | Bắt buộc | Mô tả / ràng buộc |
| :---- | :---- | :---- | :---- | :---- |
| q | query | string |  | Tên cửa hàng (fullName), email, SĐT |
| approvalStatus | query | SellerApprovalStatus |  | Màn "Duyệt người bán" dùng PENDING, sort registeredAt,asc (cũ nhất trước) |
| status | query | ProfileStatus |  |  |
| page, size, sort | query |  |  |  |

**Response thành công**

200 → data: SellerAdminView\[\] \+ meta

##### GET /identity/admin/sellers/{sellerId} — Chi tiết Seller

**Quyền: operations:read:any    UC: E01, E06**

* Tóm tắt ví lấy qua GET /finance/admin/wallets?sellerId=.

**Response thành công**

200 → data: SellerAdminView. Customer xem SellerPublic qua GET /identity/sellers/{sellerId}, không được nhận DTO quản trị.

##### POST /identity/admin/sellers/{sellerId}/approve — Duyệt hồ sơ Seller

**Quyền: seller:approve    UC: E06**

* Chỉ xét SellerApplication PENDING với applicationId mới nhất; ADMIN độc lập có seller:approve. Cập nhật application APPROVED \+ Seller.approval\_status APPROVED \+ duy nhất UserRole SELLER, tăng authzVersion, audit/outbox nguyên tử.

* Phát seller.approved chứa userId, sellerId, authzVersion → Finance tạo ví idempotent theo sellerId; không transaction xuyên service. Event trùng không tạo thêm ví.

* Chỉ Seller APPROVED mới có thể bị cấm; do đó không tồn tại Seller PENDING/BANNED được xét duyệt. Seller REJECTED muốn xét lại phải nộp SellerApplication mới, không duyệt hồ sơ REJECTED cũ.

**Response thành công**

200 → data: SellerAdminView

**Lỗi nghiệp vụ**

* ALREADY\_PROCESSED (409), SELF\_APPROVAL\_FORBIDDEN (403)

* NOT\_FOUND (404)

##### POST /identity/admin/sellers/{sellerId}/reject — Từ chối hồ sơ Seller

**Quyền: seller:approve    UC: E06**

* Chỉ SellerApplication PENDING → REJECTED; giữ nguyên applicationId, lý do và lịch sử, không cấp role Seller hay login. User/email thuộc tài khoản SELLER được giữ. Muốn đăng ký lại phải xác minh email qua API PUBLIC và tạo SellerApplication MỚI, cùng sellerId nhưng applicationId mới, không sửa hồ sơ cũ.

* P-21d: chọn Thông tin đăng ký không chính xác/không nhất quán; Thông tin gian hàng không phù hợp; Khác. Gửi reason dạng nhãn hoặc “Nhãn: mô tả”, tổng 1–1.000 ký tự; Khác phải có mô tả không rỗng sau trim. Không thêm enum/reasonCode/reasonNote hoặc yêu cầu KYC/giấy phép.

**Request body (application/json)**

| Field | Kiểu | Bắt buộc | Validation / ghi chú |
| :---- | :---- | :---- | :---- |
| reason | string | ✓ | 1–1.000 ký tự |

**Response thành công**

200 → data: SellerAdminView

**Lỗi nghiệp vụ**

* ALREADY\_PROCESSED (409)

##### POST /identity/admin/ban/{userId} — Cấm tài khoản

Quyền: account:moderate. Request: `{reason: string 1..1000}`. Không nhận `scope`; gửi field cũ trả 400 VALIDATION_ERROR, không alias.

P-21d: chọn Gian lận/lừa đảo; Kinh doanh hàng cấm; Kinh doanh hàng giả/vi phạm sở hữu trí tuệ; Khác. Lý do kinh doanh chỉ áp dụng Seller. Gửi reason dạng nhãn hoặc “Nhãn: mô tả”, tổng 1–1.000 ký tự; Khác bắt buộc mô tả không rỗng sau trim. Không áp giới hạn 500 của P-21a/P-21b, không thêm enum/cột lý do.

200 → `data: {userId,accountType,accountStatus,userStatus,customerStatus|null,sellerStatus|null,accessMode,bannedAt|null,disabledAt|null,restoreUntil|null,authzVersion,obligations}`.

Chỉ tác động tài khoản đã chọn, không tài khoản khác của cùng người; không cấm ADMIN (403), chỉ Seller APPROVED được cấm (Seller PENDING/REJECTED → 409 INVALID_STATE_TRANSITION). Đọc lại nguồn nghĩa vụ trước commit: còn nghĩa vụ → BANNED; xác minh hết → DISABLED. Nguồn lỗi/404 bất thường/không nhất quán → 503 SERVICE_UNAVAILABLE, không ghi ban một phần. Ban lặp không phát sự kiện hoặc đổi mốc. Lỗi chung: 401 khi chưa xác thực, 403 thiếu quyền/target ADMIN, 404 target không tồn tại.

`bannedAt` là đầu đợt cấm; `disabledAt` là lần đầu DISABLED trong đợt, `restoreUntil = disabledAt + 30 ngày` theo UTC. Cấm/job lặp và DISABLED→BANNED→DISABLED giữ mốc của cùng đợt. disabled_reason dùng lý do đợt cấm, profile ban_reason giữ đến unban; không có lệnh khóa độc lập vượt nghĩa vụ.

Các mốc response là timestamp UTC (ISO 8601), nullable trước khi được lập; BANNED chưa từng DISABLED có disabledAt=null và restoreUntil=null. `obligations` dùng đúng cấu trúc BanImpact.obligations (§6.1 DTO), không trả snapshot lịch sử hoặc field scope.

##### POST /identity/admin/unban/{userId} — Gỡ cấm tài khoản

Quyền: account:moderate. Request: `{reason?: string <=1000}`; `scope` → 400 VALIDATION_ERROR.

200 → `data: {userId,accountType,accountStatus,userStatus,customerStatus|null,sellerStatus|null,accessMode,bannedAt|null,disabledAt|null,restoreUntil|null,authzVersion}`.

BANNED được gỡ bất kỳ lúc nào, kể cả hơn 30 ngày chưa DISABLED. DISABLED được gỡ khi `now <= restoreUntil`; sau hạn → 409 ACCOUNT_RESTORE_WINDOW_EXPIRED, ví dụ `{"error":{"code":"ACCOUNT_RESTORE_WINDOW_EXPIRED","message":"Cửa sổ gỡ cấm đã hết hạn"}}`. Không xóa tài khoản/dữ liệu/tiền hoặc đổi trạng thái đơn khi quá hạn. ACTIVE trả 200 idempotent, không phát lặp.

Unban không cần Order/Finance để bỏ lệnh cấm nhưng kiểm tra tài khoản/Seller local; chuyển ACTIVE, xóa mốc hiệu lực/lý do hiện tại, giữ lịch sử audit. Đợt ban mới tạo mốc mới. Transaction Identity gồm cặp trạng thái, authzVersion, audit, Outbox; không phục hồi đơn hủy, Product HIDDEN/REMOVED hoặc quyền Seller chưa duyệt. Lỗi chung 401/403/404; Seller chưa duyệt 409 INVALID_STATE_TRANSITION.

##### POST /identity/auth/seller-reapplication/challenge — Yêu cầu xác minh email để nộp hồ sơ Seller mới (PUBLIC)

Quyền: PUBLIC; rate limit theo IP/email, không yêu cầu Seller đăng nhập; UC A02

POST /identity/auth/seller-reapplication/challenge (PUBLIC): request {email}, luôn trả 202 thông báo chung; chỉ gửi email verificationToken một lần/có hạn cho tài khoản SELLER bị REJECTED. Không đòi Seller đăng nhập, có rate limit.

Response: 202 thông báo chung khi yêu cầu email xác minh; không lộ email/hồ sơ có tồn tại.

Lưu ý: endpoint challenge chỉ gửi token xác minh qua email cho Seller REJECTED, luôn trả phản hồi chung 202; không tạo application ở bước này. Tạo SellerApplication mới chỉ thực hiện tại POST /identity/auth/seller-reapplication (mục kế tiếp).

##### POST /identity/auth/seller-reapplication — Nộp SellerApplication mới sau REJECTED (PUBLIC)

Quyền: PUBLIC với verificationToken xác minh email tài khoản SELLER bị REJECTED; UC A02

Request body {verificationToken,fullName,phoneNumber,description?}; token email xác minh một lần, có hạn; chỉ User SELLER đang REJECTED mới tạo được hồ sơ mới.

201 Created: {sellerId,applicationId,approvalStatus:PENDING}; không cấp session, giữ SellerApplication REJECTED trước đó.

Seller PENDING/REJECTED không đăng nhập. Chỉ tạo hồ sơ MỚI khi application trước đã REJECTED; PENDING/APPROVED trả 409 INVALID\_STATE\_TRANSITION. Không chuyển tài khoản Customer/Admin thành Seller.

##### (ĐÃ LOẠI) GET /identity/users/me/customer-profile — không cho tạo Customer trên User SELLER/ADMIN

Quyền: ANY\_AUTH (policy nền)

Request: Body rỗng; không nhận role/status/owner

Response cũ không còn hợp lệ; client đăng ký tài khoản Customer riêng qua POST /identity/auth/register. Endpoint cũ trả 410 GONE hoặc bị xóa khỏi OpenAPI.

ĐÃ BỎ: không cho bổ sung hồ sơ Customer trên User Seller/Admin. Endpoint GET /identity/users/me/customer-profile cũ ngừng hỗ trợ (410 GONE hoặc loại bỏ khỏi OpenAPI); phải đăng ký User accountType=CUSTOMER riêng bằng /identity/auth/register.

#### Nguồn dữ liệu địa giới

Hai API /identity/regions/\*\* đọc danh mục địa giới tĩnh seed trong DB Identity: tỉnh/thành và phường/xã/đặc khu thuộc tỉnh/thành. Nguồn và phiên bản seed theo [P-26](parameters.md#p-26). Identity không gọi API địa giới ngoài.

Lưu code dạng chuỗi, name, quan hệ ward thuộc province và phiên bản dữ liệu. API danh mục, thêm/sửa địa chỉ và snapshot dùng cùng dữ liệu seed. Không đọc được/chưa sẵn sàng →503 REGION_SOURCE_UNAVAILABLE, không kết luận mã sai. 422 INVALID_REGION chỉ khi dữ liệu hợp lệ xác nhận mã hoặc quan hệ sai. Order lưu mã và tên cùng addressDetail tại checkout, không tra lại thay đổi đơn cũ.

### 6.2 Product Service

**Phạm vi:** danh mục, sản phẩm và media, tìm kiếm sản phẩm, đánh giá, gợi ý liên quan, gỡ/khôi phục sản phẩm. Tên cũ: Catalog Service. Tồn kho **không** thuộc Product Service (thuộc Order — DB 3.2.2). **UC:** B01, C01, D01, E04, E07, F01.

#### Danh sách endpoint

| \# | Method | Path | Mô tả | Quyền | UC |
| :---- | :---- | :---- | :---- | :---- | :---- |
| 1 | GET | /categories | Danh mục đang hiển thị | PUBLIC | B01, D01 |
| 2 | GET | /products | Tìm kiếm / lọc / duyệt sản phẩm | PUBLIC | B01, B02 |
| 3 | GET | /products/{productId} | Chi tiết sản phẩm | PUBLIC | B01, B10 |
| 4 | GET | /products/{productId}/related | Gợi ý sản phẩm liên quan (Could) | PUBLIC | F01 |
| 5 | GET | /products/{productId}/reviews | Đánh giá của sản phẩm | PUBLIC | B01, C01 |
| 6 | POST | /media/upload-urls | Xin URL upload media sản phẩm / review | product:manage:own hoặc review:write:own theo purpose | D01, C01 |
| 7 | POST | /products/{productId}/reviews | Tạo đánh giá (Should) | review:write:own | C01 |
| 8 | GET | /seller/products | Sản phẩm của tôi | product:manage:own | D01, D03 |
| 9 | POST | /seller/products | Thêm sản phẩm | product:manage:own | D01 |
| 10 | GET | /seller/products/{productId} | Chi tiết sản phẩm của tôi | product:manage:own | D01 |
| 11 | PUT | /seller/products/{productId} | Sửa thông tin sản phẩm | product:manage:own | D01 |
| 12 | PATCH | /seller/products/{productId}/status | Bật bán / tạm ẩn | product:manage:own | D01 |
| 13 | GET | /admin/products | Tra cứu sản phẩm toàn hệ thống | operations:read:any | E04 |
| 14 | GET | /admin/products/{productId} | Chi tiết sản phẩm (Admin) | operations:read:any | E04 |
| 15 | POST | /admin/products/{productId}/remove | Gỡ sản phẩm | product:moderate | E04 |
| 16 | POST | /admin/products/{productId}/restore | Khôi phục sản phẩm (về Tạm ẩn) | product:moderate | E04 |
| 17 | GET | /admin/categories | Tất cả danh mục (gồm ẩn) | category:manage | E07 |
| 18 | POST | /admin/categories | Thêm danh mục (Should) | category:manage | E07 |
| 19 | PATCH | /admin/categories/{categoryId} | Đổi tên / ẩn / hiện danh mục | category:manage | E07 |
| 20 | DELETE | /admin/categories/{categoryId} | Xóa danh mục chưa có sản phẩm | category:manage | E07 |

#### DTO

| DTO | Field |
| :---- | :---- |
| Category | categoryId, categoryName, status, productCount (chỉ trong API Admin) |
| MediaItem | mediaType, url, sortOrder, isCover |
| ProductCard | productId, productName, priceVnd, coverImageUrl, ratingAverage (trung bình cộng, 1 chữ số thập phân, P-12), ratingCount, stockStatus, sellerId, sellerName |
| ProductDetail | ProductCard \+ description, category { categoryId, categoryName }, media: MediaItem\[\], availableQuantity, maxPerOrder (99 theo P-10, checkout vẫn kiểm tra tồn), seller: SellerPublic |
| SellerProduct | productId, productName, category, priceVnd, status, coverImageUrl, media\[\], description, onHandQuantity, reservedQuantity, availableQuantity, inventoryVersion, removalReason, removedAt, createdAt, updatedAt |
| AdminProduct | ProductCard \+ status, categoryName, removalReason, removedAt, restoreDeadline, restoreDaysLeft |
| Review | reviewId, productId, orderItemId, rating, content, media: MediaItem\[\], reviewerDisplayName (che một phần theo P-12), isMine, createdAt, updatedAt |

#### Chi tiết endpoint

##### GET /categories — Danh mục đang hiển thị

**Quyền: PUBLIC    UC: B01, D01**

**Response thành công**

200 → data: Category\[\] (chỉ VISIBLE, sắp theo tên)

Danh mục nền phải seed đúng 10 ngành hàng/tên/ví dụ tại [parameters.md](parameters.md#danh-mục-ban-đầu--p-21e) và Database §3.2.1, kể cả chưa làm màn quản trị Should. categoryId UUID, không lấy số 01–10 làm ID; Category không có tỷ lệ hoa hồng riêng.

##### GET /products — Tìm kiếm / lọc / duyệt sản phẩm

**Quyền: PUBLIC    UC: B01, B02**

* Chỉ trả sản phẩm bán được theo G-06: Product SELLING, User chủ shop ACTIVE, UserRole SELLER hiệu lực, Seller APPROVED/ACTIVE. Hết hàng vẫn trả stockStatus OUT\_OF\_STOCK.

* Product Service hỏi Identity trạng thái User/quyền SELLER/hồ sơ theo lô và Order về tồn kho qua internal API. Không đọc database của Identity/Order; không cache quyền trong MVP.

* Dùng cho trang chủ, trang kết quả tìm kiếm, trang người bán (sellerId).

**Tham số (path / query)**

| Tên | Vị trí | Kiểu | Bắt buộc | Mô tả / ràng buộc |
| :---- | :---- | :---- | :---- | :---- |
| q | query | string |  | Tìm theo tên sản phẩm, không phân biệt dấu tiếng Việt và hoa thường; áp dụng chuẩn hóa tại §3.5 (P-37) |
| categoryId | query | uuid |  |  |
| sellerId | query | uuid |  | Lọc sản phẩm của một người bán (trang người bán) |
| minPriceVnd, maxPriceVnd | query | int |  | Khoảng giá |
| sort | query | string |  | createdAt,desc (mặc định — mới nhất), priceVnd,asc\|desc, ratingAverage,desc |
| page, size | query | int |  |  |

**Response thành công**

200 → data: ProductCard\[\] \+ meta

GET /api/v1/products?q=ao thun\\\&categoryId=4c1e...\\\&sort=priceVnd,asc\\\&page=1\\\&size=20

```text
200 OK
{ "data": [ { "productId": "8b1f...", "productName": "Áo thun basic cotton", "priceVnd": 150000,
              "coverImageUrl": "https://cdn.../p/8b1f/cover.webp", "ratingAverage": 4.6, "ratingCount": 23,
              "stockStatus": "IN_STOCK", "sellerId": "5d2a...", "sellerName": "Shop Áo Xinh" } ],
  "meta": { "page": 1, "size": 20, "totalItems": 41, "totalPages": 3 } }
```

**Lỗi nghiệp vụ**

* VALIDATION\_ERROR (400) — minPriceVnd \> maxPriceVnd, size \> 100

##### GET /products/{productId} — Chi tiết sản phẩm

**Quyền: PUBLIC    UC: B01, B10**

* Sản phẩm không bán được → 404 (FE báo "sản phẩm không còn bán").

* availableQuantity dùng cho "Còn X sản phẩm", giới hạn ô số lượng và bật/tắt nút "Thêm vào giỏ" / "Mua ngay".

**Response thành công**

200 → data: ProductDetail

**Lỗi nghiệp vụ**

* NOT\_FOUND (404)

##### GET /products/{productId}/related — Gợi ý sản phẩm liên quan (Could)

**Quyền: PUBLIC    UC: F01**

* Luật đơn giản: cùng danh mục, loại sản phẩm hiện tại, hết hàng, không bán được. Có thể khác Seller.

**Tham số (path / query)**

| Tên | Vị trí | Kiểu | Bắt buộc | Mô tả / ràng buộc |
| :---- | :---- | :---- | :---- | :---- |
| limit | query | int |  | Mặc định 8, tối đa 20 |

**Response thành công**

200 → data: ProductCard\[\] (có thể rỗng — FE ẩn khối)

##### GET /products/{productId}/reviews — Đánh giá của sản phẩm

**Quyền: PUBLIC    UC: B01, C01**

**Tham số (path / query)**

| Tên | Vị trí | Kiểu | Bắt buộc | Mô tả / ràng buộc |
| :---- | :---- | :---- | :---- | :---- |
| rating | query | int 1–5 |  | Lọc theo số sao |
| page, size | query |  |  | sort mặc định createdAt,desc |

**Response thành công**

200 → data: Review\[\] \+ meta \+ summary: { ratingAverage, ratingCount, countByStar: {"1":0,...,"5":0} }

##### POST /media/upload-urls — Xin URL upload media sản phẩm / review

**Quyền: product:manage:own hoặc review:write:own theo purpose    UC: D01, C01**

**Request body (application/json)**

| Field | Kiểu | Bắt buộc | Validation / ghi chú |
| :---- | :---- | :---- | :---- |
| files\[\] | array (1–9) | ✓ | Mỗi phần tử: fileName, mimeType, sizeBytes, durationSec (video), purpose |

**Response thành công**

200 → data: \[{ objectKey, uploadUrl, expiresAt }\]

**Lỗi nghiệp vụ**

* MEDIA\_INVALID (422)

##### POST /products/{productId}/reviews — Tạo đánh giá (Should)

**Quyền: review:write:own    UC: C01**

* Product Service hỏi Order (GET /internal/v1/orders/order-items/{orderItemId}/review-eligibility) để xác minh: OrderItem thuộc Customer, đúng productId, Order ở CONFIRMED.

* Mỗi Customer tối đa một review/sản phẩm. Customer BANNED dù có phiên giới hạn xử lý Order chưa hoàn tất cũng không được tạo đánh giá mới.

**Request body (application/json)**

| Field | Kiểu | Bắt buộc | Validation / ghi chú |
| :---- | :---- | :---- | :---- |
| orderItemId | uuid | ✓ | Dòng hàng làm căn cứ đã mua |
| rating | int | ✓ | Số nguyên 1–5 |
| content | — |  | ≤ 1.000 ký tự (P-08.5) |
| mediaObjectKeys | string\[\] |  | purpose REVIEW\_MEDIA, tối đa 5 tệp, tối đa 1 video (P-08.3) |

**Response thành công**

201 → data: Review

**Lỗi nghiệp vụ**

* REVIEW\_NOT\_ALLOWED (403)

* REVIEW\_ALREADY\_EXISTS (409); MVP không có API sửa review.

* VALIDATION\_ERROR (400) — rating ngoài 1–5 hoặc không nguyên

##### GET /seller/products — Sản phẩm của tôi

**Quyền: product:manage:own    UC: D01, D03**

* Gồm cả số liệu tồn kho (Product Service ghép từ internal API Order) để màn danh sách hiển thị "tồn / đang giữ / khả dụng".

* Tab "Hết hàng" \= stock=OUT\_OF\_STOCK; tab "Đã gỡ" \= status=REMOVED.

**Tham số (path / query)**

| Tên | Vị trí | Kiểu | Bắt buộc | Mô tả / ràng buộc |
| :---- | :---- | :---- | :---- | :---- |
| status | query | ProductStatus |  |  |
| stock | query | StockStatus |  |  |
| q | query | string |  |  |
| categoryId | query | uuid |  |  |
| page, size, sort | query |  |  | sort: updatedAt (mặc định desc), productName, priceVnd |

**Response thành công**

200 → data: SellerProduct\[\] \+ meta

##### POST /seller/products — Thêm sản phẩm

**Quyền: product:manage:own    UC: D01**    Idempotency-Key: bắt buộc

* Product tạo HIDDEN cùng công việc provision bền vững trong transaction; sau commit gọi POST /internal/v1/inventory idempotent theo productId. Chỉ khi Inventory xác nhận và Seller/Product còn hợp lệ mới áp trạng thái Seller yêu cầu. Timeout giữ HIDDEN, retry cùng productId và Idempotency-Key, không sinh Product mới/rollback giả xuyên DB. Seller ban hoặc Product REMOVED trong lúc đợi không tự SELLING. Validation/media lỗi trước chấp nhận tạo không lưu Product; không áp quy tắc đó cho HIDDEN hợp lệ đang chờ tồn. Dùng cơ chế [SAD §5.7](software-architecture.md), không thêm job/endpoint/trạng thái.

* Ảnh bìa \= media IMAGE đầu tiên theo thứ tự; chỉ có video → placeholder.

**Request body (application/json)**

| Field | Kiểu | Bắt buộc | Validation / ghi chú |
| :---- | :---- | :---- | :---- |
| productName | string | ✓ | 1–255 ký tự |
| categoryId | uuid | ✓ | Danh mục VISIBLE |
| priceVnd | int | ✓ | \> 0 |
| initialStock | int | ✓ | ≥ 0 |
| description | — |  | ≤ 5.000 ký tự (P-08.5) |
| media\[\] | array | ✓ | 1–9 phần tử { objectKey, mediaType, sortOrder }, tối đa 2 video (P-08.3) |
| status | ProductStatus | ✓ | SELLING hoặc HIDDEN |

**Response thành công**

201 → data: SellerProduct

Khi provision đang chờ, response có `status: HIDDEN`. Gửi lại cùng Seller, `Idempotency-Key` và nội dung trả sản phẩm cũ với trạng thái hiện tại. Cùng key nhưng nội dung khác trả `409 IDEMPOTENCY_KEY_REUSED`. Frontend đọc lại chi tiết sản phẩm để cập nhật trạng thái hiển thị.

```text
POST /api/v1/seller/products
Idempotency-Key: 44b9996e-82dd-4d7f-9023-288c84c9646e
{ "productName": "Áo thun basic cotton", "categoryId": "4c1e...", "priceVnd": 150000,
  "initialStock": 50, "description": "Cotton 100%...", "status": "SELLING",
  "media": [ { "objectKey": "tmp/seller/5d2a/7f3a.webp", "mediaType": "IMAGE", "sortOrder": 1 },
             { "objectKey": "tmp/seller/5d2a/91bc.mp4",  "mediaType": "VIDEO", "sortOrder": 2 } ] }
```

**Lỗi nghiệp vụ**

* MEDIA\_REQUIRED (422), MEDIA\_INVALID (422)

* CATEGORY\_NOT\_AVAILABLE (422)

##### GET /seller/products/{productId} — Chi tiết sản phẩm của tôi

**Quyền: product:manage:own    UC: D01**

**Response thành công**

200 → data: SellerProduct (kèm description, media\[\] đầy đủ)

**Lỗi nghiệp vụ**

* NOT\_FOUND (404) — không phải sản phẩm của mình

##### PUT /seller/products/{productId} — Sửa thông tin sản phẩm

**Quyền: product:manage:own    UC: D01**

* Không gồm tồn kho (sửa tồn qua PUT /orders/seller/inventory/{productId}).

* Giá mới chỉ áp dụng cho lượt mua mới (G-03).

**Request body (application/json)**

| Field | Kiểu | Bắt buộc | Validation / ghi chú |
| :---- | :---- | :---- | :---- |
| productName, categoryId, priceVnd, description, media\[\] |  | ✓ | Như thêm mới; media\[\] là danh sách đầy đủ sau khi sửa |

**Response thành công**

200 → data: SellerProduct

**Lỗi nghiệp vụ**

* PRODUCT\_REMOVED\_BY\_ADMIN (409)

* MEDIA\_REQUIRED (422)

* CATEGORY\_NOT\_AVAILABLE (422)

##### PATCH /seller/products/{productId}/status — Bật bán / tạm ẩn

**Quyền: product:manage:own    UC: D01**

**Request body (application/json)**

| Field | Kiểu | Bắt buộc | Validation / ghi chú |
| :---- | :---- | :---- | :---- |
| status | ProductStatus | ✓ | SELLING hoặc HIDDEN |

**Response thành công**

200 → data: SellerProduct

**Lỗi nghiệp vụ**

* PRODUCT\_REMOVED\_BY\_ADMIN (409)

##### GET /admin/products — Tra cứu sản phẩm toàn hệ thống

**Quyền: operations:read:any    UC: E04**

**Tham số (path / query)**

| Tên | Vị trí | Kiểu | Bắt buộc | Mô tả / ràng buộc |
| :---- | :---- | :---- | :---- | :---- |
| q | query | string |  |  |
| sellerId | query | uuid |  |  |
| categoryId | query | uuid |  |  |
| status | query | ProductStatus |  |  |
| page, size, sort | query |  |  |  |

**Response thành công**

200 → data: AdminProduct\[\] \+ meta

##### GET /admin/products/{productId} — Chi tiết sản phẩm (Admin)

**Quyền: operations:read:any    UC: E04**

**Response thành công**

200 → data: AdminProduct \+ media\[\], description

##### POST /admin/products/{productId}/remove — Gỡ sản phẩm

**Quyền: product:moderate    UC: E04**

* Chuyển REMOVED, lưu removedAt. Gỡ lại sản phẩm đã gỡ → 200, không đổi gì.

* Ghi AuditLog; phát product.removed → Notification báo Seller. Giỏ hàng tự cập nhật khi khách mở lại; checkout kiểm tra lại trước khi tạo đơn.

* P-21d chọn Sản phẩm bị cấm; Hàng giả/vi phạm sở hữu trí tuệ; Nội dung sai lệch/không phù hợp; Đăng trùng lặp/spam; Khác. reason dạng nhãn hoặc “Nhãn: mô tả”, tổng 1–1.000 ký tự; Khác phải có mô tả không rỗng sau trim. Không thêm enum/cột, không áp cơ chế điểm phạt sàn tham khảo.

**Request body (application/json)**

| Field | Kiểu | Bắt buộc | Validation / ghi chú |
| :---- | :---- | :---- | :---- |
| reason | string | ✓ | 1–1.000 ký tự |

**Response thành công**

200 → data: AdminProduct

##### POST /admin/products/{productId}/restore — Khôi phục sản phẩm (về Tạm ẩn)

**Quyền: product:moderate    UC: E04**

* Chỉ trong 30 ngày kể từ removedAt. Sau khôi phục, sản phẩm ở HIDDEN để Seller tự bật bán.

**Response thành công**

200 → data: AdminProduct

**Lỗi nghiệp vụ**

* RESTORE\_WINDOW\_EXPIRED (409)

* INVALID\_STATE\_TRANSITION (409) — không ở REMOVED

##### GET /admin/categories — Tất cả danh mục (gồm ẩn)

**Quyền: category:manage    UC: E07**

**Response thành công**

200 → data: Category\[\] (kèm productCount)

##### POST /admin/categories — Thêm danh mục (Should)

**Quyền: category:manage    UC: E07**

**Request body (application/json)**

| Field | Kiểu | Bắt buộc | Validation / ghi chú |
| :---- | :---- | :---- | :---- |
| categoryName | string | ✓ | 1–150 ký tự, duy nhất (không phân biệt hoa thường) |
| status | CategoryStatus |  | Mặc định VISIBLE |

**Response thành công**

201 → data: Category

**Lỗi nghiệp vụ**

* CATEGORY\_NAME\_DUPLICATE (409)

##### PATCH /admin/categories/{categoryId} — Đổi tên / ẩn / hiện danh mục

**Quyền: category:manage    UC: E07**

* Danh mục bị ẩn không chọn được khi tạo/sửa sản phẩm, nhưng sản phẩm đã gắn vẫn hiển thị.

**Request body (application/json)**

| Field | Kiểu | Bắt buộc | Validation / ghi chú |
| :---- | :---- | :---- | :---- |
| categoryName | string |  |  |
| status | CategoryStatus |  |  |

**Response thành công**

200 → data: Category

**Lỗi nghiệp vụ**

* CATEGORY\_NAME\_DUPLICATE (409)

##### DELETE /admin/categories/{categoryId} — Xóa danh mục chưa có sản phẩm

**Quyền: category:manage    UC: E07**

**Response thành công**

204

**Lỗi nghiệp vụ**

* CATEGORY\_IN\_USE (409)

### 6.3 Cart Service

**Phạm vi:** giỏ hàng của Customer đã đăng nhập; tính trạng thái dòng; giảm đúng lượng đã mua sau khi thanh toán thành công (tiêu thụ event checkout.paid, chỉ khi source \= CART). **UC:** B03 (và một phần B06). Theo FRD, khách phải đăng nhập mới thêm giỏ — **không** triển khai giỏ khách vãng lai (xem C-03).

Việc **chọn dòng để thanh toán** là trạng thái phía FE: FE gửi danh sách cartItemIds đã chọn vào API checkout của Order, không lưu lựa chọn ở Cart.

#### Danh sách endpoint

| \# | Method | Path | Mô tả | Quyền | UC |
| :---- | :---- | :---- | :---- | :---- | :---- |
| 1 | GET | /cart | Xem giỏ hàng | cart:manage:own | B03 |
| 2 | GET | /cart/summary | Số lượng trên icon giỏ | cart:manage:own | B03 |
| 3 | POST | /cart/items | Thêm vào giỏ | cart:manage:own | B03 |
| 4 | PATCH | /cart/items/{cartItemId} | Đổi số lượng | cart:manage:own | B03 |
| 5 | DELETE | /cart/items/{cartItemId} | Xóa dòng khỏi giỏ | cart:manage:own | B03 |

#### DTO

| DTO | Field |
| :---- | :---- |
| Cart | cartId, groups: \[{ seller { sellerId, sellerName }, items: CartItem\[\] }\], removedItems: \[{ productId, productName, reason }\], summary { totalQuantity, lineCount }, paymentBlocked |
| CartItem | cartItemId, productId, productName, coverImageUrl, unitPriceVnd (giá hiện tại), quantity, availableQuantity, lineStatus, selectable, lineTotalVnd |

#### Chi tiết endpoint

##### GET /cart — Xem giỏ hàng

**Quyền: cart:manage:own    UC: B03**

* Mỗi lần xem, Cart lấy giá hiện tại, trạng thái bán được (Product Service) và tồn khả dụng (Order) để tính lineStatus (DB 3.3.2). Không lưu trạng thái này.

* Dòng có sản phẩm ngừng bán bị xóa khỏi giỏ và trả trong removedItems để FE hiển thị thông báo.

* Dòng được nhóm theo Seller; mỗi nhóm sẽ thành một Order khi checkout.

**Response thành công**

200 → data: Cart

```json
{ "data": {
    "cartId": "c91e...",
    "groups": [
      { "seller": { "sellerId": "5d2a...", "sellerName": "Shop Áo Xinh" },
        "items": [
          { "cartItemId": "ci-01", "productId": "8b1f...", "productName": "Áo thun basic cotton",
            "coverImageUrl": "https://cdn...", "unitPriceVnd": 150000, "quantity": 2,
            "availableQuantity": 48, "lineStatus": "IN_STOCK", "selectable": true, "lineTotalVnd": 300000 },
          { "cartItemId": "ci-02", "productId": "a71c...", "productName": "Quần jean slim",
            "coverImageUrl": "https://cdn...", "unitPriceVnd": 320000, "quantity": 3,
            "availableQuantity": 2, "lineStatus": "INSUFFICIENT_STOCK", "selectable": false, "lineTotalVnd": 960000 } ] } ],
    "removedItems": [ { "productId": "e44b...", "productName": "Mũ lưỡi trai", "reason": "PRODUCT_NOT_SELLABLE" } ],
    "summary": { "totalQuantity": 5, "lineCount": 2 },
    "paymentBlocked": false } }
```

##### GET /cart/summary — Số lượng trên icon giỏ

**Quyền: cart:manage:own    UC: B03**

* Nhẹ, không gọi service khác. Dùng cho navbar.

**Response thành công**

200 → data: { lineCount, totalQuantity }

##### POST /cart/items — Thêm vào giỏ

**Quyền: cart:manage:own    UC: B03**

* P-10: mỗi dòng 1–99 đơn vị; giỏ tối đa 50 sản phẩm khác nhau. Sản phẩm đã có cộng vào dòng cũ, không vượt 99; không giữ hàng. Kiểm tra giới hạn cả khi thêm đồng thời; checkout kiểm tra tồn thực tế.

* Cho phép thêm sản phẩm đang hết hàng (FRD UC-B01), nhưng không thêm sản phẩm không bán được.

* Thiếu cart:manage:own/CUSTOMER →403; User có SELLER hoặc ADMIN cùng CUSTOMER hợp lệ vẫn thêm giỏ được. Chưa login →401. customerId chủ giỏ phải bằng sub.

**Request body (application/json)**

| Field | Kiểu | Bắt buộc | Validation / ghi chú |
| :---- | :---- | :---- | :---- |
| productId | uuid | ✓ |  |
| quantity | int | ✓ | 1–99; tổng sau cộng dồn ≤99 (P-10) |

**Response thành công**

200 → data: Cart (giỏ đầy đủ sau khi thêm)

**Lỗi nghiệp vụ**

* PRODUCT\_NOT\_SELLABLE (422)

* QUANTITY\_LIMIT\_EXCEEDED (422)

##### PATCH /cart/items/{cartItemId} — Đổi số lượng

**Quyền: cart:manage:own    UC: B03**

**Request body (application/json)**

| Field | Kiểu | Bắt buộc | Validation / ghi chú |
| :---- | :---- | :---- | :---- |
| quantity | int | ✓ | 1–99 (P-10) |

**Response thành công**

200 → data: Cart

**Lỗi nghiệp vụ**

* NOT\_FOUND (404)

* QUANTITY\_LIMIT\_EXCEEDED (422)

##### DELETE /cart/items/{cartItemId} — Xóa dòng khỏi giỏ

**Quyền: cart:manage:own    UC: B03**

**Response thành công**

200 → data: Cart

**Lỗi nghiệp vụ**

* NOT\_FOUND (404)

### 6.4 Order Service (Order Inventory Fulfillment)

**Phạm vi:** checkout và tách đơn theo cửa hàng, giữ hàng, vòng đời đơn của Customer và Seller, tồn kho và lịch sử kho, số liệu đơn, job J1/J3/J4. **UC:** B04, B06, B07, B08, B09, B10, D02, D03, một phần D04, E02, E08.

#### Luồng chính của Customer

Từ giỏ (`CART`) hoặc Mua ngay (`BUY_NOW`), khách gọi Preview, rồi `POST /orders/checkout/pay`. Order giữ hàng, điều phối Payment lấy link để khách xác nhận tại provider. FE tra cứu phiên tại Order. Chỉ hiển thị thành công sau khi Order áp dụng `PAID` (§8.1).

#### State machine của Order (DB 8.2B)

| Từ | Sự kiện / API | Sang |
| :---- | :---- | :---- |
| AWAITING\_PAYMENT | payment.succeeded được Order áp dụng trước expiresAt | PAID |
| AWAITING\_PAYMENT | payment.failed xác định trước hạn / ExpireCheckout hoặc xử lý kết quả từ expiresAt | PAYMENT\_FAILED / EXPIRED |
| PAID | start-packing (Seller) | PACKING |
| PAID | cancel (Customer) | CANCELLED → Refund |
| PAID, PACKING | reject Seller / J4 quá 7 ngày / Order chứng minh invalid giao dịch khi apply Payment SUCCESS (§8.1) | CANCELLED → Refund riêng Order; ban sau capture không là trigger |
| PACKING | mark-delivered (Seller) | DELIVERED |
| DELIVERED | confirm-receipt (Customer) / J3 quá 7 ngày | CONFIRMED → quyết toán |
| CONFIRMED, CANCELLED | — | Kết thúc, không chuyển tiếp |

#### Danh sách endpoint

| \# | Method | Path | Mô tả | Quyền | UC |
| :---- | :---- | :---- | :---- | :---- | :---- |
| 1 | POST | /orders/checkout/preview | Xem trước trang Thanh toán | checkout:create:own | B04, B10 |
| 2 | POST | /orders/checkout/pay | Giữ hàng, tạo phiên và nhận link thanh toán | checkout:create:own | B04, B05, B10 |
| 3 | GET | /orders/checkout-sessions/current | Phiên đang chờ thanh toán (nếu có) | order:read:own | B04, B05 |
| 4 | GET | /orders/checkout-sessions/{checkoutSessionId} | Chi tiết phiên thanh toán | order:read:own | B05, B07 |
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

#### DTO

| DTO | Field |
| :---- | :---- |
| CheckoutPreview | source, orders: \[{ seller, items: \[{ productId, productName, coverImageUrl, unitPriceVnd, quantity, lineTotalVnd }\], orderTotalVnd }\], shippingFeeVnd (0), totalAmountVnd, defaultAddress: Address \| null, issues: \[{ productId, cartItemId, code, availableQuantity }\], blockers: \[{ code, ... }\], canPlaceOrder, quoteToken, isSimulated |
| CheckoutSession | checkoutSessionId, source, status, closeReason, paymentStatus (NONE \| PENDING \| SUCCESS \| FAILED), paymentUrl (null khi chưa có hoặc không còn dùng được), totalAmountVnd, shippingFeeVnd, expiresAt, paidAt, shippingAddress (snapshot), orders: OrderSummary\[\], allowedActions, isSimulated |
| ShippingAddress | recipientName, recipientPhone, provinceCode, provinceName, wardCode, wardName, addressDetail |
| OrderSummary | orderId, checkoutSessionId, seller { sellerId, sellerName }, status, totalAmountVnd, itemCount, firstItem { productName, coverImageUrl }, createdAt, paidAt, refundStatus (null \| PENDING \| SUCCESS \| FAILED) |
| OrderItem | orderItemId, productId, productName, coverImageUrl, unitPriceVnd, quantity, lineTotalVnd |
| Cancellation | cancelledByType (CancelledBy), reasonCode, reasonNote, cancelledAt (DB: cancel\_reason\_code, cancel\_reason\_note, cancelled\_by\_type, cancelled\_at); reasonCode null khi hệ thống hủy |
| CustomerOrderDetail | OrderSummary \+ items: OrderItem\[\], shippingAddress, sellerActionDueAt, deliveredAt, customerConfirmDueAt, confirmedAt, cancellation\|null, refund\|null, refundPendingCreation, reviewSummary: \[{ orderItemId, productId, hasReviewed, reviewId\|null, canReview }\], timeline\[\], allowedActions, isSimulated. reviewSummary lấy từ Product Service nội bộ và Order CONFIRMED. |
| SellerOrderSummary | orderId, checkoutSessionId, status, totalAmountVnd, items (tên \+ số lượng), paidAt, sellerActionDueAt, customerConfirmDueAt, allowedActions |
| SellerOrderDetail | SellerOrderSummary \+ shippingAddress, items: OrderItem\[\], commissionBps, commissionAmountVnd, sellerAmountVnd (tính theo DB 8.3 trên Order.total), deliveredAt, confirmedAt, cancellation, timeline, isSimulated |
| AdminOrderSummary | OrderSummary \+ customerId, customerName, sellerActionDueAt, customerConfirmDueAt |
| InventoryItem | productId, productName, onHandQuantity, reservedQuantity, availableQuantity, version, updatedAt |
| StockMovement | movementId, movementType (IMPORT \| MANUAL\_ADJUST \| SOLD \| RESTOCK), quantityDelta, orderItemId, orderId, actorType, note, createdAt |

#### Chi tiết endpoint

##### POST /orders/checkout/preview — Xem trước trang Thanh toán

**Quyền: checkout:create:own    UC: B04, B10**

* Không ghi dữ liệu, không giữ hàng. Dùng để render trang "Thanh toán / Xác nhận đặt hàng".

* Gom dòng hàng theo Seller (mỗi nhóm \= 1 đơn dự kiến), tính tổng từng đơn và tổng chung, kiểm tra tồn và trạng thái bán được, trả địa chỉ mặc định.

* Với BUY_NOW, Order đọc địa chỉ mặc định từ `GET /api/v1/identity/customers/me/addresses` bằng Bearer của Customer và tên Seller từ `GET /api/v1/identity/sellers/{sellerId}`. Quote chỉ được cấp khi có thể xác nhận và có hạn 5 phút kể từ lúc Preview cấp.

* Trả 200 kể cả khi có dòng lỗi; lỗi nằm trong issues\[\] và canPlaceOrder \= false. Mọi điều kiện được kiểm tra lại ở bước tạo phiên.

**Request body (application/json)**

| Field | Kiểu | Bắt buộc | Validation / ghi chú |
| :---- | :---- | :---- | :---- |
| source | CheckoutSource | ✓ | CART hoặc BUY\_NOW |
| cartItemIds | uuid\[\] | nếu CART | ≥ 1 dòng đã chọn trong giỏ |
| buyNowItem | object | nếu BUY\_NOW | { productId, quantity }; 1 ≤ quantity ≤ min(khả dụng, 99), không tạo/đọc/trừ dòng giỏ (P-10) |

**Response thành công**

200 → data: CheckoutPreview

```text
POST /api/v1/orders/checkout/preview
{ "source": "CART", "cartItemIds": ["ci-01", "ci-07"] }
```

```text
200 OK
{ "data": {
    "source": "CART",
    "orders": [
      { "seller": { "sellerId": "5d2a...", "sellerName": "Shop Áo Xinh" },
        "items": [ { "productId": "8b1f...", "productName": "Áo thun basic cotton", "coverImageUrl": "...",
                     "unitPriceVnd": 150000, "quantity": 2, "lineTotalVnd": 300000 } ],
        "orderTotalVnd": 300000 },
      { "seller": { "sellerId": "9e7c...", "sellerName": "Giày Việt" },
        "items": [ { "productId": "f02d...", "productName": "Sneaker trắng", "coverImageUrl": "...",
                     "unitPriceVnd": 450000, "quantity": 1, "lineTotalVnd": 450000 } ],
        "orderTotalVnd": 450000 } ],
    "shippingFeeVnd": 0,
    "totalAmountVnd": 750000,
    "defaultAddress": { "addressId": "ad-3", "recipientName": "Nguyễn Văn An", "...": "..." },
    "issues": [],
    "blockers": [],
    "canPlaceOrder": true,
    "isSimulated": true } }
```

**Lỗi nghiệp vụ**

* blockers\[\] (trong response 200, không phải lỗi HTTP): PROFILE\_INCOMPLETE (thiếu SĐT/địa chỉ), CHECKOUT\_PENDING\_EXISTS, CUSTOMER\_PAYMENT\_BLOCKED

* issues\[\] theo dòng: PRODUCT\_NOT\_SELLABLE, INSUFFICIENT\_STOCK (kèm availableQuantity), QUANTITY\_LIMIT\_EXCEEDED

##### POST /orders/checkout/pay — Tạo checkout và lấy link thanh toán

Quyền `checkout:create:own`. Bắt buộc `Idempotency-Key`. Body gồm `quoteToken` và `addressId`; danh sách hàng, số lượng và nguồn lấy từ quote đã xác minh. Quote có chữ ký gắn Customer và một `checkoutIntentId`, không chứa PII. Nếu giá hoặc tập hàng thay đổi, yêu cầu Preview và xác nhận lại. Order tự tính tổng tiền. CheckoutPreview trả quoteToken khi có thể xác nhận; quote gắn nguồn, dòng hàng/số lượng/giá, cartItemId nếu CART, tổng tiền và hạn quote.

Trong một transaction Order, tạo phiên và N đơn theo Seller, giữ đủ hàng, lưu công việc tạo giao dịch và `ExpireCheckout`. Giữ một phiên chờ mỗi Customer và một phiên trên mỗi `(customerId, checkoutIntentId)`. Giữ kiểm tra địa chỉ hiện hành và cập nhật `lastUsedAt` qua Identity sau commit, idempotent theo mã phiên.

- `202` với CheckoutSession khi phiên còn chờ và chưa có link; trả `200` cùng DTO khi link đã có hoặc phiên đã kết thúc. Khách dùng API tra cứu phiên để lấy link hoặc kết quả hiện hành.
- Với BUY_NOW, đối chiếu nội dung bằng hash có phiên bản từ `checkoutIntentId`, nguồn, `productId`, `sellerId`, số lượng, đơn giá và tổng VND, `addressId`. Quote ký lại đúng Customer, purpose và phiên bản với cùng ý định/nội dung có thể trả lại cùng phiên. Cùng key khác nội dung → `409 IDEMPOTENCY_KEY_REUSED`. Key mới dùng ý định cũ với nội dung khác, hoặc key và ý định trỏ hai phiên khác nhau → `409 CHECKOUT_INTENT_CONFLICT`. Không đổi mapping cũ khi xung đột.
- Sau kiểm tra chữ ký, quyền hiện hành, chủ phiên và nội dung, replay trả phiên cũ dù quote hết hạn hoặc địa chỉ gốc đã xóa. Dùng snapshot địa chỉ và tên Seller; ghi alias khi dùng key mới cho cùng ý định. Phiên `CLOSED/EXPIRED` trả `200` và `paymentUrl=null`; phiên còn lưu `AWAITING_PAYMENT` tại hoặc sau hạn trả `202` và link null. Replay không gia hạn, giữ hàng thêm hay tự đóng phiên.
- Pay mới kiểm tra quote còn hạn, Customer ACTIVE, số điện thoại và địa chỉ hiện hành thuộc Customer. Báo `CHECKOUT_QUOTE_INVALID`, `CHECKOUT_QUOTE_EXPIRED`, `CHECKOUT_QUOTE_CHANGED`, `CUSTOMER_PAYMENT_BLOCKED`, `PROFILE_INCOMPLETE`, `CHECKOUT_ITEMS_INVALID` hoặc `CHECKOUT_PENDING_EXISTS` theo nguyên nhân. Nếu nguồn bắt buộc timeout, lỗi hoặc thiếu dữ liệu để kết luận, trả `503 SERVICE_UNAVAILABLE`. Khách chủ động mua lại bắt đầu bằng Preview mới. Số tiền hoặc URL từ client không được dùng để tạo giao dịch.

```http
POST /api/v1/orders/checkout/pay
Idempotency-Key: 5f0c2b1a-7d3e-4e1b-9a55-2c1d9b7e0f11
Content-Type: application/json

{"quoteToken":"<signed quote>","addressId":"dd2d93f9-dc54-4a68-8350-13a9c1d7a23f"}
```

##### GET /orders/checkout-sessions/current — Phiên đang chờ thanh toán (nếu có)

**Quyền: order:read:own    UC: B04, B05**

**Response thành công**

200 → data: CheckoutSession \| null

##### GET /orders/checkout-sessions/{checkoutSessionId} — Chi tiết phiên thanh toán

**Quyền: order:read:own    UC: B05, B07**

* paymentStatus lấy từ Payment (internal). FE hiển thị đang xác nhận khi Order chưa áp dụng kết quả. Khi phiên còn mở, chưa đến expiresAt và đúng Customer ACTIVE, link đã sẵn sàng thì trả paymentUrl và hành động OPEN_PAYMENT_LINK. Các trường hợp còn lại trả paymentUrl=null. Không có nút hủy phiên chờ.
* Khi Payment tạm lỗi và dữ liệu của Payment cần để trả chi tiết phiên, Order trả 503 SERVICE_UNAVAILABLE theo envelope chung. Frontend giữ màn hình, ẩn thao tác mở link khi chưa xác minh được và polling lại cùng checkoutSessionId. Hiển thị “Đang kiểm tra trạng thái thanh toán”. Lỗi đọc không đổi trạng thái phiên và không kích hoạt Pay mới.

**Response thành công**

200 → data: CheckoutSession

**Lỗi nghiệp vụ**

* NOT\_FOUND (404)



##### GET /orders/me — Đơn mua của tôi

**Quyền: order:read:own    UC: B07**

* Mỗi Order một dòng; kèm checkoutSessionId để khách biết các đơn được trả cùng lần.

**Tham số (path / query)**

| Tên | Vị trí | Kiểu | Bắt buộc | Mô tả / ràng buộc |
| :---- | :---- | :---- | :---- | :---- |
| status | query | OrderStatus\[\] (CSV) |  | Xem bảng tab ở 4.2 |
| checkoutSessionId | query | uuid |  | Lọc các đơn cùng lần thanh toán |
| q | query | string |  | Tên cửa hàng; hoặc orderId / checkoutSessionId (UUID đầy đủ) |
| page, size | query |  |  | sort mặc định createdAt,desc |

**Response thành công**

200 → data: OrderSummary\[\] \+ meta

##### GET /orders/me/{orderId} — Chi tiết đơn (Customer)

**Quyền: order:read:own    UC: B05, B07, B08, B09, C01**

* allowedActions là gợi ý UI được tính từ permission hiện hành \+ ownership \+ trạng thái đơn \+ G-14; API thao tác luôn kiểm tra lại. Có PAY, CANCEL\_SESSION, CANCEL, CONFIRM\_RECEIPT, REVIEW tùy điều kiện.

* Khối hoàn tiền lấy từ Payment (internal) theo orderId.

**Response thành công**

200 → data: CustomerOrderDetail

```json
{ "data": {
    "orderId": "od-2", "checkoutSessionId": "cs-71a...",
    "seller": { "sellerId": "9e7c...", "sellerName": "Giày Việt" },
    "status": "DELIVERED", "totalAmountVnd": 450000, "shippingFeeVnd": 0,
    "items": [ { "orderItemId": "oi-9", "productId": "f02d...", "productName": "Sneaker trắng",
                 "coverImageUrl": "...", "unitPriceVnd": 450000, "quantity": 1, "lineTotalVnd": 450000 } ],
    "shippingAddress": { "...": "snapshot lúc đặt" },
    "paidAt": "2026-10-18T08:32:10Z", "sellerActionDueAt": "2026-10-25T08:32:10Z",
    "deliveredAt": "2026-10-20T03:00:00Z", "customerConfirmDueAt": "2026-10-27T03:00:00Z",
    "confirmedAt": null, "cancellation": null, "refund": null,
    "timeline": [ { "status": "PAID", "at": "2026-10-18T08:32:10Z", "actor": "SYSTEM" },
                  { "status": "PACKING", "at": "2026-10-19T02:00:00Z", "actor": "SELLER" },
                  { "status": "DELIVERED", "at": "2026-10-20T03:00:00Z", "actor": "SELLER" } ],
    "allowedActions": ["CONFIRM_RECEIPT"],
    "isSimulated": true } }
```

**Lỗi nghiệp vụ**

* NOT\_FOUND (404) — không phải đơn của mình

##### POST /orders/me/{orderId}/cancel — Khách hủy đơn đã thanh toán

**Quyền: order:cancel:own    UC: B08**

* Chỉ khi Order ở PAID (chưa đóng gói). Trong một transaction: Order → CANCELLED, hoàn kho (StockMovement RESTOCK, một lần), ghi outbox order.cancelled (Payment tạo Refund \= tổng đơn; Finance chuyển Settlement sang PENDING\_REFUND; Notification báo khách \+ Seller).

* Đơn khác cùng lần thanh toán không bị ảnh hưởng. Gọi lại khi đã CANCELLED → 200\.

* Customer BANNED còn Order chưa hoàn tất chỉ được hủy chính Order đó nếu trạng thái cho phép; không hủy đơn đã hoàn tất hay dùng chức năng mua sắm khác.

* Phiên chưa thanh toán tự hết hạn và nhả hàng tại Order.

**Request body (application/json)**

| Field | Kiểu | Bắt buộc | Validation / ghi chú |
| :---- | :---- | :---- | :---- |
| reasonCode | CustomerCancelReason | ✓ | P-21a |
| reasonNote | string |  | ≤ 500 ký tự; bắt buộc nếu OTHER |

**Response thành công**

200 → data: CustomerOrderDetail; refund có thể null trong lúc event đang được xử lý. Nếu trả null, kèm refundPendingCreation=true; poll chi tiết để lấy Refund thật, không giả định đã tạo Refund đồng bộ.

**Lỗi nghiệp vụ**

* ORDER\_NOT\_CANCELLABLE (409) — đã PACKING/DELIVERED/CONFIRMED

* PAYMENT\_IN\_PROGRESS (409)

##### POST /orders/me/{orderId}/confirm-receipt — Đã nhận hàng

**Quyền: order:confirm:own    UC: B09**

* Chỉ khi DELIVERED. Order → CONFIRMED, ghi confirmedAt, phát order.confirmed → Finance quyết toán (UC-G04), Notification.

* Tranh chấp với J3: cập nhật có điều kiện, chỉ một kết quả. Gọi lại khi đã CONFIRMED → 200\.

**Response thành công**

200 → data: CustomerOrderDetail

**Lỗi nghiệp vụ**

* INVALID\_STATE\_TRANSITION (409)

##### GET /orders/seller/orders — Đơn của cửa hàng tôi

**Quyền: order:fulfill:own    UC: D02**

* Chỉ đơn có sellerId của mình và đã qua bước thanh toán (không hiển thị đơn AWAITING\_PAYMENT/PAYMENT\_FAILED/EXPIRED).

**Tham số (path / query)**

| Tên | Vị trí | Kiểu | Bắt buộc | Mô tả / ràng buộc |
| :---- | :---- | :---- | :---- | :---- |
| status | query | OrderStatus\[\] (CSV) |  |  |
| q | query | string |  | orderId (UUID đầy đủ) |
| dueBefore | query | datetime |  | Lọc đơn sắp hết hạn phản hồi (sellerActionDueAt trước mốc) |
| page, size, sort | query |  |  | sort: paidAt (mặc định desc), sellerActionDueAt,asc |

**Response thành công**

200 → data: SellerOrderSummary\[\] \+ meta

##### GET /orders/seller/orders/{orderId} — Chi tiết đơn (Seller)

**Quyền: order:fulfill:own    UC: D02, D04**

* Gồm thông tin người nhận (chỉ đơn của mình), sản phẩm, mốc thời gian, commissionBps snapshot, hoa hồng và số tiền dự kiến (C, S theo DB 8.3). Trạng thái tiền chi tiết lấy từ GET /finance/seller/settlements?orderId=.

**Response thành công**

200 → data: SellerOrderDetail

**Lỗi nghiệp vụ**

* NOT\_FOUND (404)

##### POST /orders/seller/orders/{orderId}/start-packing — Bắt đầu đóng gói

**Quyền: order:fulfill:own    UC: D02**

* PAID → PACKING. Sau bước này khách không tự hủy được nữa. Hạn 7 ngày không thay đổi.

**Response thành công**

200 → data: SellerOrderDetail

**Lỗi nghiệp vụ**

* INVALID\_STATE\_TRANSITION (409)

* SELLER\_DEADLINE\_PASSED (409)

##### POST /orders/seller/orders/{orderId}/mark-delivered — Xác nhận đã giao

**Quyền: order:fulfill:own    UC: D02**

* PACKING → DELIVERED trước sellerActionDueAt. Ghi deliveredAt, customerConfirmDueAt \= deliveredAt \+ 7 ngày. Không có trạng thái "Đang giao".

* Phát order.delivered → Notification báo khách.

* Không có body; MVP không lưu mã vận đơn (C-06).

**Response thành công**

200 → data: SellerOrderDetail

**Lỗi nghiệp vụ**

* INVALID\_STATE\_TRANSITION (409) — phải ở PACKING

* SELLER\_DEADLINE\_PASSED (409)

##### POST /orders/seller/orders/{orderId}/reject — Từ chối giao hàng (Must)

**Quyền: order:fulfill:own    UC: D02, G02**

* Cho phép ở PAID hoặc PACKING, trước khi xác nhận đã giao. Order → CANCELLED (cancelledBy \= SELLER), hoàn kho, phát order.cancelled (Refund lý do SELLER\_REJECT, Finance chuyển Settlement sang PENDING\_REFUND, Notification).

* Tranh chấp với J4: chỉ một kết quả, không hoàn hai lần.

**Request body (application/json)**

| Field | Kiểu | Bắt buộc | Validation / ghi chú |
| :---- | :---- | :---- | :---- |
| reasonCode | SellerRejectReason | ✓ | P-21b |
| reasonNote | string | ✓ | 1–500 ký tự (lý do bắt buộc rõ ràng) |

**Response thành công**

200 → data: SellerOrderDetail

**Lỗi nghiệp vụ**

* INVALID\_STATE\_TRANSITION (409) — đã DELIVERED/CONFIRMED/CANCELLED

* SELLER\_DEADLINE\_PASSED (409)

##### GET /orders/seller/inventory — Tồn kho sản phẩm của tôi

**Quyền: inventory:manage:own    UC: D03**

**Tham số (path / query)**

| Tên | Vị trí | Kiểu | Bắt buộc | Mô tả / ràng buộc |
| :---- | :---- | :---- | :---- | :---- |
| productIds | query | uuid\[\] (CSV) |  | Tối đa 100; bỏ trống \= tất cả (phân trang) |
| page, size | query |  |  |  |

**Response thành công**

200 → data: InventoryItem\[\]

Cảnh báo tồn thấp khi availableQuantity ≤5 (P-16), không lấy onHandQuantity để bỏ qua lượng đang giữ; không thay điều kiện kiểm tra tồn khi checkout.

##### PUT /orders/seller/inventory/{productId} — Cập nhật tồn kho

**Quyền: inventory:manage:own    UC: D03**

* Đặt tồn thực tế mới. Ghi StockMovement MANUAL\_ADJUST với delta. Không cho thấp hơn số đang giữ.

**Request body (application/json)**

| Field | Kiểu | Bắt buộc | Validation / ghi chú |
| :---- | :---- | :---- | :---- |
| onHandQuantity | int | ✓ | Số nguyên ≥ reservedQuantity |
| version | int | ✓ | Từ lần đọc gần nhất |
| note | string |  | Ghi chú nhập/kiểm kê |

**Response thành công**

200 → data: InventoryItem

**Lỗi nghiệp vụ**

* STOCK\_BELOW\_RESERVED (422) — details\[0\].minAllowed

* VERSION\_CONFLICT (409)

* NOT\_FOUND (404)

##### GET /orders/seller/inventory/{productId}/movements — Lịch sử biến động kho

**Quyền: inventory:manage:own    UC: D03**

**Tham số (path / query)**

| Tên | Vị trí | Kiểu | Bắt buộc | Mô tả / ràng buộc |
| :---- | :---- | :---- | :---- | :---- |
| page, size | query |  |  | sort createdAt,desc |

**Response thành công**

200 → data: StockMovement\[\] \+ meta

##### GET /orders/seller/stats — Kết quả kinh doanh theo kỳ

**Quyền: order:fulfill:own    UC: D04**

* Phần số đơn và sản phẩm của UC-D04. Phần tiền (hoa hồng, được nhận, trạng thái tiền) lấy từ Finance.

* Chỉ tính Order.totalAmountVnd của Seller đang xem, không tính toàn bộ Payment chung.

**Tham số (path / query)**

| Tên | Vị trí | Kiểu | Bắt buộc | Mô tả / ràng buộc |
| :---- | :---- | :---- | :---- | :---- |
| from, to | query | date | ✓ | P-14 |
| topLimit | query | int |  | Mặc định 5 |

**Response thành công**

200 → data: { paidOrderCount, confirmedOrderCount, confirmedValueVnd, cancelledOrderCount, cancelledValueVnd, topProducts: \[{ productId, productName, quantitySold }\] }. P-15 chốt bán chạy theo số lượng và phải trên 500 lượt bán; đúng 500 chưa đạt. Không tự đặt kỳ/trạng thái đơn được tính; endpoint phân tích vẫn ngoài phần bắt buộc MVP.

##### GET /orders/seller/dashboard — Khối "Việc cần làm" của Seller

**Quyền: order:fulfill:own    UC: D02, D03**

**Tham số (path / query)**

| Tên | Vị trí | Kiểu | Bắt buộc | Mô tả / ràng buộc |
| :---- | :---- | :---- | :---- | :---- |
| dueWithinHours | query | int |  | Mặc định 24; P-36 cảnh báo khi thời gian xử lý còn không quá 24 giờ trước hạn 7 ngày |

**Response thành công**

200 → data: { paidCount, packingCount, dueSoonCount, outOfStockProductCount }. Số sản phẩm bị gỡ lấy từ Product Service (status=REMOVED).

##### GET /orders/admin/orders — Tra cứu đơn toàn hệ thống

**Quyền: operations:read:any    UC: E08, E01**

**Tham số (path / query)**

| Tên | Vị trí | Kiểu | Bắt buộc | Mô tả / ràng buộc |
| :---- | :---- | :---- | :---- | :---- |
| orderId | query | uuid |  |  |
| checkoutSessionId | query | uuid |  | Các đơn cùng một phiên thanh toán |
| sellerId, customerId | query | uuid |  |  |
| status | query | OrderStatus\[\] |  |  |
| overdue | query | SELLER \| CUSTOMER |  | Đơn quá hạn 7 ngày chưa được J4/J3 xử lý |
| page, size, sort | query |  |  |  |

**Response thành công**

200 → data: AdminOrderSummary\[\] \+ meta

##### GET /orders/admin/orders/{orderId} — Chi tiết đơn (Admin)

**Quyền: operations:read:any    UC: E08**

* Payment chung và Refund lấy từ GET /payments/admin/payments/by-checkout/{checkoutSessionId}; Settlement từ GET /finance/admin/settlements?orderId=.

**Response thành công**

200 → data: SellerOrderDetail \+ { customer { customerId, fullName, email }, checkoutSession { ... } }

##### GET /orders/admin/stats — Tiến độ đơn và hàng chờ (tổng quan Admin)

**Quyền: operations:read:any    UC: E02**

**Tham số (path / query)**

| Tên | Vị trí | Kiểu | Bắt buộc | Mô tả / ràng buộc |
| :---- | :---- | :---- | :---- | :---- |
| from, to | query | date | ✓ |  |

**Response thành công**

200 → data: { countByStatus: { PAID, PACKING, DELIVERED, CONFIRMED, CANCELLED }, overdueSellerCount, overdueCustomerCount, expiredCheckoutCount }

### 6.5 Payment Service

**Phạm vi:** một Payment chung/CheckoutSession, Refund ORDER hoặc FULL_CHECKOUT, webhook provider và đối soát theo J2/J5; Customer chủ Payment mở chu kỳ hoàn mới, Admin chỉ đọc Refund. **UC:** B05, B07, G02, một phần B06, E02, E08.

#### Danh sách endpoint

| \# | Method | Path | Mô tả | Quyền | UC |
| :---- | :---- | :---- | :---- | :---- | :---- |
| 1 | POST | /payments/webhooks/demo-provider | Webhook kết quả thu và hoàn | Chữ ký provider | B05, G02 |
| 2 | GET | /payments/by-checkout/{checkoutSessionId} | Trạng thái thanh toán của phiên | order:read:own | B05, B07 |
| 3 | GET | /payments/admin/issues | Việc cần xử lý về tiền (Must) | operations:read:any | E08, E02 |
| 4 | GET | /payments/admin/payments/by-checkout/{checkoutSessionId} | Payment chung và các Refund | operations:read:any | E08 |
| 5 | GET | /payments/admin/refunds | Danh sách khoản hoàn | operations:read:any | E08 |
| 6 | GET | /payments/refunds | Refund của chính Customer theo checkoutSessionId | order:read:own | B07, G02 |
| 7 | POST | /payments/refunds/{refundId}/retry | Customer mở chu kỳ hoàn mới | refund:retry:own | B07, G02 |
| 8 | POST | /payments/admin/checkout-sessions/{checkoutSessionId}/mark-handled | Ghi chú đối soát khoản thu, không Refund | operations:annotate | E08 |
| 9 | GET | /payments/admin/summary | Số liệu thu / hoàn | operations:read:any | E02 |

#### DTO

| DTO | Field |
| :---- | :---- |
| Payment | paymentId, checkoutSessionId, amountVnd, currency, provider, status, collectionClosedAt, providerTransactionId, errorCode, refundedAmountVnd, refundState, createdAt, completedAt, isSimulated |
| Refund | refundId, paymentId, orderId (null nếu FULL\_CHECKOUT), scope, amountVnd, reason, status, retryCycle, retryCount (retry tự động trong chu kỳ, không gồm lần đầu/query), customerRetryRequired, canRetryRefund (server suy ra), providerRefundId, createdAt, completedAt, isSimulated |

#### Chi tiết endpoint

##### POST /payments/webhooks/demo-provider — Nhận kết quả của provider

Gateway chuyển đúng route này tới Payment và giữ nguyên body. Provider dùng `X-Webhook-Timestamp` (epoch giây) và `X-Webhook-Signature` (HMAC-SHA256 dạng hex trên timestamp, dấu chấm và body gốc). Payment xác minh chữ ký bằng secret cấu hình riêng, kiểm tra timestamp và đối chiếu tham chiếu, số tiền, VND với dữ liệu bền vững trước khi áp dụng. Cửa sổ xác thực 5 phút; mỗi lần gửi lại ký timestamp mới với cùng `eventId`. Không dùng JWT Customer hay redirect của browser để xác nhận kết quả.

Envelope gồm `eventId, type, occurredAt, paymentId, providerTransactionId, amountVnd, currency, status, completedAt`; type là `payment.result` hoặc `refund.result`. Với refund, thêm `refundId, attemptId, providerRefundId`. Kết quả terminal mang `SUCCESS` hoặc `FAILED` và thời điểm hoàn tất; khi thất bại trả `errorCode`. Nếu provider xác định giao dịch hết hạn mà chưa thu, ghi `FAILED` với `errorCode=LINK_EXPIRED` và gửi webhook. Ghi hết hạn và ghi thu tại provider tranh cùng khóa. Khoản thu đã thành công trước hạn giữ `SUCCESS` dù webhook giao muộn. Đối chiếu riêng identifier và số tiền của refund với nghĩa vụ đã lưu.

Webhook có thể tới trước phản hồi tạo link. Nếu đã có ý định theo `paymentId` nhưng chưa lưu `providerTransactionId`, gắn mã giao dịch trong cùng transaction sau khi xác thực và đối chiếu. Nếu mã đã được gắn thì phải khớp. Không hạ `SUCCESS` đã ghi khi nhận sự kiện cũ.

Trả `204` sau khi lưu kết quả và hiệu ứng cần thiết hoặc nhận lại cùng sự kiện đã lưu. Chữ ký không hợp lệ → `401`; payload không hợp lệ → `400`; trùng ID với nội dung khác hoặc sai tham chiếu/số tiền → `409`, không cập nhật nghiệp vụ. Trả `503` nếu chưa thể ghi bền vững. Provider lưu Outbox cùng kết quả; gửi lại lỗi tạm thời hoặc mất phản hồi và cho vận hành phát lại sự kiện chưa giao được. Không ghi secret hoặc link thanh toán vào log.

##### GET /payments/by-checkout/{checkoutSessionId} — Trạng thái thanh toán của phiên

**Quyền: order:read:own    UC: B05, B07**

**Response thành công**

200 → data: Payment \| null (null nếu chưa thanh toán)

**Lỗi nghiệp vụ**

* NOT\_FOUND (404) — phiên không thuộc Customer

##### GET /payments/admin/issues — Việc cần xử lý về tiền (Must)

**Quyền: operations:read:any    UC: E08, E02**

* `PAYMENT_PENDING`: Payment PENDING từ 2 phút trở lên, checkout còn mở và chưa có link thanh toán hợp lệ. Chỉ gắn lý do webhook chậm khi có bằng chứng trực tiếp đã lưu.
* `REFUND_STUCK`: attempt hiện tại vẫn PENDING từ 30 phút trở lên tính từ lúc tạo attempt. Các ngưỡng chỉ dùng để hiển thị.
* Một dòng cho mỗi khoản thu hoặc nghĩa vụ Refund. Cờ đối soát ưu tiên `MANUAL_REVIEW`. Xác định loại cuối trước khi lọc và phân trang.

**Tham số (path / query)**

| Tên | Vị trí | Kiểu | Bắt buộc | Mô tả / ràng buộc |
| :---- | :---- | :---- | :---- | :---- |
| type | query | enum |  | PAYMENT\_PENDING \| REFUND\_FAILED \| REFUND\_STUCK \| MANUAL\_REVIEW |
| page, size | query |  |  |  |

**Response thành công**

200 → data: \[{ issueType, paymentId, refundId, checkoutSessionId, orderId, amountVnd, since, retryCount, displayReason, nextRetryAt }\] \+ meta

`displayReason` và `nextRetryAt` có thể null. Các mã lý do gồm `PAYMENT_LINK_DELAYED`, `WEBHOOK_DELAYED`, `REFUND_ATTEMPT_STUCK`, `REFUND_WAITING_AUTO_RETRY`, `REFUND_WAITING_CUSTOMER`, `REFUND_REVIEW_REQUIRED`, `COLLECTION_REVIEW_REQUIRED`. Chỉ trả lịch thử lại tự động đã lưu. Cờ review Refund vẫn chặn thử lại khi đã hết lượt.

##### GET /payments/admin/payments/by-checkout/{checkoutSessionId} — Payment chung và các Refund

**Quyền: operations:read:any    UC: E08**

**Response thành công**

200 → data: Payment \+ { refunds: Refund\[\], manualNote }

##### GET /payments/admin/refunds — Danh sách khoản hoàn

**Quyền: operations:read:any    UC: E08**

**Tham số (path / query)**

| Tên | Vị trí | Kiểu | Bắt buộc | Mô tả / ràng buộc |
| :---- | :---- | :---- | :---- | :---- |
| status | query | RefundStatus |  |  |
| orderId, paymentId | query | uuid |  |  |
| page, size | query |  |  |  |

**Response thành công**

200 → data: Refund\[\] \+ meta

##### GET /payments/refunds — Danh sách khoản hoàn của chính Customer

**Quyền: order:read:own    UC: B07, G02**

* Query checkoutSessionId UUID bắt buộc; xác minh checkout/Payment thuộc sub CUSTOMER, sai chủ trả 404. Chỉ đọc trạng thái đã lưu, không gọi gateway. Trả cả ORDER/FULL_CHECKOUT dù phiên đã đóng; Customer BANNED được đọc đúng nghĩa vụ. canRetryRefund do server suy ra từ quyền/ownership, FAILED và customerRetryRequired; PENDING/SUCCESS không mở nút retry.

**Response thành công**

200 → data: Refund\[\] \+ meta

##### POST /payments/refunds/{refundId}/retry — Customer mở chu kỳ hoàn mới

**Quyền: refund:retry:own    UC: B07, G02    Idempotency-Key: bắt buộc**

* Chỉ Customer chủ Payment qua checkout; hỗ trợ ORDER/FULL_CHECKOUT, kể cả Customer BANNED dùng OPEN_ORDERS_ONLY trên đúng nghĩa vụ. Sai chủ →404; Seller/Admin →403. Chỉ nhận khi Refund FAILED và customerRetryRequired=true.
* Body `{expectedRetryCycle: integer >=0}`; không nhận số tiền/key gateway từ client. Kiểm tra lại quyền/trạng thái rồi CAS/khóa Refund: tăng retryCycle, reset retryCount=0, xóa cờ chờ, chuyển PENDING và ghi công việc bền vững trong cùng local transaction. Không tạo Refund/số tiền/key gateway mới. Cùng HTTP key trả kết quả cũ; key khác với snapshot chu kỳ cũ →409 REFUND_RETRY_NOT_SAFE, không mở thêm chu kỳ.
* Khi Refund chờ đối soát vì kết quả provider mâu thuẫn, DTO trả `canRetryRefund=false`. Yêu cầu mở chu kỳ mới trả `409 REFUND_RETRY_NOT_SAFE`.
* Worker query trước phát lại. SUCCESS muộn áp dụng một lần nếu attempt còn chưa rõ. SUCCESS mâu thuẫn với FAILED của cùng attempt dừng gửi và chuyển đối soát theo §6.5.1. Chỉ FAILED xác định mới phát lệnh. Mỗi chu kỳ có một lần đầu và tối đa 3 retry sau 1/5/15 phút từ lỗi trước (P-RF-01), query không tính retry. Hết lượt giữ FAILED + customerRetryRequired=true, gửi thông báo Customer và chờ chu kỳ mới. Giữ toàn bộ lịch sử. Khoảng 21 phút là ước tính.
* Không có API Admin query/retry Refund, không shim route cũ, không API đánh dấu tay đã hoàn. Không xác minh được dependency thì giữ công việc/đối soát, không suy là chưa hoàn.

**Response thành công**

202 → data: Refund (status PENDING); chấp nhận mở chu kỳ, không xác nhận đã hoàn.

**Lỗi nghiệp vụ**

* REFUND\_RETRY\_NOT\_SAFE (409); NOT_FOUND (404); FORBIDDEN (403); IDEMPOTENCY_KEY_REQUIRED (400); IDEMPOTENCY_KEY_REUSED (409).

##### POST /payments/admin/checkout-sessions/{checkoutSessionId}/mark-handled — Đánh dấu đã xử lý trường hợp đối soát

**Quyền: operations:annotate    UC: E08**

* Chỉ ghi chú/gỡ cờ đối soát khoản THU của CheckoutSession, không áp dụng Refund/cờ chờ Customer; không đổi trạng thái tiền, không gọi gateway. Ghi AuditLog MARK_HANDLED.
* Payment cung cấp API công khai. Order sở hữu cờ và ghi chú CheckoutSession, cập nhật cùng audit trong một transaction. Order kiểm tra credential của Payment và Bearer Admin với `operations:annotate` hiện hành. Payment không tự thử lại lệnh ghi.

**Request body (application/json)**

| Field | Kiểu | Bắt buộc | Validation / ghi chú |
| :---- | :---- | :---- | :---- |
| note | string | ✓ | 1–1.000 ký tự |

**Response thành công**

200 → data: { checkoutSessionId, manualNote, handledAt }

##### GET /payments/admin/summary — Số liệu thu / hoàn

**Quyền: operations:read:any    UC: E02**

* Thu \= Payment SUCCESS (mỗi phiên đếm một lần); hoàn \= Refund SUCCESS; chờ hoàn \= Refund PENDING \+ FAILED (P-13); số dư chờ của đơn bị hủy chỉ giảm khi Refund SUCCESS (DB 8.3).

**Tham số (path / query)**

| Tên | Vị trí | Kiểu | Bắt buộc | Mô tả / ràng buộc |
| :---- | :---- | :---- | :---- | :---- |
| from, to | query | date | ✓ | Theo thời điểm hoàn tất |

**Response thành công**

200 → data: { collectedVnd, refundedVnd, pendingRefundVnd, pendingPaymentCount, failedRefundCount, isSimulated }

<a id="provider-demo"></a>
#### 6.5.1 Hợp đồng với provider demo

Provider là ứng dụng HTTPS độc lập. Các API dưới đây yêu cầu credential riêng của Payment. Browser chỉ mở trang xác nhận bằng link có token khó đoán, có hạn và gắn với giao dịch. Trang quay về trang chi tiết phiên của sàn bằng base URL đã cấu hình và checkoutSessionId đã lưu. Dữ liệu browser gửi qua redirect không xác nhận thu.

| API provider | Hợp đồng |
| --- | --- |
| `POST /v1/payments` | Nhận `paymentId, checkoutSessionId, amountVnd, currency, expiresAt`. Tạo một giao dịch duy nhất theo `paymentId`. Trả `providerTransactionId, status, expiresAt, paymentUrl`; giữ nguyên hạn và giao dịch khi replay cùng dữ liệu. Payload khác trên cùng mã → `409`. Từ `expiresAt` không tạo giao dịch mới. |
| `GET /v1/payments/{paymentId}` | Tra cứu bằng mã Payment của sàn kể cả khi phản hồi tạo link bị mất. Trả ID, số tiền, loại tiền, trạng thái, hạn, mã lỗi và thời điểm có kết quả; link chỉ trả trong hạn khi còn có thể xác nhận. |
| `GET /pay/{token}`, `POST /pay/{token}/confirm` | Hiển thị số tiền và hạn, sau đó xác nhận giả lập. Khóa giao dịch, kiểm tra hạn và chỉ ghi một khoản thu; gửi lại cùng xác nhận chỉ trả kết quả đã lưu. |
| `POST /v1/refunds` | Nhận `refundId, attemptId, paymentId, providerTransactionId, amountVnd, currency`. Một nghĩa vụ `refundId` chỉ hoàn thành công một lần. Cùng `attemptId` và nội dung trả cùng kết quả; nội dung khác hoặc attempt đã bị đóng bởi GET trả `409`. Chỉ mở lần thử mới sau thất bại xác định của lần cũ. Khóa giao dịch gốc và kiểm tra tổng hoàn. Trả `providerRefundId, status, amountVnd, currency, completedAt, errorCode`. |
| `GET /v1/refunds/{refundId}/attempts/{attemptId}` | Tra cứu cùng lần hoàn, kể cả sau khi phiên thu đóng. Attempt được nhận trả kết quả thực. Nếu xác nhận attempt chưa được nhận, provider ghi dấu đóng bền vững rồi trả HTTP 404 với JSON `{refundId, attemptId, providerRefundId, completedAt, errorCode: "REFUND_ATTEMPT_NOT_FOUND"}`. Dấu đóng dùng cùng khóa với POST và GET lặp lại trả cùng mã tham chiếu, cùng thời điểm đóng. |

`providerRefundId` trong 404 định danh bản ghi đóng attempt tại provider để đối soát. Payment chỉ dùng 404 nếu body khớp hai ID đã hỏi, có mã lỗi, mã tham chiếu và thời điểm đóng. Thuộc tính chưa rõ hoặc 404 chung tiếp tục query. `SUCCESS` mâu thuẫn với `FAILED` của cùng attempt dừng gửi hoàn cho Refund và chuyển đối soát theo ADR 0001. Webhook mâu thuẫn hợp lệ được ghi nhận và trả 204 để dừng giao lại.

Cấu hình test provider cho phép từ chối, mất phản hồi, trì hoãn webhook và lỗi hoàn. Kịch bản thu muộn là đã xác nhận tại provider trước hạn nhưng kết quả tới Order muộn. Bộ demo không cho browser chọn kịch bản bằng API checkout.

Khi nhận `checkout.closed`, Payment lưu dấu đóng theo checkout, dừng tạo giao dịch/cấp link/tự động tra cứu khoản thu. Nếu chưa có Payment thì dấu đóng vẫn được lưu để chặn tạo muộn. Nếu đã có `SUCCESS`, hoặc webhook hợp lệ đến sau xác nhận `SUCCESS`, cùng handler bảo đảm tạo một `FULL_CHECKOUT` và công việc gửi hoàn. Tiếp tục tra cứu **Refund** chưa rõ theo J5.

### 6.6 Finance Service

**Phạm vi:** Settlement theo đơn, ví Seller và ví nền tảng, bút toán append-only, rút tiền giả lập, tỷ lệ hoa hồng (P-COM-01), báo cáo tiền và đối soát. **UC:** D04 (phần tiền), D05, G04, một phần E02, E08.

| Sự kiện nhận | Tác động trong Finance (Database RBAC v5.0 mục 8.3) |
| :---- | :---- |
| checkout.paid | Với mỗi Order: tạo Settlement PENDING; WalletTransaction PAYMENT\_RECEIVED: ví Seller pending \+S, ví nền tảng pending \+C. |
| order.cancelled (đơn đã thanh toán) | Settlement → PENDING\_REFUND (khóa việc chuyển sang khả dụng). Chưa ghi bút toán; số dư chờ giữ nguyên đến khi hoàn tiền thành công (DB 3.5.1, 3.5.3). |
| refund.succeeded (scope ORDER) | Settlement → REFUNDED; WalletTransaction REFUND\_DEBIT: ví Seller pending −S, ví nền tảng pending −C. |
| order.confirmed | Settlement → SETTLED, chốt C và S; WalletTransaction SETTLEMENT\_RELEASED: Seller pending −S, available \+S; nền tảng pending −C, available (hoa hồng đã chốt) \+C. Phát funds.available. |
| seller.approved | Tạo ví Seller, mọi số dư \= 0\. |
| seller.banned/unbanned; user.disabled/enabled | isFrozen/canWithdraw tính từ User ACTIVE, accountType=SELLER, Seller APPROVED, available và accessMode. Seller APPROVED/BANNED có phiên giới hạn vẫn rút available; không tự isFrozen=true chỉ vì Seller BANNED. Mọi lệnh rút xác thực Identity hiện hành. |

#### Danh sách endpoint

| \# | Method | Path | Mô tả | Quyền | UC |
| :---- | :---- | :---- | :---- | :---- | :---- |
| 1 | GET | /finance/seller/wallet | Ví của tôi | wallet:read:own | D05, D04 |
| 2 | GET | /finance/seller/wallet/transactions | Lịch sử biến động ví | wallet:read:own | D05 |
| 3 | GET | /finance/seller/settlements | Tiền theo từng đơn | wallet:read:own | D04, D05 |
| 4 | POST | /finance/seller/withdrawals | Rút tiền (giả lập, tức thời) | withdrawal:create:own | D05 |
| 5 | GET | /finance/seller/withdrawals | Lịch sử rút tiền | wallet:read:own | D05 |
| 6 | GET | /finance/seller/withdrawals/{withdrawalId} | Chi tiết giao dịch rút | wallet:read:own | D05 |
| 7 | GET | /finance/admin/summary | Các ngăn tiền toàn hệ thống | operations:read:any | E02 |
| 8 | GET | /finance/admin/reconciliation | Kiểm tra bảo toàn tiền | operations:read:any | E02 |
| 9 | GET | /finance/admin/wallets | Tra cứu ví (chỉ xem) | operations:read:any | E08, E01 |
| 10 | GET | /finance/admin/wallets/{walletId}/transactions | Bút toán của một ví | operations:read:any | E08 |
| 11 | GET | /finance/admin/withdrawals | Giao dịch rút (chỉ xem) | operations:read:any | E08 |
| 12 | GET | /finance/admin/settlements | Quyết toán theo đơn | operations:read:any | E08 |

#### DTO

| DTO | Field |
| :---- | :---- |
| Wallet | walletId,ownerType,sellerId,sellerName(Admin),pendingBalanceVnd,availableBalanceVnd,totalWithdrawnVnd,minWithdrawalVnd (=10000),allowBelowMinimumFullWithdrawal:boolean,isFrozen (không chỉ vì ban),canWithdraw,withdrawDisabledReason,isSimulated |
| WalletTransaction | walletTransactionId, transactionType (WalletTransactionType), referenceType (PAYMENT \| ORDER \| REFUND \| WITHDRAWAL), referenceId, settlementId, withdrawalId, orderId (khi liên quan đơn), pendingDeltaVnd, availableDeltaVnd, withdrawnDeltaVnd, createdAt |
| Settlement | settlementId, orderId, paymentId, grossAmountVnd, commissionBpsSnapshot, commissionAmountVnd, sellerAmountVnd (tính khi tạo Settlement; khi PENDING là khoản đang chờ, chưa rút được), status, createdAt, settledAt, isSimulated |
| Withdrawal | withdrawalId, withdrawalCode, amountVnd, availableBeforeVnd, availableAfterVnd, destination, withdrawnAt, isSimulated |

#### Chi tiết endpoint

##### GET /finance/seller/wallet — Ví của tôi

**Quyền: wallet:read:own    UC: D05, D04**

* Ba số dư theo DB 3.5.2, 8.3: chờ quyết toán (không rút được), khả dụng (rút được), đã rút. pendingBalanceVnd \= Σ sellerAmount của Settlement PENDING và PENDING\_REFUND (gồm đơn đã hủy đang chờ hoàn tiền).

* canWithdraw và withdrawDisabledReason để FE bật/tắt nút và hiển thị giải thích đúng UC-D05.

**Response thành công**

200 → data: Wallet

```json
{ "data": { "walletId": "wl-5d2a", "ownerType": "SELLER", "sellerId": "5d2a...",
            "pendingBalanceVnd": 285000, "availableBalanceVnd": 190000, "totalWithdrawnVnd": 400000,
            "minWithdrawalVnd": 10000, "allowBelowMinimumFullWithdrawal": false, "isFrozen": false,
            "canWithdraw": true, "withdrawDisabledReason": null,
            "isSimulated": true } }
```

withdrawDisabledReason ∈ {null, BELOW\_MINIMUM\_PENDING\_EXISTS, BELOW\_MINIMUM, ACCOUNT\_DISABLED, SELLER\_NOT\_APPROVED, WALLET\_INCONSISTENT}. Seller BANNED không tự kích hoạt WALLET\_FROZEN.
Ngoại lệ WD-03/WD-09: allowBelowMinimumFullWithdrawal=true khi đủ quyền rút và accountStatus=BANNED, 0 < availableBalanceVnd < 10000. Khi đó canWithdraw=true, withdrawDisabledReason=null; UI khóa amount bằng toàn bộ available. Không rút pending; ACTIVE giữ ngưỡng 10000.

##### GET /finance/seller/wallet/transactions — Lịch sử biến động ví

**Quyền: wallet:read:own    UC: D05**

**Tham số (path / query)**

| Tên | Vị trí | Kiểu | Bắt buộc | Mô tả / ràng buộc |
| :---- | :---- | :---- | :---- | :---- |
| type | query | WalletTransactionType |  |  |
| from, to | query | date |  |  |
| page, size | query |  |  | sort createdAt,desc |

**Response thành công**

200 → data: WalletTransaction\[\] \+ meta

##### GET /finance/seller/settlements — Tiền theo từng đơn

**Quyền: wallet:read:own    UC: D04, D05**

* Mỗi đơn đã thanh toán một dòng: tổng đơn, hoa hồng, khoản được nhận, tình trạng tiền (chờ quyết toán / đã vào khả dụng / chờ hoàn / đã hoàn khách).

* Thời điểm dự kiến tự xác nhận của đơn đã giao không lưu ở Finance (Database RBAC v5.0); FE lấy customerConfirmDueAt từ GET /orders/seller/orders khi cần hiển thị.

**Tham số (path / query)**

| Tên | Vị trí | Kiểu | Bắt buộc | Mô tả / ràng buộc |
| :---- | :---- | :---- | :---- | :---- |
| status | query | SettlementStatus\[\] |  |  |
| orderId | query | uuid |  |  |
| from, to | query | date |  | Theo createdAt (thanh toán) hoặc settledAt khi lọc SETTLED |
| page, size | query |  |  |  |

**Response thành công**

200 → data: Settlement\[\] \+ meta \+ summary: { settledCommissionVnd, settledSellerAmountVnd, pendingSellerAmountVnd, refundedGrossVnd }

##### POST /finance/seller/withdrawals — Rút tiền (giả lập, tức thời)

**Quyền: withdrawal:create:own    UC: D05    Idempotency-Key: bắt buộc**

* Rút giả lập hoàn tất ngay theo WD-01…WD-05/WD-09: Seller APPROVED/ACTIVE hoặc APPROVED/BANNED đủ điều kiện phiên giới hạn được rút available, transaction trừ available và tăng totalWithdrawn, tạo Withdrawal \+ WalletTransaction, chống lặp; không tự chặn chỉ vì Seller BANNED.

* sellerId=sub thuộc accountType=SELLER; User ACTIVE, role SELLER hiệu lực, Seller APPROVED/ACTIVE HOẶC APPROVED/BANNED có phiên giới hạn theo G-14/WD-09 đều được rút available trong ngưỡng hợp lệ; kiểm tra lại trước transaction. USER DISABLED/PENDING/REJECTED bị chặn; không tin sellerId do client gửi.

* Phát withdrawal.completed → Notification gửi biên nhận.

* Database RBAC v5.0 không có Payout, ngăn reserved hay trạng thái Withdrawal: mỗi bản ghi Withdrawal là một lần rút đã thành công. Lỗi giữa chừng → rollback, không tạo bản ghi.

**Request body (application/json)**

| Field | Kiểu | Bắt buộc | Validation / ghi chú |
| :---- | :---- | :---- | :---- |
| amountVnd | int | ✓ | Số nguyên dương ≤ available; amountVnd >= 10000 hoặc (accountStatus == BANNED && 0 < availableBalanceVnd < 10000 && amountVnd == availableBalanceVnd). Kiểm tra lại quyền/số dư trong giao dịch; atomic debit + ledger + Withdrawal, concurrency/idempotency giữ nguyên. |

**Response thành công**

201 → data: Withdrawal (gửi lặp cùng key → 200, cùng giao dịch)

```text
POST /api/v1/finance/seller/withdrawals
Idempotency-Key: 9a0b7f31-4f0e-4a6f-8f3e-6c1b2d4e5a77
{ "amountVnd": 190000 }
```

```text
201 Created
{ "data": { "withdrawalId": "wd-01f...", "withdrawalCode": "RT-20261018-K2P9", "amountVnd": 190000,
            "availableBeforeVnd": 190000, "availableAfterVnd": 0,
            "destination": "Ví ngoài giả lập", "withdrawnAt": "2026-10-18T09:00:00Z",
            "isSimulated": true } }
```

**Lỗi nghiệp vụ**

* WITHDRAWAL\_BELOW\_MINIMUM (422): BANNED available=9500 rút 9500 hợp lệ, rút 5000 lỗi; ACTIVE rút 9500 vẫn lỗi.

* INSUFFICIENT\_AVAILABLE\_BALANCE (409) — kể cả khi hai tab rút đồng thời

* WALLET\_FROZEN (403) chỉ khi có khóa ví độc lập; KHÔNG dùng chỉ vì Seller BANNED.

* WALLET\_INCONSISTENT (409)

* IDEMPOTENCY\_KEY\_REUSED (409)

##### GET /finance/seller/withdrawals — Lịch sử rút tiền

**Quyền: wallet:read:own    UC: D05**

**Tham số (path / query)**

| Tên | Vị trí | Kiểu | Bắt buộc | Mô tả / ràng buộc |
| :---- | :---- | :---- | :---- | :---- |
| from, to | query | date |  |  |
| page, size | query |  |  |  |

**Response thành công**

200 → data: Withdrawal\[\] \+ meta

##### GET /finance/seller/withdrawals/{withdrawalId} — Chi tiết giao dịch rút

**Quyền: wallet:read:own    UC: D05**

**Response thành công**

200 → data: Withdrawal

##### GET /finance/admin/summary — Các ngăn tiền toàn hệ thống

**Quyền: operations:read:any    UC: E02**

* Số dư tại thời điểm xem (không trộn với số phát sinh theo kỳ — DB 8.3).

**Response thành công**

200 → data: { sellerPendingVnd, platformPendingVnd, commissionSettledVnd, sellerAvailableVnd, totalWithdrawnVnd, frozenWalletCount, frozenAvailableVnd, isSimulated }

##### GET /finance/admin/reconciliation — Kiểm tra bảo toàn tiền

**Quyền: operations:read:any    UC: E02**

* Finance đọc tổng thu/hoàn/chờ hoàn từ Payment, số liệu checkout PAID từ Order và các ngăn tiền cục bộ. Số liệu Order dùng giải thích tiến độ. `differenceVnd` bằng tổng thu trừ tổng đã hoàn, nghĩa vụ FULL_CHECKOUT chưa thành công, pending mọi ví, hoa hồng đã chốt, available Seller và tổng đã rút Seller. ORDER đang chờ hoàn đã nằm trong pending. Kiểm WD-11 và ledger từng ví.

**Response thành công**

200 → data: { status, checkedAt, sourceReadAt, missingSources, balanced, totals, differenceVnd, inconsistentWallets: [{ walletId, sellerId, differenceVnd }] }

* `COMPLETE` khi đủ nguồn, tổng cân và các ví khớp. `DIFFERENCE` khi đủ nguồn và có chênh lệch tổng hoặc ví lệch. Ví lệch vẫn được báo khi `differenceVnd=0`.
* `INCOMPLETE` khi nguồn thiếu, lỗi hoặc dữ liệu sai hợp đồng. Trả `balanced=false`, `differenceVnd=null` và danh sách nguồn thiếu. `sourceReadAt` và `totals` chỉ có khóa `ORDER`, `PAYMENT`, `FINANCE` tương ứng nguồn đọc được. Các lần đọc có thể quan sát chênh lệch tạm. Báo cáo chỉ đọc.

##### GET /finance/admin/wallets — Tra cứu ví (chỉ xem)

**Quyền: operations:read:any    UC: E08, E01**

**Tham số (path / query)**

| Tên | Vị trí | Kiểu | Bắt buộc | Mô tả / ràng buộc |
| :---- | :---- | :---- | :---- | :---- |
| ownerType | query | WalletOwnerType |  |  |
| sellerId | query | uuid |  |  |
| frozen | query | bool | | Lọc theo isFrozen thực tế; không đồng nghĩa Seller bị cấm |
| page, size | query |  |  |  |

**Response thành công**

200 → data: Wallet\[\] \+ meta

##### GET /finance/admin/wallets/{walletId}/transactions — Bút toán của một ví

**Quyền: operations:read:any    UC: E08**

**Response thành công**

200 → data: WalletTransaction\[\] \+ meta

##### GET /finance/admin/withdrawals — Giao dịch rút (chỉ xem)

**Quyền: operations:read:any    UC: E08**

**Tham số (path / query)**

| Tên | Vị trí | Kiểu | Bắt buộc | Mô tả / ràng buộc |
| :---- | :---- | :---- | :---- | :---- |
| sellerId | query | uuid |  |  |
| withdrawalCode | query | string |  |  |
| from, to | query | date |  |  |

**Response thành công**

200 → data: Withdrawal\[\] \+ meta

##### GET /finance/admin/settlements — Quyết toán theo đơn

**Quyền: operations:read:any    UC: E08**

**Tham số (path / query)**

| Tên | Vị trí | Kiểu | Bắt buộc | Mô tả / ràng buộc |
| :---- | :---- | :---- | :---- | :---- |
| orderId, sellerId | query | uuid |  |  |
| status | query | SettlementStatus |  |  |

**Response thành công**

200 → data: Settlement\[\] \+ meta

### 6.7 Notification Service

**Nhận event hoặc internal POST từ service được phép, tạo Notification và gửi email bất đồng bộ (Must), in-app (Should). User tự đọc/xóa thông báo của mình; không mở API public tạo thông báo. Xóa in-app không xóa email delivery, audit hay giao dịch. Admin không cần màn thông báo riêng trong MVP nhưng API tự phục vụ dùng cùng policy nếu có dữ liệu.**

| Event | Người nhận | NotificationType |
| :---- | :---- | :---- |
| checkout.paid | Customer (1 thông báo gồm checkoutSessionId, tổng tiền và danh sách đơn theo cửa hàng); mỗi Seller (đơn mới của mình) | CHECKOUT\_PAID, NEW\_ORDER |
| checkout.closed (closeReason: PAYMENT_FAILED / EXPIRED) | Customer | PAYMENT\_FAILED, CHECKOUT\_EXPIRED |
| order.delivered | Customer | ORDER\_DELIVERED |
| order.cancelled | Customer \+ Seller (theo cancelledBy, reason) | ORDER\_CANCELLED, ORDER\_REJECTED, ORDER\_AUTO\_CANCELLED |
| order.confirmed / funds.available | order.confirmed thông báo trạng thái; funds.available thông báo tiền sau khi Finance commit | ORDER\_CONFIRMED / ORDER\_AUTO\_CONFIRMED / FUNDS\_AVAILABLE |
| refund.succeeded | Customer | REFUND\_SUCCEEDED |
| refund.failed (retryExhausted=true) | Customer chủ Payment; ORDER và FULL_CHECKOUT | REFUND\_RETRY\_REQUIRED; email Must, in-app Should, một thông báo logic/refundId/retryCycle |
| withdrawal.completed | Seller (biên nhận) | WITHDRAWAL\_COMPLETED |
| seller.registered / approved / rejected | User sở hữu Seller; ngữ cảnh SELLER | SELLER\_REGISTERED / SELLER\_APPROVED / SELLER\_REJECTED |
| customer.banned / seller.banned / unbanned | Tài khoản tương ứng | ACCOUNT\_BANNED, ACCOUNT\_UNBANNED |
| product.removed | Seller | PRODUCT\_REMOVED |
| password.reset\_requested | Tài khoản (email chứa liên kết) | — (chỉ email, không lưu in-app) |

DedupeKey \= eventId:channel:recipientUserId:audienceContext; riêng REFUND_RETRY_REQUIRED ổn định theo refundId:retryCycle:recipientUserId:channel. recipient lấy từ chủ Payment, không từ client; link chỉ mở trang đăng nhập/đọc trạng thái/xác nhận, không tự chạy refund. Soft delete/hết hạn hiển thị giữ dedupe; retry email không tạo thông báo mới. Tiền giả lập luôn có nhãn rõ.

#### Danh sách endpoint

| \# | Method | Path | Mô tả | Quyền | UC |
| :---- | :---- | :---- | :---- | :---- | :---- |
| 1 | GET | /notifications/me | Thông báo trong ứng dụng (Should) | notification:read:own | G01 |
| 2 | GET | /notifications/me/unread-count | Số chưa đọc (chuông) | notification:read:own | G01 |
| 3 | POST | /notifications/me/{notificationId}/read | Đánh dấu đã đọc | notification:read:own | G01 |
| 4 | POST | /notifications/me/read-all | Đánh dấu tất cả đã đọc | notification:read:own | G01 |
| 5 | DELETE | /notifications/me/{notificationId} | Xóa thông báo in-app của mình | notification:read:own | G01 |

#### DTO

| DTO | Field |
| :---- | :---- |
| Notification | notificationId, audienceContext (CUSTOMER\|SELLER\|ACCOUNT), type, title, content, reference { type, id, code }, isRead, createdAt. code chỉ có giá trị với WITHDRAWAL (withdrawalCode), còn lại null. recipientUserId giữ nội bộ; deletedAt là field lưu soft delete, API list chỉ trả chưa xóa. |

#### Chi tiết endpoint

##### GET /notifications/me — Thông báo trong ứng dụng (Should)

**Quyền: notification:read:own    UC: G01**

**Tham số (path / query)**

| Tên | Vị trí | Kiểu | Bắt buộc | Mô tả / ràng buộc |
| :---- | :---- | :---- | :---- | :---- |
| unreadOnly | query | bool |  |  |
| page, size | query |  |  | Chỉ hiển thị tối đa 50 thông báo gần nhất, giữ nội dung 30 ngày (P-22). Không xóa email/cắt retry/mất dedupe |

**Response thành công**

200 → data: Notification\[\] \+ meta

##### GET /notifications/me/unread-count — Số chưa đọc (chuông)

**Quyền: notification:read:own    UC: G01**

**Response thành công**

200 → data: { unreadCount }

##### POST /notifications/me/{notificationId}/read — Đánh dấu đã đọc

**Quyền: notification:read:own    UC: G01**

**Response thành công**

204

##### POST /notifications/me/read-all — Đánh dấu tất cả đã đọc

**Quyền: notification:read:own    UC: G01**

**Response thành công**

204

##### DELETE  /notifications/me/{notificationId}  —  Xóa thông báo của tôi

Quyền: notification:read:own

Request: notificationId UUID trong path; không có body

Response: 204 nếu đã xóa hoặc xóa lần đầu; 404 nếu không tồn tại/không sở hữu

Chỉ Notification IN\_APP, cập nhật deletedAt và isRead=true nguyên tử; list/unread-count/read-all bỏ bản ghi deletedAt khác null. Giữ delivery EMAIL và khóa dedupe; không hủy sự kiện hay giao dịch nguồn. notification:read:own bao gồm quản lý trạng thái hiển thị của chính mình trong MVP; không có quyền xóa của người khác.

## 7. API nội bộ và sự kiện giữa các service

### 7.1 Internal API (đồng bộ)

Base /internal/v1, chỉ gọi bởi service đã xác thực (mTLS hoặc token nội bộ theo audience/scope), Gateway chặn public. Mỗi endpoint có allowlist consumer. User ID truyền nội bộ phải xuất phát từ Bearer đã xác minh hoặc event tin cậy; không coi mạng nội bộ là đủ quyền. Timeout mặc định 2s; retry thao tác ghi chỉ khi có idempotency key.

| Provider | Method \+ Path | Consumer | Mục đích / response chính |
| :---- | :---- | :---- | :---- |
| Identity | POST /identity/auth/authorization-context | Gateway, mọi service bảo vệ | Bearer + service credential → {userId,accountType,accountStatus,userStatus,accessMode,bannedAt,disabledAt,restoreUntil,authzVersion,roles[],permissions[],customerProfile,sellerProfile,sessionActive}; ba mốc nullable; kiểm tra sessionId/version và allowlist G-14 hiện hành. |
| Identity | GET /identity/sellers/status?ids= | Product, Cart, Order, Finance | \[{ sellerId, userId, userStatus, approvalStatus, status, sellerRoleActive, sellable }\]; no credential/PII; quyết định hiện hành không cache quyền. |
| Identity | GET /identity/customers/{id}/checkout-eligibility | Order, Payment | { userId, userStatus, customerStatus, paymentBlocked, hasPhone, addressCount, canCheckout, authzVersion }; xác minh quyền và Customer. |
| Identity | GET /identity/customers/{id}/addresses/{addressId} | Order | Snapshot recipientName, recipientPhone, provinceCode, provinceName, wardCode, wardName, addressDetail; mã/tên từ cùng danh mục seed DB Identity, 404 nếu không thuộc Customer. |
| Identity | POST /identity/customers/{id}/addresses/{addressId}/touch | Order | `{ checkoutSessionId }`; chỉ gọi sau commit và dùng lại cùng ID khi retry. Địa chỉ đã xác nhận xóa và thuộc đúng Customer trả `200` với `{ addressId, lastUsedAt: null, applied: false }`. Địa chỉ không rõ hoặc khác chủ trả `404`; lỗi touch giữ nguyên checkout đã commit. |
| Identity | GET /identity/users/{userId}/contact | Notification | { userId, email, fullName }; service credential allowlist, bỏ contact theo actorType. |
| Product | POST /products/batch | Cart, Order | `{ productIds }` → productId, sellerId, giá, tên, media, sellable. Product kiểm tra service credential dành cho Cart hoặc Order. Lỗi nguồn xác minh Seller trả `503 SERVICE_UNAVAILABLE`. |
| Product | POST /products/reviews/summary | Order | { customerId, productIds\[\] } ≤100 → \[{ productId, reviewId\|null, hasReviewed }\]; từ customerId của đơn đã xác minh, chỉ allow Order; không API public my-reviews. |
| Order | GET /inventory/availability?productIds= | Product, Cart | \[{ productId, availableQuantity }\]. |
| Order | POST /inventory | Product | `{ productId, sellerId, onHandQuantity }`. Một dòng trên mỗi `productId`. Tạo mới trả `201` với `{"data":null}`. Đã tồn tại và đúng Seller trả `200` cùng body và giữ nguyên tồn, kể cả khi số lượng gửi lại khác. Cùng sản phẩm nhưng khác Seller trả `409 INVENTORY_PROVISION_CONFLICT`. Order chấp nhận thiếu `Idempotency-Key`; Product dùng lại cùng key khi retry. Thay đổi số lượng qua API Seller §6.4. |
| Order | GET /inventory/details?productIds= | Product | Tồn thực/giữ/khả dụng/version; Product Service chỉ chuyển tiếp dữ liệu đúng quyền Seller/Admin. |
| Order | GET /orders/checkout-sessions/{id}/payable | Payment | { customerId, status, expiresAt, totalAmountVnd, orders\[{ orderId, status }\] }. Chỉ Payment gọi bằng service credential. Order đọc customerId từ checkout đã xác thực quyền sở hữu lúc tạo. Payment đối chiếu chủ phiên với bản ghi Payment đã có khi gọi lại. Trạng thái Order cho phép giới hạn tra cứu của Customer BANNED vào đúng nghĩa vụ. Relay dùng checkoutSessionId và service credential, không lưu hoặc chuyển tiếp Bearer của khách. |
| Order | GET /orders/accounts/{userId}/obligations?accountType=CUSTOMER\|SELLER | Identity | {hasOpenOrders,openOrderIds[],hasPendingPayment,hasUnresolvedRefund,evaluatedAt,orderStateVersion}; xác minh Payment qua API nội bộ. Dấu đóng không kết luận hết nghĩa vụ. Payment PENDING, SUCCESS chưa áp dụng và Refund chưa giải quyết vẫn được đánh giá theo G-14, kể cả FULL_CHECKOUT. |
| Cart | POST /cart/checkout-items | Order | Đọc toàn bộ tập dòng đã chọn theo Customer và trả snapshot có chủ giỏ, số lượng và phiên bản. Xem [DELTA-02](#delta-02-cart-checkout-items). |
| Order | GET /orders/order-items/{id}/review-eligibility?customerId= | Product | { eligible, productId, orderStatus }; xác minh chủ Order và CONFIRMED. |
| Payment | GET /payments/by-checkout/{checkoutSessionId} | Order | Trạng thái Payment và link có hạn nếu đủ điều kiện. Khi có Payment, `data.customerId` lấy từ bản ghi Payment để Order đối chiếu chủ phiên. Khi đã xác minh chưa có bản ghi, trả `200 {"data":null}`. Lỗi nguồn trả 503. |
| Payment | POST /payments/checkouts/{checkoutSessionId}/provider-session | Order | Chỉ Order gọi bằng service credential. Tạo hoặc lấy cùng link và kết quả theo checkoutSessionId. Kiểm tra payable, hạn và dấu đóng; Customer, số tiền và quy ước VND lấy từ checkout do Order sở hữu. |
| Payment | GET /payments/refunds?orderIds= | Order | Trạng thái hoàn của các Order; Order cũng truy vấn theo checkoutSessionIds để lấy Refund FULL_CHECKOUT có orderId=null, kể cả CheckoutSession đã đóng. Đây là mở rộng contract tra cứu hiện có; không loại Refund PENDING/FAILED khỏi đánh giá nghĩa vụ. |
| Payment | GET /payments/totals | Finance | { capturedVnd, refundedVnd, unsettledRefundVnd, unsettledFullCheckoutRefundVnd, readAt }; đọc cục bộ, phần chờ gồm PENDING và FAILED. |
| Order | GET /orders/reconciliation-totals | Finance | { paidCheckoutCount, paidCheckoutTotalVnd, readAt }; mỗi checkout đã áp dụng PAID đếm một lần. |
| Order | GET /orders/collection-reviews | Payment | Trả phiên còn cờ thu, phân trang theo since tăng dần rồi checkoutSessionId. Query checkoutSessionId cho phép đọc ghi chú của đúng phiên kể cả đã tắt cờ. Yêu cầu Bearer Admin có operations:read:any. |
| Order | POST /orders/checkout-sessions/{checkoutSessionId}/mark-handled | Payment | { note } → { checkoutSessionId, manualNote, handledAt }; yêu cầu Bearer Admin có operations:annotate. |
| Finance | GET /finance/commission-rate | Order | { commissionBps: 500 } = 5% chung mọi Seller/ngành hàng để snapshot từng Order; không tra Category. |
| Finance | GET /finance/sellers/{sellerId}/obligations | Identity | {hasPendingFinance,pendingBalanceVnd,availableBalanceVnd,evaluatedAt,financeStateVersion}; gồm settlement/hiệu ứng pending chưa apply kể cả bằng 0, không chỉ wallet. |
| Order | GET /orders/sellers/{sellerId}/open-orders | Identity | {hasOpenOrders,evaluatedAt}; chỉ đọc đơn đã thu còn PAID/PACKING/DELIVERED để phân quyền Seller BANNED trong authorization-context Phase 4. |
| Notification | POST /notifications | Identity, Product, Order, Payment, Finance | Tạo thông báo từ service allowlist; hợp đồng chi tiết 7.1.1. Không lộ qua public Gateway. |

<a id="delta-02-cart-checkout-items"></a>

#### DELTA-02 — Đọc các dòng giỏ dùng cho checkout

- `POST /internal/v1/cart/checkout-items` chỉ nhận `X-Service-Token` riêng của Order. Body `{customerId, cartItemIds}` gồm UUID Customer đã xác thực ở Order và 1–50 UUID dòng khác nhau. Cart đọc theo chủ giỏ và chỉ trả thành công khi toàn bộ dòng thuộc cùng giỏ của Customer.
- HTTP 200 trả `{"data":{"cartId":"<uuid>","customerId":"<uuid>","items":[{"cartItemId":"<uuid>","productId":"<uuid>","quantity":2,"rowVersion":7}]}}`. Các trường đều bắt buộc. `customerId` lấy từ chủ giỏ đã lưu. `quantity` là số nguyên 1–99 và `rowVersion` là số nguyên 64 bit không âm theo Database §3.3.2. `items` chứa đúng tập ID yêu cầu, mỗi ID một lần. Order đối chiếu theo ID. Thứ tự trả về không ảnh hưởng kết quả.
- Cart trả `400 VALIDATION_ERROR` nếu input sai, `401 UNAUTHENTICATED` nếu thiếu hoặc sai token và `404 NOT_FOUND` chung nếu bất kỳ dòng nào thiếu hoặc khác chủ. Ở Preview, Order giữ lỗi 404 chung, không tiết lộ chủ khác. Với Pay mới, cùng lỗi 404 hợp lệ hoặc snapshot đã xác minh có mã giỏ, sản phẩm, số lượng hoặc phiên bản khác quote dẫn đến `409 CHECKOUT_QUOTE_CHANGED`. Timeout, lỗi nguồn/credential và phản hồi sai cấu trúc, thiếu/trùng dòng hoặc sai chủ dẫn đến `503 SERVICE_UNAVAILABLE`. Order đọc Cart trước transaction giữ hàng. Replay hợp lệ dùng phiên cũ theo §6.4.

<a id="order-obligations-state-version"></a>

#### Phiên bản kết quả nghĩa vụ Order

`orderStateVersion` là chuỗi hex thường 64 ký tự, tính bằng SHA-256 trên mảng JSON UTF-8 viết gọn theo thứ tự cố định `[accountType,userId,hasOpenOrders,openOrderIds,hasPendingPayment,hasUnresolvedRefund]`. UUID dùng dạng chuẩn chữ thường và `openOrderIds` là danh sách ID duy nhất đã sắp xếp theo thứ tự từ điển. Chỉ tính trên kết quả đã xác minh. Tài khoản được Identity xác minh nhưng chưa có dữ liệu nghĩa vụ dùng các cờ `false` và danh sách rỗng. Trường hợp nguồn chưa xác minh được xử lý theo §7.1.2.

Identity dùng version này để nhận diện kết quả nghĩa vụ đã thay đổi bằng phép so bằng. `evaluatedAt` ghi thời điểm đánh giá và nằm ngoài hash. Việc đánh giá quyền dùng dữ liệu hiện hành và kiểm tra phiên bản/đợt cấm do Identity sở hữu trước commit. Hash không yêu cầu bộ đếm hoặc migration và không dùng làm token khóa hay thứ tự cập nhật.

#### 7.1.1 Tạo thông báo nội bộ

POST /internal/v1/notifications. Quyền INTERNAL, service credential có notification:create và allowlist producer/type. Request: { eventId, recipientUserId, audienceContext: CUSTOMER\|SELLER\|ACCOUNT, type: NotificationType, channels: \[EMAIL\|IN\_APP\], reference?: { type, id, code }, templateData: object }. Không nhận nội dung HTML, URL redirect hay email người nhận tự do từ public client; email lấy từ Identity qua userId.

201 → data: { notificationIds\[\], status: "PENDING" }; retry cùng event/type/recipient/context/kênh và payload →200 cùng ID, khác payload →409 IDEMPOTENCY\_KEY\_REUSED. Commit Notification \+ dedupe trước khi trả; email xử lý bất đồng bộ J6. Mỗi kênh là một bản ghi để xóa in-app không ảnh hưởng email.

Allowlist: Identity chỉ seller lifecycle/account/reset; Product chỉ product moderation; Order chỉ checkout/order lifecycle; Payment chỉ payment/refund; Finance chỉ funds.available/withdrawal.completed. Producer phải suy ra recipientUserId từ dữ liệu nghiệp vụ sở hữu; Notification kiểm tra User có tồn tại, template phù hợp type và reference. 400 validation, 401 credential sai, 403 NOTIFICATION\_TYPE\_NOT\_ALLOWED, 404 User không tồn tại, 409 payload xung đột.

Consumer event Outbox và internal POST dùng chung hàm createNotification với eventId và dedupeKey thống nhất. Một sự kiện chỉ chọn một đường phát tại producer; nếu cả hai tới thì vẫn một bản ghi mỗi kênh. Kể cả bản ghi đã soft delete, retry trả ID cũ, không khôi phục hiển thị.

#### 7.1.2 Nguồn quyền và dữ liệu liên service

G-14 dùng hai contract obligations §7.1 tại ban/login/refresh/J7. Đây là bổ sung đặc tả, không tuyên bố endpoint code tồn tại. 404 tài khoản/ví đáng lẽ tồn tại, timeout/lỗi/không nhất quán không là nghĩa vụ 0: request cần xác minh →503, không ghi ban một phần/cấp quyền rộng hơn; job giữ trạng thái và retry.

HTTP liên service nằm ngoài transaction giữ khóa. Trước commit Identity kiểm tra version và đợt cấm còn hiện hành để job không ghi đè Admin unban. Snapshot các nguồn không nguyên tử; giữ bền vững nghĩa vụ/hiệu ứng muộn để mở lại quyền giới hạn, không mất tiền.

Điều phối giữa các service dùng outbox/inbox và retry; không join database chéo. Khi event cancel/confirm/refund tới Finance trước checkout.paid hoặc trước ví được tạo, consumer giữ bản ghi chờ phụ thuộc và retry; không đánh dấu đã áp dụng thành công hoặc ghi số dư âm. Event cùng aggregate phải kiểm tra version/state; event cũ không đảo trạng thái mới. Đối soát J5/Finance kiểm tra lại service sở hữu khi thiếu dữ kiện; không tự đoán số tiền từ notification.

### Bằng chứng đủ điều kiện bán tại thời điểm thu (G3-A)

Order gọi hai API nội bộ chỉ đọc qua OpenFeign, dùng `X-Service-Token` với quyền `order-service` ở service đích. `completedAt` là thời điểm UTC RFC 3339 do Payment đã xác minh và gửi trong `payment.succeeded`. Lấy các ID phân biệt từ snapshot checkout.

| Service | HTTP | Request JSON | Phần tử trong `data.items` |
| --- | --- | --- | --- |
| Identity | `POST /internal/v1/identity/sellers/sale-evidence` | `{completedAt, sellerIds: UUID[]}` | `{sellerId, decision}` |
| Product | `POST /internal/v1/products/sale-evidence` | `{completedAt, productIds: UUID[]}` | `{productId, sellerId, decision}` |

HTTP 200 trả `{data:{completedAt,items:[...]}}`. Trả đúng thời điểm yêu cầu và đúng một mục cho mỗi ID. `ELIGIBLE` nghĩa là nguồn sở hữu xác nhận đủ điều kiện tại thời điểm đó. Identity xác minh quyền bán theo G-06. Product xác minh điều kiện bán và Seller sở hữu sản phẩm tại cùng thời điểm. `INELIGIBLE` cần bằng chứng xác định từ nguồn. `UNKNOWN` dùng khi chưa có lịch sử đáng tin cậy. Product có thể trả `sellerId: null` khi thiếu chủ sở hữu và chỉ được xác nhận `ELIGIBLE` khi có chủ sở hữu. Lỗi cú pháp trả 400, lỗi credential hoặc quyền trả 401 hoặc 403, lỗi nguồn tạm thời trả 503.

Order gọi HTTP bên ngoài transaction khóa checkout và phân loại từng Order theo snapshot tại `completedAt`. Order hợp lệ khi cả hai nguồn xác nhận `ELIGIBLE` và Seller của từng sản phẩm khớp snapshot. Khi còn Order chưa kết luận do timeout, lỗi nguồn, phản hồi thiếu/sai hoặc `UNKNOWN`, Order giữ công việc xác minh bền vững đến hạn. Khi đủ kết luận và giờ DB sau khi lấy khóa vẫn trước hạn, Order áp dụng PAID, ghi SOLD và `checkout.paid`. Trong cùng transaction, hệ thống chỉ hủy các Order có bằng chứng không hợp lệ tại thời điểm thu, ghi RESTOCK và `order.cancelled` với `refundReason=SELLER_CANCEL` để Payment hoàn từng Order. Payment giữ SUCCESS. Nếu tới hạn mà checkout chưa áp dụng PAID, J1 đóng EXPIRED, nhả hàng và Payment dùng FULL_CHECKOUT khi xác nhận đã thu. HTTP mock theo hợp đồng dùng cho 4A. Nghiệm thu với Identity/Product thật được kiểm riêng.

### 7.2 Sự kiện (bất đồng bộ, qua Outbox)

Event dùng Outbox, consumer Inbox chống lặp eventId; envelope thêm actor { userId\|null, kind: USER\|SERVICE, context: CUSTOMER\|SELLER\|ADMIN\|ACCOUNT\|null }. role/context trong event chỉ là dữ liệu audit, không cấp quyền gọi API. Mỗi aggregate có version tăng đơn điệu; consumer không áp dụng ngược trạng thái từ event cũ.

```text
{ "eventId": "evt-0c1...", "eventType": "order.cancelled", "occurredAt": "2026-10-19T02:10:00Z",
  "producer": "order-service", "aggregateId": "od-2", "version": 1,
  "payload": { ... } }
```

| Event | Producer | Payload chính | Consumer |
| :---- | :---- | :---- | :---- |
| payment.succeeded | Payment | paymentId, checkoutSessionId, amountVnd, completedAt | Order |
| payment.failed | Payment | paymentId, checkoutSessionId, errorCode | Order |
| checkout.paid | Order | checkoutSessionId, paymentId (UUID bắt buộc, từ Payment đã lưu trong checkout khi áp dụng PAID), customerId, source, paidAt, cartItems\[{ cartItemId, quantity }\], orders\[{ orderId, sellerId, totalAmountVnd, commissionBps }\] | Cart, Finance, Notification, Payment (snapshot kiểm tra Refund ORDER) |
| checkout.closed | Order | checkoutSessionId, customerId, closeReason (PAYMENT_FAILED \| EXPIRED), closedAt | Payment đóng luồng thu; Notification báo theo lý do |
| order.packing | Order | orderId, sellerId | (Notification — tùy chọn) |
| order.delivered | Order | orderId, customerId, sellerId, deliveredAt, customerConfirmDueAt | Notification |
| order.cancelled | Order | orderId, checkoutSessionId, customerId, sellerId, totalAmountVnd, wasPaid, cancelledBy, reasonCode, reasonNote, refundReason | Payment (nếu wasPaid), Finance, Notification |
| order.confirmed | Order | orderId, sellerId, customerId, confirmedAt, confirmedBy (CUSTOMER \| SYSTEM) | Finance, Notification |
| refund.succeeded | Payment | refundId, paymentId, orderId, scope, amountVnd | Finance, Order, Notification |
| refund.failed | Payment | refundId, paymentId, checkoutSessionId, customerId, orderId\|null, scope, amountVnd, errorCode, retryCycle, retryCount, retryExhausted | Notification chỉ gửi REFUND_RETRY_REQUIRED khi retryExhausted=true; dedupe refundId + retryCycle, hỗ trợ FULL_CHECKOUT không cần orderId. SUCCESS muộn vẫn áp dụng; link không tự retry |
| funds.available | Finance | orderId, sellerId, sellerAmountVnd | Notification |

Payment dùng snapshot `checkout.paid` để đối chiếu Payment SUCCESS, chủ checkout, Seller và số tiền từng Order trước khi tạo Refund ORDER từ `order.cancelled`. Khi lệnh hủy đến trước snapshot, Payment ghi Inbox chờ và xử lý tiếp khi nhận được snapshot. Các nghĩa vụ ORDER và FULL_CHECKOUT loại trừ nhau. Phát lại sự kiện hoặc nhận nhiều eventId cho cùng nghĩa vụ hợp lệ vẫn dùng một Refund.
| withdrawal.completed | Finance | withdrawalId, sellerId, amountVnd, availableAfterVnd | Notification |
| seller.registered / approved / rejected | Identity | userId, sellerId (=userId), approvalStatus, authzVersion, reason? | Finance (approved), Notification |
| seller.banned / seller.unbanned | Identity | userId,sellerId,status,accountStatus,accessMode,bannedAt,disabledAt,restoreUntil,authzVersion,reason | Order không tự hủy; J4 giữ nguyên. Finance không đóng băng available vì ban; Notification phân biệt quyền giới hạn/unban, nêu deadline nếu có. |
| customer.banned / customer.unbanned | Identity | userId,customerId,accountStatus,accessMode,bannedAt,disabledAt,restoreUntil,authzVersion,reason | Notification theo userId/accountType độc lập; không hợp quyền tài khoản khác. |
| product.removed | Product | productId, sellerId, reason | Notification |
| password.reset\_requested | Identity | userId, resetUrl (secret nội bộ, không log), expiresAt | Notification EMAIL; không lưu in-app, queue retention ngắn |
| user.disabled / user.enabled | Identity | userId,accountType,accountStatus,accessMode,bannedAt,disabledAt,restoreUntil,authzVersion,reason | Không tự hủy Order/đóng băng available; Payment/Refund vẫn chạy. user.enabled do nghĩa vụ muộn có accountStatus BANNED, không là unban; chỉ lệnh unban phát *.unbanned. Notification dùng type hiện có, phân biệt quyền giới hạn với khôi phục đầy đủ. |
| user.roles\_changed | Identity | userId, roleCodes hiện hành, authzVersion | Audit/đồng bộ policy; API vẫn hỏi quyền hiện hành |

### 7.3 Job nền

| Job | Service | Việc làm | Tham số |
| :---- | :---- | :---- | :---- |
| J1 | Order | Consumer SQS Standard nhận `ExpireCheckout` (`commandId`, `checkoutSessionId`, `expiresAt`), khóa phiên và kiểm tra hạn từ DB. Đến hạn thì đóng, nhả hàng và ghi Outbox `checkout.closed` cùng transaction. Xóa message sau commit; lệnh đến sớm dời visibility đến hạn. | P-01 |
| J2 | Payment | Tra cứu khoản thu chưa rõ bằng cùng paymentId khi luồng còn mở. Timeout HTTP mặc định 2 giây; khoảng chờ 5/15/30/60 giây, sau đó lặp mốc 60 giây. Timeout giữ kết quả chưa rõ. Dừng khi có kết quả xác định hoặc nhận `checkout.closed`. Khi đã đóng, SUCCESS đã lưu hoặc webhook SUCCESS đến sau đi theo luồng hoàn toàn bộ. | P-02, P-03 |
| J3 (Must) | Order | Đơn DELIVERED quá customerConfirmDueAt → CONFIRMED (confirmedBy \= SYSTEM) | S-RECEIPT-07 |
| J4 (Must) | Order | Đơn PAID/PACKING quá sellerActionDueAt → CANCELLED (refundReason \= SELLER\_TIMEOUT) | S-DELIVERY-07 |
| J5 | Payment | Query kết quả chưa rõ; chỉ retry FAILED xác định, gồm 404 đủ điều kiện theo hợp đồng provider. Một lần đầu + tối đa 3 retry mỗi chu kỳ, chờ 1/5/15 phút từ lỗi trước với cùng Refund/key và attempt mới. Query không tính lượt. Hết lượt FAILED, thông báo Customer theo mỗi chu kỳ. SUCCESS muộn áp dụng nếu attempt còn chưa rõ. Nếu mâu thuẫn với FAILED, giữ để đối soát và dừng gửi tiếp. | P-RF-01 |
| J6 | Notification | Một lần gửi đầu + tối đa 3 retry email sau 1/5/15 phút từ lỗi trước, chỉ lỗi tạm thời, tôn trọng Retry-After. Lỗi vĩnh viễn/token hết hạn dừng ngay; hết lượt FAILED, cùng thông báo/key, không đổi nghiệp vụ | P-19 |
| J7 — Đối soát trạng thái tài khoản bị cấm | Identity | Định kỳ đánh giá BANNED/DISABLED trong đợt cấm; hết nghĩa vụ đã xác minh → DISABLED, nghĩa vụ muộn → BANNED giới hạn. Không chuyển ACTIVE/không tự unban, không reset mốc/phát duplicate; kiểm tra version/đợt cấm trước commit | Retry an toàn, không đặt chu kỳ/backoff hoặc SLA mới |

J1 theo lịch từng message SQS. Retry relay, DLQ và phát lại dùng expiresAt gốc. Chu kỳ job khác thuộc N-06; J5 tiếp tục tra cứu Refund chưa rõ sau khi luồng thu đóng.

## 8. Luồng tích hợp tiêu biểu

### 8.1 Checkout nhiều cửa hàng và thanh toán

| Bên gửi → Bên nhận | Xử lý |
| --- | --- |
| FE → Order | Preview rồi pay với quote và Idempotency-Key. Tạo phiên, N đơn, giữ hàng và lưu công việc tạo link + lệnh hết hạn cùng transaction. |
| Order → Payment → provider | Tạo một giao dịch, lấy link có cùng expiresAt. Khách mở link và xác nhận tại provider. |
| Provider → Payment → Kafka → Order | Webhook có chữ ký. Order chỉ áp dụng PAID trước hạn dưới khóa, rồi trừ kho và phát checkout.paid. |
| SQS → Order → Kafka → Payment | ExpireCheckout đến hạn đóng phiên và nhả hàng. checkout.closed chặn thu và query thu. SUCCESS đã có hoặc webhook SUCCESS đến sau tạo đúng một FULL_CHECKOUT. |
| Order → Cart, Finance, Notification | checkout.paid giảm hàng đã mua khỏi giỏ khi CART, tạo tiền chờ từng đơn và gửi thông báo. |

Order xác minh điều kiện giao dịch qua Identity/Product ngoài transaction giữ khóa. Với phiên đã được áp dụng PAID trước hạn, đơn invalid được chứng minh tại thời điểm giao dịch bị hủy/hoàn riêng, đơn hợp lệ tiếp tục. Lỗi nguồn giữ công việc để xác minh lại, ban sau capture không tự hủy. Phiên CLOSED dùng nhánh FULL_CHECKOUT, không phát checkout.paid.

Phân kỳ đã duyệt: Phase 2 giữ xác minh và công việc retry bền vững khi nguồn thiếu bằng chứng tại thời điểm thu. Nếu tới hạn mà Order chưa áp dụng PAID, dùng luồng EXPIRED và FULL_CHECKOUT khi xác nhận đã thu. Hủy và hoàn theo từng Order đã PAID thuộc ORD-08 ở Phase 3. Kiểm thử dùng bằng chứng thật tiếp tục phụ thuộc nguồn Identity/Product tương thích.

### 8.2 Seller từ chối giao hàng

| \# | Bên gọi → Bên nhận | Hành động | Kết quả |
| :---- | :---- | :---- | :---- |
| 1 | Seller FE → Order | POST /orders/seller/orders/{id}/reject | Order → CANCELLED, StockMovement RESTOCK, outbox order.cancelled |
| 2 | Order → Payment | event order.cancelled | Tạo Refund (ORDER, SELLER\_REJECT) bằng tổng đơn, gọi API hoàn tiền của provider |
| 3 | Order → Finance | event order.cancelled | Settlement PENDING\_REFUND (chưa trừ số dư chờ) |
| 4 | Order → Notification | event order.cancelled | Báo khách và Seller |
| 5 | Payment → Finance, Order, Notification | event refund.succeeded | Settlement REFUNDED \+ bút toán REFUND\_DEBIT (trừ pending của Seller và nền tảng); đơn hiển thị "Đã hoàn"; báo khách |

### 8.3 Xác nhận nhận hàng và rút tiền

| \# | Bên gọi → Bên nhận | Hành động | Kết quả |
| :---- | :---- | :---- | :---- |
| 1 | Customer FE → Order | POST /orders/me/{id}/confirm-receipt | DELIVERED → CONFIRMED, outbox order.confirmed |
| 2 | Order → Finance | event order.confirmed | Settlement SETTLED; Seller chờ −S, khả dụng \+S; nền tảng chờ −C, hoa hồng \+C; phát funds.available |
| 3 | Seller FE → Finance | GET /finance/seller/wallet | canWithdraw \= true |
| 4 | Seller FE → Finance | POST /finance/seller/withdrawals \+ Idempotency-Key | Một transaction: khả dụng −A, đã rút \+A, Withdrawal, bút toán → 201 hoàn tất ngay |
| 5 | Finance → Notification | event withdrawal.completed | Gửi biên nhận cho Seller |

## 9. Mâu thuẫn tài liệu và điểm cần chốt

Các quyết định tiền/tồn còn hiệu lực từ bản trước. Bản v1.0 áp dụng Database RBAC v5.0, xử lý comment tại 10.3; các tài liệu ngoài file này chưa tự động được sửa. Những khác biệt/điểm chưa chốt được ghi rõ trong bảng dưới.

| Mã | Nội dung lệch | Quyết định trong API | Cần làm |
| :---- | :---- | :---- | :---- |
| C-01 | **Đã xử lý (Database RBAC v5.0). Rút tiền tức thời theo FRD v5.2: bỏ Payout, ngăn reserved và trạng thái Withdrawal.** | API giữ nguyên POST /finance/seller/withdrawals trả 201 hoàn tất ngay; DTO Wallet/WalletTransaction/Withdrawal đã đổi tên field theo DB. | Không còn việc cần làm. |
| C-02 | **Đã chốt — theo FRD. Trạng thái Order: FRD có chờ\_xử\_lý và đang\_đóng\_gói; Database RBAC v5.0 thiếu.** | OrderStatus gồm AWAITING\_PAYMENT, PAYMENT\_FAILED, EXPIRED, PAID (chờ xử lý), PACKING, DELIVERED, CONFIRMED, CANCELLED. | Đã cập nhật Database RBAC v5.0 (3.6.2, 8.2B: thêm PACKING, PAYMENT\_FAILED, EXPIRED, packing\_at, cancelled\_by\_type). Không còn việc cần làm. |
| C-03 | **Đã chốt — theo FRD.** Khách phải đăng nhập mới thêm vào giỏ; không có giỏ khách vãng lai. | Không có API guest cart; Cart chỉ cho CUSTOMER, chưa đăng nhập → 401\. | Đã cập nhật Database RBAC v5.0 (3.3: bỏ owner\_type, guest\_token\_hash, merge). Không còn việc cần làm. |
| C-04 | **Đã chốt — theo FRD.** Mua ngay (UC-B10). | API có source: CART \| BUY\_NOW; BUY\_NOW không có cartItemIds, không đọc/ghi giỏ. | Đã cập nhật Database RBAC v5.0 (3.6.1 checkout\_source; source\_cart\_id, cart\_snapshot chỉ bắt buộc khi CART; G-16). Không còn việc cần làm. |
| C-05 | Trạng thái CheckoutSession: FRD có 5 giá trị; DB có AWAITING\_PAYMENT / PAID / CLOSED \+ close\_reason. Payment: FRD có partially\_refunded / refunded; DB chỉ PENDING / SUCCESS / FAILED. | API trả 5 trạng thái FRD (map từ CLOSED \+ close\_reason). Payment giữ 3 trạng thái DB, thêm field tính toán refundState. | Thống nhất mapping khi code; không cần đổi DB nếu chấp nhận cách tính. |
| C-06 | **Đã chốt (06/11/2026).** Không có mã hiển thị đơn / mã thanh toán và không có mã vận đơn (chưa có đơn vị vận chuyển). | Bỏ orderCode, checkoutCode, trackingNumber; FE dùng orderId / checkoutSessionId (UUID). withdrawalCode giữ nguyên (withdrawal\_code). | Không còn việc cần làm. |
| C-07 | **Đã xử lý.** UI Screens và Figma còn mô hình cũ (đơn chung, Hủy phần hàng, Báo cáo, Payout). | API theo FRD v5.3. | Đã cập nhật UI Screens v2.0 và các nhãn/ghi chú trên Figma (mã thanh toán \+ mã đơn theo cửa hàng, ví và rút tiền, không báo cáo vi phạm). |
| C-08 | **Đã chốt trong đặc tả ngày 19/11/2026 theo P-37.** Tìm tên sản phẩm và tên cửa hàng không phân biệt dấu tiếng Việt và hoa thường. | Chuẩn hóa cả q và tên để so khớp: bỏ dấu tiếng Việt, đổi đ/Đ thành d, chuyển về chữ thường; giữ nguyên tên lưu và hiển thị. | C-08 đóng trong đặc tả; đây là yêu cầu triển khai, không phải xác nhận code đã hỗ trợ. |
| C-09 | UC-A05 bước 2 nói "báo lỗi nếu email không tồn tại", nhưng luồng ngoại lệ của chính UC-A05 và G-05 yêu cầu luôn trả cùng một thông báo. | Theo G-05: luôn 202 cùng nội dung. | BA sửa bước 2 của UC-A05 (FRD v5.3 chưa sửa vì chưa có quyết định). |
| C-10 | **Đã chốt — theo Database.** Số dư chờ chỉ bị trừ khi hoàn tiền thành công. | Đơn hủy → Settlement PENDING\_REFUND, chưa ghi bút toán; Refund SUCCESS → REFUND\_DEBIT (Seller pending −S, nền tảng pending −C) \+ Settlement REFUNDED. | Đã cập nhật FRD v5.3 (bỏ hủy\_chờ ở B06, B08, D02, E03, G02, G03; phần Giải nghĩa công thức) và Database RBAC v5.0 mục 8.3. Không còn việc cần làm. |
| C-11 | Reporting thứ 8 vẫn tạm hoãn trong Database v5.0. | API triển khai 7 service; tra cứu vận hành tại service sở hữu. Các stats/dashboard trong bản trước để tùy chọn sau MVP. | Không bắt buộc các endpoint phân tích như /orders/\*/stats, /orders/seller/dashboard, /payments/admin/summary và /finance/admin/summary/reconciliation cho RBAC MVP; nếu triển khai vẫn kiểm tra permission. |
| C-12 | Ngưỡng sắp hết hạn 7 ngày | P-36 đã chốt: thời gian xử lý còn ≤24 giờ; dueWithinHours mặc định 24. | Không mở rộng phạm vi dashboard/Reporting. |
| C-13 | RefundReason SELLER\_CANCEL chỉ khi giao dịch bị hủy vì Seller thực sự không hợp lệ tại bước xác minh hoặc nguyên nhân nghiệp vụ khác; cấm Seller SAU thanh toán không tự hủy Order đang dở. | CUSTOMER\_CANCEL, SELLER\_REJECT, SELLER\_CANCEL, SELLER\_TIMEOUT, LATE\_PAYMENT. Seller bị cấm sau thanh toán không tự kích hoạt SELLER\_CANCEL; vẫn xử lý Order tồn qua phiên giới hạn hoặc tự hết hạn theo J4. | Database RBAC v5.0 (3.4.2) đã giải thích SELLER\_CANCEL; nhóm xác nhận. |

### 9.1 Tham số ảnh hưởng tới API

[parameters.md](parameters.md) là nguồn giá trị đã chốt Thẻ 16/xác nhận 18/11/2026, bổ sung quyết định P-37 ngày 19/11/2026, thay giá trị cũ cùng chủ đề. Các tham số khác còn mở giữ riêng; N-09/N-11 không đổi. P-37 chốt tìm tên sản phẩm và tên cửa hàng không phân biệt dấu tiếng Việt và hoa thường; C-08 đóng trong đặc tả.

| Tham số | Quy tắc / trạng thái |
| :---- | :---- |
| P-01 | Chốt 15 phút từ lúc tạo checkout; link và Reservation có cùng expiresAt |
| P-02 / P-03 | Timeout HTTP mặc định 2 giây. J2 chờ 5/15/30/60 giây giữa các lần tra cứu khi chưa rõ kết quả, rồi giữ khoảng chờ 60 giây. Dừng khi có kết quả xác định hoặc nhận checkout.closed. P-04 không còn đóng phiên. |
| P-05 | Reset link 30 phút từ tạo, một lần |
| P-06 | Đề xuất access/refresh 15 phút / 7 ngày, chưa chốt |
| P-08.3 / P-08.5 | Product ≤9 tệp/2 video, mô tả ≤5.000 ký tự; Review Should ≤5 tệp/1 video, nội dung ≤1.000; định dạng/dung lượng/thời lượng chưa chốt riêng |
| P-09 / P-10 | 3 địa chỉ hiện có; giỏ 1–99 đơn vị/dòng và 50 sản phẩm khác nhau; mua ngay ≤99 |
| P-11 / P-12 | Mặc định 20 sản phẩm/trang, mới nhất; trung bình cộng 1 chữ số thập phân, che một phần tên reviewer |
| P-14 / P-15 | GMT+7, tuần thứ Hai/quý lịch; bán chạy theo số lượng, trên 500 lượt, không tự đặt kỳ/trạng thái được tính |
| P-16 / P-36 | Tồn khả dụng ≤5 cảnh báo thấp; còn ≤24 giờ trước hạn 7 ngày là sắp hết hạn |
| P-18 | Không khóa tạm vì sai đăng nhập; vẫn rate limit/chống lạm dụng |
| P-19 / P-RF-01 | Một lần đầu +3 retry, chờ 1/5/15 phút từ lỗi trước; email theo §7.3, Refund theo §6.5/7.3 |
| P-21a/b/d | Enum hủy/từ chối §4.2, ghi chú ≤500; ba danh sách quản trị riêng reason 1–1.000, Khác có mô tả; §6.1/6.2 |
| P-21e | Seed đúng 10 ngành hàng và ví dụ [parameters.md](parameters.md#danh-mục-ban-đầu--p-21e), UUID; bắt buộc có dù màn quản trị Should chưa làm; Category không có hoa hồng riêng |
| P-22 / P-26 | In-app Should 50 gần nhất/30 ngày không mất dedupe/email; địa giới seed DB Identity hai cấp, nguồn/phiên bản ghi rõ khi chuẩn bị, chưa chọn dataset |
| P-COM-01 | 500 bps = 5% đã chốt, không chỉ dùng test; snapshot Order |
| N-06 | Chu kỳ đối soát còn mở; SQS đặt lịch từng ExpireCheckout theo P-01 |

### 9.2 Giới hạn của tài liệu này

* Tài liệu API v1.1 dựa trên FRD v5.5, FRD Conflicts và Database v5.1; những phần không liên quan giữ theo API v1.0. Không khẳng định đã sửa mã nguồn, UI/Figma hoặc chạy migration/test hệ thống thật.

* FE/BE cập nhật login accountType, các phiên giới hạn, SellerApplication mới, avatarObjectKey và quyền Wallet.canWithdraw; ba web app dùng Auth API chung nhưng tài khoản riêng. Giữ UUID cho Order/CheckoutSession theo conflict \#15.

* P-01 chốt 15 phút; dung lượng/định dạng/thời lượng upload và rate limit còn trạng thái riêng. P-26 đã chốt seed DB Identity thay provider ngoài, nhưng nguồn/phiên bản dataset phải ghi rõ khi chuẩn bị. Không khẳng định đã kiểm thử service thật hoặc Google OAuth trong lần sửa tài liệu này.

## 10. Quy trình sử dụng và nhật ký thay đổi

### 10.1 Quy trình đề xuất

* **Bước 1:** Cả nhóm review Mục 2–4 và Mục 9 (chốt các điểm C-xx), điền cột "Phụ trách" ở Mục 5\.

* **Bước 2:** Người phụ trách từng service chuyển phần của mình sang OpenAPI 3.0 YAML (mỗi service một file), giữ nguyên tên field, enum và mã lỗi.

* **Bước 3:** Dựng mock server (Prism) từ OpenAPI để FE code ngay; BE viết contract test theo OpenAPI.

* **Bước 4:** Mọi thay đổi API: sửa tài liệu/OpenAPI trước, thông báo cho người dùng API đó, ghi vào bảng dưới.

### 10.2 Nhật ký thay đổi

| Phiên bản | Ngày | Nội dung | Người sửa |
| :---- | :---- | :---- | :---- |
| Thẻ 15 | 17/11/2026 | Đặc tả vòng đời cấm/nghĩa vụ/30 ngày, DTO/preview, internal obligations/J7/events, rút toàn bộ số dư nhỏ, provision HIDDEN và trigger invalid sau capture; authority [coverage §the-15](coverage.md#the-15). Không xác nhận runtime. |
| v0.3 (draft) | 19/10/2026 | Đồng bộ FRD v5.3 / Database v4.6: chốt C-02, C-03, C-04 (theo FRD), C-10 (theo Database), C-06 chốt sau, C-07 đã xử lý; mọi tham chiếu FRD 4.x đổi sang mục Database (DB x.y, G-xx, WD-xx); bỏ endpoint công khai /payments/provider/mock/callback; thay Mock Provider bằng bộ giả lập thanh toán nội bộ (6.5.1), mockScenario → simulatorScenario, provider \= SIMULATOR, thêm kịch bản REFUND\_FAIL và lộ trình hai giai đoạn. | Claude, chờ nhóm review |
| v0.2 (draft) | 18/10/2026 | Đồng bộ Database v4.6: bỏ mọi tham chiếu Payout; enum WalletTransactionType, RefundReason, SettlementStatus và DTO Wallet/WalletTransaction/Settlement/Withdrawal theo tên field DB; Settlement không còn customerConfirmDueAt; luồng hoàn tiền theo DB (REFUND\_DEBIT khi Refund SUCCESS); cập nhật C-01, C-10, C-11, C-13. | Claude, chờ nhóm review |
| v0.1 (draft) | 18/10/2026 | Bản đầu: quy ước chung, ma trận UC → service, đặc tả 7 service, internal API, event, job, danh sách điểm cần chốt. | Claude (soạn nháp), chờ nhóm review |
| v1.0 (đồng bộ 06/11/2026) | 06/11/2026 | Lịch sử v1.0: đổi tên Catalog → Product; cập nhật một tên cửa hàng tại users.full\_name, trạng thái ví, hard delete địa chỉ, snapshot địa giới, DTO Cancellation, bỏ orderCode/checkoutCode/trackingNumber (C-06). Quy tắc cấm Seller ở bản v1.0 đã được THAY THẾ bởi v1.1. | Claude, chờ nhóm review |
| v1.0 | 25/10/2026 | RBAC theo Database v5.0; xử lý comment Bùi Minh Quang về auth, ban/unban User, public Catalog routes, review, audit, địa giới, SellerPublic, tạo/xóa Notification. Thêm hợp đồng internal, schema delta và kiểm thử chấp nhận. | v1.1 — 16/11/2026: đồng bộ FRD v5.5/Database v5.1; tài khoản Customer/Seller/Admin độc lập và login accountType; SellerApplication mới sau REJECTED; chỉ Seller APPROVED được ban; Customer/Seller BANNED có quyền theo nghĩa vụ; Seller BANNED rút available; avatar-logo dùng users.avatar\_object\_key; quyết định \#15 giữ UUID, C-06 đóng. Các bản v1.0 dưới đây là lịch sử, không dùng làm quy tắc hiện hành. |

### 10.3 Đối chiếu comment của Bùi Minh Quang

Các comment gốc được giữ và neo tại các dòng đối chiếu dưới đây để tránh chỉ vào endpoint đã xóa. Các thay đổi này chỉ áp dụng trong tài liệu API hiện tại; mục 10.4 liệt kê điều chỉnh DB/OpenAPI cần đồng bộ khi triển khai.

| Comment gốc | Xử lý trong bản v1.0 |
| :---- | :---- |
| IDs 0, 2, 4, 6, 8, 10, 12 — dùng POST /identity/auth/login cho cả Customer Seller Admin | Giữ một endpoint login về mặt kỹ thuật, nhưng request bắt buộc accountType và dùng tài khoản Customer/Seller/Admin độc lập. Không có User đa vai trò; refresh, reset, DTO và session theo đúng accountType. |
| IDs 21,23,25,27 — identity/admin/ban/{userId} | Thẻ 15 thay quyết định scope cũ: ban/unban một tài khoản theo §3.2/§6.1, chỉ Seller APPROVED; nghĩa vụ hiện tại quyết định BANNED/DISABLED và quyền giới hạn, không tự cấm tài khoản khác. |
| ID 31 — chuẩn hóa /categories và /products | Public Catalog bỏ /catalog ở route, bảng endpoint, ví dụ và cross-reference; giữ /catalog riêng internal service routing; từ 06/11/2026 service đổi tên thành Product và internal dùng /internal/v1/products/\*\*. |
| IDs 33, 36 — API review không cần thiết | Bỏ PUT review và GET my-reviews. Tạo review chỉ một lần; Order lấy review summary qua internal Product khi cần biết đã đánh giá. |
| ID 29 — DB có lưu đâu tại audit-logs | Bỏ public GET audit-logs và DTO banHistory. Giữ audit kỹ thuật bắt buộc theo Database v5.0 để truy vết quyền/khóa/duyệt, không xây màn xem log. |
| IDs 14, 16 — địa giới | Quyết định proxy RegionProvider cũ được P-26 Thẻ 16 thay thế: đọc danh mục tĩnh seed DB Identity, cùng dữ liệu validation và snapshot; 503 nếu không xác minh được. |
| ID 19 — Customer cũng xem được Seller | API PUBLIC /identity/sellers/{sellerId} trả SellerPublic cho mọi người; API Admin giữ dữ liệu xét duyệt nhạy cảm. Không mở DTO Admin cho Customer. |
| ID 40 — thiếu xóa và tạo noti | DELETE in-app theo owner; POST nội bộ cho service allowlist, dedupe chung với event; không cho public client tự tạo email/thông báo. |

### 10.4 Tác động lên schema và chuyển đổi API

Database RBAC v5.0 là nền: User/Customer/Seller shared PK, bốn bảng RBAC, AuthSession, PasswordResetToken.user\_id, reviewed\_by\_user\_id, recipient\_user\_id và audience\_context đã được mô tả. Bản API này không sửa trực tiếp file database đã giao; các bổ sung dưới đây cần được đưa vào migration/đặc tả DB khi triển khai.

| Thành phần | Đồng bộ bắt buộc |
| :---- | :---- |
| Notification | Thêm deleted\_at TIMESTAMPTZ NULL; filter IN\_APP chưa xóa; giữ dedupe\_key UQ kể cả đã xóa. Một record/kênh; không xóa email log. NotificationType thêm SELLER\_REGISTERED, USER\_DISABLED, USER\_ENABLED. |
| Account ban policy | G-14 §3.2/§7.1.2: nghĩa vụ Order/Payment/Refund/Finance hiện tại; accountStatus suy ra theo cặp trạng thái, users.banned_at/disabled_at/restore_until TIMESTAMPTZ NULL; chuyển cặp trạng thái/authzVersion/audit/Outbox local transaction. Đây là đặc tả, không chứng minh migration/runtime. |
| User/Customer/Seller API | Credential lưu tại users của MỖI tài khoản tách vai trò; accountType là cột bắt buộc, tối đa một role hiện hành/User. DTO có avatarObjectKey/avatarUrl từ users.avatar\_object\_key; Seller logoUrl lấy cùng ảnh này, không còn sellers.logo\_object\_key. |
| Product Review | Không còn public update/my-reviews; UNIQUE(customer\_id, product\_id) giữ nguyên. updated\_at có thể giữ để tương thích DB, không tạo endpoint sửa chỉ vì có cột. |
| Danh mục địa giới DB Identity | Nạp mã/tên tỉnh, phường/xã/đặc khu, quan hệ thuộc tỉnh và phiên bản seed; API/validation cùng dữ liệu, không tích hợp RegionProvider; Order snapshot cả mã/tên. |
| AuthSession và event | Session ID trong JWT thuộc duy nhất một accountType. Login/refresh kiểm tra quyền phiên giới hạn; logout/reset/disable thu hồi đúng tài khoản. Event truyền userId của tài khoản riêng, không cộng gộp role. |
| Thay đổi breaking | Breaking v1.1: login yêu cầu accountType và tài khoản riêng, không bổ sung role Customer/Seller trên User cũ, Seller reapplication public qua email, AVATAR dùng users.avatar\_object\_key, ban/withdraw hạn chế theo nghĩa vụ. Giữ UUID orderId/checkoutSessionId; FE/BE cần cập nhật OpenAPI đồng bộ. |

### 10.5 Kiểm thử chấp nhận RBAC và comment

Ba tài khoản Customer/Seller/Admin độc lập; login sai accountType báo INVALID\_CREDENTIALS chung; Seller PENDING/REJECTED không đăng nhập; Seller REJECTED nộp SellerApplication mới qua email verification; chỉ Seller APPROVED được cấm. Token cũ/sai role bị từ chối; backend không tự nâng role.

Tiêu chí Thẻ 15 B1–B11: chỉ CONFIRMED+hết nghĩa vụ →DISABLED/thu hồi phiên; PAID/DELIVERED →BANNED đúng chủ, hủy/xác nhận theo state, không mua/review. CANCELLED+Refund FAILED vẫn BANNED đọc nghĩa vụ; hết lượt retry →OPEN_ORDERS_ONLY với refund:retry:own của chủ Payment. Seller pending/effect chưa apply →OBLIGATIONS_READ_ONLY, không rút pending; BANNED available=9500 rút 9500, không 5000, ACTIVE không ngoại lệ. Hết nghĩa vụ/J7 lặp chỉ DISABLED một lần, không đổi mốc/ledger. Unban đúng restoreUntil →200 ACTIVE, sau 1 giây →409 ACCOUNT_RESTORE_WINDOW_EXPIRED; tiền/dữ liệu nguyên; BANNED hơn 30 ngày chưa DISABLED vẫn unban. Lỗi nguồn →503/giữ trạng thái, không ban một phần; job không ghi đè Admin unban. Nghĩa vụ muộn mở lại BANNED giới hạn giữ mốc; ADMIN/PENDING/REJECTED không bị cấm qua UC-E03.

P1/P2: provision timeout/replay cùng key chỉ một Product HIDDEN/productId, Inventory xác nhận mới publish nếu còn hợp lệ; ban/REMOVED trong lúc đợi không bật SELLING, validation/media lỗi trước tạo không lưu Product.

O1–O3: Payment SUCCESS giữ nguyên; một Order invalid giao dịch chỉ hoàn Order đó, đủ checkout.paid/order.cancelled, pending chỉ giảm Refund SUCCESS; ban sau capture với event trễ không tự cancel/refund. Identity/Product lỗi giữ retry bền vững, replay không nhân đôi kho/Refund/ledger.

P-RF-01: mỗi chu kỳ một lần đầu +3 retry FAILED sau 1/5/15 phút từ lỗi trước; PENDING/timeout chỉ query, không đếm retry. Hết lượt gửi một REFUND_RETRY_REQUIRED/refundId/retryCycle, giữ nghĩa vụ/pending. Hai tab cùng expectedRetryCycle chỉ mở một chu kỳ; replay cùng HTTP key trả kết quả cũ, key khác/snapshot cũ 409. Chu kỳ mới lại hết lượt gửi thông báo mới; SUCCESS muộn áp dụng đúng một lần, không phát lệnh/ledger trùng. Sai chủ 404, Seller/Admin 403; Customer BANNED vẫn retry đúng nghĩa vụ; FULL_CHECKOUT đóng có đường GET/retry không cần orderId.

Biên tham số: reset sau 30 phút hoặc dùng lại trả RESET_TOKEN_INVALID; địa chỉ thứ 4 lỗi, xóa rồi thêm được; Product >9 tệp/>2 video/>5.000 ký tự và Review Should >5 tệp/>1 video/>1.000 ký tự bị từ chối. Dòng giỏ 100/dòng mới thứ 51 lỗi; thêm trùng cộng không quá 99, mua ngay 99 vẫn kiểm tra tồn. In-app Should chỉ 50 gần nhất/30 ngày nhưng dedupe/email/retry không bị xóa; P-19 dừng lỗi vĩnh viễn/token hết hạn, tôn trọng Retry-After. Bán chạy đúng 500 chưa đạt; tồn khả dụng 5 và thời gian còn 24 giờ đều thuộc cảnh báo.

Không còn public /catalog, PUT review, my-reviews hoặc public audit. Login vẫn một endpoint nhưng buộc accountType của tài khoản riêng. SellerPublic không có PII; Region 503 đúng khi lỗi, Notification delete/idempotency/allowlist giữ nguyên. Order/CheckoutSession dùng UUID, chỉ Withdrawal có withdrawalCode. dedupe/payload không gửi trùng.

Các kiểm thử trên là tiêu chí triển khai, chưa là kết quả chạy trên backend. Tài liệu đã được rà soát endpoint inventory và phần chi tiết để dùng cùng route, permission và DTO.

