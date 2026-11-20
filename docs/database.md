# **Thiết kế dữ liệu và phân quyền RBAC cho hệ thống Ecommerce**

**THIẾT KẾ DỮ LIỆU VÀ NGHIỆP VỤ ECOMMERCE**

MÔ HÌNH USER CHUNG VÀ KẾ THỪA CUSTOMER SELLER

**Phiên bản 5.0 — 25/10/2026. Thiết kế lại Identity theo RBAC: User là lớp cơ sở trừu tượng ở mô hình miền, lưu danh tính chung tại bảng users; Customer và Seller kế thừa bằng khóa chính đồng thời là khóa ngoại. Admin là vai trò của User. Cập nhật xác thực, API, ownership, thông báo, audit, quy tắc khóa tài khoản và migration tương ứng. Các quy tắc đơn hàng, tồn kho và dòng tiền được giữ và áp dụng cùng kiểm soát quyền tại backend.**

**Mục lục**

**2 Bản đồ entity theo service**

**3 Entity Data Dictionary chi tiết**

3.1 Identity và Seller với User và RBAC

3.2 Product

3.3 Cart

3.4 Payment

3.5 Finance

3.6 Order Inventory Fulfillment

3.7 Notification

3.8 Reporting

**4 Quan hệ ownership và bất biến**

4.1 Các bất biến phải kiểm thử

4.2 Quyền ghi dữ liệu

**5 Luồng nghiệp vụ cập nhật**

5.1 Đăng ký tài khoản và cấp quyền Seller

5.2 Tìm kiếm đơn giản trong database

5.3 Giỏ hàng, Mua ngay và checkout

5.4 Use case Seller xem ví và rút tiền (UC-D05)

5.5 Trạng thái Order và các job

5.6 Reporting tạm hoãn và bỏ chức năng báo cáo vi phạm

5.7 Hợp đồng API và thực thi phân quyền

5.8 Migration dữ liệu và kiểm thử RBAC

**6 Bảng kỹ thuật tối thiểu**

**7 Đối chiếu thay đổi và điểm cần xác nhận**

7.1 Các quyết định còn mở

**8 Quy tắc toàn cục, state machine và dòng tiền (chuyển từ FRD mục 4\)**

8.1 Quy tắc toàn cục (G-01 đến G-17)

8.2 State machine

8.3 Dòng tiền giả lập, hoa hồng và quy tắc ví (WD-01 đến WD-11)

8.4 Bộ giả lập thanh toán nội bộ và lộ trình hai giai đoạn

# **2 Bản đồ entity theo service**

| SERVICE | ENTITY | SỐ LƯỢNG |
| :---- | :---- | :---- |
| Identity và Seller | User, Customer, Seller, CustomerAddress, Role, Permission, UserRole, RolePermission | 8 |
| Product | Category, Product, Review | 3 |
| Cart | Cart, CartItem | 2 |
| Payment | Payment, Refund | 2 |
| Finance | Settlement, Wallet, WalletTransaction, Withdrawal | 4 |
| Order Inventory Fulfillment | CheckoutSession, Order, OrderItem, Inventory, Reservation, StockMovement | 6 |
| Notification | Notification | 1 |
| Reporting | OrderReportProjection, FinancialReportProjection, InventoryReportProjection | 3 |

Tổng cộng 29 entity: Identity 8, Product 3, Cart 2, Payment 2, Finance 4, Order Inventory Fulfillment 6, Notification 1 và Reporting 3\. Bốn bảng RBAC được tính trong mô hình Identity; bảng kỹ thuật mục 6 không tính vào tổng. Giữ 8 service và Reporting tạm hoãn; Search không là service riêng trong MVP.

# **3 Entity Data Dictionary chi tiết**

Tên field dùng snake\_case tiếng Anh. Mỗi entity có mục đích, danh sách trường và ràng buộc. Các nhóm trường thời gian hoặc snapshot có thể tách thành cột riêng khi triển khai migration.

## **3.1 Identity và Seller với User và RBAC**

Identity sở hữu danh tính, hồ sơ nghiệp vụ và RBAC trong PostgreSQL. User là lớp cơ sở trừu tượng ở tầng miền; bảng users vẫn lưu bản ghi thật và là nguồn danh tính duy nhất. Customer và Seller dùng chiến lược class table inheritance, không dùng PostgreSQL INHERITS. Quan hệ kế thừa dữ liệu không tự cấp quyền: API phải kiểm tra UserRole → RolePermission → Permission và điều kiện nghiệp vụ.

Một User có thể có cả Customer và Seller; các hồ sơ chồng lấp và không tự sinh lẫn nhau. MVP cấp CUSTOMER khi tạo Customer, cấp SELLER khi Seller được duyệt. ADMIN chỉ cấp qua bootstrap hoặc quy trình nội bộ được kiểm soát; không cần bảng AdminAccount. Admin không tự động có quyền mua hoặc bán. Role CUSTOMER, SELLER, ADMIN là tập quyền độc lập, không có kế thừa vai trò ngầm.

### **3.1.1 User**

Bảng users chứa các thuộc tính chung kế thừa bởi Customer và Seller. Không lặp email, mật khẩu hoặc tên người dùng trong bảng con.

| FIELD | TYPE | REQ | KEY | GIẢI THÍCH VÀ NGHIỆP VỤ |
| :---- | :---- | :---- | :---- | :---- |
| user\_id | UUID | Có | PK | Định danh đăng nhập duy nhất; JWT sub và mọi actor/recipient chung dùng ID này. |
| email | VARCHAR(254) | Có | UQ | Chuẩn hóa trim và lowercase trước lưu; UNIQUE(email), không đổi email trong MVP. |
| auth\_method | ENUM | Có | CHECK | LOCAL / GOOGLE; Google chỉ dùng onboarding Customer trong MVP. |
| password\_hash | TEXT | ĐK | CHECK | LOCAL bắt buộc; GOOGLE để NULL. Chỉ lưu hash mật khẩu. |
| google\_subject | VARCHAR(255) | ĐK | UQ | GOOGLE bắt buộc và duy nhất khi khác NULL; LOCAL để NULL. |
| full\_name | VARCHAR(150) | Có | — | Tên hiển thị chung của User. Với User có hồ sơ Seller, full\_name đồng thời là **tên cửa hàng**; không có cột seller\_name riêng. User GOOGLE nhận giá trị ban đầu từ Google. |
| phone\_number | VARCHAR(20) | Không | UQ\* | Chuẩn hóa định dạng; unique khi có. Customer phải bổ sung trước checkout; bắt buộc khi nộp hồ sơ Seller và là số liên hệ của cửa hàng (không có sellers.contact\_phone). |
| status | ENUM | Có | CHECK | ACTIVE / DISABLED; DISABLED khóa đăng nhập và mọi API bảo vệ của User. |
| disabled\_reason | TEXT | ĐK | — | Bắt buộc khi DISABLED; khác lý do hạn chế riêng Customer/Seller. |
| authz\_version | BIGINT | Có | CHECK | Mặc định 1, tăng khi quyền hoặc trạng thái truy cập đổi để vô hiệu quyền trong token cũ. |
| created\_at, updated\_at | TIMESTAMPTZ | Có | — | Dấu thời gian danh tính. |

Ràng buộc: CHECK theo auth\_method đảm bảo đúng bộ thông tin xác thực. Không tự liên kết GOOGLE với LOCAL trùng email; trả lỗi và hướng dẫn phương thức đúng. Một email tương ứng một User, không phụ thuộc số hồ sơ hay số vai trò. Tạo User nền cho Admin là hợp lệ; không cho client tạo tài khoản không có mục đích nghiệp vụ hoặc tự chọn vai trò. Không tái dùng email khi Seller bị từ chối vì User vẫn tồn tại. **Tên cửa hàng:** không dùng UNIQUE trên toàn bảng users vì nhiều Customer có thể trùng họ tên; khi nộp hồ sơ Seller hoặc đổi full\_name, Identity kiểm tra full\_name (trim, không phân biệt hoa thường) không trùng User khác có Seller PENDING/APPROVED, trong cùng transaction có khóa để tránh hai hồ sơ nộp đồng thời. User có Seller PENDING/APPROVED không đổi full\_name (G-02).

### **3.1.2 Customer**

Customer kế thừa User qua customer\_id \= User.user\_id. Bảng customers chỉ chứa trạng thái nghiệp vụ người mua; dữ liệu chung đọc bằng JOIN với users.

| FIELD | TYPE | REQ | KEY | GIẢI THÍCH VÀ NGHIỆP VỤ |
| :---- | :---- | :---- | :---- | :---- |
| customer\_id | UUID | Có | PK \+ FK | REFERENCES users(user\_id) ON DELETE RESTRICT; đồng thời là định danh dùng ở Cart, Order, Review. |
| status | ENUM | Có | CHECK | ACTIVE / BANNED; BANNED là hạn chế người mua theo G-14, không phải trạng thái đăng nhập chung. |
| ban\_reason | TEXT | ĐK | — | Bắt buộc khi BANNED. |
| created\_at, updated\_at | TIMESTAMPTZ | Có | — | Thời điểm tạo và cập nhật hồ sơ Customer. |

Tạo Customer và UserRole CUSTOMER trong cùng transaction Identity. Quyền mua hàng yêu cầu User ACTIVE, vai trò CUSTOMER và Customer ACTIVE. Customer BANNED đã có đơn thanh toán vẫn được xử lý đơn cũ theo G-14; không vô hiệu User hoặc thu hồi CUSTOMER cho trường hợp này. Thiếu hồ sơ dù có role cũng phải từ chối thao tác nghiệp vụ. Đối chiếu UC-A01–A07, UC-E03.

### **3.1.3 Seller**

Seller kế thừa User qua seller\_id \= User.user\_id. Bảng sellers chỉ giữ thông tin shop, trạng thái nghiệp vụ và hồ sơ xét duyệt; không có credential riêng. Tên cửa hàng là users.full\_name và số liên hệ là users.phone\_number; sellers không có seller\_name hay contact\_phone.

| FIELD | TYPE | REQ | KEY | GIẢI THÍCH VÀ NGHIỆP VỤ |
| :---- | :---- | :---- | :---- | :---- |
| seller\_id | UUID | Có | PK \+ FK | REFERENCES users(user\_id) ON DELETE RESTRICT; định danh shop trong Product, Order, Wallet. |
| description | TEXT | Không | — | Mô tả shop. |
| logo\_object\_key | TEXT | Không | — | Tham chiếu object storage. |
| status | ENUM | Có | CHECK | ACTIVE / BANNED; chỉ tác động hoạt động bán hàng. |
| ban\_reason | TEXT | ĐK | — | Bắt buộc khi BANNED. |
| approval\_status | ENUM | Có | CHECK | PENDING / APPROVED / REJECTED. |
| rejection\_reason | TEXT | ĐK | — | Bắt buộc khi REJECTED. |
| registered\_at, reviewed\_at | TIMESTAMPTZ | ĐK | — | Ngày nộp và ngày xử lý hồ sơ. |
| reviewed\_by\_user\_id | UUID | ĐK | FK | REFERENCES users(user\_id); Admin có seller:approve tại thời điểm duyệt, lưu audit. |
| created\_at, updated\_at | TIMESTAMPTZ | Có | — | Thời điểm tạo và chỉnh sửa hồ sơ. |

User đăng ký Seller tạo hoặc nộp lại hồ sơ PENDING, chưa có quyền SELLER. Duyệt APPROVED và cấp UserRole SELLER là một transaction Identity, phát seller.approved bằng outbox; Finance tạo ví idempotent theo seller\_id, không giả định transaction xuyên service. Từ chối không cấp SELLER và không xóa User/Customer. REJECTED được nộp lại cùng seller\_id, giữ lịch sử trong audit. Tên cửa hàng (users.full\_name) không bị giữ chỗ khi REJECTED vì kiểm tra trùng tên chỉ xét Seller PENDING/APPROVED; số điện thoại thuộc User nên vẫn unique toàn hệ thống. Nộp hồ sơ yêu cầu users.phone\_number khác NULL. Không cho sửa hồ sơ APPROVED về PENDING qua API đăng ký.

Chỉ User ACTIVE có SELLER, Seller ACTIVE và APPROVED được thao tác shop/đơn/ví. Seller BANNED giữ role để truy vết nhưng backend từ chối mọi quyền bán; gỡ cấm khôi phục theo trạng thái hiện tại. User còn CUSTOMER vẫn dùng quyền mua nếu Customer hợp lệ. ADMIN không được duyệt hồ sơ Seller của chính mình. Đối chiếu UC-A02, UC-B02, UC-E03, UC-E06.

