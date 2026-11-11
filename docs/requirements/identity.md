# Tài khoản, hồ sơ, Seller và quản trị

Baseline: SAD v1.1 §7.1/D.1, được người dùng chấp nhận ngày 11/11/2026.
[Quy ước/thuật ngữ](README.md) · [Coverage và câu hỏi](coverage.md) · [Kỹ thuật/validation](../engineering.md).

Đọc đúng UC dưới đây, quy tắc bổ sung và hàng AC liên quan; chỉ mở mục contract nguồn khi cần DTO/cột/endpoint chi tiết. Yêu cầu TO-BE không chứng minh code đã làm đúng.

[UC-A01](#uc-a01) · [UC-A02](#uc-a02) · [UC-A03](#uc-a03) · [UC-A04](#uc-a04) · [UC-A05](#uc-a05) · [UC-A06](#uc-a06) · [UC-A07](#uc-a07) · [UC-B02](#uc-b02) · [UC-E01](#uc-e01) · [UC-E03](#uc-e03) · [UC-E06](#uc-e06)

## Phạm vi sửa và bằng chứng hiện trạng

Khảo sát tĩnh ngày 11/11/2026; mọi test nêu dưới đây **chưa chạy**.

| Phạm vi | Code owner hiện có | Test đã tìm thấy | Giới hạn |
|---|---|---|---|
| A01–A07 | [identity-service/src/main/java/com/example/identity_service/auth](../../identity-service/src/main/java/com/example/identity_service/auth) | [identity-service/src/test/java/com/example/identity_service/auth/AuthEndpointsTests.java](../../identity-service/src/test/java/com/example/identity_service/auth/AuthEndpointsTests.java) | Có code/test; request Seller khác baseline, chưa chạy. |
| A04/A05 | [identity-service/src/main/java/com/example/identity_service/auth/PasswordService.java](../../identity-service/src/main/java/com/example/identity_service/auth/PasswordService.java) | [identity-service/src/test/java/com/example/identity_service/auth/PasswordEndpointsTests.java](../../identity-service/src/test/java/com/example/identity_service/auth/PasswordEndpointsTests.java) | Đổi/reset và thu hồi session; email end-to-end chưa xác minh. |
| A06, B02 | [identity-service/src/main/java/com/example/identity_service/account](../../identity-service/src/main/java/com/example/identity_service/account) | [identity-service/src/test/java/com/example/identity_service/account/AccountEndpointsTests.java](../../identity-service/src/test/java/com/example/identity_service/account/AccountEndpointsTests.java) | Xem thêm customer/, seller/, media/, region/ và tests tương ứng. |
| A02/A06 Seller | [identity-service/src/main/java/com/example/identity_service/account/SellerAccountService.java](../../identity-service/src/main/java/com/example/identity_service/account/SellerAccountService.java) | [identity-service/src/test/java/com/example/identity_service/account/SellerAccountEndpointsTests.java](../../identity-service/src/test/java/com/example/identity_service/account/SellerAccountEndpointsTests.java) | Cần sửa cùng User/Seller model và contract, không chỉ request field. |
| A06 Address | [identity-service/src/main/java/com/example/identity_service/customer/AddressService.java](../../identity-service/src/main/java/com/example/identity_service/customer/AddressService.java) | [identity-service/src/test/java/com/example/identity_service/customer/AddressEndpointsTests.java](../../identity-service/src/test/java/com/example/identity_service/customer/AddressEndpointsTests.java) | unusedAddressIsDeletedAndAUsedOneIsOnlyHidden hiện bảo vệ soft-delete khác baseline. |
| A06 Region | [identity-service/src/main/java/com/example/identity_service/region/RegionCatalog.java](../../identity-service/src/main/java/com/example/identity_service/region/RegionCatalog.java) | [identity-service/src/test/java/com/example/identity_service/region/RegionEndpointsTests.java](../../identity-service/src/test/java/com/example/identity_service/region/RegionEndpointsTests.java) | Test endpoint dùng fake provider; provider hiện trạng ngoài khác baseline dataset nội bộ. |
| B02 Public Seller | [identity-service/src/main/java/com/example/identity_service/seller/SellerDirectory.java](../../identity-service/src/main/java/com/example/identity_service/seller/SellerDirectory.java) | [identity-service/src/test/java/com/example/identity_service/seller/PublicSellerEndpointsTests.java](../../identity-service/src/test/java/com/example/identity_service/seller/PublicSellerEndpointsTests.java) | Eligibility/public view; không suy ra frontend search đã hoàn tất. |
| E01/E03/E06 | [identity-service/src/main/java/com/example/identity_service/admin](../../identity-service/src/main/java/com/example/identity_service/admin) | [identity-service/src/test/java/com/example/identity_service/admin/SellerReviewEndpointsTests.java](../../identity-service/src/test/java/com/example/identity_service/admin/SellerReviewEndpointsTests.java) | ModerationEndpointsTests dùng FakeOrderClient, chưa chứng minh tích hợp Order thật. |
| E03 ban/unban | [identity-service/src/main/java/com/example/identity_service/admin/ModerationService.java](../../identity-service/src/main/java/com/example/identity_service/admin/ModerationService.java) | [identity-service/src/test/java/com/example/identity_service/admin/ModerationEndpointsTests.java](../../identity-service/src/test/java/com/example/identity_service/admin/ModerationEndpointsTests.java) | Snapshot has-paid-order; Order lỗi có test fake, chưa integration thật. |
| Schema | [identity-service/src/main/resources/db/migration](../../identity-service/src/main/resources/db/migration) | [identity-service/src/test/java/com/example/identity_service/IdentityServiceApplicationTests.java](../../identity-service/src/test/java/com/example/identity_service/IdentityServiceApplicationTests.java) | V1–V5; có assertion versions/tables, chưa chạy; không sửa migration đã áp dụng. |

Khi lập task, lần theo caller/consumer và artifacts trong cùng hàng, rồi xem dependency liên service dưới đây; không đổi các source gốc để che lệch implementation.

## Contract và dữ liệu cần tra cứu

- [§3.1, §5.1/5.7/5.8, §6](../../business/database.md): field, required/conditional, unique/FK/CHECK và ownership. Bảng nguồn là thiết kế, không khẳng định migration tồn tại.
- [§7.1–7.3](../../business/api.md): provider/consumer allowlist, event payload và jobs. [§7.2](../../business/Software-Architecture.md): DELTA cần review.
- Public paths bên dưới là phần sau `/api/v1`; internal dùng `/internal/v1`. Base URL mẫu trong nguồn không chứng minh cổng service hiện tại. Tên path là khóa contract ổn định; quyền ở bảng vẫn phải kết hợp trạng thái/ownership.
- **Bảng contract nguồn, chưa phải bằng chứng triển khai.** Endpoint Should vẫn giữ tham chiếu; luồng checkout cũ và stats/dashboard bị baseline hiệu chỉnh không được triển khai nguyên xi. DTO, body/error examples và required/optional giữ tại đúng mục nguồn, không suy ra từ tên endpoint.

[§6.1 / danh sách endpoint](../../business/api.md), L508–543

| \# | Method | Path | Mô tả | Quyền | UC |
| :---- | :---- | :---- | :---- | :---- | :---- |
| 1 | POST | /identity/auth/register | Đăng ký Customer bằng email | PUBLIC | A01 |
| 2 | POST | /identity/auth/login | Đăng nhập chung User (một SPA theo ADR-02) | PUBLIC | A03 |
| 3 | POST | /identity/auth/google | Đăng ký / đăng nhập Customer bằng Google (Should) | PUBLIC | A01, A03 |
| 4 | POST | /identity/auth/register-seller | Đăng ký bán hàng | PUBLIC | A02 |
| 5 | POST | /identity/auth/refresh | Làm mới access token | PUBLIC | A03 |
| 6 | POST | /identity/auth/logout | Đăng xuất thiết bị hiện tại | ANY_AUTH (session own) | A07 |
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
| 32 | POST | /identity/users/me/customer-profile | Bổ sung hồ sơ Customer cho User hiện tại | ANY_AUTH (policy nền) | A01 |

## Quy tắc bổ sung và source hiệu lực

| Mã nguồn | Quy tắc đã xác nhận | Nguồn |
|---|---|---|
| <a id="g-01"></a>G-01 | Email chuẩn hóa duy nhất trong User; một User có nhiều hồ sơ/vai trò. Không tự liên kết GOOGLE/LOCAL trùng email. Seller REJECTED giữ User/email và nộp lại cùng seller_id; tên cửa hàng (users.full_name) chỉ bị kiểm tra trùng với Seller PENDING/APPROVED. | [§8.1](../../business/database.md), L953 |
| <a id="g-02"></a>G-02 | Không đổi User.email; User có Seller PENDING/APPROVED không đổi full_name (tên cửa hàng). Tên chung và mật khẩu chỉ cập nhật tại User qua API tự phục vụ. | [§8.1](../../business/database.md), L954 |
| <a id="g-05"></a>G-05 | Quên mật khẩu: luôn trả cùng một thông báo dù email có tồn tại hay không. | [§8.1](../../business/database.md), L957 |
| <a id="g-07"></a>G-07 | Mật khẩu: tối thiểu 8 ký tự, có chữ hoa, chữ thường và chữ số. | [§8.1](../../business/database.md), L959 |
| <a id="g-12"></a>G-12 | Mọi hành động của Admin ghi AdminAuditLog (mục 6). | [§8.1](../../business/database.md), L964 |
| <a id="g-14"></a>G-14 | Khóa có phạm vi: User DISABLED khóa toàn bộ vai trò, thu hồi phiên. Seller BANNED chỉ chặn quyền bán, đóng băng quyền rút, xử lý Order theo UC-E03; không chặn CUSTOMER hợp lệ. Customer BANNED có đơn đã trả tiền vẫn đăng nhập, xem hồ sơ/giỏ/đơn, hủy đủ điều kiện, xác nhận và review đơn cũ; cấm checkout và mọi thanh toán mới. Chưa có đơn trả tiền thì chặn API nghiệp vụ Customer, vẫn cho đăng nhập để dùng vai trò khác hoặc xem trạng thái tài khoản. Không dùng disable User để thay hạn chế Customer và không tự hủy đơn đã trả tiền. | [§8.1](../../business/database.md), L966 |

Quy tắc chung G-09/G-11 ở [engineering](../engineering.md); G-10/G-13/G-15 ở [quy ước chung](README.md).

| Mã | Điều khoản | Nguồn / phân loại |
|---|---|---|
| <a id="req-idn-001"></a>REQ-IDN-001 | User là nguồn danh tính; Customer/Seller dùng shared PK=FK tới user_id, có thể đồng thời tồn tại; ADMIN không mặc nhiên có CUSTOMER/SELLER. User ACTIVE chưa có role vẫn có policy self-service theo API; tạo Customer bổ sung không phục hồi role đã bị Admin thu hồi. | Yêu cầu baseline; [§3.1, §5.7](../../business/database.md), L90–153; [§3.2.1](../../business/api.md), L176–192 |
| <a id="req-idn-002"></a>REQ-IDN-002 | Tên shop = users.full_name, liên hệ = users.phone_number. Chặn trùng tên với User có Seller PENDING/APPROVED trong transaction có bảo vệ đồng thời; Customer trùng họ tên vẫn hợp lệ. REJECTED không giữ tên shop; phone vẫn unique ở User. | Yêu cầu baseline; [§3.1.1–3.1.3](../../business/database.md), L96–153 |
| <a id="req-idn-003"></a>REQ-IDN-003 | Địa chỉ thuộc Customer; province/ward hai cấp, ward phải thuộc province; số địa chỉ tối đa P-09 chưa chốt. Địa chỉ mặc định suy ra từ last_used_at mới nhất; chưa dùng địa chỉ nào thì created_at nhỏ nhất. Hard-delete không đổi snapshot Order; không tạo cờ is_default mới. | Yêu cầu baseline; [§3.1.4](../../business/database.md), L154–173 |
| <a id="req-idn-004"></a>REQ-IDN-004 | Baseline RegionCatalog nội bộ có version thay adapter địa giới ngoài trong API §6.1/10.4. Nguồn dataset/version/ngày nhập còn cần review; không tự chọn nhà cung cấp hoặc chấp nhận mã khi nguồn không sẵn sàng. | Yêu cầu baseline; [§3.1, §6.7, DELTA-09](../../business/Software-Architecture.md), L201–216; [§6.7](../../business/Software-Architecture.md), L687–704 |
| <a id="req-idn-005"></a>REQ-IDN-005 | Khi cấm Customer, đọc Order.has-paid-order và lưu snapshot policy banMode/hasPaidOrderBeforeBan cùng audit; Order lỗi trả 503 và không cấm một phần. Không tự thay snapshot khi có đơn phát sinh sau; không có transaction chung với Order. | Yêu cầu baseline; [§6.2](../../business/Software-Architecture.md), L602–614 |
| <a id="req-idn-006"></a>REQ-IDN-006 | Access token chỉ ở memory; refresh qua HttpOnly Secure Cookie, thuộc tính SameSite/Path/Origin/CORS/CSRF được review theo môi trường; không trả refresh token JSON theo mẫu API cũ. Logout thu hồi phiên rồi clear đúng cookie. Local phải có profile tường minh, không tắt Secure toàn cục. | Yêu cầu baseline; [§6.3, AT-25](../../business/Software-Architecture.md), L616–622 |
| <a id="req-idn-007"></a>REQ-IDN-007 | RS256/JWKS, iss/aud/exp, session và authzVersion hiện hành; permission+trạng thái+ownership đều bắt buộc. Thiếu quyền 403, tài nguyên người khác 404; token stale 401 TOKEN_STALE; không lấy được context cần thiết 503. Không cache quyết định quyền; cache public key là vấn đề khác. | Yêu cầu baseline; [§6.1](../../business/Software-Architecture.md), L596–600 |
| <a id="req-idn-008"></a>REQ-IDN-008 | Admin bootstrap/nội bộ có kiểm soát; không public CRUD role/permission hay tự gán ADMIN. RolePermission đổi bằng migration phải tăng authzVersion User liên quan; auth session bị thu hồi không dùng lại được. | Yêu cầu baseline; [§3.2.1](../../business/api.md), L176–192 |

## Acceptance criteria để triển khai/kiểm chứng

Các AC sau là diễn giải kiểm chứng từ điều khoản cùng mã UC/REQ/G/WD; chưa là kết quả test. Tham số TBD chỉ kiểm tra biên sau khi giá trị được chốt. Mỗi dòng gộp một tình huống; triển khai test phải tách assertions và cả nhánh lỗi của UC.

| Mã AC | Liên kết yêu cầu | Given | When | Then |
|---|---|---|---|---|
| <a id="ac-idn-001"></a>AC-IDN-001 | UC-A01; G-01/07 | Email/phone hợp lệ chưa dùng; LOCAL | Đăng ký rồi login | Tạo User+Customer+CUSTOMER; không lộ password hash; email/phone trùng không tạo User thứ hai. |
| <a id="ac-idn-002"></a>AC-IDN-002 | UC-A02; G-01/02 | User chưa Seller hoặc REJECTED | Nộp/nộp lại; thử tên trùng PENDING/APPROVED | Một seller_id=User.id; REJECTED tái nộp cùng ID; tên/phone thuộc User; hồ sơ PENDING/APPROVED không nộp thêm. |
| <a id="ac-idn-003"></a>AC-IDN-003 | UC-A03; G-14 | User đa vai trò, Seller PENDING/REJECTED/BANNED | Login và gọi thao tác từng vai trò | Một danh tính; giữ vai trò hợp lệ; Seller chưa đủ điều kiện không bán; USER DISABLED không dùng API bảo vệ. |
| <a id="ac-idn-004"></a>AC-IDN-004 | UC-A04/A05/A07; G-05/07 | LOCAL có nhiều session | Đổi/reset mật khẩu, logout một session, thử token cũ | Đổi/reset thu hồi mọi session; logout chỉ session hiện tại; forgot email không tồn tại/Google vẫn cùng 202; link reset dùng một lần và có hạn P-05. |
| <a id="ac-idn-005"></a>AC-IDN-005 | UC-A06; G-13 | Có địa chỉ đã được Order snapshot | Sửa/xóa địa chỉ của mình rồi xem đơn cũ | Hard-delete địa chỉ; snapshot Order không đổi; ward thuộc province; owner khác 404; không nhận quá P-09 sau khi tham số được chốt. |
| <a id="ac-idn-006"></a>AC-IDN-006 | UC-B02; G-06 | Seller khác trạng thái User/role/approval | Search/list/detail công khai | Chỉ trả Seller đủ điều kiện; không lộ dữ liệu xét duyệt/credential; kết quả rỗng hợp lệ. |
| <a id="ac-idn-007"></a>AC-IDN-007 | UC-E01 | Admin và User thường | Tra cứu Customer/Seller, thử khóa Admin | Admin chỉ xem dữ liệu được phép; không tự sửa hồ sơ, không màn lịch sử ban; User thường bị từ chối. |
| <a id="ac-idn-008"></a>AC-IDN-008 | UC-E03; G-14 | Customer từng trả tiền tại thời điểm cấm; Order nguồn lỗi | Cấm CUSTOMER; thử checkout, xem/xử lý đơn cũ; cấm lại khi nguồn lỗi | Chặn mua mới, giữ quyền đơn cũ; không thay snapshot sau cấm; nguồn lỗi 503 không ghi ban một phần; SELLER ban không disable User. |
| <a id="ac-idn-009"></a>AC-IDN-009 | UC-E06 | Seller PENDING và Admin đủ quyền | Approve/reject đồng thời; tự duyệt; approve hồ sơ BANNED | Một quyết định; state/role/authzVersion/audit/Outbox cùng transaction Identity; ví tạo qua Finance idempotent; tự duyệt bị chặn, BANNED vẫn không bán. |

### AT nguồn thuộc hồ sơ này

Các AT bao phủ nhiều service; hồ sơ này là đầu mối đọc, không có nghĩa chỉ sửa một module. Đọc owner khác từ coverage khi task chạm biên. **Chưa có bằng chứng chạy AT.**

| Mã | Tình huống | Bằng chứng cần có | Nguồn |
|---|---|---|---|
| <a id="at-18"></a>AT-18 | Ban từng scope, token stale, logout/reset, IDOR | Policy đúng G-14; 401/403/404/503 theo contract; không lộ đơn/ví người khác | [B.2](../../business/Software-Architecture.md), L999 |
| <a id="at-25"></a>AT-25 | Cookie/CORS/CSRF và service allowlist | Sai origin/audience/caller bị chặn; internal không public; không lộ refresh token JSON | [B.2](../../business/Software-Architecture.md), L1006 |

## Chênh lệch, đề xuất và câu hỏi còn mở

- **GAP-IDN-001 — khác yêu cầu:** `sellers.seller_name/contact_phone`, request/profile và test hiện tại còn dữ liệu riêng; baseline dùng User. Phạm vi task sau gồm migration mới, User/Seller/repository, auth/account/public/admin/internal views, OpenAPI và test; không đổi riêng một DTO.
- **GAP-IDN-002 — khác yêu cầu:** AddressService soft-delete địa chỉ đã dùng; `AddressEndpointsTests.unusedAddressIsDeletedAndAUsedOneIsOnlyHidden` bảo vệ hành vi này. Baseline G-13 hard-delete; cần rà touch/reference và snapshot Order trước sửa.
- **GAP-IDN-003 — khác baseline:** RegionProvider hiện gọi API ngoài; baseline RegionCatalog nội bộ, dataset cụ thể còn mở. Không gọi provider dev là quyết định nghiệp vụ.
- **GAP-IDN-004 — khác baseline:** AuthTokens/Session trả refresh token trong JSON; baseline §6.3 cookie/memory. Cần task riêng về HTTP/session/security/test/FE; không chỉ đổi tài liệu OpenAPI sinh ra.
- **Chưa xác minh:** approve có test audit/outbox nhưng chưa có consumer Finance tạo ví. Admin/read views chưa chứng minh có số đơn/ví từ service thật. Google Should và Customer bổ sung cần đối chiếu contract cụ thể trước task.
- Cấu hình TTL 15m/7d, reset 30m, địa chỉ 5, media 2MB, rate-limit chỉ là hiện trạng; xem câu hỏi tham số trong coverage.

## Đặc tả UC và ngoại lệ


<a id="uc-a01"></a>
## UC-A01 — Đăng ký tài khoản khách hàng

Nguồn: [§6 / UC-A01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L303–336. Vai trò: Khách hàng · Ưu tiên: M (đăng ký bằng Google: S).

Các điều khoản dưới đây là yêu cầu nguồn đã đối chiếu baseline; nhánh Google/in-app ghi Should không thuộc DoD Must. Mã clause giữ ổn định khi di chuyển tài liệu.

| Mã điều khoản | Loại | Yêu cầu / điều kiện / ngoại lệ | Nguồn hiệu lực |
|---|---|---|---|
| <a id="uc-a01.goal-01"></a>UC-A01.GOAL-01 | Mục tiêu | Khách hàng tạo tài khoản để mua sắm và quản lý đơn hàng. | [UC-A01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L307 |
| <a id="uc-a01.pre-01"></a>UC-A01.PRE-01 | Điều kiện | Khách chưa có tài khoản với email hoặc số điện thoại đăng ký. | [UC-A01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L309 |
| <a id="uc-a01.main-01"></a>UC-A01.MAIN-01 | Luồng chính | Khách chọn “Đăng ký”, nhập email, họ tên, số điện thoại và mật khẩu; xác nhận lại mật khẩu. | [UC-A01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L313 |
| <a id="uc-a01.main-02"></a>UC-A01.MAIN-02 | Luồng chính | Hệ thống kiểm tra thông tin bắt buộc, định dạng, độ mạnh mật khẩu và việc trùng thông tin. | [UC-A01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L315 |
| <a id="uc-a01.main-03"></a>UC-A01.MAIN-03 | Luồng chính | Nếu hợp lệ, hệ thống tạo tài khoản khách hàng và thông báo đăng ký thành công (popup 1s). | [UC-A01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L317 |
| <a id="uc-a01.main-04"></a>UC-A01.MAIN-04 | Luồng chính | Khách có thể đăng nhập ngay để sử dụng các chức năng mua sắm. | [UC-A01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L319 |
| <a id="uc-a01.alt-01"></a>UC-A01.ALT-01 | Ngoại lệ | Đăng ký bằng Google (Should): khách cho phép sử dụng tài khoản Google; hệ thống lấy email và họ tên, tạo tài khoản. Khách phải bổ sung số điện thoại trong hồ sơ trước khi đặt hàng. | [UC-A01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L323 |
| <a id="uc-a01.alt-02"></a>UC-A01.ALT-02 | Ngoại lệ | Email hoặc số điện thoại đã được sử dụng: hệ thống chỉ rõ thông tin trùng và không tạo tài khoản mới. | [UC-A01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L325 |
| <a id="uc-a01.alt-03"></a>UC-A01.ALT-03 | Ngoại lệ | Tài khoản đã có nhưng chưa có hồ sơ khách hàng (ví dụ chỉ đăng ký bán hàng): đăng nhập rồi bổ sung hồ sơ khách hàng ngay trên tài khoản đó; không đăng ký lại email. | [UC-A01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L327 |
| <a id="uc-a01.alt-04"></a>UC-A01.ALT-04 | Ngoại lệ | Khách chọn phương thức Google cho email đã đăng ký bằng mật khẩu hoặc ngược lại: hệ thống hướng dẫn đăng nhập bằng phương thức đã dùng; không tự gộp tài khoản. | [UC-A01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L329 |
| <a id="uc-a01.alt-05"></a>UC-A01.ALT-05 | Ngoại lệ | Google không xác minh được email hoặc khách không đồng ý: hệ thống dừng đăng ký và quay lại màn hình ban đầu. | [UC-A01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L331 |
| <a id="uc-a01.alt-06"></a>UC-A01.ALT-06 | Ngoại lệ | Khách gửi yêu cầu nhiều lần do mất kết nối: hệ thống không tạo tài khoản trùng. | [UC-A01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L333 |
| <a id="uc-a01.post-01"></a>UC-A01.POST-01 | Hậu điều kiện | Mỗi email chỉ gắn với một tài khoản trong toàn nền tảng; thông tin mật khẩu được bảo vệ. Xem quy tắc G-01 và G-07 (Database RBAC v5.0 mục 8.1). | [UC-A01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L335 |

<a id="uc-a02"></a>
## UC-A02 — Đăng ký trở thành người bán

Nguồn: [§6 / UC-A02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L337–370. Vai trò: Người bán · Ưu tiên: M.

Các điều khoản dưới đây là yêu cầu nguồn đã đối chiếu baseline; nhánh Google/in-app ghi Should không thuộc DoD Must. Mã clause giữ ổn định khi di chuyển tài liệu.

| Mã điều khoản | Loại | Yêu cầu / điều kiện / ngoại lệ | Nguồn hiệu lực |
|---|---|---|---|
| <a id="uc-a02.goal-01"></a>UC-A02.GOAL-01 | Mục tiêu | Người dùng gửi hồ sơ để xin quyền kinh doanh trên nền tảng, bằng tài khoản mới hoặc tài khoản đã có. | [UC-A02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L341 |
| <a id="uc-a02.pre-01"></a>UC-A02.PRE-01 | Điều kiện | Tài khoản chưa có hồ sơ người bán, hoặc hồ sơ trước đó đã bị từ chối. Tên cửa hàng không trùng hồ sơ đang chờ duyệt hoặc đã duyệt; email và số điện thoại không trùng tài khoản khác. | [UC-A02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L343 |
| <a id="uc-a02.main-01"></a>UC-A02.MAIN-01 | Luồng chính | Người chưa có tài khoản chọn “Đăng ký bán hàng”, nhập email, tên cửa hàng, số điện thoại và mật khẩu. Tên cửa hàng được lưu làm họ tên của tài khoản; số điện thoại là số của tài khoản, dùng làm số liên hệ của cửa hàng. | [UC-A02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L347 |
| <a id="uc-a02.main-02"></a>UC-A02.MAIN-02 | Luồng chính | Người đã có tài khoản (kể cả tài khoản Google) đăng nhập rồi chọn “Đăng ký bán hàng”; hệ thống điền sẵn họ tên làm tên cửa hàng (có thể sửa trước khi gửi) và yêu cầu bổ sung số điện thoại nếu tài khoản chưa có. | [UC-A02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L349 |
| <a id="uc-a02.main-03"></a>UC-A02.MAIN-03 | Luồng chính | Hệ thống kiểm tra thông tin và cho người dùng xác nhận gửi hồ sơ. | [UC-A02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L351 |
| <a id="uc-a02.main-04"></a>UC-A02.MAIN-04 | Luồng chính | Hệ thống tiếp nhận hồ sơ ở trạng thái “Chờ duyệt”, gửi email xác nhận đã nhận hồ sơ và hiển thị thông báo. | [UC-A02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L353 |
| <a id="uc-a02.main-05"></a>UC-A02.MAIN-05 | Luồng chính | Quản trị viên xem xét hồ sơ (UC-E06); hệ thống gửi kết quả duyệt hoặc từ chối qua email. | [UC-A02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L355 |
| <a id="uc-a02.alt-01"></a>UC-A02.ALT-01 | Ngoại lệ | Email đã có tài khoản: hệ thống không tạo tài khoản mới, hướng dẫn đăng nhập rồi đăng ký bán hàng từ tài khoản đó. | [UC-A02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L359 |
| <a id="uc-a02.alt-02"></a>UC-A02.ALT-02 | Ngoại lệ | Tên cửa hàng trùng hồ sơ đang chờ duyệt hoặc đã duyệt, hoặc số điện thoại đã thuộc tài khoản khác: hệ thống chỉ rõ thông tin trùng và không nhận hồ sơ. | [UC-A02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L361 |
| <a id="uc-a02.alt-03"></a>UC-A02.ALT-03 | Ngoại lệ | Đăng nhập khi hồ sơ đang chờ duyệt: được đăng nhập; kênh người bán hiển thị trạng thái “Chờ duyệt”, chưa dùng được chức năng bán. Nếu tài khoản có vai trò khách hàng thì vẫn mua sắm bình thường. | [UC-A02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L363 |
| <a id="uc-a02.alt-04"></a>UC-A02.ALT-04 | Ngoại lệ | Hồ sơ bị từ chối: email nêu lý do; người bán đăng nhập, xem lý do, sửa thông tin và nộp lại trên chính hồ sơ cũ (cùng tài khoản và email); hồ sơ quay về “Chờ duyệt”. | [UC-A02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L365 |
| <a id="uc-a02.alt-05"></a>UC-A02.ALT-05 | Ngoại lệ | Hồ sơ đang chờ duyệt hoặc đã duyệt: không nộp thêm hồ sơ. | [UC-A02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L367 |
| <a id="uc-a02.post-01"></a>UC-A02.POST-01 | Hậu điều kiện | Mỗi tài khoản có tối đa một hồ sơ người bán; chỉ hồ sơ đã được duyệt và không bị cấm mới dùng được chức năng bán và hiển thị công khai. Tài khoản Google được đăng ký bán hàng từ tài khoản đã có. Xem Database RBAC v5.0 mục 3.1.3, 5.1. | [UC-A02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L369 |

<a id="uc-a03"></a>
## UC-A03 — Đăng nhập

Nguồn: [§6 / UC-A03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L371–404. Vai trò: Khách hàng, Người bán, Quản trị viên · Ưu tiên: M (Google: S).

Các điều khoản dưới đây là yêu cầu nguồn đã đối chiếu baseline; nhánh Google/in-app ghi Should không thuộc DoD Must. Mã clause giữ ổn định khi di chuyển tài liệu.

| Mã điều khoản | Loại | Yêu cầu / điều kiện / ngoại lệ | Nguồn hiệu lực |
|---|---|---|---|
| <a id="uc-a03.goal-01"></a>UC-A03.GOAL-01 | Mục tiêu | Người dùng đăng nhập đúng quyền để truy cập hệ thống. | [UC-A03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L375 |
| <a id="uc-a03.pre-01"></a>UC-A03.PRE-01 | Điều kiện | Người dùng đã có tài khoản và nhập thông tin đăng nhập phù hợp. | [UC-A03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L377 |
| <a id="uc-a03.main-01"></a>UC-A03.MAIN-01 | Luồng chính | Người dùng nhập email/mật khẩu, hoặc chọn Google nếu tài khoản được tạo bằng Google. | [UC-A03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L381 |
| <a id="uc-a03.main-02"></a>UC-A03.MAIN-02 | Luồng chính | Hệ thống xác minh tài khoản, phương thức và tình trạng quyền truy cập. | [UC-A03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L383 |
| <a id="uc-a03.main-03"></a>UC-A03.MAIN-03 | Luồng chính | Một lần đăng nhập dùng chung mọi vai trò; một React SPA hiển thị vùng Customer/Seller/Admin theo permission và trạng thái hiện hành. Không có ba phiên hay ba tài khoản độc lập. | [UC-A03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L385; **baseline thay thế/hiệu chỉnh:** [§1.3, ADR-02](../../business/Software-Architecture.md), L83–98 |
| <a id="uc-a03.main-04"></a>UC-A03.MAIN-04 | Luồng chính | Khách hàng đang bị hạn chế thanh toán nhưng đã có đơn thanh toán thành công trước khi bị cấm vẫn xem và xử lý các đơn cũ; nút “Thanh toán” bị khóa. | [UC-A03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L387 |
| <a id="uc-a03.alt-01"></a>UC-A03.ALT-01 | Ngoại lệ | Sai thông tin: thông báo chung, không tiết lộ tài khoản có tồn tại. | [UC-A03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L391 |
| <a id="uc-a03.alt-02"></a>UC-A03.ALT-02 | Ngoại lệ | Hồ sơ người bán đang chờ duyệt, bị từ chối hoặc bị cấm: vẫn đăng nhập; kênh người bán hiển thị trạng thái hồ sơ (chờ duyệt; bị từ chối kèm lý do và nút nộp lại; bị cấm kèm lý do) và chặn thao tác bán. Seller bị cấm vẫn xem được ví ở trạng thái đóng băng. | [UC-A03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L393 |
| <a id="uc-a03.alt-03"></a>UC-A03.ALT-03 | Ngoại lệ | Tài khoản chưa có hồ sơ người bán mở kênh người bán: hướng dẫn đăng ký bán hàng (UC-A02). Tài khoản không có vai trò quản trị mở trang quản trị: thông báo không có quyền. | [UC-A03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L395 |
| <a id="uc-a03.alt-04"></a>UC-A03.ALT-04 | Ngoại lệ | Tài khoản bị vô hiệu hóa: không đăng nhập được; mọi phiên bị thu hồi. | [UC-A03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L397 |
| <a id="uc-a03.alt-05"></a>UC-A03.ALT-05 | Ngoại lệ | Khách bị cấm sau khi đã có đơn thanh toán: khóa nút “Thanh toán”, vẫn được xem và xử lý các đơn hàng cũ. Khách bị cấm khi chưa có đơn thanh toán: vẫn đăng nhập, xem trạng thái tài khoản và dùng vai trò khác; chức năng mua sắm bị chặn (G-14). | [UC-A03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L399 |
| <a id="uc-a03.alt-06"></a>UC-A03.ALT-06 | Ngoại lệ | Đăng nhập bằng Google khác phương thức đăng ký: hướng dẫn dùng phương thức đúng; không tự liên kết tài khoản. | [UC-A03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L401 |
| <a id="uc-a03.post-01"></a>UC-A03.POST-01 | Hậu điều kiện | Một tài khoản đăng nhập dùng chung các vai trò hợp lệ. Customer BANNED chỉ giữ quyền xem/xử lý đơn cũ theo G-14 nếu snapshot tại thời điểm cấm cho biết đã từng có đơn thanh toán thành công; không có lịch sử đó thì chặn phạm vi Customer nhưng giữ vai trò khác hợp lệ. Cấm Customer không tự disable User. | [UC-A03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L403; **baseline thay thế/hiệu chỉnh:** [§6.2, G-14](../../business/Software-Architecture.md), L602–614 |

<a id="uc-a04"></a>
## UC-A04 — Đổi mật khẩu

Nguồn: [§6 / UC-A04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L405–426. Vai trò: Khách hàng (local), Người bán, Quản trị viên · Ưu tiên: M.

Các điều khoản dưới đây là yêu cầu nguồn đã đối chiếu baseline; nhánh Google/in-app ghi Should không thuộc DoD Must. Mã clause giữ ổn định khi di chuyển tài liệu.

| Mã điều khoản | Loại | Yêu cầu / điều kiện / ngoại lệ | Nguồn hiệu lực |
|---|---|---|---|
| <a id="uc-a04.goal-01"></a>UC-A04.GOAL-01 | Mục tiêu | Người dùng thay đổi mật khẩu để bảo vệ tài khoản. | [UC-A04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L409 |
| <a id="uc-a04.pre-01"></a>UC-A04.PRE-01 | Điều kiện | Đang đăng nhập bằng tài khoản có mật khẩu; không áp dụng tài khoản Google. | [UC-A04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L411 |
| <a id="uc-a04.main-01"></a>UC-A04.MAIN-01 | Luồng chính | Người dùng mở “Đổi mật khẩu”, nhập mật khẩu hiện tại và mật khẩu mới. | [UC-A04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L415 |
| <a id="uc-a04.main-02"></a>UC-A04.MAIN-02 | Luồng chính | Hệ thống kiểm tra mật khẩu hiện tại, yêu cầu về độ mạnh và việc mật khẩu mới khác mật khẩu cũ. | [UC-A04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L417 |
| <a id="uc-a04.main-03"></a>UC-A04.MAIN-03 | Luồng chính | Hệ thống xác nhận đổi mật khẩu thành công và yêu cầu các thiết bị đăng nhập khác đăng nhập lại. | [UC-A04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L419 |
| <a id="uc-a04.alt-01"></a>UC-A04.ALT-01 | Ngoại lệ | Mật khẩu cũ không đúng hoặc mật khẩu mới không đạt yêu cầu: hiển thị lỗi ngay tại biểu mẫu, không thay đổi mật khẩu. | [UC-A04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L423 |
| <a id="uc-a04.post-01"></a>UC-A04.POST-01 | Hậu điều kiện | Chỉ đổi khi xác thực đúng mật khẩu cũ; mật khẩu mới tuân thủ G-07 (Database RBAC v5.0 mục 8.1). | [UC-A04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L425 |

<a id="uc-a05"></a>
## UC-A05 — Quên mật khẩu

Nguồn: [§6 / UC-A05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L427–454. Vai trò: Khách hàng (local), Người bán, Quản trị viên · Ưu tiên: M.

Các điều khoản dưới đây là yêu cầu nguồn đã đối chiếu baseline; nhánh Google/in-app ghi Should không thuộc DoD Must. Mã clause giữ ổn định khi di chuyển tài liệu.

| Mã điều khoản | Loại | Yêu cầu / điều kiện / ngoại lệ | Nguồn hiệu lực |
|---|---|---|---|
| <a id="uc-a05.goal-01"></a>UC-A05.GOAL-01 | Mục tiêu | Người dùng đặt lại mật khẩu khi không thể đăng nhập. | [UC-A05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L431 |
| <a id="uc-a05.pre-01"></a>UC-A05.PRE-01 | Điều kiện | Người dùng truy cập màn hình “Quên mật khẩu”. | [UC-A05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L433 |
| <a id="uc-a05.main-01"></a>UC-A05.MAIN-01 | Luồng chính | Người dùng nhập địa chỉ email và chọn “Gửi hướng dẫn”. | [UC-A05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L437 |
| <a id="uc-a05.main-02"></a>UC-A05.MAIN-02 | Luồng chính | Hệ thống luôn hiển thị cùng một thông báo, dù email có tồn tại hay là tài khoản Google (G-05). | [UC-A05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L439 |
| <a id="uc-a05.main-03"></a>UC-A05.MAIN-03 | Luồng chính | Nếu tài khoản đủ điều kiện (email tồn tại và tài khoản dùng mật khẩu, không phải Google), người dùng nhận liên kết đặt lại mật khẩu qua email. | [UC-A05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L441 |
| <a id="uc-a05.main-04"></a>UC-A05.MAIN-04 | Luồng chính | Người dùng mở liên kết còn hiệu lực, đặt mật khẩu mới (đáp ứng các điều kiện) và nhận thông báo thành công. | [UC-A05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L443 |
| <a id="uc-a05.main-05"></a>UC-A05.MAIN-05 | Luồng chính | Hệ thống yêu cầu các phiên đăng nhập trước đó đăng nhập lại. | [UC-A05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L445 |
| <a id="uc-a05.alt-01"></a>UC-A05.ALT-01 | Ngoại lệ | Email không tồn tại hoặc thuộc tài khoản Google: không gửi liên kết nhưng vẫn hiển thị thông báo chung. Tài khoản có hồ sơ người bán chờ duyệt hoặc bị từ chối vẫn đặt lại mật khẩu bình thường vì mật khẩu thuộc tài khoản chung. | [UC-A05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L449 |
| <a id="uc-a05.alt-02"></a>UC-A05.ALT-02 | Ngoại lệ | Liên kết đã sử dụng hoặc hết hạn (P-05): yêu cầu người dùng gửi lại yêu cầu mới. | [UC-A05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L451 |
| <a id="uc-a05.post-01"></a>UC-A05.POST-01 | Hậu điều kiện | Liên kết chỉ dùng một lần và không làm lộ thông tin tài khoản. | [UC-A05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L453 |

<a id="uc-a06"></a>
## UC-A06 — Cập nhật hồ sơ

Nguồn: [§6 / UC-A06](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L455–486. Vai trò: mọi role · Ưu tiên: M.

Các điều khoản dưới đây là yêu cầu nguồn đã đối chiếu baseline; nhánh Google/in-app ghi Should không thuộc DoD Must. Mã clause giữ ổn định khi di chuyển tài liệu.

| Mã điều khoản | Loại | Yêu cầu / điều kiện / ngoại lệ | Nguồn hiệu lực |
|---|---|---|---|
| <a id="uc-a06.goal-01"></a>UC-A06.GOAL-01 | Mục tiêu | Người dùng cập nhật thông tin cá nhân và địa chỉ nhận hàng. | [UC-A06](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L459 |
| <a id="uc-a06.pre-01"></a>UC-A06.PRE-01 | Điều kiện | Đã đăng nhập vào tài khoản của mình. | [UC-A06](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L461 |
| <a id="uc-a06.main-01"></a>UC-A06.MAIN-01 | Luồng chính | Người dùng mở “Hồ sơ”, xem các thông tin hiện có và chọn nội dung được phép cập nhật. | [UC-A06](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L465 |
| <a id="uc-a06.main-02"></a>UC-A06.MAIN-02 | Luồng chính | Người dùng sửa họ tên và số điện thoại của tài khoản; khách hàng thêm/sửa/xóa địa chỉ nhận hàng; người bán sửa mô tả và logo cửa hàng; quản trị viên sửa họ tên. MVP không lưu ảnh đại diện; giao diện dùng ảnh mặc định. | [UC-A06](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L467 |
| <a id="uc-a06.main-03"></a>UC-A06.MAIN-03 | Luồng chính | Hệ thống kiểm tra các trường bắt buộc, sự trùng lặp và giới hạn thông tin. | [UC-A06](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L469 |
| <a id="uc-a06.main-04"></a>UC-A06.MAIN-04 | Luồng chính | Hệ thống lưu thay đổi hợp lệ và hiển thị dữ liệu mới trong hồ sơ. | [UC-A06](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L471 |
| <a id="uc-a06.alt-01"></a>UC-A06.ALT-01 | Ngoại lệ | Email không được sửa. Tài khoản có hồ sơ người bán đang chờ duyệt hoặc đã duyệt không sửa được họ tên vì đó là tên cửa hàng (G-02). Số điện thoại của tài khoản cũng là số liên hệ của cửa hàng. | [UC-A06](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L475 |
| <a id="uc-a06.alt-02"></a>UC-A06.ALT-02 | Ngoại lệ | Logo không đúng loại/giới hạn (P-08), số điện thoại trùng hoặc địa chỉ thiếu thông tin: hệ thống báo lỗi và giữ dữ liệu cũ. | [UC-A06](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L477 |
| <a id="uc-a06.alt-03"></a>UC-A06.ALT-03 | Ngoại lệ | Khách xóa địa chỉ: địa chỉ bị xóa hẳn khỏi sổ địa chỉ; nếu đó là địa chỉ đang ưu tiên, hệ thống tự chọn lại theo quy tắc địa chỉ mặc định (Database RBAC v5.0 mục 3.1.4); khách vẫn được xóa địa chỉ cuối cùng nhưng sẽ phải thêm địa chỉ trước khi đặt hàng. | [UC-A06](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L479 |
| <a id="uc-a06.alt-04"></a>UC-A06.ALT-04 | Ngoại lệ | Khách chọn tỉnh/thành khác: hệ thống yêu cầu chọn lại phường/xã phù hợp; không cho lưu quá P-09 địa chỉ. | [UC-A06](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L481 |
| <a id="uc-a06.alt-05"></a>UC-A06.ALT-05 | Ngoại lệ | Thay đổi địa chỉ sau khi đặt hàng không làm thay đổi thông tin nhận hàng của những đơn đã lập. | [UC-A06](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L483 |
| <a id="uc-a06.post-01"></a>UC-A06.POST-01 | Hậu điều kiện | Thông tin được cập nhật đúng phạm vi quyền; khách đăng ký bằng Google phải bổ sung số điện thoại trước khi đặt hàng. | [UC-A06](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L485 |

<a id="uc-a07"></a>
## UC-A07 — Đăng xuất

Nguồn: [§6 / UC-A07](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L487–506. Vai trò: mọi role · Ưu tiên: M.

Các điều khoản dưới đây là yêu cầu nguồn đã đối chiếu baseline; nhánh Google/in-app ghi Should không thuộc DoD Must. Mã clause giữ ổn định khi di chuyển tài liệu.

| Mã điều khoản | Loại | Yêu cầu / điều kiện / ngoại lệ | Nguồn hiệu lực |
|---|---|---|---|
| <a id="uc-a07.goal-01"></a>UC-A07.GOAL-01 | Mục tiêu | Người dùng kết thúc phiên đăng nhập trên thiết bị đang dùng. | [UC-A07](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L491 |
| <a id="uc-a07.pre-01"></a>UC-A07.PRE-01 | Điều kiện | Người dùng chọn “Đăng xuất”. | [UC-A07](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L493 |
| <a id="uc-a07.main-01"></a>UC-A07.MAIN-01 | Luồng chính | Hệ thống kết thúc phiên đăng nhập tại thiết bị hiện tại. | [UC-A07](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L497 |
| <a id="uc-a07.main-02"></a>UC-A07.MAIN-02 | Luồng chính | Màn hình quay về trạng thái chưa đăng nhập; các chức năng cần tài khoản yêu cầu đăng nhập lại. | [UC-A07](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L499 |
| <a id="uc-a07.alt-01"></a>UC-A07.ALT-01 | Ngoại lệ | Phiên hiện tại đã hết hạn: hệ thống vẫn xử lý thao tác đăng xuất như thành công. | [UC-A07](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L503 |
| <a id="uc-a07.post-01"></a>UC-A07.POST-01 | Hậu điều kiện | Việc đăng xuất một thiết bị không ảnh hưởng các thiết bị khác; phiên vừa đăng xuất không được dùng lại. | [UC-A07](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L505 |

<a id="uc-b02"></a>
## UC-B02 — Tìm kiếm và xem người bán

Nguồn: [§6 / UC-B02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L537–560. Vai trò: Khách hàng · Ưu tiên: M.

Các điều khoản dưới đây là yêu cầu nguồn đã đối chiếu baseline; nhánh Google/in-app ghi Should không thuộc DoD Must. Mã clause giữ ổn định khi di chuyển tài liệu.

| Mã điều khoản | Loại | Yêu cầu / điều kiện / ngoại lệ | Nguồn hiệu lực |
|---|---|---|---|
| <a id="uc-b02.goal-01"></a>UC-B02.GOAL-01 | Mục tiêu | Khách tra cứu người bán và xem những sản phẩm họ đang kinh doanh. | [UC-B02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L541 |
| <a id="uc-b02.pre-01"></a>UC-B02.PRE-01 | Điều kiện | Không yêu cầu khách phải đăng nhập. | [UC-B02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L543 |
| <a id="uc-b02.main-01"></a>UC-B02.MAIN-01 | Luồng chính | Khách tìm kiếm người bán theo tên cửa hàng hoặc chọn tên cửa hàng từ một sản phẩm. | [UC-B02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L547 |
| <a id="uc-b02.main-02"></a>UC-B02.MAIN-02 | Luồng chính | Hệ thống hiển thị trang người bán với tên, logo, mô tả và danh sách sản phẩm đang được bán. | [UC-B02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L549 |
| <a id="uc-b02.main-03"></a>UC-B02.MAIN-03 | Luồng chính | Khách chọn sản phẩm để tiếp tục xem hoặc mua. | [UC-B02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L551 |
| <a id="uc-b02.alt-01"></a>UC-B02.ALT-01 | Ngoại lệ | Người bán chưa có sản phẩm: trang vẫn hiển thị nhưng danh sách sản phẩm trống. | [UC-B02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L555 |
| <a id="uc-b02.alt-02"></a>UC-B02.ALT-02 | Ngoại lệ | Hồ sơ chưa duyệt, bị từ chối hoặc tài khoản bị cấm: không hiển thị trang người bán công khai. | [UC-B02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L557 |
| <a id="uc-b02.post-01"></a>UC-B02.POST-01 | Hậu điều kiện | Khách chỉ nhìn thấy sản phẩm của đúng người bán được chọn. | [UC-B02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L559 |

<a id="uc-e01"></a>
## UC-E01 — Quản lý người dùng

Nguồn: [§6 / UC-E01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1049–1074. Vai trò: Quản trị viên · Ưu tiên: M.

Các điều khoản dưới đây là yêu cầu nguồn đã đối chiếu baseline; nhánh Google/in-app ghi Should không thuộc DoD Must. Mã clause giữ ổn định khi di chuyển tài liệu.

| Mã điều khoản | Loại | Yêu cầu / điều kiện / ngoại lệ | Nguồn hiệu lực |
|---|---|---|---|
| <a id="uc-e01.goal-01"></a>UC-E01.GOAL-01 | Mục tiêu | Quản trị viên tra cứu khách hàng, người bán và trạng thái tài khoản. | [UC-E01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1053 |
| <a id="uc-e01.pre-01"></a>UC-E01.PRE-01 | Điều kiện | Quản trị viên đã đăng nhập. | [UC-E01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1055 |
| <a id="uc-e01.main-01"></a>UC-E01.MAIN-01 | Luồng chính | Quản trị viên mở mục “Người dùng”. | [UC-E01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1059 |
| <a id="uc-e01.main-02"></a>UC-E01.MAIN-02 | Luồng chính | Hệ thống liệt kê khách hàng và người bán (một tài khoản có cả hai hồ sơ xuất hiện ở cả hai danh sách), hỗ trợ tìm theo tên, email, số điện thoại và lọc trạng thái. | [UC-E01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1061 |
| <a id="uc-e01.main-03"></a>UC-E01.MAIN-03 | Luồng chính | Quản trị viên chọn tài khoản để xem hồ sơ, số đơn, tóm tắt ví (với Seller), trạng thái và lý do khóa hiện tại của từng phạm vi. Lịch sử khóa/mở khóa chỉ lưu trong nhật ký nội bộ; MVP không có màn xem nhật ký. | [UC-E01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1063 |
| <a id="uc-e01.main-04"></a>UC-E01.MAIN-04 | Luồng chính | Từ hồ sơ, quản trị viên có thể mở chức năng duyệt người bán hoặc cấm/gỡ cấm tài khoản. | [UC-E01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1065 |
| <a id="uc-e01.alt-01"></a>UC-E01.ALT-01 | Ngoại lệ | Danh sách không có kết quả: hệ thống hiển thị trạng thái trống. | [UC-E01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1069 |
| <a id="uc-e01.alt-02"></a>UC-E01.ALT-02 | Ngoại lệ | Tài khoản quản trị viên không xuất hiện trong danh sách có thể thao tác khóa. | [UC-E01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1071 |
| <a id="uc-e01.post-01"></a>UC-E01.POST-01 | Hậu điều kiện | Quản trị viên chỉ xem chi tiết hồ sơ, không tạo/sửa trực tiếp hồ sơ của khách và người bán; các tác vụ quản trị có use case riêng. | [UC-E01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1073 |

<a id="uc-e03"></a>
## UC-E03 — Cấm và gỡ cấm tài khoản

Nguồn: [§6 / UC-E03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1105–1140. Vai trò: Quản trị viên · Ưu tiên: M.

Các điều khoản dưới đây là yêu cầu nguồn đã đối chiếu baseline; nhánh Google/in-app ghi Should không thuộc DoD Must. Mã clause giữ ổn định khi di chuyển tài liệu.

| Mã điều khoản | Loại | Yêu cầu / điều kiện / ngoại lệ | Nguồn hiệu lực |
|---|---|---|---|
| <a id="uc-e03.goal-01"></a>UC-E03.GOAL-01 | Mục tiêu | Quản trị viên cấm/gỡ cấm khách hoặc Seller, bảo vệ các quyền lợi đã phát sinh. | [UC-E03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1109 |
| <a id="uc-e03.pre-01"></a>UC-E03.PRE-01 | Điều kiện | Đã đăng nhập Admin; không cấm Admin. | [UC-E03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1111 |
| <a id="uc-e03.main-01"></a>UC-E03.MAIN-01 | Luồng chính | Quản trị viên chọn tài khoản, chọn phạm vi cấm — toàn tài khoản (vô hiệu hóa), chỉ vai trò khách hàng, hoặc chỉ vai trò người bán — nhập lý do bắt buộc và xác nhận. | [UC-E03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1115 |
| <a id="uc-e03.main-02"></a>UC-E03.MAIN-02 | Luồng chính | Với phạm vi khách hàng, hệ thống kiểm tra khách đã từng có đơn thanh toán thành công trước thời điểm cấm hay chưa. | [UC-E03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1117 |
| <a id="uc-e03.main-03"></a>UC-E03.MAIN-03 | Luồng chính | Nếu khách đã thanh toán trước khi bị cấm, hệ thống CHỈ vô hiệu chức năng thanh toán mới; khách vẫn đăng nhập, xem hồ sơ/giỏ/đơn, hủy đơn hợp lệ và xác nhận đã nhận. | [UC-E03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1119 |
| <a id="uc-e03.main-04"></a>UC-E03.MAIN-04 | Luồng chính | Nếu khách chưa từng thanh toán thành công, hệ thống chặn mọi chức năng mua sắm của vai trò khách hàng; tài khoản vẫn đăng nhập để xem trạng thái và dùng vai trò khác. Với phạm vi người bán, hệ thống chặn chức năng bán (không khóa đăng nhập chung), xử lý các đơn của Seller đó và đóng băng ví theo WD-09. Với phạm vi toàn tài khoản, hệ thống chặn đăng nhập và mọi vai trò, thu hồi các phiên; các tác vụ hoàn tiền của hệ thống vẫn chạy. | [UC-E03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1121 |
| <a id="uc-e03.main-05"></a>UC-E03.MAIN-05 | Luồng chính | Hệ thống gửi thông báo, lưu nhật ký. Gỡ cấm theo đúng phạm vi đã cấm và khôi phục quyền tương ứng (kể cả quyền rút tiền), không tự khôi phục đơn đã hủy. | [UC-E03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1123 |
| <a id="uc-e03.alt-01"></a>UC-E03.ALT-01 | Ngoại lệ | Seller bị cấm trước khi xác nhận đã giao: các đơn chưa giao của Seller được hủy/hoàn riêng (Settlement chờ_hoàn, trừ số dư chờ khi hoàn thành công); đơn của cửa hàng khác cùng lần thanh toán vẫn xử lý bình thường. | [UC-E03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1127 |
| <a id="uc-e03.alt-02"></a>UC-E03.ALT-02 | Ngoại lệ | Seller đã xác nhận “Đã giao”: giữ đơn ở trạng thái chờ khách xác nhận, không tự hủy vì cấm Seller; khi đơn đã_xác_nhận, tiền vào số dư khả dụng nhưng bị đóng băng đến khi gỡ cấm. | [UC-E03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1129 |
| <a id="uc-e03.alt-03"></a>UC-E03.ALT-03 | Ngoại lệ | Đơn đang có khoản hoàn chờ kết quả: tiếp tục đối soát; không xóa nghĩa vụ. | [UC-E03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1131 |
| <a id="uc-e03.alt-04"></a>UC-E03.ALT-04 | Ngoại lệ | Cấm/gỡ cấm lặp an toàn; mọi thao tác có nhật ký, không tự hoàn tiền trùng. | [UC-E03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1133 |
| <a id="uc-e03.alt-05"></a>UC-E03.ALT-05 | Ngoại lệ | Cấm vai trò người bán không ảnh hưởng vai trò khách hàng của cùng tài khoản, và ngược lại. | [UC-E03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1135 |
| <a id="uc-e03.alt-06"></a>UC-E03.ALT-06 | Ngoại lệ | Cấm người bán áp dụng được cho hồ sơ đang chờ duyệt, đã duyệt hoặc bị từ chối; nếu hồ sơ đang bị cấm sau đó được duyệt, người bán vẫn không hoạt động cho đến khi được gỡ cấm. | [UC-E03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1137 |
| <a id="uc-e03.post-01"></a>UC-E03.POST-01 | Hậu điều kiện | Khách đã trả tiền trước khi bị cấm chỉ bị chặn thanh toán mới; việc cấm Seller không ảnh hưởng đơn của cửa hàng khác và không xóa số dư Seller đã được quyết toán. | [UC-E03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1139 |

<a id="uc-e06"></a>
## UC-E06 — Duyệt Seller mới đăng ký

Nguồn: [§6 / UC-E06](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1173–1202. Vai trò: Quản trị viên · Ưu tiên: M.

Các điều khoản dưới đây là yêu cầu nguồn đã đối chiếu baseline; nhánh Google/in-app ghi Should không thuộc DoD Must. Mã clause giữ ổn định khi di chuyển tài liệu.

| Mã điều khoản | Loại | Yêu cầu / điều kiện / ngoại lệ | Nguồn hiệu lực |
|---|---|---|---|
| <a id="uc-e06.goal-01"></a>UC-E06.GOAL-01 | Mục tiêu | Quản trị viên xét duyệt hồ sơ người bán mới. | [UC-E06](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1177 |
| <a id="uc-e06.pre-01"></a>UC-E06.PRE-01 | Điều kiện | Có hồ sơ ở trạng thái “Chờ duyệt”. | [UC-E06](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1179 |
| <a id="uc-e06.main-01"></a>UC-E06.MAIN-01 | Luồng chính | Quản trị viên vào danh sách “Người bán chờ duyệt”. | [UC-E06](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1183 |
| <a id="uc-e06.main-02"></a>UC-E06.MAIN-02 | Luồng chính | Hệ thống hiển thị hồ sơ đăng ký: tên cửa hàng, email, số điện thoại, mô tả và ngày nộp. | [UC-E06](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1185 |
| <a id="uc-e06.main-03"></a>UC-E06.MAIN-03 | Luồng chính | Quản trị viên chọn “Duyệt” hoặc “Từ chối”; khi từ chối phải nhập lý do. | [UC-E06](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1187 |
| <a id="uc-e06.main-04"></a>UC-E06.MAIN-04 | Luồng chính | Hệ thống ghi nhận quyết định và gửi email kết quả; khi duyệt, hệ thống cấp vai trò người bán cho tài khoản và tạo ví giả lập của Seller với mọi số dư bằng 0. | [UC-E06](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1189 |
| <a id="uc-e06.alt-01"></a>UC-E06.ALT-01 | Ngoại lệ | Hồ sơ đã được người khác xử lý: hệ thống thông báo đã xử lý, không nhận quyết định thứ hai. | [UC-E06](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1193 |
| <a id="uc-e06.alt-02"></a>UC-E06.ALT-02 | Ngoại lệ | Hồ sơ bị từ chối: không cấp vai trò người bán, không tạo ví; người bán vẫn đăng nhập, xem lý do và nộp lại trên chính hồ sơ cũ (UC-A02). | [UC-E06](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1195 |
| <a id="uc-e06.alt-03"></a>UC-E06.ALT-03 | Ngoại lệ | Hồ sơ đang bị cấm: vẫn duyệt được, nhưng người bán không hoạt động cho đến khi được gỡ cấm (UC-E03). | [UC-E06](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1197 |
| <a id="uc-e06.alt-04"></a>UC-E06.ALT-04 | Ngoại lệ | Quản trị viên không được duyệt hồ sơ người bán của chính tài khoản mình. | [UC-E06](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1199 |
| <a id="uc-e06.post-01"></a>UC-E06.POST-01 | Hậu điều kiện | Chỉ người bán đã được duyệt và không bị cấm mới dùng được chức năng bán và hiển thị trong tìm kiếm người bán; mọi hồ sơ được duyệt đều có ví. | [UC-E06](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1201 |
