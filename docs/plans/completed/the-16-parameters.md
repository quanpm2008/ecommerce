# Đồng bộ các tham số Thẻ 16

Ngày: 18/11/2026

## Trạng thái

Hoàn tất cập nhật tài liệu và kiểm tra phần thay đổi.

## Kết quả cần có

Tài liệu trong `docs/` dùng đúng các giá trị đã chốt tại Thẻ 16 và các xác nhận của người dùng. Quy tắc được viết trực tiếp cho team dev, không thay bằng danh sách vị trí đã chỉnh sửa. Không coi tài liệu là bằng chứng code đã triển khai.

## Nguồn

- [Thẻ 16 trên Google Docs](https://docs.google.com/document/d/1VukChbA8p9eOU_loe7y_RVCFvdkzIeMDQCWBoUn2nRI/edit?tab=t.m7xl31skw7qz).
- Người dùng yêu cầu áp dụng cả vào tài liệu repository sau khi các thẻ Google Docs đã được lưu và đọc lại.
- P-01 giữ tạm 15 phút, chưa chốt; P-22 được làm rõ là tối đa 50 thông báo gần nhất, giữ 30 ngày.
- `docs/WORKFLOW.md`, bản đồ tài liệu và quy ước authority trong `docs/requirements/README.md`.

## Phạm vi

Sửa hồ sơ nghiệp vụ, ba đặc tả FRD/API/Database, các phần kiến trúc và bản đồ/coverage bị ảnh hưởng. Tập hợp giá trị tại `docs/requirements/parameters.md` để người đọc không phải đoán từ ví dụ hoặc cấu hình dev.

Không sửa code, migration, OpenAPI sinh từ ứng dụng, cấu hình, test runtime hoặc hướng dẫn vận hành. Không chốt N-09, N-11, P-37; không đổi phạm vi Should/Could hoặc các quyết định khác chưa được duyệt. Giữ các thay đổi có sẵn của người dùng, gồm coverage và các kế hoạch Thẻ 15.

## Cách thực hiện

- Người phụ trách hồ sơ Identity/Catalog/Order sửa các giới hạn, địa giới, lý do và cảnh báo.
- Người phụ trách Payment/Finance/Notification sửa hoa hồng, chu kỳ thử lại và thông báo.
- Người phụ trách FRD/API/Database sửa các contract chi tiết, không thay toàn bộ nguồn bằng bản export.
- Người phụ trách kiến trúc sửa phần tham số hiện hành, giữ các quyết định ngoài phạm vi.
- Chủ tích hợp cập nhật nguồn tham số, bản đồ, coverage, hash và kiểm tra tài liệu một lần sau khi các phần hoàn tất.

## Rủi ro và khôi phục

- Giữ riêng giới hạn thử lại với chu kỳ chạy job; timeout không phải thất bại đã xác nhận.
- Không ghi đè tỷ lệ hoa hồng đã chụp của đơn cũ; các đơn mới dùng mức chung 5%.
- Không xóa lịch sử hoặc khóa chống trùng khi dọn thông báo trong ứng dụng.
- Chỉ hoàn tác các đoạn của task này nếu cần; không dùng lệnh reset/clean vì worktree đã có thay đổi của người dùng.
- Kiểm tra tài liệu hiện có đã ghi nhận link lịch sử `business/` thiếu. Không chạy lại để xác nhận lỗi đã biết, không che hoặc sửa rộng các link đó trong task này.

## Tiến độ

- [x] Đọc nguồn quyết định, quy trình và khoanh tài liệu sở hữu.
- [x] Cập nhật tài liệu theo từng nhóm có phạm vi file riêng.
  - Hồ sơ nghiệp vụ, FRD/API/Database, kiến trúc, bản đồ và nguồn tham số đã được sửa.
  - Quyền thử lại hoàn tiền thuộc Customer chủ Payment; Admin chỉ xem tiến độ, ghi chú xử lý khoản thu không làm đổi Refund.
- [x] Kiểm tra nội dung đã chốt, link/anchor mới và hash nguồn hiện hành.
- [x] Ghi kết quả và chuyển kế hoạch sang completed.

## Kiểm chứng

Đã chạy `python3 -B /tmp/check-the16-docs.py` với kết quả:

- PASS: hash SHA-256 và số dòng của 5 nguồn hiện hành khớp manifest.
- PASS: 1.457 link/anchor còn truy cập được; ID tường minh duy nhất; đủ 37 UC, 17 G, 11 WD và 26 AT.
- PASS: giá trị đã chốt, danh sách 10 ngành hàng, nguồn tham số trong các hồ sơ và ranh giới quyền hoàn tiền.
- Giới hạn: bỏ riêng 634 link nguồn/implementation lịch sử đã được ghi nhận thiếu từ trước; không tuyên bố check toàn repo đã pass.
- Check ban đầu chưa hỗ trợ anchor tự sinh từ tiêu đề Markdown; đã bổ sung cách kiểm tra phù hợp. Sau đó phát hiện một link mục lục FRD còn trỏ tiêu đề cũ, đã sửa link và cập nhật lại hash FRD; lần kiểm tra cuối pass.
- Không chạy Maven, Docker, migration, generated OpenAPI hoặc UAT. Script kiểm tra tạm được xóa sau khi chạy.

## Kết quả

Repository có bảng tham số thống nhất và các quy tắc hiện hành tương ứng trong hồ sơ nghiệp vụ, contract, kiến trúc và coverage. P-01 vẫn tạm 15 phút; N-09, N-11 và P-37 không được chốt lại. Các thay đổi có sẵn ở coverage và các kế hoạch Thẻ 15 được giữ lại. Tài liệu không chứng minh code đã đáp ứng; nguồn/version địa giới, các tham số kỹ thuật chưa chốt và quyền xem email demo vẫn mở đúng phạm vi.