\- Seller mới đăng ký mặc định có approval\_status \= PENDING và status \= ACTIVE.  
\- Admin có thể cấm Seller (status \= BANNED) bất kể hồ sơ đang ở trạng thái PENDING, APPROVED hay REJECTED.  
\- Khi Seller đang ở trạng thái BANNED được duyệt hồ sơ, Seller vẫn không được phép hoạt động cho đến khi được Admin gỡ cấm.

### **3.1.4 CustomerAddress**

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

**Ràng buộc:** Chỉ Customer sở hữu địa chỉ qua FK customer\_id. Tối đa P-09 địa chỉ (chưa chốt giá trị). Địa giới 2 cấp từ 1/7/2025: không có quận/huyện; ward\_code phải thuộc province\_code (danh mục P-26). Địa chỉ chỉ được thêm/sửa/xóa trong hồ sơ (UC-A06), không sửa tại trang checkout;  Xoá địa chỉ thì sẽ là hard delete. **Địa chỉ mặc định khi checkout:** địa chỉ có last\_used\_at mới nhất; nếu chưa địa chỉ nào từng được dùng thì lấy địa chỉ có created\_at nhỏ nhất. Một lần checkout dùng một địa chỉ cho tất cả N đơn; checkout thành công cập nhật last\_used\_at. Chọn địa chỉ hợp lệ trước khi tạo CheckoutSession.

**Đối chiếu:** UC-A06 và UC-B04 kế thừa.

### **3.1.5 Role**

Danh mục vai trò chuẩn hóa; MVP seed CUSTOMER, SELLER, ADMIN và không mở CRUD vai trò công khai.

| FIELD | TYPE | REQ | KEY | GIẢI THÍCH VÀ NGHIỆP VỤ |
| :---- | :---- | :---- | :---- | :---- |
| role\_id | UUID | Có | PK | Định danh vai trò. |
| code | VARCHAR(50) | Có | UQ | CUSTOMER / SELLER / ADMIN, bất biến trong MVP. |
| name, description | VARCHAR / TEXT | Có / Không | — | Tên hiển thị và ý nghĩa tập quyền. |
| created\_at | TIMESTAMPTZ | Có | — | Thời điểm seed. |

### **3.1.6 Permission**

Một quyền là một hành động có phạm vi tài nguyên. Phạm vi own phải được kiểm tra tại service sở hữu dữ liệu.

| FIELD | TYPE | REQ | KEY | GIẢI THÍCH VÀ NGHIỆP VỤ |
| :---- | :---- | :---- | :---- | :---- |
| permission\_id | UUID | Có | PK | Định danh quyền. |
| code | VARCHAR(100) | Có | UQ | Ví dụ cart:manage:own, order:fulfill:own, seller:approve. |
| description | TEXT | Có | — | Hành động và điều kiện sở hữu; không dùng tên màn hình làm quyền. |
| created\_at | TIMESTAMPTZ | Có | — | Thời điểm seed. |

### **3.1.7 UserRole**

Bảng nối User và Role, không lưu một cột role cố định trên User.

| FIELD | TYPE | REQ | KEY | GIẢI THÍCH VÀ NGHIỆP VỤ |
| :---- | :---- | :---- | :---- | :---- |
| user\_id | UUID | Có | PK \+ FK | REFERENCES users(user\_id). |
| role\_id | UUID | Có | PK \+ FK | REFERENCES roles(role\_id); PK(user\_id, role\_id). |
| granted\_by\_user\_id | UUID | Không | FK | REFERENCES users(user\_id); NULL cho bootstrap/đăng ký hệ thống. |
| granted\_at | TIMESTAMPTZ | Có | — | Ngày cấp quyền. |
| revoked\_at | TIMESTAMPTZ | Không | — | NULL là còn hiệu lực; cấp lại cập nhật cùng bản ghi, audit giữ lịch sử. |

### **3.1.8 RolePermission**

Bảng nối Role và Permission; quyền hiệu lực là hợp các quyền của vai trò chưa bị thu hồi.

| FIELD | TYPE | REQ | KEY | GIẢI THÍCH VÀ NGHIỆP VỤ |
| :---- | :---- | :---- | :---- | :---- |
| role\_id | UUID | Có | PK \+ FK | REFERENCES roles(role\_id). |
| permission\_id | UUID | Có | PK \+ FK | REFERENCES permissions(permission\_id); PK(role\_id, permission\_id). |
| created\_at | TIMESTAMPTZ | Có | — | Ngày gán quyền trong seed/migration có kiểm soát. |

### **3.1.9 Ma trận quyền và quan hệ kế thừa**

users 1 → 0..1 customers và users 1 → 0..1 sellers. Hai khóa bảng con có thể cùng bằng một user\_id. users N ↔ N roles qua user\_roles; roles N ↔ N permissions qua role\_permissions. Admin chỉ là User có ADMIN. Không coi một hồ sơ Seller là bằng chứng cho phép gọi API bán hàng.

| PERMISSION | CUSTOMER | SELLER | ADMIN | ĐIỀU KIỆN |
| :---- | :---- | :---- | :---- | :---- |
| account:read:own / account:update:own | Có | Có | Có | Tự phục vụ cho User ACTIVE, lấy sub; không sửa role/status/email. |
| customer:profile:own | Có | — | — | Hồ sơ Customer của sub. |
| cart:manage:own / checkout:create:own / payment:create:own | Có | — | — | Customer ACTIVE; giỏ/checkout thuộc sub. |
| order:read:own / order:cancel:own / order:confirm:own | Có | — | — | Customer sở hữu đơn; kiểm tra state machine và G-14. |
| review:write:own | Có | — | — | Đã nhận hàng, Order CONFIRMED; G-14. |
| seller:application:own | Có\* | Có\* | Có\* | Quyền tự phục vụ User ACTIVE; đăng ký Seller không cần SELLER. |
| seller:profile:own / product:manage:own / inventory:manage:own | — | Có | — | Seller APPROVED và ACTIVE; tài nguyên thuộc sub. |
| order:fulfill:own | — | Có | — | Đúng seller\_id, trạng thái đơn hợp lệ. |
| wallet:read:own / withdrawal:create:own | — | Có | — | Đúng ví, Seller hợp lệ; WD-01…WD-11. |
| seller:approve / account:moderate / product:moderate | — | — | Có | Không tự duyệt; audit lý do và phạm vi khóa. |
| category:manage / operations:read:any / refund:retry | — | — | Có | Tra cứu toàn hệ thống có lọc PII; không sửa số dư hay xác nhận Payment. |
| notification:read:own | Có | Có | Có | recipient\_user\_id \= sub; không đọc thông báo User khác. |

Dấu \*: account tự phục vụ và seller:application:own được cấp bởi policy nền cho mọi User ACTIVE, không đòi vai trò SELLER; các quyền còn lại được seed qua RolePermission như ma trận. Dấu — không cấp quyền qua vai trò đó. User có nhiều vai trò nhận hợp quyền nhưng vẫn chịu trạng thái/ownership tương ứng. Không mặc định ADMIN có mọi quyền hoặc bỏ qua kiểm tra nghiệp vụ. API sản phẩm công khai của Product Service chỉ trả dữ liệu đã lọc, không yêu cầu role. Job và callback dùng danh tính service riêng, không mượn quyền ADMIN.

## **3.2 Product**

Product Service (tên cũ: Catalog) sở hữu danh mục, sản phẩm, media dạng JSONB và review. Tìm kiếm sản phẩm nằm tại service này. Data store PostgreSQL và object storage cho file media.

### **3.2.1 Category**

Danh mục phẳng một cấp; có thể seed nếu chưa triển khai màn hình quản trị động.

| FIELD | TYPE | REQ | KEY | GIẢI THÍCH VÀ NGHIỆP VỤ |
| :---- | :---- | :---- | :---- | :---- |
| category\_id | UUID | Có | PK | Mã danh mục. |
| category\_name | VARCHAR(150) | Có | UQ | Tên hiển thị duy nhất. |
| status | ENUM | Có | CHECK | VISIBLE / HIDDEN. |
| created\_at, updated\_at | TIMESTAMPTZ | Có | — | Ngày tạo và thay đổi. |

**Ràng buộc:** Không xóa danh mục đang được Product tham chiếu. Quản trị danh mục động là SHOULD theo bản nguồn.

**Đối chiếu:** UC-B01, UC-D01 và UC-E07 kế thừa.

### **3.2.2 Product**

Sản phẩm thuộc một Seller, một giá hiện tại, không có SKU hoặc ProductVariant. Tồn kho chính thức thuộc Order Inventory Fulfillment.

| FIELD | TYPE | REQ | KEY | GIẢI THÍCH VÀ NGHIỆP VỤ |
| :---- | :---- | :---- | :---- | :---- |
| product\_id | UUID | Có | PK | Mã sản phẩm. |
| seller\_id | UUID | Có | Ref | Người bán sở hữu; tham chiếu Identity/Seller. |
| category\_id | UUID | Có | FK | Danh mục sản phẩm trong Product Service. |
| product\_name | VARCHAR(255) | Có | — | Tên hiển thị và tìm kiếm. |
| description | TEXT | Không | — | Mô tả sản phẩm. |
| price\_vnd | BIGINT | Có | CHECK | Giá hiện tại \> 0; một Product/một giá. |
| status | ENUM | Có | CHECK | SELLING / HIDDEN / REMOVED. |
| removal\_reason | TEXT | Không | — | Lý do Admin gỡ khi REMOVED. |
| removed\_at | TIMESTAMPTZ | Không | — | Mốc xét điều kiện khôi phục 30 ngày. |
| media | JSONB | Có | — | Mảng metadata có thứ tự: object\_key, media\_type IMAGE/VIDEO, sort\_order, is\_cover; không lưu binary hoặc URL ký tạm thời. |
| created\_at, updated\_at | TIMESTAMPTZ | Có | — | Dấu thời gian đồng bộ và audit. |

Ràng buộc: price\_vnd \> 0\. Chỉ Product thỏa policy G-06 mới được mua; policy gồm cả User và quyền SELLER hiện hành. media có ít nhất 1 phần tử (ảnh hoặc video), tối đa theo P-08; mỗi phần tử có loại, object\_key, thứ tự, MIME type, dung lượng và thời lượng nếu là video; backend kiểm tra loại file thực tế. Ảnh bìa là ảnh đầu tiên theo thứ tự; nếu chỉ có video thì dùng ảnh placeholder. MVP không chuyển mã video. Điểm đánh giá trung bình và số đánh giá là giá trị tính từ Review (hiển thị theo P-12), không lưu cột riêng. Seller chỉ sửa sản phẩm của mình. Product Service cung cấp search theo product\_name bằng LIKE hoặc ILIKE, kèm lọc danh mục và phân trang; xem quy tắc tại mục 5.2.

**Đối chiếu:** UC-B01, UC-D01 và UC-E04 kế thừa; tìm kiếm DB theo note.

### **3.2.3 Review**

Customer đã nhận hàng có thể đánh giá sản phẩm; giữ SHOULD theo bản nguồn.

| FIELD | TYPE | REQ | KEY | GIẢI THÍCH VÀ NGHIỆP VỤ |
| :---- | :---- | :---- | :---- | :---- |
| review\_id | UUID | Có | PK | Mã đánh giá. |
| customer\_id | UUID | Có | Ref | Tài khoản đã mua sản phẩm. |
| product\_id | UUID | Có | FK | Product được đánh giá. |
| order\_item\_id | UUID | Có | Ref | OrderItem làm căn cứ đã mua/đã xác nhận nhận. |
| rating | SMALLINT | Có | CHECK | Số nguyên 1–5. |
| content | TEXT | Không | — | Nội dung bình luận, có thể trống. |
| media\_metadata | JSONB | Không | — | Danh sách ảnh/video review, không tạo entity media mới. |
| created\_at, updated\_at | TIMESTAMPTZ | Có | — | Ngày đánh giá/sửa. |

**Ràng buộc: UNIQUE(customer\_id, product\_id). Cần review:write:own, hồ sơ Customer của sub và OrderItem thuộc Order CONFIRMED của Customer, product\_id khớp. Chỉ có SELLER không đủ quyền review; User có CUSTOMER vẫn được đánh giá các đơn mua hợp lệ. Rating 1–5; nội dung và media tùy chọn; ẩn review khi Product không hiển thị.**

