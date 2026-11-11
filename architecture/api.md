# **Đặc tả API và phân quyền RBAC cho hệ thống Ecommerce**

**API Specification**

User chung và Customer Seller kế thừa cùng hợp đồng API theo service

| Thông tin | Giá trị |
| :---- | :---- |
| Phiên bản | v1.0 — áp dụng Database RBAC v5.0 và phản hồi của Bùi Minh Quang |
| Ngày | 25/10/2026 |
| Tài liệu nguồn | Database RBAC v5.0; API v0.3 và comment trong file đính kèm; FRD v5.3 cho nghiệp vụ còn hiệu lực |
| Phạm vi | 7 service: Identity & Seller, Product, Cart, Order, Payment, Finance, Notification · API Gateway · 3 web app |
| Trạng thái | Bản cập nhật để review và chuyển sang OpenAPI; tham số và nguồn API địa giới chưa chốt tại mục 9 |

**Mục lục**

**1\. Giới thiệu**  
1.1 Mục đích  
1.2 Tài liệu nguồn  
1.3 Cách đọc  
**2\. Kiến trúc API**  
2.1 Thành phần  
2.2 Định tuyến tại Gateway  
2.3 Truyền danh tính từ Gateway xuống service  
**3\. Quy ước chung**  
3.1 Base URL và versioning  
3.2 Xác thực  
3.3 Format response chuẩn (envelope)  
3.4 HTTP status code  
3.5 Phân trang, lọc, sắp xếp  
3.6 Quy ước dữ liệu  
3.7 Idempotency (chống gửi lặp)  
3.8 Cập nhật đồng thời (optimistic locking)  
3.9 Upload ảnh/video (presigned URL)  
3.10 Header chuẩn  
3.2.1 Mô hình RBAC và hợp đồng kiểm tra quyền  
3.2.2 Ví dụ response đăng nhập đa vai trò  
**4\. Danh mục mã lỗi và enum dùng chung**  
4.1 Bảng mã lỗi  
4.2 Enum  
**5\. Ma trận Use case → Service**  
5.1 Ownership dữ liệu (theo Database RBAC v5.0)  
**6\. Đặc tả theo service**  
6.1 Identity & Seller Service  
6.2 Product Service  
6.3 Cart Service  
6.4 Order Service (Order Inventory Fulfillment)  
6.5 Payment Service  
6.6 Finance Service  
6.7 Notification Service  
**7\. API nội bộ và sự kiện giữa các service**  
7.1 Internal API (đồng bộ)  
7.2 Sự kiện (bất đồng bộ, qua Outbox)  
7.3 Job nền  
**8\. Luồng tích hợp tiêu biểu**  
8.1 Checkout nhiều cửa hàng và thanh toán  
8.2 Seller từ chối giao hàng  
8.3 Xác nhận nhận hàng và rút tiền  
**9\. Mâu thuẫn tài liệu và điểm cần chốt**  
9.1 Tham số chưa chốt ảnh hưởng tới API  
9.2 Giới hạn của tài liệu này  
**10\. Quy trình sử dụng và nhật ký thay đổi**  
10.1 Quy trình đề xuất  
10.2 Nhật ký thay đổi  
10.3 Đối chiếu comment của Bùi Minh Quang  
10.4 Tác động lên schema và chuyển đổi API  
10.5 Kiểm thử chấp nhận RBAC và comment

# **1\. Giới thiệu**

## **1.1 Mục đích**

Tài liệu đặc tả hợp đồng giao tiếp (API contract) giữa Frontend (3 web app: Customer, Seller, Admin) và Backend (7 service), cùng các API nội bộ và sự kiện giữa các service. Mục tiêu: FE và BE code song song, FE có thể dựng mock server từ tài liệu này mà không cần chờ BE.

## **1.2 Tài liệu nguồn**

| Tài liệu | Phiên bản | Dùng cho |
| :---- | :---- | :---- |
| FRD | v5.3 — 19/10/2026 | Use case, luồng nghiệp vụ, acceptance criteria. **Nguồn ưu tiên cao nhất** về nghiệp vụ. Từ v5.3 không còn mục 4 (mô hình dữ liệu). |
| Database document | RBAC v5.0 — 25/10/2026 | User là danh tính chung; Customer/Seller kế thừa shared PK; UserRole và RolePermission cấp quyền. DB 3.1 mô tả Identity/RBAC; DB 8 giữ trạng thái và quy tắc tiền. |
| BRD | v3.0 — 19/10/2026 | Phạm vi MVP, Definition of Done. |
| Parameter Register | v3.0 — 19/10/2026 | Tham số P-xx, S-xx, P-SIM-01, job J1–J6, NFR N-xx. |
| UI Screens \+ Figma wireframe | v2.0 — 19/10/2026 | Danh sách màn hình, dữ liệu mỗi màn hình cần. |

Phiên bản này đồng bộ Database RBAC v5.0 và xử lý các comment của Bùi Minh Quang. Mục 10.3 đối chiếu từng nhóm comment; mục 10.4 nêu tác động lên schema và chuyển đổi API. Tài liệu FRD/UI bên ngoài file này chưa được chỉnh sửa trong lần cập nhật này.

## **1.3 Cách đọc**

* **Mục 2–4** là quy ước chung (Khối A): mọi service phải tuân theo. Chốt trước khi code.  
    
* **Mục 5** là ma trận Use case → Service: dùng để phân công người phụ trách.  
    
* **Mục 6** đặc tả từng service: tổng quan, bảng endpoint, DTO, chi tiết từng endpoint.  
    
* **Mục 7–8** là API nội bộ, sự kiện và job nền giữa các service.  
    
* Ký hiệu: PUBLIC không cần đăng nhập; ANY\_AUTH là User ACTIVE với phiên hợp lệ; CUSTOMER/SELLER/ADMIN là vai trò, có thể cùng thuộc một User. Cột Quyền tại từng API ghi permission cụ thể. INTERNAL yêu cầu danh tính service và allowlist, không chỉ vị trí trong mạng nội bộ.  
    
* Mọi path trong tài liệu được viết **tương đối với base URL** /api/v1 (public) hoặc /internal/v1 (nội bộ).

# **2\. Kiến trúc API**

## **2.1 Thành phần**

| Thành phần | Vai trò |
| :---- | :---- |
| 3 Frontend | Customer web (gồm khách vãng lai), Seller web, Admin web — 3 tên miền riêng theo tài liệu UI Screens. |
| API Gateway | Định tuyến, kiểm tra JWT, loại bỏ header danh tính do client gửi, rate limit và CORS; service xác minh lại token và kiểm tra RBAC hiện hành. |
| 7 service | Identity & Seller · Product · Cart · Order (Order Inventory Fulfillment) · Payment · Finance · Notification. Mỗi service sở hữu database riêng (PostgreSQL), không join chéo DB. |
| Message broker \+ Outbox | Sự kiện bất đồng bộ giữa service (Mục 7.2). Mỗi service phát event qua bảng OutboxEvent, consumer chống lặp qua InboxEvent. |
| Object storage | Lưu ảnh/video; FE upload trực tiếp bằng presigned URL (N-08). |
| Bộ giả lập thanh toán nội bộ | Thành phần bên trong Payment service: cài đặt SimulatedGateway của interface PaymentGateway (charge, refund, query); không có nhà cung cấp bên ngoài, không có endpoint callback công khai (DB 8.4, N-01). |

## **2.2 Định tuyến tại Gateway**

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

## **2.3 Truyền danh tính từ Gateway xuống service**

* Gateway và service xác minh JWT bằng thuật toán cho phép, chữ ký, iss, aud, exp và sessionId. sub \= User.user\_id; không dùng X-Actor-Type để chọn bảng tài khoản. Gateway xóa mọi X-User-Id/X-Actor-Id/X-Actor-Type/X-Permissions từ client, chuyển tiếp Bearer và X-Request-Id qua kênh nội bộ tin cậy.  
    
* Mỗi service lấy sub từ JWT đã xác minh, gọi Identity authorization context để lấy trạng thái User, authzVersion, permissions và hồ sơ. Với API own, đối chiếu customerId/sellerId/recipientUserId của tài nguyên với sub; không tin owner ID trong body, query hay header client.  
    
* MVP không cache quyền bảo vệ: Identity không sẵn sàng → 503 SERVICE\_UNAVAILABLE, không cho qua. Token authzVersion cũ → 401 TOKEN\_STALE để client refresh. Mọi API vẫn kiểm tra trạng thái nghiệp vụ và ownership; trước checkout/thu tiền/rút tiền kiểm tra quyền hiện hành, sau đó cập nhật có điều kiện trong transaction dữ liệu sở hữu.  
    
* Gateway chặn mọi request từ bên ngoài vào /internal/\*\*.

# **3\. Quy ước chung**

## **3.1 Base URL và versioning**

| Môi trường | Base URL (đề xuất) |
| :---- | :---- |
| Local | http\://localhost:8080/api/v1 |
| Dev / Staging | https\://api-dev.\<domain\>/api/v1 |
| Production (demo chấm đồ án) | https\://api.\<domain\>/api/v1 |
| Mock server cho FE | Sinh từ OpenAPI (Prism) — http\://localhost:4010/api/v1 |

Thay đổi không tương thích ngược (đổi tên field, đổi kiểu, bỏ endpoint) phải tăng version hoặc được cả FE và BE đồng ý trước, ghi vào changelog ở Mục 10\.

## **3.2 Xác thực**

* Cơ chế: JWT Bearer. Header: Authorization: Bearer \<accessToken\>.  
    
* Access token sống ngắn, refresh token sống dài (đề xuất 15 phút / 7 ngày theo **P-06 — chưa chốt**).  
    
* Dùng duy nhất POST /identity/auth/login cho Customer, Seller và Admin. Một User có một bộ credential và có thể có nhiều vai trò; Customer/Seller là hồ sơ kế thừa, không phải tài khoản đăng nhập riêng. Ba web app có thể dùng cùng Auth API, không gửi actorType/role để chọn danh tính.  
    
* Refresh: POST /identity/auth/refresh. Refresh token được xoay vòng (rotation): mỗi lần refresh trả token mới và vô hiệu token cũ.  
    
* Logout thu hồi AuthSession của thiết bị hiện tại; access token thuộc session đã thu hồi cũng không dùng tiếp. Đổi/đặt lại mật khẩu thu hồi mọi phiên của User, yêu cầu đăng nhập lại; không tạo mật khẩu riêng cho từng vai trò.  
    
* Claims tối thiểu: sub (User.user\_id), iss, aud, iat, exp, jti, sessionId, authzVersion. roles nếu kèm chỉ là snapshot cho FE; service lấy permission hiện hành từ Identity. Không có actorType độc quyền, không đưa credential, email hay số điện thoại vào token.

### **Quy tắc tài khoản bị cấm (G-14, N-05)**

| Trường hợp | Đăng nhập | Hành vi API |
| :---- | :---- | :---- |
| Customer BANNED, đã có đơn thanh toán trước khi bị cấm | Được nếu User ACTIVE | Chặn tạo checkout/thanh toán; cho xem hồ sơ/giỏ/đơn cũ, hủy đủ điều kiện, xác nhận và đánh giá đơn cũ theo G-14. |
| Customer BANNED, chưa có đơn đã thanh toán | Được nếu User ACTIVE | Chặn API nghiệp vụ Customer; vẫn xem trạng thái tài khoản và dùng vai trò khác hợp lệ. Không vô hiệu User hay thu hồi CUSTOMER để biểu diễn hạn chế này. |
| Seller BANNED | Được nếu User ACTIVE | Chặn quyền bán và rút tiền; CUSTOMER hợp lệ vẫn dùng được. Không thu hồi phiên chung chỉ vì khóa Seller. |
| Seller PENDING / REJECTED | Được nếu User ACTIVE | Chưa cấp SELLER; chỉ xem hồ sơ/nộp lại qua policy seller:application:own và dùng vai trò khác. |
| User DISABLED | Không | Thu hồi mọi phiên, chặn mọi API bảo vệ trên tất cả vai trò; job/callback hoàn tiền vẫn chạy. |
| User nhiều vai trò | Một lần đăng nhập | Quyền là hợp các vai trò đang hiệu lực, vẫn chịu trạng thái hồ sơ và ownership tương ứng. |

### **3.2.1 Mô hình RBAC và hợp đồng kiểm tra quyền**

User lưu credential và danh tính chung; Customer.customer\_id và Seller.seller\_id vừa là PK vừa FK tới User.user\_id. Cả hai có thể cùng tồn tại. Role, Permission, UserRole, RolePermission là dữ liệu của Identity; quyền hiệu lực là hợp các role chưa thu hồi nhưng luôn kết hợp điều kiện User, hồ sơ và ownership. ADMIN không tự có quyền mua/bán. Mọi API bảo vệ theo permission cụ thể; không chỉ so sánh một chuỗi role.

Policy nền account:read:own, account:update:own, seller:application:own và tạo Customer lần đầu dành cho User ACTIVE kể cả chưa có vai trò. Các permission nghiệp vụ dưới đây được seed qua RolePermission theo Database RBAC v5.0. Bổ sung Customer bằng tự phục vụ không được khôi phục role đã bị quản trị thu hồi.

| Vai trò | Permission được seed | Điều kiện |
| :---- | :---- | :---- |
| CUSTOMER | customer:profile:own; cart:manage:own; checkout:create:own; payment:create:own; order:read:own; order:cancel:own; order:confirm:own; review:write:own; notification:read:own | User ACTIVE, hồ sơ của sub; Customer ACTIVE khi mua mới, G-14 cho đơn cũ. |
| SELLER | seller:profile:own; product:manage:own; inventory:manage:own; order:fulfill:own; wallet:read:own; withdrawal:create:own; notification:read:own | Chỉ cấp khi duyệt; User ACTIVE, Seller APPROVED/ACTIVE, sellerId=sub. |
| ADMIN | seller:approve; account:moderate; product:moderate; category:manage; operations:read:any; refund:retry; notification:read:own | Admin nội bộ; không tự duyệt; audit mọi thao tác, không sửa số dư hoặc xác nhận tay đã hoàn. |

Với các API đọc checkout/payment của chính Customer, dùng order:read:own; API hủy checkout dùng order:cancel:own. Với Notification, notification:read:own bao gồm đọc/đánh dấu/xóa phần hiển thị của chính mình. API media kiểm tra purpose và quyền tương ứng. Không mở CRUD Role/Permission hoặc endpoint tự gán ADMIN trong MVP.

Mỗi đổi UserRole/trạng thái truy cập tăng authzVersion trong transaction Identity và ghi audit/outbox. Thay RolePermission bằng migration phải tăng version mọi User liên quan. Phiên đã thu hồi và token version cũ bị chặn; refresh luôn tải lại quyền, không phát lại quyền từ token cũ.

G-14 được áp dụng đúng phạm vi: khóa Seller không khóa CUSTOMER; khóa Customer không khóa SELLER. Khi USER bị disable, mọi quyền bảo vệ bị chặn. Trước yêu cầu nhạy cảm phải lấy trạng thái mới; thao tác đã được chấp thuận trước khi Identity commit khóa có thể hoàn tất, không hứa transaction nguyên tử xuyên service. Job hoàn tiền và xử lý sự kiện vẫn dùng danh tính service độc lập.

### **3.2.2 Ví dụ response đăng nhập đa vai trò**

POST /api/v1/identity/auth/login với { "email": "[user@example.test](mailto:user@example.test)", "password": "\<password\>" }.

200 data: { accessToken, accessTokenExpiresIn, refreshToken, tokenType: "Bearer", profile: { userId, userStatus: "ACTIVE", roles: \["CUSTOMER", "SELLER"\], permissions: \[...\], authzVersion, customerProfile: { customerId: userId, status: "ACTIVE", paymentBlocked: false }, sellerProfile: { sellerId: userId, approvalStatus: "APPROVED", status: "ACTIVE", ... } } }. Ký hiệu ... là viết tắt DTO, không phải JSON mẫu hoàn chỉnh. FE chọn giao diện theo permission; lựa chọn giao diện không đổi sub hay cấp thêm quyền.

## **3.3 Format response chuẩn (envelope)**

Mọi response JSON dùng một trong hai dạng sau. FE chỉ cần một hàm xử lý chung.

**Thành công — một object:**

{  
"data": { "productId": "8b1f...", "productName": "Áo thun basic", "priceVnd": 150000 }  
}

**Thành công — danh sách có phân trang:**

{  
"data": \[ { ... }, { ... } \],  
"meta": { "page": 1, "size": 20, "totalItems": 135, "totalPages": 7 }  
}

**Lỗi:**

{  
"error": {  
"code": "INSUFFICIENT\_STOCK",  
"message": "Một số sản phẩm không đủ hàng",  
"details": \[  
{ "field": "items\[1\].quantity", "productId": "a71c...", "issue": "AVAILABLE\_LESS\_THAN\_REQUESTED", "availableQuantity": 2 }  
\],  
"requestId": "c0a8-..."  
}  
}

* code là mã ổn định, FE dùng để quyết định hiển thị. message là tiếng Việt, chỉ để debug hoặc hiển thị mặc định.  
    
* details dùng cho lỗi theo field (validation) hoặc theo dòng hàng (checkout, giỏ hàng).  
    
* requestId trùng header X-Request-Id, dùng tra log khi báo lỗi.  
    
* 204 No Content không có body.

## **3.4 HTTP status code**

| Status | Khi dùng |
| :---- | :---- |
| 200 OK | Đọc dữ liệu; cập nhật thành công; gửi lại request idempotent đã xử lý. |
| 201 Created | Tạo mới tài nguyên (đăng ký, checkout, thanh toán, rút tiền, review...). |
| 202 Accepted | Đã nhận yêu cầu, kết quả chưa rõ (thanh toán đang chờ kết quả từ bộ giả lập; quên mật khẩu). |
| 204 No Content | Xóa thành công; đăng xuất; đánh dấu đã đọc. |
| 400 Bad Request | Sai định dạng / thiếu field / vi phạm validation (VALIDATION\_ERROR). |
| 401 Unauthorized | Chưa đăng nhập, token sai hoặc hết hạn (UNAUTHENTICATED, TOKEN\_EXPIRED, INVALID\_CREDENTIALS). |
| 403 Forbidden | Đăng nhập rồi nhưng không đủ quyền hoặc bị chặn (FORBIDDEN, ACCOUNT\_BANNED, CUSTOMER\_PAYMENT\_BLOCKED...). |
| 404 Not Found | Không tồn tại **hoặc không thuộc quyền xem** của actor (không tiết lộ dữ liệu của người khác). |
| 409 Conflict | Xung đột trạng thái/dữ liệu: trùng email, sai trạng thái, idempotency key dùng lại với payload khác, version cũ, hết hàng. |
| 422 Unprocessable Entity | Dữ liệu hợp lệ về định dạng nhưng vi phạm quy tắc nghiệp vụ (rút dưới mức tối thiểu, sản phẩm không bán được...). |
| 429 Too Many Requests | Vượt rate limit (N-04). |
| 500 / 503 | Lỗi hệ thống / service phụ thuộc không sẵn sàng. |

## **3.5 Phân trang, lọc, sắp xếp**

| Tham số | Kiểu | Mặc định | Ghi chú |
| :---- | :---- | :---- | :---- |
| page | int ≥ 1 | 1 | Trang bắt đầu từ 1\. |
| size | int 1–100 | 20 | Theo đề xuất P-11 (chưa chốt). |
| sort | string | tùy endpoint | Dạng field,asc\|desc, ví dụ priceVnd,asc. Chỉ nhận field được liệt kê trong từng endpoint. |
| q | string ≤ 100 ký tự | — | Từ khóa tìm kiếm. Không phân biệt hoa thường; tìm không dấu chưa chốt (C-08). |
| from, to | date (YYYY-MM-DD) | — | Khoảng thời gian thống kê, tính theo GMT+7 (P-14 đề xuất). to bao gồm cả ngày đó. |

## **3.6 Quy ước dữ liệu**

