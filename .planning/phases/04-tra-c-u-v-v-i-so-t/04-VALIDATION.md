---
phase: "04"
slug: "tra-c-u-v-v-i-so-t"
status: draft
nyquist_compliant: false
wave_0_complete: false
created: "2026-11-24"
---

# Phase 4 — Chiến lược kiểm thử

## Môi trường

| Biên | Môi trường |
| --- | --- |
| Transaction, khóa và ràng buộc | JUnit, Spring Boot, PostgreSQL thật và Flyway từ DB trắng. Tái dùng fixture của Phase 3. |
| HTTP liên service | Mock theo DTO/hợp đồng đã duyệt cho các ca permission, timeout và payload lỗi. Order/Payment/Finance thật ở plan 04-07. |
| Phát biên nhận | Kafka thật trong bài tích hợp, consumer kiểm thử dùng hợp đồng thông báo. Notification thật có bằng chứng riêng khi sẵn sàng. |
| Quyền Identity | Chạy ca mock đúng contract và kiểm thêm authorization-context thật. Ghi lại ca nguồn thật chưa đáp ứng. |

## Tần suất

Chạy lệnh gắn vào task sau mỗi thay đổi liên quan. Sau mỗi wave chạy các suite của nhánh bị ảnh hưởng. Sau Wave 4 chạy bài tích hợp và hồi quy trong 04-07. Đọc báo cáo Surefire mới để kiểm test chạy thật, không skip. Lưu command, profile, fixture, migration và kết quả theo plan. Chưa có số đo thời gian chạy Phase 4.

## Ma trận

| Task | Wave | Requirement | Threat | Bằng chứng | Lệnh |
| --- | --- | --- | --- | --- | --- |
| 04-01-01 | 1 | ORD-09, PAY-07, FIN-04, FIN-05 | T-04-01 | JSON passed=true và rà từng trường/endpoint so với D-01–D-08. Phân biệt fixture Identity với quyền thật hiện có. | `node .codex/gsd-core/bin/gsd-tools.cjs validate consistency` |
| 04-02-01 | 2 | ORD-09 | T-04-02 | POST không liên quan; HTTP GET thực trả đúng trang và GET chi tiết giữ quyền, sai chủ 404. Mô phỏng Payment lỗi và refund đúng Order. | `mvn -f order-service/pom.xml "-Dtest=OrderReadPostgresTests,OrderLifecyclePostgresTests" "-Dsurefire.failIfNoSpecifiedTests=true" "-DfailIfNoTests=true" test` |
| 04-02-02 | 2 | ORD-09 | T-04-02 | Hai Seller cùng checkout chỉ thấy đơn của mình. Kiểm quyền Admin từ chối sai vai trò/quyền, lọc và phân trang ổn định. | `mvn -f order-service/pom.xml "-Dtest=OrderReadPostgresTests,OrderLifecyclePostgresTests,OrderAuthorizationHttpTests" "-Dsurefire.failIfNoSpecifiedTests=true" "-DfailIfNoTests=true" test` |
| 04-03-01 | 2 | PAY-07 | T-04-03 | Đúng owner thấy Payment, hoặc data:null khi chưa có Payment; sai owner 404; checkout đóng không lộ link; FULL_CHECKOUT hiện đúng chỗ. | `mvn -f payment-service/pom.xml "-Dtest=PaymentReadPostgresTests,CustomerRefundRetryPostgresTests" "-Dsurefire.failIfNoSpecifiedTests=true" "-DfailIfNoTests=true" test` |
| 04-03-02 | 2 | PAY-07 | T-04-03 | Kiểm ngay trước và đúng ngưỡng, link hợp lệ, checkout đóng, attempt cũ, có/thiếu bằng chứng webhook, review và paginator. Admin chỉ đọc Refund. | `mvn -f payment-service/pom.xml "-Dtest=PaymentReadPostgresTests,PaymentCollectionPostgresTests,FullCheckoutRefundPostgresTests" "-Dsurefire.failIfNoSpecifiedTests=true" "-DfailIfNoTests=true" test` |
| 04-04-01 | 2 | FIN-04 | T-04-04 | 201 lần đầu, 200 cùng receipt khi replay, 409 khác amount. BANNED đủ quyền rút toàn bộ 9.500đ, ACTIVE hoặc rút một phần dưới ngưỡng bị từ chối. Transaction rollback toàn bộ. Một event nghiệp vụ, không trùng khi replay. | `mvn -f finance-service/pom.xml "-Dtest=WithdrawalPostgresTests,SettlementLifecyclePostgresTests" "-Dsurefire.failIfNoSpecifiedTests=true" "-DfailIfNoTests=true" test` |
| 04-04-02 | 2 | FIN-04 | T-04-04 | Đúng owner thấy dữ liệu, sai owner 404; phân quyền pending-only và available-only; chưa có ví, ví frozen và ví có nghĩa vụ đúng hợp đồng. | `mvn -f finance-service/pom.xml "-Dtest=FinanceReadPostgresTests,WithdrawalPostgresTests" "-Dsurefire.failIfNoSpecifiedTests=true" "-DfailIfNoTests=true" test` |
| 04-05-01 | 3 | PAY-07 | T-04-05 | Credential/user permission sai không ghi. Rollback audit lỗi giữ nguyên note/cờ. Không đọc hoặc thay Refund/job/chi tiết tiền. | `mvn -f order-service/pom.xml "-Dtest=CollectionReviewPostgresTests" "-Dsurefire.failIfNoSpecifiedTests=true" "-DfailIfNoTests=true" test` |
| 04-05-02 | 3 | PAY-07 | T-04-05 | Đúng payload response 200, kiểm mock Order rollback/error, note hiển thị, một dòng theo nghĩa vụ, filter đúng type và không lộ cờ cho actor thiếu quyền. | `mvn -f payment-service/pom.xml "-Dtest=PaymentAdminReviewHttpTests,PaymentReadPostgresTests" "-Dsurefire.failIfNoSpecifiedTests=true" "-DfailIfNoTests=true" test` |
| 04-06-01 | 3 | FIN-05 | T-04-06 | Một checkout nhiều đơn đếm một lần. Finance-only. | `mvn -f order-service/pom.xml "-Dtest=OrderReconciliationSourceTests" "-Dsurefire.failIfNoSpecifiedTests=true" "-DfailIfNoTests=true" test` |
| 04-06-02 | 3 | FIN-05 | T-04-06 | Tính đúng thu và chờ hoàn theo scope; Finance-only. | `mvn -f payment-service/pom.xml "-Dtest=PaymentTotalsPostgresTests" "-Dsurefire.failIfNoSpecifiedTests=true" "-DfailIfNoTests=true" test` |
| 04-06-03 | 3 | FIN-05 | T-04-06 | Kiểm nguồn 0 hợp lệ, timeout từng nguồn, invalid, ORDER chờ hoàn không cộng trùng, FULL_CHECKOUT, lệch giữa timestamp, ví lệch và độ lệch có dấu. GET không ghi DB/outbox. | `mvn -f finance-service/pom.xml "-Dtest=FinanceReconciliationPostgresTests,FinanceReadPostgresTests" "-Dsurefire.failIfNoSpecifiedTests=true" "-DfailIfNoTests=true" test` |
| 04-07-01 | 4 | ORD-09, PAY-07, FIN-04, FIN-05 | T-04-07 | Không skip test. Ghi timestamp đọc, response, DB cuối và eventId. Ghi Identity/provider mock nếu dùng, Notification broker consumer khác dịch vụ nhận thật. | `mvn -f order-service/pom.xml "-Dtest=Phase4LocalBrokerIT,Phase3LocalBrokerIT,Phase2LocalBrokerPreflightTests" "-Dsurefire.failIfNoSpecifiedTests=true" "-DfailIfNoTests=true" test` |
| 04-07-02 | 4 | ORD-09, PAY-07, FIN-04, FIN-05 | T-04-07 | Kết quả 4 yêu cầu và 3 success criteria gắn bằng chứng. Bảng biên thật/mock và blocker có điều kiện mở lại rõ ràng. Khi bài kiểm thử nguồn thật không khả dụng, fixture đã đạt được ghi riêng, còn ca thật để pending. | `mvn -f order-service/pom.xml "-Dtest=Phase4LocalBrokerIT" "-Dsurefire.failIfNoSpecifiedTests=true" "-DfailIfNoTests=true" test` |

