# Đồng bộ tài liệu Thẻ 15 — thực thi và bản vá remote

Ngày khởi tạo: 17/11/2026; tiếp tục ngày 18/11/2026. Trạng thái: Blocked remote; repository và bản vá bàn giao đã hoàn tất, không có bằng chứng lưu thay đổi trên Drive/Figma.

## Kết quả cần đạt
Đồng bộ ban/unban theo nghĩa vụ, UC-D01 HIDDEN/provision và trigger Order áp dụng Payment SUCCESS trong tài liệu hiệu lực repository và các tài sản Drive/Figma liên quan. Không sửa code/migration/generated OpenAPI; không tuyên bố runtime pass.

## Authority
[Coverage — quyết định Thẻ 15](../../requirements/coverage.md#the-15). Kế hoạch được người dùng duyệt ngày 17/11/2026; quyết định thay thế riêng các quy tắc mâu thuẫn thuộc ba mục Thẻ 15, không thay toàn bộ baseline SAD.

## Thứ tự thực hiện
1. Ghi authority và xác minh quyền truy cập.
2. FRD → Database/API: contract accountStatus suy ra từ User/profile; nghĩa vụ hiện tại, J7, deadline 30 ngày, rút toàn bộ số dư nhỏ; HIDDEN/provision và postcapture trigger.
3. Lan truyền hồ sơ/kiến trúc/UI/đồ thị; giữ bản nguồn lịch sử và hiện trạng code.
4. Đối chiếu B1–B11, P1–P2, O1–O3; kiểm tra link/hash và ghi rõ giới hạn remote.

## Tiến độ
- Remote: các lần mở browser/relay không vào được editing context; không có text tab hay tài sản thiết kế nào được xác nhận đã sửa. Không suy ra thiếu quyền Google Docs từ nút Đăng nhập. Bằng chứng lỗi và bản vá từng section/frame ở dưới.
- FRD/Database/API đã hoàn tất contract và đối chiếu 16 ca B1–B11/P1–P2/O1–O3 ở mức đặc tả. Các hồ sơ Identity/Finance/Checkout/Catalog/Payments đã đồng bộ và kiểm tra ngữ nghĩa bởi owner; không chạy code.
- Authority đã ghi và xác minh tại coverage #the-15. Các bản nguồn lịch sử giữ nguyên; ba nguồn hiệu lực được phân quyền theo file để tránh ghi đè lẫn nhau. Chưa thay đổi Java/migration/generated OpenAPI.
- Figma wireframe/mockup: lần khảo sát trước bấm Edit file menu mở “Sign up for Figma”; drawio Drive chỉ có viewer/download/print và Đăng nhập. Chưa ghi tài sản thiết kế. Lượt tiếp tục: mở Chromium thất bại do cài browser timeout; mở browser own cũng timeout. Không coi đây là từ chối quyền Google Docs.

## Quyết định và recovery
Giữ nguyên các tài liệu nguồn lịch sử docs/api.md, docs/database.md và FRD v5.4; chỉ sửa nguồn hiệu lực. Remote không ghi được phải có nội dung bàn giao từng section/frame tại file này; không tạo bản sao Drive/đổi sharing. Task chưa hoàn tất remote nếu chỉ có bản vá bàn giao.

## Kiểm chứng và kết quả — 18/11/2026

- FRD/Database/API owners đối chiếu 16 ca B1–B11, P1–P2, O1–O3; Identity/Finance và Order/Catalog/Payments owners đối chiếu clause/AC/dependencies. Đây là semantic review đặc tả, không executable runtime test.
- Hoàn tất các sửa đổi còn lại tại SAD/System Architecture, Notification, thuật ngữ/quyền/index/engineering và chú thích độ lệch trong bốn tài liệu implementation Identity. Không thay checkbox/code/đồ thị lớp thành tính năng mới.
- Chạy tìm kiếm các mẫu policy cũ theo kế hoạch; giữ lại provenance lịch sử, snapshot giao dịch và các câu phủ định policy cũ. Không suy ra “còn từ scope” là sai khi đó là Refund scope/service permission hay lịch sử.
- Kiểm tra coverage gốc **FAIL**: `FileNotFoundError: business/UET FRD v5.4 - dong bo API DB.md`. Không sửa validator để giả pass; không dọn nguồn lịch sử ngoài phạm vi.
- Kiểm tra link có nhận diện heading Markdown: 561 explicit ID duy nhất; giữ đủ 37 UC, 17 G, 11 WD, 26 AT. Có 656 dẫn legacy `business/` được tách ra và không tuyên bố đã sửa. Ngoài chúng, một tham chiếu implementation chưa giải quyết: `docs/requirements/catalog.md` → `product-service/src/main/java/com/example/product_service/ProductTestController.java` không tồn tại; vùng khảo sát lịch sử 11/11, không thuộc thay đổi Thẻ 15.
- Đã sửa link mới Database §7.1 trỏ nhầm heading API; kiểm tra cuối **PASS** các local link của bản vá remote và SHA-256 của cả 5 nguồn hiện hành trong manifest coverage.
- Không chạy Java/Maven/Docker, migration hoặc UAT. Không có screenshot sau sửa Figma/drawio vì không ghi được tài sản. Các export remote chỉ chứng minh nội dung đã đọc, không chứng minh đã cập nhật.
- Repository và nội dung bàn giao đã hoàn tất phần truy cập được. Phần remote giữ Blocked/active: cần editing context hoạt động cho Google Docs và quyền chỉnh sửa tài sản thiết kế, áp dụng bản vá rồi export/screenshot để đóng. Không tự đăng nhập, thay sharing hoặc tạo bản sao tài sản.

## Bản vá remote — thiết kế chưa áp dụng

Giữ link tài sản gốc; chưa có frame/node nào được tuyên bố đã sửa. Bản vá dưới đây là nội dung/nhánh cần áp dụng trong tài sản, không thay bằng chứng lưu và kiểm tra hình. Node ID phải lấy từ file thực tế, không đoán.

### Activity Diagram

Nguồn: [UET_Cloud_Activity_Diagrams_v5.3.drawio](https://drive.google.com/file/d/1-XyyKVxbCHiZ8LJxr-twZo0VDicuAkrk/view). XML khảo sát có 19 trang, không có UC-D05; không tự tạo trang mới.

| Trang / nhánh hiện tại | Thay bằng hoạt động và đường nối |
|---|---|
| UC-A03: “khách bị cấm chưa từng thanh toán”, chặn mọi Seller BANNED | Sau xác thực đúng accountType, đánh giá nghĩa vụ hiện tại. Nguồn lỗi → 503, không tạo phiên; ACTIVE hợp lệ → FULL; DISABLED đã xác minh hết nghĩa vụ → ACCOUNT_DISABLED; BANNED → phiên allowlist theo accessMode, chỉ vào đơn/ví liên quan. Không có nhánh “đã từng thanh toán”. Seller PENDING/REJECTED vẫn từ chối login. |
| UC-A03: “Khóa nút Thanh toán, vẫn xem giỏ/đơn cũ” | “Chỉ xem/thao tác hợp lệ trên nghĩa vụ của mình; không giỏ, checkout, thanh toán mới, review hoặc toàn bộ lịch sử”. Đường đến Customer đơn/phiên nghĩa vụ hoặc Seller đơn/ví theo accessMode, không đưa phiên giới hạn tới dashboard chung. |
| UC-E03: nhánh cấm | Admin chọn tài khoản → kiểm tra không ADMIN/Seller phải APPROVED → đọc banImpact → IV.P1 nhập lý do/cảnh báo → Hủy quay lại, Xác nhận đọc lại nghĩa vụ → nguồn lỗi 503 không ghi một phần → có nghĩa vụ BANNED / đã xác minh hết DISABLED → ghi cặp User/profile + authzVersion + audit/Outbox nguyên tử → trả trạng thái thực tế và thông báo. Bỏ chọn scope, snapshot lịch sử và mọi cạnh ban→hủy đơn/đóng băng ví. |
| UC-E03: nhánh gỡ | ACTIVE → thành công idempotent; BANNED → cho gỡ; DISABLED → decision “now <= restoreUntil?”; Có → ACTIVE/xóa mốc hiệu lực, giữ audit; Không → 409 ACCOUNT_RESTORE_WINDOW_EXPIRED, không đổi dữ liệu/tiền. Không nối gỡ cấm tới khôi phục đơn/Product tự động. |
| UC-E03: nhánh hệ thống thêm vào cùng trang | J7 Identity hoặc login/refresh → đọc nguồn nghĩa vụ ngoài transaction → nguồn lỗi giữ trạng thái/retry → xác minh version/đợt cấm → BANNED hết nghĩa vụ chuyển DISABLED, DISABLED có nghĩa vụ muộn chuyển BANNED giới hạn; không đổi ACTIVE/không tự unban. Giữ disabledAt/restoreUntil lần đầu trong đợt; conflict với Admin unban không ghi đè. |
| UC-D01: “Lưu sản phẩm; cập nhật hiển thị” | Tách tạo mới/cập nhật. Validation/media lỗi trước nhận → báo form, không lưu Product. Tạo mới hợp lệ → Product HIDDEN + công việc provision bền vững → sau commit Order tạo Inventory idempotent cùng productId. Timeout → giữ HIDDEN/retry cùng ID; xác nhận tồn → Seller/Product còn hợp lệ? Có áp dụng trạng thái yêu cầu; Không không bật SELLING (REMOVED giữ REMOVED). Cập nhật sản phẩm sẵn có theo rule hiện hành, không đi lại nhánh tạo tồn. |
| UC-B06: “Order hợp lệ? Seller không bị cấm, sản phẩm còn bán” | “Order xác minh được điều kiện giao dịch tại thời điểm thu?” sau Payment SUCCESS qua handler chung event/đối soát. Lỗi/thiếu bằng chứng → công việc xác minh/retry, không refund do timeout; invalid đã chứng minh → chỉ Order PAID/PACKING liên quan CANCELLED/Refund. Ban Seller sau thu không phải invalid dù event trễ; không suy thứ tự từ status hiện tại. |
| UC-B06: hủy/hoàn riêng đơn | Giữ Payment SUCCESS, đủ checkout.paid/order.cancelled; không chạm Order seller khác hoặc DELIVERED/CONFIRMED. Transition/inbox/business dedupe ngăn lặp SOLD/RESTOCK/Refund/ledger; pending chỉ giảm sau Refund SUCCESS. |
| UC-G02: nguồn hoàn có “Seller bị cấm” | “Order hủy hợp lệ: Customer hủy PAID, Seller từ chối, J4 quá hạn hoặc Order xác minh invalid giao dịch theo UC-B06”. Giữ FULL_CHECKOUT cho success đến muộn của phiên đóng, không trộn ORDER/FULL_CHECKOUT; không giảm pending trước refund thành công. |
| UC-B05 | Giữ chặn thanh toán mới khi BANNED, khóa nút lúc Payment PENDING và luồng đối soát hiện hành. Dẫn UC-B06 xác minh sau thu, không thêm lần charge. |

### Nội dung frame Figma wireframe và mockup

[Wireframe](https://www.figma.com/design/qhP1ewQegG4q8KcupeXTT2/-UET--Cloud-Project---Wireframes?node-id=1-4) · [Mockup](https://www.figma.com/design/NfetH0lkCmQduzsuJiS1Py/-UET--Cloud-Project---UI-Design?node-id=0-1).
Áp dụng cùng nội dung cho frame cùng mã trên hai file, giữ component/layout. Nếu thiếu frame, ghi thiếu frame trong bảng trạng thái, không ghi đã cập nhật.

| Mã màn | Nội dung/trạng thái thay thế |
|---|---|
| I.3/I.4 | Tài khoản theo đúng kênh; sai loại báo lỗi đăng nhập chung. Seller PENDING/REJECTED không login. BANNED vào “Tài khoản bị hạn chế — chỉ giải quyết nghĩa vụ còn lại”; DISABLED không có nghĩa vụ không tạo phiên. Nguồn nghĩa vụ lỗi: “Chưa thể xác minh quyền truy cập. Vui lòng thử lại.”, không hiển thị zero/khôi phục FULL. |
| II.6 | Không đưa phiên BANNED vào giỏ; không chỉ khóa mỗi nút Thanh toán mà vẫn cho dùng giỏ như mô tả cũ. Public xem sản phẩm không là quyền mua authenticated. |
| II.8/II.9 | Phiên BANNED chỉ liệt kê đơn/phiên đang có nghĩa vụ đúng chủ, kể cả Order CANCELLED đang hoàn FAILED/PENDING; không toàn bộ lịch sử. Nút Hủy/Xác nhận chỉ theo trạng thái bình thường. Ẩn review mới và sửa profile ngoài allowlist. |
| II.9b | BANNED khóa Thanh toán, không tạo charge mới. AWAITING_PAYMENT vẫn theo quy tắc hủy/J1/J2/J2b; Payment đang xác nhận khóa Thanh toán/Hủy, không suy timeout là FAILED. Hiển thị kết quả/hoàn liên quan nghĩa vụ. |
| III.2/III.3 | Chỉ Order còn nghĩa vụ của shop; giữ giao/từ chối và deadline 7 ngày, không tự hủy do ban. Không thấy shop khác. Ban sau thu không khóa nút xử lý hợp lệ trên đơn tồn. |
| III.4/III.4a | Khi tạo hợp lệ nhưng đợi Inventory: “Tạm ẩn — đang khởi tạo tồn kho. Sản phẩm chưa được bán.” Timeout giữ cùng Product, không gợi tạo lại. Thành công chỉ bật trạng thái yêu cầu khi còn hợp lệ; ban/REMOVED trong lúc đợi không tự publish. |
| III.8 | Bỏ “Seller bị cấm không vào được trang/ví đóng băng”. Chỉ pending: thông tin nghĩa vụ chỉ đọc; available được rút. Luôn ghi “Giả lập — không phát sinh tiền thật”. available=9.500 và BANNED: nút “Rút toàn bộ 9.500đ”; ACTIVE cùng số dư: “Chưa đủ số dư tối thiểu để rút (10.000đ)”. Pending không rút. |
| III.P3 | BANNED có 0<available<10.000: amount cố định toàn bộ available, không cho rút một phần; thông báo “Được rút toàn bộ số dư còn lại khi tài khoản bị hạn chế.” Ngoài ngoại lệ giữ ngưỡng 10.000; validation server/quyền/số dư được kiểm tra lại. |
| III.P4 | Hiện giao dịch rút, số dư còn lại, nhãn giả lập. Khi hết nghĩa vụ: “Tài khoản sẽ chuyển sang vô hiệu hóa sau khi hệ thống xác minh không còn nghĩa vụ.” Không tuyên bố DISABLED đã lưu từ một response rút tiền. |
| IV.2 | Tài khoản riêng theo Customer/Seller; hiển thị accountStatus ACTIVE/BANNED/DISABLED và approvalStatus Seller riêng. Không suy có nhiều hồ sơ trên cùng User. |
| IV.3 | Hiển thị lý do, bannedAt, disabledAt, restoreUntil. BANNED gỡ bất kỳ lúc nào; DISABLED đúng hạn có “Gỡ cấm”, quá hạn vô hiệu và “Đã quá hạn khôi phục 30 ngày của đợt cấm này”. Không hiển thị giao diện lịch sử audit mới chỉ vì có timestamp. |
| IV.P1 | Tiêu đề “Cấm tài khoản”; loại tài khoản; lý do bắt buộc; có/không đơn, Payment, Refund dở; Seller thêm pending/available. Bỏ scope. “Trạng thái sau xác nhận: BANNED — chỉ xử lý nghĩa vụ” hoặc “DISABLED — chặn đăng nhập, được gỡ trong 30 ngày từ thời điểm vô hiệu hóa”. Hai nút “Hủy” và “Xác nhận cấm”. |
| IV.P1 lỗi/xung đột | Không tải được nghĩa vụ: khóa xác nhận, báo chưa xác minh, không hiển thị số 0 giả. Preview chỉ snapshot: POST kiểm tra lại; nếu kết quả đổi phải hiển thị trạng thái thực tế. Không tự gửi lệnh ban từ việc mở popup. |

### Kiểm tra hình bắt buộc khi có quyền ghi

Mở lại từng trang drawio và frame đã sửa ở zoom đọc được: đường nhánh nguồn lỗi không tới refund/DISABLED; nhãn deadline, số dư 9.500 và popup không tràn. Export/đọc lại text không thay screenshot tài sản thiết kế. Hiện chưa có bằng chứng hình sau sửa vì chưa áp dụng được remote.

## Bản vá remote — Google Docs text chưa áp dụng

Document gốc: `1VukChbA8p9eOU_loe7y_RVCFvdkzIeMDQCWBoUn2nRI`. Mở URL `https://docs.google.com/document/d/1VukChbA8p9eOU_loe7y_RVCFvdkzIeMDQCWBoUn2nRI/edit?tab=<ID>`; export kiểm chứng dùng `/export?format=txt&tab=<ID>`.

Lượt tiếp tục đã đọc đúng 13 export: FRD, Database, API, BRD, Parameter, UI, Gateway, ba Conflicts, Thẻ 15, Plan và System. System chỉ BOM. Không có export “sau sửa” vì chưa ghi được. `browser.targets({app:'relay',timeout:10})` báo `Listing browser targets timed out after 10000ms`; mở Google Docs với app auto/timeout 20 báo `Browser open timed out after 19999.996733000036ms while opening tab "remote-text"`. Đây là lỗi tiếp cận editing context, không bằng chứng thiếu quyền Google Docs. Không tab nào đã thay đổi.

### Ba nguồn chính: thay section đích bằng section repository hiệu lực

Thay từng paragraph/cell/section cùng mã bên dưới, giữ styles/bảng/comment. Không nhập toàn bộ file/export vào document nhiều tab; không bỏ định dạng rich text để dán Markdown thô. Các câu hiện hành trái contract phải thay, không chỉ thêm note cuối tab. Nhật ký cũ giữ như lịch sử.

| Thẻ / ID | Đích thay và nguồn replacement chính xác |
|---|---|
| FRD `t.z7ee2ts69665` | [FRD hiệu lực](../../requirements/FRD.md): UC-A03, UC-E03, UC-D05, UC-G03, UC-D01, UC-B06, UC-G02 lấy toàn subsection cùng mã; heading UC-E03 bỏ “theo phạm vi”. §2 Actor/§5 quyền lấy các hàng account/ban; phần hướng dẫn “04 Những điều phải nắm”, công thức/tiền thay câu ban và ngưỡng rút bằng câu tương ứng nguồn. |
| FRD — các ngoại lệ consumer | Trong UC-B03/B04/B05/B07/B08/B09/B10/C01/D02/D03/D04/E01/E02/E08/G01 thay đúng paragraph quyền ban/notification/withdrawal cùng UC từ nguồn; không chép luồng khác. §9 thêm nguyên “Ca nghiệm thu Thẻ 15” B1–B11/P1–P2/O1–O3. |
| Database `t.np00hnkiu90` | [Database hiệu lực](../../requirements/database.md): §3.1.1/3.1.2/3.1.3 lấy từng subsection User/Customer/Seller (không kéo địa chỉ vào); các hàng RBAC/§5 quyền account/order/product/wallet liên quan ban thay bằng nguồn cùng tên. Wallet/Withdrawal lấy hàng quyền/amount/ràng buộc tương ứng, giữ ledger schema khác; §5.4 lấy toàn subsection UC-D05. |
| Database — rule/state | §8.1 thay hàng G-14, §8.2B thay một hàng invalid-after-capture và chú giải dưới bằng nguồn; §8.2D thay hàng D/chú giải; §8.3 thay WD-01/03/09. RefundReason SELLER_CANCEL lấy chú giải nguồn, giữ literal enum. Không giữ scope hoặc hai trục cấm độc lập như rule hiện hành. |
| API `t.ysygluyhdxdg` | [API hiệu lực](../../requirements/api_specs.md): §3.2/3.2.1 auth/G-14/authorization; §4.1 account/restore/withdrawal errors; §4.2 accessMode/account enum; §6.1 ban/unban và DTO auth/UserSelf/CustomerAdminView/SellerAdminView; login/google/refresh/users-me và Admin detail thay paragraph trạng thái/response/errors theo nguồn. Không tạo endpoint preview public. |
| API — consumer/finance | §6.2 create Product giữ provision và dẫn UC-D01; §6.3/6.4/6.5 thay paragraph quyền bị cấm và state cancellation tương ứng nguồn; §6.6 thay Wallet, wallet GET và withdrawals POST theo nguồn gồm allowBelowMinimumFullWithdrawal/canWithdraw/reason. |
| API — liên service | §7.1 thay has-paid-order bằng Order/Finance obligations và authorization-context theo nguồn; §7.1.2 lấy nguyên subsection; §7.2 thay hàng customer/seller ban/unban và user disabled/enabled; §7.3 thêm J7 nguồn. §8.1/8.2 thay paragraph trigger/cancel/refund; §10.3 IDs 21/23/25/27, §10.4/10.5 dùng hàng Thẻ 15 trong nguồn, giữ comment gốc như lịch sử. |

Literal kiểm soát khi chép: ban `{reason: string 1..1000}`, unban `{reason?: string <=1000}`; scope gửi lên →400. Cả hai response 200 `data={userId,accountType,accountStatus,userStatus,customerStatus|null,sellerStatus|null,accessMode,bannedAt|null,disabledAt|null,restoreUntil|null,authzVersion}`; ban thêm obligations. Unban quá hạn 409 ACCOUNT_RESTORE_WINDOW_EXPIRED; Seller chưa duyệt 409 INVALID_STATE_TRANSITION; ban nguồn lỗi 503 không ghi một phần. accountStatus suy ra: ACTIVE=(User ACTIVE, profile ACTIVE), BANNED=(ACTIVE,BANNED), DISABLED=(DISABLED,BANNED). Các mốc TIMESTAMPTZ ở User, không thêm cột accountStatus.

### BRD — t.2s7rnjrns26s

Thay bullet Tài khoản §2.1:
> Tài khoản: đăng ký/đăng nhập bằng email và mật khẩu; Google (Should) cho Customer. Mỗi tài khoản chỉ có một loại Customer, Seller hoặc Administrator. Một người muốn mua và bán phải dùng tài khoản độc lập; quyền không cộng gộp.

Thay paragraph đầu §3:
> Customer, Seller và Administrator sử dụng tài khoản độc lập. Admin được cấp nội bộ, không đăng ký công khai. Cấm một tài khoản không tự tác động tài khoản khác của cùng người.

Thay nội dung BR-01, giữ mã/priority/trace:
> Đăng ký và đăng nhập theo loại tài khoản độc lập Customer/Seller/Administrator. Không bổ sung vai trò mua/bán vào tài khoản loại khác; Google dành cho Customer ở mức Should.

Thay BR-12:
> Admin cấm/gỡ cấm tài khoản, gỡ/khôi phục sản phẩm. Tài khoản bị cấm còn nghĩa vụ giữ quyền giới hạn để giải quyết; hết nghĩa vụ đã xác minh tự chuyển DISABLED. Bảo toàn đơn, hoàn tiền và tiền Seller; ban không tự hủy đơn hoặc đóng băng available. BANNED gỡ bất kỳ lúc nào; DISABLED chỉ gỡ trong 30 ngày từ lần DISABLED đầu đợt cấm. Quá hạn không xóa tài khoản/tiền.

Giữ nhật ký v4.0 như lịch sử, thêm dòng “17/11/2026 — Thẻ 15 thay riêng mô hình đa vai trò/cấm theo phạm vi và quyền giải quyết nghĩa vụ”.

### Parameter — t.p9ko6i2o5nha

Giữ ID và giá trị ngưỡng chung, thay nội dung hiện hành:

| Hàng | Replacement |
|---|---|
| P-29 | Mỗi User chỉ một accountType CUSTOMER/SELLER/ADMIN, tài khoản/phiên độc lập, không cộng quyền. Seller PENDING/REJECTED không login; chỉ APPROVED được cấm. Cùng người mua/bán dùng tài khoản khác nhau. Nguồn FRD UC-A02/A03/E03/E06; Database §3.1; Thẻ 15. |
| P-30 | Bỏ scope. Còn nghĩa vụ hiện tại thì BANNED giới hạn, đã xác minh hết thì DISABLED. Nghĩa vụ gồm Order AWAITING_PAYMENT/PAID/PACKING/DELIVERED, Payment PENDING/SUCCESS chưa apply, Refund PENDING/FAILED chưa giải quyết; Seller thêm Settlement/hiệu ứng pending, pending>0 hoặc available>0, không bỏ qua effect bằng 0. Nguồn lỗi không là zero. J7 đối soát hai chiều; nghĩa vụ muộn mở BANNED, không unban. BANNED gỡ bất kỳ lúc nào; DISABLED now<=restoreUntil=disabledAt đầu đợt+30 ngày UTC. Ban/job/nghĩa vụ muộn không gia hạn; quá hạn không xóa dữ liệu/tiền. Unban kết thúc đợt cấm, giữ audit; không phục hồi Order/Product. |
| P-WD-01 | Giữ giá trị 10.000 VND/Đã chốt. amount nguyên dương <=available, >=10000; ngoại lệ BANNED có 0<available<10000 chỉ rút đúng toàn bộ. Không rút một phần dưới ngưỡng/pending; ACTIVE giữ ngưỡng chung. Wallet allowBelowMinimumFullWithdrawal điều khiển form khóa amount toàn bộ. |
| N-05 | Identity đánh giá nghĩa vụ ở ban/login/refresh/J7; request kiểm tra allowlist/ownership/state hiện hành. Không scope/snapshot lịch sử. Chuyển cặp User/profile cùng authzVersion/audit/Outbox local; HTTP ngoài transaction giữ khóa; version/đợt cấm không ghi đè Admin unban. Lỗi/404 bất thường/timeout trả 503 cho request cần xác minh; job giữ trạng thái/retry, không zero giả hoặc cấp quyền rộng hơn. accessMode không phải trạng thái ban. |
| N-06 | J1–J6 đổi thành J1–J7; J7 Identity đối soát BANNED/DISABLED, không đổi ACTIVE/tự unban; không chốt chu kỳ/backoff hoặc SLA số học mới. Giá trị dev không thành cam kết J7. |
| SELLER_CANCEL (#10 danh sách chưa chốt) | Đã chốt Thẻ 15: giữ literal cho nhánh hủy hợp lệ do Order xác minh invalid giao dịch khi áp Payment SUCCESS. Ban sau capture không tự hủy/refund; thiếu bằng chứng/lỗi nguồn giữ retry, không coi current BANNED là bằng chứng invalid tại capture. |

Đánh dấu mô tả đa vai trò/đóng băng/snapshot cũ là lịch sử đã bị Thẻ 15 thay thế, không xóa bằng chứng quyết định cũ.

### UI Screens — t.f8uuqznbc6wb

Áp dụng literal ở bảng frame phía trên cho paragraph/bullet cùng mã màn; giữ layout/thuộc tính không liên quan. Bổ sung menu Seller: OPEN_ORDERS_ONLY chỉ đơn; ORDERS_AND_WITHDRAWAL đơn+ví/rút; WITHDRAWAL_ONLY ví/rút/biên nhận; OBLIGATIONS_READ_ONLY tiến độ pending/nghĩa vụ hệ thống không có thao tác. III.8 giữ pending gồm cả PENDING_REFUND; không rút pending. II.8 BANNED có thể xem CANCELLED còn Refund PENDING/FAILED; Order CONFIRMED không tự giữ phiên. IV.P1 xem banImpact không phải lệnh ban và không khóa liên service. Các nút Hủy/Xác nhận giữ điều kiện state hiện hành. Không tạo/sửa review cho BANNED.

### Simulated Gateway — t.q3f13lnppt6i

Thay bước 1 luồng hoàn theo đơn:
> Order hủy hợp lệ sau thu (Customer hủy đúng state, Seller từ chối, J4, hoặc Order xác minh invalid điều kiện giao dịch khi áp Payment SUCCESS) → outbox order.cancelled. Seller ban sau capture không tự tạo refund; lỗi Identity/Product không phải invalid. Không chuyển DELIVERED/CONFIRMED sang CANCELLED.

Thêm chú giải ở bước Order nhận payment.succeeded:
> Order Service là owner qua event hoặc đối soát dùng chung handler, xác minh ngoài transaction giữ khóa. Invalid được chứng minh chỉ hủy/hoàn riêng Order PAID/PACKING; Payment vẫn SUCCESS; đủ checkout.paid/order.cancelled. Payment tạo Refund, Finance giữ PENDING/PENDING_REFUND đến Refund SUCCESS. Không suy invalid từ Seller hiện tại khi event trễ; replay không nhân đôi kho/hoàn/bút toán. Giữ FULL_CHECKOUT cho phiên CLOSED và các simulator scenario/adapter hiện hành.

### Các bảng Conflicts — giữ lịch sử, thêm cột quyết định hiện hành

FRD Conflicts `t.mlnt08f3uyfw`: giữ vấn đề/đề xuất cũ ở cột lịch sử; thêm “Quyết định hiện hành — Thẻ 15, 17/11/2026”:

| Hàng | Nội dung hiện hành |
|---|---|
| #1/#6 | Mỗi User một accountType, tài khoản Customer/Seller/Admin riêng; không bổ sung profile vai trò khác vào cùng User. Quyết định đa vai trò cũ hết hiệu lực. |
| #2 | Seller PENDING/REJECTED không login; APPROVED bị cấm còn nghĩa vụ BANNED allowlist đơn/ví/pending, hết nghĩa vụ đã xác minh DISABLED. |
| #8 | Customer BANNED theo nghĩa vụ hiện tại gồm Payment/Refund dù Order terminal, không cờ từng trả tiền; không giỏ/checkout/payment/review/toàn bộ lịch sử; nguồn lỗi không zero. |
| #9 | Bỏ scope API/UI; còn nghĩa vụ BANNED→hết DISABLED, J7 mở lại BANNED khi nghĩa vụ muộn không unban. BANNED gỡ bất kỳ lúc nào; DISABLED 30 ngày từ lần đầu, không gia hạn. Hết hạn 409 ACCOUNT_RESTORE_WINDOW_EXPIRED, giữ dữ liệu/tiền. |
| #10 | Không cấm Admin, chỉ Seller APPROVED; không đổi approval_status thành trạng thái ban. |
| #18 | BANNED không đóng băng available, rút theo quyền và ngoại lệ toàn bộ available dương dưới 10.000đ. ACTIVE giữ ngưỡng; không rút pending; Admin không rút thay. |

Đầu bảng ghi: “Chỉ các hàng nêu quyết định Thẻ 15 được cập nhật; câu ‘Giữ quyết định cũ’ trong lịch sử không là authority khôi phục scope/đa vai trò.”

Database Conflicts `t.m3dk44mmot8v`:
- #6 thêm: “Đã bị Thẻ 15 thay thế: không thêm ban_mode/has_paid_order_before_ban làm policy. Identity đọc nghĩa vụ hiện tại Order/Finance, accountStatus suy ra cặp User/profile và allowlist/ownership. User thêm banned_at/disabled_at/restore_until; 30 ngày từ DISABLED đầu đợt, không gia hạn. Nguồn lỗi không disable.”
- #7 thêm: “Seller PENDING/status ACTIVE không có quyền login; chỉ APPROVED được cấm, PENDING/REJECTED trả 409 INVALID_STATE_TRANSITION. Approval riêng trạng thái ban. Nội dung ‘cấm bất kể approval’ là lịch sử không hiệu lực.”

Wireframe Conflicts `t.v9y8iapgln4y`: giữ 28 hàng/marker lịch sử, không đánh dấu tất cả đã sửa:
- #2: “PENDING/REJECTED không login; APPROVED BANNED chỉ nghĩa vụ, pending-only OBLIGATIONS_READ_ONLY, available-only WITHDRAWAL_ONLY, không freeze available.”
- #3: “Customer nghĩa vụ hiện tại, không lịch sử đã trả; hết đã xác minh DISABLED; J7/login/refresh xét nghĩa vụ muộn, nguồn lỗi không giả zero.”
- #4/#7: “Giả định thêm vai trò/profile trên cùng User đã bị thay; không cần màn cấp Seller cho Customer. Sai accountType/kênh báo lỗi đăng nhập chung.”
- #17: “Pending gồm PENDING/PENDING_REFUND và hiệu ứng chưa áp dụng kể cả amount=0; chỉ xem tiến độ. III.8/III.P3 có ngoại lệ rút toàn bộ available dương dưới 10.000 cho BANNED, ACTIVE giữ ngưỡng.”
- #19: “IV.P1 không scope, dùng banImpact hiện đơn/payment/refund/pending/available và kết quả dự kiến; lỗi nguồn khóa xác nhận, không zero. IV.3 hiện timestamp/deadline, gỡ theo hạn, không xóa tiền; POST đọc lại và UI phản ánh response.”
- Mỗi hàng ghi “Chưa xác minh frame đã cập nhật”, không dùng thay đổi bảng conflict để tuyên bố Figma đã sửa.

### Thẻ 15 — t.9bkh3t22k4xx

Không thay/xóa đoạn gốc. Chèn dưới mỗi mục trước mục kế tiếp:

**Mục 1 — đoạn kết luận thêm:**
> Đã chốt 17/11/2026: mỗi tài khoản một accountType, không scope. Còn nghĩa vụ đơn/Payment/Refund/Finance thì BANNED với quyền giải quyết đúng chủ; hết nghĩa vụ xác minh thì DISABLED. J7 Identity đối soát hai chiều, nghĩa vụ muộn mở BANNED giới hạn, không tự unban; nguồn lỗi không là hết nghĩa vụ. Không thêm trạng thái Seller riêng. BANNED gỡ bất kỳ lúc nào; DISABLED gỡ đến restoreUntil=disabledAt đầu đợt+30 ngày UTC; ban/job/nghĩa vụ muộn không gia hạn, quá hạn ACCOUNT_RESTORE_WINDOW_EXPIRED không xóa tiền/dữ liệu. Seller BANNED rút toàn bộ available dương dưới 10.000đ, không rút một phần/pending; ACTIVE giữ ngưỡng. Ban không tự hủy Order/đóng băng available. Popup IV.P1 cảnh báo nghĩa vụ, POST đọc lại; IV.3 hiển thị mốc/deadline.
>
> Đích: FRD UC-A03/E03/D05/G03; Database §3.1/G-14/§8.2D/WD-03/09; API auth/ban/unban/Admin detail/Wallet/§7; UI I.3/I.4/III.8/IV.3/IV.P1. Link cùng document: FRD ?tab=t.z7ee2ts69665, Database ?tab=t.np00hnkiu90, API ?tab=t.ysygluyhdxdg, UI ?tab=t.f8uuqznbc6wb. Repository đã đặc tả; Drive/thiết kế chưa có bằng chứng lưu, không runtime/UAT pass.

**Mục 2 — đoạn kết luận thêm:**
> Đã kiểm tra: UC-D01 thiếu bước so với API/SAD. Tạo Product hợp lệ HIDDEN + công việc provision bền vững, sau commit gọi Order idempotent theo productId; timeout giữ HIDDEN/retry cùng ID/key, không tạo Product khác. Chỉ publish trạng thái yêu cầu khi tồn xác nhận và Seller/Product còn hợp lệ; ban/REMOVED lúc đợi không tự SELLING. Validation/media lỗi trước nhận không lưu, khác Product hợp lệ HIDDEN chờ tồn. Đích FRD UC-D01 (?tab=t.z7ee2ts69665), API create Product (?tab=t.ysygluyhdxdg), SAD §5.7/A.6, UI III.4a (?tab=t.f8uuqznbc6wb), Activity UC-D01. Không thêm job/state/endpoint. Repo đã đặc tả, Drive/Activity chưa xác nhận sửa.

**Mục 3 — đoạn kết luận thêm:**
> Database §8.2B đã có hàng invalid sau thu, chỉ làm rõ trigger: Order khi áp Payment SUCCESS qua payment.succeeded hoặc đối soát handler chung, xác minh ngoài transaction. Invalid đã chứng minh chỉ hủy/hoàn riêng Order PAID/PACKING, giữ Payment SUCCESS và checkout.paid/order.cancelled; Finance giữ pending đến Refund SUCCESS. Ban Seller sau capture không tự hủy, event trễ không suy thời điểm từ status hiện tại. Lỗi nguồn/thiếu bằng chứng giữ retry; replay không nhân kho/Refund/ledger; không hủy DELIVERED/CONFIRMED, không dùng J7/J2/J5 làm owner. Đích Database §8.2B/RefundReason (?tab=t.np00hnkiu90), FRD B06/G02 (?tab=t.z7ee2ts69665), API state/§8.1/§7.2 (?tab=t.ysygluyhdxdg), Gateway (?tab=t.q3f13lnppt6i), Activity B06/G02. Repo đã đặc tả, Drive/Activity chưa xác nhận sửa.

Mục 4 giữ trống.

### Kế hoạch tổng quan — t.75ndkt4teod6

Giữ Output/lịch 13 tuần và phiên bản deliverable lịch sử. Thay cell Công việc:
- Tuần 11: “Admin (E01–E04, E06, E08); ban/unban theo nghĩa vụ/deadline 30 ngày, preview banImpact, quyền giới hạn/J7 (nghĩa vụ muộn/lỗi nguồn/cạnh tranh Admin unban); dashboard CloudWatch, alarm, metric nghiệp vụ; secrets, rate limit.”
- Tuần 12: “Load test + autoscaling, báo cáo chi phí, UAT theo FRD; thêm ca B1–B11 (vòng đời/nghĩa vụ/rút số dư nhỏ/deadline/lỗi nguồn), P1–P2 (HIDDEN/provision/ban/REMOVED), O1–O3 (invalid sau thu/ban sau capture/retry). Đây là kế hoạch kiểm tra, không ghi pass nếu chưa chạy.”

Thiết kế hệ thống `t.78w2al5vn3mi`: no-op, export chỉ BOM/trang trống. Activity `t.jmvqsbkz6lso` và Figma `t.getmw36tr5de`: giữ link tài sản gốc, không coi cập nhật text tab là sửa artifact. Hình áp dụng theo bản vá thiết kế ở trên.