| Loại | Quy ước | Ví dụ |
| :---- | :---- | :---- |
| Tên field JSON | camelCase tiếng Anh. DB dùng snake\_case; service tự map. | totalAmountVnd, sellerActionDueAt |
| ID | UUID v4 dạng chuỗi. | "3f2b9c1e-..." |
| Mã hiển thị | Đơn và phiên thanh toán không có mã hiển thị riêng: FE hiển thị orderId / checkoutSessionId (UUID; có thể rút gọn trên giao diện, tra cứu dùng UUID đầy đủ). Không có mã vận đơn. Chỉ giao dịch rút có withdrawalCode. | withdrawalCode: "RT-20261018-K2P9" |
| Thời gian | ISO 8601, luôn UTC với hậu tố Z. FE tự đổi sang giờ Việt Nam. | "2026-10-18T08:30:00Z" |
| Ngày (bộ lọc) | YYYY-MM-DD, hiểu theo GMT+7. | 2026-10-01 |
| Tiền | Số nguyên VND, field có hậu tố Vnd. Không dùng số thực. Phí vận chuyển luôn 0\. | "priceVnd": 150000 |
| Tỷ lệ hoa hồng | Số nguyên basis points (0–10000), hậu tố Bps. | "commissionBps": 500 (= 5%) |
| Enum | UPPER\_SNAKE\_CASE tiếng Anh; FE tự map sang nhãn tiếng Việt (Mục 4.2). | "status": "DELIVERED" |
| Giá trị rỗng | Field không có giá trị trả null, không bỏ field. Mảng rỗng trả \[\]. | "cancellation": null |
| Nhãn giả lập | Response có tiền/thanh toán/hoàn/ví trả thêm "isSimulated": true; FE hiển thị nhãn "Giả lập — không phát sinh tiền thật" (G-15). |  |

## **3.7 Idempotency (chống gửi lặp)**

* Các endpoint đánh dấu **Idempotency-Key: bắt buộc** yêu cầu header Idempotency-Key: \<uuid do FE sinh\>.  
    
* FE sinh key **một lần cho một ý định** (ví dụ khi mở popup xác nhận đặt hàng) và dùng lại đúng key đó khi bấm lặp hoặc retry do mất mạng.  
    
* Gửi lặp cùng key \+ payload sau khi kiểm tra quyền hiện hành → 200 với cùng kết quả nghiệp vụ; nếu tác vụ vẫn đang chờ thì 202\. Không tái tạo hiệu ứng. Header Idempotent-Replayed: true cho lần trả lại; lần tạo đầu giữ 201/202 theo endpoint.  
    
* Cùng key \+ payload khác → 409 IDEMPOTENCY\_KEY\_REUSED.  
    
* Phạm vi IdempotencyRecord: UNIQUE(actor\_user\_id, operation\_context, endpoint, key); operationContext CUSTOMER/SELLER/SERVICE do server xác định. UQ theo entity như UNIQUE(customer\_id, idempotency\_key) hoặc UNIQUE(seller\_id, idempotency\_key) vẫn giữ. Kiểm tra quyền/ownership trước khi trả kết quả đã lưu.  
    
* Idempotency-Key bắt buộc khi tạo checkout, thanh toán, rút tiền và tạo sản phẩm; productId được giữ ổn định qua bước tạo Inventory. Chuyển trạng thái đơn idempotent theo trạng thái đích, không cần header. Mỗi request vẫn kiểm tra quyền trước xử lý/replay.

## **3.8 Cập nhật đồng thời (optimistic locking)**

* Tài nguyên có thể bị sửa từ nhiều nơi (tồn kho UC-D03) trả field version. Client gửi lại version khi cập nhật.  
    
* Version không khớp → 409 VERSION\_CONFLICT, kèm dữ liệu mới nhất trong error.details\[0\].current để FE yêu cầu người dùng tải lại.  
    
* Chuyển trạng thái đơn không cần version: server dùng cập nhật có điều kiện; nếu trạng thái đã đổi (ví dụ J4 vừa tự hủy) → 409 INVALID\_STATE\_TRANSITION kèm currentStatus.

## **3.9 Upload ảnh/video (presigned URL)**

Áp dụng logo Seller và media sản phẩm/review. MVP chưa lưu avatar User; ảnh mặc định do FE hiển thị. Không upload file qua API service.

* **Bước 1:** FE gọi POST .../media/upload-urls với danh sách file (tên, MIME, dung lượng, mục đích).  
    
* **Bước 2:** Server kiểm tra loại và dung lượng theo P-08, trả uploadUrl (PUT, hết hạn sau 10 phút) và objectKey cho từng file.  
    
* **Bước 3:** FE PUT file trực tiếp lên uploadUrl với header Content-Type đúng MIME đã khai.  
    
* **Bước 4:** FE gửi objectKey trong request tạo/sửa sản phẩm, review hoặc hồ sơ. Server kiểm tra object tồn tại, đúng chủ sở hữu, đúng loại thực tế (magic bytes) trước khi lưu.  
    
* File upload nhưng không được gắn vào entity trong 24 giờ bị dọn (N-08).  
    
* Response trả media dưới dạng url đọc được (CDN hoặc URL ký có hạn), không trả objectKey cho người xem công khai.

| Mục đích (purpose) | Loại cho phép | Giới hạn đề xuất (P-08 — chưa chốt) |
| :---- | :---- | :---- |
| SELLER\_LOGO | Ảnh JPG/PNG/WebP | ≤2 MB (P-08 đề xuất) |
| PRODUCT\_MEDIA | Ảnh JPG/PNG/WebP; video MP4 (H.264/AAC) | Ảnh ≤ 5 MB; video ≤ 50 MB, ≤ 60 giây; ≤ 9 media/sản phẩm, ≤ 2 video |
| REVIEW\_MEDIA | Như trên | ≤ 5 media/review, ≤ 1 video |

## **3.10 Header chuẩn**

| Header | Chiều | Ghi chú |
| :---- | :---- | :---- |
| Authorization | Request | Bearer \<accessToken\> |
| Content-Type | Request/Response | application/json; charset=utf-8 |
| Idempotency-Key | Request | Xem 3.7 |
| X-Request-Id | Request (tùy chọn) / Response | Gateway sinh nếu FE không gửi; có trong mọi log. |
| Accept-Language | Request | Mặc định vi. MVP chỉ hỗ trợ tiếng Việt. |

# **4\. Danh mục mã lỗi và enum dùng chung**

## **4.1 Bảng mã lỗi**

Mỗi service chỉ trả mã trong bảng này. Thêm mã mới phải cập nhật bảng trước.

| Mã lỗi (code) | HTTP | Ý nghĩa / khi nào |
| :---- | :---- | :---- |
| VALIDATION\_ERROR | 400 | Thiếu field, sai định dạng, vi phạm độ dài/khoảng giá trị. details liệt kê từng field. |
| PASSWORD\_POLICY\_VIOLATION | 400 | Mật khẩu \< 8 ký tự hoặc thiếu chữ hoa / chữ thường / chữ số (G-07). |
| UNAUTHENTICATED | 401 | Không có token hoặc token không hợp lệ. |
| TOKEN\_EXPIRED | 401 | Access token hết hạn → FE gọi refresh. |
| INVALID\_CREDENTIALS | 401 | Sai email/mật khẩu. Thông báo chung, không tiết lộ tài khoản có tồn tại. |
| INVALID\_REFRESH\_TOKEN | 401 | Refresh token sai, hết hạn hoặc đã bị thu hồi → FE đưa về trang đăng nhập. |
| FORBIDDEN | 403 | Actor không có quyền với endpoint/tài nguyên. |
| ACCOUNT\_BANNED | 403 | Hồ sơ CUSTOMER/SELLER bị hạn chế trong scope trả về; không đồng nghĩa User DISABLED. |
| SELLER\_PENDING\_APPROVAL | 403 | Seller chưa được duyệt. |
| SELLER\_REJECTED | 403 | Hồ sơ Seller bị từ chối. details\[0\].reason \= lý do. |
| CUSTOMER\_PAYMENT\_BLOCKED | 403 | Customer bị cấm sau khi đã có đơn thanh toán thành công: chỉ chặn checkout/thanh toán mới. |
| WRONG\_AUTH\_METHOD | 409 | Email đã đăng ký bằng phương thức khác (Google ↔ mật khẩu). details\[0\].authMethod \= phương thức đúng. |
| GOOGLE\_AUTH\_FAILED | 401 | Google không xác minh được email hoặc người dùng từ chối. |
| NOT\_FOUND | 404 | Tài nguyên không tồn tại hoặc không thuộc quyền xem. details\[0\].resource \= tên tài nguyên. |
| EMAIL\_ALREADY\_USED | 409 | Email đã gắn với tài khoản khác trong toàn hệ thống (G-01). |
| PHONE\_ALREADY\_USED | 409 | Số điện thoại đã được dùng. |
| SELLER\_NAME\_ALREADY\_USED | 409 | Tên cửa hàng (fullName của User) trùng với User khác có Seller PENDING/APPROVED. |
| SELLER\_NAME\_LOCKED | 409 | User có Seller PENDING/APPROVED không đổi fullName vì đó là tên cửa hàng (G-02). |
| RESET\_TOKEN\_INVALID | 400 | Liên kết đặt lại mật khẩu đã dùng hoặc hết hạn (P-05). |
| PASSWORD\_INCORRECT | 400 | Mật khẩu hiện tại không đúng (đổi mật khẩu). |
| PASSWORD\_SAME\_AS\_OLD | 400 | Mật khẩu mới trùng mật khẩu cũ. |
| ADDRESS\_LIMIT\_EXCEEDED | 422 | Vượt số địa chỉ tối đa P-09. |
| INVALID\_REGION | 422 | Mã phường/xã không thuộc tỉnh/thành đã chọn. |
| IDEMPOTENCY\_KEY\_REUSED | 409 | Cùng Idempotency-Key nhưng payload khác lần đầu. |
| IDEMPOTENCY\_KEY\_REQUIRED | 400 | Thiếu header Idempotency-Key ở endpoint bắt buộc. |
| VERSION\_CONFLICT | 409 | Dữ liệu đã bị thay đổi ở nơi khác (tồn kho). Tải lại rồi thử lại. |
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
| QUANTITY\_LIMIT\_EXCEEDED | 422 | Vượt số lượng tối đa mỗi dòng hoặc số dòng giỏ (P-10). |
| STOCK\_BELOW\_RESERVED | 422 | Tồn kho mới thấp hơn lượng đang giữ. details\[0\].minAllowed. |
| PROFILE\_INCOMPLETE | 422 | Thiếu số điện thoại hoặc chưa có địa chỉ nhận hàng trước checkout. details chỉ rõ thiếu gì. |
| CHECKOUT\_PENDING\_EXISTS | 409 | Customer đã có một phiên chờ thanh toán. details\[0\].checkoutSessionId. |
| CHECKOUT\_ITEMS\_INVALID | 409 | Một hoặc nhiều dòng hàng không hợp lệ khi đặt hàng; không tạo đơn nào. details theo dòng. |
| CHECKOUT\_NOT\_PAYABLE | 409 | Phiên đã hết hạn, đã hủy, đã thanh toán hoặc thanh toán thất bại. |
| PAYMENT\_IN\_PROGRESS | 409 | Đang có thanh toán chưa rõ kết quả: không cho thanh toán lại hoặc hủy phiên. |
| ORDER\_NOT\_CANCELLABLE | 409 | Đơn đã đóng gói / đã giao / đã xác nhận / đã hủy. |
| SELLER\_DEADLINE\_PASSED | 409 | Đã quá hạn 7 ngày phản hồi; hệ thống đang/đã tự hủy (J4). |
| REVIEW\_NOT\_ALLOWED | 403 | Không phải người mua, đơn chưa xác nhận nhận hàng hoặc đã hủy. |
| REVIEW\_ALREADY\_EXISTS | 409 | Đã có review cho cặp Customer–Product; không cho tạo lại hay sửa qua API trong MVP. |
| WITHDRAWAL\_BELOW\_MINIMUM | 422 | Số tiền rút \< 10.000 VND (P-WD-01). |
| INSUFFICIENT\_AVAILABLE\_BALANCE | 409 | Số tiền rút lớn hơn số dư khả dụng tại thời điểm xử lý. |
| WALLET\_FROZEN | 403 | Ví bị đóng băng do Seller bị cấm (WD-09). |
| WALLET\_INCONSISTENT | 409 | Phát hiện lệch giữa số dư và tổng bút toán; tạm chặn rút, chờ Admin. |
| REFUND\_RETRY\_NOT\_SAFE | 409 | Khoản hoàn chưa tra cứu được kết quả hoặc không ở trạng thái FAILED. |
| RATE\_LIMITED | 429 | Quá số lần gọi cho phép. |
| INTERNAL\_ERROR | 500 | Lỗi không xác định. |
| SERVICE\_UNAVAILABLE | 503 | Service phụ thuộc không phản hồi; có thể thử lại. |
| TOKEN\_STALE | 401 | authzVersion token không còn hiện hành; refresh lấy quyền mới. |
| ACCOUNT\_DISABLED | 403 | User DISABLED, khóa mọi vai trò và thu hồi phiên. |
| PROFILE\_REQUIRED | 403 | Có permission nhưng thiếu hồ sơ nghiệp vụ tương ứng. |
| SELF\_APPROVAL\_FORBIDDEN | 403 | Admin không được duyệt hồ sơ Seller của chính mình. |
| NOTIFICATION\_TYPE\_NOT\_ALLOWED | 403 | Service producer không được tạo loại thông báo hoặc kênh này. |
| REGION\_SOURCE\_UNAVAILABLE | 503 | Nguồn API địa giới chưa cấu hình, timeout hoặc không có dữ liệu cache hợp lệ. |

## **4.2 Enum**

| Enum | Giá trị (nhãn hiển thị) | Nguồn |
| :---- | :---- | :---- |
| RoleCode | CUSTOMER, SELLER, ADMIN; một User có thể nhận nhiều giá trị | DB 3.1.5–3.1.9 |
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
| CustomerCancelReason | CHANGED\_MIND, ORDERED\_BY\_MISTAKE, FOUND\_BETTER\_PRICE, ADDRESS\_CHANGE, OTHER (P-21a — chưa chốt) | Param 4.5 |
| SellerRejectReason | DEFECTIVE, OUT\_OF\_STOCK\_ACTUAL, CANNOT\_DELIVER, OTHER (P-21b — chưa chốt) | Param 4.5 |
| PaymentStatus | PENDING (Đang xác nhận), SUCCESS (Thành công), FAILED (Thất bại) | DB 3.4.1 |
| PaymentRefundState | NONE, PARTIALLY\_REFUNDED, REFUNDED (trường tính toán, tương ứng DB 3.4) | DB 3.4 |
| RefundScope | ORDER, FULL\_CHECKOUT | DB 3.4.2 |
| RefundReason | CUSTOMER\_CANCEL (khách hủy), SELLER\_REJECT (Seller từ chối), SELLER\_CANCEL (đơn bị hủy do Seller bị cấm hoặc không còn hợp lệ — xem C-13), SELLER\_TIMEOUT (J4 quá 7 ngày), LATE\_PAYMENT (thu muộn, hoàn toàn bộ) | DB 3.4.2 |
| RefundStatus | PENDING (Đang hoàn), SUCCESS (Đã hoàn), FAILED (Hoàn lỗi) | DB 3.4 |
| SettlementStatus | PENDING (Chờ quyết toán), SETTLED (Đã quyết toán), PENDING\_REFUND (Chờ hoàn), REFUNDED (Đã hoàn) | DB 3.5.1 |
| WalletOwnerType | SELLER, PLATFORM | DB 3.5, 8.3 |
| WalletTransactionType | PAYMENT\_RECEIVED (ghi chờ khi thanh toán), SETTLEMENT\_RELEASED (chờ → khả dụng khi đơn xác nhận), REFUND\_DEBIT (trừ chờ khi hoàn tiền thành công), WITHDRAWAL\_COMPLETED (rút tiền) | DB 3.5.3 |
| NotificationChannel | EMAIL, IN\_APP | DB 3.7 |
| NotificationType | CHECKOUT\_PAID, NEW\_ORDER, ORDER\_DELIVERED, ORDER\_REJECTED, ORDER\_CANCELLED, ORDER\_AUTO\_CANCELLED, ORDER\_CONFIRMED, ORDER\_AUTO\_CONFIRMED, REFUND\_SUCCEEDED, FUNDS\_AVAILABLE, WITHDRAWAL\_COMPLETED, SELLER\_REGISTERED, SELLER\_APPROVED, SELLER\_REJECTED, ACCOUNT\_BANNED, ACCOUNT\_UNBANNED, USER\_DISABLED, USER\_ENABLED, PRODUCT\_REMOVED, PAYMENT\_FAILED, CHECKOUT\_EXPIRED | UC-G01; enum mới phải đồng bộ producer và Notification |
| AuditAction | APPROVE\_SELLER, REJECT\_SELLER, BAN, UNBAN, DISABLE\_USER, ENABLE\_USER, GRANT\_ROLE, REVOKE\_ROLE, REMOVE\_PRODUCT, RESTORE\_PRODUCT, RETRY\_REFUND, QUERY\_REFUND, MARK\_HANDLED, MANAGE\_CATEGORY | DB mục 6; audit nội bộ, không mở endpoint xem log |
| UserStatus | ACTIVE / DISABLED | DB 3.1.1 |
| ModerationScope | USER / CUSTOMER / SELLER; bắt buộc khi ban/unban | G-14 |
| AudienceContext | CUSTOMER / SELLER / ACCOUNT; ngữ cảnh thông báo, không cấp quyền | DB 3.7 |

### **Tab trạng thái đơn trên giao diện (gợi ý cho FE)**

| Màn hình | Tab | Query status |
| :---- | :---- | :---- |
| Customer — Đơn mua | Tất cả / Chờ thanh toán / Đang xử lý / Chờ xác nhận / Hoàn tất / Đã hủy | — / AWAITING\_PAYMENT / PAID,PACKING / DELIVERED / CONFIRMED / CANCELLED,PAYMENT\_FAILED,EXPIRED |
| Seller — Quản lý đơn | Tất cả / Chờ xử lý / Đang đóng gói / Đã giao / Đã xác nhận / Đã hủy | — / PAID / PACKING / DELIVERED / CONFIRMED / CANCELLED |

# **5\. Ma trận Use case → Service**

Mỗi use case có **một service owner** (service sở hữu dữ liệu chính mà use case tạo ra hoặc thay đổi). Người phụ trách service owner viết endpoint public và mô tả luồng; service phụ thuộc cung cấp internal API hoặc tiêu thụ event. Cột "Người phụ trách" để nhóm điền.

| UC | Tên | Actor | Owner | Phụ thuộc | Phụ trách |
| :---- | :---- | :---- | :---- | :---- | :---- |
| A01 | Đăng ký Customer (email; Google — S) | Customer | Identity | — |  |
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
| E03 | Cấm / gỡ cấm | Admin | Identity | Order (hủy đơn chưa giao của Seller), Finance (đóng băng ví), Notification |  |
| E04 | Gỡ / khôi phục sản phẩm | Admin | Product | Notification |  |
| E06 | Duyệt Seller | Admin | Identity | Finance (tạo ví), Notification |  |
| E07 | Quản lý danh mục (S) | Admin | Product | — |  |
| E08 | Xem đơn, xử lý tiền gặp sự cố | Admin | Payment (Refund) \+ Order (xem đơn) \+ Finance (ví) | — |  |
| F01 | Gợi ý sản phẩm liên quan (C) | Public | Product | Order (loại sản phẩm hết hàng) |  |
| G01 | Thông báo | System | Notification | Mọi service (event) |  |
| G02 | Hoàn tiền | System | Payment | Order (event hủy), Finance |  |
| G03 | Job quá hạn (J1–J6) | System | Order (J1, J3, J4), Payment (J2, J2b, J5), Notification (J6) | — |  |
| G04 | Quyết toán, ghi có ví | System | Finance | Order (event CONFIRMED) |  |

## **5.1 Ownership dữ liệu (theo Database RBAC v5.0)**

| Service | Entity sở hữu | Không tự ý ghi |
| :---- | :---- | :---- |
| Identity & Seller | User, Customer, Seller, CustomerAddress, Role, Permission, UserRole, RolePermission, AuthSession, PasswordResetToken, AdminAuditLog | Product, Order, Payment; service khác không sửa RBAC |
| Product | Category, Product (gồm media JSONB), Review | Inventory, OrderItem |
| Cart | Cart, CartItem | Order, Reservation |
| Order | CheckoutSession, Order, OrderItem, Inventory, Reservation, StockMovement | Payment, Refund, Settlement, Wallet |
| Payment | Payment, Refund | Order.status, Settlement |
| Finance | Settlement, Wallet, WalletTransaction, Withdrawal | Payment, Refund, Order |
| Notification | Notification | Dữ liệu nghiệp vụ của service khác |

