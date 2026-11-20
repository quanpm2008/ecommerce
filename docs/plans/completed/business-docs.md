# Chuyển tài liệu nghiệp vụ cho coding agent

Ngày: 2026-11-11. Trạng thái: Completed.

## Kết quả và authority

Người dùng duyệt cây tài liệu trong trao đổi và chấp nhận
`business/Software-Architecture.md` v1.1 §7.1, D.1 làm baseline.
Giữ nguyên năm tài liệu `business/`, code ứng dụng, schema, API artifacts,
test, cấu hình, index Git và mọi thay đổi có trước.

## Phạm vi

- Cập nhật `AGENTS.md` ngoài khối HARNESS, `docs/README.md`,
  `docs/requirements/README.md`.
- Tạo `docs/engineering.md`, `docs/requirements/{identity,catalog,checkout-orders,
  payments,finance,notifications,coverage}.md`.
- Đặc tả đầy đủ Must; ghi nguồn/ranh giới Should/Could và UC đã loại bỏ.
- Giữ mã nguồn; cấp mã ổn định cho điều khoản/AC chưa có mã. Phân biệt
  yêu cầu, hiện trạng, đề xuất, câu hỏi mở và bằng chứng chưa chạy.
- Không thêm ADR: dẫn sổ quyết định đã chấp nhận trong SAD.

## Tiến trình

- [x] Đọc hướng dẫn, kiểm tra phê duyệt và chụp baseline hash/index tại
  `/tmp/ecommerce-business-docs-baseline.json`.
- [x] Chuyển Must, bổ sung quy tắc dữ liệu/quyền/trạng thái/ngoại lệ và AC.
- [x] Lập bản đồ contract/code/schema/test và inventory/coverage nguồn.
- [x] Kiểm tra link, mã, độ phủ, baseline và thử tra cứu ba tình huống.
- [x] Ghi kết quả, giới hạn; chuyển kế hoạch sang completed sau validation.

## Cách thực hiện và kiểm tra

Dùng một bản đồ và sáu hồ sơ nghiệp vụ; quy tắc chung chỉ có một nơi sở hữu.
Nguồn FRD/DB/API được giữ nguyên và dẫn đường dẫn, mục, khoảng dòng; SAD
được áp dụng trước khi diễn giải lại điều khoản đã bị thay thế. Chi tiết
DTO/data dictionary vẫn nằm ở nguồn contract, không tự tạo hợp đồng mới.

Kiểm tra tĩnh bằng Python ở `/tmp/`: mọi UC có đích hoặc lý do chưa chuyển;
G-01…17, WD-01…11, AT-01…26 có chủ sở hữu; mọi link local tồn tại;
mọi mục/điều khoản trích xuất khớp nguồn; protected files/hash/index bất biến.
Kiểm tra nội dung các nhánh lỗi, quyền và điều khoản SAD supersede bằng review.
Không chạy Maven/Docker: đây là thay đổi tài liệu, test ứng dụng có thể tạo
container và ghi lại API artifacts.

## Rủi ro và phục hồi

Các tham số và DELTA chưa chốt được giữ mở; không lấy cấu hình dev làm policy.
Nguồn gốc S1–S6 không có đầy đủ trong business, nhưng baseline SAD đã được
người dùng chấp nhận. Các phần ngoài Must chỉ ghi ranh giới, chưa có full AC.
Nếu cần phục hồi, chỉ khôi phục đúng file tài liệu đã sửa từ bản chụp trước
hoặc gỡ các file mới của công việc; không reset/clean toàn worktree.

## Quyết định trong lượt này

- 2026-11-11: thực hiện trực tiếp trên worktree có thay đổi của người dùng;
  không tạo control-plane/worktree hoặc commit. Một plan Git-native theo
  AGENTS và phạm vi được duyệt thay cho workspace/ledger riêng của skill.
- Mâu thuẫn chưa được baseline giải quyết giữ mở, không chặn chuyển phần
  độc lập và không tự chốt chính sách.

## Kết quả validation

- `python3 -B /tmp/validate_ecommerce_business_docs.py`: PASS. Chạy block kiểm
  tra đã ghi trong coverage; kiểm tra độc lập mọi dòng có nghĩa của UC Must
  khớp 377 clause đích hoặc 21 hiệu chỉnh baseline có dẫn nguồn; kiểm tra
  Markdown table columns/header, code/link/anchor, hash và phạm vi file.
- 37 UC = 32 Must/mixed + 3 Should/Could chỉ ranh giới + 2 đã loại bỏ;
  G-01…17, WD-01…11, AT-01…26 có owner; 29 AC theo Given/When/Then.
- Hash cả năm nguồn business không đổi; index Git/staged rename nguyên vẹn;
  609 file có trước ngoài ba file được phép cập nhật giữ nguyên hash;
  khối HARNESS của AGENTS giữ nguyên bytes.
- Review độc lập chỉ đọc và mô phỏng tra cứu Seller profile, Address delete,
  Withdrawal: tìm được authority, code/schema/test, gap và proof. Đây là
  review có context, không phải thử nghiệm fresh-agent cô lập hoặc test runtime.
- Review phát hiện và đã sửa: điều kiện Customer BANNED theo snapshot,
  unaccent/lowercase đã chốt trong SAD §6.7, link nguồn khác file, header
  bảng role và heading state rỗng. Giữ nguyên nguồn thay vì sửa để che lệch.
- Không chạy Maven/Docker/service, test ứng dụng/AT, CI hay benchmark;
  không có bằng chứng runtime mới. Validator tạm không được đưa vào repo;
  kiểm tra link/hash/mã có lệnh tự chứa trong coverage để chạy lại.
- Review lại đúng các lỗi đã sửa: tất cả blocker được xác nhận đã giải quyết;
  không mở rộng thành một lượt kiểm thử runtime. Quy tắc công nghệ không có mã
  trong nguồn được cấp REQ-ENG-001…010, không đánh lại mã có sẵn.

## Kết quả bàn giao

Cập nhật ba entrypoint được duyệt (AGENTS, docs/README, requirements/README);
tạo engineering, sáu hồ sơ nghiệp vụ và coverage. Coverage lưu manifest,
inventory section, UC/rule/AT routing, 21 clause hiệu chỉnh và câu hỏi/phần
chưa chuyển đầy đủ. Chi tiết DTO/data dictionary và Should/Could giữ ở nguồn
theo phạm vi đã thống nhất, không coi chúng đã được đặc tả/kiểm thử toàn bộ.
Code nghiệp vụ, tài liệu business và thay đổi có trước không bị sửa.
