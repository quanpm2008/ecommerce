# Nguồn, độ phủ và phần còn mở

## Baseline và giới hạn kết luận

Người dùng chấp nhận SAD v1.1 §7.1/D.1 và cây tài liệu ngày 11/11/2026.
Nguồn Markdown dưới đây là input trực tiếp; không tuyên bố đã đọc chốt.txt,
BRD, Parameter Register, Figma hoặc các DOCX/PDF _UPDATED được SAD D.1 dẫn.
Chấp nhận baseline không làm các nguồn thiếu xuất hiện hoặc phê duyệt DELTA.

Mốc code đối chiếu: a79ab611f59d592c4d7fe407b8da96b3ef7290ae, nhánh main,
worktree có staged rename architecture/→business/ và tài liệu untracked của người dùng.
Mọi kết luận code/test là khảo sát tĩnh ngày 11/11/2026, không kết quả runtime.

## Manifest nguồn

Hash là SHA-256 toàn bộ bytes; dùng phát hiện đổi nguồn, không chứng minh nguồn
đúng hoặc đầy đủ về nghiệp vụ. Không đổi tên/biên tập nguồn trong công việc này.

| Đường dẫn | Phiên bản / phạm vi | Dòng | SHA-256 |
|---|---|---:|---|
| `business/UET FRD v5.4 - dong bo API DB.md` | FRD v5.4, 06/11/2026; 37 UC (2 loại bỏ), nghiệp vụ/ngoại lệ | 1539 | `e4fbc0e7b16583e90da42e0ca0fb139f54b63682be9a5c8ca3a91ca283b07de9` |
| `business/database.md` | Database RBAC v5.0, 25/10/2026; data dictionary/G/WD/state | 1055 | `033b8c01e69a8e6c9f783c51b29933ae8afe240e5f85e37423c60c4923e65ab1` |
| `business/api.md` | API v1.0, 25/10/2026, có cập nhật sau metadata; contract để review | 2979 | `6cf4d3144537fcabf1e4aafd3f1e3886200166e6ad3cbe696dbd5463d12b4046` |
| `business/Software-Architecture.md` | SAD-ECOM-01 v1.1, 10/11/2026; TO-BE, ADR/DELTA/AT | 1132 | `4245fb93d5227a8a30b59dd6ca8299cd672d85b69225c4674916f379e4894f73` |
| `business/system-architecture.md` | Không có metadata version/ngày rõ; kiến trúc hệ thống | 1136 | `1e60a4348d298cef62c98c81c37d1dd07a14c2afc1b0b812a59e45257a66c959` |

## UC và điều khoản đã chuyển

37 UC có đích: 32 UC Must/mixed được chuyển thành 377 clause gồm mục tiêu, điều kiện, bước chính, ngoại lệ, hậu điều kiện và ghi chú; 3 UC Should/Could chỉ ranh giới; 2 UC loại bỏ giữ mã. Google trong A01/A03, in-app trong G01 và phần mở rộng E08 không thành Must chỉ vì nằm cùng UC.

Đây là độ phủ clause FRD đã chuyển, không khẳng định mọi field API/DB đã được chép sang bản tóm tắt hoặc mọi yêu cầu đã triển khai. Contract chi tiết giữ ở nguồn, có bảng route và section tra cứu trong mỗi hồ sơ.

