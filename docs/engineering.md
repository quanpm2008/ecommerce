# Quy ước kỹ thuật và kiểm chứng

## Authority và phạm vi

Baseline được người dùng chấp nhận: [SAD v1.1 §7.1/D.1](../business/Software-Architecture.md).
Đây là tài liệu hướng dẫn thực hiện yêu cầu; không thêm guard, CI, dependency,
hook hoặc policy mới. Khi làm invariant, đọc [encoding-invariants](patterns/encoding-invariants.md)
và tìm accepted authority trước; các check tài liệu dưới đây không là merge protection.

## Công nghệ/library đã chốt và hiện trạng

Nguồn quy định: SAD §1.3, §2.2–2.4, §4.2, §6.4 và A.2. Phiên bản đang có trong
POM là quan sát implementation; không tự coi version đó là floor/chính sách nâng cấp.

| Mã | Chủ đề | Baseline đã chấp nhận | Quan sát / điểm cần review |
|---|---|---|---|
| <a id="req-eng-001"></a>REQ-ENG-001 | Backend | Java 21, Spring Boot, Maven; MVC+JPA/JDBC cho service nghiệp vụ | Identity POM Boot 4.1.2; schema identity/Flyway/JPA validate; nhiều service khác còn scaffold |
| <a id="req-eng-002"></a>REQ-ENG-002 | Gateway | Spring Cloud Gateway WebFlux; không blocking JDBC/OpenFeign trên event loop | gateway-service/pom.xml còn Gateway Server WebMVC cùng WebFlux; cần task riêng, chưa sửa |
| <a id="req-eng-003"></a>REQ-ENG-003 | Frontend | Một React SPA, TypeScript/Vite/Tailwind; chung phiên các vùng | Chưa thấy module SPA trong tree được khảo sát |
| <a id="req-eng-004"></a>REQ-ENG-004 | Đồng bộ | REST/OpenFeign trong service nghiệp vụ; reactive client ở Gateway | Không dùng trực tiếp repository/entity của service khác |
| <a id="req-eng-005"></a>REQ-ENG-005 | Bất đồng bộ | Kafka + local Outbox/Inbox và business dedupe | Root Compose chưa có Kafka; Identity publisher chưa cấu hình, không coi event đã giao |
| <a id="req-eng-006"></a>REQ-ENG-006 | Dữ liệu | PostgreSQL 16, service sở hữu dữ liệu; versioned SQL migration | Dev chung database ecommerce, Identity schema identity; không suy ra đã cách ly DB role |
| <a id="req-eng-007"></a>REQ-ENG-007 | Tổ chức | Package by layer; Controller→Service→Repository | Identity hiện theo nhiều package tính năng; không refactor chỉ vì chuyển tài liệu |
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
| Notification | Dữ liệu delivery thuộc service; in-app Should theo contract nguồn |

Không FK/join/ghi DB vượt ownership. Chỉ local transaction; gọi HTTP/simulator
ngoài transaction ghi dữ liệu, giữ ý định bền vững và event cùng commit nguồn.
Consumer áp dụng dữ liệu và dấu đã xử lý cùng transaction của mình. Không gộp
Refund/Order/Settlement vào transaction xuyên service. Tên lớp/worker và bảng
kỹ thuật chi tiết trong phụ lục SAD là đề xuất, không bắt code tạo đúng tên.

| Mã nguồn | Quy tắc đã xác nhận | Nguồn |
|---|---|---|
| <a id="g-09"></a>G-09 | Khóa chống lặp: tạo checkout dùng khóa phiên; Payment dùng key theo CheckoutSession; Refund dùng key ổn định theo (payment_id, order_id) hoặc key hoàn toàn bộ phiên; Withdrawal dùng key do client gửi, duy nhất theo Seller; mỗi WalletTransaction có business_event_key duy nhất. Cùng key \+ cùng nội dung → trả kết quả cũ; cùng key, nội dung khác → 409. Tổng hoàn không vượt số đã thu. | [§8.1](../business/database.md), L961 |
| <a id="g-11"></a>G-11 | Mọi chuyển trạng thái CheckoutSession, Order, Payment, Refund, Settlement, mọi giao dịch rút tiền, mọi bút toán ví và tác động tồn kho dùng cập nhật có điều kiện trong transaction. Gọi bộ giả lập thanh toán (hoặc cổng thật sau này) ngoài transaction và lưu ý định bền vững; callback/job chạy lặp không thu/hoàn trùng. | [§8.1](../business/database.md), L963 |

## API, bảo mật và quản lý thay đổi

- [API §3.3–3.10](../business/api.md): success envelope `data`, list thêm meta,
  error envelope `error`; status/error code/field validation/version/idempotency
  phải lấy đúng contract. JWKS riêng không bọc data (SAD §6.1).
- `X-Request-Id` hợp lệ được Identity echo/MDC/error; thiếu hoặc unsafe thì
  không tự sinh ID. Không mô tả mọi log đều có ID khi caller gọi trực tiếp.
  Hiện trạng: RequestIdFilter và WebContractTests; baseline gateway tạo/chuyển
  ID theo API §3.10, chưa có bằng chứng toàn đường Gateway.
- Không log token/password/resetUrl/presigned URL/credential. Refresh cookie,
  CSRF/CORS và service allowlist: xem identity REQ-IDN-006/007, SAD §6.1–6.4.
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

Các module còn lại chỉ có context-load test hiện tại; không đồng nghĩa UC/AT
đã đạt. Chưa tìm thấy CI checked-in; branch protection không được khảo sát.
Không tuyên bố check tài liệu local là enforcement CI.

## AT liên service có đầu mối kỹ thuật

| Mã | Tình huống | Bằng chứng cần có | Nguồn |
|---|---|---|---|
| <a id="at-21"></a>AT-21 | Event lặp ID và khác ID cùng nghĩa vụ | Một mutation kho/Settlement/ledger; không chỉ dựa eventId | [B.2](../business/Software-Architecture.md), L1002 |
| <a id="at-24"></a>AT-24 | Restart và migration/restore diễn tập | Key và giao dịch còn dùng được; không mất ý định hoặc tác động lặp | [B.2](../business/Software-Architecture.md), L1005 |

Kiểm tra tài liệu có thể chạy độc lập, không Maven/Docker: dùng block Python
trong [coverage](requirements/coverage.md#kiem-tra). Lượt chuyển đổi chỉ chạy kiểm tra
tĩnh đó và so hash protected files; chưa chạy test ứng dụng hay AT runtime.