# **6\. Đặc tả theo service**

Mỗi service gồm: phạm vi, bảng endpoint, DTO và chi tiết endpoint. Các DTO chỉ liệt kê field; kiểu dữ liệu theo quy ước 3.6 và Database RBAC v5.0.

## **6.1 Identity & Seller Service**

**Phạm vi: Auth API chung, User và RBAC, hồ sơ Customer/Seller, sổ địa chỉ, proxy API địa giới, tra cứu Seller công khai, duyệt và khóa theo scope. Admin là vai trò, không có AdminAccount riêng. Ghi AdminAuditLog nội bộ; bỏ endpoint public xem audit theo comment. UC A01–A07, B02, E01, E03, E06.**

### **Danh sách endpoint**

| \# | Method | Path | Mô tả | Quyền | UC |
| :---- | :---- | :---- | :---- | :---- | :---- |
| 1 | POST | /identity/auth/register | Đăng ký Customer bằng email | PUBLIC | A01 |
| 2 | POST | /identity/auth/login | Đăng nhập chung User cho cả ba web app | PUBLIC | A03 |
| 3 | POST | /identity/auth/google | Đăng ký / đăng nhập Customer bằng Google (Should) | PUBLIC | A01, A03 |
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
| 28 | POST | /identity/admin/ban/{userId} | Cấm User hoặc hồ sơ theo scope bắt buộc | account:moderate | E03 |
| 29 | POST | /identity/admin/unban/{userId} | Gỡ cấm theo scope | account:moderate | E03 |
| 30 | POST | /identity/users/me/seller-application | Nộp hoặc nộp lại hồ sơ Seller | seller:application:own | A02 |
| 31 | GET | /identity/users/me/seller-application | Xem tiến độ hồ sơ của mình | seller:application:own | A02 |
| 32 | POST | /identity/users/me/customer-profile | Bổ sung hồ sơ Customer cho User hiện tại | ANY\_AUTH (policy nền) | A01 |

### **DTO**

| DTO | Field |
| :---- | :---- |
| CustomerProfile | customerId (= userId), status, banReason, paymentBlocked, accessMode (FULL \| PAYMENT\_BLOCKED \| BLOCKED), profileComplete, createdAt; không lưu credential |
| SellerSelfProfile | sellerId (= userId), description, logoUrl, approvalStatus, status, rejectionReason, banReason, registeredAt, reviewedAt; tên cửa hàng là UserSelfProfile.fullName, số liên hệ là UserSelfProfile.phoneNumber (không có sellerName/contactPhone riêng) |
| UserSelfProfile | userId, email, authMethod, fullName, phoneNumber, userStatus, roles\[\], permissions\[\], authzVersion, customerProfile: CustomerProfile\|null, sellerProfile: SellerSelfProfile\|null, createdAt |
| SellerPublic | sellerId, sellerName, description, logoUrl. Ở mọi DTO đọc (SellerPublic, ProductCard, Cart, OrderSummary, Wallet…), sellerName là tên cửa hàng đọc từ users.full\_name, không phải cột riêng. |
| SellerAdminView | { user: UserSummary (userId, email, fullName \= tên cửa hàng, phoneNumber \= số liên hệ, userStatus), sellerProfile: SellerSelfProfile, reviewedByUserId }; không trả passwordHash/googleSubject/token |
| Address | addressId, recipientName, recipientPhone, provinceCode, provinceName, wardCode, wardName, addressDetail, lastUsedAt, isDefault theo DB 3.1.4, createdAt |
| AuthTokens | accessToken, accessTokenExpiresIn, tokenType=Bearer, refreshToken, profile: UserSelfProfile; permission list chỉ để FE hiển thị, backend kiểm tra lại |
| UserSummary | userId, email, fullName, phoneNumber, userStatus; chỉ DTO quản trị có operations:read:any, không dùng cho public Seller |

### **Chi tiết endpoint**

**POST   /identity/auth/register  —  Đăng ký Customer bằng email**

**Quyền: PUBLIC    UC: A01**

* Trong một transaction tạo User LOCAL \+ Customer (customerId \= userId) \+ UserRole CUSTOMER; email unique tại User, không dùng LoginIdentifier.  
    
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

201 → data: UserSelfProfile (User \+ customerProfile; không trả token, đăng nhập qua Auth API chung).

POST /api/v1/identity/auth/register  
{ "email": "[an.nguyen@gmail.com](mailto:an.nguyen@gmail.com)", "fullName": "Nguyễn Văn An",  
"phoneNumber": "0912345678", "password": "Matkhau123", "confirmPassword": "Matkhau123" }

201 Created  
{ "data": { "userId": "0b6f...", "email": "[an.nguyen@gmail.com](mailto:an.nguyen@gmail.com)", "authMethod": "LOCAL",  
"fullName": "Nguyễn Văn An", "phoneNumber": "0912345678", "userStatus": "ACTIVE",  
"roles": \["CUSTOMER"\], "customerProfile": { "customerId": "0b6f...", "status": "ACTIVE", "paymentBlocked": false },  
"sellerProfile": null, "authzVersion": 1, "createdAt": "2026-10-25T08:30:00Z" } }

**Lỗi nghiệp vụ**

* EMAIL\_ALREADY\_USED (409), PHONE\_ALREADY\_USED (409) — details chỉ rõ field trùng  
    
* PASSWORD\_POLICY\_VIOLATION (400), VALIDATION\_ERROR (400)

**POST  /identity/auth/login  —  Đăng nhập chung cho Customer Seller Admin**

**Quyền: PUBLIC    UC: A03**

* Xác thực User bằng email/password chung. Seller PENDING/REJECTED hoặc hồ sơ bị BANNED vẫn đăng nhập nếu User ACTIVE; quyền nghiệp vụ bị chặn theo scope. User DISABLED trả 403 ACCOUNT\_DISABLED. Không nhận role/actorType trong request và không cấp ADMIN qua login.

**Request body (application/json)**

| Field | Kiểu | Bắt buộc | Validation / ghi chú |
| :---- | :---- | :---- | :---- |
| email | string | ✓ |  |
| password | string | ✓ |  |

**Response thành công**

200 → data: AuthTokens; profile là UserSelfProfile có cả customerProfile và sellerProfile nếu có.

200 OK  
{ "data": { "accessToken": "eyJhbGciOi...", "accessTokenExpiresIn": 900,  
"refreshToken": "rt\_7Hq...", "tokenType": "Bearer",  
"profile": { "userId": "0b6f...", "roles": \["CUSTOMER", "SELLER"\], "permissions": \["cart:manage:own", "order:fulfill:own"\], "authzVersion": 3, "customerProfile": { "customerId": "0b6f...", "status": "ACTIVE" }, "sellerProfile": { "sellerId": "0b6f...", "approvalStatus": "APPROVED", "status": "ACTIVE" } } } }

**Lỗi nghiệp vụ**

* INVALID\_CREDENTIALS (401)  
    
* WRONG\_AUTH\_METHOD (409) — email đăng ký bằng Google  
    
* ACCOUNT\_DISABLED (403); hồ sơ bị cấm không làm login chung thất bại.

**POST   /identity/auth/google  —  Đăng ký / đăng nhập Customer bằng Google (Should)**

**Quyền: PUBLIC    UC: A01, A03**

* FE lấy idToken từ Google Identity Services rồi gửi lên. BE verify với Google, lấy email \+ họ tên.  
    
* Google subject mới và email chưa dùng → tạo User GOOGLE \+ Customer \+ CUSTOMER nguyên tử, trả isNewAccount=true; email verified và audience/issuer token phải hợp lệ.  
    
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
    
* ACCOUNT\_DISABLED (403); quyền hồ sơ bị hạn chế theo G-14.

**POST   /identity/auth/register-seller  —  Đăng ký bán hàng**

**Quyền: PUBLIC    UC: A02**

* Tạo User LOCAL \+ Seller PENDING, status ACTIVE, sellerId=userId trong một transaction; chưa cấp SELLER và chưa tạo ví. User đã có tài khoản phải login rồi POST /identity/users/me/seller-application, không gửi lại password/email qua luồng đó.  
    
* Email duy nhất trong User, vẫn giữ khi Seller REJECTED. fullName là tên cửa hàng, lưu tại users.full\_name, không trùng User khác có Seller PENDING/APPROVED (Identity kiểm tra trong transaction). phoneNumber lưu tại users.phone\_number, unique toàn hệ thống. Không có sellerName hay contactPhone riêng; tái nộp dùng cùng sellerId.  
    
* Phát event seller.registered để Notification gửi email xác nhận đã nhận hồ sơ.

**Request body (application/json)**

| Field | Kiểu | Bắt buộc | Validation / ghi chú |
| :---- | :---- | :---- | :---- |
| email | string | ✓ | Duy nhất toàn hệ thống |
| fullName | string | ✓ | Tên cửa hàng, 1–150 ký tự (users.full\_name); không trùng tên cửa hàng PENDING/APPROVED; không đổi được khi hồ sơ PENDING/APPROVED |
| phoneNumber | string | ✓ | SĐT Việt Nam, duy nhất toàn hệ thống (users.phone\_number); dùng làm số liên hệ cửa hàng |
| password | string | ✓ | G-07 |
| confirmPassword | string | ✓ |  |

**Response thành công**

201 → data: UserSelfProfile với sellerProfile.approvalStatus=PENDING, roles=\[\] nếu chưa có hồ sơ khác. User đăng nhập và xem hồ sơ qua policy nền, chưa gọi API SELLER.

**Lỗi nghiệp vụ**

* EMAIL\_ALREADY\_USED, PHONE\_ALREADY\_USED, SELLER\_NAME\_ALREADY\_USED (409)  
    
* PASSWORD\_POLICY\_VIOLATION (400)

**POST   /identity/auth/refresh**  —  Làm mới access token

**Quyền: PUBLIC    UC: A03**

* Xoay vòng refresh token. Token cũ dùng lại sau khi đã xoay → thu hồi toàn bộ chuỗi token đó (phát hiện đánh cắp).  
    
* User DISABLED hoặc session bị thu hồi → INVALID\_REFRESH\_TOKEN (401). Hồ sơ BANNED/PENDING không tự thu hồi phiên chung; quyền hiện hành tính lại từ Identity.

**Request body (application/json)**

| Field | Kiểu | Bắt buộc | Validation / ghi chú |
| :---- | :---- | :---- | :---- |
| refreshToken | string | ✓ |  |

**Response thành công**

200 → data: AuthTokens với UserSelfProfile, roles và permissions hiện hành; không sao chép role từ access token cũ.

**Lỗi nghiệp vụ**

* INVALID\_REFRESH\_TOKEN (401)  
    
* INVALID\_REFRESH\_TOKEN (401) nếu phiên không còn hiệu lực.

**POST   /identity/auth/logout**  —  Đăng xuất thiết bị hiện tại

**Quyền: ANY\_AUTH (session own)    UC: A07**

* Thu hồi AuthSession gửi lên sau khi xác minh refresh token thuộc cùng User/session; token đã hết hạn/đã logout vẫn trả 204\. Không cho dùng token người khác để thu hồi phiên không thuộc mình.

**Request body (application/json)**

| Field | Kiểu | Bắt buộc | Validation / ghi chú |
| :---- | :---- | :---- | :---- |
| refreshToken | string | ✓ |  |

**Response thành công**

204

**POST   /identity/auth/password/forgot**  —  Yêu cầu đặt lại mật khẩu

**Quyền: PUBLIC    UC: A05**

* **Luôn** trả cùng một response dù email tồn tại hay không, là tài khoản Google hay Seller chưa duyệt (G-05). Xem điểm C-09 về mâu thuẫn trong UC-A05.  
    
* Nếu đủ điều kiện: tạo PasswordResetToken tham chiếu User.user\_id, chỉ lưu hash; gửi link trên một origin reset tin cậy đã cấu hình, không chọn tùy actor hoặc redirect URL từ client. Token hạn P-05.

**Request body (application/json)**

| Field | Kiểu | Bắt buộc | Validation / ghi chú |
| :---- | :---- | :---- | :---- |
| email | string | ✓ |  |

**Response thành công**

202 → data: { message: "Nếu email hợp lệ, hướng dẫn đặt lại mật khẩu đã được gửi." }

**Lỗi nghiệp vụ**

* RATE\_LIMITED (429)

**POST   /identity/auth/password/reset**  —  Đặt mật khẩu mới bằng liên kết

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

**PUT   /identity/users/me/password  —  Đổi mật khẩu**

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

**GET   /identity/users/me  —  Hồ sơ của actor đang đăng nhập**

**Quyền: account:read:own    UC: A06**

**Response thành công**

200 → data: UserSelfProfile; gồm userId, userStatus, roles\[\], permissions\[\], authzVersion và hai hồ sơ tùy chọn. Không đổi DTO theo một actorType độc quyền.

**PATCH  /identity/users/me  —  Cập nhật thông tin User chung**

**Quyền: account:update:own    UC: A06**

* PATCH allowlist fullName, phoneNumber. Không sửa email, authMethod, userId, role, permission, status hoặc authzVersion. Field ngoài allowlist trả 400, không mass assignment.

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

**PATCH   /identity/sellers/me**  —  Seller cập nhật hồ sơ người bán

**Quyền: seller:profile:own    UC: A06**

* Không sửa email, tên cửa hàng (users.full\_name) hay số điện thoại tại đây; số điện thoại sửa qua PATCH /identity/users/me.

**Request body (application/json)**

| Field | Kiểu | Bắt buộc | Validation / ghi chú |
| :---- | :---- | :---- | :---- |
| description | string \| null |  | ≤ 2.000 ký tự |
| logoObjectKey | string \| null |  | purpose SELLER\_LOGO |

**Response thành công**

200 → data: SellerSelfProfile

**Lỗi nghiệp vụ**

* MEDIA\_INVALID (422)

**POST   /identity/media/upload-urls**  —  Xin URL upload logo Seller

**Quyền: seller:profile:own    UC: A06**

* Chỉ purpose SELLER\_LOGO, cần seller:profile:own và Seller APPROVED/ACTIVE. Không hỗ trợ AVATAR vì Database RBAC v5.0 chưa có field lưu avatar; hồ sơ User hiển thị avatar mặc định tại FE.

**Request body (application/json)**

| Field | Kiểu | Bắt buộc | Validation / ghi chú |
| :---- | :---- | :---- | :---- |
| files\[\] | array (1) | ✓ | { fileName, mimeType, sizeBytes, purpose: SELLER\_LOGO }; cần seller:profile:own |

**Response thành công**

200 → data: \[{ objectKey, uploadUrl, expiresAt }\]

**Lỗi nghiệp vụ**

* MEDIA\_INVALID (422)

**GET   /identity/customers/me/addresses**  —  Danh sách sổ địa chỉ

**Quyền: customer:profile:own    UC: A06, B04**

* Địa chỉ của customerId \= sub; mặc định trước theo DB 3.1.4, rồi lastUsedAt giảm dần và createdAt tăng dần. Mọi chi tiết/sửa/xóa kiểm tra ownership.

**Response thành công**

200 → data: Address\[\], meta: { maxAddresses }

**POST   /identity/customers/me/addresses**  —  Thêm địa chỉ

**Quyền: customer:profile:own    UC: A06**

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

**PUT   /identity/customers/me/addresses/{addressId}**  —  Sửa địa chỉ

**Quyền: customer:profile:own    UC: A06**

* Body giống thêm mới. Không ảnh hưởng địa chỉ snapshot của các đơn đã tạo.

**Response thành công**

200 → data: Address

**Lỗi nghiệp vụ**

* NOT\_FOUND (404)  
    
* INVALID\_REGION (422)

**DELETE   /identity/customers/me/addresses/{addressId}**  —  Xóa địa chỉ

**Quyền: customer:profile:own    UC: A06**

* Cho phép xóa cả địa chỉ cuối cùng. Xóa vật lý (hard delete) theo DB 3.1.4; Order đã lưu snapshot nên không phụ thuộc, và địa chỉ đã xóa không còn tính vào giới hạn P-09.

**Response thành công**

204

**Lỗi nghiệp vụ**

* NOT\_FOUND (404)

**GET  /identity/regions/provinces  —  Proxy API danh mục tỉnh thành**

**Quyền: PUBLIC    UC: A06**

**Response thành công**

200 → data: \[{ code, name }\], meta: { sourceVersion, fetchedAt }. Identity gọi RegionProvider qua HTTPS; không query bảng địa giới cục bộ.

**GET  /identity/regions/provinces/{provinceCode}/wards  —  Proxy API phường xã theo tỉnh**

**Quyền: PUBLIC    UC: A06**

**Response thành công**

200 → data: \[{ code, name, provinceCode }\], meta: { sourceVersion, fetchedAt }. Lấy từ cùng RegionProvider/version với danh mục tỉnh.

**Lỗi nghiệp vụ**

* INVALID\_REGION (422) với mã không hợp lệ; REGION\_SOURCE\_UNAVAILABLE (503) nếu thiếu cấu hình/timeout và không có cache hợp lệ.

**GET   /identity/sellers**  —  Tìm kiếm người bán

**Quyền: PUBLIC    UC: B02**

* Chỉ trả Seller thỏa G-06 về User ACTIVE, hồ sơ APPROVED/ACTIVE và role SELLER hiện hành. Tìm theo tên cửa hàng (users.full\_name), không phân biệt hoa thường; tìm không dấu còn mở tại C-08.

**Tham số (path / query)**

| Tên | Vị trí | Kiểu | Bắt buộc | Mô tả / ràng buộc |
| :---- | :---- | :---- | :---- | :---- |
| q | query | string |  | Từ khóa |
| page, size | query | int |  | Mặc định 1, 20 |

**Response thành công**

200 → data: SellerPublic\[\] \+ meta

**GET   /identity/sellers/{sellerId}**  —  Thông tin công khai của người bán

**Quyền: PUBLIC    UC: B02**

* Dùng API PUBLIC này cho cả khách vãng lai, Customer, Seller và Admin khi xem trang shop. Chỉ trả SellerPublic; không lộ email, phone, lý do duyệt/cấm. Seller không hợp lệ theo G-06 →404. Sản phẩm lấy qua GET /products?sellerId=.

**Response thành công**

200 → data: SellerPublic

**Lỗi nghiệp vụ**

* NOT\_FOUND (404)

**GET   /identity/admin/customers**  —  Danh sách Customer

**Quyền: operations:read:any    UC: E01**

**Tham số (path / query)**

| Tên | Vị trí | Kiểu | Bắt buộc | Mô tả / ràng buộc |
| :---- | :---- | :---- | :---- | :---- |
| q | query | string |  | Tìm theo tên, email, số điện thoại |
| status | query | ProfileStatus |  |  |
| page, size, sort | query |  |  | sort: createdAt, fullName |

**Response thành công**

200 → data: CustomerAdminView\[\] \+ meta

**GET   /identity/admin/customers/{customerId}**  —  Chi tiết Customer

**Quyền: operations:read:any    UC: E01**

* Số đơn của khách lấy qua GET /orders/admin/orders?customerId= (FE gọi thêm).

**Response thành công**

200 → data: CustomerAdminView \= { user: UserSummary, customerProfile: CustomerProfile, hasPaidOrder, banReason }. Không trả banHistory/AuditEntry từ DB service khác.

**GET   /identity/admin/sellers**  —  Danh sách Seller (gồm hồ sơ chờ duyệt)

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

**GET   /identity/admin/sellers/{sellerId}**  —  Chi tiết Seller

**Quyền: operations:read:any    UC: E01, E06**

* Tóm tắt ví lấy qua GET /finance/admin/wallets?sellerId=.

**Response thành công**

200 → data: SellerAdminView. Customer xem SellerPublic qua GET /identity/sellers/{sellerId}, không được nhận DTO quản trị.

**POST   /identity/admin/sellers/{sellerId}/approve**  —  Duyệt hồ sơ Seller

**Quyền: seller:approve    UC: E06**

* Chỉ PENDING, Admin có seller:approve và admin userId khác sellerId. Cập nhật APPROVED \+ UserRole SELLER \+ tăng User.authzVersion nguyên tử trong Identity; đồng thời ghi audit/outbox.  
    
* Phát seller.approved chứa userId, sellerId, authzVersion → Finance tạo ví idempotent theo sellerId; không transaction xuyên service. Event trùng không tạo thêm ví.  
    
* Seller đang BANNED vẫn được duyệt (APPROVED \+ SELLER) nhưng mọi quyền bán và rút tiền bị chặn, ví bị đóng băng, cho đến khi Admin gỡ cấm scope SELLER (DB 3.1.3).

