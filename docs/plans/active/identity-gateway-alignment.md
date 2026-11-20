# Kế hoạch: đồng bộ identity-service theo đặc tả v1.1 và hoàn thiện Gateway trên WebFlux

Ngày: 2026-11-20

## Trạng thái

Active

## Kết quả cần có

- identity-service đáp ứng [hồ sơ Identity](../../requirements/identity.md) theo API v1.1, Database v5.1,
  FRD v5.5, [Thẻ 15](../../requirements/coverage.md#the-15) và [tham số Thẻ 16](../../requirements/parameters.md).
- gateway-service chạy trên Spring Cloud Gateway WebFlux (ADR-02, REQ-ENG-002) và đạt
  [checklist](../../../gateway-service/gateway-service-checklist.md) đã cập nhật.
- Bằng chứng: `mvn test` của từng service và một lượt end-to-end qua `http://localhost:8081/api/v1` trong Docker.

## Bối cảnh

- Code identity viết theo baseline cũ: User đa vai trò, ban có scope theo has-paid-order, tên shop ở
  `sellers`, địa chỉ soft-delete/tối đa 5, địa giới gọi API ngoài, refresh token trong JSON
  (GAP-IDN-001…004).
- Gateway đang là servlet/WebMVC, xong bước 0–3 của checklist (commit `50f6166`), 173 test pass.
- [Quyết định 0001](../../decisions/0001-refresh-cookie-va-origin.md) (cookie/Origin),
  [0002](../../decisions/0002-gioi-han-tan-suat-gateway.md) (rate limit/body), coverage OQ-003/004/010/011.

## Phạm vi

Trong phạm vi:

- Gateway: G1 chuyển WebFlux giữ nguyên hành vi; G2 routing/Product/PUBLIC; G3 header và X-Request-Id;
  G4 envelope; G5 CORS; G6 rate limit; G7 log/Docker; G8 end-to-end.
- Identity: I1 accountType/RBAC; I2 Seller trên User + SellerApplication; I3 địa chỉ + địa giới tĩnh;
  I4 vòng đời cấm/J7/nghĩa vụ; I5 refresh cookie; I6 tìm kiếm P-37 + rate limit.

Ngoài phạm vi:

- Google login (Should), publisher Kafka, endpoint nghĩa vụ của Order/Finance, refactor package-by-layer,
  rate limit nhiều instance, Docker image release, đổi tên AuditAction (OQ-009), sửa code product/payment.

## Cách thực hiện

Thứ tự: G1 → G7, rồi I1 → I6, cuối cùng G8 và cập nhật tài liệu. Mỗi bước kết thúc với test xanh và đề
xuất một commit. Identity mỗi bước một migration mới (V6 trở đi), không sửa V1–V5. Test mã hóa hành vi
cũ được sửa theo đặc tả mới, không xóa để cho qua.

## Rủi ro và phục hồi

- WebFlux: giữ nguyên các test black-box làm lưới an toàn; lỗi Netty từ chối trước WebFlux (không có
  envelope) phải ghi nhận, không nới test.
- MDC trong reactive: kiểm chứng bằng test bắt log.
- Migration không đảo ngược trên DB dev dùng chung: backfill dừng ngay khi gặp User có cả hai hồ sơ;
  phục hồi bằng `docker compose -p ecommerce down -v`.
- Ban/J7 không chạy end-to-end được tới khi Order/Finance có endpoint nghĩa vụ: Docker trả 503 là đúng,
  hành vi chứng minh bằng fake.

## Tiến độ

- [x] Bước 0: gateway 173 test xanh, commit bước 3 (`50f6166`), quyết định 0001/0002, coverage OQ-003/004/010/011
- [x] G1 WebFlux (giữ hành vi) — `3fe409f`
- [x] G2 Routing/Product/PUBLIC/checklist — `3e7769f`
- [x] G3 Header và X-Request-Id — `8d5da0c`
- [x] G4 Envelope (429/413) — `51e15cc`
- [x] G5 CORS — `51e15cc`
- [x] G6 Rate limit — `51e15cc`
- [x] G7 Log/Docker — `5405244`; smoke test Docker 24/24 + TOKEN_STALE + identity dừng → 503
- [x] I1 accountType/RBAC — `469c19a`
- [x] I2 Seller trên User + SellerApplication — `6844666`
- [x] I3 Địa chỉ + địa giới tĩnh
- [x] I4 Vòng đời cấm/J7
- [x] I5 Refresh cookie
- [ ] I6 Tìm kiếm P-37 + rate limit
- [ ] G8 End-to-end + tài liệu

## Quyết định

- 2026-11-20: Gateway chuyển WebFlux theo ADR-02 (người dùng chọn).
- 2026-11-20: Identity gọi nghĩa vụ Order/Finance đúng contract API §7.1/§7.1.2, test dùng fake, không có
  stub dev trả "không có nghĩa vụ".
- 2026-11-20: Chưa làm publisher Kafka; sửa tên/payload sự kiện theo API §7.2, sự kiện nằm ở outbox.
- 2026-11-20: Gateway bỏ tiền tố `/api/v1` chỉ trên route Product (product-service phục vụ `/products`…);
  Identity giữ đường dẫn đầy đủ.
- 2026-11-20: Migration V6 dừng nếu có User mang cả Customer và Seller; dev reset DB bằng `down -v`.
- 2026-11-20: Gateway tiếp tục không gọi authorization-context (quyết định team 02/11; API §7.1 vẫn liệt kê
  Gateway là consumer). Thông báo lỗi giữ tiếng Anh.
- Còn phải đọc nguồn khi tới bước, mơ hồ thì hỏi: sự kiện khi ban chuyển thẳng DISABLED (API §7.2); định
  dạng reason P-21d (API §6.1); quyền upload #13 (bảng ghi `seller:profile:own`, chi tiết ghi
  `account:update:own`; theo chi tiết và ghi xung đột vào coverage).

## Kiểm chứng

- Từng bước: `cd gateway-service && mvn test`; `cd identity-service && mvn test` (cần Docker) và
  `mvn test -Dtest=OpenApiDocsTests`.
- End-to-end (`./up.sh`): register → login có accountType → me → refresh bằng cookie với
  `Origin: http://localhost:5173` → logout xóa cookie → token cũ bị từ chối; Origin sai bị từ chối;
  `X-User-Id` giả bị bỏ qua; `/internal/**` 404 từ ngoài; CORS preflight; 429 ở lần đăng nhập thứ 11
  trong một phút; body > 1 MB bị từ chối; một `X-Request-Id` thấy ở log gateway, log identity và body
  lỗi; `/api/v1/products` tới product-service; tỉnh/thành đọc từ DB; ban trả 503 khi chưa có Order/Finance.

## Kết quả

Chưa hoàn tất.