| UC / nguồn | Đích | Phạm vi chuyển | Clause |
|---|---|---|---:|
| [UC-A01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L303–336 | [identity](identity.md#uc-a01) | Must/mixed; đủ clause, hiệu chỉnh theo baseline nếu có | 13 |
| [UC-A02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L337–370 | [identity](identity.md#uc-a02) | Must/mixed; đủ clause, hiệu chỉnh theo baseline nếu có | 13 |
| [UC-A03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L371–404 | [identity](identity.md#uc-a03) | Must/mixed; đủ clause, hiệu chỉnh theo baseline nếu có | 13 |
| [UC-A04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L405–426 | [identity](identity.md#uc-a04) | Must/mixed; đủ clause, hiệu chỉnh theo baseline nếu có | 7 |
| [UC-A05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L427–454 | [identity](identity.md#uc-a05) | Must/mixed; đủ clause, hiệu chỉnh theo baseline nếu có | 10 |
| [UC-A06](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L455–486 | [identity](identity.md#uc-a06) | Must/mixed; đủ clause, hiệu chỉnh theo baseline nếu có | 12 |
| [UC-A07](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L487–506 | [identity](identity.md#uc-a07) | Must/mixed; đủ clause, hiệu chỉnh theo baseline nếu có | 6 |
| [UC-B01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L509–536 | [catalog](catalog.md#uc-b01) | Must/mixed; đủ clause, hiệu chỉnh theo baseline nếu có | 10 |
| [UC-B02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L537–560 | [identity](identity.md#uc-b02) | Must/mixed; đủ clause, hiệu chỉnh theo baseline nếu có | 8 |
| [UC-B03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L561–594 | [checkout-orders](checkout-orders.md#uc-b03) | Must/mixed; đủ clause, hiệu chỉnh theo baseline nếu có | 13 |
| [UC-B04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L595–632 | [checkout-orders](checkout-orders.md#uc-b04) | Must/mixed; đủ clause, hiệu chỉnh theo baseline nếu có | 15 |
| [UC-B05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L633–670 | [payments](payments.md#uc-b05) | Must/mixed; đủ clause, hiệu chỉnh theo baseline nếu có | 15 |
| [UC-B06](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L671–698 | [checkout-orders](checkout-orders.md#uc-b06) | Must/mixed; đủ clause, hiệu chỉnh theo baseline nếu có | 10 |
| [UC-B07](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L699–728 | [checkout-orders](checkout-orders.md#uc-b07) | Must/mixed; đủ clause, hiệu chỉnh theo baseline nếu có | 11 |
| [UC-B08](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L729–762 | [checkout-orders](checkout-orders.md#uc-b08) | Must/mixed; đủ clause, hiệu chỉnh theo baseline nếu có | 13 |
| [UC-B09](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L763–794 | [checkout-orders](checkout-orders.md#uc-b09) | Must/mixed; đủ clause, hiệu chỉnh theo baseline nếu có | 12 |
| [UC-B10](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L795–838 | [checkout-orders](checkout-orders.md#uc-b10) | Must/mixed; đủ clause, hiệu chỉnh theo baseline nếu có | 18 |
| [UC-C01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L841–870 | [catalog](catalog.md#uc-c01) | Should/Could: ranh giới; chưa full AC | 0 |
| [UC-C02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L871–874 | [catalog](catalog.md#uc-c02) | Đã loại bỏ, không dùng lại mã | 0 |
| [UC-D01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L879–910 | [catalog](catalog.md#uc-d01) | Must/mixed; đủ clause, hiệu chỉnh theo baseline nếu có | 12 |
| [UC-D02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L911–944 | [checkout-orders](checkout-orders.md#uc-d02) | Must/mixed; đủ clause, hiệu chỉnh theo baseline nếu có | 13 |
| [UC-D03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L945–972 | [catalog](catalog.md#uc-d03) | Must/mixed; đủ clause, hiệu chỉnh theo baseline nếu có | 10 |
| [UC-D04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L973–1000 | [finance](finance.md#uc-d04) | Must/mixed; đủ clause, hiệu chỉnh theo baseline nếu có | 10 |
| [UC-D05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1001–1046 | [finance](finance.md#uc-d05) | Must/mixed; đủ clause, hiệu chỉnh theo baseline nếu có | 19 |
| [UC-E01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1049–1074 | [identity](identity.md#uc-e01) | Must/mixed; đủ clause, hiệu chỉnh theo baseline nếu có | 9 |
| [UC-E02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1075–1104 | [finance](finance.md#uc-e02) | Must/mixed; đủ clause, hiệu chỉnh theo baseline nếu có | 11 |
| [UC-E03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1105–1140 | [identity](identity.md#uc-e03) | Must/mixed; đủ clause, hiệu chỉnh theo baseline nếu có | 14 |
| [UC-E04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1141–1168 | [catalog](catalog.md#uc-e04) | Must/mixed; đủ clause, hiệu chỉnh theo baseline nếu có | 10 |
| [UC-E05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1169–1172 | [catalog](catalog.md#uc-e05) | Đã loại bỏ, không dùng lại mã | 0 |
| [UC-E06](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1173–1202 | [identity](identity.md#uc-e06) | Must/mixed; đủ clause, hiệu chỉnh theo baseline nếu có | 11 |
| [UC-E07](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1203–1230 | [catalog](catalog.md#uc-e07) | Should/Could: ranh giới; chưa full AC | 0 |
| [UC-E08](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1231–1260 | [payments](payments.md#uc-e08) | Must/mixed; đủ clause, hiệu chỉnh theo baseline nếu có | 11 |
| [UC-F01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1263–1284 | [catalog](catalog.md#uc-f01) | Should/Could: ranh giới; chưa full AC | 0 |
| [UC-G01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1287–1316 | [notifications](notifications.md#uc-g01) | Must/mixed; đủ clause, hiệu chỉnh theo baseline nếu có | 11 |
| [UC-G02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1317–1352 | [payments](payments.md#uc-g02) | Must/mixed; đủ clause, hiệu chỉnh theo baseline nếu có | 14 |
| [UC-G03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1353–1382 | [checkout-orders](checkout-orders.md#uc-g03) | Must/mixed; đủ clause, hiệu chỉnh theo baseline nếu có | 11 |
| [UC-G04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1383–1414 | [finance](finance.md#uc-g04) | Must/mixed; đủ clause, hiệu chỉnh theo baseline nếu có | 12 |

## Quy tắc và AT sở hữu ở đâu

Mỗi G/WD/AT chỉ có một hồ sơ sở hữu bản diễn giải; hồ sơ khác dẫn mã. Bảng này là định tuyến, không tạo policy mới.

| Mã nguồn | Đích sở hữu |
|---|---|
| G-01 | [identity](identity.md#g-01) |
| G-02 | [identity](identity.md#g-02) |
| G-03 | [checkout-orders](checkout-orders.md#g-03) |
| G-04 | [checkout-orders](checkout-orders.md#g-04) |
| G-05 | [identity](identity.md#g-05) |
| G-06 | [catalog](catalog.md#g-06) |
| G-07 | [identity](identity.md#g-07) |
| G-08 | [checkout-orders](checkout-orders.md#g-08) |
| G-09 | [engineering](../engineering.md#g-09) |
| G-10 | [README](README.md#g-10) |
| G-11 | [engineering](../engineering.md#g-11) |
| G-12 | [identity](identity.md#g-12) |
| G-13 | [README](README.md#g-13) |
| G-14 | [identity](identity.md#g-14) |
| G-15 | [README](README.md#g-15) |
| G-16 | [checkout-orders](checkout-orders.md#g-16) |
| G-17 | [finance](finance.md#g-17) |
| WD-01 | [finance](finance.md#wd-01) |
| WD-02 | [finance](finance.md#wd-02) |
| WD-03 | [finance](finance.md#wd-03) |
| WD-04 | [finance](finance.md#wd-04) |
| WD-05 | [finance](finance.md#wd-05) |
| WD-06 | [finance](finance.md#wd-06) |
| WD-07 | [finance](finance.md#wd-07) |
| WD-08 | [finance](finance.md#wd-08) |
| WD-09 | [finance](finance.md#wd-09) |
| WD-10 | [finance](finance.md#wd-10) |
| WD-11 | [finance](finance.md#wd-11) |
| AT-01 | [checkout-orders](checkout-orders.md#at-01) |
| AT-02 | [checkout-orders](checkout-orders.md#at-02) |
| AT-03 | [checkout-orders](checkout-orders.md#at-03) |
| AT-04 | [checkout-orders](checkout-orders.md#at-04) |
| AT-05 | [checkout-orders](checkout-orders.md#at-05) |
| AT-06 | [checkout-orders](checkout-orders.md#at-06) |
| AT-07 | [payments](payments.md#at-07) |
| AT-08 | [checkout-orders](checkout-orders.md#at-08) |
| AT-09 | [payments](payments.md#at-09) |
| AT-10 | [payments](payments.md#at-10) |
| AT-11 | [payments](payments.md#at-11) |
| AT-12 | [checkout-orders](checkout-orders.md#at-12) |
| AT-13 | [payments](payments.md#at-13) |
| AT-14 | [finance](finance.md#at-14) |
| AT-15 | [finance](finance.md#at-15) |
| AT-16 | [checkout-orders](checkout-orders.md#at-16) |
| AT-17 | [finance](finance.md#at-17) |
| AT-18 | [identity](identity.md#at-18) |
| AT-19 | [checkout-orders](checkout-orders.md#at-19) |
| AT-20 | [catalog](catalog.md#at-20) |
| AT-21 | [engineering](../engineering.md#at-21) |
| AT-22 | [notifications](notifications.md#at-22) |
| AT-23 | [finance](finance.md#at-23) |
| AT-24 | [engineering](../engineering.md#at-24) |
| AT-25 | [identity](identity.md#at-25) |
| AT-26 | [checkout-orders](checkout-orders.md#at-26) |

## Hiệu chỉnh và nội dung bị thay thế

Nguồn vẫn giữ nguyên; bảng ghi clause đích đã áp dụng baseline, không coi code đang chạy là căn cứ thay thế.

| Clause | Nguồn cũ | Baseline và nội dung hiệu lực |
|---|---|---|
| [UC-A03.MAIN-03](identity.md#uc-a03.main-03) | [UC-A03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L385 | Một lần đăng nhập dùng chung mọi vai trò; một React SPA hiển thị vùng Customer/Seller/Admin theo permission và trạng thái hiện hành. Không có ba phiên hay ba tài khoản độc lập. [baseline](../../business/Software-Architecture.md), L83–98 |
| [UC-A03.POST-01](identity.md#uc-a03.post-01) | [UC-A03](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L403 | Một tài khoản đăng nhập dùng chung các vai trò hợp lệ. Customer BANNED chỉ giữ quyền xem/xử lý đơn cũ theo G-14 nếu snapshot tại thời điểm cấm cho biết đã từng có đơn thanh toán thành công; không có lịch sử đó thì chặn phạm vi Customer nhưng giữ vai trò khác hợp lệ. Cấm Customer không tự disable User. [baseline](../../business/Software-Architecture.md), L602–614 |
| [UC-B04.MAIN-03](checkout-orders.md#uc-b04.main-03) | [UC-B04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L609 | Khách chọn một địa chỉ cho toàn bộ N đơn và xác nhận Thanh toán. Preview chỉ đọc; bước pay mới tạo checkout, giữ tồn và lưu ý định charge. [baseline](../../business/Software-Architecture.md), L444–496 |
| [UC-B04.MAIN-05](checkout-orders.md#uc-b04.main-05) | [UC-B04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L613 | Khi pay hợp lệ, local transaction O1 của Order giữ đủ tất cả dòng, tạo một CheckoutSession, N Order theo Seller và lưu ý định gọi Payment bền vững; thiếu một dòng rollback toàn bộ. [baseline](../../business/Software-Architecture.md), L444–496 |
| [UC-B04.MAIN-06](checkout-orders.md#uc-b04.main-06) | [UC-B04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L615 | Order chưa PAID thì UI hiển thị đang xác nhận; chỉ báo đặt hàng thành công sau O3 áp dụng PAID. Không triển khai luồng tạo phiên công khai rồi browser tự gọi charge cũ như baseline mới. [baseline](../../business/Software-Architecture.md), L444–496 |
| [UC-B04.POST-01](checkout-orders.md#uc-b04.post-01) | [UC-B04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L629 | Một checkout có một CheckoutSession, N Order theo N Seller và tối đa một Payment; Order được định danh bằng orderId, không có mã hiển thị riêng. [baseline](../../business/Software-Architecture.md), L444–496 |
| [UC-B10.MAIN-03](checkout-orders.md#uc-b10.main-03) | [UC-B10](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L809 | Khách chọn địa chỉ chung; quy tắc mặc định tham chiếu Database §3.1.4 (FRD ghi nhầm §3.1.3). Chưa pay thì chưa tạo Order/Reservation. [baseline](../../business/Software-Architecture.md), L444–496 |
| [UC-B10.MAIN-04](checkout-orders.md#uc-b10.main-04) | [UC-B10](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L811 | Khi xác nhận Thanh toán, O1 kiểm tra lại quote/giá/Seller/tồn, tạo một CheckoutSession BUY_NOW, một Order, một OrderItem và công việc charge bền vững. BUY_NOW không gọi Cart. [baseline](../../business/Software-Architecture.md), L444–496 |
| [UC-B10.ALT-06](checkout-orders.md#uc-b10.alt-06) | [UC-B10](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L827 | Rời trang trước khi xác nhận Thanh toán: chỉ có Preview, chưa có phiên/đơn/giữ tồn; không coi việc mở trang hoặc bấm một bước xem trước là O1. [baseline](../../business/Software-Architecture.md), L444–520 |
| [UC-B05.PRE-01](payments.md#uc-b05.pre-01) | [UC-B05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L639 | Customer được phép thanh toán; kiểm tra dữ liệu hiện hành, địa chỉ, quote và idempotency tại pay; O1 mới tạo checkout/giữ tồn. Nếu tiếp tục workflow đã nhận thì dùng tham chiếu và ý định cũ, không thu mới. [baseline](../../business/Software-Architecture.md), L444–520 |
| [UC-B05.MAIN-01](payments.md#uc-b05.main-01) | [UC-B05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L643 | Khách xem Preview và xác nhận Thanh toán qua Order; chi tiết pay/quote/internal charge thuộc DELTA-01/03 cần đồng bộ contract trước code. Không xem endpoint đề xuất là API đã tồn tại. [baseline](../../business/Software-Architecture.md), L444–496 |
| [UC-G02.MAIN-04](payments.md#uc-g02.main-04) | [UC-G02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1333 | Payment ghi Refund SUCCESS và Outbox trong local transaction; Finance nhận kết quả tin cậy, cập nhật Settlement REFUNDED, pending Seller −Sᵢ/PLATFORM −Cᵢ và REFUND_DEBIT trong transaction Finance. Order/Notification cập nhật riêng, có dedupe; không có transaction chung Refund–Settlement–Order. [baseline](../../business/Software-Architecture.md), L522–539 |
| [UC-D04.MAIN-01](finance.md#uc-d04.main-01) | [UC-D04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L983 | Seller mở tra cứu vận hành đơn, Settlement và ví thuộc mình; không bắt buộc màn thống kê phân tích hoặc chọn kỳ phân tích trong MVP. [baseline](../../business/Software-Architecture.md), L75–81 |
| [UC-D04.MAIN-02](finance.md#uc-d04.main-02) | [UC-D04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L985 | MVP tra cứu danh sách/chi tiết đơn và khoản tiền tại service sở hữu; hoãn biểu đồ, top sản phẩm, export phân tích và Reporting Service. [baseline](../../business/Software-Architecture.md), L75–81 |
| [UC-D04.ALT-03](finance.md#uc-d04.alt-03) | [UC-D04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L997 | Không có giao dịch: danh sách trống và số dư thực tế; không dựng read model kỳ thống kê chỉ để đáp ứng phần phân tích đã hoãn. [baseline](../../business/Software-Architecture.md), L75–81 |
| [UC-D05.PRE-01](finance.md#uc-d05.pre-01) | [UC-D05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1007 | Điều kiện rút: Seller được duyệt, ACTIVE, xác thực/quyền/ownership hợp lệ và available ≥ 10.000 VND. Điều kiện số dư này không cấm xem ví dưới 10.000; các nhánh ALT dưới đây quy định trạng thái nút rút. [baseline](../../business/Software-Architecture.md), L572–578 |
| [UC-E02.GOAL-01](finance.md#uc-e02.goal-01) | [UC-E02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1079 | Admin tra cứu vận hành và đối soát nguồn tiền chính thức; không bắt buộc dashboard phân tích theo kỳ trong MVP. [baseline](../../business/Software-Architecture.md), L75–81 |
| [UC-E02.MAIN-01](finance.md#uc-e02.main-01) | [UC-E02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1085 | Admin mở tra cứu vận hành và đối soát; hoãn dashboard phân tích, biểu đồ và export. [baseline](../../business/Software-Architecture.md), L75–81 |
| [UC-E02.ALT-01](finance.md#uc-e02.alt-01) | [UC-E02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1097 | Nguồn đủ dữ liệu và không có giao dịch thì phản ánh 0; thiếu nguồn không được coi là balanced=true hoặc không có giao dịch. [baseline](../../business/Software-Architecture.md), L665–685 |
| [UC-G04.MAIN-02](finance.md#uc-g04.main-02) | [UC-G04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1395 | Order ghi CONFIRMED và Outbox trong local transaction; Finance xử lý order.confirmed, chuyển Settlement SETTLED, Seller pending −Sᵢ/available +Sᵢ, PLATFORM pending −Cᵢ/available +Cᵢ và SETTLEMENT_RELEASED trong transaction Finance. Không gộp transaction Order và Finance. [baseline](../../business/Software-Architecture.md), L559–570 |
| [UC-G01.MAIN-05](notifications.md#uc-g01.main-05) | [UC-G01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1305 | Email demo bất đồng bộ là Must qua DemoEmailAdapter; không tuyên bố email thật đã giao. In-app là Should, chưa bắt buộc trong MVP. [baseline](../../business/Software-Architecture.md), L303–318 |

Các hiệu chỉnh xuyên nguồn khác: API nói 3 web app → ADR-02 một SPA; API §3.2
ví dụ refreshToken JSON → SAD §6.3 cookie; API nguồn địa giới ngoài → SAD §3.1/6.7
RegionCatalog nội bộ; FRD G02/G04 transaction xuyên service → SAD local transaction.
API C-09 là ghi chú cũ: FRD v5.4 A05 đã cùng thông báo theo G-05, không mở lại policy.
SAD C.1 ghi host DB 5433 nhưng Compose khảo sát là 5432: chỉ lệch hiện trạng,
không đặt port 5432 làm contract môi trường khác.

<a id="delta"></a>
## Quyết định đã nhận và DELTA chưa thành contract

Nguồn: SAD §7.1–7.2. Sổ quyết định được dẫn tại đây, không tạo ADR trùng trong docs/decisions/.

| Mã | Quyết định | Lý do / hệ quả |
|---|---|---|
| ADR-01 | Giữ 7 service, Order sở hữu Inventory/điều phối | Giảm giao dịch xuyên service trong luồng kho; không tách thêm service |
| ADR-02 | Một React SPA, Gateway WebFlux, layer trong service | Thống nhất vận hành và cấu trúc triển khai theo S1 |
| ADR-03 | quoteToken có chữ ký | Chứng minh giá khách đã xem mà Preview không ghi dữ liệu; thêm field và config TTL |
| ADR-04 | Chi tiết Must; Should/Could chỉ ranh giới | Hoàn thành MVP trước, không thiết kế trước các module không cần |
| ADR-05 | Tra cứu vận hành và đối soát; hoãn phân tích | Giải quyết mâu thuẫn FRD D04/E02 với DB §5.6/API C-11 |
| ADR-06 | Local transaction + Kafka Outbox/Inbox + business dedupe | Không mất ý định/sự kiện; chấp nhận eventual consistency có đối soát |

Các nội dung sau **đề xuất/cần review trước code**, không tự tạo API/schema/config mới:

| Mã | Hồ sơ bị ảnh hưởng | Nội dung đề xuất / điều kiện đóng |
|---|---|---|
| DELTA-01 | API preview/pay | quoteToken request/response, errors, TTL/purpose/canonicalization; test giá đổi và replay |
| DELTA-02 | API internal Cart | checkout-items batch theo owner; response snapshot/version; allowlist Order |
| DELTA-03 | API internal Payment | Internal charge thay public POST cũ; service và user context tách biệt; job không lưu user token |
| DELTA-04 | API internal Order/Payment jobs | close-expired có idempotency; PAYMENT_ORDER_APPLICATION cho SUCCESS chưa áp dụng; đầy đủ đường FULL_CHECKOUT |
| DELTA-05 | Database bảng kỹ thuật | WorkerExecutionLog lease/state/payload; Outbox retry; Inbox chờ phụ thuộc và APPLIED; không thêm entity nghiệp vụ |
| DELTA-06 | Event schemas | aggregateType/schemaVersion/correlation; checkout.paid thêm paymentId; checkout.closed đủ Customer/reason; bảo vệ reset event |
| DELTA-07 | Security/config | Service JWT issuer/audience/key/allowlist, cookie/CSRF/rotation; cấu hình startup và vận hành |
| DELTA-08 | FRD/API phạm vi | Ghi lại ADR-04/05, không còn hiểu D04/E02 là phải có biểu đồ/phân tích |
| DELTA-09 | Dữ liệu/Cloud | DB logic/role và phương án triển khai AWS/Kafka/backup, RPO/RTO, retention |

<a id="cau-hoi"></a>
## Câu hỏi còn mở và tham số

| Mã câu hỏi | Phạm vi / nguồn | Điều cần chốt, không suy diễn |
|---|---|---|
| OQ-001 | SAD §7.3; API §9.1; FRD §9 | P-01…P-26/N-xx thiếu Parameter Register đầy đủ. Giá trị TTL/giới hạn dev không là policy; chưa có đủ taxonomy để bịa tên/giá trị mọi P. |
| OQ-002 | P-COM-01; Database §8.3 | Nguồn/rate hoa hồng bps, validation cấu hình; không tự chọn 500 hoặc 2.000 từ ví dụ. |
| OQ-003 | SAD §3.1/6.7/A.6.2; P-26 | Chọn dataset địa giới nội bộ, xuất xứ/version/ngày nhập và cập nhật; baseline đã chọn nội bộ, không hỏi lại ngoài/nội bộ. |
| OQ-004 | SAD §6.3–6.4; DELTA-07 | Thuộc tính cookie/profile local, Origin/CORS/CSRF/key rotation và service credential cụ thể. JWT service trong A.3 vẫn đề xuất. |
| OQ-005 | SAD §7.2; DELTA-01…06 | Review và đồng bộ quote/pay/internal charge/cart snapshot, worker, close-expired, event/inbox; không tự gán endpoint hoặc table mới. |
| OQ-006 | SAD §6.7 thay API C-08/Database §5.2; A.6.1 | Baseline đã quy định unaccent/lowercase. Cần đồng bộ contract, quy tắc normalize/index và test; không hỏi lại có tìm không dấu, không tự coi đã có implementation hoặc đổi thành full-text. |
| OQ-007 | P-19, N-06; SAD §7.3/A.6.4 | Retry/email demo access/retention/dedupe và job interval; không hứa exactly-once gửi email thật. |
| OQ-008 | SAD §7.3, C.5; DELTA-09 | Cloud topology/DB role/Kafka/backup, retention, tải/p95/RPO/RTO; không kết luận SLO từ thiết kế. |
| OQ-009 | identity-service/should.md A8; API enum vs DB §6 | Tên AuditAction khác nhau giữa source API và DB chưa được baseline hiệu chỉnh cụ thể; giữ mở, không tự rename enum/CHECK. |

Hai hạn 7 ngày và P-WD-01=10.000 VND đã chốt; không đưa lại TBD. ADR-05 đã hoãn
phân tích, P-14/P-15 không chặn Must tra cứu vận hành. Bảng nguồn tham số SAD:

| Tham số | Phạm vi | Cách xử lý trong thiết kế |
|---|---|---|
| P-01 | TTL giữ tồn | Cấu hình Order; ràng buộc với dispatch/J1/J2b |
| P-02/P-03/P-04 | Chờ/query/thời gian tối đa Payment | Cấu hình Payment/Order; không biến HTTP timeout thành FAILED |
| P-05/P-06 | Reset/access/refresh TTL | Cấu hình Identity; review cùng rotation/reuse |
| P-08/P-09/P-10/P-11 | Media/địa chỉ/giỏ/phân trang | Bind validation/config; test biên theo giá trị được chốt |
| P-19/P-21 | Retry email/lý do hủy-từ chối | Không hardcode ví dụ ngoài contract đã duyệt |
| P-COM-01 | Hoa hồng | Nguồn Finance; snapshot Order; bắt buộc có khi bật checkout |
| P-SIM-01 | Độ trễ success muộn | Chỉ scenario test/demo được cấu hình |
| N-06 | Chu kỳ job | Cấu hình; không mặc định lấy một phút từ ví dụ |
| SAD-QUOTE-TTL | Hạn quoteToken | Tham số kỹ thuật mới, TBD |
| SAD-HTTP/RETRY/LEASE | HTTP budgets, backoff, worker lease | TBD theo đo đạc; lease không bảo đảm thay idempotency |
| SAD-RETENTION | Outbox/Inbox/idempotency/audit/secret event | Phải bao phủ retry/replay và chính sách dữ liệu |
| SAD-NFR | Tải mục tiêu, p95, error budget, RPO/RTO | Chốt trước kiểm thử tải và phát hành cloud |
| SAD-SECURITY | Issuer/audience/algorithm/key rotation/CORS/cookie | Review theo môi trường, không có secret mặc định |

## Nội dung chưa chuyển đầy đủ và hiện trạng chưa xác minh

- Should/Could: UC-C01 Review, UC-E07 Category CRUD, UC-F01 gợi ý; Google A01/A03,
  in-app G01, phần Admin mở rộng E08 chỉ ranh giới/nguồn. Không có full AC cho các phần này.
- UC-C02/E05 đã loại bỏ; Reporting read models Database §3.8, dashboard/top/export
  không thuộc Must theo ADR-04/05. Không xóa khỏi inventory hoặc tái dùng mã.
- Không chép toàn bộ DTO/data dictionary/HTTP examples/error enum/event schema.
  Hồ sơ tính năng có route/permission và trỏ mục chính xác; field required/conditional,
  kiểu/cột/UQ/FK/CHECK cụ thể vẫn là contract nguồn. Task sửa contract phải mở đúng mục đó.
- Phụ lục SAD A là phương án kỹ thuật review, không đặc tả triển khai đã được phê duyệt
  từng chi tiết. Chiến lược test/AT và bất biến chính được dẫn; chưa tạo guard/CI/test code.
- Code identity còn lệch User/Seller, hard-delete Address, region và refresh-cookie.
  Các service khác chủ yếu scaffold; chưa chứng minh E2E mua/thu/hoàn/rút.
- Unit/integration/runtime/AT chưa chạy. Không lấy context-load, fake Order/Region,
  checkbox tiến độ hoặc OpenAPI artifact làm bằng chứng toàn luồng đã đạt.
- Thiếu BRD/Parameter Register/UI/Figma và source gốc S1–S6 trong business/ được ghi
  nhận là giới hạn đầu vào, không bịa nguồn hoặc trang PDF chưa được cung cấp.

## Inventory section nguồn

Mỗi heading có nơi tra cứu hoặc trạng thái rõ. “Hợp đồng tra cứu” nghĩa chi tiết
vẫn ở nguồn, không tuyên bố mọi clause trong section đã được diễn giải lại.
Độ phủ heading không thay review điều kiện/ngoại lệ hoặc bằng chứng runtime.

| Nguồn / section (khoảng dòng subtree) | Nơi đọc / trạng thái |
|---|---|
| [HƯỚNG DẪN ĐỌC & TRA CỨU](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L69–137 | [quy ước](README.md), finance/coverage — index/thuật ngữ/truy vết/nghiệm thu; lịch sử giữ ở nguồn |
| [01 &#124; Chọn đúng nơi cần đọc](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L73–83 | [quy ước](README.md), finance/coverage — index/thuật ngữ/truy vết/nghiệm thu; lịch sử giữ ở nguồn |
| [02 &#124; Từ điển định danh](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L84–106 | [quy ước](README.md), finance/coverage — index/thuật ngữ/truy vết/nghiệm thu; lịch sử giữ ở nguồn |
| [03 &#124; Tra cứu nhanh các nhóm use case](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L107–120 | [quy ước](README.md), finance/coverage — index/thuật ngữ/truy vết/nghiệm thu; lịch sử giữ ở nguồn |
| [04 &#124; Những điều phải nắm trong FRD 5.x](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L121–137 | [quy ước](README.md), finance/coverage — index/thuật ngữ/truy vết/nghiệm thu; lịch sử giữ ở nguồn |
| [GIẢI NGHĨA CÔNG THỨC & SỔ TIỀN (FRD v5.x)](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L138–207 | [quy ước](README.md), finance/coverage — index/thuật ngữ/truy vết/nghiệm thu; lịch sử giữ ở nguồn |
| [01 &#124; Bảng ký hiệu và đơn vị](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L142–154 | [quy ước](README.md), finance/coverage — index/thuật ngữ/truy vết/nghiệm thu; lịch sử giữ ở nguồn |
| [02 &#124; Công thức & ví dụ tính từng bước](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L155–164 | [quy ước](README.md), finance/coverage — index/thuật ngữ/truy vết/nghiệm thu; lịch sử giữ ở nguồn |
| [03 &#124; Các ngăn tiền trong sổ giả lập](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L165–198 | [quy ước](README.md), finance/coverage — index/thuật ngữ/truy vết/nghiệm thu; lịch sử giữ ở nguồn |
| [04 &#124; Những điểm chưa được chốt trong hồ sơ hiện có](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L199–207 | [quy ước](README.md), finance/coverage — index/thuật ngữ/truy vết/nghiệm thu; lịch sử giữ ở nguồn |
| [MA TRẬN TRUY VẾT TÓM TẮT](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L208–225 | [quy ước](README.md), finance/coverage — index/thuật ngữ/truy vết/nghiệm thu; lịch sử giữ ở nguồn |
| [1. Giới thiệu](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L226–229 | [quy ước](README.md), finance/coverage — index/thuật ngữ/truy vết/nghiệm thu; lịch sử giữ ở nguồn |
| [2. Actor](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L230–238 | [quy ước](README.md), finance/coverage — index/thuật ngữ/truy vết/nghiệm thu; lịch sử giữ ở nguồn |
| [3. Sơ đồ Use Case tổng quan](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L239–242 | [quy ước](README.md), finance/coverage — index/thuật ngữ/truy vết/nghiệm thu; lịch sử giữ ở nguồn |
| [4. Mô hình dữ liệu — ĐÃ CHUYỂN SANG DATABASE (v5.3; hiện hành: Database RBAC v5.0)](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L243–268 | [quy ước](README.md), finance/coverage — index/thuật ngữ/truy vết/nghiệm thu; lịch sử giữ ở nguồn |
| [5. Ma trận quyền theo actor](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L269–296 | [quy ước](README.md), finance/coverage — index/thuật ngữ/truy vết/nghiệm thu; lịch sử giữ ở nguồn |
| [6. Đặc tả chi tiết theo nhóm chức năng](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L297–1414 | [quy ước](README.md), finance/coverage — index/thuật ngữ/truy vết/nghiệm thu; lịch sử giữ ở nguồn |
| [Nhóm A — Tài khoản & hồ sơ](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L301–506 | [quy ước](README.md), finance/coverage — index/thuật ngữ/truy vết/nghiệm thu; lịch sử giữ ở nguồn |
| [UC-A01 &#124; Đăng ký tài khoản khách hàng](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L303–336 | [identity](identity.md#uc-a01) — UC đã chuyển/rành giới/loại bỏ theo bảng trên |
| [UC-A02 &#124; Đăng ký trở thành người bán](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L337–370 | [identity](identity.md#uc-a02) — UC đã chuyển/rành giới/loại bỏ theo bảng trên |
| [UC-A03 &#124; Đăng nhập](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L371–404 | [identity](identity.md#uc-a03) — UC đã chuyển/rành giới/loại bỏ theo bảng trên |
| [UC-A04 &#124; Đổi mật khẩu](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L405–426 | [identity](identity.md#uc-a04) — UC đã chuyển/rành giới/loại bỏ theo bảng trên |
| [UC-A05 &#124; Quên mật khẩu](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L427–454 | [identity](identity.md#uc-a05) — UC đã chuyển/rành giới/loại bỏ theo bảng trên |
| [UC-A06 &#124; Cập nhật hồ sơ](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L455–486 | [identity](identity.md#uc-a06) — UC đã chuyển/rành giới/loại bỏ theo bảng trên |
| [UC-A07 &#124; Đăng xuất](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L487–506 | [identity](identity.md#uc-a07) — UC đã chuyển/rành giới/loại bỏ theo bảng trên |
| [Nhóm B — Customer: mua sắm (đa-seller, tách đơn theo cửa hàng)](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L507–838 | [quy ước](README.md), finance/coverage — index/thuật ngữ/truy vết/nghiệm thu; lịch sử giữ ở nguồn |
| [UC-B01 &#124; Tìm kiếm và xem sản phẩm](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L509–536 | [catalog](catalog.md#uc-b01) — UC đã chuyển/rành giới/loại bỏ theo bảng trên |
| [UC-B02 &#124; Tìm kiếm và xem người bán](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L537–560 | [identity](identity.md#uc-b02) — UC đã chuyển/rành giới/loại bỏ theo bảng trên |
| [UC-B03 &#124; Quản lý giỏ hàng](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L561–594 | [checkout-orders](checkout-orders.md#uc-b03) — UC đã chuyển/rành giới/loại bỏ theo bảng trên |
| [UC-B04 &#124; Đặt hàng và tách đơn theo cửa hàng](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L595–632 | [checkout-orders](checkout-orders.md#uc-b04) — UC đã chuyển/rành giới/loại bỏ theo bảng trên |
| [UC-B05 &#124; Thanh toán](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L633–670 | [payments](payments.md#uc-b05) — UC đã chuyển/rành giới/loại bỏ theo bảng trên |
| [UC-B06 &#124; Xác nhận đơn sau thanh toán](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L671–698 | [checkout-orders](checkout-orders.md#uc-b06) — UC đã chuyển/rành giới/loại bỏ theo bảng trên |
| [UC-B07 &#124; Theo dõi trạng thái và lịch sử đơn](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L699–728 | [checkout-orders](checkout-orders.md#uc-b07) — UC đã chuyển/rành giới/loại bỏ theo bảng trên |
| [UC-B08 &#124; Hủy đơn](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L729–762 | [checkout-orders](checkout-orders.md#uc-b08) — UC đã chuyển/rành giới/loại bỏ theo bảng trên |
| [UC-B09 &#124; Xác nhận đã nhận hàng](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L763–794 | [checkout-orders](checkout-orders.md#uc-b09) — UC đã chuyển/rành giới/loại bỏ theo bảng trên |
| [UC-B10 &#124; Mua ngay từ trang sản phẩm](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L795–838 | [checkout-orders](checkout-orders.md#uc-b10) — UC đã chuyển/rành giới/loại bỏ theo bảng trên |
| [Nhóm C — Customer: sau mua hàng](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L839–874 | [quy ước](README.md), finance/coverage — index/thuật ngữ/truy vết/nghiệm thu; lịch sử giữ ở nguồn |
| [UC-C01 &#124; Đánh giá sản phẩm](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L841–870 | [catalog](catalog.md#uc-c01) — UC đã chuyển/rành giới/loại bỏ theo bảng trên |
| [UC-C02 &#124; Báo cáo sản phẩm/Seller vi phạm — ĐÃ LOẠI BỎ (v5.2)](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L871–874 | [catalog](catalog.md#uc-c02) — UC đã chuyển/rành giới/loại bỏ theo bảng trên |
| [Nhóm D — Seller (phạm vi của chính mình)](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L875–1046 | [quy ước](README.md), finance/coverage — index/thuật ngữ/truy vết/nghiệm thu; lịch sử giữ ở nguồn |
| [UC-D01 &#124; Quản lý sản phẩm](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L879–910 | [catalog](catalog.md#uc-d01) — UC đã chuyển/rành giới/loại bỏ theo bảng trên |
| [UC-D02 &#124; Chuẩn bị, xác nhận giao hoặc từ chối giao đơn](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L911–944 | [checkout-orders](checkout-orders.md#uc-d02) — UC đã chuyển/rành giới/loại bỏ theo bảng trên |
| [UC-D03 &#124; Cập nhật tồn kho](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L945–972 | [catalog](catalog.md#uc-d03) — UC đã chuyển/rành giới/loại bỏ theo bảng trên |
| [UC-D04 &#124; Xem kết quả kinh doanh và khoản được nhận](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L973–1000 | [finance](finance.md#uc-d04) — UC đã chuyển/rành giới/loại bỏ theo bảng trên |
| [UC-D05 &#124; Ví người bán và rút tiền (giả lập)](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1001–1046 | [finance](finance.md#uc-d05) — UC đã chuyển/rành giới/loại bỏ theo bảng trên |
| [Nhóm E — Administrator](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1047–1260 | [quy ước](README.md), finance/coverage — index/thuật ngữ/truy vết/nghiệm thu; lịch sử giữ ở nguồn |
| [UC-E01 &#124; Quản lý người dùng](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1049–1074 | [identity](identity.md#uc-e01) — UC đã chuyển/rành giới/loại bỏ theo bảng trên |
| [UC-E02 &#124; Báo cáo tổng quan](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1075–1104 | [finance](finance.md#uc-e02) — UC đã chuyển/rành giới/loại bỏ theo bảng trên |
| [UC-E03 &#124; Cấm và gỡ cấm tài khoản](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1105–1140 | [identity](identity.md#uc-e03) — UC đã chuyển/rành giới/loại bỏ theo bảng trên |
| [UC-E04 &#124; Gỡ / khôi phục sản phẩm](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1141–1168 | [catalog](catalog.md#uc-e04) — UC đã chuyển/rành giới/loại bỏ theo bảng trên |
| [UC-E05 &#124; Xử lý báo cáo vi phạm — ĐÃ LOẠI BỎ (v5.2)](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1169–1172 | [catalog](catalog.md#uc-e05) — UC đã chuyển/rành giới/loại bỏ theo bảng trên |
| [UC-E06 &#124; Duyệt Seller mới đăng ký](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1173–1202 | [identity](identity.md#uc-e06) — UC đã chuyển/rành giới/loại bỏ theo bảng trên |
| [UC-E07 &#124; Quản lý danh mục](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1203–1230 | [catalog](catalog.md#uc-e07) — UC đã chuyển/rành giới/loại bỏ theo bảng trên |
| [UC-E08 &#124; Xem đơn và xử lý khoản tiền gặp sự cố](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1231–1260 | [payments](payments.md#uc-e08) — UC đã chuyển/rành giới/loại bỏ theo bảng trên |
| [Nhóm F — Tính năng nâng cao](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1261–1284 | [quy ước](README.md), finance/coverage — index/thuật ngữ/truy vết/nghiệm thu; lịch sử giữ ở nguồn |
| [UC-F01 &#124; Gợi ý sản phẩm liên quan](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1263–1284 | [catalog](catalog.md#uc-f01) — UC đã chuyển/rành giới/loại bỏ theo bảng trên |
| [Nhóm G — Tác vụ hệ thống](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1285–1414 | [quy ước](README.md), finance/coverage — index/thuật ngữ/truy vết/nghiệm thu; lịch sử giữ ở nguồn |
| [UC-G01 &#124; Thông báo](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1287–1316 | [notifications](notifications.md#uc-g01) — UC đã chuyển/rành giới/loại bỏ theo bảng trên |
| [UC-G02 &#124; Hoàn tiền](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1317–1352 | [payments](payments.md#uc-g02) — UC đã chuyển/rành giới/loại bỏ theo bảng trên |
| [UC-G03 &#124; Tự động xử lý đơn/giao dịch quá hạn](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1353–1382 | [checkout-orders](checkout-orders.md#uc-g03) — UC đã chuyển/rành giới/loại bỏ theo bảng trên |
| [UC-G04 &#124; Quyết toán đơn và ghi có ví (giả lập)](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1383–1414 | [finance](finance.md#uc-g04) — UC đã chuyển/rành giới/loại bỏ theo bảng trên |
| [7. Truy vết](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1415–1422 | [quy ước](README.md), finance/coverage — index/thuật ngữ/truy vết/nghiệm thu; lịch sử giữ ở nguồn |
| [8. Nhật ký quyết định cập nhật v5.0 – v5.4](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1423–1478 | [quy ước](README.md), finance/coverage — index/thuật ngữ/truy vết/nghiệm thu; lịch sử giữ ở nguồn |
| [Bảng ánh xạ thuật ngữ v4.1 → v5.x (dành cho backlog, API, DB)](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1456–1478 | [quy ước](README.md), finance/coverage — index/thuật ngữ/truy vết/nghiệm thu; lịch sử giữ ở nguồn |
| [9. Thông số bổ sung và kịch bản nghiệm thu](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1479–1539 | [quy ước](README.md), finance/coverage — index/thuật ngữ/truy vết/nghiệm thu; lịch sử giữ ở nguồn |
| [Thiết kế dữ liệu và phân quyền RBAC cho hệ thống Ecommerce](../../business/database.md), L1–70 | [quy ước](README.md), [engineering](../engineering.md), coverage — G/state/ownership/ràng buộc và câu hỏi |
| [2 Bản đồ entity theo service](../../business/database.md), L71–85 | [quy ước](README.md), [engineering](../engineering.md), coverage — G/state/ownership/ràng buộc và câu hỏi |
| [3 Entity Data Dictionary chi tiết](../../business/database.md), L86–713 | [quy ước](README.md), [engineering](../engineering.md), coverage — G/state/ownership/ràng buộc và câu hỏi |
| [3.1 Identity và Seller với User và RBAC](../../business/database.md), L90–238 | [identity](identity.md) — contract/constraint tra cứu; chi tiết field giữ ở nguồn |
| [3.1.1 User](../../business/database.md), L96–115 | [identity](identity.md) — contract/constraint tra cứu; chi tiết field giữ ở nguồn |
| [3.1.2 Customer](../../business/database.md), L116–128 | [identity](identity.md) — contract/constraint tra cứu; chi tiết field giữ ở nguồn |
| [3.1.3 Seller](../../business/database.md), L129–153 | [identity](identity.md) — contract/constraint tra cứu; chi tiết field giữ ở nguồn |
| [3.1.4 CustomerAddress](../../business/database.md), L154–173 | [identity](identity.md) — contract/constraint tra cứu; chi tiết field giữ ở nguồn |
| [3.1.5 Role](../../business/database.md), L174–184 | [identity](identity.md) — contract/constraint tra cứu; chi tiết field giữ ở nguồn |
| [3.1.6 Permission](../../business/database.md), L185–195 | [identity](identity.md) — contract/constraint tra cứu; chi tiết field giữ ở nguồn |
| [3.1.7 UserRole](../../business/database.md), L196–207 | [identity](identity.md) — contract/constraint tra cứu; chi tiết field giữ ở nguồn |
| [3.1.8 RolePermission](../../business/database.md), L208–217 | [identity](identity.md) — contract/constraint tra cứu; chi tiết field giữ ở nguồn |
| [3.1.9 Ma trận quyền và quan hệ kế thừa](../../business/database.md), L218–238 | [identity](identity.md) — contract/constraint tra cứu; chi tiết field giữ ở nguồn |
| [3.2 Product](../../business/database.md), L239–298 | [catalog](catalog.md) — contract/constraint tra cứu; chi tiết field giữ ở nguồn |
| [3.2.1 Category](../../business/database.md), L243–257 | [catalog](catalog.md) — contract/constraint tra cứu; chi tiết field giữ ở nguồn |
| [3.2.2 Product](../../business/database.md), L258–279 | [catalog](catalog.md) — contract/constraint tra cứu; chi tiết field giữ ở nguồn |
| [3.2.3 Review](../../business/database.md), L280–298 | [catalog](catalog.md) — contract/constraint tra cứu; chi tiết field giữ ở nguồn |
| [3.3 Cart](../../business/database.md), L299–342 | [checkout-orders](checkout-orders.md) — contract/constraint tra cứu; chi tiết field giữ ở nguồn |
| [3.3.1 Cart](../../business/database.md), L303–317 | [checkout-orders](checkout-orders.md) — contract/constraint tra cứu; chi tiết field giữ ở nguồn |
| [3.3.2 CartItem](../../business/database.md), L318–342 | [checkout-orders](checkout-orders.md) — contract/constraint tra cứu; chi tiết field giữ ở nguồn |
| [3.4 Payment](../../business/database.md), L343–391 | [payments](payments.md) — contract/constraint tra cứu; chi tiết field giữ ở nguồn |
| [3.4.1 Payment](../../business/database.md), L347–368 | [payments](payments.md) — contract/constraint tra cứu; chi tiết field giữ ở nguồn |
| [3.4.2 Refund](../../business/database.md), L369–391 | [payments](payments.md) — contract/constraint tra cứu; chi tiết field giữ ở nguồn |
| [3.5 Finance](../../business/database.md), L392–488 | [finance](finance.md) — contract/constraint tra cứu; chi tiết field giữ ở nguồn |
| [3.5.1 Settlement](../../business/database.md), L396–416 | [finance](finance.md) — contract/constraint tra cứu; chi tiết field giữ ở nguồn |
| [3.5.2 Wallet](../../business/database.md), L417–436 | [finance](finance.md) — contract/constraint tra cứu; chi tiết field giữ ở nguồn |
| [3.5.3 WalletTransaction](../../business/database.md), L437–466 | [finance](finance.md) — contract/constraint tra cứu; chi tiết field giữ ở nguồn |
| [3.5.4 Withdrawal](../../business/database.md), L467–488 | [finance](finance.md) — contract/constraint tra cứu; chi tiết field giữ ở nguồn |
| [3.6 Order Inventory Fulfillment](../../business/database.md), L489–624 | [checkout-orders](checkout-orders.md) — contract/constraint tra cứu; chi tiết field giữ ở nguồn |
| [3.6.1 CheckoutSession](../../business/database.md), L493–519 | [checkout-orders](checkout-orders.md) — contract/constraint tra cứu; chi tiết field giữ ở nguồn |
| [3.6.2 Order](../../business/database.md), L520–550 | [checkout-orders](checkout-orders.md) — contract/constraint tra cứu; chi tiết field giữ ở nguồn |
| [3.6.3 OrderItem](../../business/database.md), L551–570 | [checkout-orders](checkout-orders.md) — contract/constraint tra cứu; chi tiết field giữ ở nguồn |
| [3.6.4 Inventory](../../business/database.md), L571–586 | [checkout-orders](checkout-orders.md) — contract/constraint tra cứu; chi tiết field giữ ở nguồn |
| [3.6.5 Reservation](../../business/database.md), L587–604 | [checkout-orders](checkout-orders.md) — contract/constraint tra cứu; chi tiết field giữ ở nguồn |
| [3.6.6 StockMovement](../../business/database.md), L605–624 | [checkout-orders](checkout-orders.md) — contract/constraint tra cứu; chi tiết field giữ ở nguồn |
| [3.7 Notification](../../business/database.md), L625–652 | [notifications](notifications.md) — contract/constraint tra cứu; chi tiết field giữ ở nguồn |
| [3.7.1 Notification](../../business/database.md), L629–652 | [notifications](notifications.md) — contract/constraint tra cứu; chi tiết field giữ ở nguồn |
| [3.8 Reporting](../../business/database.md), L653–713 | Hoãn Reporting theo ADR-05; data dictionary giữ tại nguồn |
| [3.8.1 OrderReportProjection \[READ MODEL\]](../../business/database.md), L659–676 | Hoãn Reporting theo ADR-05; data dictionary giữ tại nguồn |
| [3.8.2 FinancialReportProjection \[READ MODEL\]](../../business/database.md), L677–695 | Hoãn Reporting theo ADR-05; data dictionary giữ tại nguồn |
| [3.8.3 InventoryReportProjection \[READ MODEL\]](../../business/database.md), L696–713 | Hoãn Reporting theo ADR-05; data dictionary giữ tại nguồn |
| [4 Quan hệ ownership và bất biến](../../business/database.md), L714–772 | [quy ước](README.md), [engineering](../engineering.md), coverage — G/state/ownership/ràng buộc và câu hỏi |
| [4.1 Các bất biến phải kiểm thử](../../business/database.md), L735–760 | [quy ước](README.md), [engineering](../engineering.md), coverage — G/state/ownership/ràng buộc và câu hỏi |
| [4.2 Quyền ghi dữ liệu](../../business/database.md), L761–772 | [quy ước](README.md), [engineering](../engineering.md), coverage — G/state/ownership/ràng buộc và câu hỏi |
| [5 Luồng nghiệp vụ cập nhật](../../business/database.md), L773–894 | [quy ước](README.md), [engineering](../engineering.md), coverage — G/state/ownership/ràng buộc và câu hỏi |
| [5.1 Đăng ký và phân quyền Seller](../../business/database.md), L775–782 | [identity](identity.md) — contract/constraint tra cứu; chi tiết field giữ ở nguồn |
| [5.2 Tìm kiếm đơn giản trong database](../../business/database.md), L783–788 | [catalog](catalog.md) — contract/constraint tra cứu; chi tiết field giữ ở nguồn |
| [5.3 Giỏ hàng, Mua ngay và checkout](../../business/database.md), L789–804 | [checkout-orders](checkout-orders.md) — contract/constraint tra cứu; chi tiết field giữ ở nguồn |
| [5.4 Use case Seller xem ví và rút tiền (UC-D05)](../../business/database.md), L805–822 | [finance](finance.md) — contract/constraint tra cứu; chi tiết field giữ ở nguồn |
| [5.5 Trạng thái Order và các job](../../business/database.md), L823–839 | [checkout-orders](checkout-orders.md) — contract/constraint tra cứu; chi tiết field giữ ở nguồn |
| [5.6 Reporting tạm hoãn và bỏ chức năng báo cáo vi phạm](../../business/database.md), L840–845 | [quy ước](README.md), [engineering](../engineering.md), coverage — G/state/ownership/ràng buộc và câu hỏi |
| [5.7 Hợp đồng API và thực thi phân quyền](../../business/database.md), L846–872 | [quy ước](README.md), [engineering](../engineering.md), coverage — G/state/ownership/ràng buộc và câu hỏi |
| [5.8 Migration dữ liệu và kiểm thử RBAC](../../business/database.md), L873–894 | [identity](identity.md) — contract/constraint tra cứu; chi tiết field giữ ở nguồn |
| [Bất biến RBAC cần kiểm thử](../../business/database.md), L885–894 | [identity](identity.md) — contract/constraint tra cứu; chi tiết field giữ ở nguồn |
| [6 Bảng kỹ thuật tối thiểu](../../business/database.md), L895–910 | [quy ước](README.md), [engineering](../engineering.md), coverage — G/state/ownership/ràng buộc và câu hỏi |
| [7 Đối chiếu thay đổi và điểm cần xác nhận](../../business/database.md), L911–944 | [quy ước](README.md), [engineering](../engineering.md), coverage — G/state/ownership/ràng buộc và câu hỏi |
| [7.1 Các quyết định còn mở](../../business/database.md), L933–944 | [quy ước](README.md), [engineering](../engineering.md), coverage — G/state/ownership/ràng buộc và câu hỏi |
| [8 Quy tắc toàn cục, state machine và dòng tiền (chuyển từ FRD mục 4\)](../../business/database.md), L945–1055 | [quy ước](README.md), [engineering](../engineering.md), coverage — G/state/ownership/ràng buộc và câu hỏi |
| [8.1 Quy tắc toàn cục (G-01 đến G-17)](../../business/database.md), L949–970 | [quy ước](README.md), [engineering](../engineering.md), coverage — G/state/ownership/ràng buộc và câu hỏi |
| [8.2 State machine](../../business/database.md), L971–1010 | [quy ước](README.md), [engineering](../engineering.md), coverage — G/state/ownership/ràng buộc và câu hỏi |
| [8.3 Dòng tiền giả lập, hoa hồng và quy tắc ví (WD-01 đến WD-11)](../../business/database.md), L1011–1041 | [finance](finance.md) — contract/constraint tra cứu; chi tiết field giữ ở nguồn |
| [8.4 Bộ giả lập thanh toán nội bộ và lộ trình hai giai đoạn](../../business/database.md), L1042–1055 | [payments](payments.md) — contract/constraint tra cứu; chi tiết field giữ ở nguồn |
| [Đặc tả API và phân quyền RBAC cho hệ thống Ecommerce](../../business/api.md), L1–68 | [engineering](../engineering.md), coverage và hồ sơ consumer — quy ước/internal/event/job/lịch sử; contract tra cứu |
| [1. Giới thiệu](../../business/api.md), L69–100 | [engineering](../engineering.md), coverage và hồ sơ consumer — quy ước/internal/event/job/lịch sử; contract tra cứu |
| [1.1 Mục đích](../../business/api.md), L71–74 | [engineering](../engineering.md), coverage và hồ sơ consumer — quy ước/internal/event/job/lịch sử; contract tra cứu |
| [1.2 Tài liệu nguồn](../../business/api.md), L75–86 | [engineering](../engineering.md), coverage và hồ sơ consumer — quy ước/internal/event/job/lịch sử; contract tra cứu |
| [1.3 Cách đọc](../../business/api.md), L87–100 | [engineering](../engineering.md), coverage và hồ sơ consumer — quy ước/internal/event/job/lịch sử; contract tra cứu |
| [2. Kiến trúc API](../../business/api.md), L101–137 | [engineering](../engineering.md), coverage và hồ sơ consumer — quy ước/internal/event/job/lịch sử; contract tra cứu |
| [2.1 Thành phần](../../business/api.md), L103–113 | [engineering](../engineering.md), coverage và hồ sơ consumer — quy ước/internal/event/job/lịch sử; contract tra cứu |
| [2.2 Định tuyến tại Gateway](../../business/api.md), L114–127 | [engineering](../engineering.md), coverage và hồ sơ consumer — quy ước/internal/event/job/lịch sử; contract tra cứu |
| [2.3 Truyền danh tính từ Gateway xuống service](../../business/api.md), L128–137 | [engineering](../engineering.md), coverage và hồ sơ consumer — quy ước/internal/event/job/lịch sử; contract tra cứu |
| [3. Quy ước chung](../../business/api.md), L138–333 | [engineering](../engineering.md), coverage và hồ sơ consumer — quy ước/internal/event/job/lịch sử; contract tra cứu |
| [3.1 Base URL và versioning](../../business/api.md), L140–150 | [engineering](../engineering.md), coverage và hồ sơ consumer — quy ước/internal/event/job/lịch sử; contract tra cứu |
| [3.2 Xác thực](../../business/api.md), L151–199 | [engineering](../engineering.md), coverage và hồ sơ consumer — quy ước/internal/event/job/lịch sử; contract tra cứu |
| [Quy tắc tài khoản bị cấm (G-14, N-05)](../../business/api.md), L165–175 | [engineering](../engineering.md), coverage và hồ sơ consumer — quy ước/internal/event/job/lịch sử; contract tra cứu |
| [3.2.1 Mô hình RBAC và hợp đồng kiểm tra quyền](../../business/api.md), L176–193 | [engineering](../engineering.md), coverage và hồ sơ consumer — quy ước/internal/event/job/lịch sử; contract tra cứu |
| [3.2.2 Ví dụ response đăng nhập đa vai trò](../../business/api.md), L194–199 | [engineering](../engineering.md), coverage và hồ sơ consumer — quy ước/internal/event/job/lịch sử; contract tra cứu |
| [3.3 Format response chuẩn (envelope)](../../business/api.md), L200–237 | [engineering](../engineering.md), coverage và hồ sơ consumer — quy ước/internal/event/job/lịch sử; contract tra cứu |
| [3.4 HTTP status code](../../business/api.md), L238–254 | [engineering](../engineering.md), coverage và hồ sơ consumer — quy ước/internal/event/job/lịch sử; contract tra cứu |
| [3.5 Phân trang, lọc, sắp xếp](../../business/api.md), L255–264 | [engineering](../engineering.md), coverage và hồ sơ consumer — quy ước/internal/event/job/lịch sử; contract tra cứu |
| [3.6 Quy ước dữ liệu](../../business/api.md), L265–279 | [engineering](../engineering.md), coverage và hồ sơ consumer — quy ước/internal/event/job/lịch sử; contract tra cứu |
| [3.7 Idempotency (chống gửi lặp)](../../business/api.md), L280–293 | [engineering](../engineering.md), coverage và hồ sơ consumer — quy ước/internal/event/job/lịch sử; contract tra cứu |
| [3.8 Cập nhật đồng thời (optimistic locking)](../../business/api.md), L294–301 | [engineering](../engineering.md), coverage và hồ sơ consumer — quy ước/internal/event/job/lịch sử; contract tra cứu |
| [3.9 Upload ảnh/video (presigned URL)](../../business/api.md), L302–323 | [engineering](../engineering.md), coverage và hồ sơ consumer — quy ước/internal/event/job/lịch sử; contract tra cứu |
| [3.10 Header chuẩn](../../business/api.md), L324–333 | [engineering](../engineering.md), coverage và hồ sơ consumer — quy ước/internal/event/job/lịch sử; contract tra cứu |
| [4. Danh mục mã lỗi và enum dùng chung](../../business/api.md), L334–445 | [engineering](../engineering.md), coverage và hồ sơ consumer — quy ước/internal/event/job/lịch sử; contract tra cứu |
| [4.1 Bảng mã lỗi](../../business/api.md), L336–404 | [engineering](../engineering.md), coverage và hồ sơ consumer — quy ước/internal/event/job/lịch sử; contract tra cứu |
| [4.2 Enum](../../business/api.md), L405–445 | [engineering](../engineering.md), coverage và hồ sơ consumer — quy ước/internal/event/job/lịch sử; contract tra cứu |
| [Tab trạng thái đơn trên giao diện (gợi ý cho FE)](../../business/api.md), L439–445 | [engineering](../engineering.md), coverage và hồ sơ consumer — quy ước/internal/event/job/lịch sử; contract tra cứu |
| [5. Ma trận Use case → Service](../../business/api.md), L446–499 | [engineering](../engineering.md), coverage và hồ sơ consumer — quy ước/internal/event/job/lịch sử; contract tra cứu |
| [5.1 Ownership dữ liệu (theo Database RBAC v5.0)](../../business/api.md), L488–499 | [engineering](../engineering.md), coverage và hồ sơ consumer — quy ước/internal/event/job/lịch sử; contract tra cứu |
| [6. Đặc tả theo service](../../business/api.md), L500–2751 | [engineering](../engineering.md), coverage và hồ sơ consumer — quy ước/internal/event/job/lịch sử; contract tra cứu |
| [6.1 Identity & Seller Service](../../business/api.md), L504–1139 | [identity](identity.md) — route/quyền đã ánh xạ; DTO/HTTP/error chi tiết giữ ở nguồn, áp dụng DELTA |
| [Danh sách endpoint](../../business/api.md), L508–544 | [identity](identity.md) — route/quyền đã ánh xạ; DTO/HTTP/error chi tiết giữ ở nguồn, áp dụng DELTA |
| [DTO](../../business/api.md), L545–557 | [identity](identity.md) — route/quyền đã ánh xạ; DTO/HTTP/error chi tiết giữ ở nguồn, áp dụng DELTA |
| [Chi tiết endpoint](../../business/api.md), L558–1083 | [identity](identity.md) — route/quyền đã ánh xạ; DTO/HTTP/error chi tiết giữ ở nguồn, áp dụng DELTA |
| [POST  /identity/admin/ban/{userId}  —  Cấm theo phạm vi](../../business/api.md), L1084–1093 | [identity](identity.md) — route/quyền đã ánh xạ; DTO/HTTP/error chi tiết giữ ở nguồn, áp dụng DELTA |
| [POST  /identity/admin/unban/{userId}  —  Gỡ cấm theo phạm vi](../../business/api.md), L1094–1103 | [identity](identity.md) — route/quyền đã ánh xạ; DTO/HTTP/error chi tiết giữ ở nguồn, áp dụng DELTA |
| [POST  /identity/users/me/seller-application  —  Nộp hồ sơ bán hàng](../../business/api.md), L1104–1113 | [identity](identity.md) — route/quyền đã ánh xạ; DTO/HTTP/error chi tiết giữ ở nguồn, áp dụng DELTA |
| [GET  /identity/users/me/seller-application  —  Xem hồ sơ xét duyệt của mình](../../business/api.md), L1114–1123 | [identity](identity.md) — route/quyền đã ánh xạ; DTO/HTTP/error chi tiết giữ ở nguồn, áp dụng DELTA |
| [POST  /identity/users/me/customer-profile  —  Tạo hồ sơ Customer bổ sung](../../business/api.md), L1124–1133 | [identity](identity.md) — route/quyền đã ánh xạ; DTO/HTTP/error chi tiết giữ ở nguồn, áp dụng DELTA |
| [Nguồn dữ liệu địa giới](../../business/api.md), L1134–1139 | [identity](identity.md) — route/quyền đã ánh xạ; DTO/HTTP/error chi tiết giữ ở nguồn, áp dụng DELTA |
| [6.2 Product Service](../../business/api.md), L1140–1555 | [catalog](catalog.md) — route/quyền đã ánh xạ; DTO/HTTP/error chi tiết giữ ở nguồn, áp dụng DELTA |
| [Danh sách endpoint](../../business/api.md), L1144–1168 | [catalog](catalog.md) — route/quyền đã ánh xạ; DTO/HTTP/error chi tiết giữ ở nguồn, áp dụng DELTA |
| [DTO](../../business/api.md), L1169–1180 | [catalog](catalog.md) — route/quyền đã ánh xạ; DTO/HTTP/error chi tiết giữ ở nguồn, áp dụng DELTA |
| [Chi tiết endpoint](../../business/api.md), L1181–1555 | [catalog](catalog.md) — route/quyền đã ánh xạ; DTO/HTTP/error chi tiết giữ ở nguồn, áp dụng DELTA |
| [6.3 Cart Service](../../business/api.md), L1556–1678 | [checkout-orders](checkout-orders.md) — route/quyền đã ánh xạ; DTO/HTTP/error chi tiết giữ ở nguồn, áp dụng DELTA |
| [Danh sách endpoint](../../business/api.md), L1562–1571 | [checkout-orders](checkout-orders.md) — route/quyền đã ánh xạ; DTO/HTTP/error chi tiết giữ ở nguồn, áp dụng DELTA |
| [DTO](../../business/api.md), L1572–1578 | [checkout-orders](checkout-orders.md) — route/quyền đã ánh xạ; DTO/HTTP/error chi tiết giữ ở nguồn, áp dụng DELTA |
| [Chi tiết endpoint](../../business/api.md), L1579–1678 | [checkout-orders](checkout-orders.md) — route/quyền đã ánh xạ; DTO/HTTP/error chi tiết giữ ở nguồn, áp dụng DELTA |
| [6.4 Order Service (Order Inventory Fulfillment)](../../business/api.md), L1679–2222 | [checkout-orders](checkout-orders.md) — route/quyền đã ánh xạ; DTO/HTTP/error chi tiết giữ ở nguồn, áp dụng DELTA |
| [Luồng chính của Customer](../../business/api.md), L1683–1692 | [checkout-orders](checkout-orders.md) — route/quyền đã ánh xạ; DTO/HTTP/error chi tiết giữ ở nguồn, áp dụng DELTA |
| [State machine của Order (DB 8.2B)](../../business/api.md), L1693–1705 | [checkout-orders](checkout-orders.md) — route/quyền đã ánh xạ; DTO/HTTP/error chi tiết giữ ở nguồn, áp dụng DELTA |
| [Danh sách endpoint](../../business/api.md), L1706–1732 | [checkout-orders](checkout-orders.md) — route/quyền đã ánh xạ; DTO/HTTP/error chi tiết giữ ở nguồn, áp dụng DELTA |
| [DTO](../../business/api.md), L1733–1749 | [checkout-orders](checkout-orders.md) — route/quyền đã ánh xạ; DTO/HTTP/error chi tiết giữ ở nguồn, áp dụng DELTA |
| [Chi tiết endpoint](../../business/api.md), L1750–2222 | [checkout-orders](checkout-orders.md) — route/quyền đã ánh xạ; DTO/HTTP/error chi tiết giữ ở nguồn, áp dụng DELTA |
| [6.5 Payment Service](../../business/api.md), L2223–2423 | [payments](payments.md) — route/quyền đã ánh xạ; DTO/HTTP/error chi tiết giữ ở nguồn, áp dụng DELTA |
| [Danh sách endpoint](../../business/api.md), L2227–2240 | [payments](payments.md) — route/quyền đã ánh xạ; DTO/HTTP/error chi tiết giữ ở nguồn, áp dụng DELTA |
| [DTO](../../business/api.md), L2241–2247 | [payments](payments.md) — route/quyền đã ánh xạ; DTO/HTTP/error chi tiết giữ ở nguồn, áp dụng DELTA |
| [Chi tiết endpoint](../../business/api.md), L2248–2409 | [payments](payments.md) — route/quyền đã ánh xạ; DTO/HTTP/error chi tiết giữ ở nguồn, áp dụng DELTA |
| [6.5.1 Bộ giả lập thanh toán nội bộ (thay cho Mock Provider)](../../business/api.md), L2410–2423 | [payments](payments.md) — route/quyền đã ánh xạ; DTO/HTTP/error chi tiết giữ ở nguồn, áp dụng DELTA |
| [6.6 Finance Service](../../business/api.md), L2424–2664 | [finance](finance.md) — route/quyền đã ánh xạ; DTO/HTTP/error chi tiết giữ ở nguồn, áp dụng DELTA |
| [Danh sách endpoint](../../business/api.md), L2437–2453 | [finance](finance.md) — route/quyền đã ánh xạ; DTO/HTTP/error chi tiết giữ ở nguồn, áp dụng DELTA |
| [DTO](../../business/api.md), L2454–2462 | [finance](finance.md) — route/quyền đã ánh xạ; DTO/HTTP/error chi tiết giữ ở nguồn, áp dụng DELTA |
| [Chi tiết endpoint](../../business/api.md), L2463–2664 | [finance](finance.md) — route/quyền đã ánh xạ; DTO/HTTP/error chi tiết giữ ở nguồn, áp dụng DELTA |
| [6.7 Notification Service](../../business/api.md), L2665–2751 | [notifications](notifications.md) — route/quyền đã ánh xạ; DTO/HTTP/error chi tiết giữ ở nguồn, áp dụng DELTA |
| [Danh sách endpoint](../../business/api.md), L2685–2694 | [notifications](notifications.md) — route/quyền đã ánh xạ; DTO/HTTP/error chi tiết giữ ở nguồn, áp dụng DELTA |
| [DTO](../../business/api.md), L2695–2700 | [notifications](notifications.md) — route/quyền đã ánh xạ; DTO/HTTP/error chi tiết giữ ở nguồn, áp dụng DELTA |
| [Chi tiết endpoint](../../business/api.md), L2701–2741 | [notifications](notifications.md) — route/quyền đã ánh xạ; DTO/HTTP/error chi tiết giữ ở nguồn, áp dụng DELTA |
| [DELETE  /notifications/me/{notificationId}  —  Xóa thông báo của tôi](../../business/api.md), L2742–2751 | [notifications](notifications.md) — route/quyền đã ánh xạ; DTO/HTTP/error chi tiết giữ ở nguồn, áp dụng DELTA |
| [7. API nội bộ và sự kiện giữa các service](../../business/api.md), L2752–2838 | [engineering](../engineering.md), coverage và hồ sơ consumer — quy ước/internal/event/job/lịch sử; contract tra cứu |
| [7.1 Internal API (đồng bộ)](../../business/api.md), L2754–2795 | [engineering](../engineering.md), coverage và hồ sơ consumer — quy ước/internal/event/job/lịch sử; contract tra cứu |
| [7.1.1 Tạo thông báo nội bộ](../../business/api.md), L2780–2789 | [engineering](../engineering.md), coverage và hồ sơ consumer — quy ước/internal/event/job/lịch sử; contract tra cứu |
| [7.1.2 Nguồn quyền và dữ liệu liên service](../../business/api.md), L2790–2795 | [engineering](../engineering.md), coverage và hồ sơ consumer — quy ước/internal/event/job/lịch sử; contract tra cứu |
| [7.2 Sự kiện (bất đồng bộ, qua Outbox)](../../business/api.md), L2796–2825 | [engineering](../engineering.md), coverage và hồ sơ consumer — quy ước/internal/event/job/lịch sử; contract tra cứu |
| [7.3 Job nền](../../business/api.md), L2826–2838 | [engineering](../engineering.md), coverage và hồ sơ consumer — quy ước/internal/event/job/lịch sử; contract tra cứu |
| [8. Luồng tích hợp tiêu biểu](../../business/api.md), L2839–2871 | [engineering](../engineering.md), coverage và hồ sơ consumer — quy ước/internal/event/job/lịch sử; contract tra cứu |
| [8.1 Checkout nhiều cửa hàng và thanh toán](../../business/api.md), L2841–2851 | [engineering](../engineering.md), coverage và hồ sơ consumer — quy ước/internal/event/job/lịch sử; contract tra cứu |
| [8.2 Seller từ chối giao hàng](../../business/api.md), L2852–2861 | [engineering](../engineering.md), coverage và hồ sơ consumer — quy ước/internal/event/job/lịch sử; contract tra cứu |
| [8.3 Xác nhận nhận hàng và rút tiền](../../business/api.md), L2862–2871 | [engineering](../engineering.md), coverage và hồ sơ consumer — quy ước/internal/event/job/lịch sử; contract tra cứu |
| [9. Mâu thuẫn tài liệu và điểm cần chốt](../../business/api.md), L2872–2918 | [engineering](../engineering.md), coverage và hồ sơ consumer — quy ước/internal/event/job/lịch sử; contract tra cứu |
| [9.1 Tham số chưa chốt ảnh hưởng tới API](../../business/api.md), L2892–2910 | [engineering](../engineering.md), coverage và hồ sơ consumer — quy ước/internal/event/job/lịch sử; contract tra cứu |
| [9.2 Giới hạn của tài liệu này](../../business/api.md), L2911–2918 | [engineering](../engineering.md), coverage và hồ sơ consumer — quy ước/internal/event/job/lịch sử; contract tra cứu |
| [10. Quy trình sử dụng và nhật ký thay đổi](../../business/api.md), L2919–2979 | [engineering](../engineering.md), coverage và hồ sơ consumer — quy ước/internal/event/job/lịch sử; contract tra cứu |
| [10.1 Quy trình đề xuất](../../business/api.md), L2921–2930 | [engineering](../engineering.md), coverage và hồ sơ consumer — quy ước/internal/event/job/lịch sử; contract tra cứu |
| [10.2 Nhật ký thay đổi](../../business/api.md), L2931–2940 | [engineering](../engineering.md), coverage và hồ sơ consumer — quy ước/internal/event/job/lịch sử; contract tra cứu |
| [10.3 Đối chiếu comment của Bùi Minh Quang](../../business/api.md), L2941–2955 | [engineering](../engineering.md), coverage và hồ sơ consumer — quy ước/internal/event/job/lịch sử; contract tra cứu |
| [10.4 Tác động lên schema và chuyển đổi API](../../business/api.md), L2956–2969 | [engineering](../engineering.md), coverage và hồ sơ consumer — quy ước/internal/event/job/lịch sử; contract tra cứu |
| [10.5 Kiểm thử chấp nhận RBAC và comment](../../business/api.md), L2970–2979 | [engineering](../engineering.md), coverage và hồ sơ consumer — quy ước/internal/event/job/lịch sử; contract tra cứu |
| [TÀI LIỆU KIẾN TRÚC PHẦN MỀM](../../business/Software-Architecture.md), L1–1132 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [Hướng dẫn sử dụng tài liệu](../../business/Software-Architecture.md), L15–38 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [Cách hiểu mức độ ràng buộc](../../business/Software-Architecture.md), L31–38 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [Mục lục](../../business/Software-Architecture.md), L39–56 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [1. Bối cảnh và phạm vi kiến trúc](../../business/Software-Architecture.md), L57–116 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [1.1. Mục đích và quan hệ với hồ sơ dự án](../../business/Software-Architecture.md), L59–74 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [1.2. Phạm vi phiên bản MVP](../../business/Software-Architecture.md), L75–82 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [1.3. Ràng buộc và nền tảng đã chốt](../../business/Software-Architecture.md), L83–99 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [1.4. Các yêu cầu chi phối thiết kế](../../business/Software-Architecture.md), L100–116 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [2. Cấu trúc phần mềm và tổ chức mã nguồn](../../business/Software-Architecture.md), L117–196 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [2.1. Bản đồ trách nhiệm và dữ liệu](../../business/Software-Architecture.md), L121–137 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [2.2. Cấu trúc backend và hướng phụ thuộc](../../business/Software-Architecture.md), L138–168 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [2.3. Cấu trúc React SPA](../../business/Software-Architecture.md), L169–186 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [2.4. Gateway, Discovery và biên mở rộng](../../business/Software-Architecture.md), L187–196 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [3. Thiết kế bên trong từng service](../../business/Software-Architecture.md), L197–321 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [3.1. Identity — tài khoản, hồ sơ và quyền](../../business/Software-Architecture.md), L201–217 | [identity](identity.md) — yêu cầu baseline và biên liên service |
| [3.2. Product — sản phẩm và tìm kiếm](../../business/Software-Architecture.md), L218–234 | [catalog](catalog.md) — yêu cầu baseline và biên liên service |
| [3.3. Cart — giỏ hàng](../../business/Software-Architecture.md), L235–251 | [checkout-orders](checkout-orders.md) — yêu cầu baseline và biên liên service |
| [3.4. Order — checkout, đơn hàng và tồn kho](../../business/Software-Architecture.md), L252–268 | [checkout-orders](checkout-orders.md) — yêu cầu baseline và biên liên service |
| [3.5. Payment — thanh toán và hoàn tiền](../../business/Software-Architecture.md), L269–285 | [payments](payments.md) — yêu cầu baseline và biên liên service |
| [3.6. Finance — quyết toán, ví và rút tiền](../../business/Software-Architecture.md), L286–302 | [finance](finance.md) — yêu cầu baseline và biên liên service |
| [3.7. Notification — thông báo](../../business/Software-Architecture.md), L303–321 | [notifications](notifications.md) — yêu cầu baseline và biên liên service |
| [4. Giao tiếp giữa các thành phần](../../business/Software-Architecture.md), L322–439 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [4.1. Khi nào dùng REST, khi nào dùng Kafka?](../../business/Software-Architecture.md), L324–337 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [4.2. Góc nhìn REST](../../business/Software-Architecture.md), L338–368 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [4.3. Góc nhìn sự kiện Kafka](../../business/Software-Architecture.md), L369–417 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [4.4. Hợp đồng public/internal và quản lý thay đổi](../../business/Software-Architecture.md), L418–439 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [5. Các luồng nghiệp vụ xuyên service](../../business/Software-Architecture.md), L440–591 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [5.1. Đặt hàng từ giỏ hoặc Mua ngay](../../business/Software-Architecture.md), L444–498 | [checkout-orders](checkout-orders.md) — yêu cầu baseline và biên liên service |
| [5.2. Giá thay đổi, timeout và các kết quả không rõ ràng](../../business/Software-Architecture.md), L499–521 | [checkout-orders](checkout-orders.md) — yêu cầu baseline và biên liên service |
| [5.3. Hủy đơn và hoàn tiền](../../business/Software-Architecture.md), L522–540 | [payments](payments.md) — yêu cầu baseline và biên liên service |
| [5.4. Seller thực hiện đơn và Customer xác nhận](../../business/Software-Architecture.md), L541–558 | [checkout-orders](checkout-orders.md) — yêu cầu baseline và biên liên service |
| [5.5. Quyết toán sau xác nhận nhận hàng](../../business/Software-Architecture.md), L559–571 | [finance](finance.md) — yêu cầu baseline và biên liên service |
| [5.6. Seller rút tiền giả lập](../../business/Software-Architecture.md), L572–579 | [finance](finance.md) — yêu cầu baseline và biên liên service |
| [5.7. Tạo sản phẩm và khởi tạo tồn](../../business/Software-Architecture.md), L580–591 | [catalog](catalog.md) — yêu cầu baseline và biên liên service |
| [6. Các quy tắc thiết kế dùng chung](../../business/Software-Architecture.md), L592–707 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [6.1. Xác thực, quyền hiện hành và ownership](../../business/Software-Architecture.md), L596–601 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [6.2. Chính sách cấm và thu hồi phiên](../../business/Software-Architecture.md), L602–615 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [6.3. Phiên frontend, cookie và bí mật](../../business/Software-Architecture.md), L616–623 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [6.4. Xác thực lời gọi nội bộ](../../business/Software-Architecture.md), L624–631 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [6.5. Transaction, tồn kho và chống trùng](../../business/Software-Architecture.md), L632–664 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [6.6. Tiền, ledger và đối soát](../../business/Software-Architecture.md), L665–686 | [finance](finance.md) — yêu cầu baseline và biên liên service |
| [6.7. Quyền sở hữu dữ liệu và các dữ liệu hỗ trợ](../../business/Software-Architecture.md), L687–707 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [7. Quyết định kiến trúc và nội dung cần chốt](../../business/Software-Architecture.md), L708–775 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [7.1. Sổ quyết định đã xác nhận](../../business/Software-Architecture.md), L712–724 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [7.2. Các bổ sung hợp đồng cần review trước triển khai](../../business/Software-Architecture.md), L725–740 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [7.3. Tham số còn mở](../../business/Software-Architecture.md), L741–760 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [7.4. Rủi ro và đánh đổi](../../business/Software-Architecture.md), L761–775 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [8. Kiểm chứng kiến trúc và điều kiện nghiệm thu](../../business/Software-Architecture.md), L776–798 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [8.1. Điều kiện hoàn thành phiên bản MVP](../../business/Software-Architecture.md), L791–798 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [Phụ lục A — Ghi chú thiết kế chi tiết để triển khai](../../business/Software-Architecture.md), L799–967 | Đề xuất kỹ thuật phụ lục A; giữ chi tiết ở nguồn, DELTA cần review trước code |
| [A.1. Checkout: công việc bền vững, dispatch và đóng quá hạn](../../business/Software-Architecture.md), L803–833 | Đề xuất kỹ thuật phụ lục A; giữ chi tiết ở nguồn, DELTA cần review trước code |
| [A.1.1. Lưu ý định và ranh giới transaction](../../business/Software-Architecture.md), L805–819 | Đề xuất kỹ thuật phụ lục A; giữ chi tiết ở nguồn, DELTA cần review trước code |
| [A.1.2. Đóng phiên, đối soát và success muộn](../../business/Software-Architecture.md), L820–833 | Đề xuất kỹ thuật phụ lục A; giữ chi tiết ở nguồn, DELTA cần review trước code |
| [A.2. Ranh giới lớp và transaction Spring](../../business/Software-Architecture.md), L834–841 | Đề xuất kỹ thuật phụ lục A; giữ chi tiết ở nguồn, DELTA cần review trước code |
| [A.3. Identity lifecycle và phương án service token](../../business/Software-Architecture.md), L842–857 | Đề xuất kỹ thuật phụ lục A; giữ chi tiết ở nguồn, DELTA cần review trước code |
| [A.3.1. Vòng đời tài khoản và hồ sơ](../../business/Software-Architecture.md), L844–849 | Đề xuất kỹ thuật phụ lục A; giữ chi tiết ở nguồn, DELTA cần review trước code |
| [A.3.2. Phương án credential service đề xuất](../../business/Software-Architecture.md), L850–857 | Đề xuất kỹ thuật phụ lục A; giữ chi tiết ở nguồn, DELTA cần review trước code |
| [A.4. Simulator, topology Kafka và envelope](../../business/Software-Architecture.md), L858–888 | Đề xuất kỹ thuật phụ lục A; giữ chi tiết ở nguồn, DELTA cần review trước code |
| [A.4.1. PaymentGateway và simulator](../../business/Software-Architecture.md), L860–865 | Đề xuất kỹ thuật phụ lục A; giữ chi tiết ở nguồn, DELTA cần review trước code |
| [A.4.2. Topology đề xuất](../../business/Software-Architecture.md), L866–871 | Đề xuất kỹ thuật phụ lục A; giữ chi tiết ở nguồn, DELTA cần review trước code |
| [A.4.3. Envelope và dữ liệu còn thiếu trong contract](../../business/Software-Architecture.md), L872–888 | Đề xuất kỹ thuật phụ lục A; giữ chi tiết ở nguồn, DELTA cần review trước code |
| [A.5. Relay, Inbox và áp dụng thay đổi giỏ/ví](../../business/Software-Architecture.md), L889–912 | Đề xuất kỹ thuật phụ lục A; giữ chi tiết ở nguồn, DELTA cần review trước code |
| [A.5.1. Outbox relay](../../business/Software-Architecture.md), L891–896 | Đề xuất kỹ thuật phụ lục A; giữ chi tiết ở nguồn, DELTA cần review trước code |
| [A.5.2. Inbox và sự kiện chờ phụ thuộc](../../business/Software-Architecture.md), L897–904 | Đề xuất kỹ thuật phụ lục A; giữ chi tiết ở nguồn, DELTA cần review trước code |
| [A.5.3. Áp dụng checkout.paid vào Cart](../../business/Software-Architecture.md), L905–912 | Đề xuất kỹ thuật phụ lục A; giữ chi tiết ở nguồn, DELTA cần review trước code |
| [A.6. Product, địa chỉ, media và delivery](../../business/Software-Architecture.md), L913–942 | Đề xuất kỹ thuật phụ lục A; giữ chi tiết ở nguồn, DELTA cần review trước code |
| [A.6.1. Product và tìm kiếm](../../business/Software-Architecture.md), L915–924 | Đề xuất kỹ thuật phụ lục A; giữ chi tiết ở nguồn, DELTA cần review trước code |
| [A.6.2. Địa chỉ và địa giới](../../business/Software-Architecture.md), L925–930 | Đề xuất kỹ thuật phụ lục A; giữ chi tiết ở nguồn, DELTA cần review trước code |
| [A.6.3. Media](../../business/Software-Architecture.md), L931–934 | Đề xuất kỹ thuật phụ lục A; giữ chi tiết ở nguồn, DELTA cần review trước code |
| [A.6.4. Notification và email demo](../../business/Software-Architecture.md), L935–942 | Đề xuất kỹ thuật phụ lục A; giữ chi tiết ở nguồn, DELTA cần review trước code |
| [A.7. Ranh giới mở rộng Should/Could](../../business/Software-Architecture.md), L943–946 | Đề xuất kỹ thuật phụ lục A; giữ chi tiết ở nguồn, DELTA cần review trước code |
| [A.8. Checklist constraint và migration](../../business/Software-Architecture.md), L947–967 | Đề xuất kỹ thuật phụ lục A; giữ chi tiết ở nguồn, DELTA cần review trước code |
| [Phụ lục B — Danh mục kiểm thử và truy vết](../../business/Software-Architecture.md), L968–1028 | Đề xuất kỹ thuật phụ lục A; giữ chi tiết ở nguồn, DELTA cần review trước code |
| [B.1. Chiến lược kiểm thử](../../business/Software-Architecture.md), L972–977 | AT-01…26 phân chủ sở hữu; chiến lược test engineering, trace nguồn giữ tham chiếu |
| [B.2. Ma trận 26 kịch bản nghiệm thu](../../business/Software-Architecture.md), L978–1008 | AT-01…26 phân chủ sở hữu; chiến lược test engineering, trace nguồn giữ tham chiếu |
| [B.3. Truy vết yêu cầu → thiết kế → kiểm chứng](../../business/Software-Architecture.md), L1009–1028 | AT-01…26 phân chủ sở hữu; chiến lược test engineering, trace nguồn giữ tham chiếu |
| [Phụ lục C — Hiện trạng, triển khai và vận hành](../../business/Software-Architecture.md), L1029–1098 | AT-01…26 phân chủ sở hữu; chiến lược test engineering, trace nguồn giữ tham chiếu |
| [C.1. Khoảng cách với code tại lần rà soát 10/11/2026](../../business/Software-Architecture.md), L1033–1051 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [C.2. Các mốc triển khai và điều kiện đóng](../../business/Software-Architecture.md), L1052–1064 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [C.3. Danh mục tác vụ nền](../../business/Software-Architecture.md), L1065–1082 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [C.4. Quan sát và runbook](../../business/Software-Architecture.md), L1083–1090 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [C.5. Triển khai, backup và phục hồi](../../business/Software-Architecture.md), L1091–1098 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [Phụ lục D — Nguồn, thuật ngữ và lịch sử](../../business/Software-Architecture.md), L1099–1132 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [D.1. Hồ sơ đầu vào và quy tắc ưu tiên](../../business/Software-Architecture.md), L1101–1116 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [D.2. Nguồn tham khảo kỹ thuật](../../business/Software-Architecture.md), L1117–1122 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [D.3. Thuật ngữ](../../business/Software-Architecture.md), L1123–1126 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [D.4. Lịch sử phiên bản](../../business/Software-Architecture.md), L1127–1132 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [Tài liệu Kiến trúc hệ thống Ecommerce](../../business/system-architecture.md), L1–2 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [1. Giới thiệu và phạm vi hệ thống](../../business/system-architecture.md), L3–63 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [1.1. Tổng quan dự án](../../business/system-architecture.md), L5–12 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [1.2. Đối tượng sử dụng](../../business/system-architecture.md), L13–23 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [1.2.1. Người sử dụng hệ thống](../../business/system-architecture.md), L15–23 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [1.3. Phạm vi chức năng](../../business/system-architecture.md), L24–41 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [1.4. Phạm vi kỹ thuật và giới hạn](../../business/system-architecture.md), L42–63 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [1.4.1. Phạm vi kỹ thuật](../../business/system-architecture.md), L44–55 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [1.4.2. Giới hạn phạm vi](../../business/system-architecture.md), L56–63 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [2. Yêu cầu kiến trúc và lựa chọn công nghệ](../../business/system-architecture.md), L64–217 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [2.1. Các yêu cầu nghiệp vụ chi phối kiến trúc](../../business/system-architecture.md), L66–81 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [2.2. Yêu cầu chất lượng hệ thống](../../business/system-architecture.md), L82–120 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [2.2.1. Bảo mật](../../business/system-architecture.md), L84–91 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [2.2.2. Tính nhất quán và độ tin cậy](../../business/system-architecture.md), L92–97 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [2.2.3. Hiệu năng và khả năng mở rộng](../../business/system-architecture.md), L98–103 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [2.2.4. Khả năng vận hành và phục hồi](../../business/system-architecture.md), L104–107 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [2.2.5. Tiêu chí kiểm chứng](../../business/system-architecture.md), L108–120 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [2.3. Ràng buộc thiết kế và triển khai](../../business/system-architecture.md), L121–126 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [2.4. Lựa chọn kiến trúc](../../business/system-architecture.md), L127–166 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [2.4.1. Chia hệ thống theo nghiệp vụ rõ ràng](../../business/system-architecture.md), L133–138 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [2.4.2. Mở rộng riêng service cần thêm tài nguyên](../../business/system-architecture.md), L139–144 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [2.4.3. Cập nhật và triển khai từng service](../../business/system-architecture.md), L145–150 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [2.4.4. Giảm ảnh hưởng khi một service gặp lỗi](../../business/system-architecture.md), L151–158 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [2.4.6. Nguyên tắc áp dụng](../../business/system-architecture.md), L159–166 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [2.5. Lựa chọn công nghệ](../../business/system-architecture.md), L167–217 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [2.5.1. Lý do lựa chọn AWS](../../business/system-architecture.md), L189–199 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [2.5.2. Lý do lựa chọn Apache Kafka](../../business/system-architecture.md), L200–217 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [3. Kiến trúc tổng thể và phân chia service](../../business/system-architecture.md), L218–919 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [3.1. Tổng quan kiến trúc](../../business/system-architecture.md), L220–345 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [3.1.1. C4 Level 2 - Container Diagram](../../business/system-architecture.md), L261–345 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [3.2. Các lớp thành phần](../../business/system-architecture.md), L346–358 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [3.3. Phân chia trách nhiệm và dữ liệu](../../business/system-architecture.md), L359–382 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [3.4. Vai trò của Gateway và Discovery](../../business/system-architecture.md), L383–398 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [3.4.1. API Gateway](../../business/system-architecture.md), L385–392 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [3.4.2. Discovery Service](../../business/system-architecture.md), L393–398 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [3.5. Giao tiếp đồng bộ qua API](../../business/system-architecture.md), L399–441 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [3.6. Giao tiếp bất đồng bộ qua Kafka](../../business/system-architecture.md), L442–485 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [3.7. Tổ chức dữ liệu và media](../../business/system-architecture.md), L486–493 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [3.8. C4 Level 3 - Component Diagram](../../business/system-architecture.md), L494–919 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [3.8.1. Identity Service](../../business/system-architecture.md), L504–567 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [3.8.2. Product Service](../../business/system-architecture.md), L568–613 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [3.8.3. Cart Service](../../business/system-architecture.md), L614–651 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [3.8.4. Order Service](../../business/system-architecture.md), L652–701 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [3.8.5. Payment Service](../../business/system-architecture.md), L702–748 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [3.8.6. Finance Service](../../business/system-architecture.md), L749–799 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [3.8.7. Notification Service](../../business/system-architecture.md), L800–843 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [3.8.8. Gateway Service](../../business/system-architecture.md), L844–883 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [3.8.9. Discovery Service](../../business/system-architecture.md), L884–911 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [3.8.10. Nguyên tắc triển khai từ Component Diagram](../../business/system-architecture.md), L912–919 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [4. Kiến trúc dữ liệu và tính nhất quán](../../business/system-architecture.md), L920–1136 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [4.1. Nguyên tắc tổ chức dữ liệu](../../business/system-architecture.md), L922–942 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [4.2. Mô hình dữ liệu tổng quan](../../business/system-architecture.md), L943–981 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [4.3. Snapshot và quy ước lưu trữ](../../business/system-architecture.md), L982–1004 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [4.3.1. Snapshot giao dịch](../../business/system-architecture.md), L984–996 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [4.3.2. Kiểu dữ liệu và media](../../business/system-architecture.md), L997–1004 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [4.4. Transaction và cập nhật đồng thời](../../business/system-architecture.md), L1005–1032 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [4.4.1. Transaction trong từng service](../../business/system-architecture.md), L1007–1021 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [4.4.2. Kiểm soát thao tác đồng thời](../../business/system-architecture.md), L1022–1032 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [4.5. Tính nhất quán của tồn kho](../../business/system-architecture.md), L1033–1052 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [4.6. Tính nhất quán của thanh toán và ví](../../business/system-architecture.md), L1053–1081 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [4.7. Nhất quán giữa các service](../../business/system-architecture.md), L1082–1126 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [4.7.1. Outbox, Inbox và idempotency](../../business/system-architecture.md), L1108–1114 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [4.7.2. Event sai thứ tự và thiếu dữ liệu phụ thuộc](../../business/system-architecture.md), L1115–1120 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [4.7.3. Retry, xử lý bù và đối soát](../../business/system-architecture.md), L1121–1126 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [4.8. Vòng đời dữ liệu và thay đổi cấu trúc](../../business/system-architecture.md), L1127–1136 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |

<a id="kiem-tra"></a>
## Cách kiểm tra độ đầy đủ

1. So manifest bytes nguồn; nếu hash đổi, đọc diff nguồn và review lại clause/line/coverage.
2. Mỗi UC có một đích hoặc lý do Should/Could/loại bỏ; mỗi Must giữ điều kiện,
   bước, ngoại lệ, hậu điều kiện, note. Clause hiệu chỉnh phải có cả nguồn cũ và baseline mới.
3. So mỗi G/WD/AT với nguồn; không đếm số mã thay cho đọc đủ điều kiện, ngoại lệ,
   required/optional, rollback, quyền và ownership. Field chi tiết cần mở section contract.
4. So ngược từng REQ/AC về source được dẫn; phân biệt phương án phụ lục với yêu cầu.
   Tham số dev không được đổi thành policy. Chưa chạy test thì giữ trạng thái chưa chạy.
5. Thử task mới từ AGENTS→docs map: Seller profile (User/Seller/auth/views/migration/test),
   xóa địa chỉ (G-13/snapshot/touch/test), rút tiền (WD/rounding/idempotency/Finance/Identity).
   Mỗi task phải xác định được authority, phạm vi và proof, không đọc tuần tự 5 nguồn.

Lệnh dưới đây kiểm tra **cấu trúc/link/hash** từ root, không chạy ứng dụng, không ghi file.
Nó không chứng minh diễn giải đúng ngữ nghĩa, tính khả thi contract hoặc test runtime pass.

```bash
python3 -B - <<'PY'
from pathlib import Path
from urllib.parse import unquote
import hashlib
import re

coverage = Path('docs/requirements/coverage.md').read_text()
inputs = re.findall(r'^\| `(business/[^`]+)` \|.*?\| `([0-9a-f]{64})` \|$', coverage, re.M)
assert len(inputs) == 5, 'Manifest nguồn phải có 5 file'
for name, expected in inputs:
    assert hashlib.sha256(Path(name).read_bytes()).hexdigest() == expected, name
files = [Path('AGENTS.md'), Path('docs/README.md'), Path('docs/engineering.md'), *Path('docs/requirements').glob('*.md')]
definitions = []
for p in files:
    text = p.read_text()
    outside_code = re.sub(r'```.*?```', '', text, flags=re.S)
    definitions += re.findall(r'<a id="([^"]+)"></a>', outside_code)
    for raw in re.findall(r'\]\(([^)]+)\)', outside_code):
        if '://' in raw or raw.startswith('mailto:'):
            continue
        name, _, anchor = unquote(raw).partition('#')
        target = p.parent / name if name else p
        assert target.exists(), (p, raw)
        if anchor:
            assert f'id="{anchor}"' in target.read_text(), (p, raw, 'anchor')
assert len(definitions) == len(set(definitions)), 'Mã/anchor bị lặp'
for prefix, count in [('g', 17), ('wd', 11), ('at', 26)]:
    assert all(f'{prefix}-{n:02d}' in definitions for n in range(1, count+1)), prefix
assert len([x for x in definitions if re.fullmatch(r'uc-[a-g][0-9]{2}', x)]) == 37
print('PASS: 5 nguồn nguyên vẹn; local links/anchors; 37 UC, 17 G, 11 WD, 26 AT')
PY
```

Lưu kết quả semantic review, protected-file comparison và giới hạn validation trong
kế hoạch completed. Số liệu “đã chuyển” là tài liệu, không là số feature đã hoàn thành.