**Response thành công**

200 → data: SellerAdminView

**Lỗi nghiệp vụ**

* ALREADY\_PROCESSED (409), SELF\_APPROVAL\_FORBIDDEN (403)  
    
* NOT\_FOUND (404)

**POST   /identity/admin/sellers/{sellerId}/reject**  —  Từ chối hồ sơ Seller

**Quyền: seller:approve    UC: E06**

* Chỉ PENDING → REJECTED, ghi rejectionReason/audit và tăng authzVersion; không cấp SELLER, giữ User/email và Customer nếu có. Tên cửa hàng không còn bị giữ chỗ vì kiểm tra trùng chỉ xét PENDING/APPROVED; số điện thoại thuộc User nên không đổi. Nộp lại bằng API seller-application cùng sellerId.

**Request body (application/json)**

| Field | Kiểu | Bắt buộc | Validation / ghi chú |
| :---- | :---- | :---- | :---- |
| reason | string | ✓ | 1–1.000 ký tự |

**Response thành công**

200 → data: SellerAdminView

**Lỗi nghiệp vụ**

* ALREADY\_PROCESSED (409)

### **POST  /identity/admin/ban/{userId}  —  Cấm theo phạm vi**

Quyền: account:moderate

Request: { scope: USER \| CUSTOMER \| SELLER, reason: string 1–1000 }

Response: 200 data: { userId, scope, userStatus, customerStatus, sellerStatus, banMode, authzVersion }

scope bắt buộc, không suy ra từ vai trò duy nhất. USER → DISABLED và thu hồi mọi phiên; CUSTOMER → BANNED theo G-14; SELLER → BANNED, áp dụng cho hồ sơ PENDING, APPROVED hoặc REJECTED; chặn bán/rút và phát seller.banned. Cấm hồ sơ không tồn tại → 404; không cho khóa User có ADMIN qua UI MVP. Cùng trạng thái/lý do → 200 không phát event hay audit lặp; thay lý do khi đã cấm → 409 INVALID\_STATE\_TRANSITION, tránh ghi đè lịch sử.

### **POST  /identity/admin/unban/{userId}  —  Gỡ cấm theo phạm vi**

Quyền: account:moderate

Request: { scope: USER \| CUSTOMER \| SELLER, reason?: string ≤1000 }

Response: 200 data: { userId, scope, userStatus, customerStatus, sellerStatus, authzVersion }

USER → ACTIVE; CUSTOMER/SELLER → ACTIVE riêng hồ sơ. Không xóa điều kiện khóa khác, không mở lại đơn đã hủy, không tự cấp SELLER cho hồ sơ chưa duyệt. Khóa có điều kiện \+ tăng authzVersion \+ audit/outbox trong cùng transaction Identity. Nếu đã ACTIVE trả 200, không phát lặp. Lỗi chung 401/403/404; scope thiếu/sai → 400\.

### **POST  /identity/users/me/seller-application  —  Nộp hồ sơ bán hàng**

Quyền: seller:application:own

Request: { fullName?, phoneNumber?, description? }; không nhận userId/email/password/role/status. fullName mặc định là users.full\_name hiện tại (User GOOGLE lấy từ Google), gửi kèm để đặt tên cửa hàng; phoneNumber bắt buộc nếu User chưa có số điện thoại. Cả hai được ghi vào User trong cùng transaction với hồ sơ Seller.

Response: 201 lần đầu; 200 nộp lại; data: SellerSelfProfile, approvalStatus=PENDING

Lấy sellerId=sub. Chỉ chưa có Seller hoặc REJECTED mới được nộp; PENDING/APPROVED →409 INVALID\_STATE\_TRANSITION. Chưa cấp SELLER, không tạo ví. Nộp lại dùng cùng ID, giữ audit. Tên cửa hàng không trùng Seller PENDING/APPROVED khác (SELLER\_NAME\_ALREADY\_USED); số điện thoại unique toàn hệ thống (PHONE\_ALREADY\_USED). Logo được cập nhật qua PATCH hồ sơ Seller sau khi duyệt. User GOOGLE được dùng luồng này.

### **GET  /identity/users/me/seller-application  —  Xem hồ sơ xét duyệt của mình**

Quyền: seller:application:own

Request: Không có body

Response: 200 data: SellerSelfProfile\|null

Không cần SELLER để xem PENDING/REJECTED. Không nhận sellerId của người khác; User DISABLED bị chặn.

### **POST  /identity/users/me/customer-profile  —  Tạo hồ sơ Customer bổ sung**

Quyền: ANY\_AUTH (policy nền)

Request: Body rỗng; không nhận role/status/owner

Response: 201 tạo mới; 200 nếu đã tồn tại; data: UserSelfProfile

Tạo Customer ACTIVE \+ UserRole CUSTOMER nguyên tử, customerId=sub, tăng authzVersion. Nếu Customer đã BANNED trả hồ sơ hiện tại, không gỡ cấm; nếu CUSTOMER từng bị quản trị thu hồi thì không tự cấp lại, trả 403 FORBIDDEN. Không tạo giỏ hay đơn ở transaction Identity.

### **Nguồn dữ liệu địa giới**

Hai API /identity/regions/\*\* là facade của RegionProvider bên ngoài. GEO\_API\_BASE\_URL, credential nếu cần, datasetVersion và timeout là cấu hình server allowlist; không nhận URL upstream từ client. Nhóm chọn nhà cung cấp có dữ liệu hành chính hai cấp hiện hành trước triển khai, không coi một tên miền ví dụ là nguồn đã được xác minh. Chưa cấu hình thì trả 503 REGION\_SOURCE\_UNAVAILABLE.

Adapter map mã nguồn sang code dạng chuỗi và name; lấy danh sách ward theo provinceCode từ cùng bộ dữ liệu. Cache 24 giờ là giá trị dev đề xuất; chỉ dùng bản cache còn hạn, không fallback sang bảng DB tự quản lý hoặc danh sách tĩnh không có version. Thêm/sửa địa chỉ kiểm tra ward thuộc province qua cùng adapter; nếu không xác minh được thì 503, không kết luận mã sai. INVALID\_REGION 422 chỉ khi nguồn hợp lệ xác nhận mã không hợp lệ. Order lưu snapshot province\_name, ward\_name (DB 3.6.2) để không đổi đơn cũ.

## **6.2 Product Service**

**Phạm vi:** danh mục, sản phẩm và media, tìm kiếm sản phẩm, đánh giá, gợi ý liên quan, gỡ/khôi phục sản phẩm. Tên cũ: Catalog Service. Tồn kho **không** thuộc Product Service (thuộc Order — DB 3.2.2). **UC:** B01, C01, D01, E04, E07, F01.

### **Danh sách endpoint**

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

### **DTO**

| DTO | Field |
| :---- | :---- |
| Category | categoryId, categoryName, status, productCount (chỉ trong API Admin) |
| MediaItem | mediaType, url, sortOrder, isCover |
| ProductCard | productId, productName, priceVnd, coverImageUrl, ratingAverage (1 chữ số thập phân, P-12), ratingCount, stockStatus, sellerId, sellerName |
| ProductDetail | ProductCard \+ description, category { categoryId, categoryName }, media: MediaItem\[\], availableQuantity, maxPerOrder (P-10), seller: SellerPublic |
| SellerProduct | productId, productName, category, priceVnd, status, coverImageUrl, media\[\], description, onHandQuantity, reservedQuantity, availableQuantity, inventoryVersion, removalReason, removedAt, createdAt, updatedAt |
| AdminProduct | ProductCard \+ status, categoryName, removalReason, removedAt, restoreDeadline, restoreDaysLeft |
| Review | reviewId, productId, orderItemId, rating, content, media: MediaItem\[\], reviewerDisplayName (che một phần theo P-12), isMine, createdAt, updatedAt |

### **Chi tiết endpoint**

**GET   /categories  —  Danh mục đang hiển thị**

**Quyền: PUBLIC    UC: B01, D01**

**Response thành công**

200 → data: Category\[\] (chỉ VISIBLE, sắp theo tên)

**GET   /products  —  Tìm kiếm / lọc / duyệt sản phẩm**

**Quyền: PUBLIC    UC: B01, B02**

* Chỉ trả sản phẩm bán được theo G-06: Product SELLING, User chủ shop ACTIVE, UserRole SELLER hiệu lực, Seller APPROVED/ACTIVE. Hết hàng vẫn trả stockStatus OUT\_OF\_STOCK.  
    
* Product Service hỏi Identity trạng thái User/quyền SELLER/hồ sơ theo lô và Order về tồn kho qua internal API. Không đọc database của Identity/Order; không cache quyền trong MVP.  
    
* Dùng cho trang chủ, trang kết quả tìm kiếm, trang người bán (sellerId).

**Tham số (path / query)**

| Tên | Vị trí | Kiểu | Bắt buộc | Mô tả / ràng buộc |
| :---- | :---- | :---- | :---- | :---- |
| q | query | string |  | Tìm theo tên sản phẩm, không phân biệt hoa thường; tìm không dấu chưa chốt |
| categoryId | query | uuid |  |  |
| sellerId | query | uuid |  | Lọc sản phẩm của một người bán (trang người bán) |
| minPriceVnd, maxPriceVnd | query | int |  | Khoảng giá |
| sort | query | string |  | createdAt,desc (mặc định — mới nhất), priceVnd,asc\|desc, ratingAverage,desc |
| page, size | query | int |  |  |

**Response thành công**

200 → data: ProductCard\[\] \+ meta

GET /api/v1/products?q=ao thun\&categoryId=4c1e...\&sort=priceVnd,asc\&page=1\&size=20

200 OK  
{ "data": \[ { "productId": "8b1f...", "productName": "Áo thun basic cotton", "priceVnd": 150000,  
"coverImageUrl": "https\://cdn.../p/8b1f/cover.webp", "ratingAverage": 4.6, "ratingCount": 23,  
"stockStatus": "IN\_STOCK", "sellerId": "5d2a...", "sellerName": "Shop Áo Xinh" } \],  
"meta": { "page": 1, "size": 20, "totalItems": 41, "totalPages": 3 } }

**Lỗi nghiệp vụ**

* VALIDATION\_ERROR (400) — minPriceVnd \> maxPriceVnd, size \> 100

**GET   /products/{productId}  —  Chi tiết sản phẩm**

**Quyền: PUBLIC    UC: B01, B10**

* Sản phẩm không bán được → 404 (FE báo "sản phẩm không còn bán").  
    
* availableQuantity dùng cho "Còn X sản phẩm", giới hạn ô số lượng và bật/tắt nút "Thêm vào giỏ" / "Mua ngay".

**Response thành công**

200 → data: ProductDetail

**Lỗi nghiệp vụ**

* NOT\_FOUND (404)

**GET   /products/{productId}/related  —  Gợi ý sản phẩm liên quan (Could)**

**Quyền: PUBLIC    UC: F01**

* Luật đơn giản: cùng danh mục, loại sản phẩm hiện tại, hết hàng, không bán được. Có thể khác Seller.

**Tham số (path / query)**

| Tên | Vị trí | Kiểu | Bắt buộc | Mô tả / ràng buộc |
| :---- | :---- | :---- | :---- | :---- |
| limit | query | int |  | Mặc định 8, tối đa 20 |

**Response thành công**

200 → data: ProductCard\[\] (có thể rỗng — FE ẩn khối)

**GET   /products/{productId}/reviews  —  Đánh giá của sản phẩm**

**Quyền: PUBLIC    UC: B01, C01**

**Tham số (path / query)**

| Tên | Vị trí | Kiểu | Bắt buộc | Mô tả / ràng buộc |
| :---- | :---- | :---- | :---- | :---- |
| rating | query | int 1–5 |  | Lọc theo số sao |
| page, size | query |  |  | sort mặc định createdAt,desc |

**Response thành công**

200 → data: Review\[\] \+ meta \+ summary: { ratingAverage, ratingCount, countByStar: {"1":0,...,"5":0} }

**POST   /media/upload-urls  —  Xin URL upload media sản phẩm / review**

**Quyền: product:manage:own hoặc review:write:own theo purpose    UC: D01, C01**

**Request body (application/json)**

| Field | Kiểu | Bắt buộc | Validation / ghi chú |
| :---- | :---- | :---- | :---- |
| files\[\] | array (1–9) | ✓ | Mỗi phần tử: fileName, mimeType, sizeBytes, durationSec (video), purpose |

**Response thành công**

200 → data: \[{ objectKey, uploadUrl, expiresAt }\]

**Lỗi nghiệp vụ**

* MEDIA\_INVALID (422)

**POST   /products/{productId}/reviews  —  Tạo đánh giá (Should)**

**Quyền: review:write:own    UC: C01**

* Product Service hỏi Order (GET /internal/v1/orders/order-items/{orderItemId}/review-eligibility) để xác minh: OrderItem thuộc Customer, đúng productId, Order ở CONFIRMED.  
    
* Mỗi Customer tối đa 1 review/sản phẩm. Customer bị cấm theo diện chỉ chặn thanh toán vẫn được đánh giá.

**Request body (application/json)**

| Field | Kiểu | Bắt buộc | Validation / ghi chú |
| :---- | :---- | :---- | :---- |
| orderItemId | uuid | ✓ | Dòng hàng làm căn cứ đã mua |
| rating | int | ✓ | Số nguyên 1–5 |
| content | string \| null |  | ≤ 1.000 ký tự (P-08.5) |
| mediaObjectKeys | string\[\] |  | purpose REVIEW\_MEDIA, ≤ P-08.3 |

**Response thành công**

201 → data: Review

**Lỗi nghiệp vụ**

* REVIEW\_NOT\_ALLOWED (403)  
    
* REVIEW\_ALREADY\_EXISTS (409); MVP không có API sửa review.  
    
* VALIDATION\_ERROR (400) — rating ngoài 1–5 hoặc không nguyên

**GET   /seller/products  —  Sản phẩm của tôi**

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

**POST   /seller/products  —  Thêm sản phẩm**

**Quyền: product:manage:own    UC: D01**    Idempotency-Key: bắt buộc

* Product Service tạo Product HIDDEN cùng outbox yêu cầu khởi tạo tồn; gọi POST /internal/v1/inventory idempotent theo productId. Sau khi Inventory xác nhận, mới chuyển sang trạng thái khách yêu cầu. Timeout giữ HIDDEN, retry cùng productId; không giả định rollback xuyên hai database. Request tạo sản phẩm dùng Idempotency-Key để retry không sinh Product mới.  
    
* Ảnh bìa \= media IMAGE đầu tiên theo thứ tự; chỉ có video → placeholder.

**Request body (application/json)**

| Field | Kiểu | Bắt buộc | Validation / ghi chú |
| :---- | :---- | :---- | :---- |
| productName | string | ✓ | 1–255 ký tự |
| categoryId | uuid | ✓ | Danh mục VISIBLE |
| priceVnd | int | ✓ | \> 0 |
| initialStock | int | ✓ | ≥ 0 |
| description | string \| null |  | ≤ 5.000 ký tự (P-08.5) |
| media\[\] | array | ✓ | ≥ 1 phần tử { objectKey, mediaType, sortOrder }, ≤ P-08.3 |
| status | ProductStatus | ✓ | SELLING hoặc HIDDEN |

**Response thành công**

201 → data: SellerProduct

POST /api/v1/seller/products  
{ "productName": "Áo thun basic cotton", "categoryId": "4c1e...", "priceVnd": 150000,  
"initialStock": 50, "description": "Cotton 100%...", "status": "SELLING",  
"media": \[ { "objectKey": "tmp/seller/5d2a/7f3a.webp", "mediaType": "IMAGE", "sortOrder": 1 },  
{ "objectKey": "tmp/seller/5d2a/91bc.mp4",  "mediaType": "VIDEO", "sortOrder": 2 } \] }

**Lỗi nghiệp vụ**

* MEDIA\_REQUIRED (422), MEDIA\_INVALID (422)  
    
* CATEGORY\_NOT\_AVAILABLE (422)

**GET   /seller/products/{productId}  —  Chi tiết sản phẩm của tôi**

**Quyền: product:manage:own    UC: D01**

**Response thành công**

200 → data: SellerProduct (kèm description, media\[\] đầy đủ)

**Lỗi nghiệp vụ**

* NOT\_FOUND (404) — không phải sản phẩm của mình

**PUT   /seller/products/{productId}  —  Sửa thông tin sản phẩm**

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

**PATCH   /seller/products/{productId}/status  —  Bật bán / tạm ẩn**

**Quyền: product:manage:own    UC: D01**

**Request body (application/json)**

| Field | Kiểu | Bắt buộc | Validation / ghi chú |
| :---- | :---- | :---- | :---- |
| status | ProductStatus | ✓ | SELLING hoặc HIDDEN |

**Response thành công**

200 → data: SellerProduct

**Lỗi nghiệp vụ**

* PRODUCT\_REMOVED\_BY\_ADMIN (409)

**GET   /admin/products  —  Tra cứu sản phẩm toàn hệ thống**

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

**GET   /admin/products/{productId}  —  Chi tiết sản phẩm (Admin)**

**Quyền: operations:read:any    UC: E04**

**Response thành công**

200 → data: AdminProduct \+ media\[\], description

**POST   /admin/products/{productId}/remove  —  Gỡ sản phẩm**

**Quyền: product:moderate    UC: E04**

* Chuyển REMOVED, lưu removedAt. Gỡ lại sản phẩm đã gỡ → 200, không đổi gì.  
    
* Ghi AuditLog; phát product.removed → Notification báo Seller. Giỏ hàng tự cập nhật khi khách mở lại; checkout kiểm tra lại trước khi tạo đơn.

**Request body (application/json)**

| Field | Kiểu | Bắt buộc | Validation / ghi chú |
| :---- | :---- | :---- | :---- |
| reason | string | ✓ | 1–1.000 ký tự |

**Response thành công**

200 → data: AdminProduct

**POST   /admin/products/{productId}/restore  —  Khôi phục sản phẩm (về Tạm ẩn)**

**Quyền: product:moderate    UC: E04**

* Chỉ trong 30 ngày kể từ removedAt. Sau khôi phục, sản phẩm ở HIDDEN để Seller tự bật bán.

**Response thành công**

200 → data: AdminProduct

**Lỗi nghiệp vụ**

* RESTORE\_WINDOW\_EXPIRED (409)  
    
* INVALID\_STATE\_TRANSITION (409) — không ở REMOVED

**GET   /admin/categories  —  Tất cả danh mục (gồm ẩn)**

**Quyền: category:manage    UC: E07**

**Response thành công**

200 → data: Category\[\] (kèm productCount)

**POST   /admin/categories  —  Thêm danh mục (Should)**

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

**PATCH   /admin/categories/{categoryId}  —  Đổi tên / ẩn / hiện danh mục**

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

**DELETE   /admin/categories/{categoryId}  —  Xóa danh mục chưa có sản phẩm**

**Quyền: category:manage    UC: E07**

**Response thành công**

204

**Lỗi nghiệp vụ**

* CATEGORY\_IN\_USE (409)

## **6.3 Cart Service**

**Phạm vi:** giỏ hàng của Customer đã đăng nhập; tính trạng thái dòng; giảm đúng lượng đã mua sau khi thanh toán thành công (tiêu thụ event checkout.paid, chỉ khi source \= CART). **UC:** B03 (và một phần B06). Theo FRD, khách phải đăng nhập mới thêm giỏ — **không** triển khai giỏ khách vãng lai (xem C-03).

Việc **chọn dòng để thanh toán** là trạng thái phía FE: FE gửi danh sách cartItemIds đã chọn vào API checkout của Order, không lưu lựa chọn ở Cart.

### **Danh sách endpoint**

| \# | Method | Path | Mô tả | Quyền | UC |
| :---- | :---- | :---- | :---- | :---- | :---- |
| 1 | GET | /cart | Xem giỏ hàng | cart:manage:own | B03 |
| 2 | GET | /cart/summary | Số lượng trên icon giỏ | cart:manage:own | B03 |
| 3 | POST | /cart/items | Thêm vào giỏ | cart:manage:own | B03 |
| 4 | PATCH | /cart/items/{cartItemId} | Đổi số lượng | cart:manage:own | B03 |
| 5 | DELETE | /cart/items/{cartItemId} | Xóa dòng khỏi giỏ | cart:manage:own | B03 |

### **DTO**

