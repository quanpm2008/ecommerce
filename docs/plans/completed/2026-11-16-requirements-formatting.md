# Execution Plan: Chuẩn hoá hình thức ba tài liệu yêu cầu

Date: 2026-11-16

## Status

Completed

## Outcome

Chuẩn hoá Markdown của `docs/requirements/database.md`, `api_specs.md` và `FRD.md`, giữ nguyên nội dung cốt lõi và các định danh tham chiếu.

## Context

- Yêu cầu người dùng: chỉnh hình thức, bỏ thông tin không cần thiết, không thay đổi nội dung cốt lõi.
- Quy trình: `docs/WORKFLOW.md`; bản đồ nguồn: `docs/README.md`.
- Ba tài liệu mới là đối tượng biên tập; không sửa code, chính sách nghiệp vụ hoặc thứ tự ưu tiên nguồn của repository.

## Scope

In scope:

- Tiêu đề, mục lục có liên kết, cấu trúc heading, bảng, danh sách và khối mã.
- Loại bỏ dấu vết xuất tài liệu, định dạng thừa và nội dung biên tập trùng lặp rõ ràng.

Out of scope:

- Thay đổi schema, endpoint, enum, quyền, quy tắc, AC, phiên bản, trạng thái triển khai và các điểm còn mở.
- Tự giải quyết mâu thuẫn nguồn hoặc nâng tài liệu dự thảo thành contract đã triển khai.

## Approach

Mỗi tài liệu có một người biên tập riêng, theo cùng quy ước: một H1; phần chính H2, phần con H3 trở xuống; giữ số mục/UC/mã tham chiếu; mục lục liên kết. Chủ tích hợp lưu baseline và kiểm tra cấu trúc cùng nội dung trước/sau sau khi cả ba hoàn tất.

## Risks And Recovery

- Rủi ro: chỉnh Markdown làm mất hoặc đổi ý nghĩa. Giữ baseline ngoài repository, đối chiếu phần nội dung loại bỏ/thay đổi và kiểm tra code block, bảng, tham chiếu.
- Không sửa thay đổi ngoài phạm vi. Nếu phát hiện mâu thuẫn nghiệp vụ, giữ nguyên và báo giới hạn thay vì suy diễn quyết định.

## Progress

- [x] Chuẩn hoá database.md.
- [x] Chuẩn hoá api_specs.md.
- [x] Chuẩn hoá FRD.md.
- [x] Kiểm tra cấu trúc Markdown và bảo toàn nội dung.

## Decisions

- 2026-11-16: Chỉ biên tập hình thức; lịch sử quyết định, giới hạn triển khai và câu hỏi mở có giá trị truy vết nên được giữ.

## Validation

- Kiểm tra Markdown bằng `markdown-it-py`: cả ba file có đúng một H1, không nhảy cấp heading, 107 liên kết mục lục hợp lệ; các bảng đủ cột, khối mã đóng đầy đủ.
- Đối chiếu token nội dung từ phần thân với baseline: không mất hoặc thay đổi nội dung sau khi chuẩn hoá khác biệt hình thức (hoa/thường ở tiêu đề, khoảng trắng, dấu đầu dòng, escape xuất tài liệu và cách trình bày URL).
- API: 20 ví dụ được đặt trong khối mã; 5 ví dụ JSON hoàn chỉnh qua `json.loads`, 15 ví dụ rút gọn/kèm HTTP giữ nhãn `text`.
- Đã mở bản render HTML tạm trong trình duyệt, quan sát API envelope, bảng User và use case FRD; nhấp mục lục tới đúng section ở cả ba tài liệu.
- Không chạy test ứng dụng vì không thay đổi runtime. Script, baseline và bản render chỉ dùng tạm ngoài repository.

## Result

Đã hoàn tất ba tài liệu: thống nhất cấu trúc và điều hướng, bỏ tiêu đề lặp/định dạng thừa, sửa cách hiển thị ví dụ API mà không đổi dữ liệu mẫu. Lịch sử quyết định, cảnh báo chưa triển khai, tham chiếu phiên bản cũ và mâu thuẫn nguồn được giữ nguyên; đây không phải xác nhận contract đã nhất quán hoặc hệ thống sẵn sàng production.
