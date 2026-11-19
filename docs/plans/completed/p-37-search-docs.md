# Đồng bộ tài liệu P-37

Date: 2026-11-19

## Status

Completed

## Outcome

Tài liệu hiện hành trong repo chốt tìm theo tên sản phẩm và tên cửa hàng không phân biệt dấu tiếng Việt, không phân biệt hoa thường, giống quyết định P-37 trên Google Docs.

## Context

Nguồn: yêu cầu người dùng ngày 19/11/2026 và [Thẻ 16](https://docs.google.com/document/d/1VukChbA8p9eOU_loe7y_RVCFvdkzIeMDQCWBoUn2nRI/edit?tab=t.m7xl31skw7qz). [Bảng tham số](../../requirements/parameters.md), [Catalog](../../requirements/catalog.md), [coverage](../../requirements/coverage.md) và SAD §6.7 là đầu mối đọc.

## Scope

- Sửa đặc tả hiện hành, hồ sơ nghiệp vụ, authority và trạng thái C-08/OQ-006.
- Không sửa code, migration, OpenAPI sinh từ ứng dụng, bản nguồn lịch sử docs/api.md, docs/database.md, FRD v5.4 hoặc kế hoạch đã hoàn tất.
- Không chốt N-09/N-11 hoặc phương án index chi tiết; không thêm full-text/Search Service.

## Approach

Contract owner sửa FRD/API/Database hiện hành; architecture owner sửa SAD/System Architecture/engineering; integration owner sửa tham số, Catalog/Identity, README, coverage và manifest. Chỉ chạy kiểm tra tài liệu sau khi các phần đã tích hợp.

## Risks And Recovery

- Giữ nguyên nguồn lịch sử, mã yêu cầu và thay đổi có sẵn của người dùng.
- Chỉ khôi phục các đoạn của lượt này nếu cần; không reset cả file/worktree.
- Tài liệu không chứng minh backend đã triển khai.

## Progress

- [x] Xác định authority và phạm vi.
- [x] Sửa contract, kiến trúc và hồ sơ nghiệp vụ.
- [x] Kiểm tra nội dung và cập nhật manifest nguồn hiện hành.

## Decisions

- 2026-11-19: P-37 áp dụng cho tên sản phẩm và tên cửa hàng; “dien thoai”, “Điện Thoại”, “ĐIỆN THOẠI” cho cùng kết quả. Chuẩn hóa khi so khớp, không đổi tên lưu/hiển thị.

## Validation

- PASS: hash và số dòng của 5 nguồn hiện hành; hash của 3 nguồn lịch sử khớp baseline. Hash Database được cập nhật lại sau khi sửa ghi chú còn sót ở §8.3.
- PASS: không còn ghi chú P-37 chưa chốt/không đổi trong tài liệu hiện hành; C-08 và AC cho tên sản phẩm/tên cửa hàng có quy tắc và ví dụ tương đương.
- PASS: ID duy nhất, giữ đủ 37 UC/17 G/11 WD/26 AT.
- Bộ quét ban đầu khớp nhầm câu giữ nguyên tên hiển thị và cách viết “không phân biệt dấu tiếng Việt và hoa thường”; đã thu hẹp mẫu kiểm tra. Không đổi đặc tả để khớp cách viết máy móc.
- Không chạy runtime: chỉ sửa tài liệu. Không chạy lại kiểm tra link toàn repo đã biết thiếu đường dẫn business/; không tuyên bố các link đó đã được sửa.

## Result

Đã cập nhật 12 tài liệu hiện hành: bảng tham số, FRD/API/Database, Catalog/Identity, SAD/System Architecture, hai README, engineering và coverage. P-37/C-08 dùng cùng quyết định; OQ-006 chỉ còn phương án index và bằng chứng implementation/test. Nguồn lịch sử và code giữ nguyên.