| DTO | Field |
| :---- | :---- |
| Cart | cartId, groups: \[{ seller { sellerId, sellerName }, items: CartItem\[\] }\], removedItems: \[{ productId, productName, reason }\], summary { totalQuantity, lineCount }, paymentBlocked |
| CartItem | cartItemId, productId, productName, coverImageUrl, unitPriceVnd (giá hiện tại), quantity, availableQuantity, lineStatus, selectable, lineTotalVnd |

### **Chi tiết endpoint**

**GET   /cart**  —  Xem giỏ hàng

**Quyền: cart:manage:own    UC: B03**

* Mỗi lần xem, Cart lấy giá hiện tại, trạng thái bán được (Product Service) và tồn khả dụng (Order) để tính lineStatus (DB 3.3.2). Không lưu trạng thái này.  
    
* Dòng có sản phẩm ngừng bán bị xóa khỏi giỏ và trả trong removedItems để FE hiển thị thông báo.  
    
* Dòng được nhóm theo Seller; mỗi nhóm sẽ thành một Order khi checkout.

**Response thành công**

200 → data: Cart

{ "data": {  
"cartId": "c91e...",  
"groups": \[  
{ "seller": { "sellerId": "5d2a...", "sellerName": "Shop Áo Xinh" },  
"items": \[  
{ "cartItemId": "ci-01", "productId": "8b1f...", "productName": "Áo thun basic cotton",  
"coverImageUrl": "https\://cdn...", "unitPriceVnd": 150000, "quantity": 2,  
"availableQuantity": 48, "lineStatus": "IN\_STOCK", "selectable": true, "lineTotalVnd": 300000 },  
{ "cartItemId": "ci-02", "productId": "a71c...", "productName": "Quần jean slim",  
"coverImageUrl": "https\://cdn...", "unitPriceVnd": 320000, "quantity": 3,  
"availableQuantity": 2, "lineStatus": "INSUFFICIENT\_STOCK", "selectable": false, "lineTotalVnd": 960000 } \] } \],  
"removedItems": \[ { "productId": "e44b...", "productName": "Mũ lưỡi trai", "reason": "PRODUCT\_NOT\_SELLABLE" } \],  
"summary": { "totalQuantity": 5, "lineCount": 2 },  
"paymentBlocked": false } }

**GET   /cart/summary**  —  Số lượng trên icon giỏ

**Quyền: cart:manage:own    UC: B03**

* Nhẹ, không gọi service khác. Dùng cho navbar.

**Response thành công**

200 → data: { lineCount, totalQuantity }

**POST   /cart/items**  —  Thêm vào giỏ

**Quyền: cart:manage:own    UC: B03**

* Sản phẩm đã có trong giỏ → cộng dồn số lượng. Không giữ hàng.  
    
* Cho phép thêm sản phẩm đang hết hàng (FRD UC-B01), nhưng không thêm sản phẩm không bán được.  
    
* Thiếu cart:manage:own/CUSTOMER →403; User có SELLER hoặc ADMIN cùng CUSTOMER hợp lệ vẫn thêm giỏ được. Chưa login →401. customerId chủ giỏ phải bằng sub.

**Request body (application/json)**

| Field | Kiểu | Bắt buộc | Validation / ghi chú |
| :---- | :---- | :---- | :---- |
| productId | uuid | ✓ |  |
| quantity | int | ✓ | ≥ 1; tổng sau cộng dồn ≤ P-10 |

**Response thành công**

200 → data: Cart (giỏ đầy đủ sau khi thêm)

**Lỗi nghiệp vụ**

* PRODUCT\_NOT\_SELLABLE (422)  
    
* QUANTITY\_LIMIT\_EXCEEDED (422)

**PATCH   /cart/items/{cartItemId}**  —  Đổi số lượng

**Quyền: cart:manage:own    UC: B03**

**Request body (application/json)**

| Field | Kiểu | Bắt buộc | Validation / ghi chú |
| :---- | :---- | :---- | :---- |
| quantity | int | ✓ | 1 ≤ quantity ≤ P-10 |

**Response thành công**

200 → data: Cart

**Lỗi nghiệp vụ**

* NOT\_FOUND (404)  
    
* QUANTITY\_LIMIT\_EXCEEDED (422)

**DELETE   /cart/items/{cartItemId}**  —  Xóa dòng khỏi giỏ

**Quyền: cart:manage:own    UC: B03**

**Response thành công**

200 → data: Cart

**Lỗi nghiệp vụ**

* NOT\_FOUND (404)

## **6.4 Order Service (Order Inventory Fulfillment)**

**Phạm vi:** checkout và tách đơn theo cửa hàng, giữ hàng, vòng đời đơn của Customer và Seller, tồn kho và lịch sử kho, số liệu đơn, job J1/J3/J4. **UC:** B04, B06, B07, B08, B09, B10, D02, D03, một phần D04, E02, E08.

### **Luồng chính của Customer**

* **Từ giỏ:** chọn dòng → POST /orders/checkout/preview (source CART) → trang Thanh toán → POST /orders/checkout-sessions.  
    
* **Mua ngay:** trang sản phẩm → POST /orders/checkout/preview (source BUY\_NOW) → POST /orders/checkout-sessions.  
    
* Kết quả: 1 CheckoutSession \+ N Order ở AWAITING\_PAYMENT → FE gọi POST /payments (Payment Service).  
    
* Khi nhận payment.succeeded: N đơn → PAID, trừ kho, phát checkout.paid cho Cart, Finance, Notification. Chi tiết ở Mục 8.1.

### **State machine của Order (DB 8.2B)**

| Từ | Sự kiện / API | Sang |
| :---- | :---- | :---- |
| AWAITING\_PAYMENT | payment.succeeded | PAID |
| AWAITING\_PAYMENT | payment.failed / J1 hết hạn / khách hủy phiên | PAYMENT\_FAILED / EXPIRED / CANCELLED |
| PAID | start-packing (Seller) | PACKING |
| PAID | cancel (Customer) | CANCELLED → Refund |
| PAID, PACKING | reject (Seller) / J4 quá 7 ngày / Seller bị cấm | CANCELLED → Refund |
| PACKING | mark-delivered (Seller) | DELIVERED |
| DELIVERED | confirm-receipt (Customer) / J3 quá 7 ngày | CONFIRMED → quyết toán |
| CONFIRMED, CANCELLED | — | Kết thúc, không chuyển tiếp |

### **Danh sách endpoint**

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

### **DTO**

| DTO | Field |
| :---- | :---- |
| CheckoutPreview | source, orders: \[{ seller, items: \[{ productId, productName, coverImageUrl, unitPriceVnd, quantity, lineTotalVnd }\], orderTotalVnd }\], shippingFeeVnd (0), totalAmountVnd, defaultAddress: Address \| null, issues: \[{ productId, cartItemId, code, availableQuantity }\], blockers: \[{ code, ... }\], canPlaceOrder, isSimulated |
| CheckoutSession | checkoutSessionId, source, status, paymentStatus (NONE \| PENDING \| SUCCESS \| FAILED), totalAmountVnd, shippingFeeVnd, expiresAt, paidAt, shippingAddress (snapshot), orders: OrderSummary\[\], allowedActions, isSimulated |
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

### **Chi tiết endpoint**

**POST   /orders/checkout/preview**  —  Xem trước trang Thanh toán

**Quyền: checkout:create:own    UC: B04, B10**

* Không ghi dữ liệu, không giữ hàng. Dùng để render trang "Thanh toán / Xác nhận đặt hàng".  
    
* Gom dòng hàng theo Seller (mỗi nhóm \= 1 đơn dự kiến), tính tổng từng đơn và tổng chung, kiểm tra tồn và trạng thái bán được, trả địa chỉ mặc định.  
    
* Trả 200 kể cả khi có dòng lỗi; lỗi nằm trong issues\[\] và canPlaceOrder \= false. Mọi điều kiện được kiểm tra lại ở bước tạo phiên.

**Request body (application/json)**

| Field | Kiểu | Bắt buộc | Validation / ghi chú |
| :---- | :---- | :---- | :---- |
| source | CheckoutSource | ✓ | CART hoặc BUY\_NOW |
| cartItemIds | uuid\[\] | nếu CART | ≥ 1 dòng đã chọn trong giỏ |
| buyNowItem | object | nếu BUY\_NOW | { productId, quantity }; 1 ≤ quantity ≤ min(khả dụng, P-10) |

**Response thành công**

200 → data: CheckoutPreview

POST /api/v1/orders/checkout/preview  
{ "source": "CART", "cartItemIds": \["ci-01", "ci-07"\] }

200 OK  
{ "data": {  
"source": "CART",  
"orders": \[  
{ "seller": { "sellerId": "5d2a...", "sellerName": "Shop Áo Xinh" },  
"items": \[ { "productId": "8b1f...", "productName": "Áo thun basic cotton", "coverImageUrl": "...",  
"unitPriceVnd": 150000, "quantity": 2, "lineTotalVnd": 300000 } \],  
"orderTotalVnd": 300000 },  
{ "seller": { "sellerId": "9e7c...", "sellerName": "Giày Việt" },  
"items": \[ { "productId": "f02d...", "productName": "Sneaker trắng", "coverImageUrl": "...",  
"unitPriceVnd": 450000, "quantity": 1, "lineTotalVnd": 450000 } \],  
"orderTotalVnd": 450000 } \],  
"shippingFeeVnd": 0,  
"totalAmountVnd": 750000,  
"defaultAddress": { "addressId": "ad-3", "recipientName": "Nguyễn Văn An", "...": "..." },  
"issues": \[\],  
"blockers": \[\],  
"canPlaceOrder": true,  
"isSimulated": true } }

**Lỗi nghiệp vụ**

* blockers\[\] (trong response 200, không phải lỗi HTTP): PROFILE\_INCOMPLETE (thiếu SĐT/địa chỉ), CHECKOUT\_PENDING\_EXISTS, CUSTOMER\_PAYMENT\_BLOCKED  
    
* issues\[\] theo dòng: PRODUCT\_NOT\_SELLABLE, INSUFFICIENT\_STOCK (kèm availableQuantity), QUANTITY\_LIMIT\_EXCEEDED

**POST   /orders/checkout-sessions**  —  Đặt hàng: tạo phiên thanh toán và N đơn

**Quyền: checkout:create:own    UC: B04, B10    Idempotency-Key: bắt buộc**

* Cần User ACTIVE, checkout:create:own và Customer ACTIVE; địa chỉ thuộc customerId=sub, có phone, chưa có phiên AWAITING\_PAYMENT. Gửi customerId/sellerId từ client không thay thế ownership.  
    
* Lấy lại giá \+ Seller \+ trạng thái bán được từ Product Service (theo lô), tỷ lệ hoa hồng hiện hành từ Finance, rồi trong **một transaction**: tạo CheckoutSession, N Order (mỗi Seller 1 đơn, UNIQUE(checkoutSessionId, sellerId)), OrderItem snapshot, Reservation giữ toàn bộ hàng. Một dòng lỗi → rollback, không tạo gì (FRD UC-B04).  
    
* expiresAt=now+P-01. Sau commit checkout, cập nhật lastUsedAt qua Identity idempotent bằng checkoutSessionId; lỗi touch được retry, không giả định rollback checkout xuyên service.  
    
* source \= BUY\_NOW: đúng 1 Order, 1 OrderItem; không đọc/ghi giỏ (G-016).

**Request body (application/json)**

| Field | Kiểu | Bắt buộc | Validation / ghi chú |
| :---- | :---- | :---- | :---- |
| source | CheckoutSource | ✓ |  |
| cartItemIds | uuid\[\] | nếu CART |  |
| buyNowItem | object | nếu BUY\_NOW | { productId, quantity } |
| addressId | uuid | ✓ | Địa chỉ trong sổ địa chỉ của Customer; dùng chung cho N đơn |

**Response thành công**

201 → data: CheckoutSession (gửi lặp cùng key → 200 cùng dữ liệu)

POST /api/v1/orders/checkout-sessions  
Idempotency-Key: 5f0c2b1a-7d3e-4e1b-9a55-2c1d9b7e0f11  
{ "source": "CART", "cartItemIds": \["ci-01", "ci-07"\], "addressId": "ad-3" }

201 Created  
{ "data": {  
"checkoutSessionId": "cs-71a...", "source": "CART",  
"status": "AWAITING\_PAYMENT", "paymentStatus": "NONE",  
"totalAmountVnd": 750000, "shippingFeeVnd": 0,  
"expiresAt": "2026-10-18T08:45:00Z", "paidAt": null,  
"shippingAddress": { "recipientName": "Nguyễn Văn An", "recipientPhone": "0912345678",  
"provinceName": "Hà Nội", "wardName": "Phường Bạch Mai", "addressDetail": "12 Ngõ 5" },  
"orders": \[  
{ "orderId": "od-1", "seller": { "sellerId": "5d2a...", "sellerName": "Shop Áo Xinh" },  
"totalAmountVnd": 300000, "status": "AWAITING\_PAYMENT", "itemCount": 1 },  
{ "orderId": "od-2", "seller": { "sellerId": "9e7c...", "sellerName": "Giày Việt" },  
"totalAmountVnd": 450000, "status": "AWAITING\_PAYMENT", "itemCount": 1 } \],  
"allowedActions": \["PAY", "CANCEL"\],  
"isSimulated": true } }

**Lỗi nghiệp vụ**

* CUSTOMER\_PAYMENT\_BLOCKED (403)  
    
* PROFILE\_INCOMPLETE (422)  
    
* CHECKOUT\_PENDING\_EXISTS (409) — details\[0\].checkoutSessionId để FE dẫn tới phiên cũ  
    
* CHECKOUT\_ITEMS\_INVALID (409) — details theo dòng (hết hàng, ngừng bán, vượt giới hạn)  
    
* IDEMPOTENCY\_KEY\_REUSED (409), IDEMPOTENCY\_KEY\_REQUIRED (400)

**GET   /orders/checkout-sessions/current**  —  Phiên đang chờ thanh toán (nếu có)

**Quyền: order:read:own    UC: B04, B05**

**Response thành công**

200 → data: CheckoutSession \| null

**GET   /orders/checkout-sessions/{checkoutSessionId}**  —  Chi tiết phiên thanh toán

**Quyền: order:read:own    UC: B05, B07**

* paymentStatus lấy từ Payment (internal) để FE hiển thị "Đang xác nhận thanh toán" và khóa nút khi PENDING.  
    
* allowedActions gồm PAY, CANCEL tùy trạng thái — FE dựa vào đây để bật/tắt nút.

**Response thành công**

200 → data: CheckoutSession

**Lỗi nghiệp vụ**

* NOT\_FOUND (404)

**POST   /orders/checkout-sessions/{checkoutSessionId}/cancel**  —  Hủy cả phiên trước khi thanh toán

**Quyền: order:cancel:own    UC: B08**

* Chỉ khi AWAITING\_PAYMENT và không có Payment PENDING. Hủy tất cả N đơn, nhả toàn bộ hàng giữ; không hoàn tiền vì chưa thu.  
    
* MVP không cho hủy lẻ một đơn trước khi thanh toán (DB 3.6).

**Response thành công**

200 → data: CheckoutSession (status CANCELLED)

**Lỗi nghiệp vụ**

* PAYMENT\_IN\_PROGRESS (409)  
    
* INVALID\_STATE\_TRANSITION (409)

**GET   /orders/me**  —  Đơn mua của tôi

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

**GET   /orders/me/{orderId}**  —  Chi tiết đơn (Customer)

**Quyền: order:read:own    UC: B05, B07, B08, B09, C01**

* allowedActions là gợi ý UI được tính từ permission hiện hành \+ ownership \+ trạng thái đơn \+ G-14; API thao tác luôn kiểm tra lại. Có PAY, CANCEL\_SESSION, CANCEL, CONFIRM\_RECEIPT, REVIEW tùy điều kiện.  
    
* Khối hoàn tiền lấy từ Payment (internal) theo orderId.

**Response thành công**

200 → data: CustomerOrderDetail

{ "data": {  
"orderId": "od-2", "checkoutSessionId": "cs-71a...",  
"seller": { "sellerId": "9e7c...", "sellerName": "Giày Việt" },  
"status": "DELIVERED", "totalAmountVnd": 450000, "shippingFeeVnd": 0,  
"items": \[ { "orderItemId": "oi-9", "productId": "f02d...", "productName": "Sneaker trắng",  
"coverImageUrl": "...", "unitPriceVnd": 450000, "quantity": 1, "lineTotalVnd": 450000 } \],  
"shippingAddress": { "...": "snapshot lúc đặt" },  
"paidAt": "2026-10-18T08:32:10Z", "sellerActionDueAt": "2026-10-25T08:32:10Z",  
"deliveredAt": "2026-10-20T03:00:00Z", "customerConfirmDueAt": "2026-10-27T03:00:00Z",  
"confirmedAt": null, "cancellation": null, "refund": null,  
"timeline": \[ { "status": "PAID", "at": "2026-10-18T08:32:10Z", "actor": "SYSTEM" },  
{ "status": "PACKING", "at": "2026-10-19T02:00:00Z", "actor": "SELLER" },  
{ "status": "DELIVERED", "at": "2026-10-20T03:00:00Z", "actor": "SELLER" } \],  
"allowedActions": \["CONFIRM\_RECEIPT"\],  
"isSimulated": true } }

**Lỗi nghiệp vụ**

* NOT\_FOUND (404) — không phải đơn của mình

**POST   /orders/me/{orderId}/cancel**  —  Khách hủy đơn đã thanh toán

**Quyền: order:cancel:own    UC: B08**

* Chỉ khi Order ở PAID (chưa đóng gói). Trong một transaction: Order → CANCELLED, hoàn kho (StockMovement RESTOCK, một lần), ghi outbox order.cancelled (Payment tạo Refund \= tổng đơn; Finance chuyển Settlement sang PENDING\_REFUND; Notification báo khách \+ Seller).  
    
* Đơn khác cùng lần thanh toán không bị ảnh hưởng. Gọi lại khi đã CANCELLED → 200\.  
    
* Khách bị cấm theo diện chỉ chặn thanh toán vẫn được hủy.  
    
* Để hủy phiên chưa thanh toán, dùng API hủy phiên.

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

**POST   /orders/me/{orderId}/confirm-receipt**  —  Đã nhận hàng

**Quyền: order:confirm:own    UC: B09**

* Chỉ khi DELIVERED. Order → CONFIRMED, ghi confirmedAt, phát order.confirmed → Finance quyết toán (UC-G04), Notification.  
    
* Tranh chấp với J3: cập nhật có điều kiện, chỉ một kết quả. Gọi lại khi đã CONFIRMED → 200\.

**Response thành công**

200 → data: CustomerOrderDetail

**Lỗi nghiệp vụ**

* INVALID\_STATE\_TRANSITION (409)

**GET   /orders/seller/orders**  —  Đơn của cửa hàng tôi

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

**GET   /orders/seller/orders/{orderId}**  —  Chi tiết đơn (Seller)

**Quyền: order:fulfill:own    UC: D02, D04**

* Gồm thông tin người nhận (chỉ đơn của mình), sản phẩm, mốc thời gian, commissionBps snapshot, hoa hồng và số tiền dự kiến (C, S theo DB 8.3). Trạng thái tiền chi tiết lấy từ GET /finance/seller/settlements?orderId=.

**Response thành công**

200 → data: SellerOrderDetail

**Lỗi nghiệp vụ**

* NOT\_FOUND (404)

**POST   /orders/seller/orders/{orderId}/start-packing**  —  Bắt đầu đóng gói

**Quyền: order:fulfill:own    UC: D02**

* PAID → PACKING. Sau bước này khách không tự hủy được nữa. Hạn 7 ngày không thay đổi.

**Response thành công**

200 → data: SellerOrderDetail

**Lỗi nghiệp vụ**

* INVALID\_STATE\_TRANSITION (409)  
    
* SELLER\_DEADLINE\_PASSED (409)

**POST   /orders/seller/orders/{orderId}/mark-delivered**  —  Xác nhận đã giao

**Quyền: order:fulfill:own    UC: D02**

* PACKING → DELIVERED trước sellerActionDueAt. Ghi deliveredAt, customerConfirmDueAt \= deliveredAt \+ 7 ngày. Không có trạng thái "Đang giao".  
    
* Phát order.delivered → Notification báo khách.  
    
* Không có body; MVP không lưu mã vận đơn (C-06).

**Response thành công**

200 → data: SellerOrderDetail

**Lỗi nghiệp vụ**

