# Tài khoản, hồ sơ, Seller và quản trị

Baseline: SAD v1.1 §7.1/D.1, được người dùng chấp nhận ngày 11/11/2026.
[Quy ước/thuật ngữ](README.md) · [Coverage và câu hỏi](coverage.md) · [Kỹ thuật/validation](../engineering.md).

Các điều khoản tài khoản đơn vai trò và vòng đời cấm dưới đây theo nguồn hiệu lực 16/11 và [quyết định Thẻ 15](coverage.md#the-15), thay các policy đa vai trò/cấm theo scope/snapshot lịch sử tương ứng của baseline; không thay thế toàn bộ SAD hoặc chứng minh implementation.

Các giá trị reset mật khẩu, sổ địa chỉ, địa giới và chống lạm dụng đăng nhập theo [tham số đã chốt](parameters.md) từ Thẻ 16; không thay đổi baseline session, RBAC hay vòng đời cấm.

Phiên mục tiêu theo [quyết định 0003](../decisions/0003-mot-phien-refresh-tren-user.md): mỗi User chỉ có một phiên hiện hành trên User, thay AuthSession; giữ cookie/Origin của quyết định 0001. Đây là thay đổi tài liệu, chưa phải thay đổi implementation.

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
| E03 ban/unban | [identity-service/src/main/java/com/example/identity_service/admin/ModerationService.java](../../identity-service/src/main/java/com/example/identity_service/admin/ModerationService.java) | [identity-service/src/test/java/com/example/identity_service/admin/ModerationEndpointsTests.java](../../identity-service/src/test/java/com/example/identity_service/admin/ModerationEndpointsTests.java) | Hiện trạng khảo sát dùng snapshot has-paid-order; khác đặc tả [Thẻ 15](coverage.md#the-15). Order lỗi có test fake, chưa kiểm chứng implementation theo contract nghĩa vụ/J7 mới. |
| Schema | [identity-service/src/main/resources/db/migration](../../identity-service/src/main/resources/db/migration) | [identity-service/src/test/java/com/example/identity_service/IdentityServiceApplicationTests.java](../../identity-service/src/test/java/com/example/identity_service/IdentityServiceApplicationTests.java) | V1–V5; có assertion versions/tables, chưa chạy; không sửa migration đã áp dụng. |

Khi lập task, lần theo caller/consumer và artifacts trong cùng hàng, rồi xem dependency liên service dưới đây; không đổi các source gốc để che lệch implementation.

## Contract và dữ liệu cần tra cứu

- [Database §3.1, §5.1/5.7/5.8, §6, §8.2D](database.md): field, trạng thái suy ra, timestamp, RBAC và ownership. Bảng nguồn là thiết kế, không khẳng định migration tồn tại.
- [API §6.1, §7.1–7.3](api_specs.md): DTO, provider/consumer allowlist, sự kiện và J7; [FRD UC-A03/E03/G03](FRD.md) và [Thẻ 15](coverage.md#the-15) là policy hiệu lực cho các clause tài khoản được cập nhật.
- Public paths bên dưới là phần sau `/api/v1`; internal dùng `/internal/v1`. Base URL mẫu trong nguồn không chứng minh cổng service hiện tại. Tên path là khóa contract ổn định; quyền ở bảng vẫn phải kết hợp trạng thái/ownership.
- **Bảng contract nguồn, chưa phải bằng chứng triển khai.** Endpoint Should vẫn giữ tham chiếu; luồng checkout cũ và stats/dashboard bị baseline hiệu chỉnh không được triển khai nguyên xi. DTO, body/error examples và required/optional giữ tại đúng mục nguồn, không suy ra từ tên endpoint.

[API §6.1 / danh sách endpoint](api_specs.md)

| \# | Method | Path | Mô tả | Quyền | UC |
| :---- | :---- | :---- | :---- | :---- | :---- |
| 1 | POST | /identity/auth/register | Đăng ký Customer bằng email | PUBLIC | A01 |
| 2 | POST | /identity/auth/login | Đăng nhập đúng accountType CUSTOMER/SELLER/ADMIN, tài khoản độc lập | PUBLIC | A03 |
| 3 | POST | /identity/auth/google | Đăng ký / đăng nhập Customer bằng Google (Should) | PUBLIC | A01, A03 |
| 4 | POST | /identity/auth/register-seller | Đăng ký bán hàng | PUBLIC | A02 |
| 5 | POST | /identity/auth/refresh | Làm mới access token | PUBLIC | A03 |
| 6 | POST | /identity/auth/logout | Kết thúc phiên được nhận diện bằng cookie; không xóa phiên mới từ cookie cũ | PUBLIC (cookie + Origin, không cần Bearer) | A07 |
| 7 | POST | /identity/auth/password/forgot | Yêu cầu đặt lại mật khẩu | PUBLIC | A05 |
| 8 | POST | /identity/auth/password/reset | Đặt mật khẩu mới bằng liên kết | PUBLIC | A05 |
| 9 | PUT | /identity/users/me/password | Đổi mật khẩu | account:update:own (LOCAL) | A04 |
| 10 | GET | /identity/users/me | Hồ sơ tài khoản, accountType, accountStatus/mốc và accessMode hiện hành | account:read:own | A06 |
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
| 28 | POST | /identity/admin/ban/{userId} | Cấm tài khoản theo nghĩa vụ, body {reason}, không scope | account:moderate | E03 |
| 29 | POST | /identity/admin/unban/{userId} | Gỡ cấm tài khoản theo thời hạn, body {reason?}, không scope | account:moderate | E03 |
| 30 | POST | /identity/auth/seller-reapplication/challenge | Yêu cầu email xác minh đăng ký lại Seller | PUBLIC (email verification) | A02 |
| 31 | POST | /identity/auth/seller-reapplication | Nộp SellerApplication mới sau REJECTED | PUBLIC (verificationToken) | A02 |
| 32 | GET | /identity/admin/sellers/{sellerId}/applications | Lịch sử các lần đăng ký Seller | operations:read:any | E06 |

## Quy tắc bổ sung và source hiệu lực

| Mã nguồn | Quy tắc đã xác nhận | Nguồn |
|---|---|---|
| <a id="g-01"></a>G-01 | Email chuẩn hóa duy nhất trong User; mỗi tài khoản có một accountType và tối đa một role hiện hành, Customer/Seller/Admin dùng tài khoản độc lập. Không tự liên kết GOOGLE/LOCAL trùng email. Seller REJECTED đăng ký application mới qua xác minh email, giữ lịch sử application cũ; không nộp lại hồ sơ cũ. Tên cửa hàng (users.full_name) chỉ kiểm tra trùng với Seller PENDING/APPROVED. | [Database §3.1/§8.1](database.md); [FRD UC-A01/A02](FRD.md) |
| <a id="g-02"></a>G-02 | Không đổi User.email; User có Seller PENDING/APPROVED không đổi full_name (tên cửa hàng). Tên chung và mật khẩu chỉ cập nhật tại User qua API tự phục vụ. | [§8.1](../../business/database.md), L954 |
| <a id="g-05"></a>G-05 | Quên mật khẩu: luôn trả cùng một thông báo dù email có tồn tại hay không. | [§8.1](../../business/database.md), L957 |
| <a id="g-07"></a>G-07 | Mật khẩu: tối thiểu 8 ký tự, có chữ hoa, chữ thường và chữ số. | [§8.1](../../business/database.md), L959 |
| <a id="g-12"></a>G-12 | Mọi hành động của Admin ghi AdminAuditLog (mục 6). | [§8.1](../../business/database.md), L964 |
| <a id="g-14"></a>G-14 | Một vòng đời cấm cho tài khoản đã chọn, không scope, không ảnh hưởng tài khoản khác cùng người; không cấm ADMIN hoặc Seller chưa APPROVED. Còn nghĩa vụ hiện tại → BANNED với allowlist đúng chủ; mọi nguồn xác minh hết → DISABLED, thu hồi phiên. J7/login/refresh đánh giá nghĩa vụ đến muộn, giữ mốc đợt cấm, không tự unban. Nguồn lỗi không coi là zero; request cần xác minh trả 503. Không giỏ/thu mới/review/bán mới; không tự hủy Order hoặc đóng băng available. Unban BANNED bất kỳ lúc nào, DISABLED khi now ≤ restoreUntil; quá hạn không xóa dữ liệu/tiền. Chi tiết [UC-E03](#uc-e03). | [Database §8.1 G-14/§8.2D](database.md); [API §3.2](api_specs.md); [Thẻ 15](coverage.md#the-15) |

Quy tắc chung G-09/G-11 ở [engineering](../engineering.md); G-10/G-13/G-15 ở [quy ước chung](README.md).

| Mã | Điều khoản | Nguồn / phân loại |
|---|---|---|
| <a id="req-idn-001"></a>REQ-IDN-001 | User là nguồn danh tính với accountType CUSTOMER/SELLER/ADMIN cố định. Customer/Seller dùng shared PK=FK tới user_id nhưng không đồng thời tồn tại; ADMIN không có hai hồ sơ đó, tối đa một role hiệu lực. Không bổ sung/chuyển vai trò trên tài khoản có sẵn; mỗi tài khoản có credential/session riêng, không hợp quyền giữa các tài khoản. Seller PENDING/REJECTED không login. | Nguồn hiệu lực: [Database §3.1/§5.7](database.md); [API §3.2.1](api_specs.md); [FRD UC-A01/A02](FRD.md) |
| <a id="req-idn-002"></a>REQ-IDN-002 | Tên shop = users.full_name, liên hệ = users.phone_number. Chặn trùng tên với User có Seller PENDING/APPROVED trong transaction có bảo vệ đồng thời; Customer trùng họ tên vẫn hợp lệ. REJECTED không giữ tên shop; phone vẫn unique ở User. | Yêu cầu baseline; [§3.1.1–3.1.3](../../business/database.md), L96–153 |
| <a id="req-idn-003"></a>REQ-IDN-003 | Địa chỉ thuộc Customer; tối đa 3 địa chỉ hiện có (P-09). Province/ward hai cấp, ward phải thuộc province; không có quận/huyện. Địa chỉ mặc định suy ra từ last_used_at mới nhất; chưa dùng địa chỉ nào thì created_at nhỏ nhất. Hard-delete giải phóng một chỗ trong giới hạn, không đổi snapshot Order; không tạo cờ is_default mới. | [Tham số P-09/P-26](parameters.md); baseline [§3.1.4](../../business/database.md), L154–173 |
| <a id="req-idn-004"></a>REQ-IDN-004 | Nạp sẵn danh mục địa giới tĩnh có nguồn và phiên bản vào DB Identity. RegionCatalog/API và validation địa chỉ cùng đọc bộ dữ liệu hai cấp tỉnh/thành → phường/xã/đặc khu trực thuộc, không gọi API địa giới ngoài. Ghi rõ nguồn, phiên bản và ngày nhập khi chuẩn bị dữ liệu; chưa tự chọn dataset cụ thể. Order giữ mã và tên địa giới tại thời điểm đặt hàng. | [Tham số P-26](parameters.md); [§3.1, §6.7, DELTA-09](software-architecture.md) |
| <a id="req-idn-005"></a>REQ-IDN-005 | Ban/login/refresh/J7 đánh giá nghĩa vụ hiện tại từ Order và (Seller) Finance theo [dependencies](#nguon-nghia-vu-va-j7), không snapshot lịch sử đã trả tiền. Ban đọc lại sau preview, lỗi nguồn trả 503 không ghi ban một phần; unban hợp lệ không cần Order/Finance. HTTP ngoài transaction giữ khóa; trước commit kiểm tra version/đợt cấm để không ghi đè unban. Cặp trạng thái, authzVersion, audit và Outbox cùng transaction Identity, không transaction xuyên service. | [FRD UC-E03/G03](FRD.md); [API §7.1/7.1.2/7.3](api_specs.md); [Database §3.1.1/§8.2D](database.md); [Thẻ 15](coverage.md#the-15) |
| <a id="req-idn-006"></a>REQ-IDN-006 | Access token chỉ ở memory; refresh qua HttpOnly Secure Cookie, thuộc tính SameSite/Path/Origin/CORS/CSRF được review theo môi trường; không trả refresh token JSON theo mẫu API cũ. Logout thu hồi phiên rồi clear đúng cookie. Local phải có profile tường minh, không tắt Secure toàn cục. | Yêu cầu baseline; [§6.3, AT-25](software-architecture.md), L616–622 |
| <a id="req-idn-007"></a>REQ-IDN-007 | RS256/JWKS, iss/aud/exp, session và authzVersion hiện hành; permission+trạng thái+ownership đều bắt buộc. Thiếu quyền 403, tài nguyên người khác 404; token stale 401 TOKEN_STALE; không lấy được context cần thiết 503. Không cache quyết định quyền; cache public key là vấn đề khác. | Yêu cầu baseline; [§6.1](software-architecture.md), L596–600 |
| <a id="req-idn-008"></a>REQ-IDN-008 | Admin bootstrap/nội bộ có kiểm soát; không public CRUD role/permission hay tự gán ADMIN hoặc bổ sung vai trò khác vào tài khoản. RolePermission đổi bằng migration phải tăng authzVersion User liên quan; auth session bị thu hồi không dùng lại được. | [API §3.2.1](api_specs.md) |
| <a id="req-idn-009"></a>REQ-IDN-009 | User là nguồn phiên duy nhất: `refresh_token_hash` TEXT NULL UNIQUE, `refresh_token_expires_at` TIMESTAMPTZ NULL, `session_id` UUID NULL UNIQUE; cả ba cùng có giá trị hoặc cùng NULL. Không lưu token nguyên văn, không lộ trong DTO/log/event. Login thành công, kể cả Google Should, thay cả ba trong một transaction và tạo sessionId mới; access/refresh phiên trước hết hiệu lực tại lần kiểm tra quyền tiếp theo dù JWT chưa hết hạn. Login sai hoặc lỗi xác minh nghĩa vụ không thay phiên. Thay phiên không tự tăng authzVersion. | [Quyết định 0003](../decisions/0003-mot-phien-refresh-tren-user.md) |
| <a id="req-idn-010"></a>REQ-IDN-010 | Refresh chỉ dùng cookie/Origin; xác minh quyền/nghĩa vụ, xoay hash và lưu dấu cũ cùng transaction, giữ sessionId, gia hạn từ thời điểm thành công theo TTL cấu hình (P-06 chưa chốt). `rotated_refresh_tokens` giữ token_hash, user_id, session_id, rotated_at, không FK AuthSession; giữ toàn bộ dấu chuỗi khi phiên còn hiệu lực. Reuse đúng phiên hiện hành phải commit xóa ba trường User rồi trả 401 INVALID_REFRESH_TOKEN; token lạ/hết hạn/phiên cũ trả 401 nhưng không xóa phiên mới. Không grace period. | [Quyết định 0003](../decisions/0003-mot-phien-refresh-tren-user.md) |
| <a id="req-idn-011"></a>REQ-IDN-011 | Login/refresh/logout/thu hồi khóa hoặc cập nhật có điều kiện cùng User, kiểm tra lại hash/sessionId và version/trạng thái trước ghi. HTTP nghĩa vụ ngoài transaction giữ khóa. Hai login: lần ghi thành công sau cùng là phiên hiện hành. Hai refresh cùng token: chỉ một lần xoay; lần còn lại là reuse thu hồi chuỗi. Logout/refresh cũ không được ghi đè login mới. Đổi/reset mật khẩu hoặc chuyển DISABLED xóa ba trường cùng transaction cập nhật User; reset không tạo phiên mới. | [Quyết định 0003](../decisions/0003-mot-phien-refresh-tren-user.md) |

## Acceptance criteria để triển khai/kiểm chứng

Các AC sau là diễn giải kiểm chứng từ điều khoản cùng mã UC/REQ/G/WD; chưa là kết quả test. Tham số TBD chỉ kiểm tra biên sau khi giá trị được chốt. Mỗi dòng gộp một tình huống; triển khai test phải tách assertions và cả nhánh lỗi của UC.

| Mã AC | Liên kết yêu cầu | Given | When | Then |
|---|---|---|---|---|
| <a id="ac-idn-001"></a>AC-IDN-001 | UC-A01; G-01/07 | Email/phone hợp lệ chưa dùng; LOCAL | Đăng ký rồi login | Tạo User+Customer+CUSTOMER; không lộ password hash; email/phone trùng không tạo User thứ hai. |
| <a id="ac-idn-002"></a>AC-IDN-002 | UC-A02; G-01/02 | Tài khoản Customer/Admin hoặc Seller REJECTED | Đăng ký Seller; thử tên trùng PENDING/APPROVED | Customer/Admin phải dùng tài khoản Seller độc lập; REJECTED xác minh email rồi tạo application mới, giữ hồ sơ cũ; PENDING/APPROVED không nộp thêm; tên/phone thuộc User. |
| <a id="ac-idn-003"></a>AC-IDN-003 | UC-A03; G-14; B1/B2/B3 | Tài khoản đúng/sai kênh; Customer chỉ CONFIRMED không còn nghĩa vụ, có PAID/DELIVERED hoặc CANCELLED kèm Refund FAILED | Ban, login/refresh và gọi API; đăng nhập sai nhiều lần | Sai kênh không hợp quyền; chỉ CONFIRMED → DISABLED/ACCOUNT_DISABLED; PAID/DELIVERED → BANNED chỉ đơn đúng chủ, hủy/xác nhận theo state; CANCELLED/Refund FAILED → OBLIGATIONS_READ_ONLY đến khi hết nghĩa vụ, vẫn được thử lại hoàn tiền đúng điều kiện P-RF-01. Không giỏ/thu mới/review/toàn bộ lịch sử; AWAITING_PAYMENT không thanh toán, J1/J2/J2b vẫn chạy; Order người khác 404. Seller PENDING/REJECTED không login. Sai mật khẩu không gây khóa tạm tài khoản; rate-limit/chống lạm dụng vẫn áp dụng (P-18), không thay lệnh cấm Admin. |
| <a id="ac-idn-004"></a>AC-IDN-004 | UC-A04/A05/A07; G-05/07; REQ-IDN-011 | LOCAL có phiên hiện hành; liên kết reset vừa tạo, đã dùng hoặc hết 30 phút | Đổi/reset mật khẩu, logout, thử access/refresh cũ | Đổi/reset thu hồi phiên cùng transaction; reset không cấp phiên mới. Logout cookie-only không cần access còn hạn; phiên cũ không xóa phiên mới. Forgot email không tồn tại/Google vẫn cùng 202; link dùng một lần trong 30 phút (P-05). |
| <a id="ac-idn-005"></a>AC-IDN-005 | UC-A06; REQ-IDN-003/004; G-13 | Có 2 hoặc 3 địa chỉ, gồm địa chỉ đã được Order snapshot | Thêm địa chỉ thứ 3/thứ 4, sửa/xóa rồi thêm lại; chọn ward sai province | Cho lưu tối đa 3 địa chỉ, từ chối địa chỉ thứ 4; xóa hẳn một địa chỉ cho phép thêm lại. Snapshot mã/tên địa giới của Order không đổi; ward phải thuộc province trong cùng dataset DB Identity, không gọi nguồn ngoài; owner khác 404. |
| <a id="ac-idn-006"></a>AC-IDN-006 | UC-B02; G-06; P-37 | Cửa hàng tên “Điện Thoại”; Seller khác trạng thái User/role/approval | Tìm lần lượt `dien thoai`, `Điện Thoại`, `ĐIỆN THOẠI`; xem list/detail công khai | Ba từ khóa cho cùng kết quả, tên hiển thị giữ nguyên dấu/chữ hoa; chỉ trả Seller đủ điều kiện; không lộ dữ liệu xét duyệt/credential; kết quả rỗng hợp lệ. |
| <a id="ac-idn-007"></a>AC-IDN-007 | UC-E01 | Admin và User thường | Tra cứu Customer/Seller, thử khóa Admin | Admin chỉ xem dữ liệu được phép; không tự sửa hồ sơ, không màn lịch sử ban; User thường bị từ chối. |
| <a id="ac-idn-008"></a>AC-IDN-008 | UC-E03; REQ-IDN-005; B9/B11 | Admin preview; Order/Finance timeout, ví đáng lẽ tồn tại trả 404; Seller PENDING/REJECTED hoặc target ADMIN | Preview/ban; J7 đồng thời Admin unban; gửi scope cũ | Không zero giả/không cho xác nhận khi preview lỗi; ban nguồn lỗi 503 không ghi một phần; job giữ trạng thái/retry, không ghi đè ACTIVE sau unban. Preview có thể khác kết quả POST thực tế; scope → 400; không cấm ADMIN, Seller chưa APPROVED → 409 INVALID_STATE_TRANSITION. |
| <a id="ac-idn-009"></a>AC-IDN-009 | UC-E06; REQ-IDN-001 | Seller PENDING và Admin độc lập đủ quyền | Approve/reject đồng thời; thử ban trước duyệt | Một quyết định; approval/role/authzVersion/audit/Outbox cùng transaction Identity; Finance tạo ví idempotent. Trước duyệt không login/không ban; REJECTED chỉ đăng ký application mới qua xác minh email. |
| <a id="ac-idn-010"></a>AC-IDN-010 | UC-E03; G-14; B6/B7/B8 | BANNED chưa từng DISABLED quá 30 ngày; hoặc lần đầu hết nghĩa vụ | J7 hai lần; unban tại restoreUntil và sau đó 1 giây | BANNED vẫn được unban, chưa bắt đầu cửa sổ. Hết nghĩa vụ → DISABLED một lần, lập disabledAt/restoreUntil UTC +30 ngày, không reset/phát duplicate. Đúng hạn → 200 ACTIVE, xóa mốc/lý do hiện tại giữ audit; sau hạn → 409 ACCOUNT_RESTORE_WINDOW_EXPIRED, không xóa dữ liệu/tiền/đổi đơn. ACTIVE unban idempotent; không phục hồi đơn hủy/Product HIDDEN/REMOVED/quyền chưa duyệt. |
| <a id="ac-idn-011"></a>AC-IDN-011 | UC-E03; REQ-IDN-005; B4/B10 | Seller DISABLED xuất hiện available muộn; hoặc chỉ pending/Settlement/hiệu ứng chưa apply kể cả bằng 0 | Login/refresh/J7 đánh giá lại, rồi xử lý hết nghĩa vụ | Nghĩa vụ muộn → BANNED giới hạn không unban, chỉ available → WITHDRAWAL_ONLY; chỉ pending/hiệu ứng → OBLIGATIONS_READ_ONLY, không rút pending. Giữ mốc cùng đợt cấm khi quay DISABLED, không gia hạn; nguồn lỗi không mở rộng quyền. user.enabled mang BANNED không phát *.unbanned; tiền/ledger được giữ. |
| <a id="ac-idn-012"></a>AC-IDN-012 | REQ-IDN-009; UC-A03 | User có phiên A, các tab chia sẻ cookie A | Login B thành công; thử access/refresh A; thử login sai/lỗi nguồn | B là phiên duy nhất; A không refresh và access A bị từ chối ở lần kiểm tra quyền tiếp theo. Login thất bại không thay B; các tab chung cookie là một phiên, không phải nhiều phiên độc lập. |
| <a id="ac-idn-013"></a>AC-IDN-013 | REQ-IDN-010/011 | Token đã xoay trong phiên hiện hành và token thuộc phiên bị thay | Reuse; hai refresh cùng token; hai login; refresh/logout cũ đua login mới | Reuse phiên hiện hành commit thu hồi rồi 401; hai refresh chỉ một lần xoay, lần còn lại thu hồi chuỗi. Token cũ/lạ/hết hạn không xóa phiên mới; hai login giữ lần ghi sau cùng. Không ghi đè phiên mới bằng request cũ. |
| <a id="ac-idn-014"></a>AC-IDN-014 | UC-A07; REQ-IDN-006/011 | Cookie hiện hành/đã xoay/cũ/thiếu/lạ và Origin hợp lệ/sai | Logout không Bearer | Đúng phiên hiện hành thì thu hồi; cookie cũ không xóa phiên mới. Các trường hợp hợp lệ Origin đều 204 và clear cookie đúng thuộc tính, kể cả không tìm thấy phiên. Sai Origin 403, không thay dữ liệu; không nhận userId/sessionId client để chọn tài khoản. |

### AT nguồn thuộc hồ sơ này

Các AT bao phủ nhiều service; hồ sơ này là đầu mối đọc, không có nghĩa chỉ sửa một module. Đọc owner khác từ coverage khi task chạm biên. **Chưa có bằng chứng chạy AT.**

| Mã | Tình huống | Bằng chứng cần có | Nguồn |
|---|---|---|---|
| <a id="at-18"></a>AT-18 | Ban/unban theo nghĩa vụ, J7, token stale, logout/reset, IDOR | Policy đúng G-14/AC-IDN-003/008/010/011; 401/403/404/409/503 theo contract, scope cũ 400; không lộ đơn/ví người khác | [Thẻ 15](coverage.md#the-15); [API §3.2/§6.1/§7](api_specs.md); AT baseline [B.2](software-architecture.md) |
| <a id="at-25"></a>AT-25 | Cookie/CORS/CSRF và service allowlist | Sai origin/audience/caller bị chặn; internal không public; không lộ refresh token JSON | [B.2](software-architecture.md), L1006 |

## Chênh lệch, đề xuất và câu hỏi còn mở

- **GAP-IDN-001 — đã xử lý 20/11/2026 (bước I2, V7):** tên cửa hàng/SĐT liên hệ chuyển sang `users.full_name/phone_number`, thêm `seller_applications`; mô tả cũ: `sellers.seller_name/contact_phone`, request/profile và test hiện tại còn dữ liệu riêng; baseline dùng User. Phạm vi task sau gồm migration mới, User/Seller/repository, auth/account/public/admin/internal views, OpenAPI và test; không đổi riêng một DTO.
- **GAP-IDN-002 — đã xử lý 20/11/2026 (bước I3, V9):** luôn hard-delete, tối đa 3 địa chỉ, `touch` địa chỉ đã xóa là no-op; mô tả cũ: AddressService soft-delete địa chỉ đã dùng; `AddressEndpointsTests.unusedAddressIsDeletedAndAUsedOneIsOnlyHidden` bảo vệ hành vi này. Baseline G-13 hard-delete; cần rà touch/reference và snapshot Order trước sửa.
- **GAP-IDN-003 — đã xử lý 20/11/2026 (bước I3, V8):** snapshot provinces.open-api.vn v2 (34 tỉnh, 3321 xã/phường, `provinces-open-api-vn-v2@2026-11-20`) trong DB Identity; mô tả cũ: RegionProvider hiện gọi API ngoài; P-26 yêu cầu dataset tĩnh trong DB Identity. Nguồn và phiên bản cụ thể còn phải ghi nhận khi chuẩn bị dữ liệu; không gọi provider dev là quyết định nghiệp vụ.
- **GAP-IDN-004 — đã xử lý 20/11/2026 (bước I5, quyết định 0001):** refresh token chỉ trong cookie HttpOnly `refresh_token`, refresh/logout kiểm tra Origin; kiểm tra end-to-end qua gateway 21/11/2026; mô tả cũ: AuthTokens/Session trả refresh token trong JSON; baseline §6.3 cookie/memory. Cần task riêng về HTTP/session/security/test/FE; không chỉ đổi tài liệu OpenAPI sinh ra.
- **GAP-IDN-005 — chưa triển khai, quyết định 0003 (23/11/2026):** [SessionService](../../identity-service/src/main/java/com/example/identity_service/session/SessionService.java) vẫn dùng AuthSessionRepository để cấp/xoay/thu hồi phiên; [AuthSession](../../identity-service/src/main/java/com/example/identity_service/session/AuthSession.java) là model phiên hiện tại. Thiết kế mới chuyển nguồn phiên sang ba trường User theo [0003](../decisions/0003-mot-phien-refresh-tren-user.md), chuyển lịch sử token và mọi validator/caller (gồm authorization-context) sang User. Cần migration mới, thu hồi toàn bộ phiên cũ để đăng nhập lại, rồi bỏ AuthSession sau cutover; không giữ song song hai nguồn phiên, không sửa V1–V11. Code, migration, OpenAPI sinh từ code và database chưa thay đổi; AC mới chưa chạy.
- **Chưa xác minh:** approve có test audit/outbox nhưng chưa có consumer Finance tạo ví. Admin/read views chưa chứng minh có số đơn/ví từ service thật. Google Should cần đối chiếu contract cụ thể trước task. Đặc tả tài khoản đơn vai trò/ban theo nghĩa vụ và J7 đã đổi theo [Thẻ 15](coverage.md#the-15); không coi endpoint bổ sung hoặc AC là runtime proof.
- Cấu hình TTL 15m/7d, media 2MB và rate-limit là hiện trạng (địa chỉ đã là 3 theo P-09), không phải toàn bộ giá trị nghiệp vụ. Reset 30 phút đã được chốt; sổ địa chỉ phải giới hạn 3; P-18 không khóa tạm vì sai đăng nhập nhưng vẫn giữ rate-limit. TTL/session và giới hạn media chưa được chốt riêng giữ câu hỏi tại coverage.

## Đặc tả UC và ngoại lệ


<a id="uc-a01"></a>
## UC-A01 — Đăng ký tài khoản khách hàng

Nguồn hiệu lực: [FRD UC-A01](FRD.md), [API §3.2.1/§6.1](api_specs.md). Vai trò: Khách hàng · Ưu tiên: M (Google: S).

Các điều khoản dưới đây là yêu cầu nguồn đã đối chiếu baseline; nhánh Google/in-app ghi Should không thuộc DoD Must. Mã clause giữ ổn định khi di chuyển tài liệu.

| Mã điều khoản | Loại | Yêu cầu / điều kiện / ngoại lệ | Nguồn hiệu lực |
|---|---|---|---|
| <a id="uc-a01.goal-01"></a>UC-A01.GOAL-01 | Mục tiêu | Tạo tài khoản Customer riêng để mua sắm/quản lý đơn, không dùng tài khoản Seller/Admin. | [FRD UC-A01](FRD.md) |
| <a id="uc-a01.pre-01"></a>UC-A01.PRE-01 | Điều kiện | Khách chưa có tài khoản với email hoặc số điện thoại đăng ký. | [UC-A01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L309 |
| <a id="uc-a01.main-01"></a>UC-A01.MAIN-01 | Luồng chính | Khách chọn “Đăng ký”, nhập email, họ tên, số điện thoại và mật khẩu; xác nhận lại mật khẩu. | [UC-A01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L313 |
| <a id="uc-a01.main-02"></a>UC-A01.MAIN-02 | Luồng chính | Hệ thống kiểm tra thông tin bắt buộc, định dạng, độ mạnh mật khẩu và việc trùng thông tin. | [UC-A01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L315 |
| <a id="uc-a01.main-03"></a>UC-A01.MAIN-03 | Luồng chính | Nếu hợp lệ, hệ thống tạo tài khoản khách hàng và thông báo đăng ký thành công (popup 1s). | [UC-A01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L317 |
| <a id="uc-a01.main-04"></a>UC-A01.MAIN-04 | Luồng chính | Khách có thể đăng nhập ngay để sử dụng các chức năng mua sắm. | [UC-A01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L319 |
| <a id="uc-a01.alt-01"></a>UC-A01.ALT-01 | Ngoại lệ | Đăng ký bằng Google (Should): khách cho phép sử dụng tài khoản Google; hệ thống lấy email và họ tên, tạo tài khoản. Khách phải bổ sung số điện thoại trong hồ sơ trước khi đặt hàng. | [UC-A01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L323 |
| <a id="uc-a01.alt-02"></a>UC-A01.ALT-02 | Ngoại lệ | Email hoặc số điện thoại đã được sử dụng: hệ thống chỉ rõ thông tin trùng và không tạo tài khoản mới. | [UC-A01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L325 |
| <a id="uc-a01.alt-03"></a>UC-A01.ALT-03 | Ngoại lệ | Người có tài khoản Seller/Admin muốn mua hàng phải đăng ký Customer độc lập bằng email khác; không bổ sung hồ sơ Customer vào tài khoản đó. | [FRD UC-A01](FRD.md); [API §3.2.1](api_specs.md) |
| <a id="uc-a01.alt-04"></a>UC-A01.ALT-04 | Ngoại lệ | Khách chọn phương thức Google cho email đã đăng ký bằng mật khẩu hoặc ngược lại: hệ thống hướng dẫn đăng nhập bằng phương thức đã dùng; không tự gộp tài khoản. | [UC-A01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L329 |
| <a id="uc-a01.alt-05"></a>UC-A01.ALT-05 | Ngoại lệ | Google không xác minh được email hoặc khách không đồng ý: hệ thống dừng đăng ký và quay lại màn hình ban đầu. | [UC-A01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L331 |
| <a id="uc-a01.alt-06"></a>UC-A01.ALT-06 | Ngoại lệ | Khách gửi yêu cầu nhiều lần do mất kết nối: hệ thống không tạo tài khoản trùng. | [UC-A01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L333 |
| <a id="uc-a01.post-01"></a>UC-A01.POST-01 | Hậu điều kiện | Mỗi email gắn một tài khoản, mỗi tài khoản một accountType Customer/Seller/Admin; nhiều vai trò dùng tài khoản độc lập, không hợp quyền. Mật khẩu bảo vệ theo G-07. | [FRD UC-A01](FRD.md); [Database §3.1/§8.1](database.md) |

<a id="uc-a02"></a>
## UC-A02 — Đăng ký trở thành người bán

Nguồn hiệu lực: [FRD UC-A02](FRD.md), [API §6.1](api_specs.md), [Database §3.1.3](database.md). Vai trò: Seller · Ưu tiên: M.

Các điều khoản dưới đây là yêu cầu nguồn đã đối chiếu baseline; nhánh Google/in-app ghi Should không thuộc DoD Must. Mã clause giữ ổn định khi di chuyển tài liệu.

| Mã điều khoản | Loại | Yêu cầu / điều kiện / ngoại lệ | Nguồn hiệu lực |
|---|---|---|---|
| <a id="uc-a02.goal-01"></a>UC-A02.GOAL-01 | Mục tiêu | Tạo tài khoản Seller độc lập và hồ sơ kinh doanh; không chuyển/bổ sung vai trò trên tài khoản Customer/Admin. | [FRD UC-A02](FRD.md) |
| <a id="uc-a02.pre-01"></a>UC-A02.PRE-01 | Điều kiện | Đăng ký Seller riêng hoặc application mới sau REJECTED qua xác minh email; tên shop không trùng PENDING/APPROVED, credential không dùng chung tài khoản khác. | [FRD UC-A02](FRD.md); [API §6.1](api_specs.md) |
| <a id="uc-a02.main-01"></a>UC-A02.MAIN-01 | Luồng chính | Đăng ký tài khoản Seller riêng với email, tên shop, điện thoại và mật khẩu. Tên/phone lưu ở users.full_name/phone_number. | [FRD UC-A02](FRD.md) |
| <a id="uc-a02.main-02"></a>UC-A02.MAIN-02 | Luồng chính | Người có Customer/Admin vẫn đăng ký Seller độc lập; không dùng tài khoản có sẵn để thêm vai trò Seller. | [FRD UC-A02](FRD.md); [API §3.2.1](api_specs.md) |
| <a id="uc-a02.main-03"></a>UC-A02.MAIN-03 | Luồng chính | Hệ thống kiểm tra thông tin và cho người dùng xác nhận gửi hồ sơ. | [UC-A02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L351 |
| <a id="uc-a02.main-04"></a>UC-A02.MAIN-04 | Luồng chính | Hệ thống tiếp nhận hồ sơ ở trạng thái “Chờ duyệt”, gửi email xác nhận đã nhận hồ sơ và hiển thị thông báo. | [UC-A02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L353 |
| <a id="uc-a02.main-05"></a>UC-A02.MAIN-05 | Luồng chính | Quản trị viên xem xét hồ sơ (UC-E06); hệ thống gửi kết quả duyệt hoặc từ chối qua email. | [UC-A02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L355 |
| <a id="uc-a02.alt-01"></a>UC-A02.ALT-01 | Ngoại lệ | Email thuộc Customer/Admin hoặc Seller PENDING/APPROVED: không tạo trùng, dùng email khác khi đăng ký mới. Seller REJECTED xác minh sở hữu email rồi nộp SellerApplication mới qua public reapplication, không login/nộp lại hồ sơ cũ. | [FRD UC-A02](FRD.md); [API §6.1](api_specs.md) |
| <a id="uc-a02.alt-02"></a>UC-A02.ALT-02 | Ngoại lệ | Tên cửa hàng trùng hồ sơ đang chờ duyệt hoặc đã duyệt, hoặc số điện thoại đã thuộc tài khoản khác: hệ thống chỉ rõ thông tin trùng và không nhận hồ sơ. | [UC-A02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L361 |
| <a id="uc-a02.alt-03"></a>UC-A02.ALT-03 | Ngoại lệ | PENDING không login/thao tác bán; kết quả xét duyệt gửi email, không có vai trò Customer dùng chung. | [FRD UC-A02](FRD.md) |
| <a id="uc-a02.alt-04"></a>UC-A02.ALT-04 | Ngoại lệ | REJECTED không login; email nêu lý do. Đăng ký lại qua public xác minh email, tạo application_id mới và giữ lịch sử REJECTED; không sửa/nộp lại hồ sơ cũ. | [FRD UC-A02](FRD.md); [Database §3.1.3/§8.2C](database.md) |
| <a id="uc-a02.alt-05"></a>UC-A02.ALT-05 | Ngoại lệ | PENDING/APPROVED không nộp thêm; REJECTED chỉ thay bằng lần đăng ký mới theo ALT-04. | [FRD UC-A02](FRD.md) |
| <a id="uc-a02.post-01"></a>UC-A02.POST-01 | Hậu điều kiện | Seller độc lập, chỉ APPROVED/ACTIVE mới bán/hiển thị công khai; PENDING/REJECTED không login. BANNED chỉ quyền nghĩa vụ UC-E03, không bán hoặc dùng tài khoản Customer Google để đăng ký Seller. | [FRD UC-A02/E03](FRD.md); [API §3.2](api_specs.md) |

<a id="uc-a03"></a>
## UC-A03 — Đăng nhập

Nguồn hiệu lực: [FRD UC-A03](FRD.md), [API §3.2/§6.1](api_specs.md), [Thẻ 15](coverage.md#the-15). Vai trò: Customer/Seller/Admin độc lập · Ưu tiên: M (Google: S).

Các điều khoản dưới đây là yêu cầu nguồn đã đối chiếu baseline; nhánh Google/in-app ghi Should không thuộc DoD Must. Mã clause giữ ổn định khi di chuyển tài liệu.

| Mã điều khoản | Loại | Yêu cầu / điều kiện / ngoại lệ | Nguồn hiệu lực |
|---|---|---|---|
| <a id="uc-a03.goal-01"></a>UC-A03.GOAL-01 | Mục tiêu | Xác thực đúng kênh Customer/Seller/Admin và chỉ cấp quyền của tài khoản đó. | [FRD UC-A03](FRD.md) |
| <a id="uc-a03.pre-01"></a>UC-A03.PRE-01 | Điều kiện | Tài khoản đúng kênh, Seller APPROVED; login/refresh đánh giá nghĩa vụ UC-E03, kể cả nghĩa vụ muộn của DISABLED. | [FRD UC-A03](FRD.md); [API §3.2](api_specs.md) |
| <a id="uc-a03.main-01"></a>UC-A03.MAIN-01 | Luồng chính | Chọn kênh, gửi email/mật khẩu/accountType tương ứng; Google chỉ cho Customer dùng Google. | [FRD UC-A03](FRD.md); [API §6.1](api_specs.md) |
| <a id="uc-a03.main-02"></a>UC-A03.MAIN-02 | Luồng chính | Xác minh credential, accountType, approval và quyền/nghĩa vụ hiện hành; đúng mật khẩu không cấp quyền kênh khác. | [FRD UC-A03](FRD.md); [API §3.2](api_specs.md) |
| <a id="uc-a03.main-03"></a>UC-A03.MAIN-03 | Luồng chính | Login thành công tạo sessionId mới và thay hash/hạn refresh trên User trong cùng transaction; chỉ một phiên/tài khoản. Phiên trước mất cả refresh và access ở lần kiểm tra quyền tiếp theo. Không hợp quyền Customer/Seller/Admin; các tab chung cookie cùng một phiên. | [FRD UC-A03](FRD.md); [Quyết định 0003](../decisions/0003-mot-phien-refresh-tren-user.md) |
| <a id="uc-a03.main-04"></a>UC-A03.MAIN-04 | Luồng chính | BANNED nhận phiên giới hạn theo nghĩa vụ/accessMode UC-E03; Customer không giỏ/thu mới/review/toàn bộ lịch sử terminal; AWAITING_PAYMENT không thanh toán. | [FRD UC-A03/E03](FRD.md); [API §3.2](api_specs.md) |
| <a id="uc-a03.alt-01"></a>UC-A03.ALT-01 | Ngoại lệ | Sai thông tin: thông báo chung, không tiết lộ tài khoản có tồn tại. Không khóa tạm tài khoản vì đăng nhập sai nhiều lần; vẫn áp dụng giới hạn tần suất và biện pháp chống lạm dụng. Cấm tài khoản bởi Admin là luồng riêng UC-E03. | [FRD UC-A03](FRD.md); [Tham số P-18](parameters.md) |
| <a id="uc-a03.alt-02"></a>UC-A03.ALT-02 | Ngoại lệ | PENDING/REJECTED không login. Seller APPROVED/BANNED chỉ xử lý nghĩa vụ đơn/tiền của mình, không sản phẩm/tồn kho/đơn mới/thống kê; available không đóng băng vì ban. | [FRD UC-A03/E03](FRD.md) |
| <a id="uc-a03.alt-03"></a>UC-A03.ALT-03 | Ngoại lệ | Sai loại tài khoản của kênh dù đúng credential: thông báo chung “Thông tin đăng nhập không chính xác”, không gợi ý đăng ký Seller/tiết lộ vai trò. | [FRD UC-A03](FRD.md); [API §3.2](api_specs.md) |
| <a id="uc-a03.alt-04"></a>UC-A03.ALT-04 | Ngoại lệ | DISABLED chỉ từ chối ACCOUNT_DISABLED sau kiểm tra nghĩa vụ muộn; có nghĩa vụ → BANNED giới hạn không tự unban; lỗi nguồn cần xác minh →503. Thu hồi phiên theo trạng thái hiện hành. | [FRD UC-A03/E03](FRD.md); [API §3.2](api_specs.md) |
| <a id="uc-a03.alt-05"></a>UC-A03.ALT-05 | Ngoại lệ | Không còn đơn thao tác nhưng Payment/Refund dở vẫn có OBLIGATIONS_READ_ONLY đúng chủ, kể cả CANCELLED/Refund FAILED hoặc FULL_CHECKOUT phiên đóng. Customer được mở chu kỳ thử lại hoàn tiền khi đủ điều kiện P-RF-01, không thu mới; Admin không làm thay. Lịch sử từng trả tiền không giữ phiên. | [FRD UC-A03/E03](FRD.md); [API §3.2](api_specs.md); [P-RF-01](parameters.md) |
| <a id="uc-a03.alt-06"></a>UC-A03.ALT-06 | Ngoại lệ | Đăng nhập bằng Google khác phương thức đăng ký: hướng dẫn dùng phương thức đúng; không tự liên kết tài khoản. | [UC-A03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L401 |
| <a id="uc-a03.post-01"></a>UC-A03.POST-01 | Hậu điều kiện | Auth/self/authorization-context có accountType/accountStatus, accessMode, bannedAt/disabledAt/restoreUntil nullable và authzVersion phù hợp. Login/refresh cùng policy; mỗi request tính allowlist/ownership/state hiện hành. Ngoài allowlist →ACCOUNT_BANNED; public browsing không cấp quyền mua sắm authenticated. | [API §3.2/§6.1/§7.1](api_specs.md); [FRD UC-A03/E03](FRD.md) |

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
| <a id="uc-a04.main-03"></a>UC-A04.MAIN-03 | Luồng chính | Đổi mật khẩu và xóa ba trường phiên hiện hành trên User cùng transaction; access/refresh cũ không dùng lại, người dùng phải đăng nhập lại. | [Quyết định 0003](../decisions/0003-mot-phien-refresh-tren-user.md) |
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
| <a id="uc-a05.main-03"></a>UC-A05.MAIN-03 | Luồng chính | Nếu tài khoản đủ điều kiện (email tồn tại và tài khoản dùng mật khẩu, không phải Google), người dùng nhận liên kết đặt lại mật khẩu qua email. Liên kết có hiệu lực 30 phút kể từ khi tạo và chỉ dùng một lần. | [FRD UC-A05](FRD.md); [Tham số P-05](parameters.md) |
| <a id="uc-a05.main-04"></a>UC-A05.MAIN-04 | Luồng chính | Người dùng mở liên kết còn hiệu lực, đặt mật khẩu mới (đáp ứng các điều kiện) và nhận thông báo thành công. | [UC-A05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L443 |
| <a id="uc-a05.main-05"></a>UC-A05.MAIN-05 | Luồng chính | Reset mật khẩu và thu hồi phiên hiện hành cùng transaction; không tạo phiên mới, người dùng đăng nhập lại. | [Quyết định 0003](../decisions/0003-mot-phien-refresh-tren-user.md) |
| <a id="uc-a05.alt-01"></a>UC-A05.ALT-01 | Ngoại lệ | Email không tồn tại/GOOGLE không gửi liên kết, vẫn thông báo chung. Seller PENDING/REJECTED dùng LOCAL vẫn reset mật khẩu, nhưng reset không thay approval hoặc cấp login/quyền bán. | [FRD UC-A05](FRD.md); [API §3.2/§6.1](api_specs.md) |
| <a id="uc-a05.alt-02"></a>UC-A05.ALT-02 | Ngoại lệ | Liên kết đã sử dụng hoặc đã hết 30 phút kể từ lúc tạo: từ chối reset và yêu cầu gửi yêu cầu mới. | [FRD UC-A05](FRD.md); [Tham số P-05](parameters.md) |
| <a id="uc-a05.post-01"></a>UC-A05.POST-01 | Hậu điều kiện | Liên kết chỉ dùng một lần trong hạn 30 phút và không làm lộ thông tin tài khoản. | [FRD UC-A05](FRD.md); [Tham số P-05](parameters.md) |

<a id="uc-a06"></a>
## UC-A06 — Cập nhật hồ sơ

Nguồn: [§6 / UC-A06](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L455–486. Vai trò: mọi role · Ưu tiên: M.

Các điều khoản dưới đây là yêu cầu nguồn đã đối chiếu baseline; nhánh Google/in-app ghi Should không thuộc DoD Must. Mã clause giữ ổn định khi di chuyển tài liệu.

| Mã điều khoản | Loại | Yêu cầu / điều kiện / ngoại lệ | Nguồn hiệu lực |
|---|---|---|---|
| <a id="uc-a06.goal-01"></a>UC-A06.GOAL-01 | Mục tiêu | Người dùng cập nhật thông tin cá nhân và địa chỉ nhận hàng. | [UC-A06](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L459 |
| <a id="uc-a06.pre-01"></a>UC-A06.PRE-01 | Điều kiện | Có phiên/quyền tự phục vụ hiện hành. BANNED chỉ allowlist nghĩa vụ UC-E03, không mở cập nhật hồ sơ/sổ địa chỉ vì có phiên giới hạn. | [FRD UC-A06/E03](FRD.md); [API §3.2](api_specs.md) |
| <a id="uc-a06.main-01"></a>UC-A06.MAIN-01 | Luồng chính | Người dùng mở “Hồ sơ”, xem các thông tin hiện có và chọn nội dung được phép cập nhật. | [UC-A06](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L465 |
| <a id="uc-a06.main-02"></a>UC-A06.MAIN-02 | Luồng chính | Người dùng sửa họ tên và số điện thoại của tài khoản; khách hàng thêm/sửa/xóa địa chỉ nhận hàng; người bán sửa mô tả và logo cửa hàng; quản trị viên sửa họ tên. MVP không lưu ảnh đại diện; giao diện dùng ảnh mặc định. | [UC-A06](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L467 |
| <a id="uc-a06.main-03"></a>UC-A06.MAIN-03 | Luồng chính | Hệ thống kiểm tra trường bắt buộc, trùng lặp và giới hạn thông tin; sổ địa chỉ tối đa 3 địa chỉ hiện có. Mã tỉnh/thành và phường/xã/đặc khu phải hợp lệ trong cùng dataset tĩnh của DB Identity; không có quận/huyện, không gọi API địa giới ngoài. | [FRD UC-A06](FRD.md); [Tham số P-09/P-26](parameters.md) |
| <a id="uc-a06.main-04"></a>UC-A06.MAIN-04 | Luồng chính | Hệ thống lưu thay đổi hợp lệ và hiển thị dữ liệu mới trong hồ sơ. | [UC-A06](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L471 |
| <a id="uc-a06.alt-01"></a>UC-A06.ALT-01 | Ngoại lệ | Email không được sửa. Tài khoản có hồ sơ người bán đang chờ duyệt hoặc đã duyệt không sửa được họ tên vì đó là tên cửa hàng (G-02). Số điện thoại của tài khoản cũng là số liên hệ của cửa hàng. | [UC-A06](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L475 |
| <a id="uc-a06.alt-02"></a>UC-A06.ALT-02 | Ngoại lệ | Logo không đúng loại/giới hạn (P-08), số điện thoại trùng hoặc địa chỉ thiếu thông tin: hệ thống báo lỗi và giữ dữ liệu cũ. | [UC-A06](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L477 |
| <a id="uc-a06.alt-03"></a>UC-A06.ALT-03 | Ngoại lệ | Khách xóa địa chỉ: địa chỉ bị xóa hẳn khỏi sổ địa chỉ; nếu đó là địa chỉ đang ưu tiên, hệ thống tự chọn lại theo quy tắc địa chỉ mặc định (Database RBAC v5.0 mục 3.1.4); khách vẫn được xóa địa chỉ cuối cùng nhưng sẽ phải thêm địa chỉ trước khi đặt hàng. | [UC-A06](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L479 |
| <a id="uc-a06.alt-04"></a>UC-A06.ALT-04 | Ngoại lệ | Khách chọn tỉnh/thành khác: yêu cầu chọn lại phường/xã/đặc khu trực thuộc phù hợp; không lưu địa chỉ thứ 4. Xóa hẳn địa chỉ cũ thì địa chỉ đó không còn tính vào giới hạn 3. | [FRD UC-A06](FRD.md); [Tham số P-09/P-26](parameters.md) |
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
| <a id="uc-a07.main-01"></a>UC-A07.MAIN-01 | Luồng chính | Chỉ đọc cookie refresh_token và kiểm tra Origin, không bắt buộc Bearer còn hạn. Token hiện tại hoặc dấu đã xoay chỉ thu hồi khi đúng User và sessionId hiện hành; không nhận userId/sessionId client. Trả 204 và clear cookie đúng Path/thuộc tính. | [Quyết định 0003](../decisions/0003-mot-phien-refresh-tren-user.md) |
| <a id="uc-a07.main-02"></a>UC-A07.MAIN-02 | Luồng chính | Màn hình quay về trạng thái chưa đăng nhập; các chức năng cần tài khoản yêu cầu đăng nhập lại. | [UC-A07](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L499 |
| <a id="uc-a07.alt-01"></a>UC-A07.ALT-01 | Ngoại lệ | Cookie thiếu/lạ, phiên hết hạn/đã kết thúc vẫn 204 và clear cookie. Cookie phiên cũ không xóa phiên mới. Sai Origin 403, không thay dữ liệu. | [Quyết định 0003](../decisions/0003-mot-phien-refresh-tren-user.md) |
| <a id="uc-a07.post-01"></a>UC-A07.POST-01 | Hậu điều kiện | Phiên đã thu hồi không dùng lại cả access và refresh; các tab chia sẻ cookie cùng một phiên và phải đăng nhập lại. Không có cam kết nhiều thiết bị độc lập trên cùng User; login mới đã thay phiên trước. | [FRD UC-A07](FRD.md); [Quyết định 0003](../decisions/0003-mot-phien-refresh-tren-user.md) |

<a id="uc-b02"></a>
## UC-B02 — Tìm kiếm và xem người bán

Nguồn: [§6 / UC-B02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L537–560. Vai trò: Khách hàng · Ưu tiên: M.

Các điều khoản dưới đây là yêu cầu nguồn đã đối chiếu baseline; nhánh Google/in-app ghi Should không thuộc DoD Must. Mã clause giữ ổn định khi di chuyển tài liệu.

| Mã điều khoản | Loại | Yêu cầu / điều kiện / ngoại lệ | Nguồn hiệu lực |
|---|---|---|---|
| <a id="uc-b02.goal-01"></a>UC-B02.GOAL-01 | Mục tiêu | Khách tra cứu người bán và xem những sản phẩm họ đang kinh doanh. | [UC-B02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L541 |
| <a id="uc-b02.pre-01"></a>UC-B02.PRE-01 | Điều kiện | Không yêu cầu khách phải đăng nhập. | [UC-B02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L543 |
| <a id="uc-b02.main-01"></a>UC-B02.MAIN-01 | Luồng chính | Khách tìm kiếm người bán theo tên cửa hàng, không phân biệt dấu tiếng Việt và không phân biệt hoa thường (P-37), hoặc chọn tên cửa hàng từ một sản phẩm. | [FRD UC-B02](FRD.md); [Tham số P-37](parameters.md) |
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
| <a id="uc-e01.main-02"></a>UC-E01.MAIN-02 | Luồng chính | Liệt kê Customer/Seller theo accountType độc lập, tìm tên/email/phone và lọc trạng thái; không có hai hồ sơ trên cùng tài khoản. | [FRD UC-E01](FRD.md); [API §6.1](api_specs.md) |
| <a id="uc-e01.main-03"></a>UC-E01.MAIN-03 | Luồng chính | Detail có accountType/accountStatus, lý do, bannedAt/disabledAt/restoreUntil, số đơn/ví Seller. CustomerAdminView/SellerAdminView có banImpact UC-E03; lỗi nguồn không zero giả/cho xác nhận. Audit nội bộ, không thêm màn lịch sử ban. | [FRD UC-E01/E03](FRD.md); [API §6.1](api_specs.md) |
| <a id="uc-e01.main-04"></a>UC-E01.MAIN-04 | Luồng chính | Từ hồ sơ, quản trị viên có thể mở chức năng duyệt người bán hoặc cấm/gỡ cấm tài khoản. | [UC-E01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1065 |
| <a id="uc-e01.alt-01"></a>UC-E01.ALT-01 | Ngoại lệ | Danh sách không có kết quả: hệ thống hiển thị trạng thái trống. | [UC-E01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1069 |
| <a id="uc-e01.alt-02"></a>UC-E01.ALT-02 | Ngoại lệ | Tài khoản quản trị viên không xuất hiện trong danh sách có thể thao tác khóa. | [UC-E01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1071 |
| <a id="uc-e01.post-01"></a>UC-E01.POST-01 | Hậu điều kiện | Quản trị viên chỉ xem chi tiết hồ sơ, không tạo/sửa trực tiếp hồ sơ của khách và người bán; các tác vụ quản trị có use case riêng. | [UC-E01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1073 |

<a id="uc-e03"></a>
## UC-E03 — Cấm và gỡ cấm tài khoản

Nguồn hiệu lực: [FRD UC-E03](FRD.md), [API §3.2/§6.1/§7](api_specs.md), [Database §3.1/§8.2D](database.md), [Thẻ 15](coverage.md#the-15). Vai trò: Admin · Ưu tiên: M.

Các clause sau thay policy scope/snapshot lịch sử tương ứng; mã clause giữ ổn định. Đây là đặc tả, chưa là bằng chứng triển khai.

| Mã điều khoản | Loại | Yêu cầu / điều kiện / ngoại lệ | Nguồn hiệu lực |
|---|---|---|---|
| <a id="uc-e03.goal-01"></a>UC-E03.GOAL-01 | Mục tiêu | Cấm/gỡ một tài khoản Customer/Seller, bảo toàn nghĩa vụ hiện tại và tiền. | [FRD UC-E03](FRD.md); [Thẻ 15](coverage.md#the-15) |
| <a id="uc-e03.pre-01"></a>UC-E03.PRE-01 | Điều kiện | Admin có account:moderate; không cấm ADMIN, chỉ Seller APPROVED. Không scope/khóa khẩn cấp vượt nghĩa vụ hoặc tác động tài khoản khác cùng người. | [FRD UC-E03](FRD.md); [API §6.1](api_specs.md) |
| <a id="uc-e03.main-01"></a>UC-E03.MAIN-01 | Luồng chính | Detail có banImpact={evaluatedAt,resultingAccountStatus,obligations}. Popup không scope, hiển thị accountType, nghĩa vụ đơn/Payment/Refund, pending/available Seller, trạng thái/quyền dự kiến; lý do 1..1000 ký tự, nút Hủy/Xác nhận cấm. Lỗi nguồn không zero giả/không xác nhận. BANNED chưa bắt đầu hạn; DISABLED giải thích 30 ngày từ chuyển trạng thái. | [FRD UC-E03](FRD.md); [API §6.1](api_specs.md) |
| <a id="uc-e03.main-02"></a>UC-E03.MAIN-02 | Luồng chính | POST /identity/admin/ban/{userId} body {reason} đọc lại nghĩa vụ trước commit; còn →BANNED, mọi nguồn xác minh hết →DISABLED/thu hồi phiên. Preview không khóa liên service; hiển thị kết quả POST thực tế nếu khác. Lỗi dependency →503 không ghi ban một phần; scope cũ →400. | [FRD UC-E03](FRD.md); [API §6.1/§7.1](api_specs.md) |
| <a id="uc-e03.main-03"></a>UC-E03.MAIN-03 | Luồng chính | accountStatus suy ra không cột mới: ACTIVE=(users ACTIVE,hồ sơ ACTIVE); BANNED=(users ACTIVE,hồ sơ BANNED); DISABLED=(users DISABLED,hồ sơ BANNED). Chỉ Identity chuyển cặp trạng thái cùng authzVersion/audit/Outbox trong local transaction; approval riêng. BANNED chỉ allowlist nghĩa vụ dưới đây. | [Database §3.1.1–3.1.3/§8.2D](database.md); [API §3.2](api_specs.md) |
| <a id="uc-e03.main-04"></a>UC-E03.MAIN-04 | Luồng chính | banned_at ở đầu đợt; lần đầu DISABLED ghi disabled_at, restore_until=disabled_at+30 ngày UTC. disabled_reason/ban_reason cùng lý do đợt, không hai lệnh khóa độc lập. Giữ mốc qua ban/job lặp và DISABLED→BANNED→DISABLED. | [Database §3.1.1/§8.2D](database.md); [FRD UC-E03](FRD.md) |
| <a id="uc-e03.main-05"></a>UC-E03.MAIN-05 | Luồng chính | Admin thấy trạng thái/mốc/deadline/Gỡ cấm; quá hạn DISABLED khóa nút kèm lý do. POST /identity/admin/unban/{userId} body {reason?: string ≤1000}: BANNED bất kỳ lúc nào, DISABLED khi now≤restoreUntil; ACTIVE thành công idempotent. Không cần Order/Finance, vẫn kiểm tra tài khoản/Seller. Thành công →ACTIVE, xóa mốc/lý do hiện tại giữ audit; đợt cấm mới tạo mốc mới. | [FRD UC-E03](FRD.md); [API §6.1](api_specs.md) |
| <a id="uc-e03.alt-01"></a>UC-E03.ALT-01 | Ngoại lệ | Ban Seller sau thu không tự hủy/hoàn Order; vẫn xử lý đơn tồn đúng state/J4. Invalid giao dịch do Order chứng minh khi apply Payment SUCCESS là trigger riêng §8.2B, không suy từ status hiện tại lúc event trễ. | [FRD UC-E03/B06](FRD.md); [Database §8.2B](database.md) |
| <a id="uc-e03.alt-02"></a>UC-E03.ALT-02 | Ngoại lệ | Seller chỉ pending/hiệu ứng hệ thống chỉ đọc tiến độ, không rút pending; available vẫn rút theo WD-03/09, kể cả toàn bộ dưới 10.000 khi thỏa ngoại lệ. Không đóng băng vì ban; quyết toán hợp lệ tiếp tục. | [FRD UC-D05/E03](FRD.md); [Database §8.3](database.md); [finance](finance.md#wd-03) |
| <a id="uc-e03.alt-03"></a>UC-E03.ALT-03 | Ngoại lệ | Payment/Refund/Finance chưa xong vẫn giữ nghĩa vụ kể cả Order terminal; nguồn lỗi/thiếu/không nhất quán không chứng minh hết. Request cần xác minh →503; job giữ trạng thái/retry bền vững. | [FRD UC-E03](FRD.md); [API §7.1.2](api_specs.md) |
| <a id="uc-e03.alt-04"></a>UC-E03.ALT-04 | Ngoại lệ | Ban/job lặp không reset mốc/phát duplicate/tạo đợt mới. Quá hạn DISABLED →409 ACCOUNT_RESTORE_WINDOW_EXPIRED, không xóa tài khoản/dữ liệu/tiền/đổi đơn. Unban không phục hồi đơn hủy/Product HIDDEN/REMOVED/quyền Seller chưa duyệt. | [FRD UC-E03](FRD.md); [API §6.1](api_specs.md) |
| <a id="uc-e03.alt-05"></a>UC-E03.ALT-05 | Ngoại lệ | Một accountType/tài khoản, ban/unban chỉ target không ảnh hưởng tài khoản khác cùng người. DISABLED có nghĩa vụ muộn chỉ quay BANNED giới hạn, không tự unban/gia hạn mốc. | [FRD UC-E03](FRD.md); [Database §8.2D](database.md) |
| <a id="uc-e03.alt-06"></a>UC-E03.ALT-06 | Ngoại lệ | Seller PENDING/REJECTED không được ban: 409 INVALID_STATE_TRANSITION, không đổi approval/role; ADMIN không là target. Giữ 401/403/404 theo contract. | [FRD UC-E03/E06](FRD.md); [API §6.1](api_specs.md) |
| <a id="uc-e03.post-01"></a>UC-E03.POST-01 | Hậu điều kiện | Ban/unban 200 envelope data: {userId,accountType,accountStatus,userStatus,customerStatus nullable,sellerStatus nullable,accessMode,bannedAt nullable,disabledAt nullable,restoreUntil nullable,authzVersion}; ban thêm obligations. Admin detail/auth/self/authorization-context có trạng thái/mốc phù hợp; thông báo phân biệt quyền giới hạn/unban, nêu deadline nếu có. | [API §6.1/§7.1/§7.2](api_specs.md); [FRD UC-E03](FRD.md) |

<a id="nguon-nghia-vu-va-j7"></a>
### Nguồn nghĩa vụ và J7

- `obligations={hasOpenOrders,hasPendingPayment,hasUnresolvedRefund,hasPendingFinance,pendingBalanceVnd,availableBalanceVnd}`; Customer có hasPendingFinance=false, hai số dư 0. Order AWAITING_PAYMENT/PAID/PACKING/DELIVERED chưa kết thúc; CONFIRMED/CANCELLED/PAYMENT_FAILED/EXPIRED không tự giữ phiên. Payment PENDING/SUCCESS chưa apply, thu muộn chưa hoàn xong, Refund PENDING/FAILED chưa giải quyết vẫn giữ nghĩa vụ kể cả FULL_CHECKOUT/phiên đóng. Seller thêm Settlement PENDING/PENDING_REFUND/hiệu ứng chưa apply kể cả 0, pending>0 hoặc available>0.
- Identity là consumer duy nhất của `GET /internal/v1/orders/accounts/{userId}/obligations?accountType=CUSTOMER|SELLER` → `{hasOpenOrders,openOrderIds,hasPendingPayment,hasUnresolvedRefund,evaluatedAt,orderStateVersion}`. Order tổng hợp đúng owner, xác minh Payment/Refund qua internal API Payment, không join DB. Seller thêm `GET /internal/v1/finance/sellers/{sellerId}/obligations` → `{hasPendingFinance,pendingBalanceVnd,availableBalanceVnd,evaluatedAt,financeStateVersion}`; Finance kiểm tra Settlement/hiệu ứng pending, không chỉ wallet. Đây là bổ sung đặc tả, không tuyên bố endpoint code có sẵn. 404 tài khoản/ví đáng lẽ tồn tại không là zero.
- BANNED Customer chỉ đơn/phiên/Payment/Refund gắn nghĩa vụ đúng chủ, hủy/xác nhận theo state; được thử lại Refund khi hết lượt tự động và đủ điều kiện P-RF-01, kể cả accessMode OBLIGATIONS_READ_ONLY, không thanh toán mới. Admin không thử lại thay khách. Seller chỉ đơn tồn/thông tin tiền/biên nhận liên quan, không bán/sửa sản phẩm/tồn kho/đơn mới/thống kê. AccessMode ACTIVE=FULL; Customer có đơn=OPEN_ORDERS_ONLY; Seller có đơn+available=ORDERS_AND_WITHDRAWAL, chỉ đơn=OPEN_ORDERS_ONLY, chỉ available=WITHDRAWAL_ONLY; chỉ nghĩa vụ hệ thống/pending không có thao tác đơn/rút=OBLIGATIONS_READ_ONLY; DISABLED=LOGIN_BLOCKED. Đây là nhãn truy cập, không trạng thái cấm mới; mỗi request tính allowlist/ownership/state, không mở toàn bộ lịch sử.
- **J7 — Đối soát trạng thái tài khoản bị cấm**, owner Identity, định kỳ đánh giá BANNED/DISABLED: hết nghĩa vụ đã xác minh →DISABLED; nghĩa vụ muộn →BANNED giới hạn. Không chuyển ACTIVE/tự unban/đặt chu kỳ, backoff hoặc SLA mới; login/refresh cùng policy kể cả trước từ chối DISABLED. HTTP ngoài transaction giữ khóa; kiểm tra version/đợt cấm trước commit để không ghi đè Admin unban. Snapshot các nguồn không nguyên tử; hiệu ứng đến trễ giữ bền vững/retry an toàn, không mất tiền/mở rộng quyền khi lỗi nguồn.
- Tái sử dụng `customer.banned/unbanned`, `seller.banned/unbanned`, `user.disabled/enabled` và envelope/version/dedupe; payload liên quan thêm accountStatus/accessMode/bannedAt/disabledAt/restoreUntil. user.enabled vì nghĩa vụ muộn mang BANNED, không phải unban; chỉ lệnh unban phát *.unbanned. Consumer không tự hủy Order/đóng băng available; Payment/Refund/jobs tiếp tục.

Nguồn: [FRD UC-E03/G03](FRD.md), [API §3.2/§7.1–7.3](api_specs.md), [Database §3.1.1/§8.2D](database.md), [Thẻ 15](coverage.md#the-15).

<a id="uc-e06"></a>
## UC-E06 — Duyệt Seller mới đăng ký

Nguồn hiệu lực: [FRD UC-E06](FRD.md), [API §6.1](api_specs.md), [Database §3.1.3/§8.2C](database.md). Vai trò: Admin · Ưu tiên: M.

Các điều khoản dưới đây là yêu cầu nguồn đã đối chiếu baseline; nhánh Google/in-app ghi Should không thuộc DoD Must. Mã clause giữ ổn định khi di chuyển tài liệu.

| Mã điều khoản | Loại | Yêu cầu / điều kiện / ngoại lệ | Nguồn hiệu lực |
|---|---|---|---|
| <a id="uc-e06.goal-01"></a>UC-E06.GOAL-01 | Mục tiêu | Quản trị viên xét duyệt hồ sơ người bán mới. | [UC-E06](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1177 |
| <a id="uc-e06.pre-01"></a>UC-E06.PRE-01 | Điều kiện | Có hồ sơ ở trạng thái “Chờ duyệt”. | [UC-E06](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1179 |
| <a id="uc-e06.main-01"></a>UC-E06.MAIN-01 | Luồng chính | Quản trị viên vào danh sách “Người bán chờ duyệt”. | [UC-E06](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1183 |
| <a id="uc-e06.main-02"></a>UC-E06.MAIN-02 | Luồng chính | Hệ thống hiển thị hồ sơ đăng ký: tên cửa hàng, email, số điện thoại, mô tả và ngày nộp. | [UC-E06](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1185 |
| <a id="uc-e06.main-03"></a>UC-E06.MAIN-03 | Luồng chính | Quản trị viên chọn “Duyệt” hoặc “Từ chối”; khi từ chối phải nhập lý do. | [UC-E06](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1187 |
| <a id="uc-e06.main-04"></a>UC-E06.MAIN-04 | Luồng chính | Approve kích hoạt SELLER trên tài khoản Seller độc lập, authzVersion/audit/Outbox cùng transaction Identity; Finance tạo ví số dư 0 idempotent qua seller.approved, không transaction chung. Gửi email quyết định. | [FRD UC-E06](FRD.md); [API §7.2](api_specs.md) |
| <a id="uc-e06.alt-01"></a>UC-E06.ALT-01 | Ngoại lệ | Hồ sơ đã được người khác xử lý: hệ thống thông báo đã xử lý, không nhận quyết định thứ hai. | [UC-E06](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1193 |
| <a id="uc-e06.alt-02"></a>UC-E06.ALT-02 | Ngoại lệ | REJECTED không login/bán/tạo ví; gửi lý do email. Đăng ký lại qua public xác minh email và application mới UC-A02, giữ lịch sử. | [FRD UC-E06/A02](FRD.md); [API §6.1](api_specs.md) |
| <a id="uc-e06.alt-03"></a>UC-E06.ALT-03 | Ngoại lệ | Chỉ APPROVED mới bị ban; không có nhánh approve PENDING đang BANNED. Ban/unban không đổi approval/cấp quyền chưa duyệt. | [FRD UC-E06/E03](FRD.md); [Database §8.2C/D](database.md) |
| <a id="uc-e06.alt-04"></a>UC-E06.ALT-04 | Ngoại lệ | ADMIN không có Seller profile; duyệt bằng tài khoản Admin độc lập, không tự cấp thêm vai trò Seller. | [FRD UC-E06](FRD.md); [API §3.2.1](api_specs.md) |
| <a id="uc-e06.post-01"></a>UC-E06.POST-01 | Hậu điều kiện | Chỉ Seller APPROVED/ACTIVE mới bán/hiển thị công khai; BANNED chỉ nghĩa vụ UC-E03. APPROVED có ví qua Finance idempotent; PENDING/REJECTED không login. | [FRD UC-E06](FRD.md); [API §3.2/§7.2](api_specs.md) |