**Đối chiếu:** UC-C01 và UC-B09 kế thừa.

## **3.3 Cart**

Lưu ý định mua của Customer đã đăng nhập; không giữ tồn kho và không chốt giá. Data store PostgreSQL. Theo FRD UC-B03, khách phải đăng nhập mới được thêm vào giỏ, nên **không có giỏ của khách vãng lai** và không có hợp nhất giỏ khi đăng nhập (bỏ từ bản 4.6).

### **3.3.1 Cart**

Một giỏ cho mỗi Customer.

| FIELD | TYPE | REQ | KEY | GIẢI THÍCH VÀ NGHIỆP VỤ |
| :---- | :---- | :---- | :---- | :---- |
| cart\_id | UUID | Có | PK | Định danh giỏ. |
| customer\_id | UUID | Có | UQ \+ Ref | Customer sở hữu giỏ; mỗi Customer đúng một giỏ. |
| row\_version | BIGINT | Có | — | Chống cập nhật mất dữ liệu khi nhiều tab cùng thao tác. |
| created\_at, updated\_at | TIMESTAMPTZ | Có | — | Thời điểm tạo và chỉnh sửa. |

**Ràng buộc: UNIQUE(customer\_id). Tạo giỏ khi Customer thêm sản phẩm lần đầu. Cần cart:manage:own và customer\_id \= sub; có SELLER hoặc ADMIN đơn thuần không tạo quyền mua. User đa vai trò dùng giỏ qua CUSTOMER. Khách vãng lai chuyển sang đăng nhập, không tạo giỏ tạm.**

**Đối chiếu:** UC-B03 (điều kiện "Đã đăng nhập"), UC-B01; BRD 5.1.

### **3.3.2 CartItem**

Dòng sản phẩm với số lượng Customer muốn mua.

| FIELD | TYPE | REQ | KEY | GIẢI THÍCH VÀ NGHIỆP VỤ |
| :---- | :---- | :---- | :---- | :---- |
| cart\_item\_id | UUID | Có | PK | Mã dòng giỏ. |
| cart\_id | UUID | Có | FK | Giỏ chủ. |
| product\_id | UUID | Có | Ref | Product thuộc Product Service. |
| quantity | INTEGER | Có | CHECK | Số lượng \>= 1, \<= P-10. |
| created\_at, updated\_at | TIMESTAMPTZ | Có | — | Dùng xử lý nhiều thao tác cập nhật. |

**Ràng buộc:** UNIQUE(cart\_id, product\_id); quantity từ 1 đến P-10. Thao tác chỉ được thực hiện trên giỏ của đúng chủ sở hữu. Thêm lại sản phẩm đã có thì cộng dồn số lượng; vượt P-10 thì báo lỗi. Hết hàng không biểu diễn bằng số lượng 0\. Giá và tồn luôn kiểm tra lại khi checkout.

**Trạng thái dòng giỏ** (không lưu DB, tính mỗi lần xem giỏ; giỏ hiển thị các dòng nhóm theo cửa hàng, mỗi nhóm sẽ thành một Order khi checkout):

| TRẠNG THÁI | ĐIỀU KIỆN | CHỌN ĐỂ THANH TOÁN? |
| :---- | :---- | :---- |
| IN\_STOCK (còn hàng) | Sản phẩm bán được (G-06) và tồn kho khả dụng ≥ số lượng | Có |
| INSUFFICIENT\_STOCK (không đủ hàng) | 0 \< khả dụng \< số lượng; hiển thị "Chỉ còn X" | Không, đến khi khách giảm số lượng |
| OUT\_OF\_STOCK (hết hàng) | Khả dụng \= 0 | Không |
| Ngừng bán | Sản phẩm không còn bán được | Tự xóa khỏi giỏ khi mở giỏ, kèm thông báo |

**Đối chiếu:** UC-B03, UC-B04 và UC-B06 kế thừa; xem mục 5.3.

## **3.4 Payment**

Sở hữu một khoản thu chung cho CheckoutSession và nghĩa vụ hoàn về Customer. Data store PostgreSQL. Không có PaymentAllocation vì mỗi Order đã mang số tiền riêng bất biến trong cùng checkout. Thanh toán và hoàn tiền đi qua interface PaymentGateway; trong MVP cài đặt là bộ giả lập thanh toán nội bộ (mục 8.4), không có nhà cung cấp bên ngoài.

### **3.4.1 Payment**

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

### **3.4.2 Refund**

Hoàn toàn bộ tiền một Order bị hủy sau thanh toán, hoặc hoàn toàn bộ checkout khi nhận tiền muộn sau khi lượt checkout đã đóng.

| FIELD | TYPE | REQ | KEY | GIẢI THÍCH VÀ NGHIỆP VỤ |
| :---- | :---- | :---- | :---- | :---- |
| refund\_id | UUID | Có | PK | Mã nghĩa vụ hoàn. |
| payment\_id | UUID | Có | FK | Khoản thu chung gốc. |
| order\_id | UUID | ĐK | Ref \+ UQ\* | Bắt buộc với scope ORDER, NULL với FULL\_CHECKOUT; một nghĩa vụ hoàn hủy trên mỗi Order. |
| scope | ENUM | Có | CHECK | ORDER / FULL\_CHECKOUT. |
| amount\_vnd | BIGINT | Có | CHECK | ORDER: bằng Order.total\_amount\_vnd; FULL\_CHECKOUT: bằng Payment.amount\_vnd; \> 0\. |
| reason | ENUM | Có | CHECK | CUSTOMER\_CANCEL / SELLER\_REJECT / SELLER\_CANCEL (đơn bị hủy do Seller bị cấm hoặc không còn hợp lệ) / SELLER\_TIMEOUT / LATE\_PAYMENT. |
| status | ENUM | Có | CHECK | PENDING / SUCCESS / FAILED. |
| idempotency\_key | VARCHAR(128) | Có | UQ | Giữ nguyên qua retry của cùng nghĩa vụ hoàn. |
| provider\_refund\_id | VARCHAR(255) | Không | UQ\* | Mã hoàn do bộ giả lập (hoặc cổng thật) trả về. |
| retry\_count | INTEGER | Có | CHECK | Số lần xử lý \>= 0\. |
| needs\_manual\_review, manual\_note | BOOLEAN / TEXT | Không | — | Cờ chuyển Admin khi thử lại quá ngưỡng; ghi chú xử lý (UC-E08). |
| created\_at, completed\_at | TIMESTAMPTZ | ĐK | — | Thời điểm tạo và kết quả. |

**Ràng buộc:** Payment phải SUCCESS trước khi phát lệnh hoàn. Xác minh Order thuộc đúng checkout của Payment; không hoàn theo Order từ checkout khác. FULL\_CHECKOUT chỉ dùng khi checkout đóng trước khi ghi nhận thu thành công, và loại trừ mọi nghĩa vụ ORDER cho cùng Payment. Khóa Payment để tạo nghĩa vụ hoàn nguyên tử; tổng các nghĩa vụ hoàn đã tạo kể cả FAILED chờ retry không vượt amount\_vnd. Retry tái dùng bản ghi, không mở nghĩa vụ mới. Timeout phải tra cứu trước retry. Không hỗ trợ hoàn một phần của một Order.

**Đối chiếu:** UC-B08, UC-G02 và UC-E08 kế thừa; phạm vi hoàn sửa theo mô hình Order mới.

## **3.5 Finance**

Finance quản lý quyền hưởng tiền của từng Order, số dư ví, lịch sử biến động và các giao dịch rút tiền. Gồm 4 entity: Settlement, Wallet, WalletTransaction, Withdrawal. Triển khai PostgreSQL. Theo FRD v5.2, rút tiền là thao tác giả lập hoàn tất ngay trong Finance; MVP không gọi provider chi trả bên ngoài, nên không có Payout, không có trạng thái chờ chi và không có ngăn tiền "đang rút". Payment/Refund và Order do service tương ứng sở hữu; Finance không cập nhật dữ liệu gốc của service khác. Ví là số dư nghĩa vụ nội bộ, không phải số dư ngân hàng.

### **3.5.1 Settlement**

Một Settlement cho một Order thuộc một Seller. Sau Payment SUCCESS hợp lệ, ghi nhận số tiền Seller và hoa hồng ở trạng thái chờ; chỉ chuyển thành quyền rút khi Order CONFIRMED.

| FIELD | TYPE | REQ | KEY | GIẢI THÍCH VÀ NGHIỆP VỤ |
| :---- | :---- | :---- | :---- | :---- |
| settlement\_id | UUID | Có | PK | Định danh quyết toán theo Order. |
| order\_id | UUID | Có | UQ \+ Ref | Một Settlement cho một Order của đúng một Seller (Commerce). |
| payment\_id | UUID | Có | Ref | Khoản Payment chung của CheckoutSession (Payment Service). |
| seller\_id | UUID | Có | Ref | Seller có quyền nhận tiền; kiểm tra sở hữu Order. |
| gross\_amount\_vnd | BIGINT | Có | CHECK | Giá trị Order đã chụp tại checkout; \> 0\. |
| commission\_bps\_snapshot | INTEGER | Có | CHECK | Tỷ lệ hoa hồng chốt theo Order, trong khoảng 0–10.000 bps. |
| commission\_amount\_vnd | BIGINT | Có | CHECK | Hoa hồng tính từ gross theo tỷ lệ snapshot; ghi nhận ở ngăn chờ trước CONFIRMED. |
| seller\_amount\_vnd | BIGINT | Có | CHECK | Tiền thuộc Seller \= gross − commission; từ 0 đến gross. |
| status | ENUM | Có | CHECK | PENDING / PENDING\_REFUND / SETTLED / REFUNDED. |
| created\_at, settled\_at | TIMESTAMPTZ | ĐK | — | Thời điểm ghi nhận chờ và thời điểm quyết toán khi Order CONFIRMED. |

**Ràng buộc:** UNIQUE(order\_id). G \= Order.total\_amount\_vnd; C \= floor((G × r \+ 5000\) / 10000), S \= G − C, với r là bps. Khi PENDING, C và S là các khoản đang chờ, chưa được rút. PENDING\_REFUND khóa quyền chuyển khả dụng; REFUNDED khi nhận Refund SUCCESS. SETTLED chỉ được ghi khi Order CONFIRMED, không có nghĩa vụ hoàn đang chờ. Tạo Settlement và ghi WalletTransaction idempotent theo order\_id / payment\_id.

**Đối chiếu:** UC-B09, UC-D04, UC-D05 và UC-G04 trong FRD v5.2; Settlement là nguồn giải thích tiền chờ/quyết toán theo từng Order.

### **3.5.2 Wallet**

Một ví nội bộ cho mỗi Seller đã duyệt và đúng một ví nền tảng (seed) để ghi hoa hồng. Ví có ba ngăn (mục 8.3): **chờ quyết toán** (không được rút), **khả dụng** (được rút) và **đã rút**. Không có ngăn "đang rút" hay "reserved", vì rút tiền giả lập hoàn tất ngay trong một transaction. Chỉ ví SELLER được dùng cho Withdrawal.

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

**Ràng buộc:** UNIQUE(owner\_id, currency) WHERE owner\_type \= SELLER; UNIQUE(currency) WHERE owner\_type \= PLATFORM. Ví Seller được tạo với mọi số dư bằng 0 khi Admin duyệt Seller. Mọi số dư luôn \>= 0\. Không tự sửa số dư: mỗi thay đổi phải ghi WalletTransaction trong cùng transaction, và các trường số dư luôn bằng tổng delta của WalletTransaction của ví. Việc chuyển pending sang available chỉ theo Settlement SETTLED (Order CONFIRMED), không vì Payment SUCCESS. Refund không bao giờ trừ available hoặc total\_withdrawn của Seller (WD-06). Seller bị cấm: ví bị đóng băng, số dư giữ nguyên, không rút được; gỡ cấm thì rút tiếp (WD-09). Đối chiếu từng Seller (WD-11): Σ seller\_amount của Settlement SETTLED \= available\_balance\_vnd \+ total\_withdrawn\_vnd; Σ seller\_amount của Settlement PENDING và PENDING\_REFUND \= pending\_balance\_vnd.

**Đối chiếu:** UC-D05, UC-G04, UC-E08; quy tắc WD-01…WD-11 ở mục 8.3.

### **3.5.3 WalletTransaction**

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