* INVALID\_STATE\_TRANSITION (409) — phải ở PACKING  
    
* SELLER\_DEADLINE\_PASSED (409)

**POST   /orders/seller/orders/{orderId}/reject**  —  Từ chối giao hàng (Must)

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

**GET   /orders/seller/inventory**  —  Tồn kho sản phẩm của tôi

**Quyền: inventory:manage:own    UC: D03**

**Tham số (path / query)**

| Tên | Vị trí | Kiểu | Bắt buộc | Mô tả / ràng buộc |
| :---- | :---- | :---- | :---- | :---- |
| productIds | query | uuid\[\] (CSV) |  | Tối đa 100; bỏ trống \= tất cả (phân trang) |
| page, size | query |  |  |  |

**Response thành công**

200 → data: InventoryItem\[\]

**PUT   /orders/seller/inventory/{productId}**  —  Cập nhật tồn kho

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

**GET   /orders/seller/inventory/{productId}/movements**  —  Lịch sử biến động kho

**Quyền: inventory:manage:own    UC: D03**

**Tham số (path / query)**

| Tên | Vị trí | Kiểu | Bắt buộc | Mô tả / ràng buộc |
| :---- | :---- | :---- | :---- | :---- |
| page, size | query |  |  | sort createdAt,desc |

**Response thành công**

200 → data: StockMovement\[\] \+ meta

**GET   /orders/seller/stats**  —  Kết quả kinh doanh theo kỳ

**Quyền: order:fulfill:own    UC: D04**

* Phần số đơn và sản phẩm của UC-D04. Phần tiền (hoa hồng, được nhận, trạng thái tiền) lấy từ Finance.  
    
* Chỉ tính Order.totalAmountVnd của Seller đang xem, không tính toàn bộ Payment chung.

**Tham số (path / query)**

| Tên | Vị trí | Kiểu | Bắt buộc | Mô tả / ràng buộc |
| :---- | :---- | :---- | :---- | :---- |
| from, to | query | date | ✓ | P-14 |
| topLimit | query | int |  | Mặc định 5 |

**Response thành công**

200 → data: { paidOrderCount, confirmedOrderCount, confirmedValueVnd, cancelledOrderCount, cancelledValueVnd, topProducts: \[{ productId, productName, quantitySold }\] } (P-15 đề xuất theo số lượng)

**GET   /orders/seller/dashboard**  —  Khối "Việc cần làm" của Seller

**Quyền: order:fulfill:own    UC: D02, D03**

**Tham số (path / query)**

| Tên | Vị trí | Kiểu | Bắt buộc | Mô tả / ràng buộc |
| :---- | :---- | :---- | :---- | :---- |
| dueWithinHours | query | int |  | Ngưỡng "sắp hết hạn", mặc định 24 (chưa có trong FRD) |

**Response thành công**

200 → data: { paidCount, packingCount, dueSoonCount, outOfStockProductCount }. Số sản phẩm bị gỡ lấy từ Product Service (status=REMOVED).

**GET   /orders/admin/orders**  —  Tra cứu đơn toàn hệ thống

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

**GET   /orders/admin/orders/{orderId}**  —  Chi tiết đơn (Admin)

**Quyền: operations:read:any    UC: E08**

* Payment chung và Refund lấy từ GET /payments/admin/payments/by-checkout/{checkoutSessionId}; Settlement từ GET /finance/admin/settlements?orderId=.

**Response thành công**

200 → data: SellerOrderDetail \+ { customer { customerId, fullName, email }, checkoutSession { ... } }

**GET   /orders/admin/stats**  —  Tiến độ đơn và hàng chờ (tổng quan Admin)

**Quyền: operations:read:any    UC: E02**

**Tham số (path / query)**

| Tên | Vị trí | Kiểu | Bắt buộc | Mô tả / ràng buộc |
| :---- | :---- | :---- | :---- | :---- |
| from, to | query | date | ✓ |  |

**Response thành công**

200 → data: { countByStatus: { PAID, PACKING, DELIVERED, CONFIRMED, CANCELLED }, overdueSellerCount, overdueCustomerCount, expiredCheckoutCount }

## **6.5 Payment Service**

**Phạm vi:** một Payment chung cho mỗi CheckoutSession, Refund theo đơn hoặc toàn phiên, đối soát với bộ giả lập thanh toán nội bộ, job J2/J2b/J5, công cụ Admin xử lý hoàn tiền lỗi. **UC:** B05, G02, một phần B06, E02, E08.

### **Danh sách endpoint**

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

### **DTO**

| DTO | Field |
| :---- | :---- |
| Payment | paymentId, checkoutSessionId, amountVnd, currency, provider, status, providerTransactionId, errorCode, refundedAmountVnd, refundState, createdAt, completedAt, isSimulated |
| Refund | refundId, paymentId, orderId (null nếu FULL\_CHECKOUT), scope, amountVnd, reason, status, retryCount, providerRefundId, createdAt, completedAt, isSimulated |

### **Chi tiết endpoint**

**POST   /payments**  —  Thanh toán một lần cho phiên checkout

**Quyền: payment:create:own    UC: B05    Idempotency-Key: bắt buộc**

* Payment kiểm tra payment:create:own, User/Customer hiện hành và hỏi Order payable với customerId=sub; xác minh chủ phiên, AWAITING\_PAYMENT, chưa hết hạn. Phiên của người khác →404. Không cache quyền, không tin số tiền client.  
    
* **Số tiền luôn do server tính**, client không gửi số tiền.  
    
* Tối đa 1 Payment / phiên. Gọi PaymentGateway.charge (bộ giả lập thanh toán nội bộ) **ngoài** transaction DB; lưu ý định trước khi gọi.  
    
* Kết quả: SUCCESS → 201; bộ giả lập trả FAILED rõ ràng → 201 với status: FAILED (phiên và N đơn đóng ở PAYMENT\_FAILED, giỏ giữ nguyên); timeout P-02 (giai đoạn 2\) → 202 với status: PENDING (FE hiển thị "Đang xác nhận thanh toán", poll GET /payments/by-checkout/{id} mỗi 3–5 giây).  
    
* Môi trường dev/staging: cho phép field simulatorScenario để demo/test các nhánh của giai đoạn 2 (6.5.1); production luôn dùng kịch bản SUCCESS.

**Request body (application/json)**

| Field | Kiểu | Bắt buộc | Validation / ghi chú |
| :---- | :---- | :---- | :---- |
| checkoutSessionId | uuid | ✓ | Phiên của chính Customer |
| simulatorScenario | enum |  | Chỉ chấp nhận ở dev/staging, production bỏ qua. Giai đoạn 1: chỉ SUCCESS (mặc định). Giai đoạn 2: thêm FAIL, TIMEOUT, LATE\_SUCCESS (xem 6.5.1) |

**Response thành công**

201 (SUCCESS/FAILED) hoặc 202 (PENDING) → data: Payment

POST /api/v1/payments  
Idempotency-Key: 0e7d0a52-55b4-4d37-9c43-0a1d2f3b9e10  
{ "checkoutSessionId": "cs-71a..." }

201 Created  
{ "data": { "paymentId": "pm-4c2...", "checkoutSessionId": "cs-71a...", "amountVnd": 750000,  
"currency": "VND", "provider": "SIMULATOR", "status": "SUCCESS",  
"providerTransactionId": "SIM-TX-88231", "errorCode": null,  
"refundedAmountVnd": 0, "refundState": "NONE",  
"createdAt": "2026-10-18T08:32:08Z", "completedAt": "2026-10-18T08:32:10Z",  
"isSimulated": true } }

**Lỗi nghiệp vụ**

* CUSTOMER\_PAYMENT\_BLOCKED (403)  
    
* CHECKOUT\_NOT\_PAYABLE (409) — đã hết hạn/hủy/đã thanh toán/thất bại  
    
* PAYMENT\_IN\_PROGRESS (409) — đã có Payment PENDING (gửi lặp cùng key trả lại Payment cũ, không lỗi)  
    
* CHECKOUT\_ITEMS\_INVALID (409) — sản phẩm/Seller không còn hợp lệ ngay trước khi thu: dừng, yêu cầu đặt lại  
    
* IDEMPOTENCY\_KEY\_REUSED (409)

**GET   /payments/by-checkout/{checkoutSessionId}**  —  Trạng thái thanh toán của phiên

**Quyền: order:read:own    UC: B05, B07**

**Response thành công**

200 → data: Payment \| null (null nếu chưa thanh toán)

**Lỗi nghiệp vụ**

* NOT\_FOUND (404) — phiên không thuộc Customer

**GET   /payments/admin/issues**  —  Việc cần xử lý về tiền (Must)

**Quyền: operations:read:any    UC: E08, E02**

* Gom: Payment PENDING quá P-04, Refund FAILED hoặc PENDING quá ngưỡng, phiên needsManualReview.

**Tham số (path / query)**

| Tên | Vị trí | Kiểu | Bắt buộc | Mô tả / ràng buộc |
| :---- | :---- | :---- | :---- | :---- |
| type | query | enum |  | PAYMENT\_PENDING \| REFUND\_FAILED \| REFUND\_STUCK \| MANUAL\_REVIEW |
| page, size | query |  |  |  |

**Response thành công**

200 → data: \[{ issueType, paymentId, refundId, checkoutSessionId, orderId, amountVnd, since, retryCount }\] \+ meta

**GET   /payments/admin/payments/by-checkout/{checkoutSessionId}**  —  Payment chung và các Refund

**Quyền: operations:read:any    UC: E08**

**Response thành công**

200 → data: Payment \+ { refunds: Refund\[\], manualNote }

**GET   /payments/admin/refunds**  —  Danh sách khoản hoàn

**Quyền: operations:read:any    UC: E08**

**Tham số (path / query)**

| Tên | Vị trí | Kiểu | Bắt buộc | Mô tả / ràng buộc |
| :---- | :---- | :---- | :---- | :---- |
| status | query | RefundStatus |  |  |
| orderId, paymentId | query | uuid |  |  |
| page, size | query |  |  |  |

**Response thành công**

200 → data: Refund\[\] \+ meta

**POST   /payments/admin/refunds/{refundId}/query**  —  Tra cứu lại kết quả hoàn tại bộ giả lập

**Quyền: refund:retry    UC: E08, G02**

* Gọi PaymentGateway.query (bộ giả lập) và cập nhật trạng thái nếu đã có kết quả; nếu SUCCESS thì phát refund.succeeded để Finance ghi REFUND\_DEBIT. Ghi AdminAuditLog.

**Response thành công**

200 → data: Refund

**POST   /payments/admin/refunds/{refundId}/retry**  —  Thử lại hoàn tiền an toàn

**Quyền: refund:retry    UC: E08, G02**

* Chỉ khi Refund FAILED và đã tra cứu xác nhận chưa hoàn. Dùng **lại** idempotency key cũ, không tạo Refund mới. Ghi AuditLog RETRY\_REFUND.  
    
* Không có API đánh dấu tay "đã hoàn" (FRD UC-E08).

**Response thành công**

200 → data: Refund (status PENDING)

**Lỗi nghiệp vụ**

* REFUND\_RETRY\_NOT\_SAFE (409)

**POST   /payments/admin/checkout-sessions/{checkoutSessionId}/mark-handled**  —  Đánh dấu đã xử lý trường hợp đối soát

**Quyền: refund:retry    UC: E08**

* Chỉ thêm ghi chú và gỡ cờ needsManualReview; không đổi trạng thái tiền. Ghi AuditLog MARK\_HANDLED.

**Request body (application/json)**

| Field | Kiểu | Bắt buộc | Validation / ghi chú |
| :---- | :---- | :---- | :---- |
| note | string | ✓ | 1–1.000 ký tự |

**Response thành công**

200 → data: { checkoutSessionId, manualNote, handledAt }

**GET   /payments/admin/summary**  —  Số liệu thu / hoàn

**Quyền: operations:read:any    UC: E02**

* Thu \= Payment SUCCESS (mỗi phiên đếm một lần); hoàn \= Refund SUCCESS; chờ hoàn \= Refund PENDING \+ FAILED (P-13); số dư chờ của đơn bị hủy chỉ giảm khi Refund SUCCESS (DB 8.3).

**Tham số (path / query)**

| Tên | Vị trí | Kiểu | Bắt buộc | Mô tả / ràng buộc |
| :---- | :---- | :---- | :---- | :---- |
| from, to | query | date | ✓ | Theo thời điểm hoàn tất |

**Response thành công**

200 → data: { collectedVnd, refundedVnd, pendingRefundVnd, pendingPaymentCount, failedRefundCount, isSimulated }

### **6.5.1 Bộ giả lập thanh toán nội bộ (thay cho Mock Provider)**

MVP không có nhà cung cấp thanh toán bên ngoài, nên **không có endpoint callback công khai**. Payment service gọi interface PaymentGateway (charge, refund, query); cài đặt trong MVP là SimulatedGateway. Kết quả đến sau (kịch bản TIMEOUT/LATE\_SUCCESS) được lưu ở bảng kỹ thuật SimulatedTransaction (DB mục 6\) và đến tay Payment bằng cách bị tra cứu (J2/J5, Admin) hoặc bộ giả lập tự gọi hàm xử lý kết quả bên trong Payment service — cùng một đường xử lý idempotent theo providerTransactionId / providerRefundId. Khi chuyển sang cổng thật sau MVP chỉ thêm một cài đặt PaymentGateway (và endpoint callback có xác thực chữ ký nếu cổng yêu cầu); DTO và trạng thái giữ nguyên.

| Kịch bản | Kết quả | Giai đoạn | Cách chọn |
| :---- | :---- | :---- | :---- |
| SUCCESS | Thanh toán / hoàn tiền thành công ngay; POST /payments trả 201 SUCCESS | 1 (mặc định) | Mặc định mọi môi trường |
| FAIL | Thanh toán bị từ chối rõ ràng → 201 FAILED; phiên và N đơn đóng ở PAYMENT\_FAILED | 2 | simulatorScenario (dev/staging) |
| TIMEOUT | 202 PENDING, không bao giờ tự có kết quả → kiểm thử J2/J2b (P-02, P-04) | 2 | simulatorScenario (dev/staging) |
| LATE\_SUCCESS | 202 PENDING, sau P-SIM-01 tự chuyển SUCCESS → nếu phiên đã đóng thì tạo Refund FULL\_CHECKOUT | 2 | simulatorScenario (dev/staging) |
| REFUND\_FAIL | Refund trả FAILED → kiểm thử J5 và UC-E08; Settlement giữ PENDING\_REFUND, số dư chờ chưa bị trừ | 2 | Cấu hình môi trường của Payment service (SIMULATOR\_REFUND\_SCENARIO), chỉ dev/staging |

Giai đoạn 1 vẫn đi đủ trạng thái PENDING → SUCCESS/FAILED và phát đủ event như thiết kế đầy đủ, để giai đoạn 2 chỉ bật thêm nhánh mà không đổi API hay dữ liệu.

## **6.6 Finance Service**

**Phạm vi:** Settlement theo đơn, ví Seller và ví nền tảng, bút toán append-only, rút tiền giả lập, tỷ lệ hoa hồng (P-COM-01), báo cáo tiền và đối soát. **UC:** D04 (phần tiền), D05, G04, một phần E02, E08.

| Sự kiện nhận | Tác động trong Finance (Database RBAC v5.0 mục 8.3) |
| :---- | :---- |
| checkout.paid | Với mỗi Order: tạo Settlement PENDING; WalletTransaction PAYMENT\_RECEIVED: ví Seller pending \+S, ví nền tảng pending \+C. |
| order.cancelled (đơn đã thanh toán) | Settlement → PENDING\_REFUND (khóa việc chuyển sang khả dụng). Chưa ghi bút toán; số dư chờ giữ nguyên đến khi hoàn tiền thành công (DB 3.5.1, 3.5.3). |
| refund.succeeded (scope ORDER) | Settlement → REFUNDED; WalletTransaction REFUND\_DEBIT: ví Seller pending −S, ví nền tảng pending −C. |
| order.confirmed | Settlement → SETTLED, chốt C và S; WalletTransaction SETTLEMENT\_RELEASED: Seller pending −S, available \+S; nền tảng pending −C, available (hoa hồng đã chốt) \+C. Phát funds.available. |
| seller.approved | Tạo ví Seller, mọi số dư \= 0\. |
| seller.banned/unbanned; user.disabled/enabled | Tính lại quyền rút/isFrozen theo trạng thái User \+ Seller \+ SELLER hiện hành; không mở ví chỉ vì một scope được gỡ trong khi scope khác vẫn bị chặn. API rút kiểm tra Identity đồng bộ. |

### **Danh sách endpoint**

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

### **DTO**

| DTO | Field |
| :---- | :---- |
| Wallet | walletId, ownerType, sellerId, sellerName (Admin), pendingBalanceVnd, availableBalanceVnd, totalWithdrawnVnd (DB: pending\_balance\_vnd, available\_balance\_vnd, total\_withdrawn\_vnd), minWithdrawalVnd, isFrozen (tính từ Seller.status \= BANNED, không lưu trong Wallet), canWithdraw, withdrawDisabledReason, isSimulated |
| WalletTransaction | walletTransactionId, transactionType (WalletTransactionType), referenceType (PAYMENT \| ORDER \| REFUND \| WITHDRAWAL), referenceId, settlementId, withdrawalId, orderId (khi liên quan đơn), pendingDeltaVnd, availableDeltaVnd, withdrawnDeltaVnd, createdAt |
| Settlement | settlementId, orderId, paymentId, grossAmountVnd, commissionBpsSnapshot, commissionAmountVnd, sellerAmountVnd (tính khi tạo Settlement; khi PENDING là khoản đang chờ, chưa rút được), status, createdAt, settledAt, isSimulated |
| Withdrawal | withdrawalId, withdrawalCode, amountVnd, availableBeforeVnd, availableAfterVnd, destination, withdrawnAt, isSimulated |

### **Chi tiết endpoint**

**GET   /finance/seller/wallet**  —  Ví của tôi

**Quyền: wallet:read:own    UC: D05, D04**

* Ba số dư theo DB 3.5.2, 8.3: chờ quyết toán (không rút được), khả dụng (rút được), đã rút. pendingBalanceVnd \= Σ sellerAmount của Settlement PENDING và PENDING\_REFUND (gồm đơn đã hủy đang chờ hoàn tiền).  
    
* canWithdraw và withdrawDisabledReason để FE bật/tắt nút và hiển thị giải thích đúng UC-D05.

**Response thành công**

200 → data: Wallet

{ "data": { "walletId": "wl-5d2a", "ownerType": "SELLER", "sellerId": "5d2a...",  
"pendingBalanceVnd": 285000, "availableBalanceVnd": 190000, "totalWithdrawnVnd": 400000,  
"minWithdrawalVnd": 10000, "isFrozen": false,  
"canWithdraw": true, "withdrawDisabledReason": null,  
"isSimulated": true } }

withdrawDisabledReason ∈ { null, "BELOW\_MINIMUM\_PENDING\_EXISTS", "BELOW\_MINIMUM", "WALLET\_FROZEN", "WALLET\_INCONSISTENT" }

**GET   /finance/seller/wallet/transactions**  —  Lịch sử biến động ví

**Quyền: wallet:read:own    UC: D05**

**Tham số (path / query)**

| Tên | Vị trí | Kiểu | Bắt buộc | Mô tả / ràng buộc |
| :---- | :---- | :---- | :---- | :---- |
| type | query | WalletTransactionType |  |  |
| from, to | query | date |  |  |
| page, size | query |  |  | sort createdAt,desc |

**Response thành công**

200 → data: WalletTransaction\[\] \+ meta

**GET   /finance/seller/settlements**  —  Tiền theo từng đơn

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

**POST   /finance/seller/withdrawals**  —  Rút tiền (giả lập, tức thời)

**Quyền: withdrawal:create:own    UC: D05    Idempotency-Key: bắt buộc**

* Theo WD-01…WD-05 (DB 8.3): trong **một transaction** cập nhật có điều kiện available ≥ amount, trừ khả dụng, cộng đã rút, tạo Withdrawal và WalletTransaction WITHDRAWAL\_COMPLETED (DB 3.5.4). Thành công ngay, không có trạng thái chờ, không cần Admin duyệt.  
    
* sellerId=sub đã xác minh; kiểm tra withdrawal:create:own, User ACTIVE, Seller APPROVED/ACTIVE ngay trước transaction, ownerId ví bằng sub. Không nhận sellerId/ownerId hoặc thông tin ngân hàng từ client.  
    
