# Các tham số đã chốt

Nguồn: [Thẻ 16](https://docs.google.com/document/d/1VukChbA8p9eOU_loe7y_RVCFvdkzIeMDQCWBoUn2nRI/edit?tab=t.m7xl31skw7qz) và các xác nhận của người dùng ngày 18/11/2026; P-37 được chốt bổ sung ngày 19/11/2026. Các giá trị dưới đây thay phần còn để ngỏ hoặc khác giá trị trong tài liệu cũ cùng chủ đề. Đây là yêu cầu cho team dev, không phải xác nhận hệ thống đã triển khai.

## Giá trị sử dụng

| Tham số | Quy tắc |
|---|---|
| P-COM-01 | Hoa hồng chung **5% = 500 bps** cho mọi ngành hàng và Seller. Lưu tỷ lệ cùng từng Order khi checkout; Finance dùng tỷ lệ đã lưu, không tra theo Category và không tính lại đơn cũ. Giữ công thức làm tròn nửa lên một lần trên tổng đơn: `C = floor((G × 500 + 5000) / 10000)`, `S = G − C`. |
| P-05 | Liên kết đặt lại mật khẩu có hiệu lực **30 phút** kể từ khi tạo và chỉ dùng một lần. |
| P-08.3 | Mỗi sản phẩm tối đa **9 tệp**, gồm tối đa **2 video**. Mỗi đánh giá tối đa **5 tệp**, gồm tối đa **1 video**. Chỉ chốt số lượng; dung lượng, định dạng và thời lượng giữ trạng thái riêng. |
| P-08.5 | Mô tả sản phẩm tối đa **5.000 ký tự**; nội dung đánh giá tối đa **1.000 ký tự**. |
| P-09 | Mỗi khách hàng được lưu tối đa **3 địa chỉ** hiện có. Xóa hẳn địa chỉ thì địa chỉ đó không còn tính vào giới hạn; đơn cũ giữ bản chụp địa chỉ. |
| P-10 | Mỗi dòng giỏ có **1–99 đơn vị**; mỗi giỏ tối đa **50 dòng sản phẩm khác nhau**. Thêm lại cùng sản phẩm thì cộng vào dòng hiện có, không vượt 99. Mua ngay cũng tối đa 99 đơn vị của một sản phẩm và không tạo dòng giỏ. Checkout vẫn kiểm tra tồn kho thực tế. |
| P-11 | Mặc định **20 sản phẩm/trang**, tìm theo tên, lọc danh mục và khoảng giá; sắp xếp mặc định mới nhất. Các lựa chọn sắp xếp khác giữ theo API. Quy tắc dấu và hoa/thường theo P-37. |
| P-12 | Hiển thị điểm đánh giá trung bình cộng với **1 chữ số thập phân** trên thẻ sản phẩm; tên người đánh giá được che một phần. |
| P-14 | Dùng **GMT+7**; tuần bắt đầu thứ Hai, quý theo lịch. |
| P-15 | Xác định bán chạy theo **số lượng bán**, không phải doanh thu; sản phẩm phải có **trên 500 lượt bán**, đúng 500 chưa đạt. Không tự thêm cách tính kỳ hoặc trạng thái đơn được tính khi nguồn chưa quy định. |
| P-16 | Cảnh báo tồn kho thấp khi tồn kho khả dụng còn **5 sản phẩm trở xuống**. |
| P-18 | **Không khóa tạm tài khoản vì đăng nhập sai nhiều lần.** Vẫn giữ giới hạn tần suất và các biện pháp chống lạm dụng; không nhầm với cấm tài khoản do Admin. |
| P-21a | Giữ `CHANGED_MIND`, `ORDERED_BY_MISTAKE`, `FOUND_BETTER_PRICE`, `ADDRESS_CHANGE`, `OTHER`. Ghi chú tối đa **500 ký tự**; chọn `OTHER` phải nhập ghi chú. |
| P-21b | Giữ `DEFECTIVE`, `OUT_OF_STOCK_ACTUAL`, `CANNOT_DELIVER`, `OTHER`. Seller từ chối giao hàng luôn phải nhập ghi chú, tối đa **500 ký tự**. |
| P-21d | Giữ ba danh sách lý do quản trị riêng cho từ chối hồ sơ Seller, cấm tài khoản và gỡ sản phẩm. Lưu `reason` dạng nhãn hoặc nhãn kèm mô tả, tổng **1–1.000 ký tự**; chọn Khác phải nhập mô tả. Không áp dụng giới hạn 500 ký tự của ghi chú hủy/từ chối đơn. |
| P-21e | Dùng đúng **10 ngành hàng ban đầu** trong bảng dưới. Mỗi sản phẩm thuộc một ngành hàng; Category không có tỷ lệ hoa hồng riêng. |
| P-22 | Thông báo trong ứng dụng hiển thị tối đa **50 thông báo gần nhất**, giữ nội dung **30 ngày**. Giới hạn này không làm mất khóa chống trùng; nếu có email thì không xóa email hoặc cắt lịch gửi lại. In-app là **Must** theo [quyết định 25/11/2026](coverage.md#notification-channels). |
| P-26 | Nạp sẵn danh mục địa giới tĩnh vào **DB của Identity**. Dùng mô hình hai cấp: tỉnh/thành và phường/xã/đặc khu thuộc tỉnh/thành, không có quận/huyện. API và kiểm tra địa chỉ đọc cùng bộ dữ liệu này, không gọi API địa giới bên ngoài. Ghi rõ nguồn và phiên bản khi chuẩn bị dữ liệu; Order giữ cả mã và tên tại lúc đặt hàng. |
| P-36 | Cảnh báo đơn sắp hết hạn khi thời gian xử lý còn **không quá 24 giờ** trước hạn 7 ngày. |
| P-37 | **Đã chốt 19/11/2026:** tìm theo tên sản phẩm và tên cửa hàng **không phân biệt dấu tiếng Việt, không phân biệt hoa thường**. Ví dụ: `dien thoai`, `Điện Thoại` và `ĐIỆN THOẠI` cho cùng kết quả. Chuẩn hóa cả từ khóa và tên khi so khớp: bỏ dấu tiếng Việt, chuyển đ/Đ thành d và chuyển về chữ thường; giữ nguyên tên lưu và hiển thị. |

Các ngưỡng thống kê không tự làm Reporting Service hay các màn phân tích thành bắt buộc; review vẫn là Should. In-app là Must; email nghiệp vụ tùy chọn, triển khai sau, không chặn hoàn thành in-app.

## Thử lại hoàn tiền — P-RF-01

Mỗi chu kỳ có một lần thực hiện đầu và tối đa **3 lần thử lại**. Chờ lần lượt **1, 5 và 15 phút** tính từ lần thất bại trước. Chỉ gửi lại lệnh khi lần trước đã thất bại rõ ràng. Nếu kết quả còn `PENDING` hoặc bị timeout thì tiếp tục tra cứu; tra cứu không tính vào số lần thử lại.

Hết 3 lần mà vẫn thất bại thì dừng tự động và thông báo cho khách đã thanh toán. Khách bấm **Thử lại hoàn tiền** để mở một chu kỳ mới cho cùng Refund, số tiền và mã chống trùng. Đặt lại bộ đếm của chu kỳ nhưng giữ toàn bộ lịch sử. Admin không xử lý thay khách. Áp dụng cho cả hoàn từng đơn (`ORDER`) và hoàn toàn bộ phiên (`FULL_CHECKOUT`). Chỉ ghi nhận đã hoàn và giảm tiền chờ khi cổng xác nhận `SUCCESS`.

Khoảng 21 phút là ước tính nếu mọi lần đều lỗi; còn thời gian xử lý và độ trễ chạy J5, không phải thời hạn cưỡng bức. Không dùng ngưỡng này để kết luận giao dịch chưa rõ kết quả đã thất bại.

## Gửi lại email — P-19

Email thông báo nghiệp vụ không bắt buộc, triển khai sau theo [quyết định 25/11/2026](coverage.md#notification-channels). P-19 giữ nguyên để áp dụng khi triển khai email; không thay luồng xác minh/đặt lại mật khẩu của Identity.

Mỗi email có một lần gửi đầu và tối đa **3 lần gửi lại**, chờ lần lượt **1, 5 và 15 phút** từ lần thất bại trước. Chỉ gửi lại lỗi tạm thời như timeout, mất kết nối, lỗi nhà cung cấp hoặc giới hạn tần suất. Có `Retry-After` thì không gửi sớm hơn thời điểm nhà cung cấp yêu cầu.

Dừng ngay với lỗi vĩnh viễn như địa chỉ nhận không hợp lệ, hoặc liên kết/token trong email đã hết hạn. Hết lượt thì ghi nhận gửi thất bại, không đổi trạng thái đơn, thanh toán, hoàn tiền hay tài khoản. Dùng cùng thông báo và khóa chống trùng, không tạo email nghiệp vụ mới chỉ vì thử lại. Cơ chế xem email demo và thời gian giữ bí mật của email demo vẫn cần quyết định riêng; không lấy 30 ngày của in-app áp dụng sang token/reset URL.

## Lý do quản trị — P-21d

Mỗi thao tác dùng danh sách riêng, không dùng một danh sách chung:

| Thao tác | Các lựa chọn |
|---|---|
| Từ chối hồ sơ Seller | Thông tin đăng ký không chính xác/không nhất quán; Thông tin gian hàng không phù hợp; Khác |
| Cấm tài khoản | Gian lận/lừa đảo; Kinh doanh hàng cấm; Kinh doanh hàng giả/vi phạm sở hữu trí tuệ; Khác. Hai lý do kinh doanh chỉ áp dụng Seller; Customer dùng Gian lận/lừa đảo hoặc Khác. |
| Gỡ sản phẩm | Sản phẩm bị cấm; Hàng giả/vi phạm sở hữu trí tuệ; Nội dung sai lệch/không phù hợp; Đăng trùng lặp/spam; Khác |

Mô tả bổ sung là tùy chọn, riêng Khác phải có mô tả không rỗng sau khi bỏ khoảng trắng đầu/cuối. Đây là lựa chọn do Admin xác minh, không thêm KYC hoặc cơ chế tự phát hiện vi phạm.

## Danh mục ban đầu — P-21e

| STT | Ngành hàng | Ví dụ sản phẩm |
|---|---|---|
| 01 | Thời trang (Fashion) | Quần áo, giày dép, túi xách |
| 02 | Điện thoại & Phụ kiện (Phones & Accessories) | Điện thoại, ốp lưng, sạc |
| 03 | Máy tính & Thiết bị điện tử (Electronics) | Laptop, tai nghe, chuột |
| 04 | Nhà cửa & Đời sống (Home & Living) | Nội thất, đồ gia dụng, trang trí |
| 05 | Sắc đẹp & Chăm sóc cá nhân (Beauty & Personal Care) | Mỹ phẩm, skincare, chăm sóc tóc |
| 06 | Sức khỏe (Health) | Dụng cụ chăm sóc sức khỏe, thiết bị y tế thông thường |
| 07 | Mẹ & Bé (Mother & Baby) | Đồ chơi trẻ em, quần áo trẻ em, đồ dùng em bé |
| 08 | Thực phẩm & Đồ uống (Food & Beverage) | Đồ ăn đóng gói, bánh kẹo, đồ uống |
| 09 | Sách & Văn phòng phẩm (Books & Stationery) | Sách, bút, vở, dụng cụ học tập |
| 10 | Thể thao & Ngoài trời (Sports & Outdoors) | Dụng cụ tập gym, đồ thể thao, camping |

01–10 chỉ là số thứ tự trong tài liệu; `categoryId` vẫn là UUID. Danh mục nền phải có dù chưa làm màn quản trị Category.

## Những giá trị chưa chốt hoặc ngoài phạm vi

- **P-01:** vẫn dùng tạm **15 phút**, chưa chốt.
- P-02/P-03/P-04, P-06, P-08.1/P-08.2/P-08.4, P-SIM-01 và các chi tiết kỹ thuật chưa được quyết định riêng giữ trạng thái hiện có. Không lấy câu giao Tech Lead quyết làm một giá trị số.
- **N-09 và N-11:** không thay đổi trong lần sửa này. Giữ yêu cầu và các câu hỏi hiện hành tại tài liệu sở hữu.
- Chu kỳ chạy job khác với thời gian chờ giữa các lần thử lại. Lịch cloud, thời gian chờ HTTP, khóa worker và cách cấu hình vẫn cần quyết định ở phạm vi tương ứng.
