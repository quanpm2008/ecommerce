# Recap chỉnh sửa Google Docs theo Thẻ 15

## 1. Thống nhất cách cấm tài khoản

Trước đây tài liệu còn lẫn cấm theo scope, khóa User, đóng băng ví và quyền dựa vào việc đã từng thanh toán. Đã thay bằng vòng đời theo **nghĩa vụ hiện tại**.

| Trường hợp | Quy định sau sửa |
|---|---|
| Customer/Seller/Admin | Dùng tài khoản độc lập, không cộng quyền nhiều vai trò trên cùng User |
| Admin thực hiện ban | Chọn tài khoản và nhập lý do; không chọn scope USER/CUSTOMER/SELLER |
| Đối tượng được cấm | Customer hoặc Seller đã APPROVED; không cấm Admin, không cấm Seller PENDING/REJECTED |
| Còn nghĩa vụ | **BANNED** — chỉ được truy cập phần cần thiết để giải quyết nghĩa vụ |
| Đã xác minh hết nghĩa vụ | **DISABLED** — chặn đăng nhập, thu hồi phiên |
| Không xác minh được vì nguồn lỗi | Không được coi là hết nghĩa vụ hoặc số dư bằng 0 |

Giữ các trường trạng thái đã có, nhưng quy định rõ cách suy ra `accountStatus`:

| `accountStatus` | `users.status` | Trạng thái hồ sơ Customer/Seller |
|---|---|---|
| ACTIVE | ACTIVE | ACTIVE |
| BANNED | ACTIVE | BANNED |
| DISABLED | DISABLED | BANNED |

## 2. Làm rõ nghĩa vụ và quyền được giữ lại

| Nghĩa vụ | Cách xử lý |
|---|---|
| Order AWAITING_PAYMENT, PAID, PACKING, DELIVERED | Vẫn được tính là chưa kết thúc |
| Payment PENDING hoặc SUCCESS chưa áp dụng xong | Vẫn giữ nghĩa vụ, không cho tạo thanh toán mới |
| Order CANCELLED nhưng Refund PENDING/FAILED | Không coi là đã hết nghĩa vụ chỉ vì đơn đã hủy |
| Seller còn Settlement/hiệu ứng tài chính chưa xử lý | Vẫn giữ nghĩa vụ, kể cả hiệu ứng có số tiền bằng 0 |
| Seller còn pending | Chỉ xem tiến độ, **không rút pending** |
| Seller còn available | Được xem ví/rút theo quyền giới hạn |

**Customer BANNED:** chỉ xem và hủy/xác nhận đơn của mình khi trạng thái cho phép; không dùng giỏ, checkout, thanh toán mới, review hoặc xem toàn bộ lịch sử.

**Seller BANNED:** chỉ xử lý đơn tồn và tiền liên quan; không quản lý sản phẩm/tồn kho, nhận đơn mới hoặc xem thống kê chung.

Đã bỏ cách cấp quyền dựa trên cờ lịch sử `hasPaidOrderBeforeBan`.

## 3. Gỡ cấm, thời hạn 30 ngày và J7

| Nội dung | Quy định sau sửa |
|---|---|
| BANNED | Được Admin gỡ bất kỳ lúc nào |
| DISABLED | Được gỡ khi `now <= restoreUntil` |
| Bắt đầu thời hạn | Từ **lần chuyển DISABLED đầu tiên trong đợt cấm**, không phải từ lúc bắt đầu BANNED |
| Ban/job chạy lặp | Không gia hạn thời hạn |
| Nghĩa vụ xuất hiện muộn | Mở lại BANNED giới hạn; không tự unban, không reset mốc |
| Quá hạn khôi phục | Trả `409 ACCOUNT_RESTORE_WINDOW_EXPIRED`; không xóa tài khoản, dữ liệu hoặc tiền |
| Gỡ cấm thành công | Không tự phục hồi Order đã hủy hoặc bật lại Product HIDDEN/REMOVED |