* Phát withdrawal.completed → Notification gửi biên nhận.  
    
* Database RBAC v5.0 không có Payout, ngăn reserved hay trạng thái Withdrawal: mỗi bản ghi Withdrawal là một lần rút đã thành công. Lỗi giữa chừng → rollback, không tạo bản ghi.

**Request body (application/json)**

| Field | Kiểu | Bắt buộc | Validation / ghi chú |
| :---- | :---- | :---- | :---- |
| amountVnd | int | ✓ | Số nguyên, 10.000 ≤ amount ≤ số dư khả dụng |

**Response thành công**

201 → data: Withdrawal (gửi lặp cùng key → 200, cùng giao dịch)

POST /api/v1/finance/seller/withdrawals  
Idempotency-Key: 9a0b7f31-4f0e-4a6f-8f3e-6c1b2d4e5a77  
{ "amountVnd": 190000 }

201 Created  
{ "data": { "withdrawalId": "wd-01f...", "withdrawalCode": "RT-20261018-K2P9", "amountVnd": 190000,  
"availableBeforeVnd": 190000, "availableAfterVnd": 0,  
"destination": "Ví ngoài giả lập", "withdrawnAt": "2026-10-18T09:00:00Z",  
"isSimulated": true } }

**Lỗi nghiệp vụ**

* WITHDRAWAL\_BELOW\_MINIMUM (422)  
    
* INSUFFICIENT\_AVAILABLE\_BALANCE (409) — kể cả khi hai tab rút đồng thời  
    
* WALLET\_FROZEN (403)  
    
* WALLET\_INCONSISTENT (409)  
    
* IDEMPOTENCY\_KEY\_REUSED (409)

**GET   /finance/seller/withdrawals**  —  Lịch sử rút tiền

**Quyền: wallet:read:own    UC: D05**

**Tham số (path / query)**

| Tên | Vị trí | Kiểu | Bắt buộc | Mô tả / ràng buộc |
| :---- | :---- | :---- | :---- | :---- |
| from, to | query | date |  |  |
| page, size | query |  |  |  |

**Response thành công**

200 → data: Withdrawal\[\] \+ meta

**GET   /finance/seller/withdrawals/{withdrawalId}**  —  Chi tiết giao dịch rút

**Quyền: wallet:read:own    UC: D05**

**Response thành công**

200 → data: Withdrawal

**GET   /finance/admin/summary**  —  Các ngăn tiền toàn hệ thống

**Quyền: operations:read:any    UC: E02**

* Số dư tại thời điểm xem (không trộn với số phát sinh theo kỳ — DB 8.3).

**Response thành công**

200 → data: { sellerPendingVnd, platformPendingVnd, commissionSettledVnd, sellerAvailableVnd, totalWithdrawnVnd, frozenWalletCount, frozenAvailableVnd, isSimulated }

**GET   /finance/admin/reconciliation**  —  Kiểm tra bảo toàn tiền

**Quyền: operations:read:any    UC: E02**

* Finance lấy tổng thu/hoàn/chờ hoàn từ Payment (internal) rồi kiểm tra: tổng thu \= đã hoàn \+ Refund FULL\_CHECKOUT chưa thành công \+ Σ chờ quyết toán (gồm phần đơn đang chờ hoàn) \+ hoa hồng đã chốt \+ Σ khả dụng \+ Σ đã rút (DB 8.3), và WD-11 cho từng Seller.

**Response thành công**

200 → data: { checkedAt, balanced: boolean, totals: { ... }, differenceVnd, inconsistentWallets: \[{ walletId, sellerId, differenceVnd }\] }

**GET   /finance/admin/wallets**  —  Tra cứu ví (chỉ xem)

**Quyền: operations:read:any    UC: E08, E01**

**Tham số (path / query)**

| Tên | Vị trí | Kiểu | Bắt buộc | Mô tả / ràng buộc |
| :---- | :---- | :---- | :---- | :---- |
| ownerType | query | WalletOwnerType |  |  |
| sellerId | query | uuid |  |  |
| frozen | query | bool |  | Danh sách ví Seller bị cấm |
| page, size | query |  |  |  |

**Response thành công**

200 → data: Wallet\[\] \+ meta

**GET   /finance/admin/wallets/{walletId}/transactions**  —  Bút toán của một ví

**Quyền: operations:read:any    UC: E08**

**Response thành công**

200 → data: WalletTransaction\[\] \+ meta

**GET   /finance/admin/withdrawals**  —  Giao dịch rút (chỉ xem)

**Quyền: operations:read:any    UC: E08**

**Tham số (path / query)**

| Tên | Vị trí | Kiểu | Bắt buộc | Mô tả / ràng buộc |
| :---- | :---- | :---- | :---- | :---- |
| sellerId | query | uuid |  |  |
| withdrawalCode | query | string |  |  |
| from, to | query | date |  |  |

**Response thành công**

200 → data: Withdrawal\[\] \+ meta

**GET   /finance/admin/settlements**  —  Quyết toán theo đơn

**Quyền: operations:read:any    UC: E08**

**Tham số (path / query)**

| Tên | Vị trí | Kiểu | Bắt buộc | Mô tả / ràng buộc |
| :---- | :---- | :---- | :---- | :---- |
| orderId, sellerId | query | uuid |  |  |
| status | query | SettlementStatus |  |  |

**Response thành công**

200 → data: Settlement\[\] \+ meta

## **6.7 Notification Service**

**Nhận event hoặc internal POST từ service được phép, tạo Notification và gửi email bất đồng bộ (Must), in-app (Should). User tự đọc/xóa thông báo của mình; không mở API public tạo thông báo. Xóa in-app không xóa email delivery, audit hay giao dịch. Admin không cần màn thông báo riêng trong MVP nhưng API tự phục vụ dùng cùng policy nếu có dữ liệu.**

| Event | Người nhận | NotificationType |
| :---- | :---- | :---- |
| checkout.paid | Customer (1 thông báo gồm checkoutSessionId, tổng tiền và danh sách đơn theo cửa hàng); mỗi Seller (đơn mới của mình) | CHECKOUT\_PAID, NEW\_ORDER |
| checkout.closed (FAILED / EXPIRED) | Customer | PAYMENT\_FAILED, CHECKOUT\_EXPIRED |
| order.delivered | Customer | ORDER\_DELIVERED |
| order.cancelled | Customer \+ Seller (theo cancelledBy, reason) | ORDER\_CANCELLED, ORDER\_REJECTED, ORDER\_AUTO\_CANCELLED |
| order.confirmed / funds.available | order.confirmed thông báo trạng thái; funds.available thông báo tiền sau khi Finance commit | ORDER\_CONFIRMED / ORDER\_AUTO\_CONFIRMED / FUNDS\_AVAILABLE |
| refund.succeeded | Customer | REFUND\_SUCCEEDED |
| withdrawal.completed | Seller (biên nhận) | WITHDRAWAL\_COMPLETED |
| seller.registered / approved / rejected | User sở hữu Seller; ngữ cảnh SELLER | SELLER\_REGISTERED / SELLER\_APPROVED / SELLER\_REJECTED |
| customer.banned / seller.banned / unbanned | Tài khoản tương ứng | ACCOUNT\_BANNED, ACCOUNT\_UNBANNED |
| product.removed | Seller | PRODUCT\_REMOVED |
| password.reset\_requested | Tài khoản (email chứa liên kết) | — (chỉ email, không lưu in-app) |

DedupeKey \= eventId:channel:recipientUserId:audienceContext. recipientUserId luôn là User.user\_id; CUSTOMER/SELLER/ACCOUNT là ngữ cảnh nội dung. Soft delete giữ khóa dedupe để event retry không tạo lại thông báo đã xóa. Tiền giả lập luôn có nhãn rõ.

### **Danh sách endpoint**

| \# | Method | Path | Mô tả | Quyền | UC |
| :---- | :---- | :---- | :---- | :---- | :---- |
| 1 | GET | /notifications/me | Thông báo trong ứng dụng (Should) | notification:read:own | G01 |
| 2 | GET | /notifications/me/unread-count | Số chưa đọc (chuông) | notification:read:own | G01 |
| 3 | POST | /notifications/me/{notificationId}/read | Đánh dấu đã đọc | notification:read:own | G01 |
| 4 | POST | /notifications/me/read-all | Đánh dấu tất cả đã đọc | notification:read:own | G01 |
| 5 | DELETE | /notifications/me/{notificationId} | Xóa thông báo in-app của mình | notification:read:own | G01 |

### **DTO**

| DTO | Field |
| :---- | :---- |
| Notification | notificationId, audienceContext (CUSTOMER\|SELLER\|ACCOUNT), type, title, content, reference { type, id, code }, isRead, createdAt. code chỉ có giá trị với WITHDRAWAL (withdrawalCode), còn lại null. recipientUserId giữ nội bộ; deletedAt là field lưu soft delete, API list chỉ trả chưa xóa. |

### **Chi tiết endpoint**

**GET   /notifications/me**  —  Thông báo trong ứng dụng (Should)

**Quyền: notification:read:own    UC: G01**

**Tham số (path / query)**

| Tên | Vị trí | Kiểu | Bắt buộc | Mô tả / ràng buộc |
| :---- | :---- | :---- | :---- | :---- |
| unreadOnly | query | bool |  |  |
| page, size | query |  |  | Tối đa 50 gần nhất, lưu 90 ngày (P-22 đề xuất) |

**Response thành công**

200 → data: Notification\[\] \+ meta

**GET   /notifications/me/unread-count**  —  Số chưa đọc (chuông)

**Quyền: notification:read:own    UC: G01**

**Response thành công**

200 → data: { unreadCount }

**POST   /notifications/me/{notificationId}/read**  —  Đánh dấu đã đọc

**Quyền: notification:read:own    UC: G01**

**Response thành công**

204

**POST   /notifications/me/read-all**  —  Đánh dấu tất cả đã đọc

**Quyền: notification:read:own    UC: G01**

**Response thành công**

204

### **DELETE  /notifications/me/{notificationId}  —  Xóa thông báo của tôi**

Quyền: notification:read:own

Request: notificationId UUID trong path; không có body

Response: 204 nếu đã xóa hoặc xóa lần đầu; 404 nếu không tồn tại/không sở hữu

Chỉ Notification IN\_APP, cập nhật deletedAt và isRead=true nguyên tử; list/unread-count/read-all bỏ bản ghi deletedAt khác null. Giữ delivery EMAIL và khóa dedupe; không hủy sự kiện hay giao dịch nguồn. notification:read:own bao gồm quản lý trạng thái hiển thị của chính mình trong MVP; không có quyền xóa của người khác.

# **7\. API nội bộ và sự kiện giữa các service**

## **7.1 Internal API (đồng bộ)**

Base /internal/v1, chỉ gọi bởi service đã xác thực (mTLS hoặc token nội bộ theo audience/scope), Gateway chặn public. Mỗi endpoint có allowlist consumer. User ID truyền nội bộ phải xuất phát từ Bearer đã xác minh hoặc event tin cậy; không coi mạng nội bộ là đủ quyền. Timeout mặc định 2s; retry thao tác ghi chỉ khi có idempotency key.

| Provider | Method \+ Path | Consumer | Mục đích / response chính |
| :---- | :---- | :---- | :---- |
| Identity | POST /identity/auth/authorization-context | Gateway, mọi service bảo vệ | Bearer \+ service credential → { userId, userStatus, authzVersion, roles\[\], permissions\[\], customerProfile, sellerProfile, sessionActive }; kiểm tra sessionId và token version. |
| Identity | GET /identity/sellers/status?ids= | Product, Cart, Order, Finance | \[{ sellerId, userId, userStatus, approvalStatus, status, sellerRoleActive, sellable }\]; no credential/PII; quyết định hiện hành không cache quyền. |
| Identity | GET /identity/customers/{id}/checkout-eligibility | Order, Payment | { userId, userStatus, customerStatus, paymentBlocked, hasPhone, addressCount, canCheckout, authzVersion }; xác minh quyền và Customer. |
| Identity | GET /identity/customers/{id}/addresses/{addressId} | Order | Snapshot địa chỉ gồm recipientName, recipientPhone, provinceCode, provinceName, wardCode, wardName, addressDetail (tên lấy qua RegionProvider); 404 nếu không thuộc Customer. |
| Identity | POST /identity/customers/{id}/addresses/{addressId}/touch | Order | { checkoutSessionId }; idempotent theo checkoutSessionId, cập nhật lastUsedAt sau checkout commit; địa chỉ đã bị xóa thì bỏ qua, không báo lỗi. |
| Identity | GET /identity/users/{userId}/contact | Notification | { userId, email, fullName }; service credential allowlist, bỏ contact theo actorType. |
| Product | POST /products/batch | Cart, Order | { productIds } → productId, sellerId, giá, tên, media, sellable; không join DB. |
| Product | POST /products/reviews/summary | Order | { customerId, productIds\[\] } ≤100 → \[{ productId, reviewId\|null, hasReviewed }\]; từ customerId của đơn đã xác minh, chỉ allow Order; không API public my-reviews. |
| Order | GET /inventory/availability?productIds= | Product, Cart | \[{ productId, availableQuantity }\]. |
| Order | POST /inventory | Product | { productId, sellerId, onHandQuantity }; UQ productId và request hash; lặp cùng payload trả kết quả cũ, khác payload 409\. |
| Order | GET /inventory/details?productIds= | Product | Tồn thực/giữ/khả dụng/version; Product Service chỉ chuyển tiếp dữ liệu đúng quyền Seller/Admin. |
| Order | GET /orders/checkout-sessions/{id}/payable | Payment | { customerId, status, expiresAt, totalAmountVnd, orders\[\] }; Payment đối chiếu customerId với sub. |
| Order | GET /orders/customers/{id}/has-paid-order | Identity | { hasPaidOrder, evaluatedAt }; quyết định G-14 lúc ban. Identity không truy vấn DB Order. |
| Order | GET /orders/order-items/{id}/review-eligibility?customerId= | Product | { eligible, productId, orderStatus }; xác minh chủ Order và CONFIRMED. |
| Payment | GET /payments/by-checkout/{checkoutSessionId} | Order | Trạng thái Payment; Order đã kiểm tra chủ phiên. |
| Payment | GET /payments/refunds?orderIds= | Order | Trạng thái hoàn của các Order. |
| Payment | GET /payments/totals | Finance | Tổng thu/hoàn/chờ hoàn để đối soát. |
| Finance | GET /finance/commission-rate | Order | { commissionBps } hiện hành để snapshot. |
| Notification | POST /notifications | Identity, Product, Order, Payment, Finance | Tạo thông báo từ service allowlist; hợp đồng chi tiết 7.1.1. Không lộ qua public Gateway. |

### **7.1.1 Tạo thông báo nội bộ**

POST /internal/v1/notifications. Quyền INTERNAL, service credential có notification:create và allowlist producer/type. Request: { eventId, recipientUserId, audienceContext: CUSTOMER\|SELLER\|ACCOUNT, type: NotificationType, channels: \[EMAIL\|IN\_APP\], reference?: { type, id, code }, templateData: object }. Không nhận nội dung HTML, URL redirect hay email người nhận tự do từ public client; email lấy từ Identity qua userId.

201 → data: { notificationIds\[\], status: "PENDING" }; retry cùng event/type/recipient/context/kênh và payload →200 cùng ID, khác payload →409 IDEMPOTENCY\_KEY\_REUSED. Commit Notification \+ dedupe trước khi trả; email xử lý bất đồng bộ J6. Mỗi kênh là một bản ghi để xóa in-app không ảnh hưởng email.

Allowlist: Identity chỉ seller lifecycle/account/reset; Product chỉ product moderation; Order chỉ checkout/order lifecycle; Payment chỉ payment/refund; Finance chỉ funds.available/withdrawal.completed. Producer phải suy ra recipientUserId từ dữ liệu nghiệp vụ sở hữu; Notification kiểm tra User có tồn tại, template phù hợp type và reference. 400 validation, 401 credential sai, 403 NOTIFICATION\_TYPE\_NOT\_ALLOWED, 404 User không tồn tại, 409 payload xung đột.

Consumer event Outbox và internal POST dùng chung hàm createNotification với eventId và dedupeKey thống nhất. Một sự kiện chỉ chọn một đường phát tại producer; nếu cả hai tới thì vẫn một bản ghi mỗi kênh. Kể cả bản ghi đã soft delete, retry trả ID cũ, không khôi phục hiển thị.

### **7.1.2 Nguồn quyền và dữ liệu liên service**

Authorization context trả trạng thái hiện hành từ Identity; không cần service tự đọc users/user\_roles trực tiếp. G-14 cần hasPaidOrderBeforeBan: Identity lưu kết quả đánh giá cùng thời điểm ban trong metadata AdminAuditLog hoặc policy snapshot, không dùng kết quả đến sau thời điểm cấm để tự mở quyền. Trước khi ghi snapshot ban, kiểm tra version hồ sơ, retry an toàn nếu trạng thái đổi. Source Order không sẵn sàng thì ban CUSTOMER trả 503 và không cập nhật một phần; USER/SELLER không cần truy vấn này.

Điều phối giữa các service dùng outbox/inbox và retry; không join database chéo. Khi event cancel/confirm/refund tới Finance trước checkout.paid hoặc trước ví được tạo, consumer giữ bản ghi chờ phụ thuộc và retry; không đánh dấu đã áp dụng thành công hoặc ghi số dư âm. Event cùng aggregate phải kiểm tra version/state; event cũ không đảo trạng thái mới. Đối soát J5/Finance kiểm tra lại service sở hữu khi thiếu dữ kiện; không tự đoán số tiền từ notification.

## **7.2 Sự kiện (bất đồng bộ, qua Outbox)**

Event dùng Outbox, consumer Inbox chống lặp eventId; envelope thêm actor { userId\|null, kind: USER\|SERVICE, context: CUSTOMER\|SELLER\|ADMIN\|ACCOUNT\|null }. role/context trong event chỉ là dữ liệu audit, không cấp quyền gọi API. Mỗi aggregate có version tăng đơn điệu; consumer không áp dụng ngược trạng thái từ event cũ.

{ "eventId": "evt-0c1...", "eventType": "order.cancelled", "occurredAt": "2026-10-19T02:10:00Z",  
"producer": "order-service", "aggregateId": "od-2", "version": 1,  
"payload": { ... } }

| Event | Producer | Payload chính | Consumer |
| :---- | :---- | :---- | :---- |
| payment.succeeded | Payment | paymentId, checkoutSessionId, amountVnd, completedAt | Order |
| payment.failed | Payment | paymentId, checkoutSessionId, errorCode | Order |
| checkout.paid | Order | checkoutSessionId, customerId, source, paidAt, cartItems\[{ cartItemId, quantity }\], orders\[{ orderId, sellerId, totalAmountVnd, commissionBps }\] | Cart, Finance, Notification |
| checkout.closed | Order | checkoutSessionId, status (PAYMENT\_FAILED \| EXPIRED \| CANCELLED) | Notification |
| order.packing | Order | orderId, sellerId | (Notification — tùy chọn) |
| order.delivered | Order | orderId, customerId, sellerId, deliveredAt, customerConfirmDueAt | Notification |
| order.cancelled | Order | orderId, checkoutSessionId, customerId, sellerId, totalAmountVnd, wasPaid, cancelledBy, reasonCode, reasonNote, refundReason | Payment (nếu wasPaid), Finance, Notification |
| order.confirmed | Order | orderId, sellerId, customerId, confirmedAt, confirmedBy (CUSTOMER \| SYSTEM) | Finance, Notification |
| refund.succeeded | Payment | refundId, paymentId, orderId, scope, amountVnd | Finance, Order, Notification |
| refund.failed | Payment | refundId, orderId, errorCode, retryCount | (Admin dashboard qua API) |
| funds.available | Finance | orderId, sellerId, sellerAmountVnd | Notification |
| withdrawal.completed | Finance | withdrawalId, sellerId, amountVnd, availableAfterVnd | Notification |
| seller.registered / approved / rejected | Identity | userId, sellerId (=userId), approvalStatus, authzVersion, reason? | Finance (approved), Notification |
| seller.banned / seller.unbanned | Identity | userId, sellerId, status, authzVersion, reason | Order hủy PAID/PACKING khi banned; Finance/Notification |
| customer.banned / customer.unbanned | Identity | userId, customerId, banMode (PAYMENT\_ONLY \| CUSTOMER\_SCOPE), hasPaidOrderBeforeBan, authzVersion | Notification |
| product.removed | Product | productId, sellerId, reason | Notification |
| password.reset\_requested | Identity | userId, resetUrl (secret nội bộ, không log), expiresAt | Notification EMAIL; không lưu in-app, queue retention ngắn |
| user.disabled / user.enabled | Identity | userId, authzVersion, reason, hasSellerProfile | Order xử lý Seller PAID/PACKING nếu disable; Finance tính freeze theo policy còn hiệu lực; Notification |
| user.roles\_changed | Identity | userId, roleCodes hiện hành, authzVersion | Audit/đồng bộ policy; API vẫn hỏi quyền hiện hành |