### **3.5.4 Withdrawal**

Một lần rút tiền giả lập **đã thành công** của Seller, theo số tiền chọn từ available của ví. Lần rút không còn gắn với một Settlement vì số dư có thể đến từ nhiều Order. Theo FRD v5.2 và mục 8.2J, 8.3, Withdrawal **không có trạng thái**: một lần rút hoặc thành công ngay (tạo Withdrawal và bút toán trong cùng transaction), hoặc bị từ chối vì không hợp lệ và không để lại bản ghi hay thay đổi số dư nào. Không có Payout, không có provider chi trả, không cần Admin duyệt.

| FIELD | TYPE | REQ | KEY | GIẢI THÍCH VÀ NGHIỆP VỤ |
| :---- | :---- | :---- | :---- | :---- |
| withdrawal\_id | UUID | Có | PK | Mã giao dịch rút tiền. |
| withdrawal\_code | VARCHAR(32) | Có | UQ | Mã hiển thị cho Seller trên biên nhận và lịch sử. |
| wallet\_id | UUID | Có | FK | Ví SELLER nguồn; không rút từ ví PLATFORM. |
| seller\_id | UUID | Có | Ref | Lấy từ ngữ cảnh xác thực, không nhận từ client. |
| amount\_vnd | BIGINT | Có | CHECK | Số nguyên VND; P-WD-01 (10.000) \<= amount\_vnd \<= available tại thời điểm rút. |
| available\_before\_vnd | BIGINT | Có | CHECK | Ảnh chụp số dư khả dụng ngay trước khi rút, để hiển thị lịch sử và đối soát. |
| available\_after\_vnd | BIGINT | Có | CHECK | \\= available\_before\_vnd − amount\_vnd; \>= 0\. |
| destination | VARCHAR(100) | Có | — | Cố định "Ví ngoài giả lập". Không lưu số tài khoản ngân hàng, thẻ hay thông tin thanh toán thật. |
| idempotency\_key | VARCHAR(128) | Có | UQ\* | UNIQUE(seller\_id, idempotency\_key); bấm rút lặp hoặc gửi lại không trừ ví lần hai. |
| request\_hash | VARCHAR(128) | Có | — | Cùng key nhưng payload khác (số tiền khác) → từ chối 409\. |
| withdrawn\_at | TIMESTAMPTZ | Có | — | Thời điểm rút thành công (commit transaction). |

Ràng buộc: Chỉ User ACTIVE có withdrawal:create:own và Seller APPROVED, ACTIVE được rút trên ví của mình (WD-01). Trong một local transaction (WD-04): cập nhật có điều kiện available\_balance\_vnd \>= amount\_vnd trên Wallet; available −A, total\_withdrawn \+A; tạo Withdrawal và WalletTransaction WITHDRAWAL\_COMPLETED; ghi OutboxEvent withdrawal.completed để Notification gửi biên nhận. Commit xong là đã rút; lỗi giữa chừng thì rollback toàn bộ, không có bản ghi Withdrawal, số dư không đổi. Các lần rút đồng thời được tuần tự hóa bởi cập nhật có điều kiện, lần nào làm vượt số dư thì bị từ chối. Cùng idempotency\_key \+ cùng payload trả lại đúng giao dịch cũ. Không giới hạn số lần rút trong ngày. Admin chỉ xem, không duyệt, không hủy và không đảo giao dịch rút (WD-08). Phát hiện lệch giữa số dư và tổng bút toán: chặn rút và đánh dấu cho Admin xử lý.

**Đối chiếu:** UC-D05, UC-G01, UC-E08; mục 8.2J và 8.3 (WD-01 đến WD-11). Nếu sau MVP thay rút tiền giả lập bằng cổng chi trả thật (bất đồng bộ, có thể lỗi hoặc timeout) thì phải bổ sung trạng thái giao dịch rút, ngăn "đang rút" để giữ tiền trong lúc chờ và cơ chế đối soát/retry — ngoài phạm vi tài liệu này.

## **3.6 Order Inventory Fulfillment**

Sở hữu CheckoutSession, các Order theo Seller, dòng hàng, tồn kho và Reservation trong cùng PostgreSQL. Không có SellerOrder, FulfillmentGroup hoặc ParentOrder riêng.

### **3.6.1 CheckoutSession**

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
| needs\_manual\_review, manual\_note | BOOLEAN / TEXT | Không | — | Đối soát khoản thu chung hoặc hoàn toàn bộ bất thường (UC-E08). |
| created\_at, expires\_at | TIMESTAMPTZ | Có | — | Thời điểm tạo và mốc kiểm tra hết hạn theo cấu hình. |

**Ràng buộc:** Chỉ Customer ACTIVE tạo checkout; cần số điện thoại và địa chỉ hợp lệ. Chụp giá, danh sách dòng hàng, Seller và tỷ lệ hoa hồng trước transaction tạo toàn bộ Order cùng Reservation. UNIQUE(checkout\_session\_id, seller\_id) tại Order bảo đảm mỗi Seller một Order. Tạo checkout, Order, OrderItem và giữ kho thành công toàn bộ hoặc rollback toàn bộ. Tối đa một CheckoutSession AWAITING\_PAYMENT cho mỗi Customer (G-08). BUY\_NOW: đúng 1 Order chứa đúng 1 OrderItem, không đọc và không trừ giỏ ở bất kỳ bước nào (G-16). Trước khi thanh toán, MVP không cho hủy lẻ hay thanh toán riêng một Order; khách hủy cả phiên. Callback thu tiền muộn khi CLOSED không mở lại checkout; hoàn FULL\_CHECKOUT. PAID giữ nguyên dù sau đó một số Order bị hủy và hoàn tiền.

**Đối chiếu:** UC-B04, B05, B06, B10 và UC-G03.

### **3.6.2 Order**

Một đơn của đúng một Seller trong CheckoutSession. Customer có thể mua nhiều Seller một lần, thanh toán chung và theo dõi từng Order độc lập.

| FIELD | TYPE | REQ | KEY | GIẢI THÍCH VÀ NGHIỆP VỤ |
| :---- | :---- | :---- | :---- | :---- |
| order\_id | UUID | Có | PK | Mã đơn theo Seller. |
| checkout\_session\_id | UUID | Có | FK | Nhiều Order cùng tham chiếu một CheckoutSession. |
| seller\_id | UUID | Có | Ref | Seller duy nhất của đơn. |
| customer\_id | UUID | Có | Ref | Customer phải trùng CheckoutSession. |
| total\_amount\_vnd | BIGINT | Có | CHECK | Tổng line\_total\_vnd của OrderItem trong chính Order này; \> 0\. |
| currency | CHAR(3) | Có | CHECK | VND; giữ quy ước giá đã gồm thuế và phí vận chuyển 0 từ bản nguồn. |
| commission\_bps\_snapshot | INTEGER | Có | CHECK | Tỷ lệ hoa hồng 0 đến 10.000 bps chốt tại checkout; truyền cho Finance. |
| recipient\_name, recipient\_phone | VARCHAR | Có | — | Snapshot người nhận. |
| province\_code, ward\_code | VARCHAR(32) | Có | — | Snapshot mã địa giới nhận hàng. |
| province\_name, ward\_name | VARCHAR(150) | Có | — | Snapshot tên tỉnh/thành và phường/xã tại thời điểm checkout, lấy từ RegionProvider qua Identity; không tra lại khi hiển thị đơn cũ. |
| address\_detail | VARCHAR(300) | Có | — | Snapshot địa chỉ chi tiết. |
| status | ENUM | Có | CHECK | AWAITING\_PAYMENT (chờ thanh toán) / PAYMENT\_FAILED (thanh toán thất bại) / EXPIRED (hết hạn) / PAID (chờ xử lý) / PACKING (đang đóng gói) / DELIVERED (đã giao — chờ khách xác nhận) / CONFIRMED (đã xác nhận) / CANCELLED (đã hủy). State machine ở 8.2B. |
| cancel\_reason\_code | VARCHAR(50) | ĐK | — | Mã lý do hủy theo enum nghiệp vụ (CustomerCancelReason P-21a, SellerRejectReason P-21b); bắt buộc khi Customer hủy hoặc Seller từ chối, NULL khi hệ thống hủy. |
| cancel\_reason\_note | VARCHAR(500) | ĐK | — | Ghi chú chi tiết; bắt buộc khi Seller từ chối hoặc khi mã là OTHER. |
| cancelled\_by\_type, cancelled\_by | ENUM / UUID | ĐK | Ref cho actor | Loại tác nhân CUSTOMER / SELLER / SYSTEM / ADMIN và id tác nhân (NULL nếu hệ thống hủy). |
| paid\_at, packing\_at, delivered\_at, confirmed\_at, cancelled\_at | TIMESTAMPTZ | ĐK | — | Mốc bắt buộc tương ứng với chuyển trạng thái; packing\_at khi Seller bắt đầu đóng gói. |
| confirmed\_by | ENUM | ĐK | — | CUSTOMER hoặc SYSTEM (J3); bắt buộc khi CONFIRMED. |
| seller\_action\_due\_at, customer\_confirm\_due\_at | TIMESTAMPTZ | ĐK | — | Lần lượt paid\_at \+ 7 ngày và delivered\_at \+ 7 ngày. |
| needs\_manual\_review, manual\_note | BOOLEAN / TEXT | Không | — | Đối soát hoàn tiền bất thường của riêng đơn (UC-E08). |
| created\_at, updated\_at | TIMESTAMPTZ | Có | — | Dấu thời gian vòng đời đơn. |

**Ràng buộc:** UNIQUE(checkout\_session\_id, seller\_id); không unique riêng checkout\_session\_id. Mọi OrderItem có cùng Seller với Order. Một Payment success cập nhật tất cả Order đang chờ trong checkout và commit Reservation bằng một local transaction. Trạng thái giao, từ chối, hủy và nhận hàng độc lập theo Order; không tạo trạng thái giao chung để ghi đè các đơn khác. Khóa Order khi chuyển trạng thái để ngăn CONFIRMED và CANCELLED đồng thời. Khách chỉ hủy được Order ở PAID (chưa đóng gói); Seller từ chối được ở PAID hoặc PACKING; không có chuyển trạng thái từ DELIVERED/CONFIRMED sang CANCELLED. Hiển thị DELIVERED là "Đã giao — chờ khách xác nhận"; không có trạng thái "đang giao".

**Đối chiếu:** UC-B04 đến B09 và các luồng Seller kế thừa; thay toàn bộ vai trò SellerOrder cũ.

### **3.6.3 OrderItem**

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

**Đối chiếu:** UC-B04, UC-B06 và UC-B08 kế thừa.

### **3.6.4 Inventory**

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

### **3.6.5 Reservation**

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

### **3.6.6 StockMovement**

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

**Đối chiếu:** UC-D03, UC-B06, UC-B08 và UC-G02 kế thừa.

## **3.7 Notification**

Gửi email bất đồng bộ, tùy chọn in-app; không nằm trên đường xử lý đồng bộ của checkout. Data store queue và PostgreSQL log hoặc tương đương.

### **3.7.1 Notification**

Một thông báo cho một người nhận theo sự kiện, có chống gửi trùng và retry.

| FIELD | TYPE | REQ | KEY | GIẢI THÍCH VÀ NGHIỆP VỤ |
| :---- | :---- | :---- | :---- | :---- |
| notification\_id | UUID | Có | PK | Mã thông báo. |
| recipient\_user\_id | UUID | Có | Ref | User.user\_id duy nhất của người nhận; không định tuyến bằng loại tài khoản. |
| audience\_context | ENUM | Có | CHECK | CUSTOMER / SELLER / ACCOUNT; ngữ cảnh nội dung, không là bằng chứng cấp quyền. |
| notification\_type | VARCHAR(70) | Có | — | Payment, Order giao/hủy/nhận, Refund, tiền vào khả dụng, rút tiền thành công (biên nhận), Seller approval và ban. |
| channel | ENUM | Có | CHECK | EMAIL (Must), IN\_APP (Should). |
| subject | VARCHAR(255) | Không | — | Tiêu đề email nếu có. |
| content | TEXT | Có | — | Nội dung hiển thị/gửi; tiền giả lập cần có nhãn rõ. |
| business\_reference\_id | UUID | Không | Ref | CheckoutSession, Order, Payment hoặc Withdrawal liên quan. |
| dedupe\_key | VARCHAR(160) | Có | UQ | Một event/kênh/người nhận chỉ gửi một lần logic. |
| status | ENUM | Có | CHECK | PENDING / SENT / FAILED. |
| retry\_count, sent\_at | INTEGER/TIMESTAMPTZ | ĐK | — | Theo dõi thực thi. |
| is\_read | BOOLEAN | ĐK | — | Chỉ dùng cho IN\_APP nếu Should được triển khai. |
| created\_at | TIMESTAMPTZ | Có | — | Ngày ghi nhận yêu cầu gửi. |

