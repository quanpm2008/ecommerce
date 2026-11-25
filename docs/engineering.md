# Quy ước kỹ thuật và kiểm chứng

## Authority và phạm vi

Baseline được người dùng chấp nhận: [SAD v1.1 §7.1/D.1](requirements/software-architecture.md).
Đây là tài liệu hướng dẫn thực hiện yêu cầu; không thêm guard, CI, dependency,
hook hoặc policy mới. Khi làm invariant, đọc [encoding-invariants](patterns/encoding-invariants.md)
và tìm accepted authority trước; các check tài liệu dưới đây không là merge protection.

Phần ban/J7, rút số dư nhỏ, provision Product và trigger sau thu được hiệu chỉnh bởi [quyết định Thẻ 15](requirements/coverage.md#the-15). Contract Order/Finance obligations tại [API §7.1–7.3](requirements/api_specs.md) thay phụ thuộc has-paid-order; không dùng snapshot lịch sử để xác định quyền. Đây là đặc tả, không xác nhận code đã theo contract mới.

Tham số nghiệp vụ hiện hành lấy từ [parameters.md](requirements/parameters.md), thay các số còn mở/khác giá trị trong hồ sơ cũ cùng chủ đề. Đây là yêu cầu cho triển khai, không phải bằng chứng code đã đáp ứng; không thay ADR, quote/pay, 7 service, phạm vi Should/Could hay phần phân tích đã hoãn.

## Công nghệ/library đã chốt và hiện trạng

Nguồn quy định: SAD §1.3, §2.2–2.4, §4.2, §6.4 và A.2. Phiên bản đang có trong
POM là quan sát implementation; không tự coi version đó là floor/chính sách nâng cấp.

| Mã | Chủ đề | Baseline đã chấp nhận | Quan sát / điểm cần review |
|---|---|---|---|
| <a id="req-eng-001"></a>REQ-ENG-001 | Backend | Java 21, Spring Boot, Maven; MVC+JPA/JDBC cho service nghiệp vụ | Identity POM Boot 4.1.2; schema identity/Flyway/JPA validate; nhiều service khác còn scaffold |
| <a id="req-eng-002"></a>REQ-ENG-002 | Gateway | Spring Cloud Gateway WebFlux; không blocking JDBC/OpenFeign trên event loop | Đã chuyển 20/11/2026: `spring-cloud-starter-gateway-server-webflux` trên Netty, không còn WebMVC/servlet; security/route/filter reactive; kiểm tra end-to-end Docker 21/11/2026 |
| <a id="req-eng-003"></a>REQ-ENG-003 | Frontend | Một React SPA, TypeScript/Vite/Tailwind; chung phiên các vùng | Chưa thấy module SPA trong tree được khảo sát |
| <a id="req-eng-004"></a>REQ-ENG-004 | Đồng bộ | REST/OpenFeign trong service nghiệp vụ; reactive client ở Gateway | Không dùng trực tiếp repository/entity của service khác |
| <a id="req-eng-005"></a>REQ-ENG-005 | Bất đồng bộ | Kafka + local Outbox/Inbox và business dedupe | Root Compose chưa có Kafka; Identity publisher chưa cấu hình, không coi event đã giao |
| <a id="req-eng-006"></a>REQ-ENG-006 | Dữ liệu | PostgreSQL 16, service sở hữu dữ liệu; versioned SQL migration | Dev chung database ecommerce, Identity schema identity; không suy ra đã cách ly DB role |
| <a id="req-eng-007"></a>REQ-ENG-007 | Tổ chức | Package by layer; Controller→Service→Repository | Notification theo yêu cầu người dùng 25/11/2026: controller/service/repository/model, một module/process/schema; client/security/config hỗ trợ biên. Identity hiện theo package tính năng, không refactor ngoài scope |
| <a id="req-eng-008"></a>REQ-ENG-008 | Abstraction | Interface ở biên thay thế thật như PaymentGateway/EmailAdapter; không buộc Service/ServiceImpl | Không thêm generic repository hoặc thư viện common chứa toàn nghiệp vụ; helper dùng lại cần task cụ thể |
| <a id="req-eng-009"></a>REQ-ENG-009 | Phạm vi hạ tầng | AWS theo baseline, simulator nội bộ; không thêm service ngoài 7 nghiệp vụ+Gateway+Discovery | Topology AWS/Kafka/storage/backup và version broker cụ thể còn review |
| <a id="req-eng-010"></a>REQ-ENG-010 | Thành phần ngoài phạm vi | Không CQRS toàn hệ thống, Event Sourcing, service mesh, Kubernetes, Redis khi chưa có yêu cầu chứng minh cần | Đổi dependency/kiến trúc là task riêng, cần authority tương ứng |

## Ownership và transaction

Nguồn: [Database §4.2](../business/database.md), SAD §2.1/6.5–6.7.

| Service | Dữ liệu sở hữu / được ghi |
|---|---|
| Identity | User, Customer, Seller, Address, RBAC, session/reset, audit và outbox của mình |
| Product | Category, Product, Review (Should); không Inventory |
| Cart | Cart/CartItem; không Order/Reservation |
| Order | CheckoutSession, Order/OrderItem, Inventory/Reservation/StockMovement |
| Payment | Payment/Refund; không ghi Order.status/Settlement |
| Finance | Settlement/Wallet/WalletTransaction/Withdrawal; không ghi Payment/Order |
| Notification | Sở hữu thông báo in-app Must và khóa chống trùng; email nghiệp vụ tùy chọn, triển khai sau theo [quyết định 25/11/2026](requirements/coverage.md#notification-channels) |

Không FK/join/ghi DB vượt ownership. Chỉ local transaction; gọi HTTP/simulator
ngoài transaction ghi dữ liệu, giữ ý định bền vững và event cùng commit nguồn.
Consumer áp dụng dữ liệu và dấu đã xử lý cùng transaction của mình. Không gộp
Refund/Order/Settlement vào transaction xuyên service. Tên lớp/worker và bảng
kỹ thuật chi tiết trong phụ lục SAD là đề xuất, không bắt code tạo đúng tên.

Các giới hạn phải được thực thi ở service sở hữu, không chỉ ở frontend:

- Finance cung cấp hoa hồng chung 5% = 500 bps; Order snapshot khi checkout, Finance dùng snapshot và làm tròn nửa lên một lần trên tổng đơn, không tra Category hay tính lại đơn cũ.
- Payment: mỗi chu kỳ Refund có một lần đầu + tối đa 3 retry sau 1/5/15 phút từ lỗi trước; chỉ gửi lại lỗi rõ ràng, query PENDING/timeout không tính lượt. Hết lượt báo khách; chỉ khách đã thanh toán mở chu kỳ mới, cùng Refund/số tiền/key và giữ lịch sử, cho ORDER/FULL_CHECKOUT. Admin không retry thay khách; chỉ SUCCESS mới giảm pending.
- Notification: in-app Must giữ 50 gần nhất/30 ngày, đọc/xóa đúng owner và không mất dedupe khi xóa/hết hạn; không phụ thuộc email adapter để hoạt động. Email nghiệp vụ triển khai sau; khi có, giữ một lần đầu + tối đa 3 retry 1/5/15 phút cho lỗi tạm thời, tôn trọng Retry-After, dừng lỗi vĩnh viễn/token hết hạn, giữ cùng key và không rollback nghiệp vụ. Retention in-app không xóa email/lịch retry hay áp dụng cho secret.
- Identity: reset một lần/30 phút, không khóa tạm do sai mật khẩu (vẫn chống lạm dụng); tối đa 3 địa chỉ hiện có. Nạp địa giới tĩnh vào DB Identity, hai cấp tỉnh/thành và phường/xã/đặc khu, không quận/huyện; tra cứu/validation dùng cùng dataset có nguồn/version, không gọi ngoài. Order snapshot mã và tên.
- Product seed đúng 10 ngành hàng ở P-21e với UUID, không hoa hồng theo Category. Product tối đa 9 tệp/2 video, mô tả 5.000 ký tự; Review Should tối đa 5 tệp/1 video, nội dung 1.000 ký tự. Cart tối đa 50 dòng, 1–99 đơn vị/dòng; thêm trùng cộng dồn không vượt 99, BUY_NOW tối đa 99 và không tạo dòng giỏ.

P-01 vẫn tạm 15 phút/chưa chốt; không suy dung lượng/định dạng/thời lượng media từ số tệp. N-09 và N-11 giữ nguyên. P-37 chốt ngày 19/11/2026: tìm tên sản phẩm và tên cửa hàng không phân biệt dấu tiếng Việt và hoa thường. Chuẩn hóa cả từ khóa và tên để so khớp bằng cách bỏ dấu, đổi `đ`/`Đ` thành `d`, chuyển về chữ thường; giữ nguyên tên lưu và hiển thị. Với cùng bộ lọc, `dien thoai`, `Điện Thoại` và `ĐIỆN THOẠI` cho cùng kết quả. Giữ baseline PostgreSQL unaccent/lowercase tại SAD A.6.1; lựa chọn index trong OQ-006 vẫn cần đo đạc và review, không thêm Search Service. Lịch job/HTTP budget/lease khác lịch retry nghiệp vụ đã chốt. Các quan sát implementation bên dưới, kể cả FakeRegionProvider, không phải xác nhận yêu cầu mới đã được triển khai; chưa xác nhận kiểm thử runtime đáp ứng P-37.

| Mã nguồn | Quy tắc đã xác nhận | Nguồn |
|---|---|---|
| <a id="g-09"></a>G-09 | Khóa chống lặp: tạo checkout dùng khóa phiên; Payment dùng key theo CheckoutSession; Refund dùng key ổn định theo (payment_id, order_id) hoặc key hoàn toàn bộ phiên; Withdrawal dùng key do client gửi, duy nhất theo Seller; mỗi WalletTransaction có business_event_key duy nhất. Cùng key \+ cùng nội dung → trả kết quả cũ; cùng key, nội dung khác → 409. Tổng hoàn không vượt số đã thu. | [§8.1](../business/database.md), L961 |
| <a id="g-11"></a>G-11 | Mọi chuyển trạng thái CheckoutSession, Order, Payment, Refund, Settlement, mọi giao dịch rút tiền, mọi bút toán ví và tác động tồn kho dùng cập nhật có điều kiện trong transaction. Gọi bộ giả lập thanh toán (hoặc cổng thật sau này) ngoài transaction và lưu ý định bền vững; callback/job chạy lặp không thu/hoàn trùng. | [§8.1](../business/database.md), L963 |

## API, bảo mật và quản lý thay đổi

- [API §3.3–3.10](requirements/api_specs.md): success envelope `data`, list thêm meta,
  error envelope `error`; status/error code/field validation/version/idempotency
  phải lấy đúng contract. JWKS riêng không bọc data (SAD §6.1).
- `X-Request-Id` hợp lệ được Identity echo/MDC/error; thiếu hoặc unsafe thì
  không tự sinh ID. Không mô tả mọi log đều có ID khi caller gọi trực tiếp.
  Hiện trạng: RequestIdFilter và WebContractTests; baseline gateway tạo/chuyển
  ID theo API §3.10, chưa có bằng chứng toàn đường Gateway.
- Không log token/password/resetUrl/presigned URL/credential. Refresh cookie,
  CSRF/CORS và service allowlist: xem identity REQ-IDN-006/007, SAD §6.1–6.4.
- [Quyết định 0003](decisions/0003-mot-phien-refresh-tren-user.md): đặc tả mới chỉ có một phiên hiện hành trên User (`refresh_token_hash`, `refresh_token_expires_at`, `session_id`), không lưu raw token; giữ dấu reuse và cookie/Origin theo 0001. Login mới thay phiên trước; validator kiểm tra session_id hiện hành để chặn cả access token cũ. Code và Supabase vẫn dùng AuthSession; task tài liệu này không chạy migration. Khi triển khai phải chuyển mọi caller và bỏ AuthSession, không giữ hai nguồn phiên hiệu lực.
- Service credential là bắt buộc; định dạng JWT ký bất đối xứng/header/rotation
  cụ thể trong SAD A.3 còn đề xuất. Không tự thay token chuỗi dev bằng giao thức mới.
- Đổi API/schema/event phải đồng bộ consumer và test; DELTA-01…09 nằm trong
  [coverage](requirements/coverage.md#delta). Không thêm endpoint/bảng chỉ từ tên trong SAD.
- Không sửa migration đã áp dụng; thêm migration và xem tương thích data/API
  trong task code riêng. Nguồn: identity-service/identity-service.md “Persistence”,
  SAD A.8/C.5; tài liệu này không chạy migration.

## Lập phạm vi cho một thay đổi

1. Từ docs/README chọn hồ sơ, đọc UC/rule và ngoại lệ; kiểm tra coverage cho
   supersede/TBD. Ghi rõ authority của hành vi thay đổi.
2. Đọc implementation và mọi caller/consumer bị ảnh hưởng, DTO/public/internal
   views, schema/migration, event, fixtures, tests và API artifacts. Phân biệt
   “chưa tìm thấy” với “đã tồn tại nhưng khác yêu cầu”.
3. Chọn proof: unit cho quy tắc thuần; PostgreSQL/Testcontainers cho constraint,
   transaction/concurrency; contract cho DTO/event/token; Kafka thật cho giao nhận;
   E2E cho Gateway/UI. Không dùng mock/happy path để kết luận toàn hệ thống đúng.
4. Với policy còn mở, dừng phần phụ thuộc và hỏi quyết định tối thiểu; không
   chốt số mặc định. Báo test đã chạy và giới hạn bằng chứng riêng.

Nguồn chiến lược test: SAD B.1/B.2; quy trình: [WORKFLOW](WORKFLOW.md).

## Lệnh validation hiện có và tác dụng phụ

Chạy ứng dụng theo [identity-service/RUN.md](../identity-service/RUN.md), hoặc
[README gốc](../README.md) cho whole stack. RUN dẫn DEVELOPMENT.md chưa tồn tại;
dùng prerequisites ghi ngay trong RUN: Docker Compose v2, Java 21/Maven 3.8+
khi chạy Java ở host. Không trộn hai Compose cùng cổng/project mà chưa kiểm tra ownership.
README gốc còn mô tả chưa bật persistence; với Identity phải đối chiếu POM/Flyway
thực tế. README/RUN không được sửa trong công việc chuyển đổi này.

Lệnh được RUN tài liệu hóa, từ module identity-service:

```bash
cd identity-service
mvn test
```

Lệnh chọn lớp sau là cách chạy tập trung theo tên test hiện có, chưa chạy trong
lượt tài liệu này; dùng khi task đã cho phép chạy test/container:

```bash
mvn -Dtest=AuthEndpointsTests,SellerReviewEndpointsTests test
mvn -Dtest=AddressEndpointsTests test
```

EndpointTestSupport import PostgreSQL/SeaweedFS Testcontainers, FakeRegionProvider,
FakeOrderClient và clock test. Maven có thể tải dependency, tạo target/cache,
khởi chạy container. OpenApiDocsTests ghi `openapi.json`/`api.html` trong module
khi nội dung đổi: phải xem diff, không sửa tay artifact sinh ra để che contract.
Identity không có `mvnw` trong tree khảo sát; không đưa lệnh `./mvnw` giả định.

Notification đã có kiểm chứng PostgreSQL/HTTP/Kafka độc lập, xem [hồ sơ Notification](requirements/notifications.md); không suy ra toàn bộ UC/AT hoặc producer đã đạt. Các module khác cần đối chiếu code/test hiện hành. Chưa xác minh CI hay branch protection cho thay đổi này; local test không phải enforcement CI.

## AT liên service có đầu mối kỹ thuật

| Mã | Tình huống | Bằng chứng cần có | Nguồn |
|---|---|---|---|
| <a id="at-21"></a>AT-21 | Event lặp ID và khác ID cùng nghĩa vụ | Một mutation kho/Settlement/ledger; không chỉ dựa eventId | [B.2](requirements/software-architecture.md), L1002 |
| <a id="at-24"></a>AT-24 | Restart và migration/restore diễn tập | Key và giao dịch còn dùng được; không mất ý định hoặc tác động lặp | [B.2](requirements/software-architecture.md), L1005 |

Kiểm tra tài liệu có thể chạy độc lập, không Maven/Docker: dùng block Python
trong [coverage](requirements/coverage.md#kiem-tra). Lượt chuyển đổi chỉ chạy kiểm tra
tĩnh đó và so hash protected files; chưa chạy test ứng dụng hay AT runtime.