## Chuẩn bị

- 04-01 xác minh bản test hiện hành, hợp đồng nguồn và quyền trước khi chạy các nhánh phụ thuộc.
- Tệp test mới do task cùng plan tạo trước khi gọi Maven. Dùng helper và setup broker sẵn có, tránh tạo framework mới.
- Kiểm tra DB trắng và mapping validate trước khi dùng bài tích hợp để nghiệm thu migration.
- Kết quả chạy theo module bên dưới. Nghiệm thu quyền thực tế qua Identity được ghi riêng.

## Kiểm tra trước Execute

- GSD xác nhận cấu trúc 7 plan, độ phủ 4/4 yêu cầu, 8/8 quyết định và 14/14 lệnh có điều kiện thất bại.
- Kiểm tra tĩnh bổ sung xác nhận dependency đi từ wave trước sang wave sau, không có tệp dự kiến chồng lấn trong cùng wave, và 13 lệnh Maven có POM cùng suite hiện có hoặc task tạo suite.
- Các kết quả trên là bằng chứng chất lượng kế hoạch. Test nghiệp vụ được chạy và ghi kết quả trong Execute.

## Kết quả Execute

| Biên kiểm thử | Kết quả | Bằng chứng |
| --- | --- | --- |
| Order, gồm checkout/replay, tra cứu, audit và nguồn đối soát | 35/35 | [Log Order](../../../order-service/target/phase4-final-r2.log) |
| Payment, gồm link, quyền đọc, cảnh báo, Refund và nguồn đối soát | 25/25 | [Log Payment](../../../payment-service/target/phase4-final-r2.log) |
| Finance, gồm cạnh tranh rút, replay, ledger, rollback, quyền đọc và đối soát | 14/14 | [Log Finance](../../../finance-service/target/phase4-final-r2.log) |
| Preflight, E2E Phase 4 và hồi quy tích hợp Phase 2/3 | 4/4 | [Log E2E](../../../order-service/target/phase4-e2e-final.log) |
| Identity triển khai thật | Đạt ca Admin có quyền, Admin chỉ đọc, Seller BANNED rút hết 9.500đ và đối chiếu PostgreSQL | [Bằng chứng](04-IDENTITY-EVIDENCE.md) |
| Cổng UI GSD và build bốn backend từ Git | Đạt trên `verify/phase4-20261124`, commit `e8904d1` | [Báo cáo xác minh](04-VERIFICATION.md) |

Ba bộ dùng Java 21, Maven 3.10.0, PostgreSQL 16, Kafka cho ca cần broker. Không có test bị skip trong ba lượt trên. Mỗi module có JAR build từ workspace. Logs nằm trong `target/`; chúng là hiện vật local của lượt chạy.