**Ràng buộc: dedupe\_key theo event, kênh, recipient\_user\_id và audience\_context để không mất hai thông báo nghiệp vụ khác nhau của User đa vai trò. API đọc thông báo lọc recipient\_user\_id \= sub. Email chung lấy từ Identity; retry theo P-19. Thông báo không cấp quyền hoặc sửa giao dịch; nội dung tiền mang nhãn giả lập.**

**Đối chiếu:** UC-G01 kế thừa; thêm sự kiện tiền vào khả dụng và biên nhận rút tiền thành công (gồm mã giao dịch, số tiền, số dư khả dụng còn lại).

## **3.8 Reporting**

Chỉ sở hữu dữ liệu phục vụ dashboard; không nhận quyền cập nhật Order, Payment, Settlement, Product hoặc Inventory gốc.

**DATA STORE:** Read-model database (PostgreSQL)

### **3.8.1 OrderReportProjection \[READ MODEL\]**

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

### **3.8.2 FinancialReportProjection \[READ MODEL\]**

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

### **3.8.3 InventoryReportProjection \[READ MODEL\]**

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

**RÀNG BUỘC:** Đây là dữ liệu eventual consistency để báo cáo. Không dùng bảng này quyết định đủ tồn kho khi Checkout.

Đối chiếu FRD: UC-E02; UC-D03.

# **4 Quan hệ ownership và bất biến**

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
| User → Customer và Seller | Mỗi nhánh 1 → 0..1; customer\_id/seller\_id vừa PK vừa FK User.user\_id. Hai hồ sơ có thể cùng tồn tại. |
| User ↔ Role ↔ Permission | N ↔ N qua UserRole và RolePermission; hồ sơ không thay thế quyền. |
| User → Notification và audit | recipient\_user\_id/actor\_user\_id xác định danh tính chung; ngữ cảnh tách khỏi role. |

## **4.1 Các bất biến phải kiểm thử**

* CheckoutSession.total\_amount\_vnd \= tổng Order.total\_amount\_vnd trong checkout \= Payment.amount\_vnd. Mỗi Order.total\_amount\_vnd \= tổng OrderItem.line\_total\_vnd của đơn đó.  
    
* Mỗi Seller xuất hiện tối đa một Order trong một checkout; mọi Order thuộc cùng Customer với CheckoutSession. Payment không unique theo từng Order.  
    
* Một Payment SUCCESS chỉ trừ tồn một lần cho toàn bộ checkout. available không âm. Callback hoặc event lặp không tạo Settlement hoặc biến động kho lặp.  
    
* Refund ORDER bằng tiền của đúng Order thuộc Payment. FULL\_CHECKOUT loại trừ hoàn từng Order. Nghĩa vụ hoàn đã tạo không vượt số tiền thu, kể cả bản ghi FAILED đang chờ retry.  
    
* Order CANCELLED trước khi thu thành công không sinh Settlement hoặc quyền rút. Order đã thu rồi bị hủy tạo nghĩa vụ hoàn riêng, không hủy Order của Seller khác.  
    
* Settlement SETTLED chỉ khi Order CONFIRMED; gross\_amount\_vnd \= commission\_amount\_vnd \+ seller\_amount\_vnd. Payment SUCCESS hợp lệ tạo Settlement PENDING và ghi tiền chờ vào Wallet; Order hủy trước xác nhận chuyển PENDING\_REFUND, Refund SUCCESS mới trừ pending. Không chuyển khả dụng trong khi đang hoàn.  
    
* Một Seller có một Wallet theo loại tiền. Withdrawal rút bất kỳ số tiền hợp lệ (10.000 VND \<= A \<= available) từ available của Wallet, có thể tích lũy từ nhiều Settlement. Trong một transaction có điều kiện: available −A, total\_withdrawn \+A, tạo Withdrawal và WalletTransaction WITHDRAWAL\_COMPLETED; thất bại thì rollback, không có bản ghi. Hai lần rút đồng thời không làm tổng rút vượt available; cùng idempotency\_key chỉ trừ ví một lần. Mọi số dư không âm và mọi thay đổi có WalletTransaction.  
    
* Với mỗi Seller: Σ seller\_amount của Settlement SETTLED \= available\_balance\_vnd \+ total\_withdrawn\_vnd; Σ seller\_amount của Settlement PENDING và PENDING\_REFUND \= pending\_balance\_vnd; total\_withdrawn\_vnd \= Σ Withdrawal.amount\_vnd. Refund không bao giờ trừ available hoặc total\_withdrawn của Seller.  
    
* Refund PENDING/timeout phải tra cứu bộ giả lập (hoặc cổng thật) trước retry và giữ nguyên idempotency\_key. WalletTransaction chống trùng theo business\_event\_key; event lặp không ghi bút toán lần hai.  
    
* User ACTIVE có CUSTOMER và hồ sơ Customer hợp lệ mới có quyền giỏ/checkout; SELLER hoặc ADMIN không tự kế thừa quyền mua. User đa vai trò vẫn mua được qua CUSTOMER. Customer BANNED theo G-14 không tạo checkout/thanh toán mới. CartItem thêm sau snapshot không bị xóa sau Payment success; BUY\_NOW không đổi giỏ.  
    
* Order chỉ chuyển trạng thái theo 8.2B; khách không hủy được Order từ PACKING trở đi; không Order nào đi từ DELIVERED/CONFIRMED sang CANCELLED.

UserRole và RolePermission có khóa kép duy nhất; mọi FK Identity không mồ côi. Không có email/password\_hash ở bảng con. customer\_id \= User.user\_id và seller\_id \= User.user\_id; một User có thể xuất hiện ở cả hai. Quyền hiệu lực luôn kết hợp permission, trạng thái và ownership; xem bộ kiểm thử 5.8.

## **4.2 Quyền ghi dữ liệu**

| SERVICE | DỮ LIỆU ĐƯỢC GHI | KHÔNG TỰ Ý GHI |
| :---- | :---- | :---- |
| Identity và Seller | User, Customer, Seller, CustomerAddress, Role, Permission, UserRole, RolePermission và bảng xác thực | Product, Order, Payment; service khác không ghi RBAC trực tiếp |
| Product | Category, Product, Review | Inventory, OrderItem |
| Cart | Cart, CartItem | Order, Reservation |
| Order Inventory Fulfillment | CheckoutSession, Order, OrderItem, Inventory, Reservation, StockMovement | Payment, Refund, Settlement, Wallet |
| Payment | Payment, Refund | Order.status, Settlement |
| Finance | Settlement, Wallet, WalletTransaction, Withdrawal | Payment.status, Refund.status, Order.status; không cập nhật trực tiếp dữ liệu giao dịch dịch vụ khác |
| Notification | Notification | Dữ liệu nghiệp vụ của service khác |

# **5 Luồng nghiệp vụ cập nhật**

## **5.1 Đăng ký và phân quyền Seller**

Một hệ xác thực chung cho Customer, Seller, Admin. Đăng ký mua hàng tạo User \+ Customer \+ UserRole CUSTOMER nguyên tử; endpoint không nhận role hoặc permission từ client. Đăng ký bán hàng mới tạo User \+ Seller PENDING, không tự tạo Customer hay cấp SELLER; User đã đăng nhập dùng API nộp hồ sơ của mình, không đăng ký lại email.

Admin có seller:approve duyệt Seller: cập nhật APPROVED, gán SELLER, tăng authz\_version và ghi AdminAuditLog trong một transaction; phát outbox để Finance tạo ví idempotent. Từ chối giữ User và Customer nếu có, cho nộp lại hồ sơ theo 3.1.3. Google onboarding chỉ tạo Customer; User GOOGLE đã tồn tại vẫn được nộp Seller, không có tài khoản hay mật khẩu Seller riêng.

Mọi API kiểm tra quyền cụ thể, trạng thái User, trạng thái hồ sơ và ownership. SELLER có quyền shop/đơn/ví sau duyệt; không tự có quyền giỏ/checkout/review. Một User có cả CUSTOMER và SELLER dùng cả hai nhóm quyền trong ngữ cảnh tương ứng. ADMIN không tự có quyền mua/bán và không được tự duyệt shop. Không dựa vào việc ẩn nút trên UI.

## **5.2 Tìm kiếm đơn giản trong database**

Product Service tìm Product theo product\_name bằng truy vấn tham số hóa với ILIKE, hoặc LIKE trên giá trị chuẩn hóa chữ thường. Nếu tìm Seller theo tên, Identity và Seller cung cấp API tương tự trên tên cửa hàng (users.full\_name của User có Seller hợp lệ); không join trực tiếp database giữa service. Escape ký tự % và \_ nếu từ khóa được hiểu là chuỗi literal. Giới hạn độ dài từ khóa, kích thước trang và sắp xếp ổn định để phân trang.

Kết quả Product phải áp dụng đầy đủ G-06, gồm User ACTIVE, quyền SELLER và Seller APPROVED, ACTIVE qua API sở hữu. Có thể xác minh theo lô và tiếp tục lấy trang để tránh đưa shop không hợp lệ vào kết quả. Tồn hiển thị lấy từ Order service khi cần; checkout luôn kiểm tra lại giá, Seller và tồn chính thức. Không triển khai full-text, gợi ý từ khóa, sửa lỗi chính tả, OpenSearch hoặc pipeline đồng bộ search trong MVP. Tìm không dấu chưa được cam kết trong phạm vi LIKE cơ bản.

## **5.3 Giỏ hàng, Mua ngay và checkout**

1. Customer phải đăng nhập mới thêm sản phẩm vào giỏ; khách vãng lai bấm "Thêm vào giỏ" hoặc "Mua ngay" được chuyển sang đăng nhập rồi quay lại trang sản phẩm. Không có giỏ khách và không hợp nhất giỏ.  
     
2. Hai lối vào checkout: từ giỏ (checkout\_source \= CART, Customer chọn các dòng còn hàng) hoặc nút "Mua ngay" trên trang sản phẩm (checkout\_source \= BUY\_NOW, đúng 1 sản phẩm với số lượng đã chọn, không đọc/ghi giỏ).  
     
3. User chỉ có SELLER hoặc ADMIN không tạo giỏ/checkout; User có CUSTOMER và hồ sơ Customer hợp lệ vẫn mua được theo quyền CUSTOMER.  
     
4. Customer chọn địa chỉ (mặc định theo 3.1.4). Server lấy giá từ Product Service, Seller hợp lệ, tỷ lệ hoa hồng hiện hành và tồn kho. Trong local transaction, tạo CheckoutSession, nhóm theo seller\_id để tạo N Order, tạo OrderItem và giữ kho cho toàn bộ checkout. Thiếu hàng ở bất cứ dòng nào thì rollback cả lượt.  
     
5. Payment tạo một khoản thu chung bằng tổng N Order. Khi SUCCESS, Order service khóa checkout, cập nhật PAID, commit Reservation và ghi SOLD cho toàn bộ Order trong một transaction; phát các event bằng outbox. Với CART, Cart chỉ giảm lượng đã mua trên các dòng còn tồn tại bằng snapshot; với BUY\_NOW không động tới giỏ. Cart và xử lý event idempotent; nếu khách đã xóa rồi thêm lại thì không giảm nhầm dòng mới.  
     
6. Thanh toán FAILED rõ ràng: đóng checkout (close\_reason PAYMENT\_FAILED), mọi Order → PAYMENT\_FAILED, nhả toàn bộ giữ kho, giỏ giữ nguyên. Thanh toán PENDING: đối soát theo cấu hình trước khi nhả. Hết hạn: close\_reason EXPIRED, mọi Order → EXPIRED. Tiền đến sau CLOSED: không mở lại đơn, tạo một Refund FULL\_CHECKOUT.  
     
7. Sau khi đã thu, mỗi Seller xử lý Order của mình độc lập. Customer theo dõi danh sách Order theo checkout; hủy hoặc hoàn một Order không thay đổi tổng tiền gốc hay trạng thái thực hiện Order của Seller khác.