## **7.3 Job nền**

| Job | Service | Việc làm | Tham số |
| :---- | :---- | :---- | :---- |
| J1 | Order | Phiên AWAITING\_PAYMENT quá expiresAt và không có Payment PENDING → EXPIRED cho phiên \+ N đơn, nhả giữ hàng | P-01 |
| J2 / J2b | Payment | Tra cứu Payment PENDING; quá P-04 vẫn chưa có kết quả → báo Order đóng phiên, tiếp tục theo dõi để hoàn toàn bộ nếu tiền đến muộn | P-02, P-03, P-04 |
| J3 (Must) | Order | Đơn DELIVERED quá customerConfirmDueAt → CONFIRMED (confirmedBy \= SYSTEM) | S-RECEIPT-07 |
| J4 (Must) | Order | Đơn PAID/PACKING quá sellerActionDueAt → CANCELLED (refundReason \= SELLER\_TIMEOUT) | S-DELIVERY-07 |
| J5 | Payment | Tra cứu / retry an toàn Refund PENDING hoặc FAILED (cùng idempotency key) | Chưa có tham số (Param mục 8.9) |
| J6 | Notification | Gửi lại email lỗi | P-19 |

Chu kỳ chạy thuộc N-06 (chưa chốt). Mọi job khóa bản ghi và kiểm tra trạng thái hiện tại để không chạy chồng với thao tác người dùng (J3 với "Đã nhận hàng", J4 với "Đã giao"/"Từ chối"). J7 (payout) không còn dùng (FRD v5.2). J2/J2b/J5 chỉ có việc thực sự để làm khi bật giai đoạn 2 của bộ giả lập nhưng vẫn triển khai từ giai đoạn 1\.

# **8\. Luồng tích hợp tiêu biểu**

## **8.1 Checkout nhiều cửa hàng và thanh toán**

| \# | Bên gọi → Bên nhận | Hành động | Kết quả |
| :---- | :---- | :---- | :---- |
| 1 | FE → Order | POST /orders/checkout/preview | Order đọc giá/Seller (Product), tồn kho, địa chỉ (Identity) → 200 CheckoutPreview |
| 2 | FE → Order | POST /orders/checkout-sessions \+ Idempotency-Key | Một transaction: CheckoutSession \+ N Order \+ OrderItem \+ Reservation → 201 (AWAITING\_PAYMENT) |
| 3 | FE → Payment | POST /payments \+ Idempotency-Key | Payment gọi GET /internal/v1/orders/checkout-sessions/{id}/payable, gọi bộ giả lập (PaymentGateway.charge) → 201 SUCCESS (hoặc 202 PENDING, FE poll) |
| 4 | Payment → Order | event payment.succeeded | Một transaction: N Order → PAID, Reservation COMMITTED, StockMovement SOLD, outbox checkout.paid |
| 5 | Order → Cart, Finance, Notification | event checkout.paid | Cart giảm dòng đã mua (nếu CART); Finance tạo Settlement \+ bút toán PAYMENT\_RECEIVED; Notification gửi email khách và từng Seller |
| 6 | FE → Order | GET /orders/me | Các đơn hiển thị "Chờ xử lý" |

## **8.2 Seller từ chối giao hàng**

| \# | Bên gọi → Bên nhận | Hành động | Kết quả |
| :---- | :---- | :---- | :---- |
| 1 | Seller FE → Order | POST /orders/seller/orders/{id}/reject | Order → CANCELLED, StockMovement RESTOCK, outbox order.cancelled |
| 2 | Order → Payment | event order.cancelled | Tạo Refund (ORDER, SELLER\_REJECT) bằng tổng đơn, gọi PaymentGateway.refund (bộ giả lập) |
| 3 | Order → Finance | event order.cancelled | Settlement PENDING\_REFUND (chưa trừ số dư chờ) |
| 4 | Order → Notification | event order.cancelled | Báo khách và Seller |
| 5 | Payment → Finance, Order, Notification | event refund.succeeded | Settlement REFUNDED \+ bút toán REFUND\_DEBIT (trừ pending của Seller và nền tảng); đơn hiển thị "Đã hoàn"; báo khách |

## **8.3 Xác nhận nhận hàng và rút tiền**

| \# | Bên gọi → Bên nhận | Hành động | Kết quả |
| :---- | :---- | :---- | :---- |
| 1 | Customer FE → Order | POST /orders/me/{id}/confirm-receipt | DELIVERED → CONFIRMED, outbox order.confirmed |
| 2 | Order → Finance | event order.confirmed | Settlement SETTLED; Seller chờ −S, khả dụng \+S; nền tảng chờ −C, hoa hồng \+C; phát funds.available |
| 3 | Seller FE → Finance | GET /finance/seller/wallet | canWithdraw \= true |
| 4 | Seller FE → Finance | POST /finance/seller/withdrawals \+ Idempotency-Key | Một transaction: khả dụng −A, đã rút \+A, Withdrawal, bút toán → 201 hoàn tất ngay |
| 5 | Finance → Notification | event withdrawal.completed | Gửi biên nhận cho Seller |

# **9\. Mâu thuẫn tài liệu và điểm cần chốt**

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
| C-08 | Tìm không dấu chưa được cam kết với LIKE cơ bản trong Database. | MVP cam kết không phân biệt hoa thường; tìm không dấu là mở rộng cần chốt. Không coi mô tả v0.3 là tính năng đã có. | BE chọn chuẩn hóa/unaccent nếu nhóm giữ yêu cầu FRD; cập nhật OpenAPI và test khi chốt. |
| C-09 | UC-A05 bước 2 nói "báo lỗi nếu email không tồn tại", nhưng luồng ngoại lệ của chính UC-A05 và G-05 yêu cầu luôn trả cùng một thông báo. | Theo G-05: luôn 202 cùng nội dung. | BA sửa bước 2 của UC-A05 (FRD v5.3 chưa sửa vì chưa có quyết định). |
| C-10 | **Đã chốt — theo Database.** Số dư chờ chỉ bị trừ khi hoàn tiền thành công. | Đơn hủy → Settlement PENDING\_REFUND, chưa ghi bút toán; Refund SUCCESS → REFUND\_DEBIT (Seller pending −S, nền tảng pending −C) \+ Settlement REFUNDED. | Đã cập nhật FRD v5.3 (bỏ hủy\_chờ ở B06, B08, D02, E03, G02, G03; phần Giải nghĩa công thức) và Database RBAC v5.0 mục 8.3. Không còn việc cần làm. |
| C-11 | Reporting thứ 8 vẫn tạm hoãn trong Database v5.0. | API triển khai 7 service; tra cứu vận hành tại service sở hữu. Các stats/dashboard trong bản trước để tùy chọn sau MVP. | Không bắt buộc các endpoint phân tích như /orders/\*/stats, /orders/seller/dashboard, /payments/admin/summary và /finance/admin/summary/reconciliation cho RBAC MVP; nếu triển khai vẫn kiểm tra permission. |
| C-12 | Ngưỡng "sắp hết hạn 7 ngày" trên dashboard Seller chưa có trong FRD. | Tham số dueWithinHours, mặc định 24\. | BA chốt. |
| C-13 | RefundReason: Database dùng SELLER\_CANCEL; FRD có hệ\_thống\_hủy (gồm Seller bị cấm). | CUSTOMER\_CANCEL, SELLER\_REJECT, SELLER\_CANCEL, SELLER\_TIMEOUT, LATE\_PAYMENT. Đơn bị hủy do Seller bị cấm hoặc không còn hợp lệ dùng SELLER\_CANCEL. | Database RBAC v5.0 (3.4.2) đã giải thích SELLER\_CANCEL; nhóm xác nhận. |

## **9.1 Tham số chưa chốt ảnh hưởng tới API**

| Tham số | Ảnh hưởng | Giá trị đang dùng cho môi trường dev |
| :---- | :---- | :---- |
| P-01 | TTL giữ hàng — expiresAt của phiên checkout | 15 phút |
| P-02 / P-04 | Timeout thanh toán / thời gian chờ tối đa trước khi đóng phiên | 15 giây / 30 phút |
| P-05 | Hạn liên kết đặt lại mật khẩu | 30 phút |
| P-06 | Hạn access / refresh token | 15 phút / 7 ngày |
| P-08 | Giới hạn ảnh/video (3.9) | Xem bảng 3.9 |
| P-09 | Số địa chỉ tối đa | 5 |
| P-10 | Số lượng tối đa mỗi dòng / số dòng giỏ | 99 / 50 |
| P-11 | Kích thước trang, sắp xếp mặc định | 20, mới nhất |
| P-12 | Hiển thị điểm đánh giá, che tên người đánh giá | Có, 1 chữ số thập phân |
| P-14 / P-15 | Múi giờ kỳ thống kê / sản phẩm bán chạy | GMT+7 / theo số lượng |
| P-21 | Danh sách lý do hủy / từ chối | Enum ở 4.2 |
| P-COM-01 | Tỷ lệ hoa hồng (bps) | 500 — **chỉ để test, không phải giá trị chốt** |
| P-SIM-01 | Độ trễ kịch bản LATE\_SUCCESS của bộ giả lập (giai đoạn 2\) | P-04 \+ 5 phút |
| N-06 | Chu kỳ job J1–J6 | Mỗi 1 phút (môi trường dev) |

## **9.2 Giới hạn của tài liệu này**

* Tài liệu dựa trên API v0.3 đính kèm, comment của Bùi Minh Quang và Database RBAC v5.0 đã thiết kế trong cùng phiên làm việc. Không khẳng định đã cập nhật FRD, UI, Figma hoặc code bên ngoài.  
    
* FE đối chiếu lại tên đường dẫn mới, UserSelfProfile và allowedActions; ba web app dùng chung login. Mục 10.3 và 10.4 nêu các thay đổi không tương thích và schema cần đồng bộ.  
    
* Tham số TTL, giới hạn upload/rate limit và nhà cung cấp địa giới còn là cấu hình đề xuất. Không khẳng định đã kiểm thử service thật, Google OAuth hoặc nhà cung cấp địa giới trong lần sửa tài liệu này.

# **10\. Quy trình sử dụng và nhật ký thay đổi**

## **10.1 Quy trình đề xuất**

* **Bước 1:** Cả nhóm review Mục 2–4 và Mục 9 (chốt các điểm C-xx), điền cột "Phụ trách" ở Mục 5\.  
    
* **Bước 2:** Người phụ trách từng service chuyển phần của mình sang OpenAPI 3.0 YAML (mỗi service một file), giữ nguyên tên field, enum và mã lỗi.  
    
* **Bước 3:** Dựng mock server (Prism) từ OpenAPI để FE code ngay; BE viết contract test theo OpenAPI.  
    
* **Bước 4:** Mọi thay đổi API: sửa tài liệu/OpenAPI trước, thông báo cho người dùng API đó, ghi vào bảng dưới.

## **10.2 Nhật ký thay đổi**

| Phiên bản | Ngày | Nội dung | Người sửa |
| :---- | :---- | :---- | :---- |
| v0.3 (draft) | 19/10/2026 | Đồng bộ FRD v5.3 / Database v4.6: chốt C-02, C-03, C-04 (theo FRD), C-10 (theo Database), C-06 chốt sau, C-07 đã xử lý; mọi tham chiếu FRD 4.x đổi sang mục Database (DB x.y, G-xx, WD-xx); bỏ endpoint công khai /payments/provider/mock/callback; thay Mock Provider bằng bộ giả lập thanh toán nội bộ (6.5.1), mockScenario → simulatorScenario, provider \= SIMULATOR, thêm kịch bản REFUND\_FAIL và lộ trình hai giai đoạn. | Claude, chờ nhóm review |
| v0.2 (draft) | 18/10/2026 | Đồng bộ Database v4.6: bỏ mọi tham chiếu Payout; enum WalletTransactionType, RefundReason, SettlementStatus và DTO Wallet/WalletTransaction/Settlement/Withdrawal theo tên field DB; Settlement không còn customerConfirmDueAt; luồng hoàn tiền theo DB (REFUND\_DEBIT khi Refund SUCCESS); cập nhật C-01, C-10, C-11, C-13. | Claude, chờ nhóm review |
| v0.1 (draft) | 18/10/2026 | Bản đầu: quy ước chung, ma trận UC → service, đặc tả 7 service, internal API, event, job, danh sách điểm cần chốt. | Claude (soạn nháp), chờ nhóm review |
| v1.0 (đồng bộ 06/11/2026) | 06/11/2026 | Đồng bộ với Database theo danh sách xung đột: đổi tên Catalog → Product (internal /internal/v1/products/\*\*); một tên cửa hàng là User.fullName, bỏ sellerName/contactPhone khỏi request và SellerSelfProfile; số dư chờ gồm Settlement PENDING \+ PENDING\_REFUND; xóa địa chỉ là hard delete; cấm Seller không phụ thuộc trạng thái duyệt; snapshot tên địa giới trong Order; DTO Cancellation (reasonCode/reasonNote); bỏ orderCode, checkoutCode, trackingNumber (đóng C-06). | Claude, chờ nhóm review |
| v1.0 | 25/10/2026 | RBAC theo Database v5.0; xử lý comment Bùi Minh Quang về auth, ban/unban User, public Catalog routes, review, audit, địa giới, SellerPublic, tạo/xóa Notification. Thêm hợp đồng internal, schema delta và kiểm thử chấp nhận. | Cập nhật theo yêu cầu chủ tài liệu |

## **10.3 Đối chiếu comment của Bùi Minh Quang**

Các comment gốc được giữ và neo tại các dòng đối chiếu dưới đây để tránh chỉ vào endpoint đã xóa. Các thay đổi này chỉ áp dụng trong tài liệu API hiện tại; mục 10.4 liệt kê điều chỉnh DB/OpenAPI cần đồng bộ khi triển khai.

| Comment gốc | Xử lý trong bản v1.0 |
| :---- | :---- |
| IDs 0, 2, 4, 6, 8, 10, 12 — dùng POST /identity/auth/login cho cả Customer Seller Admin | Một login theo User; xóa hai login Seller/Admin; refresh, reset, me và DTO không còn actorType độc quyền. Thêm permission và shared PK. |
| IDs 21, 23, 25, 27 — identity/admin/ban/{userId} | POST /identity/admin/ban/{userId} và /unban/{userId}, scope bắt buộc USER/CUSTOMER/SELLER; giữ ngoại lệ G-14 và không khóa nhầm vai trò khác. |
| ID 31 — chuẩn hóa /categories và /products | Public Catalog bỏ /catalog ở route, bảng endpoint, ví dụ và cross-reference; giữ /catalog riêng internal service routing; từ 06/11/2026 service đổi tên thành Product và internal dùng /internal/v1/products/\*\*. |
| IDs 33, 36 — API review không cần thiết | Bỏ PUT review và GET my-reviews. Tạo review chỉ một lần; Order lấy review summary qua internal Product khi cần biết đã đánh giá. |
| ID 29 — DB có lưu đâu tại audit-logs | Bỏ public GET audit-logs và DTO banHistory. Giữ audit kỹ thuật bắt buộc theo Database v5.0 để truy vết quyền/khóa/duyệt, không xây màn xem log. |
| IDs 14, 16 — lấy địa giới từ API có sẵn, không query DB | Hai endpoint là proxy adapter RegionProvider, timeout/cache/version và lỗi 503 rõ ràng; provider cụ thể là cấu hình cần chốt, không tự dựng nguồn DB địa giới. |
| ID 19 — Customer cũng xem được Seller | API PUBLIC /identity/sellers/{sellerId} trả SellerPublic cho mọi người; API Admin giữ dữ liệu xét duyệt nhạy cảm. Không mở DTO Admin cho Customer. |
| ID 40 — thiếu xóa và tạo noti | DELETE in-app theo owner; POST nội bộ cho service allowlist, dedupe chung với event; không cho public client tự tạo email/thông báo. |

## **10.4 Tác động lên schema và chuyển đổi API**

Database RBAC v5.0 là nền: User/Customer/Seller shared PK, bốn bảng RBAC, AuthSession, PasswordResetToken.user\_id, reviewed\_by\_user\_id, recipient\_user\_id và audience\_context đã được mô tả. Bản API này không sửa trực tiếp file database đã giao; các bổ sung dưới đây cần được đưa vào migration/đặc tả DB khi triển khai.

| Thành phần | Đồng bộ bắt buộc |
| :---- | :---- |
| Notification | Thêm deleted\_at TIMESTAMPTZ NULL; filter IN\_APP chưa xóa; giữ dedupe\_key UQ kể cả đã xóa. Một record/kênh; không xóa email log. NotificationType thêm SELLER\_REGISTERED, USER\_DISABLED, USER\_ENABLED. |
| Customer ban policy | Giữ ban\_mode và has\_paid\_order\_before\_ban trong metadata policy snapshot/AdminAuditLog tại thời điểm cấm để thực thi G-14 ổn định; không lấy cờ hasPaidOrder cập nhật sau cấm làm căn cứ tự gỡ hạn chế. |
| User/Customer/Seller API | Credential chỉ thuộc User; response shared IDs. Schema không cần cột actorType duy nhất. Không lưu avatar cho tới khi DB có thiết kế rõ; bỏ field avatar khỏi API hiện tại. |
| Product Review | Không còn public update/my-reviews; UNIQUE(customer\_id, product\_id) giữ nguyên. updated\_at có thể giữ để tương thích DB, không tạo endpoint sửa chỉ vì có cột. |
| RegionProvider | Không tạo bảng danh mục tỉnh/phường tự quản; lưu mã trong CustomerAddress và snapshot tên trong Order như thiết kế. Adapter/cache có dataset version, cấu hình nguồn cần được chốt. |
| AuthSession và event | Session ID trong JWT đối chiếu AuthSession; logout/reset/disable thu hồi phiên. Producer phát userId thống nhất, consumer không dùng role trong event để cấp quyền. |
| Thay đổi breaking | Xóa login tách loại tài khoản, profile Admin riêng, public audit, review edit/my-reviews; đổi public path của Product (bỏ /catalog); gộp ban/unban; AuthTokens đổi sang UserSelfProfile. FE/BE cập nhật đồng thời và ghi OpenAPI v1.0 trước cutover; không duy trì ba login cùng hoạt động. |

## **10.5 Kiểm thử chấp nhận RBAC và comment**

Một email login một lần với CUSTOMER+SELLER; Seller PENDING login được nhưng không quản lý shop/rút. User chỉ SELLER/ADMIN không mua; có thêm CUSTOMER hợp lệ thì mua được. Không nhận role/status từ client; Admin không tự duyệt. Token cũ sau thay quyền trả TOKEN\_STALE; session logout/reset không dùng lại; Identity lỗi không cho qua.

Ban CUSTOMER/SELLER không chặn phạm vi còn lại; User DISABLED chặn tất cả API bảo vệ. G-14 cho Customer đã trả tiền thao tác đơn cũ nhưng cấm checkout/thu mới; chưa có đơn trả tiền chặn nghiệp vụ Customer. Đổi ID giỏ/đơn/sản phẩm/ví/notification của người khác luôn bị từ chối, cả list và detail; idempotency replay vẫn kiểm tra quyền.

Không còn route public /catalog, login tách loại, PUT review, GET my-reviews hoặc public audit. SellerPublic không chứa email/lý do cấm. Region API timeout không có cache →503; mã ward không thuộc tỉnh →422 khi nguồn hợp lệ. Notification DELETE lần hai →204, list/unread không thấy đã xóa; retry event không làm sống lại. Public POST tạo noti không được định tuyến; internal producer trái allowlist →403; cùng dedupe/payload không gửi trùng.

Các kiểm thử trên là tiêu chí triển khai, chưa là kết quả chạy trên backend. Tài liệu đã được rà soát endpoint inventory và phần chi tiết để dùng cùng route, permission và DTO.

