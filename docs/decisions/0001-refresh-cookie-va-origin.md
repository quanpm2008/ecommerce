# 0001 Refresh token trong cookie HttpOnly và kiểm tra Origin

Date: 2026-11-20

## Status

Accepted

## Context

[REQ-IDN-006](../requirements/identity.md#req-idn-006) và [SAD §6.3](../requirements/software-architecture.md)
yêu cầu access token chỉ ở memory, refresh token trong HttpOnly Secure Cookie, endpoint dùng cookie
phải kiểm tra Origin theo allowlist và có cơ chế chống CSRF được kiểm chứng, CORS có credentials không
dùng wildcard. Thuộc tính cụ thể còn mở tại [OQ-004](../requirements/coverage.md#cau-hoi). Hiện trạng
identity-service trả refresh token trong JSON ([GAP-IDN-004](../requirements/identity.md)).

## Decision

- Identity đặt cookie `refresh_token`: `HttpOnly`, `Secure`, `SameSite=Strict`,
  `Path=/api/v1/identity/auth`. Không trả refresh token trong JSON.
- Login và refresh đặt cookie mới; refresh chỉ đọc cookie; logout thu hồi phiên rồi xóa cookie với
  đúng các thuộc tính trên.
- Chống CSRF: `SameSite=Strict` cộng kiểm tra header `Origin` của refresh/logout theo allowlist
  (`IDENTITY_ALLOWED_ORIGINS`). Origin thiếu hoặc ngoài allowlist bị từ chối.
- Profile `local` tường minh bỏ `Secure` chỉ cho `http://localhost`; không tắt `Secure` toàn cục.
- Gateway bật CORS `allowCredentials` cho cùng danh sách origin (`GATEWAY_CORS_ALLOWED_ORIGINS`) và
  chuyển nguyên `Cookie`/`Set-Cookie`.
- Origin dev của SPA: `http://localhost:5173` (Vite), dùng chung cho CORS, allowlist Identity và link
  đặt lại mật khẩu; môi trường khác cấu hình qua biến môi trường.

## Alternatives Considered

1. `SameSite=Lax` cộng CSRF token double-submit: chắc hơn khi SPA và API khác site, nhưng FE phải
   quản lý token; chưa cần khi SPA và API cùng site.
2. Giữ refresh token trong JSON: không đạt REQ-IDN-006/AT-25.

## Consequences

Positive:

- JavaScript không đọc được refresh token; request cross-site không gửi cookie.

Tradeoffs:

- Nếu triển khai SPA và API khác site, `SameSite=Strict` chặn cookie; khi đó phải xem lại quyết định này.
- FE phải gọi refresh/logout với `credentials: "include"`.

## Follow-Up

- Thời hạn access/refresh token (P-06) vẫn giữ trạng thái hiện có.
- Service credential nội bộ (phần còn lại của OQ-004/DELTA-07) không thuộc quyết định này.
