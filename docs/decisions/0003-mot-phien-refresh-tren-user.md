# 0003 Một phiên refresh trên User

Date: 2026-11-23

## Status

Accepted — người dùng chọn chuyển từ AuthSession sang một phiên refresh cho mỗi User ngày 23/11/2026. Đây là đặc tả cần triển khai; code và database hiện tại vẫn dùng AuthSession.

## Context

Người dùng yêu cầu bổ sung refresh token vào User và cập nhật API. Sau khi được giải thích về lưu hash, không lưu token nguyên văn và ảnh hưởng đến đăng nhập nhiều thiết bị, người dùng chọn một phiên refresh trên User thay cho AuthSession. Quyết định này thay riêng cách lưu và quản lý phiên; giữ [quyết định 0001](0001-refresh-cookie-va-origin.md) về cookie/Origin, tài khoản độc lập và quyền theo nghĩa vụ.

## Decision

- User lưu `refresh_token_hash` TEXT NULL UNIQUE, `refresh_token_expires_at` TIMESTAMPTZ NULL và `session_id` UUID NULL UNIQUE. Ba trường cùng NULL khi chưa có phiên hoặc đã thu hồi; khi cấp phiên phải có đủ cả ba. `session_id` là mã phiên đăng nhập hiện tại, không phải CheckoutSession và không phải refresh token.
- Không thêm cột `refresh_token` chứa token nguyên văn. Token nguyên văn chỉ được cấp qua cookie HttpOnly `refresh_token`; không trả trong DTO User, AuthTokens, API quản trị, log hoặc sự kiện.
- Mỗi User có tối đa một phiên hiệu lực. Login thành công, kể cả Google Should, tạo `session_id` mới và thay hash/thời hạn trong cùng transaction. Phiên trước mất khả năng refresh và access token mang sessionId cũ bị từ chối ở lần kiểm tra quyền tiếp theo, dù JWT chưa hết hạn. Login sai hoặc kiểm tra nghĩa vụ lỗi không thay phiên đang có. Hai login đồng thời được tuần tự hóa trên User; phiên ghi thành công sau cùng là phiên hiện hành.
- Refresh chỉ đọc cookie, kiểm tra Origin và đối chiếu hash/thời hạn với User. Sau khi xác minh quyền và nghĩa vụ hiện hành, xoay hash trong cùng transaction với dấu token cũ; giữ `session_id`, cập nhật hạn bằng thời điểm refresh thành công cộng TTL refresh đang cấu hình. P-06 vẫn chưa chốt; không biến ví dụ 15 phút/7 ngày thành giá trị bắt buộc mới.
- Giữ `rotated_refresh_tokens` làm lịch sử chống dùng lại: `token_hash` TEXT PK, `user_id` UUID FK User, `session_id` UUID, `rotated_at` TIMESTAMPTZ. Không còn FK đến AuthSession; đây không phải nơi lưu phiên hiệu lực thứ hai. Giữ dấu của toàn bộ chuỗi trong khi phiên đó còn hiệu lực để nhận biết reuse dù phiên được gia hạn.
- Dùng lại token đã xoay của đúng `users.session_id` hiện hành: thu hồi phiên đó bằng cách xóa cả ba trường trên User, commit việc thu hồi rồi trả 401 INVALID_REFRESH_TOKEN. Token không biết, đã hết hạn hoặc thuộc phiên bị thay thế trả 401, không được xóa phiên mới. Refresh đồng thời cùng token chỉ xoay thành công một lần; lần còn lại là reuse và thu hồi chuỗi theo quy tắc này. Không thêm grace period.
- Logout chỉ dùng cookie và kiểm tra Origin, không bắt buộc access token còn hạn. Token hiện tại hoặc dấu token đã xoay chỉ được thu hồi khi thuộc đúng User và đúng phiên hiện hành; cookie của phiên cũ không được xóa phiên mới. Thành công, cookie thiếu, token không biết hoặc phiên đã kết thúc đều trả 204 và xóa cookie đúng Path/thuộc tính. Sai Origin trả 403, không thay dữ liệu. Không nhận userId/sessionId từ client để chọn tài khoản cần thu hồi.
- Đổi/reset mật khẩu và chuyển DISABLED thu hồi phiên hiện hành cùng transaction cập nhật User; password reset không tạo phiên mới. Thu hồi access token dựa sessionId hiện hành và authzVersion như contract quyền hiện có. Việc thay phiên do login không phải thay vai trò/quyền, không tự tăng authzVersion vì lý do này.
- Login, refresh, logout và thu hồi phiên phải khóa/cập nhật có điều kiện trên cùng User rồi kiểm tra lại hash/sessionId trước ghi. HTTP hỏi nghĩa vụ vẫn ngoài transaction giữ khóa; kiểm tra lại version/trạng thái trước commit, không để refresh hoặc logout cũ ghi đè login mới.
- API liên quan dùng lại các endpoint login, Google Should, refresh, logout, đổi/reset mật khẩu và internal authorization-context hiện có. Không thêm API đọc/ghi trực tiếp refresh token.

## Alternatives Considered

1. Giữ AuthSession để hỗ trợ nhiều thiết bị: không được chọn.
2. Lưu token nguyên văn hoặc lưu đồng thời phiên hiệu lực ở User và AuthSession: không dùng vì lộ bí mật và có hai nguồn trạng thái.

## Consequences

Positive:

- User là nguồn duy nhất cho phiên hiện hành; vẫn xoay token và phát hiện reuse.
- API giữ cookie HttpOnly và không lộ bí mật trong dữ liệu hồ sơ.

Tradeoffs:

- Đăng nhập trên thiết bị khác kết thúc phiên trước; các tab dùng chung cookie vẫn cùng một phiên, không phải các phiên độc lập.
- Vẫn cần bảng dấu token đã xoay để giữ bảo vệ chống reuse; thêm trường User không loại bỏ mọi bảng kỹ thuật.
- Nhiều tab phải phối hợp refresh theo SAD §6.3; gửi lại token đã xoay có thể làm cả chuỗi bị thu hồi.

## Follow-Up

Phạm vi được giao chỉ là tài liệu. Khi triển khai phải thêm migration mới, không sửa V1–V11 đã áp dụng: bổ sung trường User, thu hồi mọi phiên cũ để yêu cầu đăng nhập lại, chuyển dấu token sang cấu trúc mới và bỏ AuthSession sau khi mọi caller/validator đã chuyển sang User. Không chọn tùy ý một thiết bị cũ để giữ phiên. Migration, code, OpenAPI sinh từ code, test ứng dụng và database Supabase chưa được thay đổi bởi quyết định này.
