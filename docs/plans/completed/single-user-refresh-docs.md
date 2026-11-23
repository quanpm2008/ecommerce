# Execution Plan: Một phiên refresh trên User

Date: 2026-11-23

## Status

Completed

## Outcome

Tài liệu Database/API/Identity/FRD/kiến trúc mô tả cùng một phiên refresh hiện hành trên User, theo lựa chọn được người dùng chấp nhận. Không sửa code hoặc database.

## Context

Authority mới: [quyết định 0003](../../decisions/0003-mot-phien-refresh-tren-user.md). Giữ [0001 cookie/Origin](../../decisions/0001-refresh-cookie-va-origin.md), G-14 và P-06 chưa chốt. Code hiện có AuthSession, rotated_refresh_tokens và cookie HttpOnly.

## Scope

- Cập nhật tài liệu hiệu lực trong docs/requirements, docs/engineering.md, bản đồ docs và index quyết định.
- Không sửa nguồn lịch sử docs/api.md, docs/database.md, FRD v5.4, Google Docs, OpenAPI sinh ra, code, migration hay dữ liệu Supabase.

## Approach

Chốt contract chung trong 0003; cập nhật Database và authority/coverage; cập nhật API và hồ sơ/FRD/kiến trúc theo hai phạm vi file độc lập; kiểm tra tĩnh phần thay đổi và hoàn tất kế hoạch.

## Risks And Recovery

- Một phiên thay đa thiết bị là thay đổi có chủ ý được người dùng chọn; tài liệu phải nói rõ code chưa chuyển.
- Lưu hash thay raw token; giữ dấu reuse; token/đăng xuất cũ không thu hồi phiên mới.
- Có thể hoàn tác riêng nội dung tài liệu của lần thay đổi này; không có migration hoặc thay dữ liệu cần phục hồi.

## Progress

- [x] Người dùng chọn một phiên refresh trên User, ghi quyết định 0003.
- [x] Cập nhật tài liệu hiệu lực và ghi nhận chênh lệch với implementation.
- [x] Kiểm tra link/anchor, manifest nguồn và contract refresh; ghi kết quả.

## Decisions

- 2026-11-23: Giữ cookie/Origin; lưu refresh_token_hash, refresh_token_expires_at, session_id trên User, thay AuthSession; giữ lịch sử token đã xoay.

## Validation

Chỉ kiểm tra tài liệu. Không chạy runtime hoặc truy cập database cho task này. Lệnh kiểm tra coverage toàn repo đã có giới hạn link lịch sử được ghi nhận; không dùng nó để tuyên bố toàn repo pass.

- Kiểm tra Python tập trung từ root: PASS 5 hash/số dòng nguồn hiện hành; PASS 29 link đến quyết định 0003 và explicit anchor không trùng trong các tài liệu đã sửa.
- PASS từ điển User có hash/hạn/session_id, không có cột raw refresh_token hay AuthSession như bảng đích; AuthTokens/ví dụ login không chứa refreshToken JSON; refresh/logout dùng cookie + Origin, logout 204.
- PASS FRD không còn cam kết phiên độc lập theo thiết bị; hồ sơ và kiến trúc dẫn quyết định mới.
- Không chạy kiểm tra link lịch sử toàn repo đã biết có lỗi, không chạy test ứng dụng hoặc truy cập Supabase. Đây là bằng chứng cấu trúc/contract tài liệu, không phải runtime proof.

## Result

Đã cập nhật Database, API, FRD, Identity, SAD/System Architecture, coverage, engineering và bản đồ/decision index. Đặc tả một phiên trên User đã nhất quán qua các kiểm tra tập trung ở trên; nguồn lịch sử, code, migration, OpenAPI sinh từ code và Supabase không thuộc phạm vi thay đổi. Rủi ro còn lại là implementation vẫn dùng AuthSession và phải được triển khai/kiểm thử riêng trước khi áp dụng chính sách mới.