Đặc tả bổ sung các mốc `banned_at`, `disabled_at`, `restore_until`.

**J7 do Identity sở hữu** đối soát BANNED/DISABLED định kỳ; login/refresh dùng cùng policy. Job phải kiểm tra version/đợt cấm để không ghi đè thao tác unban của Admin.

## 4. Ngoại lệ rút tiền và luồng tạo sản phẩm

| Luồng | Thay đổi cụ thể |
|---|---|
| Seller BANNED còn 9.500đ available | Được rút đúng **toàn bộ 9.500đ** |
| Seller BANNED muốn rút 5.000đ từ số dư 9.500đ | Bị từ chối vì rút một phần dưới ngưỡng |
| Seller ACTIVE còn 9.500đ | Vẫn áp dụng ngưỡng tối thiểu 10.000đ |
| Wallet/UI rút tiền | Thêm `allowBelowMinimumFullWithdrawal`; khi áp dụng ngoại lệ, form khóa amount bằng toàn bộ available |
| UC-D01 tạo Product | Lưu **HIDDEN + công việc provision bền vững**, rồi gọi Order khởi tạo Inventory sau commit |
| Provision timeout | Giữ HIDDEN, retry cùng productId/key; không tạo Product khác |
| Inventory hoàn tất | Chỉ áp dụng trạng thái Seller yêu cầu khi Seller/Product vẫn hợp lệ |
| Seller bị ban hoặc Product REMOVED trong lúc chờ | Không tự bật SELLING |

Đồng thời làm rõ: **validation/media lỗi trước khi nhận tạo** khác với **Product hợp lệ đang HIDDEN chờ Inventory**.

## 5. Nhánh hủy/hoàn sau thu tiền

Đã sửa chỗ dễ hiểu nhầm “Seller bị cấm thì tự hủy đơn”:

- **Order Service** là bên kiểm tra khi áp dụng Payment SUCCESS qua `payment.succeeded` hoặc handler đối soát chung.
- Chỉ khi **chứng minh giao dịch không hợp lệ** mới hủy/hoàn riêng Order đủ điều kiện PAID/PACKING.
- **Ban Seller sau thanh toán không tự hủy đơn**, kể cả event thanh toán đến chậm.
- Identity/Product timeout hoặc thiếu bằng chứng → giữ công việc retry, không coi là invalid.
- Payment vẫn SUCCESS; giữ đủ `checkout.paid`/`order.cancelled`.
- Finance chỉ giảm pending sau **Refund SUCCESS**; replay không được nhân đôi kho, Refund hoặc ledger.

## 6. API, UI và các thẻ đã đồng bộ

| Phần               | Nội dung đã sửa                                                                                                     |
| ------------------ | ------------------------------------------------------------------------------------------------------------------- |
| Ban/unban API      | Bỏ `scope` khỏi body; ban nhận `{reason}`, unban nhận `{reason?}`; gửi scope bị 400                                 |
| DTO/quyền          | Đồng bộ `accountStatus`, `accessMode`, các mốc ban/khôi phục và thông tin nghĩa vụ                                  |
| Internal API       | Đổi phụ thuộc lịch sử thanh toán sang **Order account obligations** và **Finance seller obligations**               |
| Popup IV.P1        | Hiện nghĩa vụ, pending/available, trạng thái dự kiến và quyền còn lại; nguồn lỗi khóa xác nhận, không hiện số 0 giả |
| Admin detail IV.3  | Hiện mốc/deadline gỡ cấm; POST dùng kết quả thực tế, không tin snapshot preview                                     |
| Conflicts          | Giữ quyết định cũ để truy vết nhưng đánh dấu phần đã bị Thẻ 15 thay thế                                             |
| Kế hoạch tổng quan | Bổ sung kiểm tra ban/unban/J7 ở tuần 11 và các ca nghiệm thu mới ở tuần 12                                          |