## **5.4 Use case Seller xem ví và rút tiền (UC-D05)**

Theo FRD v5.2, Seller rút một số tiền bất kỳ từ số dư khả dụng của Wallet, thay vì rút toàn bộ Settlement theo từng Order. Finance sở hữu Settlement, Wallet, WalletTransaction và Withdrawal. Rút tiền là giao dịch giả lập **hoàn tất ngay**: không cần Admin duyệt, không có trạng thái chờ, không có Payout hay provider chi trả.

**Tiền điều kiện: User ACTIVE, quyền withdrawal:create:own, Seller ACTIVE và APPROVED; wallet.owner\_id \= seller\_id \= sub; 10.000 VND ≤ amount\_vnd ≤ available\_balance\_vnd. Kiểm tra quyền hiện hành ngay trước transaction rút; tiền chỉ vào available khi Order CONFIRMED.**

1. Seller mở Ví & rút tiền. Finance API trả ba số dư pending, available, total\_withdrawn kèm nhãn "Giả lập — không phát sinh tiền thật"; hiển thị Settlement, WalletTransaction và lịch sử Withdrawal liên quan. Nếu available \< 10.000 VND, nút Rút tiền bị vô hiệu kèm giải thích (tiền đang chờ quyết toán, hoặc chưa đủ mức tối thiểu).  
     
2. Seller nhập số tiền A và xác nhận; FE gửi amount\_vnd và idempotency\_key. Đích nhận cố định Ví ngoài giả lập. Server lấy seller\_id \= sub, xác minh Seller và ví sở hữu, không tin seller\_id/owner\_id hoặc thông báo đủ tiền do client gửi.  
     
3. Finance kiểm tra trạng thái Seller và hạn mức, rồi trong **một** local transaction: cập nhật có điều kiện available \>= A; available −A, total\_withdrawn \+A; tạo Withdrawal (kèm available\_before/after); ghi WalletTransaction WITHDRAWAL\_COMPLETED và OutboxEvent withdrawal.completed.  
     
4. Commit thành công: trả ngay kết quả "Rút tiền thành công" với mã giao dịch, số tiền và số dư khả dụng còn lại; Notification gửi biên nhận. Gửi lại cùng key/payload trả đúng giao dịch cũ; cùng key, số tiền khác → 409\.  
     
5. Không hợp lệ (dưới mức tối thiểu, vượt available, Seller bị cấm, lệch sổ ví) hoặc lỗi giữa chừng: từ chối/rollback, không tạo Withdrawal, số dư không đổi; Seller có thể thử lại.

**Hậu điều kiện:** Mỗi lần rút hoặc thành công ngay với đúng một Withdrawal và một WalletTransaction, hoặc không để lại dấu vết nào. Tổng đã rút không vượt Σ seller\_amount đã quyết toán. Khi Order bị hủy trước CONFIRMED, phần tiền nằm ở pending của chính Order đó; Refund do Payment Service thực hiện không chạm tới available hay total\_withdrawn của Seller.

## **5.5 Trạng thái Order và các job**

Bảng dưới tóm tắt; state machine đầy đủ ở 8.2.

| TỪ TRẠNG THÁI | SANG TRẠNG THÁI | ĐIỀU KIỆN VÀ TÁC ĐỘNG |
| :---- | :---- | :---- |
| AWAITING\_PAYMENT | PAID | Payment SUCCESS khi checkout còn hợp lệ; commit tồn toàn bộ checkout. |
| AWAITING\_PAYMENT | PAYMENT\_FAILED / EXPIRED / CANCELLED | Thanh toán thất bại rõ ràng / hết hạn giữ hàng hoặc quá P-04 / khách hủy cả phiên; nhả hold, không hoàn tiền vì chưa thu. |
| PAID | PACKING | Seller bắt đầu chuẩn bị hàng; từ đây khách không tự hủy được. |
| PAID | CANCELLED | Khách hủy hợp lệ (chưa đóng gói); hoặc Seller từ chối / J4 quá hạn / Seller bị cấm. Restock một lần; Settlement PENDING\_REFUND; tạo Refund. |
| PACKING | CANCELLED | Seller từ chối / J4 quá hạn / Seller bị cấm. Như trên. |
| PACKING | DELIVERED | Seller xác nhận đã giao trước hạn 7 ngày; đặt customer\_confirm\_due\_at \= delivered\_at \+ 7 ngày. |
| DELIVERED | CONFIRMED | Customer xác nhận nhận hoặc J3 hết hạn 7 ngày; Settlement SETTLED và Wallet pending → available, không tự chi. |
| CONFIRMED, CANCELLED, PAYMENT\_FAILED, EXPIRED | Không chuyển tiếp | Trạng thái kết thúc của MVP; không có trả hàng sau hoàn tất. |

J4 kiểm tra Order ở PAID hoặc PACKING chưa DELIVERED hoặc từ chối trong 7 ngày kể từ Payment success và hủy Order quá hạn. J3 xác nhận tự động sau 7 ngày từ DELIVERED nếu Customer chưa xác nhận. Các job khóa bản ghi và kiểm tra trạng thái hiện tại để không chạy chồng với thao tác người dùng. Trường hợp PAID chưa có Shipment hoặc trạng thái vận chuyển chi tiết là giới hạn chủ động của MVP.

## **5.6 Reporting tạm hoãn và bỏ chức năng báo cáo vi phạm**

Reporting Service cùng OrderReportProjection, FinancialReportProjection và InventoryReportProjection vẫn tạm hoãn trong MVP. Chức năng Customer gửi báo cáo vi phạm sản phẩm/Seller (UC-C02), chức năng Admin tiếp nhận/xử lý báo cáo từ Customer (UC-E05) và entity ViolationReport/Report được loại bỏ khỏi phạm vi; không triển khai API, màn hình hoặc bảng nghiệp vụ cho các chức năng này. Đây là quyết định loại bỏ, không phải tạm hoãn.

Giữ các màn hình vận hành cần thiết: danh sách Order, tồn kho, Settlement, Wallet (chờ/khả dụng/đã rút), lịch sử WalletTransaction, lịch sử Withdrawal và lịch sử Payment/Refund. Mỗi màn hình đọc API service sở hữu và lọc đúng quyền; không đọc trực tiếp database service khác. Admin vẫn duyệt Seller, khóa tài khoản và gỡ sản phẩm theo quyền quản trị, mà không cần luồng tiếp nhận báo cáo từ Customer. Dashboard phân tích doanh số, biểu đồ, tổng hợp đa service và export report được hoãn; UC-D04/UC-E02 chỉ còn phần tra cứu vận hành nếu tương ứng.

## **5.7 Hợp đồng API và thực thi phân quyền**

Các endpoint dưới đây là hợp đồng điều chỉnh đi kèm tài liệu database, không khẳng định đã sửa tài liệu API Specs hoặc mã nguồn bên ngoài. Một Auth API dùng chung mọi vai trò; đường dẫn customer/seller/admin thể hiện nhóm nghiệp vụ, không có bộ credential riêng.

| API | METHOD | CHỦ THỂ | QUYỀN | HÀNH VI |
| :---- | :---- | :---- | :---- | :---- |
| /auth/register | POST | Public | — | email, password, full\_name, thông tin Customer; tạo User \+ Customer \+ CUSTOMER. Từ chối role/status/permission từ client. |
| /auth/register-seller | POST | Public | — | email, password, full\_name (tên cửa hàng), phone\_number; tạo User LOCAL \+ Seller PENDING cho email mới. Email đã tồn tại phải login rồi nộp hồ sơ. |
| /auth/login | POST | Public | — | email \+ password; không nhận loại tài khoản để chọn bảng đăng nhập. |
| /auth/google | POST | Public | — | Xác minh ID token và Google subject; onboarding Customer. Không gộp email trùng LOCAL. |
| /auth/refresh / logout | POST | User | Phiên hợp lệ | Đọc quyền hiện hành; xoay hoặc thu hồi refresh token. |
| /auth/forgot-password / reset-password | POST | Public | Token reset | Thông báo quên mật khẩu đồng nhất; reset User LOCAL, thu hồi phiên. |
| /users/me | GET/PATCH | User ACTIVE | account:\*:own | GET trả user\_id, roles, permissions và hồ sơ hiện có; PATCH allowlist full\_name/phone\_number, không mass assignment; full\_name bị khóa khi có Seller PENDING/APPROVED (G-02). |
| /users/me/seller-application | POST/GET | User ACTIVE | seller:application:own | Lấy user\_id từ sub; POST tạo hoặc nộp lại PENDING, GET xem hồ sơ của mình. |
| /users/me/customer-profile | POST | User ACTIVE | Tự phục vụ | Tạo Customer ACTIVE \+ CUSTOMER nguyên tử, idempotent; không tự gỡ BANNED. |
| /admin/sellers/{id}/approve hoặc reject | POST | ADMIN | seller:approve | Kiểm tra PENDING, chống tự duyệt, cập nhật có điều kiện; audit và version. |
| /admin/users/{id}/moderation | POST | ADMIN | account:moderate | Bắt buộc scope USER/CUSTOMER/SELLER, action và reason; xử lý G-14, không khóa Admin qua UI MVP. |
| /customer/\* / seller/\* / admin/\* | Theo nghiệp vụ | User | Permission cụ thể | Lọc ownership bằng sub; dùng ma trận 3.1.9 và state machine, không chỉ kiểm tra prefix URL. |

Access token dùng sub \= User.user\_id, iss, aud, iat, exp, jti và authz\_version; roles có thể là snapshot để hiển thị, không coi snapshot là quyền hiện hành. Không có subject\_type để chọn ba bảng đăng nhập. customer\_id/seller\_id nếu trả về chỉ là ID hồ sơ, phải bằng sub. Không đưa password\_hash, google\_subject hoặc refresh token vào dữ liệu hồ sơ công khai.

MVP mỗi API bảo vệ xác minh chữ ký/thuật toán cho phép, issuer, audience, hạn token, sau đó hỏi Identity qua kênh xác thực service để lấy trạng thái, authz\_version, quyền và hồ sơ hiện tại. Không cache quyết định quyền trong MVP; Identity lỗi thì từ chối truy cập bảo vệ với 503\. Version token cũ trả 401 và client refresh; thiếu quyền hoặc hồ sơ bị hạn chế trả 403\. Với tài nguyên không thuộc chủ thể trả 404 nhất quán. Không có token/hết hạn trả 401; xung đột unique/idempotency trả 409\.

Service sở hữu tải tài nguyên và kiểm tra customer\_id/seller\_id với sub từ token đã xác minh, không tin body/header X-User-Id do client gửi. API liệt kê luôn thêm bộ lọc ownership tại server. Trước checkout, thu tiền và rút tiền phải đọc trạng thái hiện hành; khóa và cập nhật có điều kiện trong transaction dữ liệu sở hữu. Không thể nguyên tử giữa Identity và Finance: lệnh đã được chấp thuận trước thời điểm khóa có thể hoàn tất; khóa ngăn các yêu cầu được kiểm tra sau thời điểm commit Identity. Callback và job dùng service credential, allowlist thao tác, idempotency và audit, không bị chặn hoàn tiền chỉ vì User bị khóa.

Mọi thay đổi UserRole hoặc trạng thái User/Customer/Seller tăng User.authz\_version trong cùng transaction và ghi audit. Thay RolePermission bằng migration phải tăng version mọi User bị ảnh hưởng. Quy trình cấp ADMIN/thu hồi ADMIN chỉ là công cụ nội bộ có kiểm soát, không có API công khai hay UI tự nâng quyền; giữ ít nhất một Admin hoạt động. Google login không lách chính sách email hay tự thêm hồ sơ mới khi đăng nhập một User đã tồn tại.

## **5.8 Migration dữ liệu và kiểm thử RBAC**

1\. Sao lưu, diễn tập và chặn ghi Identity trong cửa sổ migration. Lập bảng mapping old\_type \+ old\_id → user\_id; rà email/SĐT chuẩn hóa bị trùng, không tự hợp nhất credential hoặc hồ sơ khác chủ thể. Trường hợp trùng phải giải quyết danh tính trước cutover.

2\. Tạo users và bốn bảng RBAC, seed ma trận quyền. Với dữ liệu cũ Customer/Seller độc lập, ưu tiên giữ UUID cũ làm user\_id để giữ nguyên customer\_id/seller\_id; nếu UUID trùng giữa hai người khác nhau thì cấp UUID mới và cập nhật toàn bộ tham chiếu bằng mapping. Không tự ghép hai người chỉ vì email trùng.

