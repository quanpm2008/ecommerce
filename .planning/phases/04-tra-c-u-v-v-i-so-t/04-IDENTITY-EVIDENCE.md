# Phase 4 — Nghiệm thu với Identity thật

Ngày 24/11/2026. Identity, Order, Payment và Finance chạy bằng JAR Java 21, HTTP loopback và PostgreSQL 16 thử nghiệm. Seed Seller, khoản tiền và checkout dành riêng cho kiểm thử. Phiên truy cập do API login của Identity phát hành.

| Ca | Bằng chứng |
| --- | --- |
| Admin có `operations:annotate` | Identity trả permission từ migration V9. Payment trả 200 cho `mark-handled`; Order lưu một audit `MARK_HANDLED` và tắt cờ. Seller gọi cùng API trả 403. |
| Admin chỉ đọc | Bỏ riêng grant annotate trong database Identity test, gọi danh sách bất thường được 200, gọi `mark-handled` nhận 403. Grant được khôi phục sau ca thử. |
| Seller BANNED có 9.500đ khả dụng | Identity đọc Order và Finance bằng credential riêng, trả `WITHDRAWAL_ONLY` với hai quyền ví. Ví báo có thể rút hết dưới ngưỡng. Rút 5.000đ nhận 422. Rút đủ 9.500đ nhận 201. |
| Số dư và hiệu ứng sau rút | PostgreSQL ghi available = 0, đã rút = 9.500đ, một Withdrawal, một ledger và một outbox. Gọi lại sau khi đã hết quyền rút nhận 403 theo quy tắc kiểm quyền hiện hành trước replay. Replay cùng khóa khi quyền còn hiệu lực đã đạt riêng trong bộ kiểm thử Finance. |
| Phân loại chế độ và nguồn lỗi | `SellerObligationsTests` đạt 2/2, gồm có đơn và có tiền, chỉ đơn, chỉ số dư, chỉ pending và thiếu nguồn Finance. |

[HTTP với Identity thật](../../../order-service/target/phase4-identity-e1-http-final.log) · [Admin chỉ đọc](../../../order-service/target/phase4-identity-e1-read-only.log) · [Đối chiếu PostgreSQL](../../../order-service/target/phase4-identity-e1-db.log) · [Test Identity](../../../identity-service/target/phase4-close-package.log)
