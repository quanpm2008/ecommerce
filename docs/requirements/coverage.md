# Nguồn, độ phủ và phần còn mở

<a id="the-15"></a>
## Quyết định Thẻ 15 — 17/11/2026

Nguồn: [Thẻ 15](https://docs.google.com/document/d/1VukChbA8p9eOU_loe7y_RVCFvdkzIeMDQCWBoUn2nRI/edit?tab=t.9bkh3t22k4xx), cùng kế hoạch được người dùng phê duyệt ngày 17/11/2026. Các quyết định sau thay thế riêng các đoạn mâu thuẫn về ban/provision/trigger trong baseline cũ; không thay thế toàn bộ SAD.

- Tài khoản Customer/Seller/Admin độc lập, ban không chọn scope; không cấm Admin hoặc Seller chưa APPROVED.
- BANNED khi còn nghĩa vụ đơn, Payment/Refund chưa xử lý xong; Seller thêm Settlement/hiệu ứng tài chính chưa xong, pending hoặc available. Chỉ DISABLED sau khi xác minh hết nghĩa vụ. Lỗi nguồn không có nghĩa số dư/nghĩa vụ bằng 0.
- J7 do Identity sở hữu đối soát BANNED/DISABLED; nghĩa vụ đến muộn mở lại BANNED giới hạn, không tự unban. Không tự hủy đơn/đóng băng available chỉ vì ban.
- BANNED được gỡ bất kỳ lúc nào; DISABLED được gỡ đến hết 30 ngày từ lần đầu DISABLED trong đợt cấm. Ban/job lặp không gia hạn. Quá hạn không xóa dữ liệu hoặc tiền.
- Seller BANNED được rút toàn bộ available dương dưới 10.000đ, không rút một phần dưới ngưỡng; Seller ACTIVE giữ ngưỡng 10.000đ, pending không rút.
- UC-D01 thiếu bước đã có trong API/SAD: tạo Product HIDDEN cùng công việc provision bền vững; xác nhận Inventory mới được áp dụng trạng thái yêu cầu nếu còn hợp lệ; timeout giữ HIDDEN/retry cùng productId.
- Database §8.2B đã có invalid-after-capture: Order xác minh khi áp dụng Payment SUCCESS, không phải ban sau thanh toán tự hủy; lỗi phụ thuộc giữ retry, không coi là invalid.

Phạm vi gồm tài liệu hiệu lực trong repository và các thẻ/tài sản liên quan trên Drive. Các bản nguồn lịch sử và mô tả implementation không được viết lại thành tính năng đã triển khai. Google Docs đã sửa/lưu trực tiếp 12 thẻ và export xác nhận 225 đoạn/ô mục tiêu; Figma/drawio chưa chỉnh sửa. Bằng chứng từng thẻ và bản vá thiết kế còn chờ lưu tại [kế hoạch active](../plans/active/the-15-docs-remote-patches.md); đối chiếu tài liệu không phải UAT/runtime pass.

| Mục Thẻ 15 | Nguồn/section Drive | Nguồn hiệu lực repository | Ca đối chiếu | Trạng thái |
|---|---|---|---|---|
| 1 — vòng đời cấm/khôi phục | FRD UC-A03/E03/D05/G03; Database User/G-14/WD; API auth/ban/unban/§7; UI/Parameter/BRD và conflicts | [FRD](FRD.md), [Database](database.md), [API](api_specs.md), [Identity](identity.md#uc-e03), [Finance](finance.md#uc-d05), [SAD](software-architecture.md) | B1–B11 theo bảng AC Thẻ 15 trong FRD | Repository và text Google Docs đã sửa/đọc lại; frame Figma chưa sửa |
| 2 — Product HIDDEN/provision | FRD UC-D01; API Product; UI III.4a; Activity UC-D01 | [FRD](FRD.md), [Catalog](catalog.md#uc-d01), [API](api_specs.md), [SAD](software-architecture.md) | P1–P2; AC-CAT-002 | Repository/FRD và UI Screens trên Drive đã đồng bộ; Activity/Figma chưa sửa |
| 3 — trigger sau thu | Database §8.2B; API state table/event; FRD UC-B06/G02; Activity B06/G02; Simulated Gateway | [Database](database.md), [API](api_specs.md), [Order](checkout-orders.md#uc-b06), [Payment](payments.md#uc-g02), [SAD §5.1](software-architecture.md) | O1–O3; AC-ORD-003 | Repository và text Google Docs đã chỉ rõ Order owner/tách ban sau thu, đọc lại sau lưu; Activity chưa sửa |

Mapping bổ sung: Identity AC-IDN-010/011 bao phủ deadline/J7/lỗi nguồn; Finance AC-FIN-004 bao phủ rút toàn bộ dưới ngưỡng; Notification AC-NOT-003 bao phủ user.enabled BANNED không đồng nghĩa unban. Đây là AC để triển khai, không kết quả test ứng dụng.

Kiểm chứng ngày 18/11/2026: 561 explicit ID duy nhất, giữ đủ 37 UC/17 G/11 WD/26 AT; manifest 5 nguồn hiện hành khớp SHA-256. Kiểm tra gốc vẫn FAIL tại đường dẫn lịch sử `business/UET FRD v5.4 - dong bo API DB.md` không tồn tại. Scan link tách 656 dẫn `business/` cũ, còn một link hiện trạng Catalog tới `ProductTestController.java` không tồn tại; không coi toàn bộ link repository đã pass. Không chạy runtime/UAT. Google Docs đã lưu và export xác nhận 12 thẻ; [chi tiết/bản vá thiết kế](../plans/active/the-15-docs-remote-patches.md) còn active vì Figma/drawio chưa sửa, không phải vì text Drive còn bị chặn.

<a id="the-16"></a>
## Tham số Thẻ 16 — 18–19/11/2026

Nguồn: [Thẻ 16 trên Google Docs](https://docs.google.com/document/d/1VukChbA8p9eOU_loe7y_RVCFvdkzIeMDQCWBoUn2nRI/edit?tab=t.m7xl31skw7qz) và yêu cầu áp dụng các quyết định này vào repository. [Bảng tham số](parameters.md) ghi các giá trị sử dụng và ngoại lệ. Khi một đoạn cũ cùng chủ đề còn để ngỏ hoặc khác giá trị, dùng quyết định Thẻ 16; không dùng ghi chú dev hoặc ví dụ để thay quyết định.

- Hoa hồng chung 5% cho mọi ngành hàng/Seller; Category dùng 10 ngành hàng ban đầu, không có tỷ lệ riêng.
- Khách lưu tối đa 3 địa chỉ; địa giới là dữ liệu tĩnh nạp sẵn trong DB Identity. Nguồn và phiên bản bộ dữ liệu phải được ghi rõ khi chuẩn bị dữ liệu.
- Giỏ tối đa 50 dòng, mỗi dòng tối đa 99 đơn vị; giới hạn media, mô tả, hiển thị và cảnh báo dùng các con số đã chốt trong bảng tham số.
- Mỗi chu kỳ hoàn tiền có lần đầu và tối đa 3 lần thử lại sau 1, 5 và 15 phút từ lỗi trước. Hết lượt thì khách đã thanh toán mở chu kỳ mới trên cùng khoản hoàn, không chuyển Admin. Kết quả chưa rõ chỉ được tra cứu, không coi timeout là thất bại.
- Email lỗi tạm thời cũng được gửi lại tối đa 3 lần sau lần đầu, chờ 1, 5 và 15 phút. Thông báo trong ứng dụng giữ 30 ngày và hiển thị tối đa 50 bản gần nhất; không xóa email hoặc khóa chống trùng.
- P-37 chốt bổ sung ngày 19/11/2026: tìm theo tên sản phẩm và tên cửa hàng không phân biệt dấu tiếng Việt, không phân biệt hoa thường. Chuẩn hóa cả từ khóa và tên khi so khớp, giữ nguyên tên lưu/hiển thị.

P-01 vẫn dùng tạm 15 phút, chưa chốt. N-09 và N-11 giữ nguyên; các phần Should/Could và phân tích đã hoãn không tự trở thành Must. Đây là thay đổi yêu cầu tài liệu, không xác nhận code, migration hoặc test ứng dụng đã đáp ứng.

| Phần team dev cần kiểm tra | Hồ sơ đọc trước | Kết quả cần kiểm chứng |
|---|---|---|
| Tài khoản và địa chỉ | [Identity](identity.md) | Liên kết reset hết hạn sau 30 phút; thêm địa chỉ thứ tư bị từ chối kể cả khi gửi đồng thời; mã địa giới được kiểm tra bằng dữ liệu trong DB; đăng nhập sai không gây khóa tạm. |
| Sản phẩm, media và hiển thị | [Catalog](catalog.md#ac-cat-005), [ngưỡng bán chạy](catalog.md#ac-cat-006) | Kiểm tra biên số tệp/video, độ dài nội dung, 20 bản ghi/trang và điểm 1 chữ số thập phân; 500 lượt chưa bán chạy, 501 lượt đạt; tồn khả dụng ≤5 có cảnh báo. |
| Tìm sản phẩm và cửa hàng (P-37) | [Catalog](catalog.md#ac-cat-001), [Identity](identity.md#ac-idn-006) | `dien thoai`, `Điện Thoại`, `ĐIỆN THOẠI` cho cùng kết quả trên tên sản phẩm và tên cửa hàng; giữ nguyên tên hiển thị và điều kiện G-06. |
| Giỏ và đơn | [Order](checkout-orders.md#ac-ord-008) | Dòng giỏ không vượt 99, giỏ không vượt 50 dòng, Mua ngay không chạm giỏ; giữ enum lý do và ghi chú tối đa 500 ký tự; cảnh báo khi còn không quá 24 giờ. |
| Tiền và hoàn tiền | [Finance](finance.md), [Payment](payments.md) | Mọi ngành hàng dùng 5%; đơn cũ giữ tỷ lệ đã lưu. Lệnh hoàn không phát lại khi chưa rõ kết quả; hết lượt chỉ đúng khách chủ Payment mở chu kỳ mới trên cùng khoản hoàn. |
| Email và thông báo | [Notification](notifications.md) | Lỗi tạm thời gửi lại 1/5/15 phút, dừng đúng điều kiện; giới hạn 50 bản/30 ngày không làm mất email, lịch sử hoặc khóa chống trùng. |

Các tình huống này là tiêu chí để triển khai và kiểm tra, chưa phải kết quả test ứng dụng.

Kiểm tra tài liệu Thẻ 16: 5 nguồn hiện hành khớp hash/số dòng; link/anchor còn truy cập được, mã và các giá trị tham số đã qua kiểm tra tĩnh. Các link lịch sử thiếu vẫn là giới hạn, không coi toàn bộ repository đã pass. Kết quả và phạm vi được ghi tại [kế hoạch hoàn tất](../plans/completed/the-16-parameters.md); chưa chạy test ứng dụng.



## Baseline và giới hạn kết luận

Người dùng chấp nhận SAD v1.1 §7.1/D.1 và cây tài liệu ngày 11/11/2026.
Nguồn Markdown là input trực tiếp của lần lập baseline ngày 11/11/2026; lần đó chưa đọc chốt.txt, BRD, Parameter Register, Figma hoặc các DOCX/PDF _UPDATED được SAD D.1 dẫn. Thẻ 16 và các xác nhận bổ sung hiện đã được đưa vào [bảng tham số](parameters.md). Chấp nhận các giá trị này không phê duyệt các DELTA kỹ thuật còn mở.

Mốc code đối chiếu: a79ab611f59d592c4d7fe407b8da96b3ef7290ae, nhánh main,
worktree có staged rename architecture/→business/ và tài liệu untracked của người dùng.
Mọi kết luận code/test là khảo sát tĩnh ngày 11/11/2026, không kết quả runtime.

## Manifest nguồn

Hash là SHA-256 toàn bộ bytes, chỉ phát hiện đổi nguồn. Ba hàng `business/...` là manifest lịch sử baseline 11/11/2026, không đổi đường dẫn/hash quá khứ thành giả định file hiện có. Ngày 16/11 hai nguồn kiến trúc chuyển vào `docs/requirements/`; ngày 18/11 nguồn hiện hành được hiệu chỉnh theo Thẻ 15 và các tham số Thẻ 16, bổ sung P-37 ngày 19/11/2026. Nguồn lịch sử `docs/api.md`, `docs/database.md`, FRD v5.4 không bị sửa bởi công việc này.

| Đường dẫn | Phiên bản / phạm vi | Dòng | SHA-256 |
|---|---|---:|---|
| `business/UET FRD v5.4 - dong bo API DB.md` | FRD v5.4, 06/11/2026; 37 UC (2 loại bỏ), nghiệp vụ/ngoại lệ | 1539 | `e4fbc0e7b16583e90da42e0ca0fb139f54b63682be9a5c8ca3a91ca283b07de9` |
| `business/database.md` | Database RBAC v5.0, 25/10/2026; data dictionary/G/WD/state | 1055 | `033b8c01e69a8e6c9f783c51b29933ae8afe240e5f85e37423c60c4923e65ab1` |
| `business/api.md` | API v1.0, 25/10/2026, có cập nhật sau metadata; contract để review | 2979 | `6cf4d3144537fcabf1e4aafd3f1e3886200166e6ad3cbe696dbd5463d12b4046` |
| `docs/requirements/software-architecture.md` | SAD-ECOM-01 v1.1 + Thẻ 15/Thẻ 16/P-37, 19/11/2026 | 1151 | `a06d0a362be04c18a3d2898dc512dac7d4a69dda106c59206ecdbedc4a139de6` |
| `docs/requirements/system-architecture.md` | Kiến trúc hệ thống + Thẻ 15/Thẻ 16/P-37, 19/11/2026 | 1156 | `70278350a7d325b9f4371406d36e44ae9d234d809a5350a56552ec6e5ef0d5a3` |

### Nguồn mới được thêm vào harness ngày 16/11/2026

| Tài liệu | Phiên bản | Dòng | SHA-256 |
|---|---|---:|---|
| [docs/requirements/FRD.md](FRD.md) | FRD v5.5 + Thẻ 15/Thẻ 16/P-37, 19/11/2026 | 1554 | `57299e5ad3e0f646d38ba06d2bc5b618699a099e1cc59856c709f0744e8191ff` |
| [docs/requirements/api_specs.md](api_specs.md) | API v1.1 + Thẻ 15/Thẻ 16/P-37, 19/11/2026 | 3067 | `cfedd5f8f726f844c3ec74dda994b6a277ddae5325c818d05cc3486a3cbddb4f` |
| [docs/requirements/database.md](database.md) | Database v5.1 + Thẻ 15/Thẻ 16/P-37, 19/11/2026 | 1120 | `169b1da5bce8f7bb68f59a5b4d9a85e2ffe9d69c6163b97143e6ab4f9fc93414` |

Các phần thuộc Thẻ 15 và các tham số Thẻ 16 được hiệu chỉnh theo nguồn quyết định tương ứng; không xác nhận đã rà lại toàn bộ 377 clause/implementation theo nguồn mới. Các dẫn `Lx–y` chưa sửa bên dưới vẫn là snapshot cũ, không chuyển nguyên số dòng sang bản mới. Khi chạm phần ngoài phạm vi, đối chiếu authority trước triển khai, không suy diễn từ hash/ngày sửa. Các inventory nguồn cũ giữ tên/section cũ như provenance, không phải đề nghị dùng lại scope/đa vai trò/snapshot đã bị thay.

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

Bảng ghi các hiệu chỉnh theo nguồn hiệu lực, không coi code đang chạy là căn cứ thay thế. Các hàng nguồn cũ/số dòng cũ là truy vết lịch sử, không là policy khi đã bị [Thẻ 15](#the-15) thay thế; các phần ngoài phạm vi này chưa được rà lại toàn bộ.

| Clause | Nguồn cũ | Baseline và nội dung hiệu lực |
|---|---|---|
| [UC-A03.MAIN-03](identity.md#uc-a03.main-03) | FRD v5.4/§UC-A03; baseline 11/11 đa vai trò, đã bị thay thế ở phần tài khoản | Customer/Seller/Admin là tài khoản độc lập, login đúng accountType; không hợp quyền nhiều vai trò. [FRD UC-A03](FRD.md), [Thẻ 15](#the-15). |
| [UC-A03.POST-01](identity.md#uc-a03.post-01) | FRD v5.4/§UC-A03; snapshot lịch sử thanh toán đã bị thay thế | BANNED chỉ có phiên giới hạn theo nghĩa vụ hiện tại, DISABLED khi xác minh hết; không dùng hasPaidOrderBeforeBan. J7 và login/refresh phục hồi BANNED nếu nghĩa vụ đến muộn, không tự unban. [FRD UC-E03](FRD.md), [Database G-14](database.md), [Thẻ 15](#the-15). |
| [UC-B04.MAIN-03](checkout-orders.md#uc-b04.main-03) | [UC-B04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L609 | Khách chọn một địa chỉ cho toàn bộ N đơn và xác nhận Thanh toán. Preview chỉ đọc; bước pay mới tạo checkout, giữ tồn và lưu ý định charge. [baseline](software-architecture.md), L444–496 |
| [UC-B04.MAIN-05](checkout-orders.md#uc-b04.main-05) | [UC-B04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L613 | Khi pay hợp lệ, local transaction O1 của Order giữ đủ tất cả dòng, tạo một CheckoutSession, N Order theo Seller và lưu ý định gọi Payment bền vững; thiếu một dòng rollback toàn bộ. [baseline](software-architecture.md), L444–496 |
| [UC-B04.MAIN-06](checkout-orders.md#uc-b04.main-06) | [UC-B04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L615 | Order chưa PAID thì UI hiển thị đang xác nhận; chỉ báo đặt hàng thành công sau O3 áp dụng PAID. Không triển khai luồng tạo phiên công khai rồi browser tự gọi charge cũ như baseline mới. [baseline](software-architecture.md), L444–496 |
| [UC-B04.POST-01](checkout-orders.md#uc-b04.post-01) | [UC-B04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L629 | Một checkout có một CheckoutSession, N Order theo N Seller và tối đa một Payment; Order được định danh bằng orderId, không có mã hiển thị riêng. [baseline](software-architecture.md), L444–496 |
| [UC-B10.MAIN-03](checkout-orders.md#uc-b10.main-03) | [UC-B10](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L809 | Khách chọn địa chỉ chung; quy tắc mặc định tham chiếu Database §3.1.4 (FRD ghi nhầm §3.1.3). Chưa pay thì chưa tạo Order/Reservation. [baseline](software-architecture.md), L444–496 |
| [UC-B10.MAIN-04](checkout-orders.md#uc-b10.main-04) | [UC-B10](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L811 | Khi xác nhận Thanh toán, O1 kiểm tra lại quote/giá/Seller/tồn, tạo một CheckoutSession BUY_NOW, một Order, một OrderItem và công việc charge bền vững. BUY_NOW không gọi Cart. [baseline](software-architecture.md), L444–496 |
| [UC-B10.ALT-06](checkout-orders.md#uc-b10.alt-06) | [UC-B10](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L827 | Rời trang trước khi xác nhận Thanh toán: chỉ có Preview, chưa có phiên/đơn/giữ tồn; không coi việc mở trang hoặc bấm một bước xem trước là O1. [baseline](software-architecture.md), L444–520 |
| [UC-B05.PRE-01](payments.md#uc-b05.pre-01) | [UC-B05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L639 | Customer được phép thanh toán; kiểm tra dữ liệu hiện hành, địa chỉ, quote và idempotency tại pay; O1 mới tạo checkout/giữ tồn. Nếu tiếp tục workflow đã nhận thì dùng tham chiếu và ý định cũ, không thu mới. [baseline](software-architecture.md), L444–520 |
| [UC-B05.MAIN-01](payments.md#uc-b05.main-01) | [UC-B05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L643 | Khách xem Preview và xác nhận Thanh toán qua Order; chi tiết pay/quote/internal charge thuộc DELTA-01/03 cần đồng bộ contract trước code. Không xem endpoint đề xuất là API đã tồn tại. [baseline](software-architecture.md), L444–496 |
| [UC-G02.MAIN-04](payments.md#uc-g02.main-04) | [UC-G02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1333 | Payment ghi Refund SUCCESS và Outbox trong local transaction; Finance nhận kết quả tin cậy, cập nhật Settlement REFUNDED, pending Seller −Sᵢ/PLATFORM −Cᵢ và REFUND_DEBIT trong transaction Finance. Order/Notification cập nhật riêng, có dedupe; không có transaction chung Refund–Settlement–Order. [baseline](software-architecture.md), L522–539 |
| [UC-D04.MAIN-01](finance.md#uc-d04.main-01) | [UC-D04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L983 | Seller mở tra cứu vận hành đơn, Settlement và ví thuộc mình; không bắt buộc màn thống kê phân tích hoặc chọn kỳ phân tích trong MVP. [baseline](software-architecture.md), L75–81 |
| [UC-D04.MAIN-02](finance.md#uc-d04.main-02) | [UC-D04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L985 | MVP tra cứu danh sách/chi tiết đơn và khoản tiền tại service sở hữu; hoãn biểu đồ, top sản phẩm, export phân tích và Reporting Service. [baseline](software-architecture.md), L75–81 |
| [UC-D04.ALT-03](finance.md#uc-d04.alt-03) | [UC-D04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L997 | Không có giao dịch: danh sách trống và số dư thực tế; không dựng read model kỳ thống kê chỉ để đáp ứng phần phân tích đã hoãn. [baseline](software-architecture.md), L75–81 |
| [UC-D05.PRE-01](finance.md#uc-d05.pre-01) | FRD v5.4 UC-D05; điều kiện ACTIVE-only và ngưỡng vô điều kiện đã bị thay ở phần này | Seller APPROVED/ACTIVE hoặc BANNED có allowlist đúng owner: rút số nguyên dương <=available, >=10.000đ; ngoại lệ BANNED available dương <10.000đ được rút đúng toàn bộ. Không rút pending; không đóng băng chỉ vì ban. [Thẻ 15](#the-15), [FRD UC-D05](FRD.md), [Database WD-03/09](database.md). |
| [UC-E02.GOAL-01](finance.md#uc-e02.goal-01) | [UC-E02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1079 | Admin tra cứu vận hành và đối soát nguồn tiền chính thức; không bắt buộc dashboard phân tích theo kỳ trong MVP. [baseline](software-architecture.md), L75–81 |
| [UC-E02.MAIN-01](finance.md#uc-e02.main-01) | [UC-E02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1085 | Admin mở tra cứu vận hành và đối soát; hoãn dashboard phân tích, biểu đồ và export. [baseline](software-architecture.md), L75–81 |
| [UC-E02.ALT-01](finance.md#uc-e02.alt-01) | [UC-E02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1097 | Nguồn đủ dữ liệu và không có giao dịch thì phản ánh 0; thiếu nguồn không được coi là balanced=true hoặc không có giao dịch. [baseline](software-architecture.md), L665–685 |
| [UC-G04.MAIN-02](finance.md#uc-g04.main-02) | [UC-G04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1395 | Order ghi CONFIRMED và Outbox trong local transaction; Finance xử lý order.confirmed, chuyển Settlement SETTLED, Seller pending −Sᵢ/available +Sᵢ, PLATFORM pending −Cᵢ/available +Cᵢ và SETTLEMENT_RELEASED trong transaction Finance. Không gộp transaction Order và Finance. [baseline](software-architecture.md), L559–570 |
| [UC-G01.MAIN-05](notifications.md#uc-g01.main-05) | [UC-G01](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1305 | Email demo bất đồng bộ là Must qua DemoEmailAdapter; không tuyên bố email thật đã giao. In-app là Should, chưa bắt buộc trong MVP. [baseline](software-architecture.md), L303–318 |

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
| OQ-001 | [Bảng tham số](parameters.md); SAD §7.3; API §9.1 | Các giá trị Thẻ 16 đã có quyết định. P-01 vẫn dùng tạm 15 phút; những giá trị chưa được quyết định riêng như P-02/P-03/P-04/P-06/P-08.1/P-08.2/P-08.4/P-SIM-01 vẫn giữ trạng thái hiện có. Không tự chốt cả nhóm. |
| OQ-002 | P-COM-01; Database §8.3 | Mức chung đã chốt 500 bps = 5%, không còn mở về tỷ lệ hoặc theo ngành hàng. Nơi cấu hình, quyền sửa và lịch sử thay đổi cấu hình nếu bổ sung vẫn cần quyết định riêng. |
| OQ-003 | SAD §3.1/6.7/A.6.2; P-26 | Địa giới dùng dữ liệu tĩnh trong DB Identity. **Đã chọn 20/11/2026:** snapshot một lần từ provinces.open-api.vn v2 (mô hình hai cấp sau 01/07/2025), nạp bằng migration Flyway có ghi URL nguồn, phiên bản và ngày lấy dữ liệu. Cách cập nhật khi nguồn đổi vẫn cần quyết định riêng. |
| OQ-004 | SAD §6.3–6.4; DELTA-07 | Cookie refresh, profile local, Origin/CORS/CSRF **đã chốt 20/11/2026** tại [quyết định 0001](../decisions/0001-refresh-cookie-va-origin.md). Key rotation và service credential cụ thể vẫn mở; JWT service trong A.3 vẫn đề xuất. |
| OQ-005 | SAD §7.2; DELTA-01…06 | Review và đồng bộ quote/pay/internal charge/cart snapshot, worker, close-expired, event/inbox; không tự gán endpoint hoặc table mới. |
| OQ-006 | P-37 ngày 19/11/2026; SAD §6.7/A.6.1; API C-08; Database §5.2 | Đã chốt hành vi cho tên sản phẩm và tên cửa hàng: không phân biệt dấu tiếng Việt, không phân biệt hoa thường. FRD/API/Database và hồ sơ đã dùng cùng quy tắc. Còn review phương án index chi tiết và kiểm chứng implementation/test; không coi đặc tả là tính năng đã chạy hoặc đổi thành full-text. |
| OQ-007 | P-19/P-22, N-06; SAD §7.3/A.6.4 | Email đã chốt 3 lần gửi lại sau lần đầu, chờ 1/5/15 phút; in-app tối đa 50 bản gần nhất, giữ 30 ngày. Quyền xem và thời gian giữ bí mật của email demo, lịch job và cách vận hành còn cần quyết định riêng; không hứa exactly-once gửi email thật. |
| OQ-008 | SAD §7.3, C.5; DELTA-09 | Cloud topology/DB role/Kafka/backup, retention, tải/p95/RPO/RTO; không kết luận SLO từ thiết kế. |
| OQ-009 | identity-service/should.md A8; API enum vs DB §6 | Tên AuditAction khác nhau giữa source API và DB chưa được baseline hiệu chỉnh cụ thể; giữ mở, không tự rename enum/CHECK. |
| OQ-010 | API §3.4 N-04; P-18; SAD C.5 | Giá trị rate limit tại Gateway và giới hạn body **đã chọn 20/11/2026** tại [quyết định 0002](../decisions/0002-gioi-han-tan-suat-gateway.md) (đề xuất của team, theo từng instance). N-04 chính thức và bộ đếm dùng chung cho nhiều instance vẫn mở. |
| OQ-011 | API §6.1 #30/#31 | Token xác minh email đăng ký lại Seller "một lần, có hạn" không có thời hạn trong nguồn. **Đã chọn 20/11/2026:** 30 phút từ khi tạo, dùng một lần, lưu dạng hash như P-05; cấu hình được. |
| OQ-012 | API §6.1 #30, §7.2 | API không đặt tên sự kiện gửi email xác minh đăng ký lại Seller. **Đã chọn 20/11/2026:** Identity ghi Outbox `seller.reapplication_requested` {userId, sellerId, verificationUrl (bí mật, không log), expiresAt}, theo mẫu `password.reset_requested`; link = cấu hình `identity.seller-reapplication.link-base` + `?token=`. Cần bổ sung vào API §7.2 và hợp đồng Notification. |
| OQ-013 | API §6.1 #13 | Bảng endpoint ghi quyền `seller:profile:own`, mục chi tiết ghi `account:update:own` cho purpose AVATAR mọi loại tài khoản. Implementation theo mục chi tiết (20/11/2026); bảng cần sửa cho khớp. |

Hai hạn 7 ngày và P-WD-01=10.000 VND đã chốt; không đưa lại TBD. ADR-05 đã hoãn
phân tích, P-14/P-15 không chặn Must tra cứu vận hành. Bảng nguồn tham số SAD:

| Tham số | Phạm vi | Cách xử lý trong thiết kế |
|---|---|---|
| P-01 | TTL giữ tồn | Dùng tạm 15 phút, chưa chốt; ràng buộc với dispatch/J1/J2b |
| P-02/P-03/P-04 | Chờ/query/thời gian tối đa Payment | Giữ trạng thái riêng; không biến HTTP timeout thành FAILED |
| P-05 | Hạn đặt lại mật khẩu | 30 phút từ khi tạo, chỉ dùng một lần |
| P-06 | Hạn access/refresh token | Giữ trạng thái hiện có; review cùng rotation/reuse |
| P-08/P-09/P-10/P-11 | Media/địa chỉ/giỏ/phân trang | Dùng giới hạn đã chốt trong [bảng tham số](parameters.md); dung lượng/định dạng không tự chốt theo số lượng tệp |
| P-14/P-15/P-16/P-36 | Thời gian, bán chạy và cảnh báo | GMT+7/thứ Hai/quý lịch; bán chạy trên 500 lượt bán; tồn thấp ≤5; sắp hết hạn ≤24 giờ. Không mở lại Reporting Service. |
| P-19/P-21/P-22 | Email/lý do/thông báo | Email có 3 lần gửi lại 1/5/15 phút; ghi chú hủy/từ chối đơn tối đa 500 ký tự; in-app 50 bản/30 ngày |
| P-RF-01 | Hoàn tiền | 3 lần thử lại sau lần đầu mỗi chu kỳ; khách mở chu kỳ mới, giữ Refund/số tiền/key và lịch sử |
| P-COM-01 | Hoa hồng | Chung 500 bps = 5%; lưu cùng Order tại checkout, không tính lại đơn cũ |
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
| [TÀI LIỆU KIẾN TRÚC PHẦN MỀM](software-architecture.md), L1–1132 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [Hướng dẫn sử dụng tài liệu](software-architecture.md), L15–38 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [Cách hiểu mức độ ràng buộc](software-architecture.md), L31–38 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [Mục lục](software-architecture.md), L39–56 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [1. Bối cảnh và phạm vi kiến trúc](software-architecture.md), L57–116 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [1.1. Mục đích và quan hệ với hồ sơ dự án](software-architecture.md), L59–74 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [1.2. Phạm vi phiên bản MVP](software-architecture.md), L75–82 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [1.3. Ràng buộc và nền tảng đã chốt](software-architecture.md), L83–99 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [1.4. Các yêu cầu chi phối thiết kế](software-architecture.md), L100–116 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [2. Cấu trúc phần mềm và tổ chức mã nguồn](software-architecture.md), L117–196 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [2.1. Bản đồ trách nhiệm và dữ liệu](software-architecture.md), L121–137 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [2.2. Cấu trúc backend và hướng phụ thuộc](software-architecture.md), L138–168 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [2.3. Cấu trúc React SPA](software-architecture.md), L169–186 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [2.4. Gateway, Discovery và biên mở rộng](software-architecture.md), L187–196 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [3. Thiết kế bên trong từng service](software-architecture.md), L197–321 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [3.1. Identity — tài khoản, hồ sơ và quyền](software-architecture.md), L201–217 | [identity](identity.md) — yêu cầu baseline và biên liên service |
| [3.2. Product — sản phẩm và tìm kiếm](software-architecture.md), L218–234 | [catalog](catalog.md) — yêu cầu baseline và biên liên service |
| [3.3. Cart — giỏ hàng](software-architecture.md), L235–251 | [checkout-orders](checkout-orders.md) — yêu cầu baseline và biên liên service |
| [3.4. Order — checkout, đơn hàng và tồn kho](software-architecture.md), L252–268 | [checkout-orders](checkout-orders.md) — yêu cầu baseline và biên liên service |
| [3.5. Payment — thanh toán và hoàn tiền](software-architecture.md), L269–285 | [payments](payments.md) — yêu cầu baseline và biên liên service |
| [3.6. Finance — quyết toán, ví và rút tiền](software-architecture.md), L286–302 | [finance](finance.md) — yêu cầu baseline và biên liên service |
| [3.7. Notification — thông báo](software-architecture.md), L303–321 | [notifications](notifications.md) — yêu cầu baseline và biên liên service |
| [4. Giao tiếp giữa các thành phần](software-architecture.md), L322–439 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [4.1. Khi nào dùng REST, khi nào dùng Kafka?](software-architecture.md), L324–337 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [4.2. Góc nhìn REST](software-architecture.md), L338–368 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [4.3. Góc nhìn sự kiện Kafka](software-architecture.md), L369–417 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [4.4. Hợp đồng public/internal và quản lý thay đổi](software-architecture.md), L418–439 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [5. Các luồng nghiệp vụ xuyên service](software-architecture.md), L440–591 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [5.1. Đặt hàng từ giỏ hoặc Mua ngay](software-architecture.md), L444–498 | [checkout-orders](checkout-orders.md) — yêu cầu baseline và biên liên service |
| [5.2. Giá thay đổi, timeout và các kết quả không rõ ràng](software-architecture.md), L499–521 | [checkout-orders](checkout-orders.md) — yêu cầu baseline và biên liên service |
| [5.3. Hủy đơn và hoàn tiền](software-architecture.md), L522–540 | [payments](payments.md) — yêu cầu baseline và biên liên service |
| [5.4. Seller thực hiện đơn và Customer xác nhận](software-architecture.md), L541–558 | [checkout-orders](checkout-orders.md) — yêu cầu baseline và biên liên service |
| [5.5. Quyết toán sau xác nhận nhận hàng](software-architecture.md), L559–571 | [finance](finance.md) — yêu cầu baseline và biên liên service |
| [5.6. Seller rút tiền giả lập](software-architecture.md), L572–579 | [finance](finance.md) — yêu cầu baseline và biên liên service |
| [5.7. Tạo sản phẩm và khởi tạo tồn](software-architecture.md), L580–591 | [catalog](catalog.md) — yêu cầu baseline và biên liên service |
| [6. Các quy tắc thiết kế dùng chung](software-architecture.md), L592–707 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [6.1. Xác thực, quyền hiện hành và ownership](software-architecture.md), L596–601 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [6.2. Chính sách cấm và thu hồi phiên](software-architecture.md), L602–615 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [6.3. Phiên frontend, cookie và bí mật](software-architecture.md), L616–623 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [6.4. Xác thực lời gọi nội bộ](software-architecture.md), L624–631 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [6.5. Transaction, tồn kho và chống trùng](software-architecture.md), L632–664 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [6.6. Tiền, ledger và đối soát](software-architecture.md), L665–686 | [finance](finance.md) — yêu cầu baseline và biên liên service |
| [6.7. Quyền sở hữu dữ liệu và các dữ liệu hỗ trợ](software-architecture.md), L687–707 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [7. Quyết định kiến trúc và nội dung cần chốt](software-architecture.md), L708–775 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [7.1. Sổ quyết định đã xác nhận](software-architecture.md), L712–724 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [7.2. Các bổ sung hợp đồng cần review trước triển khai](software-architecture.md), L725–740 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [7.3. Tham số còn mở](software-architecture.md), L741–760 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [7.4. Rủi ro và đánh đổi](software-architecture.md), L761–775 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [8. Kiểm chứng kiến trúc và điều kiện nghiệm thu](software-architecture.md), L776–798 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [8.1. Điều kiện hoàn thành phiên bản MVP](software-architecture.md), L791–798 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [Phụ lục A — Ghi chú thiết kế chi tiết để triển khai](software-architecture.md), L799–967 | Đề xuất kỹ thuật phụ lục A; giữ chi tiết ở nguồn, DELTA cần review trước code |
| [A.1. Checkout: công việc bền vững, dispatch và đóng quá hạn](software-architecture.md), L803–833 | Đề xuất kỹ thuật phụ lục A; giữ chi tiết ở nguồn, DELTA cần review trước code |
| [A.1.1. Lưu ý định và ranh giới transaction](software-architecture.md), L805–819 | Đề xuất kỹ thuật phụ lục A; giữ chi tiết ở nguồn, DELTA cần review trước code |
| [A.1.2. Đóng phiên, đối soát và success muộn](software-architecture.md), L820–833 | Đề xuất kỹ thuật phụ lục A; giữ chi tiết ở nguồn, DELTA cần review trước code |
| [A.2. Ranh giới lớp và transaction Spring](software-architecture.md), L834–841 | Đề xuất kỹ thuật phụ lục A; giữ chi tiết ở nguồn, DELTA cần review trước code |
| [A.3. Identity lifecycle và phương án service token](software-architecture.md), L842–857 | Đề xuất kỹ thuật phụ lục A; giữ chi tiết ở nguồn, DELTA cần review trước code |
| [A.3.1. Vòng đời tài khoản và hồ sơ](software-architecture.md), L844–849 | Đề xuất kỹ thuật phụ lục A; giữ chi tiết ở nguồn, DELTA cần review trước code |
| [A.3.2. Phương án credential service đề xuất](software-architecture.md), L850–857 | Đề xuất kỹ thuật phụ lục A; giữ chi tiết ở nguồn, DELTA cần review trước code |
| [A.4. Simulator, topology Kafka và envelope](software-architecture.md), L858–888 | Đề xuất kỹ thuật phụ lục A; giữ chi tiết ở nguồn, DELTA cần review trước code |
| [A.4.1. PaymentGateway và simulator](software-architecture.md), L860–865 | Đề xuất kỹ thuật phụ lục A; giữ chi tiết ở nguồn, DELTA cần review trước code |
| [A.4.2. Topology đề xuất](software-architecture.md), L866–871 | Đề xuất kỹ thuật phụ lục A; giữ chi tiết ở nguồn, DELTA cần review trước code |
| [A.4.3. Envelope và dữ liệu còn thiếu trong contract](software-architecture.md), L872–888 | Đề xuất kỹ thuật phụ lục A; giữ chi tiết ở nguồn, DELTA cần review trước code |
| [A.5. Relay, Inbox và áp dụng thay đổi giỏ/ví](software-architecture.md), L889–912 | Đề xuất kỹ thuật phụ lục A; giữ chi tiết ở nguồn, DELTA cần review trước code |
| [A.5.1. Outbox relay](software-architecture.md), L891–896 | Đề xuất kỹ thuật phụ lục A; giữ chi tiết ở nguồn, DELTA cần review trước code |
| [A.5.2. Inbox và sự kiện chờ phụ thuộc](software-architecture.md), L897–904 | Đề xuất kỹ thuật phụ lục A; giữ chi tiết ở nguồn, DELTA cần review trước code |
| [A.5.3. Áp dụng checkout.paid vào Cart](software-architecture.md), L905–912 | Đề xuất kỹ thuật phụ lục A; giữ chi tiết ở nguồn, DELTA cần review trước code |
| [A.6. Product, địa chỉ, media và delivery](software-architecture.md), L913–942 | Đề xuất kỹ thuật phụ lục A; giữ chi tiết ở nguồn, DELTA cần review trước code |
| [A.6.1. Product và tìm kiếm](software-architecture.md), L915–924 | Đề xuất kỹ thuật phụ lục A; giữ chi tiết ở nguồn, DELTA cần review trước code |
| [A.6.2. Địa chỉ và địa giới](software-architecture.md), L925–930 | Đề xuất kỹ thuật phụ lục A; giữ chi tiết ở nguồn, DELTA cần review trước code |
| [A.6.3. Media](software-architecture.md), L931–934 | Đề xuất kỹ thuật phụ lục A; giữ chi tiết ở nguồn, DELTA cần review trước code |
| [A.6.4. Notification và email demo](software-architecture.md), L935–942 | Đề xuất kỹ thuật phụ lục A; giữ chi tiết ở nguồn, DELTA cần review trước code |
| [A.7. Ranh giới mở rộng Should/Could](software-architecture.md), L943–946 | Đề xuất kỹ thuật phụ lục A; giữ chi tiết ở nguồn, DELTA cần review trước code |
| [A.8. Checklist constraint và migration](software-architecture.md), L947–967 | Đề xuất kỹ thuật phụ lục A; giữ chi tiết ở nguồn, DELTA cần review trước code |
| [Phụ lục B — Danh mục kiểm thử và truy vết](software-architecture.md), L968–1028 | Đề xuất kỹ thuật phụ lục A; giữ chi tiết ở nguồn, DELTA cần review trước code |
| [B.1. Chiến lược kiểm thử](software-architecture.md), L972–977 | AT-01…26 phân chủ sở hữu; chiến lược test engineering, trace nguồn giữ tham chiếu |
| [B.2. Ma trận 26 kịch bản nghiệm thu](software-architecture.md), L978–1008 | AT-01…26 phân chủ sở hữu; chiến lược test engineering, trace nguồn giữ tham chiếu |
| [B.3. Truy vết yêu cầu → thiết kế → kiểm chứng](software-architecture.md), L1009–1028 | AT-01…26 phân chủ sở hữu; chiến lược test engineering, trace nguồn giữ tham chiếu |
| [Phụ lục C — Hiện trạng, triển khai và vận hành](software-architecture.md), L1029–1098 | AT-01…26 phân chủ sở hữu; chiến lược test engineering, trace nguồn giữ tham chiếu |
| [C.1. Khoảng cách với code tại lần rà soát 10/11/2026](software-architecture.md), L1033–1051 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [C.2. Các mốc triển khai và điều kiện đóng](software-architecture.md), L1052–1064 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [C.3. Danh mục tác vụ nền](software-architecture.md), L1065–1082 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [C.4. Quan sát và runbook](software-architecture.md), L1083–1090 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [C.5. Triển khai, backup và phục hồi](software-architecture.md), L1091–1098 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [Phụ lục D — Nguồn, thuật ngữ và lịch sử](software-architecture.md), L1099–1132 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [D.1. Hồ sơ đầu vào và quy tắc ưu tiên](software-architecture.md), L1101–1116 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [D.2. Nguồn tham khảo kỹ thuật](software-architecture.md), L1117–1122 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [D.3. Thuật ngữ](software-architecture.md), L1123–1126 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [D.4. Lịch sử phiên bản](software-architecture.md), L1127–1132 | [engineering](../engineering.md), [quy ước](README.md), coverage — baseline/ownership/ADR/DELTA/NFR/nguồn |
| [Tài liệu Kiến trúc hệ thống Ecommerce](system-architecture.md), L1–2 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [1. Giới thiệu và phạm vi hệ thống](system-architecture.md), L3–63 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [1.1. Tổng quan dự án](system-architecture.md), L5–12 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [1.2. Đối tượng sử dụng](system-architecture.md), L13–23 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [1.2.1. Người sử dụng hệ thống](system-architecture.md), L15–23 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [1.3. Phạm vi chức năng](system-architecture.md), L24–41 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [1.4. Phạm vi kỹ thuật và giới hạn](system-architecture.md), L42–63 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [1.4.1. Phạm vi kỹ thuật](system-architecture.md), L44–55 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [1.4.2. Giới hạn phạm vi](system-architecture.md), L56–63 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [2. Yêu cầu kiến trúc và lựa chọn công nghệ](system-architecture.md), L64–217 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [2.1. Các yêu cầu nghiệp vụ chi phối kiến trúc](system-architecture.md), L66–81 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [2.2. Yêu cầu chất lượng hệ thống](system-architecture.md), L82–120 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [2.2.1. Bảo mật](system-architecture.md), L84–91 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [2.2.2. Tính nhất quán và độ tin cậy](system-architecture.md), L92–97 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [2.2.3. Hiệu năng và khả năng mở rộng](system-architecture.md), L98–103 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [2.2.4. Khả năng vận hành và phục hồi](system-architecture.md), L104–107 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [2.2.5. Tiêu chí kiểm chứng](system-architecture.md), L108–120 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [2.3. Ràng buộc thiết kế và triển khai](system-architecture.md), L121–126 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [2.4. Lựa chọn kiến trúc](system-architecture.md), L127–166 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [2.4.1. Chia hệ thống theo nghiệp vụ rõ ràng](system-architecture.md), L133–138 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [2.4.2. Mở rộng riêng service cần thêm tài nguyên](system-architecture.md), L139–144 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [2.4.3. Cập nhật và triển khai từng service](system-architecture.md), L145–150 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [2.4.4. Giảm ảnh hưởng khi một service gặp lỗi](system-architecture.md), L151–158 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [2.4.6. Nguyên tắc áp dụng](system-architecture.md), L159–166 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [2.5. Lựa chọn công nghệ](system-architecture.md), L167–217 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [2.5.1. Lý do lựa chọn AWS](system-architecture.md), L189–199 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [2.5.2. Lý do lựa chọn Apache Kafka](system-architecture.md), L200–217 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [3. Kiến trúc tổng thể và phân chia service](system-architecture.md), L218–919 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [3.1. Tổng quan kiến trúc](system-architecture.md), L220–345 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [3.1.1. C4 Level 2 - Container Diagram](system-architecture.md), L261–345 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [3.2. Các lớp thành phần](system-architecture.md), L346–358 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [3.3. Phân chia trách nhiệm và dữ liệu](system-architecture.md), L359–382 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [3.4. Vai trò của Gateway và Discovery](system-architecture.md), L383–398 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [3.4.1. API Gateway](system-architecture.md), L385–392 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [3.4.2. Discovery Service](system-architecture.md), L393–398 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [3.5. Giao tiếp đồng bộ qua API](system-architecture.md), L399–441 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [3.6. Giao tiếp bất đồng bộ qua Kafka](system-architecture.md), L442–485 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [3.7. Tổ chức dữ liệu và media](system-architecture.md), L486–493 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [3.8. C4 Level 3 - Component Diagram](system-architecture.md), L494–919 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [3.8.1. Identity Service](system-architecture.md), L504–567 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [3.8.2. Product Service](system-architecture.md), L568–613 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [3.8.3. Cart Service](system-architecture.md), L614–651 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [3.8.4. Order Service](system-architecture.md), L652–701 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [3.8.5. Payment Service](system-architecture.md), L702–748 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [3.8.6. Finance Service](system-architecture.md), L749–799 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [3.8.7. Notification Service](system-architecture.md), L800–843 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [3.8.8. Gateway Service](system-architecture.md), L844–883 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [3.8.9. Discovery Service](system-architecture.md), L884–911 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [3.8.10. Nguyên tắc triển khai từ Component Diagram](system-architecture.md), L912–919 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [4. Kiến trúc dữ liệu và tính nhất quán](system-architecture.md), L920–1136 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [4.1. Nguyên tắc tổ chức dữ liệu](system-architecture.md), L922–942 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [4.2. Mô hình dữ liệu tổng quan](system-architecture.md), L943–981 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [4.3. Snapshot và quy ước lưu trữ](system-architecture.md), L982–1004 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [4.3.1. Snapshot giao dịch](system-architecture.md), L984–996 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [4.3.2. Kiểu dữ liệu và media](system-architecture.md), L997–1004 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [4.4. Transaction và cập nhật đồng thời](system-architecture.md), L1005–1032 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [4.4.1. Transaction trong từng service](system-architecture.md), L1007–1021 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [4.4.2. Kiểm soát thao tác đồng thời](system-architecture.md), L1022–1032 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [4.5. Tính nhất quán của tồn kho](system-architecture.md), L1033–1052 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [4.6. Tính nhất quán của thanh toán và ví](system-architecture.md), L1053–1081 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [4.7. Nhất quán giữa các service](system-architecture.md), L1082–1126 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [4.7.1. Outbox, Inbox và idempotency](system-architecture.md), L1108–1114 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [4.7.2. Event sai thứ tự và thiếu dữ liệu phụ thuộc](system-architecture.md), L1115–1120 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [4.7.3. Retry, xử lý bù và đối soát](system-architecture.md), L1121–1126 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |
| [4.8. Vòng đời dữ liệu và thay đổi cấu trúc](system-architecture.md), L1127–1136 | [engineering](../engineering.md), hồ sơ service theo task — kiến trúc/boundary; sơ đồ/hạ tầng chi tiết giữ nguồn |

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
inputs = re.findall(r'^\| `((?:business|docs/requirements)/[^`]+)` \|.*?\| `([0-9a-f]{64})` \|$', coverage, re.M)
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