3\. Chuyển credential/full\_name/email vào User; với Seller cũ, seller\_name chuyển thành users.full\_name (tên cửa hàng) và contact\_phone chuyển vào users.phone\_number nếu còn trống, trùng thì giải quyết trước cutover; Customer/Seller giữ field chuyên biệt và thêm FK shared PK. Chuyển AdminAccount sang User \+ ADMIN; reviewed\_by\_admin\_id đổi reviewed\_by\_user\_id. Customer cũ cấp CUSTOMER; chỉ Seller APPROVED cấp SELLER. Profile BANNED giữ ở hồ sơ, không map thẳng thành User DISABLED vì còn ngoại lệ G-14. Admin seed LOCAL, không mở đăng ký ADMIN.

4\. Map notification recipient\_type \+ recipient\_id → recipient\_user\_id \+ audience\_context; map reset/session/audit/stock actor và idempotency sang user\_id. Khóa idempotency cũ giữ endpoint/ngữ cảnh để không tái thực hiện lệnh. Nếu đổi ID, cập nhật CustomerAddress, Cart, Review, CheckoutSession, Order, Payment, Seller Product, Settlement, Wallet, Withdrawal và các projection/event metadata có liên quan theo mapping của từng service.

5\. Kiểm tra row count, orphan FK, unique và mapping; chuyển toàn bộ service sang Auth API chung, thu hồi token/reset token cũ và yêu cầu đăng nhập lại. Sau khi kiểm tra luồng thật thành công mới xóa credential bảng con, AdminAccount và LoginIdentifier. Rollback dùng snapshot nhất quán trước cutover, không khôi phục riêng Identity sau khi hệ mới đã ghi giao dịch.

### **Bất biến RBAC cần kiểm thử**

Tạo Customer có đúng một User và CUSTOMER; đăng ký Seller PENDING không gọi được API shop/rút. Approve lặp chỉ có một role và một ví; reject/reapply giữ user\_id và lịch sử. Chặn client tự gửi ADMIN, đổi status hoặc duyệt shop của chính mình. LOCAL và GOOGLE trùng email không tự gộp.

User có CUSTOMER \+ SELLER đăng nhập một lần, mua qua Customer và bán qua Seller; User chỉ có ADMIN/SELLER không checkout. Hoán đổi ID giỏ/đơn/sản phẩm/ví/thông báo của người khác bị từ chối ở cả API list và detail. Thiếu profile dù có role cũng bị từ chối; có profile nhưng role bị thu hồi không cấp lại quyền.

Seller BANNED không quản lý shop/rút nhưng CUSTOMER hợp lệ vẫn mua. Customer BANNED có đơn đã trả tiền vẫn xử lý đơn cũ nhưng không checkout/thanh toán mới; chưa có đơn trả tiền thì chỉ chặn phạm vi Customer. User DISABLED chặn mọi API bảo vệ; callback hoàn tiền và job hệ thống vẫn chạy đúng nghiệp vụ.

Token đã cấp trước revoke/ban/disable hoặc thay RolePermission không dùng tiếp quyền cũ; refresh chỉ nhận quyền hiện tại. Identity unavailable không cho phép truy cập. Reset password thu hồi phiên. Audit mọi thay đổi quyền/khóa; migration giữ tham chiếu và không tạo giao dịch tiền hay gửi thông báo lặp.

# **6 Bảng kỹ thuật tối thiểu**

Các bảng sau hỗ trợ bảo mật và độ tin cậy, không tính vào 29 entity ở mục 2\. User và bốn bảng RBAC là dữ liệu Identity, không phải bảng kỹ thuật. Mỗi service triển khai bảng kỹ thuật theo nhu cầu; bảng phiên/reset token luôn tham chiếu user\_id chung.

| BẢN GHI | NƠI CẦN | TRƯỜNG TỐI THIỂU | MỤC ĐÍCH |
| :---- | :---- | :---- | :---- |
| OutboxEvent | Service phát event | event\_id, event\_type, aggregate\_id, payload, status, created\_at | Ghi event cùng local transaction để không mất sự kiện. |
| InboxEvent | Consumer quan trọng | message\_id, consumer\_name, processed\_at; UQ(message\_id, consumer\_name) | Chặn xử lý Payment, kho hoặc ghi bút toán ví lặp. |
| AdminAuditLog | Identity và quản trị từng service | actor\_user\_id, action, target\_type, target\_id, reason, before\_after, request\_id, happened\_at | actor\_user\_id tham chiếu User (FK nội bộ hoặc Ref liên service). Ghi APPROVE/REJECT/BAN/UNBAN/DISABLE/ENABLE/GRANT\_ROLE/REVOKE\_ROLE và mọi thao tác Admin; không ghi token/mật khẩu. |
| PasswordResetToken | Identity LOCAL auth | token\_hash UQ, user\_id FK User, expires\_at, used\_at | Token một lần; reset credential chung cho mọi vai trò; thu hồi phiên, không lưu token thô. |
| WorkerExecutionLog | Payment Finance và jobs | job\_key, reference\_id, status, retry\_count | Truy vết đối soát và retry. |
| IdempotencyRecord | API chống lặp | actor\_user\_id, operation\_context, endpoint, key, request\_hash, result\_ref, created\_at | UQ(actor\_user\_id, operation\_context, endpoint, key); ngữ cảnh CUSTOMER/SELLER/SERVICE không cấp quyền. Kiểm tra quyền trước trả kết quả cũ. |
| AuthSession | Identity | session\_id PK, user\_id FK User, refresh\_token\_hash UQ, expires\_at, revoked\_at, created\_at | Phiên chung cho các vai trò; xoay refresh token, thu hồi khi logout/reset/disable; cấp token mới từ quyền hiện hành. |
| SimulatedTransaction | Payment (bộ giả lập, giai đoạn 2\) | sim\_ref PK, kind PAYMENT/REFUND, idempotency\_key, amount\_vnd, scenario, status, complete\_at, created\_at | Lưu giao dịch giả lập có kết quả đến sau (TIMEOUT, LATE\_SUCCESS) để J2/J5 tra cứu hoặc bộ giả lập tự gọi xử lý callback (8.4). |
| Không dùng LoginIdentifier | Identity | UNIQUE(User.email), UNIQUE(User.google\_subject) khi khác NULL | Xóa registry tách loại tài khoản sau migration. AdminAccount cũng chuyển thành User \+ UserRole ADMIN. |

# **7 Đối chiếu thay đổi và điểm cần xác nhận**

| HẠNG MỤC | KẾT QUẢ HIỆN HÀNH BẢN 5.0 |
| :---- | :---- |
| Vai trò và đăng ký | User là danh tính chung; Customer/Seller kế thừa shared PK; User đa vai trò; Seller PENDING chưa được SELLER. |
| OrderSeller và SellerOrder | Loại bỏ toàn bộ entity cũ; chuyển seller\_id, trạng thái, thời hạn và xử lý tiền sang Order. |
| Checkout và Payment | Bổ sung từ điển CheckoutSession còn thiếu; quan hệ 1 checkout có N Order và tối đa 1 Payment. Bỏ UQ sai trên Order.checkout\_session\_id. |
| Hoàn và quyết toán | Refund theo Order hoặc FULL\_CHECKOUT; Settlement theo Order. Finance ghi pending lúc Payment SUCCESS, chuyển available khi Order CONFIRMED và giảm pending sau Refund SUCCESS; không thêm PaymentAllocation. |
| Search | Hoãn service và projection; LIKE hoặc ILIKE tại DB của Product Service và Identity theo đối tượng. |
| Reporting / báo cáo vi phạm | Reporting Service và ba projection vẫn tạm hoãn. Loại bỏ UC-C02 (Customer báo cáo Product/Seller), UC-E05 (Admin xử lý báo cáo từ Customer) và entity ViolationReport/Report; duy trì tra cứu vận hành qua API. |
| Cart (4.6) | Bỏ giỏ khách vãng lai theo FRD UC-B03: bỏ owner\_type, guest\_token\_hash, merged\_into\_cart\_id, expires\_at, trạng thái MERGED/EXPIRED và luồng merge; mỗi Customer đúng một giỏ. Thêm bảng trạng thái dòng giỏ (chuyển từ FRD 4.7). |
| Trạng thái Order (4.6) | Theo FRD: thêm PACKING, PAYMENT\_FAILED, EXPIRED; thêm packing\_at, confirmed\_by, cancelled\_by\_type, needs\_manual\_review. CheckoutSession.close\_reason thành enum PAYMENT\_FAILED / EXPIRED / CANCELLED\_BY\_CUSTOMER; thêm address\_id, paid\_at. |
| Mua ngay (4.6) | Thêm CheckoutSession.checkout\_source (CART / BUY\_NOW); source\_cart\_id và cart\_snapshot chỉ bắt buộc khi CART; OrderItem.source\_cart\_item\_id NULL với BUY\_NOW. |
| Tiếp nhận FRD mục 4 | Giữ quy tắc dữ liệu; địa chỉ 3.1.4, Identity/RBAC 3.1, bảng kỹ thuật mục 6\. AdminAccount chuyển vào User \+ ADMIN. |
| Bộ giả lập thanh toán (4.6) | Thanh toán/hoàn tiền qua PaymentGateway với bộ giả lập nội bộ, không có nhà cung cấp bên ngoài; provider \= SIMULATOR; thêm bảng kỹ thuật SimulatedTransaction; lộ trình 2 giai đoạn (8.4). |
| Hoàn tiền và số dư chờ (4.6) | Giữ quy tắc của bản 4.5: đơn bị hủy chỉ chuyển Settlement sang PENDING\_REFUND; số dư chờ chỉ bị trừ khi Refund SUCCESS (REFUND\_DEBIT). FRD v5.3 đã sửa các UC cho khớp. |
| Rút tiền (4.5) | Theo FRD v5.2: Withdrawal rút theo số tiền từ available của Wallet, hoàn tất tức thời trong một transaction, không có trạng thái. Bỏ entity Payout, bỏ reserved\_balance\_vnd và reserved\_delta\_vnd, bỏ các loại bút toán WITHDRAWAL\_RESERVED / PAYOUT\_COMPLETED / WITHDRAWAL\_RELEASED và reference\_type PAYOUT. Thêm Wallet.total\_withdrawn\_vnd, WalletTransaction.withdrawn\_delta\_vnd và withdrawal\_id, bút toán WITHDRAWAL\_COMPLETED; Withdrawal thêm withdrawal\_code, available\_before/after\_vnd, withdrawn\_at, đích nhận cố định "Ví ngoài giả lập". |
| Media và kiểm đếm | Giữ Product.media JSONB và 8 service; 29 entity bao gồm User và bốn bảng RBAC, Reporting tạm hoãn. |
| Tham chiếu và số mục | Đánh số lại liên tục, bỏ tham chiếu SellerOrder và allocation cũ trong quan hệ, ownership, bất biến và notification. |
| Đồng bộ danh sách xung đột (06/11/2026) | Đổi tên service Catalog → Product. Một tên cửa hàng là users.full\_name, bỏ seller\_name; bỏ sellers.contact\_phone, dùng users.phone\_number. pending\_balance\_vnd \= Σ Settlement PENDING \+ PENDING\_REFUND. CustomerAddress xóa vật lý. sellers.status độc lập approval\_status. Order thêm province\_name, ward\_name và tách cancel\_reason\_code / cancel\_reason\_note. Không lưu mã hiển thị đơn/phiên thanh toán và mã vận đơn: dùng UUID (đóng C-06). |
| Customer và Seller | Bỏ credential trùng ở Customer/Seller; khóa con bằng User.user\_id. Token, reset, notification, audit và actor dùng user\_id thống nhất; cập nhật API và kế hoạch migration ở 5.7–5.8. |

## **7.1 Các quyết định còn mở**

* Xác định thời điểm triển khai Reporting Service và các read model phân tích sau MVP, nếu có.  
    
* Chốt P-10 (số lượng tối đa mỗi dòng giỏ và số dòng giỏ).  
    
* MVP dùng bộ giả lập thanh toán nội bộ cho thanh toán và hoàn tiền; rút tiền là giao dịch giả lập tức thời. Nếu sau MVP chuyển rút tiền sang cổng chi trả thật, phải bổ sung trạng thái giao dịch rút, ngăn "đang rút", cơ chế đối soát/retry và chính sách đích nhận; nếu có đổi trả/chargeback sau Order CONFIRMED thì cần thêm thời gian tạm giữ và cơ chế xử lý số dư âm (WD-07, mục 8.3).  
    
