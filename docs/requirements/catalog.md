# Sản phẩm, tìm kiếm và quản lý tồn

Baseline: SAD v1.1 §7.1/D.1, được người dùng chấp nhận ngày 11/11/2026.
[Quy ước/thuật ngữ](README.md) · [Coverage và câu hỏi](coverage.md) · [Kỹ thuật/validation](../engineering.md).

Đọc đúng UC dưới đây, quy tắc bổ sung và hàng AC liên quan; chỉ mở mục contract nguồn khi cần DTO/cột/endpoint chi tiết. Yêu cầu TO-BE không chứng minh code đã làm đúng.

[UC-B01](#uc-b01) · [UC-C01](#uc-c01) · [UC-C02](#uc-c02) · [UC-D01](#uc-d01) · [UC-D03](#uc-d03) · [UC-E04](#uc-e04) · [UC-E05](#uc-e05) · [UC-E07](#uc-e07) · [UC-F01](#uc-f01)

## Phạm vi sửa và bằng chứng hiện trạng

Khảo sát tĩnh ngày 11/11/2026; mọi test nêu dưới đây **chưa chạy**.

| Phạm vi | Code owner hiện có | Test đã tìm thấy | Giới hạn |
|---|---|---|---|
| B01/D01/E04 | [product-service/src/main/java/com/example/product_service/ProductTestController.java](../../product-service/src/main/java/com/example/product_service/ProductTestController.java) | [product-service/src/test/java/com/example/product_service/ProductServiceApplicationTests.java](../../product-service/src/test/java/com/example/product_service/ProductServiceApplicationTests.java) | Chỉ endpoint test và context-load; không có Product schema/logic nghiệp vụ. |
| D03, Inventory | [order-service/src/main/java/com/example/order_service/OrderServiceApplication.java](../../order-service/src/main/java/com/example/order_service/OrderServiceApplication.java) | [order-service/src/test/java/com/example/order_service/OrderServiceApplicationTests.java](../../order-service/src/test/java/com/example/order_service/OrderServiceApplicationTests.java) | Chỉ scaffold Order; chưa có logic tồn/migration/test invariant. |

Khi lập task, lần theo caller/consumer và artifacts trong cùng hàng, rồi xem dependency liên service dưới đây; không đổi các source gốc để che lệch implementation.

## Contract và dữ liệu cần tra cứu

- [§3.2, §5.2, §8.2E](../../business/database.md): field, required/conditional, unique/FK/CHECK và ownership. Bảng nguồn là thiết kế, không khẳng định migration tồn tại.
- [§7.1–7.3](../../business/api.md): provider/consumer allowlist, event payload và jobs. [§7.2](../../business/Software-Architecture.md): DELTA cần review.
- Public paths bên dưới là phần sau `/api/v1`; internal dùng `/internal/v1`. Base URL mẫu trong nguồn không chứng minh cổng service hiện tại. Tên path là khóa contract ổn định; quyền ở bảng vẫn phải kết hợp trạng thái/ownership.
- **Bảng contract nguồn, chưa phải bằng chứng triển khai.** Endpoint Should vẫn giữ tham chiếu; luồng checkout cũ và stats/dashboard bị baseline hiệu chỉnh không được triển khai nguyên xi. DTO, body/error examples và required/optional giữ tại đúng mục nguồn, không suy ra từ tên endpoint.

[§6.2 / danh sách endpoint](../../business/api.md), L1144–1168

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

## Quy tắc bổ sung và source hiệu lực

| Mã nguồn | Quy tắc đã xác nhận | Nguồn |
|---|---|---|
| <a id="g-06"></a>G-06 | Sản phẩm bán được khi Product SELLING, Seller APPROVED và ACTIVE, User của Seller ACTIVE và UserRole SELLER chưa thu hồi. Product Service/search/checkout xác minh cùng policy, không chỉ tin status sản phẩm. | [§8.1](../../business/database.md), L958 |

Quy tắc chung G-09/G-11 ở [engineering](../engineering.md); G-10/G-13/G-15 ở [quy ước chung](README.md).

| Mã | Điều khoản | Nguồn / phân loại |
|---|---|---|
| <a id="req-cat-001"></a>REQ-CAT-001 | Product có một Seller, một Category, một giá VND >0; không SKU/variant. Category phẳng một cấp, tên unique; không xóa danh mục được Product tham chiếu. Danh mục nền cần seed dù UI CRUD là Should. | Yêu cầu baseline; [§3.2.1–3.2.2](../../business/database.md), L243–279 |
| <a id="req-cat-002"></a>REQ-CAT-002 | Media có ít nhất một ảnh/video, lưu metadata/object_key và thứ tự, không lưu binary/presigned URL. Backend kiểm tra loại thực tế; P-08 chưa chốt. Cover là ảnh đầu tiên, chỉ video thì placeholder; MVP không transcode video. | Yêu cầu baseline; [§3.2.2](../../business/database.md), L258–279 |
| <a id="req-cat-003"></a>REQ-CAT-003 | Khởi tạo Product HIDDEN và ý định provision tồn bền vững; gọi Order sau commit, idempotent theo productId; timeout giữ HIDDEN/retry cùng ID. Chỉ bán sau xác nhận Inventory, không tự bật nếu Seller bị cấm/Product bị gỡ khi chờ. | Yêu cầu baseline; [§5.7](../../business/Software-Architecture.md), L580–590 |
| <a id="req-cat-004"></a>REQ-CAT-004 | Search Product tại DB Product, Seller tại Identity; kiểm tra đủ G-06. Baseline SAD §6.7 quy định PostgreSQL unaccent/lowercase và tham số hóa, không Search Service; thay giới hạn case-insensitive của Database §5.2/API C-08. Contract và test tìm không dấu vẫn phải đồng bộ; phương án index/normalization chi tiết ở A.6 còn review. | Yêu cầu baseline; [§6.7 thay Database §5.2/API C-08](../../business/Software-Architecture.md), L687–704; [§5.2](../../business/database.md), L783–788; [§9 / C-08](../../business/api.md), L2885 |

## Acceptance criteria để triển khai/kiểm chứng

Các AC sau là diễn giải kiểm chứng từ điều khoản cùng mã UC/REQ/G/WD; chưa là kết quả test. Tham số TBD chỉ kiểm tra biên sau khi giá trị được chốt. Mỗi dòng gộp một tình huống; triển khai test phải tách assertions và cả nhánh lỗi của UC.

| Mã AC | Liên kết yêu cầu | Given | When | Then |
|---|---|---|---|---|
| <a id="ac-cat-001"></a>AC-CAT-001 | UC-B01; G-06; REQ-CAT-004 | Product SELLING; các tổ hợp Seller/User/role hợp lệ và không hợp lệ | Search tên với chữ hoa/thường, có/không dấu; mở detail | Kết quả theo unaccent/lowercase baseline, đủ G-06 và không lộ sản phẩm không bán được; dữ liệu tồn hiển thị không thay quyền giữ tồn Order. Contract/test tìm không dấu chưa được triển khai không phải lý do bỏ yêu cầu. |
| <a id="ac-cat-002"></a>AC-CAT-002 | UC-D01; REQ-CAT-001/002/003 | Seller hoạt động có danh mục hợp lệ, media thật | Tạo/sửa Product; provision tồn timeout | Giá nguyên dương, một giá, media ≥1; chỉ owner sửa; timeout chưa SELLING; retry cùng productId; sửa giá không đổi snapshot đơn cũ. |
| <a id="ac-cat-003"></a>AC-CAT-003 | UC-D03; G-04 | on_hand≥reserved và version hiện hành | Nhập số âm/lẻ/<reserved hoặc version cũ | Từ chối, không ghi đè; update hợp lệ có lịch sử; test tranh checkout không oversell. |
| <a id="ac-cat-004"></a>AC-CAT-004 | UC-E04 | Product REMOVED, ngày gỡ xác định | Seller tự bật; Admin khôi phục trong/sau 30 ngày | Seller không khôi phục; Admin đúng hạn chỉ về HIDDEN, quá hạn từ chối; đơn đã trả tiền không tự đổi tiến độ. |

### AT nguồn thuộc hồ sơ này

Các AT bao phủ nhiều service; hồ sơ này là đầu mối đọc, không có nghĩa chỉ sửa một module. Đọc owner khác từ coverage khi task chạm biên. **Chưa có bằng chứng chạy AT.**

| Mã | Tình huống | Bằng chứng cần có | Nguồn |
|---|---|---|---|
| <a id="at-20"></a>AT-20 | Tạo Product nhưng Inventory timeout | Product HIDDEN, retry cùng ID, không bán trước khi tồn sẵn sàng | [B.2](../../business/Software-Architecture.md), L1001 |

## Chênh lệch, đề xuất và câu hỏi còn mở

- SAD §6.7 đã quy định unaccent/lowercase; API C-08 và Database §5.2 chưa đồng bộ. Đây là gap contract/implementation, không còn câu hỏi có hỗ trợ tìm không dấu hay không. Phương án index chi tiết trong A.6 vẫn review; chưa có migration/test cho Product.
- Inventory thuộc Order, không thêm field tồn chính thức hoặc repository Order vào Product để tránh gọi HTTP.
- P-08 media, P-10 số lượng, P-11 paging, P-12 review còn tham số; không hardcode ví dụ dev. Review/Category CRUD/gợi ý chỉ ranh giới Should/Could.

## Đặc tả UC và ngoại lệ


<a id="uc-b01"></a>
## UC-B01 — Tìm kiếm và xem sản phẩm

Nguồn: [§6 / UC-B01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L509–536. Vai trò: Khách hàng (kể cả chưa đăng nhập) · Ưu tiên: M.

Các điều khoản dưới đây là yêu cầu nguồn đã đối chiếu baseline; nhánh Google/in-app ghi Should không thuộc DoD Must. Mã clause giữ ổn định khi di chuyển tài liệu.

| Mã điều khoản | Loại | Yêu cầu / điều kiện / ngoại lệ | Nguồn hiệu lực |
|---|---|---|---|
| <a id="uc-b01.goal-01"></a>UC-B01.GOAL-01 | Mục tiêu | Khách tìm kiếm, lọc và xem thông tin sản phẩm phù hợp nhu cầu. | [UC-B01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L513 |
| <a id="uc-b01.pre-01"></a>UC-B01.PRE-01 | Điều kiện | Không bắt buộc đăng nhập. | [UC-B01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L515 |
| <a id="uc-b01.main-01"></a>UC-B01.MAIN-01 | Luồng chính | Khách mở trang sản phẩm hoặc nhập tên cần tìm. | [UC-B01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L519 |
| <a id="uc-b01.main-02"></a>UC-B01.MAIN-02 | Luồng chính | Hệ thống hiển thị các sản phẩm còn được phép bán, hỗ trợ lọc theo danh mục, khoảng giá, sắp xếp và phân trang. | [UC-B01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L521 |
| <a id="uc-b01.main-03"></a>UC-B01.MAIN-03 | Luồng chính | Khách chọn sản phẩm để xem giá, hình ảnh/video, mô tả, trạng thái còn hàng và thông tin người bán; từ trang chi tiết có thể chọn số lượng rồi “Thêm vào giỏ hàng” (UC-B03) hoặc “Mua ngay” (UC-B10). | [UC-B01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L523 |
| <a id="uc-b01.alt-01"></a>UC-B01.ALT-01 | Ngoại lệ | Không có kết quả: hệ thống hiển thị thông báo phù hợp. | [UC-B01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L527 |
| <a id="uc-b01.alt-02"></a>UC-B01.ALT-02 | Ngoại lệ | Sản phẩm hết hàng: vẫn có thể xuất hiện kèm nhãn “Hết hàng”; khách có thể đưa vào giỏ nhưng chưa được thanh toán; nút “Mua ngay” bị vô hiệu. | [UC-B01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L529 |
| <a id="uc-b01.alt-03"></a>UC-B01.ALT-03 | Ngoại lệ | Sản phẩm không có ảnh tĩnh: hiển thị ảnh minh họa mặc định. | [UC-B01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L531 |
| <a id="uc-b01.alt-04"></a>UC-B01.ALT-04 | Ngoại lệ | Sản phẩm vừa bị ẩn/gỡ trong lúc khách xem: khi thêm giỏ, hệ thống báo sản phẩm không còn bán. | [UC-B01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L533 |
| <a id="uc-b01.post-01"></a>UC-B01.POST-01 | Hậu điều kiện | Danh sách không hiển thị sản phẩm của người bán chưa được duyệt hoặc bị cấm; tìm kiếm không phân biệt dấu tiếng Việt. | [UC-B01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L535 |

<a id="uc-c01"></a>
## UC-C01 — Đánh giá sản phẩm

Nguồn: [§6 / UC-C01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L841–870. Vai trò: Khách hàng · Ưu tiên: S.

Khách đánh giá sản phẩm đã nhận từ người bán.

Should: chỉ Customer có OrderItem thuộc Order CONFIRMED, đúng product, unique Customer/Product; rating 1–5, nội dung/media tùy chọn; không sửa review. Chưa đặc tả đầy đủ AC/UI Should.

<a id="uc-c02"></a>
## UC-C02 — Báo cáo sản phẩm/Seller vi phạm — ĐÃ LOẠI BỎ (v5.2)

Nguồn: [§6 / UC-C02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L871–874. Đã loại bỏ; mã giữ chỗ.

Đã loại bỏ: không nút/API/dữ liệu báo cáo vi phạm. Giữ chỗ UC-C02; Admin chủ động kiểm tra qua E03/E04.

<a id="uc-d01"></a>
## UC-D01 — Quản lý sản phẩm

Nguồn: [§6 / UC-D01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L879–910. Vai trò: Người bán · Ưu tiên: M.

Các điều khoản dưới đây là yêu cầu nguồn đã đối chiếu baseline; nhánh Google/in-app ghi Should không thuộc DoD Must. Mã clause giữ ổn định khi di chuyển tài liệu.

| Mã điều khoản | Loại | Yêu cầu / điều kiện / ngoại lệ | Nguồn hiệu lực |
|---|---|---|---|
| <a id="uc-d01.goal-01"></a>UC-D01.GOAL-01 | Mục tiêu | Người bán tạo và cập nhật sản phẩm thuộc gian hàng của mình. | [UC-D01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L883 |
| <a id="uc-d01.pre-01"></a>UC-D01.PRE-01 | Điều kiện | Người bán đã được duyệt và tài khoản đang hoạt động. | [UC-D01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L885 |
| <a id="uc-d01.main-01"></a>UC-D01.MAIN-01 | Luồng chính | Người bán mở “Sản phẩm của tôi”, chọn thêm mới hoặc một sản phẩm hiện có. | [UC-D01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L889 |
| <a id="uc-d01.main-02"></a>UC-D01.MAIN-02 | Luồng chính | Người bán nhập tên, giá, mô tả, danh mục, tồn kho và tối thiểu một ảnh hoặc video. | [UC-D01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L891 |
| <a id="uc-d01.main-03"></a>UC-D01.MAIN-03 | Luồng chính | Hệ thống kiểm tra các thông tin bắt buộc, giá và số lượng hợp lệ, định dạng/giới hạn tệp. | [UC-D01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L893 |
| <a id="uc-d01.main-04"></a>UC-D01.MAIN-04 | Luồng chính | Người bán lưu sản phẩm hoặc bật/tạm ẩn bán; hệ thống cập nhật hiển thị của sản phẩm cho khách. | [UC-D01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L895 |
| <a id="uc-d01.alt-01"></a>UC-D01.ALT-01 | Ngoại lệ | Sản phẩm đã bị quản trị viên gỡ: người bán xem lý do nhưng không được sửa hoặc bật bán lại. | [UC-D01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L899 |
| <a id="uc-d01.alt-02"></a>UC-D01.ALT-02 | Ngoại lệ | Giá thay đổi: chỉ áp dụng cho lượt mua mới, không làm thay đổi giá trong các đơn trước đó. | [UC-D01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L901 |
| <a id="uc-d01.alt-03"></a>UC-D01.ALT-03 | Ngoại lệ | Người bán đặt tồn kho thấp hơn số hàng đang được giữ cho khách: hệ thống từ chối và cho biết số lượng tối thiểu có thể nhập. | [UC-D01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L903 |
| <a id="uc-d01.alt-04"></a>UC-D01.ALT-04 | Ngoại lệ | Ảnh/video không hợp lệ hoặc việc tải lên bị hủy: hệ thống không lưu sản phẩm ở trạng thái dở dang. | [UC-D01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L905 |
| <a id="uc-d01.alt-05"></a>UC-D01.ALT-05 | Ngoại lệ | Sản phẩm bị ẩn trong khi có trong giỏ khách: giỏ được cập nhật khi khách mở lại. | [UC-D01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L907 |
| <a id="uc-d01.post-01"></a>UC-D01.POST-01 | Hậu điều kiện | Người bán chỉ quản lý sản phẩm của mình; sản phẩm phải có ít nhất một ảnh hoặc video, còn video hiển thị được trên trình duyệt theo P-08. | [UC-D01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L909 |

<a id="uc-d03"></a>
## UC-D03 — Cập nhật tồn kho

Nguồn: [§6 / UC-D03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L945–972. Vai trò: Người bán, Hệ thống · Ưu tiên: M.

Các điều khoản dưới đây là yêu cầu nguồn đã đối chiếu baseline; nhánh Google/in-app ghi Should không thuộc DoD Must. Mã clause giữ ổn định khi di chuyển tài liệu.

| Mã điều khoản | Loại | Yêu cầu / điều kiện / ngoại lệ | Nguồn hiệu lực |
|---|---|---|---|
| <a id="uc-d03.goal-01"></a>UC-D03.GOAL-01 | Mục tiêu | Người bán theo dõi và điều chỉnh số lượng hàng hiện có. | [UC-D03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L949 |
| <a id="uc-d03.pre-01"></a>UC-D03.PRE-01 | Điều kiện | Người bán đang hoạt động và chọn sản phẩm thuộc mình. | [UC-D03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L951 |
| <a id="uc-d03.main-01"></a>UC-D03.MAIN-01 | Luồng chính | Người bán mở mục “Tồn kho”, xem số lượng hiện tại và số đang được giữ cho các đơn chờ thanh toán. | [UC-D03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L955 |
| <a id="uc-d03.main-02"></a>UC-D03.MAIN-02 | Luồng chính | Khi nhập hàng hoặc kiểm kê, người bán nhập số lượng tồn kho mới. | [UC-D03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L957 |
| <a id="uc-d03.main-03"></a>UC-D03.MAIN-03 | Luồng chính | Hệ thống kiểm tra số lượng hợp lệ và không thấp hơn lượng hàng đã giữ cho khách. | [UC-D03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L959 |
| <a id="uc-d03.main-04"></a>UC-D03.MAIN-04 | Luồng chính | Hệ thống lưu số lượng mới và ghi nhận lịch sử điều chỉnh. | [UC-D03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L961 |
| <a id="uc-d03.main-05"></a>UC-D03.MAIN-05 | Luồng chính | Khi thanh toán chung thành công, hệ thống trừ kho theo từng đơn; nếu một đơn hủy hợp lệ, chỉ hoàn lại hàng của đơn đó. | [UC-D03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L963 |
| <a id="uc-d03.alt-01"></a>UC-D03.ALT-01 | Ngoại lệ | Nhập số âm, số lẻ hoặc nhỏ hơn lượng đang giữ: hệ thống từ chối. | [UC-D03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L967 |
| <a id="uc-d03.alt-02"></a>UC-D03.ALT-02 | Ngoại lệ | Có thay đổi kho từ thiết bị khác trong khi người bán đang sửa: hệ thống yêu cầu tải lại trước khi lưu. | [UC-D03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L969 |
| <a id="uc-d03.post-01"></a>UC-D03.POST-01 | Hậu điều kiện | Tồn kho không âm, có lịch sử mọi biến động và không bị trừ/hoàn lặp cho cùng giao dịch. | [UC-D03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L971 |

<a id="uc-e04"></a>
## UC-E04 — Gỡ / khôi phục sản phẩm

Nguồn: [§6 / UC-E04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1141–1168. Vai trò: Quản trị viên · Ưu tiên: M.

Các điều khoản dưới đây là yêu cầu nguồn đã đối chiếu baseline; nhánh Google/in-app ghi Should không thuộc DoD Must. Mã clause giữ ổn định khi di chuyển tài liệu.

| Mã điều khoản | Loại | Yêu cầu / điều kiện / ngoại lệ | Nguồn hiệu lực |
|---|---|---|---|
| <a id="uc-e04.goal-01"></a>UC-E04.GOAL-01 | Mục tiêu | Quản trị viên gỡ sản phẩm vi phạm hoặc khôi phục khi còn thời hạn. | [UC-E04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1145 |
| <a id="uc-e04.pre-01"></a>UC-E04.PRE-01 | Điều kiện | Quản trị viên đã đăng nhập và chọn một sản phẩm. | [UC-E04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1147 |
| <a id="uc-e04.main-01"></a>UC-E04.MAIN-01 | Luồng chính | Quản trị viên chọn “Gỡ sản phẩm”, nhập lý do và xác nhận. | [UC-E04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1151 |
| <a id="uc-e04.main-02"></a>UC-E04.MAIN-02 | Luồng chính | Hệ thống ngừng hiển thị sản phẩm cho khách, thông báo lý do cho người bán và lưu lịch sử xử lý. | [UC-E04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1153 |
| <a id="uc-e04.main-03"></a>UC-E04.MAIN-03 | Luồng chính | Các giỏ chứa sản phẩm được cập nhật khi khách mở lại; các phiên checkout chưa thanh toán bị kiểm tra lại trước khi thu tiền và không được tiếp tục mua sản phẩm đã gỡ. | [UC-E04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1155 |
| <a id="uc-e04.main-04"></a>UC-E04.MAIN-04 | Luồng chính | Nếu đủ điều kiện, quản trị viên chọn “Khôi phục”; hệ thống đưa sản phẩm về trạng thái “Tạm ẩn” để người bán tự bật bán lại. | [UC-E04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1157 |
| <a id="uc-e04.alt-01"></a>UC-E04.ALT-01 | Ngoại lệ | Sản phẩm đã bị gỡ: thao tác gỡ thêm không tạo hiệu ứng mới. | [UC-E04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1161 |
| <a id="uc-e04.alt-02"></a>UC-E04.ALT-02 | Ngoại lệ | Quá 30 ngày kể từ khi gỡ: không cho khôi phục; lịch sử sản phẩm vẫn được lưu. | [UC-E04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1163 |
| <a id="uc-e04.alt-03"></a>UC-E04.ALT-03 | Ngoại lệ | Đơn đã thanh toán trước khi sản phẩm bị gỡ: không tự động thay đổi tiến độ đơn; lịch sử giữ thông tin sản phẩm. | [UC-E04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1165 |
| <a id="uc-e04.post-01"></a>UC-E04.POST-01 | Hậu điều kiện | Việc gỡ có hiệu lực đối với lượt mua mới ngay lập tức; người bán không thể tự khôi phục sản phẩm bị gỡ. Việc phát hiện vi phạm do Admin chủ động rà soát (không có kênh báo cáo từ Customer từ v5.2). | [UC-E04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1167 |

<a id="uc-e05"></a>
## UC-E05 — Xử lý báo cáo vi phạm — ĐÃ LOẠI BỎ (v5.2)

Nguồn: [§6 / UC-E05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1169–1172. Đã loại bỏ; mã giữ chỗ.

Đã loại bỏ cùng C02; không danh sách xử lý báo cáo. Không dùng lại UC-E05 cho chức năng khác.

<a id="uc-e07"></a>
## UC-E07 — Quản lý danh mục

Nguồn: [§6 / UC-E07](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1203–1230. Vai trò: Quản trị viên · Ưu tiên: S.

Quản trị viên duy trì danh sách danh mục sản phẩm.

Should: CRUD Category Admin một cấp ở Product; không thêm service. Must vẫn cần seed danh mục và kiểm tra danh mục hợp lệ khi tạo Product. Không xóa danh mục đang được tham chiếu.

<a id="uc-f01"></a>
## UC-F01 — Gợi ý sản phẩm liên quan

Nguồn: [§6 / UC-F01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1263–1284. Vai trò: Khách hàng · Ưu tiên: C.

Khách khám phá các mặt hàng phù hợp với sản phẩm đang xem.

Could: điểm mở rộng truy vấn Product liên quan; không có engine/service gợi ý trong MVP. Không biến mục này thành task triển khai Must.
