# Quyết toán, ví, rút tiền và đối soát

Baseline: SAD v1.1 §7.1/D.1, được người dùng chấp nhận ngày 11/11/2026.
[Quy ước/thuật ngữ](README.md) · [Coverage và câu hỏi](coverage.md) · [Kỹ thuật/validation](../engineering.md).

Các điều khoản tài khoản bị cấm dưới đây theo [Thẻ 15](coverage.md#the-15) và nguồn FRD/API/Database hiệu lực: bảo toàn nghĩa vụ, không đóng băng available vì ban và ngoại lệ rút toàn bộ số dư nhỏ. Đây là đặc tả, không chứng minh runtime đã triển khai.

Hoa hồng và các quy ước P-14/P-15 theo [tham số đã chốt](parameters.md). Các giá trị này không mở rộng phạm vi Reporting đã hoãn theo ADR-05.

Đọc đúng UC dưới đây, quy tắc bổ sung và hàng AC liên quan; chỉ mở mục contract nguồn khi cần DTO/cột/endpoint chi tiết. Yêu cầu TO-BE không chứng minh code đã làm đúng.

[UC-D04](#uc-d04) · [UC-D05](#uc-d05) · [UC-E02](#uc-e02) · [UC-G04](#uc-g04)

## Phạm vi sửa và bằng chứng hiện trạng

Khảo sát tĩnh ngày 11/11/2026; mọi test nêu dưới đây **chưa chạy**.

| Phạm vi | Code owner hiện có | Test đã tìm thấy | Giới hạn |
|---|---|---|---|
| D04/D05/E02/G04 | [finance-service/src/main/java/com/example/finance_service/FinanceServiceApplication.java](../../finance-service/src/main/java/com/example/finance_service/FinanceServiceApplication.java) | [finance-service/src/test/java/com/example/finance_service/FinanceServiceApplicationTests.java](../../finance-service/src/test/java/com/example/finance_service/FinanceServiceApplicationTests.java) | Chỉ scaffold; chưa Wallet/Settlement/ledger/Withdrawal hoặc migration nghiệp vụ. |

Khi lập task, lần theo caller/consumer và artifacts trong cùng hàng, rồi xem dependency liên service dưới đây; không đổi các source gốc để che lệch implementation.

## Contract và dữ liệu cần tra cứu

- [Database §3.5, §8.2I/J, §8.3](database.md): field, required/conditional, unique/FK/CHECK, ownership và WD-01…11. Bảng nguồn là thiết kế, không khẳng định migration tồn tại.
- [API §6.6/§7.1–7.3](api_specs.md): Wallet/withdrawals, nghĩa vụ Finance cho Identity, events và J7; [FRD UC-D05/E03/G04](FRD.md) và [Thẻ 15](coverage.md#the-15) cho policy hiệu lực. [SAD §7.2](software-architecture.md): DELTA cần review ngoài phần đã được quyết định.
- Public paths bên dưới là phần sau `/api/v1`; internal dùng `/internal/v1`. Base URL mẫu trong nguồn không chứng minh cổng service hiện tại. Tên path là khóa contract ổn định; quyền ở bảng vẫn phải kết hợp trạng thái/ownership.
- **Bảng contract nguồn, chưa phải bằng chứng triển khai.** Endpoint Should vẫn giữ tham chiếu; luồng checkout cũ và stats/dashboard bị baseline hiệu chỉnh không được triển khai nguyên xi. DTO, body/error examples và required/optional giữ tại đúng mục nguồn, không suy ra từ tên endpoint.

[API §6.6 / danh sách endpoint](api_specs.md)

| \# | Method | Path | Mô tả | Quyền | UC |
| :---- | :---- | :---- | :---- | :---- | :---- |
| 1 | GET | /finance/seller/wallet | Ví của tôi | wallet:read:own | D05, D04 |
| 2 | GET | /finance/seller/wallet/transactions | Lịch sử biến động ví | wallet:read:own | D05 |
| 3 | GET | /finance/seller/settlements | Tiền theo từng đơn | wallet:read:own | D04, D05 |
| 4 | POST | /finance/seller/withdrawals | Rút tiền (giả lập, tức thời) | withdrawal:create:own | D05 |
| 5 | GET | /finance/seller/withdrawals | Lịch sử rút tiền | wallet:read:own | D05 |
| 6 | GET | /finance/seller/withdrawals/{withdrawalId} | Chi tiết giao dịch rút | wallet:read:own | D05 |
| 7 | GET | /finance/admin/summary | Các ngăn tiền toàn hệ thống | operations:read:any | E02 |
| 8 | GET | /finance/admin/reconciliation | Kiểm tra bảo toàn tiền | operations:read:any | E02 |
| 9 | GET | /finance/admin/wallets | Tra cứu ví (chỉ xem) | operations:read:any | E08, E01 |
| 10 | GET | /finance/admin/wallets/{walletId}/transactions | Bút toán của một ví | operations:read:any | E08 |
| 11 | GET | /finance/admin/withdrawals | Giao dịch rút (chỉ xem) | operations:read:any | E08 |
| 12 | GET | /finance/admin/settlements | Quyết toán theo đơn | operations:read:any | E08 |

## Quy tắc bổ sung và source hiệu lực

| Mã nguồn | Quy tắc đã xác nhận | Nguồn |
|---|---|---|
| <a id="g-17"></a>G-17 | Seller chỉ được rút từ số dư khả dụng; số dư chờ quyết toán không được rút, ứng trước hay dùng để bù. Mọi số dư ví ≥ 0 tại mọi thời điểm. Refund không bao giờ trừ số dư khả dụng hay số đã rút của Seller. Chi tiết ở 8.3. | [§8.1](../../business/database.md), L969 |

Quy tắc chung G-09/G-11 ở [engineering](../engineering.md); G-10/G-13/G-15 ở [quy ước chung](README.md).

| Mã | Điều khoản | Nguồn / phân loại |
|---|---|---|
| <a id="req-fin-001"></a>REQ-FIN-001 | G=Order.total_amount_vnd; P-COM-01 là hoa hồng chung 5%=500 bps cho mọi Category và Seller, chụp vào commission_bps_snapshot của từng Order khi checkout. Finance dùng r đã chụp, không tra tỷ lệ từ Category/Seller hiện tại và không tính lại đơn cũ. C=floor((G×r+5.000)/10.000), S=G−C; đơn checkout theo quyết định này dùng r=500. Làm tròn nửa lên một lần trên tổng đơn bằng số nguyên, kiểm tra tràn; r của snapshot lịch sử vẫn trong 0…10.000. | [P-COM-01](parameters.md); [§8.3; SAD §6.6](../../business/database.md), L1011–1013; [§6.6](software-architecture.md), L665–681 |
| <a id="req-fin-002"></a>REQ-FIN-002 | Finance tạo pending từ checkout.paid của Order, không từ payment.succeeded. Một Settlement/Order, một Wallet VND/Seller và một PLATFORM Wallet. Seller.approved tạo ví idempotent; sự kiện đến trước ví phải chờ/xác minh đáng tin cậy, không bỏ mất tiền. | Yêu cầu baseline; [§6.6](software-architecture.md), L665–681 |
| <a id="req-fin-003"></a>REQ-FIN-003 | Tiền chỉ vào available khi CONFIRMED; hủy hợp lệ sau thu chỉ đổi PENDING_REFUND, chưa trừ pending. Refund SUCCESS mới REFUND_DEBIT đúng Order. Ban sau thu không tự hủy/hoàn/đóng băng available: Seller BANNED vẫn xử lý đơn tồn/quyết toán/rút available theo allowlist và WD-03/09, không đợi unban. Settlement PENDING/PENDING_REFUND/hiệu ứng chưa apply kể cả 0, pending>0 hoặc available>0 giữ nghĩa vụ với Identity; nguồn lỗi không được báo hết nghĩa vụ. | [FRD UC-D05/E03/G04](FRD.md); [Database §8.2B/§8.3](database.md); [API §3.2/§7.1](api_specs.md); [Thẻ 15](coverage.md#the-15) |
| <a id="req-fin-004"></a>REQ-FIN-004 | Đối soát qua HTTP không có snapshot nguyên tử xuyên DB; event lag có thể lệch tạm. Thiếu nguồn không được balanced=true; không tự thêm bút toán sửa lệch. Các field trạng thái/mốc đối soát chi tiết trong SAD §6.6 là đề xuất cần review. | Yêu cầu baseline; [§6.6](software-architecture.md), L683–685 |
| <a id="req-fin-005"></a>REQ-FIN-005 | P-14 dùng GMT+7, tuần bắt đầu thứ Hai, quý theo lịch. P-15 xác định bán chạy theo số lượng bán, không theo doanh thu; sản phẩm phải trên 500 lượt bán, đúng 500 chưa đạt. Không tự quy định kỳ đo hoặc trạng thái đơn được tính khi nguồn chưa chốt. Các giá trị này áp dụng khi triển khai phần phân tích tương ứng; Reporting, biểu đồ, top sản phẩm và export vẫn hoãn theo ADR-05, không là DoD Must. | [P-14/P-15](parameters.md); [ADR-05](software-architecture.md) |

### WD-01…WD-11

| Mã nguồn | Quy tắc | Nguồn |
|---|---|---|
| <a id="wd-01"></a>WD-01 | Cần accountType SELLER, role SELLER hiện hành, APPROVED, quyền withdrawal:create:own và ví owner_id=seller_id=sub. accountStatus ACTIVE hoặc BANNED với allowlist rút hợp lệ được rút available; DISABLED/PENDING/REJECTED/sai quyền không rút. Nghĩa vụ muộn phải qua đánh giá G-14 mở BANNED giới hạn, không tự unban. | [Database §8.3 WD-01](database.md); [API §3.2/§6.6](api_specs.md) |
| <a id="wd-02"></a>WD-02 | Chỉ rút số dư khả dụng; số dư chờ không được rút, không ứng trước, không cộng vào hạn mức rút. Tiền chỉ vào khả dụng khi Order CONFIRMED. | [§8.3](../../business/database.md), L1031 |
| <a id="wd-03"></a>WD-03 | A nguyên VND dương, A≤available; hợp lệ nếu `A >= 10000` hoặc `(accountStatus == BANNED && 0 < availableBalanceVnd < 10000 && A == availableBalanceVnd)`. Giữ P-WD-01/minWithdrawalVnd=10000 cho ACTIVE; không rút một phần dưới ngưỡng (422 WITHDRAWAL_BELOW_MINIMUM), không rút pending, không giới hạn lần/ngày. Kiểm tra lại quyền/số dư trong luồng giao dịch hiện hành. | [Database §8.3 WD-03](database.md); [FRD UC-D05](FRD.md); [API §6.6](api_specs.md) |
| <a id="wd-04"></a>WD-04 | Tức thời và nguyên tử: trong một transaction, cập nhật có điều kiện available ≥ A; available −A, total_withdrawn \+A, tạo Withdrawal và WalletTransaction. Lỗi giữa chừng thì rollback toàn bộ; các lần rút đồng thời được tuần tự hóa. | [§8.3](../../business/database.md), L1033 |
| <a id="wd-05"></a>WD-05 | Chống lặp: cùng idempotency_key chỉ trừ ví một lần (G-09). | [§8.3](../../business/database.md), L1034 |
| <a id="wd-06"></a>WD-06 | Hoàn tiền không chạm số dư khả dụng hay số đã rút: Refund luôn lấy từ phần chờ của đúng Order bị hủy. Mọi số dư ví ≥ 0; không phát sinh công nợ Seller với nền tảng trong MVP. | [§8.3](../../business/database.md), L1035 |
| <a id="wd-07"></a>WD-07 | MVP không cho hủy/hoàn Order đã DELIVERED hoặc CONFIRMED. Nếu sau MVP bổ sung đổi trả sau nhận hàng thì phải thêm thời gian tạm giữ sau xác nhận và cơ chế xử lý số dư âm. | [§8.3](../../business/database.md), L1036 |
| <a id="wd-08"></a>WD-08 | Admin không duyệt, không hủy, không đảo giao dịch rút; Admin chỉ xem ví và lịch sử rút (UC-E08). | [§8.3](../../business/database.md), L1037 |
| <a id="wd-09"></a>WD-09 | Ban không đóng băng available hoặc xóa số dư; BANNED rút available theo WD-01/03, kể cả ngoại lệ toàn bộ dưới ngưỡng, không rút pending. Chỉ pending/hiệu ứng chưa áp dụng → OBLIGATIONS_READ_ONLY; chỉ available → WITHDRAWAL_ONLY, có đơn+available → ORDERS_AND_WITHDRAWAL. Hết mọi nghĩa vụ được xác minh thì Identity/J7 chuyển DISABLED; nguồn lỗi giữ trạng thái, nghĩa vụ muộn mở BANNED giới hạn và giữ mốc đợt cấm. | [Database §8.3 WD-09](database.md); [FRD UC-E03](FRD.md); [API §3.2/§7.3](api_specs.md) |
| <a id="wd-10"></a>WD-10 | Ví nền tảng: hoa hồng đã chốt chỉ ghi sổ để báo cáo; MVP không có chức năng rút tiền của nền tảng. | [§8.3](../../business/database.md), L1039 |
| <a id="wd-11"></a>WD-11 | Với mỗi Seller: Σ seller_amount của Settlement SETTLED = available \+ total_withdrawn; Σ seller_amount của Settlement PENDING và PENDING_REFUND = pending; total_withdrawn = Σ Withdrawal.amount_vnd. | [§8.3](../../business/database.md), L1040 |

### Công thức bảo toàn và thời điểm ghi ví

[Database §8.3](database.md); Finance áp dụng Payment SUCCESS thông qua `checkout.paid` do Order phát, không trực tiếp từ `payment.succeeded` (REQ-FIN-002).

Ví dụ P-COM-01 với r=500, tính trên tổng đơn, không làm tròn từng dòng: G=100.000 VND → C=5.000, S=95.000; G=9 → C=0, S=9; G=10 → C=1, S=9 (0,5 làm tròn lên). Thay đổi Category/Seller sau checkout không thay các kết quả từ snapshot.

| SỰ KIỆN | SETTLEMENT | VÍ SELLER | VÍ NỀN TẢNG |
| :---- | :---- | :---- | :---- |
| checkout.paid sau Payment SUCCESS | Tạo PENDING | pending \+Sᵢ (PAYMENT_RECEIVED) | pending \+Cᵢ |
| Order CONFIRMED | → SETTLED, chốt Cᵢ, Sᵢ | pending −Sᵢ, available \+Sᵢ (SETTLEMENT_RELEASED) | pending −Cᵢ, available (hoa hồng đã chốt) \+Cᵢ |
| Order CANCELLED sau thanh toán | → PENDING_REFUND; tạo Refund Gᵢ | Chưa đổi số dư; phần Sᵢ được khóa, không thể chuyển sang khả dụng | Chưa đổi số dư |
| Refund SUCCESS của Order đó | → REFUNDED | pending −Sᵢ (REFUND_DEBIT) | pending −Cᵢ |
| Seller rút A | — | available −A, total_withdrawn \+A (WITHDRAWAL_COMPLETED) | — |
| Thanh toán muộn khi phiên đã CLOSED | Không tạo Settlement | Không đổi | Không đổi; Refund FULL_CHECKOUT = Payment.amount_vnd |


Tại cùng thời điểm: `Payment SUCCESS = Refund SUCCESS + FULL_CHECKOUT chưa thành công + pending mọi ví + hoa hồng đã chốt + available Seller + total_withdrawn Seller`. Không cộng lại PLATFORM available nếu đã cộng hoa hồng đã chốt; không trộn số dư thời điểm với phát sinh kỳ. WD-11 kiểm tra riêng mỗi Seller. Nguồn: Database §8.3 và SAD §6.6.

<a id="finance-identity-obligations"></a>
### Nghĩa vụ Finance và quyền Identity

- Contract bổ sung đặc tả: `GET /internal/v1/finance/sellers/{sellerId}/obligations`, consumer duy nhất Identity → `{hasPendingFinance,pendingBalanceVnd,availableBalanceVnd,evaluatedAt,financeStateVersion}`. Finance kiểm tra Settlement PENDING/PENDING_REFUND và hiệu ứng chưa apply kể cả 0, không chỉ wallet. Không khẳng định endpoint implementation có sẵn; 404 ví đáng lẽ tồn tại/timeout/không nhất quán không được báo nghĩa vụ 0.
- Identity kết hợp nghĩa vụ Order/Payment/Refund qua Order, không join DB. Ban đọc lại nghĩa vụ trước commit; thiếu nguồn →503 không ban một phần/không cấp quyền rộng hơn. J7 định kỳ và login/refresh đánh giá cả BANNED/DISABLED; hết nghĩa vụ xác minh →DISABLED, đến muộn →BANNED giới hạn, không chuyển ACTIVE/tự unban. Giữ disabledAt/restoreUntil cùng đợt cấm; HTTP ngoài transaction giữ khóa, kiểm tra version/đợt cấm trước commit để không ghi đè Admin unban. Không cam kết snapshot nguyên tử/SLA/chu kỳ mới.
- Finance dùng authorization-context Identity hiện hành, permission+allowlist+ownership/state, không suy quyền rút từ sellable=false hoặc snapshot token cũ. BANNED chỉ dữ liệu đơn/tiền/biên nhận cần giải quyết nghĩa vụ, không toàn bộ thống kê/lịch sử. Khi chỉ pending cho xem tiến độ, không rút pending; available vẫn rút được. Consumer seller.banned/user.disabled không đóng băng tiền/tự hủy đơn; user.enabled mang accountStatus BANNED vì nghĩa vụ muộn không là unban. Payment/Refund/quyết toán tiếp tục, hiệu ứng trễ giữ bền vững.
- Wallet giữ `minWithdrawalVnd=10000`, thêm `allowBelowMinimumFullWithdrawal:boolean`. Khi BANNED và 0<available<10000: `canWithdraw=true`, `withdrawDisabledReason=null`, UI khóa amount ở toàn bộ available; không hạ ngưỡng chung hoặc tạo trạng thái Withdrawal. Kiểm tra quyền/số dư lại khi giao dịch, atomic debit/ledger/Withdrawal và idempotency giữ nguyên.

Nguồn: [API §3.2/§6.6/§7.1–7.3](api_specs.md), [Database §3.5/§8.3](database.md), [FRD UC-D05/E03/G03](FRD.md), [Thẻ 15](coverage.md#the-15); [identity](identity.md#nguon-nghia-vu-va-j7).

## Acceptance criteria để triển khai/kiểm chứng

Các AC sau là diễn giải kiểm chứng từ điều khoản cùng mã UC/REQ/G/WD; chưa là kết quả test. Tham số TBD chỉ kiểm tra biên sau khi giá trị được chốt. Mỗi dòng gộp một tình huống; triển khai test phải tách assertions và cả nhánh lỗi của UC.

| Mã AC | Liên kết yêu cầu | Given | When | Then |
|---|---|---|---|---|
| <a id="ac-fin-001"></a>AC-FIN-001 | UC-D04/E02; ADR-05 | Dữ liệu vận hành từ service sở hữu | Seller/Admin tra cứu đơn/tiền và đối soát | Seller chỉ thấy mình; không đếm Payment lặp theo N Order; thiếu nguồn ghi chưa đủ dữ liệu, không balanced=true; dashboard phân tích không chặn Must. |
| <a id="ac-fin-002"></a>AC-FIN-002 | UC-D05; WD-01…11; B5 | ACTIVE/BANNED APPROVED, available=9.500 hoặc ≥10.000; hai tab | BANNED rút 9.500/5.000; ACTIVE rút 9.500; gửi lẻ/>available, cùng/khác key | BANNED rút toàn bộ 9.500 hợp lệ; rút 5.000 hoặc ACTIVE rút 9.500 →422 WITHDRAWAL_BELOW_MINIMUM. Wallet giữ minWithdrawalVnd=10000, ngoại lệ allowBelowMinimumFullWithdrawal=true/canWithdraw=true/withdrawDisabledReason=null, UI khóa amount=toàn bộ available. Một atomic debit+Withdrawal+ledger, không âm; cùng key/payload trả giao dịch cũ, khác payload 409; lỗi rollback, không giới hạn lần/ngày. |
| <a id="ac-fin-003"></a>AC-FIN-003 | UC-G04; REQ-FIN-001/002/003; B4 | Order CONFIRMED, commission snapshot, Seller BANNED; Settlement/hiệu ứng pending kể cả S=0 | Replay/sai thứ tự order.confirmed/checkout.paid; hỏi nghĩa vụ | Một Settlement/Order và release; pending→available đúng rounding, chờ phụ thuộc bền vững không mất effect. Ban không đóng băng/chặn rút available; chưa apply hoặc PENDING/PENDING_REFUND dù 0 vẫn hasPendingFinance=true/OBLIGATIONS_READ_ONLY khi không có thao tác đơn/rút. S=0 không tạo bút toán Seller bằng 0; pending không được rút. |
| <a id="ac-fin-004"></a>AC-FIN-004 | REQ-FIN-003; WD-09; B6/B9/B10 | Seller rút hết, còn/không còn hiệu ứng tài chính; Finance timeout hoặc DISABLED có available muộn | Identity/J7 hỏi nghĩa vụ, job chạy lại hoặc đồng thời unban | Chỉ mọi nguồn xác minh hết mới DISABLED một lần, không reset deadline/phát duplicate/đổi ledger. Lỗi/404 ví đáng lẽ tồn tại không là zero; giữ trạng thái/retry, request cần xác minh 503, job không ghi đè Admin unban. Available muộn →BANNED/WITHDRAWAL_ONLY không unban, giữ mốc cùng đợt, xử lý hết quay DISABLED không gia hạn. |
| <a id="ac-fin-005"></a>AC-FIN-005 | REQ-FIN-001; UC-G04 | Các Order của nhiều Category/Seller checkout theo P-COM-01, tổng G=100.000/9/10 VND | Tạo snapshot, đổi thông tin Category/Seller rồi quyết toán/replay | Mỗi Order snapshot 500 bps; C/S tương ứng 5.000/95.000, 0/9, 1/9; tính một lần trên tổng đơn, không lấy tỷ lệ hiện hành hay tính lại đơn cũ; mỗi hiệu ứng ghi sổ một lần. |

### AT nguồn thuộc hồ sơ này

Các AT bao phủ nhiều service; hồ sơ này là đầu mối đọc, không có nghĩa chỉ sửa một module. Đọc owner khác từ coverage khi task chạm biên. **Chưa có bằng chứng chạy AT.**

| Mã | Tình huống | Bằng chứng cần có | Nguồn |
|---|---|---|---|
| <a id="at-14"></a>AT-14 | Refund/confirmed đến Finance trước checkout.paid | WAITING_DEPENDENCY bền vững; tiếp tục đủ bút toán sau, không số dư âm | [B.2](software-architecture.md), L995 |
| <a id="at-15"></a>AT-15 | Hai Order cùng Payment, một hủy một xác nhận | Refund riêng đơn hủy; đơn kia SETTLED; không đổi tổng gốc hoặc hoàn chồng | [B.2](software-architecture.md), L996 |
| <a id="at-17"></a>AT-17 | Hai tab rút toàn bộ; replay cùng key | Chỉ một trừ ví; key lặp trả cùng Withdrawal; không âm | [B.2](software-architecture.md), L998 |
| <a id="at-23"></a>AT-23 | Bảo toàn tiền sau các luồng hỗn hợp | Ledger/Wallet/Settlement/Payment/Refund/Withdrawal khớp khi đã hội tụ | [B.2](software-architecture.md), L1004 |

## Chênh lệch, đề xuất và câu hỏi còn mở

- ADR-05 đã giới hạn D04/E02 vào tra cứu vận hành/đối soát; top sản phẩm, biểu đồ, export và Reporting hoãn. Không cần user quyết định lại phần đã được baseline supersede.
- P-COM-01 đã chốt 5%=500 bps dùng chung tại [parameters.md](parameters.md); không còn chọn tỷ lệ riêng theo Category/Seller. Field COMPLETE/INCOMPLETE/DIFFERENCE và mốc đối soát chi tiết vẫn là đề xuất, cần contract trước code.
- FRD G04 nói cùng transaction với Order; baseline yêu cầu transaction Finance riêng nhận order.confirmed. Đã hiệu chỉnh clause tương ứng; không sửa file FRD.
- E08 do payments.md sở hữu; Finance chỉ cung cấp dữ liệu đối soát, Admin không sửa tay ví/bút toán hay rút hộ.

## Đặc tả UC và ngoại lệ


<a id="uc-d04"></a>
## UC-D04 — Xem kết quả kinh doanh và khoản được nhận

Nguồn: [§6 / UC-D04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L973–1000. Vai trò: Người bán · Ưu tiên: M.

Các điều khoản dưới đây là yêu cầu nguồn đã đối chiếu baseline; nhánh Google/in-app ghi Should không thuộc DoD Must. Mã clause giữ ổn định khi di chuyển tài liệu.

| Mã điều khoản | Loại | Yêu cầu / điều kiện / ngoại lệ | Nguồn hiệu lực |
|---|---|---|---|
| <a id="uc-d04.goal-01"></a>UC-D04.GOAL-01 | Mục tiêu | Người bán theo dõi kết quả bán hàng và tình trạng tiền theo các đơn của mình. | [UC-D04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L977 |
| <a id="uc-d04.pre-01"></a>UC-D04.PRE-01 | Điều kiện | Seller APPROVED có phiên/quyền hiện hành. BANNED chỉ đơn tồn/thông tin tiền cần giải quyết nghĩa vụ của mình, không thống kê chung/toàn bộ lịch sử; chỉ pending đọc tiến độ, chỉ available xem ví/rút/biên nhận liên quan. | [FRD UC-D04/E03](FRD.md); [API §3.2](api_specs.md) |
| <a id="uc-d04.main-01"></a>UC-D04.MAIN-01 | Luồng chính | Seller mở tra cứu vận hành đơn, Settlement và ví thuộc mình; không bắt buộc màn thống kê phân tích hoặc chọn kỳ phân tích trong MVP. | [UC-D04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L983; **baseline thay thế/hiệu chỉnh:** [§1.2, ADR-05](software-architecture.md), L75–81 |
| <a id="uc-d04.main-02"></a>UC-D04.MAIN-02 | Luồng chính | MVP tra cứu danh sách/chi tiết đơn và khoản tiền tại service sở hữu; hoãn biểu đồ, top sản phẩm, export phân tích và Reporting Service. | [UC-D04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L985; **baseline thay thế/hiệu chỉnh:** [§1.2, ADR-05](software-architecture.md), L75–81 |
| <a id="uc-d04.main-03"></a>UC-D04.MAIN-03 | Luồng chính | Hệ thống hiển thị theo từng đơn: tổng tiền đơn, hoa hồng, khoản người bán được nhận và tình trạng tiền (đang chờ quyết toán / đã vào số dư khả dụng / đã hoàn khách). | [UC-D04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L987 |
| <a id="uc-d04.main-04"></a>UC-D04.MAIN-04 | Luồng chính | Hệ thống hiển thị tóm tắt ví (số dư chờ, khả dụng, đã rút) và liên kết tới “Ví & rút tiền” (UC-D05). | [UC-D04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L989 |
| <a id="uc-d04.alt-01"></a>UC-D04.ALT-01 | Ngoại lệ | Một lần thanh toán chung bao gồm đơn của nhiều Seller: chỉ tính Order.tổng_tiền của Seller đang xem, không tính toàn bộ Payment như doanh số của Seller. | [UC-D04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L993 |
| <a id="uc-d04.alt-02"></a>UC-D04.ALT-02 | Ngoại lệ | Đơn bị hủy: không tính doanh số hoàn thành/hoa hồng, hiển thị tiền đã hoặc đang hoàn khách; không làm giảm số dư khả dụng. | [UC-D04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L995 |
| <a id="uc-d04.alt-03"></a>UC-D04.ALT-03 | Ngoại lệ | Không có giao dịch: danh sách trống và số dư thực tế; không dựng read model kỳ thống kê chỉ để đáp ứng phần phân tích đã hoãn. | [UC-D04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L997; **baseline thay thế/hiệu chỉnh:** [§1.2, ADR-05](software-architecture.md), L75–81 |
| <a id="uc-d04.post-01"></a>UC-D04.POST-01 | Hậu điều kiện | Σ khoản người bán được nhận của các đơn đã quyết toán = số dư khả dụng \+ đã rút (WD-11). P-14 dùng GMT+7, tuần thứ Hai, quý theo lịch; P-15 bán chạy theo số lượng trên 500, không theo doanh thu, đúng 500 chưa đạt. Các quy ước này không làm phần top sản phẩm/Reporting đã hoãn thành Must; kỳ đo và trạng thái đơn được tính chưa được nguồn chốt. | [UC-D04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L999; [P-14/P-15](parameters.md); [ADR-05](software-architecture.md) |

<a id="uc-d05"></a>
## UC-D05 — Ví người bán và rút tiền (giả lập)

Nguồn hiệu lực: [FRD UC-D05](FRD.md), [Database §3.5/§8.3](database.md), [API §6.6](api_specs.md), [Thẻ 15](coverage.md#the-15). Vai trò: Seller · Ưu tiên: M.

Các điều khoản dưới đây là yêu cầu nguồn đã đối chiếu baseline; nhánh Google/in-app ghi Should không thuộc DoD Must. Mã clause giữ ổn định khi di chuyển tài liệu.

| Mã điều khoản | Loại | Yêu cầu / điều kiện / ngoại lệ | Nguồn hiệu lực |
|---|---|---|---|
| <a id="uc-d05.goal-01"></a>UC-D05.GOAL-01 | Mục tiêu | Người bán xem số dư ví giả lập và rút ngay phần tiền đã được quyết toán, trong khi tiền của các đơn còn có thể bị hủy/hoàn vẫn được nền tảng giữ lại. | [UC-D05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1005 |
| <a id="uc-d05.pre-01"></a>UC-D05.PRE-01 | Điều kiện | Seller APPROVED, accountStatus ACTIVE hoặc BANNED với quyền/allowlist/ownership hợp lệ. Rút theo WD-03; không yêu cầu đủ 10.000 để xem ví, không đợi unban để rút available. Phiên giới hạn chỉ dữ liệu gắn nghĩa vụ. | [FRD UC-D05/E03](FRD.md); [Database WD-01/03](database.md); [API §3.2/§6.6](api_specs.md) |
| <a id="uc-d05.main-01"></a>UC-D05.MAIN-01 | Luồng chính | Hiển thị ví gồm pending/available/đã rút và nhãn “Giả lập — không phát sinh tiền thật”, theo allowlist hiện hành. Pending gồm đơn chưa quyết toán và phần chờ hoàn, không thể rút; ban không đóng băng available. | [FRD UC-D05](FRD.md); [API §6.6](api_specs.md) |
| <a id="uc-d05.main-02"></a>UC-D05.MAIN-02 | Luồng chính | Hiển thị bút toán/đơn/quyết toán và mốc tự xác nhận nếu phù hợp; tách nhãn “Chờ hoàn cho khách”. BANNED chỉ dữ liệu/biên nhận liên quan nghĩa vụ, không mở toàn bộ lịch sử; pending-only chỉ xem tiến độ, không rút pending. | [FRD UC-D05/E03](FRD.md); [API §3.2/§6.6](api_specs.md) |
| <a id="uc-d05.main-03"></a>UC-D05.MAIN-03 | Luồng chính | Chọn Rút tiền, nhập A mặc định toàn bộ available. Nếu allowBelowMinimumFullWithdrawal=true thì khóa A=toàn bộ available; đích “Ví ngoài giả lập”, không nhập ngân hàng. | [FRD UC-D05](FRD.md); [API §6.6 Wallet](api_specs.md) |
| <a id="uc-d05.main-04"></a>UC-D05.MAIN-04 | Luồng chính | Kiểm tra lại quyền/available và WD-01…03: A nguyên dương≤available, A≥10000 hoặc BANNED với 0<available<10000 và A=available. ACTIVE giữ ngưỡng 10.000, không rút pending. | [FRD UC-D05](FRD.md); [Database §8.3](database.md); [API §6.6](api_specs.md) |
| <a id="uc-d05.main-05"></a>UC-D05.MAIN-05 | Luồng chính | Seller xác nhận; trong một transaction hệ thống trừ A khỏi số dư khả dụng, cộng A vào tổng đã rút, tạo Withdrawal và bút toán WITHDRAWAL_COMPLETED (WD-04). | [UC-D05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1019 |
| <a id="uc-d05.main-06"></a>UC-D05.MAIN-06 | Luồng chính | Hệ thống hiển thị ngay “Rút tiền thành công” với mã giao dịch, số tiền và số dư khả dụng còn lại, đồng thời gửi biên nhận (UC-G01). Không có bước chờ duyệt hay chờ xử lý. | [UC-D05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1021 |
| <a id="uc-d05.alt-01"></a>UC-D05.ALT-01 | Ngoại lệ | ACTIVE available<10.000, pending>0: nút rút vô hiệu, giải thích tiền khả dụng khi xác nhận/theo hạn tự xác nhận hiện hành. BANNED có available dương dưới ngưỡng vẫn được rút toàn bộ, dù còn pending; pending không cộng vào hạn mức. | [FRD UC-D05](FRD.md); [Database WD-02/03](database.md); [API §6.6](api_specs.md) |
| <a id="uc-d05.alt-02"></a>UC-D05.ALT-02 | Ngoại lệ | Available=0 không rút. ACTIVE available<10.000/pending=0 báo chưa đủ tối thiểu. BANNED 0<available<10.000: minWithdrawalVnd vẫn 10000, allowBelowMinimumFullWithdrawal=true, canWithdraw=true, withdrawDisabledReason=null, khóa amount=available. | [FRD UC-D05](FRD.md); [API §6.6 Wallet](api_specs.md) |
| <a id="uc-d05.alt-03"></a>UC-D05.ALT-03 | Ngoại lệ | A dưới 10.000 không thỏa ngoại lệ (kể cả rút một phần BANNED) →422 WITHDRAWAL_BELOW_MINIMUM. A>available hoặc không nguyên dương báo lỗi theo contract, không tạo Withdrawal/đổi số dư. | [FRD UC-D05](FRD.md); [Database WD-03](database.md); [API §6.6](api_specs.md) |
| <a id="uc-d05.alt-04"></a>UC-D05.ALT-04 | Ngoại lệ | Hai tab/hai thiết bị rút cùng lúc: các lần rút được xử lý lần lượt; lần nào làm tổng vượt số dư khả dụng thì bị từ chối với thông báo “Số dư không đủ”. | [UC-D05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1031 |
| <a id="uc-d05.alt-05"></a>UC-D05.ALT-05 | Ngoại lệ | Bấm “Xác nhận” lặp hoặc mạng gián đoạn: cùng idempotency_key trả lại đúng giao dịch đã thực hiện, không trừ ví lần hai; key cũ với số tiền khác → 409. | [UC-D05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1033 |
| <a id="uc-d05.alt-06"></a>UC-D05.ALT-06 | Ngoại lệ | Lỗi hệ thống giữa chừng: transaction rollback, số dư không đổi; Seller thử lại. | [UC-D05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1035 |
| <a id="uc-d05.alt-07"></a>UC-D05.ALT-07 | Ngoại lệ | Khách hủy một đơn khác của Seller ngay sau khi Seller rút hết: khi hoàn tiền thành công, hệ thống trừ số dư chờ của đúng đơn bị hủy; số dư khả dụng và tổng đã rút không đổi (WD-06). | [UC-D05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1037 |
| <a id="uc-d05.alt-08"></a>UC-D05.ALT-08 | Ngoại lệ | BANNED không đóng băng available: chỉ quyền xử lý nghĩa vụ/đơn tồn/ví/rút/biên nhận phù hợp accessMode; chỉ pending →OBLIGATIONS_READ_ONLY không rút pending. Hết mọi nghĩa vụ xác minh →Identity/J7 DISABLED; available đến muộn mở BANNED giới hạn, không tự unban/đặt lại deadline. | [FRD UC-D05/E03](FRD.md); [Database WD-09](database.md); [API §3.2/§7.3](api_specs.md) |
| <a id="uc-d05.alt-09"></a>UC-D05.ALT-09 | Ngoại lệ | Phát hiện lệch giữa số dư và tổng bút toán: hệ thống chặn rút và đánh dấu cần xử lý thủ công cho Admin. | [UC-D05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1041 |
| <a id="uc-d05.post-01"></a>UC-D05.POST-01 | Hậu điều kiện | Seller chỉ rút được tiền của đơn đã_xác_nhận; mỗi lần rút hoặc thành công ngay hoặc bị từ chối, không có trạng thái trung gian; tổng đã rút không vượt Σ khoản được nhận đã quyết toán; hoàn tiền khách không bao giờ làm ví Seller âm. | [UC-D05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1043 |
| <a id="uc-d05.note-01"></a>UC-D05.NOTE-01 | Ràng buộc | MVP rút theo số tiền (không theo từng đơn), không cần Admin duyệt, không hủy hay hoàn tác giao dịch rút đã thực hiện, không giới hạn số lần rút trong ngày. | [UC-D05](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1045 |

<a id="uc-e02"></a>
## UC-E02 — Báo cáo tổng quan

Nguồn: [§6 / UC-E02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1075–1104. Vai trò: Quản trị viên · Ưu tiên: M.

Các điều khoản dưới đây là yêu cầu nguồn đã đối chiếu baseline; nhánh Google/in-app ghi Should không thuộc DoD Must. Mã clause giữ ổn định khi di chuyển tài liệu.

| Mã điều khoản | Loại | Yêu cầu / điều kiện / ngoại lệ | Nguồn hiệu lực |
|---|---|---|---|
| <a id="uc-e02.goal-01"></a>UC-E02.GOAL-01 | Mục tiêu | Admin tra cứu vận hành và đối soát nguồn tiền chính thức; không bắt buộc dashboard phân tích theo kỳ trong MVP. | [UC-E02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1079; **baseline thay thế/hiệu chỉnh:** [§1.2, ADR-05](software-architecture.md), L75–81 |
| <a id="uc-e02.pre-01"></a>UC-E02.PRE-01 | Điều kiện | Quản trị viên đã đăng nhập. | [UC-E02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1081 |
| <a id="uc-e02.main-01"></a>UC-E02.MAIN-01 | Luồng chính | Admin mở tra cứu vận hành và đối soát; hoãn dashboard phân tích, biểu đồ và export. | [UC-E02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1085; **baseline thay thế/hiệu chỉnh:** [§1.2, ADR-05](software-architecture.md), L75–81 |
| <a id="uc-e02.main-02"></a>UC-E02.MAIN-02 | Luồng chính | Hệ thống hiển thị tổng tiền thu từ các lần thanh toán, số đã hoàn và đang chờ hoàn, tổng số dư chờ quyết toán, hoa hồng đã chốt (ví nền tảng), tổng số dư khả dụng của các Seller và tổng đã rút. | [UC-E02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1087 |
| <a id="uc-e02.main-03"></a>UC-E02.MAIN-03 | Luồng chính | Hệ thống hiển thị kết quả kiểm tra bảo toàn tiền (Database RBAC v5.0 mục 8.3) tại thời điểm xem; lệch thì cảnh báo. | [UC-E02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1089 |
| <a id="uc-e02.main-04"></a>UC-E02.MAIN-04 | Luồng chính | Quản trị viên xem tiến độ các đơn; có thể nhóm các đơn theo phiên thanh toán chung để đối chiếu. | [UC-E02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1091 |
| <a id="uc-e02.main-05"></a>UC-E02.MAIN-05 | Luồng chính | Hệ thống hiển thị hàng chờ xử lý: đơn có Seller quá 7 ngày chưa quyết định giao/từ chối, đơn khách quá 7 ngày chưa xác nhận, hoàn tiền lỗi và các phiên cần đối soát. | [UC-E02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1093 |
| <a id="uc-e02.alt-01"></a>UC-E02.ALT-01 | Ngoại lệ | Nguồn đủ dữ liệu và không có giao dịch thì phản ánh 0; thiếu nguồn không được coi là balanced=true hoặc không có giao dịch. | [UC-E02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1097; **baseline thay thế/hiệu chỉnh:** [§6.6](software-architecture.md), L665–685 |
| <a id="uc-e02.alt-02"></a>UC-E02.ALT-02 | Ngoại lệ | Một Payment chung không được đếm lặp cho mỗi đơn/Seller; số hoàn tính theo kết quả thực tế của từng đơn; số đã rút tính theo các giao dịch Withdrawal. | [UC-E02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1099 |
| <a id="uc-e02.alt-03"></a>UC-E02.ALT-03 | Ngoại lệ | Admin có thể tra cứu ví Seller bị cấm để theo dõi nghĩa vụ pending/available và tiến độ; không gắn nhãn “Đóng băng” chỉ vì ban, không sửa số dư/rút thay Seller. | [FRD UC-E02/E03/E08](FRD.md); [Database WD-08/09](database.md) |
| <a id="uc-e02.post-01"></a>UC-E02.POST-01 | Hậu điều kiện | Phản ánh được thu một lần, hoàn theo đơn, hoa hồng theo đơn, rút tiền theo từng giao dịch; số liệu khớp bất biến tiền ở Database RBAC v5.0 mục 8.3 và WD-11. | [UC-E02](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1103 |

<a id="uc-g04"></a>
## UC-G04 — Quyết toán đơn và ghi có ví (giả lập)

Nguồn: [§6 / UC-G04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1383–1414. Vai trò: Hệ thống · Ưu tiên: M · Đổi tên và phạm vi ở v5.2 (trước đây: Chốt hoa hồng và chi trả người bán).

Các điều khoản dưới đây là yêu cầu nguồn đã đối chiếu baseline; nhánh Google/in-app ghi Should không thuộc DoD Must. Mã clause giữ ổn định khi di chuyển tài liệu.

| Mã điều khoản | Loại | Yêu cầu / điều kiện / ngoại lệ | Nguồn hiệu lực |
|---|---|---|---|
| <a id="uc-g04.goal-01"></a>UC-G04.GOAL-01 | Mục tiêu | Chốt hoa hồng và khoản Seller được nhận khi một đơn được xác nhận nhận hàng, chuyển tiền của Seller từ số dư chờ sang số dư khả dụng. | [UC-G04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1387 |
| <a id="uc-g04.pre-01"></a>UC-G04.PRE-01 | Điều kiện | Order đã_xác_nhận bởi khách hoặc J3; Payment chung success và đơn không bị hủy/hoàn; Settlement đang chờ_quyết_toán. | [UC-G04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1389 |
| <a id="uc-g04.main-01"></a>UC-G04.MAIN-01 | Luồng chính | Khi đơn đạt “Đã xác nhận”, hệ thống tính hoa hồng và tiền người bán được hưởng trên tổng tiền của đơn theo snapshot checkout; các đơn checkout theo P-COM-01 chụp 5%=500 bps cho mọi Category/Seller. Finance không tra tỷ lệ hiện tại hoặc tính lại đơn cũ; làm tròn theo REQ-FIN-001. | [UC-G04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1393; [P-COM-01](parameters.md) |
| <a id="uc-g04.main-02"></a>UC-G04.MAIN-02 | Luồng chính | Order ghi CONFIRMED và Outbox trong local transaction; Finance xử lý order.confirmed, chuyển Settlement SETTLED, Seller pending −Sᵢ/available +Sᵢ, PLATFORM pending −Cᵢ/available +Cᵢ và SETTLEMENT_RELEASED trong transaction Finance. Không gộp transaction Order và Finance. | [UC-G04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1395; **baseline thay thế/hiệu chỉnh:** [§5.5, §6.6](software-architecture.md), L559–570; [§6.6](software-architecture.md), L665–685 |
| <a id="uc-g04.main-03"></a>UC-G04.MAIN-03 | Luồng chính | Hệ thống gửi Seller thông báo tiền đã khả dụng; không phát lệnh chi tự động. | [UC-G04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1397 |
| <a id="uc-g04.main-04"></a>UC-G04.MAIN-04 | Luồng chính | Seller có thể rút số dư khả dụng bất kỳ lúc nào theo UC-D05. | [UC-G04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1399 |
| <a id="uc-g04.alt-01"></a>UC-G04.ALT-01 | Ngoại lệ | Đơn bị hủy/đang hoàn: không chốt hoa hồng, không ghi có khả dụng. | [UC-G04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1403 |
| <a id="uc-g04.alt-02"></a>UC-G04.ALT-02 | Ngoại lệ | Seller BANNED vẫn quyết toán đơn hợp lệ khi CONFIRMED; available không đóng băng, được rút theo WD-01/03/09 không đợi unban. Pending/hiệu ứng chưa apply kể cả giá trị 0 giữ nghĩa vụ; khi available xuất hiện muộn Identity mở BANNED giới hạn theo G-14. | [FRD UC-G04/E03](FRD.md); [Database §8.3](database.md); [API §7.1](api_specs.md) |
| <a id="uc-g04.alt-03"></a>UC-G04.ALT-03 | Ngoại lệ | Sᵢ = 0: chốt Settlement, không ghi bút toán cho ví Seller. | [UC-G04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1407 |
| <a id="uc-g04.alt-04"></a>UC-G04.ALT-04 | Ngoại lệ | Cùng một lần thanh toán có đơn đã_xác_nhận và đơn đã_hủy: chỉ quyết toán đơn đã_xác_nhận, đơn hủy hoàn đúng số tiền. | [UC-G04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1409 |
| <a id="uc-g04.alt-05"></a>UC-G04.ALT-05 | Ngoại lệ | Sự kiện xác nhận bị xử lý lặp: khóa sự kiện (event_key) của bút toán chặn ghi có lần hai. | [UC-G04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1411 |
| <a id="uc-g04.post-01"></a>UC-G04.POST-01 | Hậu điều kiện | Hoa hồng ghi nhận một lần tại thời điểm xác nhận nhận hàng; Σ Sᵢ đã quyết toán của Seller = khả dụng \+ đã rút. | [UC-G04](../../business/UET%20FRD%20v5.4%20-%20dong%20bo%20API%20DB.md), L1413 |