* Bổ sung thông số hoa hồng P-COM-01, timeout đối soát P-04, retry thông báo P-19 và các P khác còn thiếu; không lấy ví dụ 5% làm giá trị mặc định chính thức.  
    
* Chốt các điều kiện thời gian khác cho khách hủy đơn nếu có. Email Seller REJECTED được giữ tại User; tái nộp dùng cùng seller\_id theo 3.1.3. Các mã UC cũ phải được cập nhật trong FRD/API Specs theo phiên bản này, không coi chúng là mô tả quyền đã đồng bộ ở tài liệu ngoài.

# **8 Quy tắc toàn cục, state machine và dòng tiền (chuyển từ FRD mục 4\)**

Mục 8 giữ quy tắc toàn cục, trạng thái và dòng tiền tiếp nhận từ FRD v5.3. Phiên bản 5.0 điều chỉnh Identity/RBAC tại 3.1, 5.1 và 5.7; khi mô tả cũ trong FRD hoặc API Specs còn tách ba tài khoản hay cấm User đa vai trò mua hàng thì cần sửa theo hợp đồng tại đây. Các quy tắc tiền/tồn và state machine vẫn là điều kiện bắt buộc sau kiểm tra quyền.

## **8.1 Quy tắc toàn cục (G-01 đến G-17)**

| MÃ | QUY TẮC | NƠI ÁP DỤNG |
| :---- | :---- | :---- |
| G-01 | Email chuẩn hóa duy nhất trong User; một User có nhiều hồ sơ/vai trò. Không tự liên kết GOOGLE/LOCAL trùng email. Seller REJECTED giữ User/email và nộp lại cùng seller\_id; tên cửa hàng (users.full\_name) chỉ bị kiểm tra trùng với Seller PENDING/APPROVED. | 3.1; 5.1; 5.8 |
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
| G-14 | Khóa có phạm vi: User DISABLED khóa toàn bộ vai trò, thu hồi phiên. Seller BANNED chỉ chặn quyền bán, đóng băng quyền rút, xử lý Order theo UC-E03; không chặn CUSTOMER hợp lệ. Customer BANNED có đơn đã trả tiền vẫn đăng nhập, xem hồ sơ/giỏ/đơn, hủy đủ điều kiện, xác nhận và review đơn cũ; cấm checkout và mọi thanh toán mới. Chưa có đơn trả tiền thì chặn API nghiệp vụ Customer, vẫn cho đăng nhập để dùng vai trò khác hoặc xem trạng thái tài khoản. Không dùng disable User để thay hạn chế Customer và không tự hủy đơn đã trả tiền. | 3.1; 5.7; UC-E03 cần đồng bộ |
| G-15 | Mọi thanh toán, hoàn tiền, số dư ví, hoa hồng, giao dịch rút tiền trong MVP là giả lập, do **bộ giả lập thanh toán nội bộ** xử lý (mục 8.4); không có nhà cung cấp bên ngoài. Nhãn "Giả lập — không phát sinh tiền thật" xuất hiện trên màn hình/email liên quan; không nhập thông tin thẻ, tài khoản ngân hàng hay chuyển khoản thật. | UC-B05, D05, G01, G02 |
| G-16 | "Mua ngay" (UC-B10) dùng chung mọi quy tắc của checkout từ giỏ (đăng nhập, SĐT, địa chỉ, giữ hàng, snapshot giá, G-08, G-09, G-14). Khác biệt duy nhất: checkout\_source \= BUY\_NOW, chỉ 1 sản phẩm với số lượng chọn trên trang sản phẩm (≥ 1, ≤ tồn kho khả dụng, ≤ P-10), và giỏ hàng không bị đọc, thêm hay trừ ở bất kỳ bước nào. | UC-B10; 3.6.1 |
| G-17 | Seller chỉ được rút từ số dư khả dụng; số dư chờ quyết toán không được rút, ứng trước hay dùng để bù. Mọi số dư ví ≥ 0 tại mọi thời điểm. Refund không bao giờ trừ số dư khả dụng hay số đã rút của Seller. Chi tiết ở 8.3. | UC-D05, G02; 3.5 |

## **8.2 State machine**

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
| PAID hoặc PACKING | Seller bị cấm, hoặc Seller/sản phẩm không còn hợp lệ ngay sau khi thu tiền | CANCELLED → Refund; Settlement PENDING\_REFUND |
| PACKING | Seller chọn "Xác nhận đã giao" trước hạn 7 ngày | DELIVERED (hiển thị: Đã giao — chờ khách xác nhận) |
| DELIVERED | Khách chọn "Đã nhận hàng", hoặc 7 ngày kể từ delivered\_at khách không phản hồi (J3) | CONFIRMED → Settlement SETTLED; số dư chờ → khả dụng |

Mốc 7 ngày thứ nhất: từ paid\_at (thời điểm thanh toán chung) đến hạn Seller xác nhận đã giao hoặc từ chối (seller\_action\_due\_at). Mốc thứ hai: từ delivered\_at của riêng Order đến hạn khách xác nhận (customer\_confirm\_due\_at). Hai bộ đếm độc lập, tính riêng từng Order. Không có chuyển trạng thái từ DELIVERED hoặc CONFIRMED sang CANCELLED — đây là cơ sở để tiền khả dụng không bao giờ phải thu hồi. CONFIRMED, CANCELLED, PAYMENT\_FAILED, EXPIRED là trạng thái kết thúc; không tái mở đơn khi nhận thanh toán trễ.

**C–J. Các đối tượng khác**

| ĐỐI TƯỢNG | VÒNG ĐỜI |
| :---- | :---- |
| C. Seller duyệt | PENDING → APPROVED (cấp SELLER; event tạo ví số dư 0\) / REJECTED; REJECTED → PENDING khi nộp lại cùng Seller, giữ lịch sử audit. status (ACTIVE/BANNED) độc lập với approval\_status: Admin cấm được ở cả PENDING/APPROVED/REJECTED; Seller BANNED được duyệt vẫn không hoạt động cho đến khi gỡ cấm (3.1.3). |
| D. Tài khoản và hồ sơ | User ACTIVE ⇄ DISABLED; Customer/Seller ACTIVE ⇄ BANNED độc lập, áp dụng G-14 và tăng authz\_version. |
| E. Product | SELLING ⇄ HIDDEN (Seller tự ẩn); REMOVED do Admin gỡ; chỉ Admin khôi phục về HIDDEN trong 30 ngày kể từ removed\_at. |
| F. Payment | PENDING → SUCCESS / FAILED. Trạng thái hoàn của Payment (chưa hoàn / hoàn một phần / hoàn toàn bộ) là giá trị tính từ các Refund SUCCESS, không lưu thành trạng thái riêng. |
| G. Refund | PENDING → SUCCESS / FAILED; FAILED → PENDING khi thử lại an toàn (cùng idempotency\_key, cùng bản ghi). |
| H. Reservation | HELD → COMMITTED (thanh toán thành công) / RELEASED (phiên đóng). |
| I. Settlement | PENDING → SETTLED (Order CONFIRMED); PENDING → PENDING\_REFUND (Order bị hủy sau thanh toán) → REFUNDED (Refund SUCCESS). |
| J. Withdrawal | Không có state machine: hoặc thành công ngay (tạo Withdrawal và bút toán trong cùng transaction), hoặc bị từ chối và không để lại bản ghi. |

## **8.3 Dòng tiền giả lập, hoa hồng và quy tắc ví (WD-01 đến WD-11)**

Với Order i: Gᵢ \= Order.total\_amount\_vnd; rᵢ \= commission\_bps\_snapshot (0–10.000). Hoa hồng Cᵢ \= floor((Gᵢ × rᵢ \+ 5.000) / 10.000), làm tròn nửa lên số nguyên VND, tính một lần trên tổng đơn; Seller nhận Sᵢ \= Gᵢ − Cᵢ. Tổng thanh toán T \= ΣGᵢ \= CheckoutSession.total\_amount\_vnd.

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
| WD-01 | Cần User ACTIVE, quyền withdrawal:create:own, Seller APPROVED và ACTIVE; chỉ rút ví có owner\_id \= seller\_id \= sub. |
| WD-02 | Chỉ rút số dư khả dụng; số dư chờ không được rút, không ứng trước, không cộng vào hạn mức rút. Tiền chỉ vào khả dụng khi Order CONFIRMED. |
| WD-03 | 10.000 VND (P-WD-01) ≤ A ≤ số dư khả dụng; A là số nguyên VND; không giới hạn số lần rút trong ngày. |
| WD-04 | Tức thời và nguyên tử: trong một transaction, cập nhật có điều kiện available ≥ A; available −A, total\_withdrawn \+A, tạo Withdrawal và WalletTransaction. Lỗi giữa chừng thì rollback toàn bộ; các lần rút đồng thời được tuần tự hóa. |
| WD-05 | Chống lặp: cùng idempotency\_key chỉ trừ ví một lần (G-09). |
| WD-06 | Hoàn tiền không chạm số dư khả dụng hay số đã rút: Refund luôn lấy từ phần chờ của đúng Order bị hủy. Mọi số dư ví ≥ 0; không phát sinh công nợ Seller với nền tảng trong MVP. |
| WD-07 | MVP không cho hủy/hoàn Order đã DELIVERED hoặc CONFIRMED. Nếu sau MVP bổ sung đổi trả sau nhận hàng thì phải thêm thời gian tạm giữ sau xác nhận và cơ chế xử lý số dư âm. |
| WD-08 | Admin không duyệt, không hủy, không đảo giao dịch rút; Admin chỉ xem ví và lịch sử rút (UC-E08). |
| WD-09 | Seller bị cấm: không rút được; số dư chờ và khả dụng giữ nguyên (đóng băng); gỡ cấm thì rút tiếp bình thường. |
| WD-10 | Ví nền tảng: hoa hồng đã chốt chỉ ghi sổ để báo cáo; MVP không có chức năng rút tiền của nền tảng. |
| WD-11 | Với mỗi Seller: Σ seller\_amount của Settlement SETTLED \= available \+ total\_withdrawn; Σ seller\_amount của Settlement PENDING và PENDING\_REFUND \= pending; total\_withdrawn \= Σ Withdrawal.amount\_vnd. |

## **8.4 Bộ giả lập thanh toán nội bộ và lộ trình hai giai đoạn**

MVP không dùng nhà cung cấp thanh toán bên ngoài. Payment Service gọi thanh toán và hoàn tiền qua interface PaymentGateway (charge, refund, query); cài đặt trong MVP là **SimulatedGateway** — bộ giả lập nội bộ trả về SUCCESS, FAILED hoặc PENDING theo kịch bản cấu hình. Khi cần cổng thật sau MVP, chỉ thêm một cài đặt mới của cùng interface; dữ liệu, trạng thái và API giữ nguyên.

| KỊCH BẢN | KẾT QUẢ BỘ GIẢ LẬP TRẢ VỀ | GIAI ĐOẠN |
| :---- | :---- | :---- |
| SUCCESS | Thanh toán / hoàn tiền thành công ngay | 1 (mặc định) |
| FAIL | Thanh toán bị từ chối rõ ràng | 2 |
| TIMEOUT | PENDING, không bao giờ tự có kết quả (kiểm thử J2/J2b) | 2 |
| LATE\_SUCCESS | PENDING, sau một khoảng cấu hình tự chuyển SUCCESS (kiểm thử thanh toán muộn, Refund FULL\_CHECKOUT) | 2 |
| REFUND\_FAIL | Hoàn tiền trả FAILED (kiểm thử J5 và UC-E08) | 2 |

Giai đoạn 1 chỉ cần kịch bản SUCCESS nhưng vẫn đi qua interface, trạng thái PENDING/SUCCESS/FAILED và event như thiết kế đầy đủ, để giai đoạn 2 chỉ thêm nhánh xử lý mà không phải sửa dữ liệu hay luồng chính. Kịch bản được chọn bằng cấu hình môi trường hoặc tham số chỉ bật ở môi trường dev/staging. Các giao dịch có kết quả đến sau được lưu ở bảng kỹ thuật SimulatedTransaction (mục 6); kết quả đến bằng cách bị tra cứu (J2/J5, Admin) hoặc bộ giả lập tự gọi hàm xử lý callback của Payment Service.

