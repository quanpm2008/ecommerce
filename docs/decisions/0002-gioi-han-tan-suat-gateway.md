# 0002 Giới hạn tần suất và kích thước body tại Gateway

Date: 2026-11-20

## Status

Accepted

## Context

[API §3.4](../requirements/api_specs.md) yêu cầu 429 khi vượt rate limit (N-04) nhưng không cho con số;
P-18 ([parameters](../requirements/parameters.md)) giữ rate limit cho đăng nhập nhưng không khóa tạm
tài khoản. REQ-ENG-010 ([engineering](../engineering.md)) loại Redis khi chưa có yêu cầu; SAD C.5 ghi
rate limit in-memory không phải giới hạn toàn hệ thống khi có nhiều instance. Không nguồn nào cho giới
hạn kích thước body.

## Decision

- Gateway giới hạn theo IP client thật (địa chỉ socket, không tin `X-Forwarded-For` do client gửi),
  bộ đếm in-memory **theo từng instance**, mọi giá trị cấu hình được:
  - Endpoint xác thực nhạy cảm: `POST /api/v1/identity/auth/login`, `/register`, `/register-seller`,
    `/refresh`, `/password/forgot`, `/password/reset`, `/seller-reapplication/challenge`,
    `/seller-reapplication`: **10 request/phút/IP**.
  - Các route còn lại: **300 request/phút/IP**.
  - Vượt giới hạn: 429 `RATE_LIMITED` trong envelope lỗi, kèm `Retry-After`.
- Body request tối đa **1 MB** (media upload đi thẳng tới object storage qua presigned URL).
- Giới hạn riêng của Identity (đăng ký, quên mật khẩu, đăng nhập, xin đăng ký lại Seller) vẫn giữ;
  không có khóa tạm tài khoản vì đăng nhập sai (P-18).

## Alternatives Considered

1. Chỉ giới hạn endpoint nhạy cảm, không giới hạn chung.
2. Bộ đếm dùng chung (Redis): bị loại theo REQ-ENG-010.

## Consequences

Positive:

- Có 429 thống nhất trước khi request tới service; giá trị đổi được không cần sửa code.

Tradeoffs:

- Với N instance gateway, giới hạn thực tế tối đa là N lần giá trị cấu hình.
- Các con số là đề xuất của team, chưa phải N-04 chính thức; đổi khi N-04 được chốt.

## Follow-Up

- Chốt N-04 và cách lưu bộ đếm khi chạy nhiều instance (system-architecture, SAD C.5).
