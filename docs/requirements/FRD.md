# [UET] Cloud Project — Functional Requirements Document (FRD)

Enterprise E-commerce Platform (MVP) — v5.5

Phiên bản: **v5.5 — ngày 16/11/2026** — cập nhật quyết định mới tại FRD Conflicts: tách tài khoản Customer/Seller/Administrator; Seller chờ duyệt/bị từ chối không đăng nhập; hồ sơ bị từ chối phải đăng ký mới; Customer bị cấm chỉ đăng nhập khi còn đơn chưa hoàn tất; Seller đã duyệt bị cấm chỉ xử lý đơn chưa hoàn tất và rút tiền khả dụng; chỉ cấm Seller đã duyệt; sai vai trò báo lỗi đăng nhập chung; dùng users.avatar\_object\_key làm ảnh đại diện và logo Seller.

**Bổ sung hiệu lực 17/11/2026 — [Thẻ 15](coverage.md#the-15):** UC-E03 thay policy cấm cũ; nghĩa vụ bao gồm Payment/Refund/Finance dù không còn Order mở. UC-D05 có ngoại lệ rút toàn bộ dưới ngưỡng. Đoạn giới thiệu v5.5 và nhật ký phiên bản là lịch sử, không phải authority trái bổ sung này.

Giữ các quyết định không thay đổi; quyết định \#15 đã chốt theo API/Database: Order và CheckoutSession sử dụng UUID, không có mã hiển thị/mã vận đơn, giữ withdrawalCode. FRD v5.5 đã được đồng bộ về mặt đặc tả với Database v5.1 và API v1.1; BRD/UI/Figma, OpenAPI triển khai và mã nguồn còn cần cập nhật.

- **v5.4 (06/11/2026)** từng chốt mô hình một User nhiều vai trò, nhưng đã được thay thế bằng tài khoản tách vai trò theo conflict số 6\.
- **v5.3 (19/10/2026):** xóa mục 4 (mô hình dữ liệu): toàn bộ thực thể, trường, quy tắc toàn cục, state machine và dòng tiền chuyển sang tài liệu Database v4.6 (thực thể ở mục 3, quy tắc toàn cục G-01…G-17 ở mục 8.1, state machine ở mục 8.2, dòng tiền và quy tắc ví WD-01…WD-11 ở mục 8.3, bộ giả lập thanh toán ở mục 8.4); mọi tham chiếu “4.x” trong FRD đổi sang mục tương ứng của Database; hoàn tiền theo đơn chỉ trừ số dư chờ khi Refund thành công (bỏ bút toán hủy\_chờ lúc hủy đơn); thanh toán/hoàn tiền dùng bộ giả lập thanh toán nội bộ triển khai theo hai giai đoạn.
- **v5.2 (18/10/2026):** bổ sung ví người bán giả lập và chức năng rút tiền tức thời (UC-D05), tách rõ dòng tiền chờ quyết toán (chưa được rút) và khả dụng (được rút); rút tiền là thao tác giả lập hoàn tất ngay, không cần duyệt, tối thiểu 10.000 VND/lần; bỏ chi trả tự động theo từng đơn và thực thể Payout; loại bỏ chức năng báo cáo vi phạm sản phẩm/Seller (UC-C02, UC-E05, thực thể Report).
- **v5.1 (16/10/2026):** bổ sung “Mua ngay” (UC-B10).
- **v5.0 (16/10/2026):** một lần checkout nhiều cửa hàng được tách thành nhiều Order, mỗi Order thuộc một cửa hàng; bỏ FulfillmentGroup.

Tài liệu nền: **FRD v4.1 (10/10/2026)**.

Database v5.1 và API v1.1 đã được chỉnh theo quyết định mới của FRD Conflicts và FRD v5.5. BRD, UI Screens, Figma, OpenAPI triển khai và mã nguồn chưa được xác nhận đã đồng bộ.

## Mục lục

- [Hướng dẫn đọc & tra cứu](#hướng-dẫn-đọc--tra-cứu)
  - [01 | Chọn đúng nơi cần đọc](#01--chọn-đúng-nơi-cần-đọc)
  - [02 | Từ điển định danh](#02--từ-điển-định-danh)
  - [03 | Tra cứu nhanh các nhóm use case](#03--tra-cứu-nhanh-các-nhóm-use-case)
  - [04 | Những điều phải nắm trong FRD 5.x](#04--những-điều-phải-nắm-trong-frd-5x)
- [Giải nghĩa công thức & sổ tiền (FRD v5.x)](#giải-nghĩa-công-thức--sổ-tiền-frd-v5x)
  - [01 | Bảng ký hiệu và đơn vị](#01--bảng-ký-hiệu-và-đơn-vị)
  - [02 | Công thức & ví dụ tính từng bước](#02--công-thức--ví-dụ-tính-từng-bước)
  - [03 | Các ngăn tiền trong sổ giả lập](#03--các-ngăn-tiền-trong-sổ-giả-lập)
  - [04 | Thẩm quyền tham số và những điểm còn mở](#04--thẩm-quyền-tham-số-và-những-điểm-còn-mở)
- [Ma trận truy vết tóm tắt](#ma-trận-truy-vết-tóm-tắt)
- [1. Giới thiệu](#1-giới-thiệu)
- [2. Actor](#2-actor)
- [3. Sơ đồ Use Case tổng quan](#3-sơ-đồ-use-case-tổng-quan)
- [4. Mô hình dữ liệu — ĐÃ CHUYỂN SANG DATABASE (v5.3; hiện hành: Database RBAC v5.0)](#4-mô-hình-dữ-liệu--đã-chuyển-sang-database-v53-hiện-hành-database-rbac-v50)
- [5. Ma trận quyền theo actor](#5-ma-trận-quyền-theo-actor)
- [6. Đặc tả chi tiết theo nhóm chức năng](#6-đặc-tả-chi-tiết-theo-nhóm-chức-năng)
  - [Nhóm A — Tài khoản & hồ sơ](#nhóm-a--tài-khoản--hồ-sơ)
  - [Nhóm B — Customer: mua sắm (đa-seller, tách đơn theo cửa hàng)](#nhóm-b--customer-mua-sắm-đa-seller-tách-đơn-theo-cửa-hàng)
  - [Nhóm C — Customer: sau mua hàng](#nhóm-c--customer-sau-mua-hàng)
  - [Nhóm D — Seller (phạm vi của chính mình)](#nhóm-d--seller-phạm-vi-của-chính-mình)
  - [Nhóm E — Administrator](#nhóm-e--administrator)
  - [Nhóm F — Tính năng nâng cao](#nhóm-f--tính-năng-nâng-cao)
  - [Nhóm G — Tác vụ hệ thống](#nhóm-g--tác-vụ-hệ-thống)
- [7. Truy vết](#7-truy-vết)
- [8. Nhật ký quyết định cập nhật v5.0 – v5.5](#8-nhật-ký-quyết-định-cập-nhật-v50--v55)
  - [Bảng ánh xạ thuật ngữ v4.1 → v5.x (dành cho backlog, API, DB)](#bảng-ánh-xạ-thuật-ngữ-v41--v5x-dành-cho-backlog-api-db)
- [9. Thông số bổ sung và kịch bản nghiệm thu](#9-thông-số-bổ-sung-và-kịch-bản-nghiệm-thu)

## Hướng dẫn đọc & tra cứu

**Mục đích:** định hướng đọc theo công việc; định danh gốc (UC-xx, P-xx, N-xx) được giữ để không làm đứt liên kết giữa yêu cầu – dữ liệu – kiểm thử; quy tắc dữ liệu tham chiếu theo mã của Database RBAC v5.0 (mục 3.x, G-xx, WD-xx). Mã đã loại bỏ (UC-C02, UC-E05, mục 4 cũ) được giữ chỗ, không tái sử dụng cho chức năng khác.

### 01 | Chọn đúng nơi cần đọc

| Tôi cần tìm | Đọc mục | Mã tham chiếu |
| :---- | :---- | :---- |
| Mô hình dữ liệu, quy tắc toàn cục, state machine | Database RBAC v5.0 (mục 3, 8.1, 8.2) | 3.1–3.8; G-01…G-17 |
| Dòng tiền giả lập, hoa hồng, quyết toán | Phần “Giải nghĩa công thức” \+ Database RBAC v5.0 mục 8.3 | 8.3 |
| Ví người bán, rút tiền | UC-D05 \+ Database RBAC v5.0 mục 3.5, 8.3 | WD-01…WD-11; UC-D05 |
| Ma trận phân quyền | Mục 5 | Mục 5 |
| Đặc tả chức năng/AC | Mục 6 | Nhóm A–G |
| Truy vết, thay đổi và kiểm thử | Mục 7–9 | Mục 7–9 |

### 02 | Từ điển định danh

| Ký hiệu | Cách hiểu |
| :---- | :---- |
| BRD / FRD | Yêu cầu nghiệp vụ / yêu cầu chức năng |
| UC-A01…UC-G04 | Mã use case cố định — dùng chung với backlog, API, test case |
| M / S / C | Must (bắt buộc) / Should (nên có) / Could (có thể cắt) |
| P-xx / N-xx | Tham số nghiệp vụ / yêu cầu phi chức năng — đối chiếu tài liệu riêng |
| J1, J2/J2b, J3–J7 | Mã tác vụ nền (job), xem UC-G03; J7 do Identity đối soát tài khoản bị cấm |
| Tài khoản và vai trò | Tài khoản được tách theo vai trò: Customer, Seller và Administrator phải sử dụng ba tài khoản độc lập (email/định danh đăng nhập khác nhau). Một người tham gia nhiều vai trò phải đăng ký hoặc được cấp từng tài khoản riêng; không gán nhiều vai trò nghiệp vụ cho cùng một tài khoản. Yêu cầu này thay thế mô hình một User nhiều vai trò của FRD v5.4 và cần đồng bộ lại Database/API. |
| Tên cửa hàng | Họ tên (full\_name) của tài khoản Seller được dùng làm tên cửa hàng; không có trường tên người bán riêng. Customer và Administrator dùng tài khoản khác. |
| CheckoutSession | Một lần đặt hàng và thanh toán chung (“phiên thanh toán”); gom N Order được tách theo cửa hàng; đơn và phiên được định danh bằng ID, không có mã hiển thị riêng |
| Order | Đơn hàng của đúng một Seller (cửa hàng); đơn vị xử lý giao, hủy, hoàn tiền, xác nhận nhận hàng, quyết toán |
| Ví (Wallet) | Sổ số dư giả lập của một Seller hoặc của nền tảng; chia ngăn chờ quyết toán / khả dụng / đã rút (Database 3.5.2) |
| Withdrawal | Giao dịch rút tiền giả lập của Seller; hoàn tất tức thời, không có trạng thái chờ (Database 3.5.4) |
| WalletTransaction | Bút toán ví append-only: PAYMENT\_RECEIVED (ghi chờ), SETTLEMENT\_RELEASED (quyết toán), REFUND\_DEBIT (trừ chờ khi hoàn thành công), WITHDRAWAL\_COMPLETED (rút tiền) (Database 3.5.3) |
| PK / FK / TTL / bps | Khóa chính / khóa ngoại / thời hạn giữ chỗ / điểm cơ bản (basis points) |
| MVP / MoSCoW | Sản phẩm khả dụng tối thiểu / cách phân loại mức ưu tiên Must–Should–Could |
| API / UI / DB | Giao diện lập trình / giao diện người dùng / cơ sở dữ liệu |
| Idempotency / HTTP 409 | Gửi lại yêu cầu không tạo hiệu ứng trùng / mã xung đột dữ liệu |
| Snapshot / giả lập | Ảnh chụp giá trị tại một thời điểm / xử lý tiền mô phỏng, không phát sinh tiền thật |
| Bộ giả lập thanh toán / callback / worker | Thành phần nội bộ của Payment Service thay cho cổng thanh toán, trả kết quả theo kịch bản cấu hình (Database 8.4) / phản hồi kết quả đến sau / tiến trình xử lý nền |

### 03 | Tra cứu nhanh các nhóm use case

| Nhóm | Nội dung nghiệp vụ | Use case |
| :---- | :---- | :---- |
| Nhóm A | Tài khoản, hồ sơ | A01–A07 |
| Nhóm B | Giỏ, mua ngay, checkout tách đơn, thanh toán, đơn hàng | B01–B10 |
| Nhóm C | Đánh giá sản phẩm | C01 (C02 đã loại bỏ) |
| Nhóm D | Seller: sản phẩm, tồn kho, xử lý đơn, thống kê, ví và rút tiền | D01–D05 |
| Nhóm E | Admin: kiểm soát và báo cáo tổng quan | E01–E04, E06–E08 (E05 đã loại bỏ) |
| Nhóm F | Gợi ý sản phẩm | F01 |
| Nhóm G | System: notification, refund, job, quyết toán và ghi có ví | G01–G04 |

**Mẹo tra cứu:** Word → View → Navigation Pane → Headings, hoặc dùng Ctrl+F tìm mã UC, P hay tên entity.

### 04 | Những điều phải nắm trong FRD 5.x

| Nguyên tắc | Cách hiểu ngắn |
| :---- | :---- |
| 1 lần checkout → N đơn | Một lần checkout tạo 1 CheckoutSession và N Order; N \= số cửa hàng khác nhau trong các sản phẩm được chọn. Mỗi Order thuộc đúng một Seller. |
| Một lần thanh toán chung | Khách bấm thanh toán một lần cho tổng tiền N đơn; 1 Payment gắn với CheckoutSession. Kết quả thanh toán áp dụng đồng thời cho cả N đơn. |
| Mỗi đơn xử lý riêng | Sau thanh toán, giao hàng, hủy, hoàn tiền, xác nhận nhận hàng và quyết toán độc lập theo từng Order. |
| Không còn FulfillmentGroup | Order là đơn vị xử lý duy nhất của Seller; OrderItem thuộc trực tiếp Order. Không còn trạng thái tổng hợp “hoàn tất một phần”. |
| Hai lối vào checkout | Từ giỏ hàng (UC-B03/B04) hoặc nút “Mua ngay” trên trang sản phẩm (UC-B10). Mua ngay tạo 1 đơn cho đúng 1 sản phẩm và không đọc/ghi giỏ hàng. |
| Tiền chờ ≠ tiền khả dụng (v5.2) | Khi khách thanh toán, phần của Seller vào **số dư chờ quyết toán** (không rút được). Chỉ khi đơn **đã\_xác\_nhận** — trạng thái kết thúc, không thể hủy/hoàn — tiền mới chuyển sang **số dư khả dụng** và Seller mới được rút. |
| Hoàn tiền không trừ ví khả dụng (v5.2) | Hoàn tiền khách luôn lấy từ phần nền tảng đang tạm giữ của đúng đơn bị hủy; không bao giờ trừ vào số dư khả dụng hoặc số tiền đã rút của Seller. Ví Seller không bao giờ âm. |
| Seller chủ động rút | Ngưỡng chung 10.000 VND; BANNED được rút toàn bộ available dương dưới ngưỡng theo UC-D05; giao dịch tức thời không duyệt. |
| Không còn báo cáo vi phạm (v5.2) | Customer không gửi báo cáo sản phẩm/Seller; Admin vẫn chủ động gỡ sản phẩm (UC-E04) và cấm tài khoản (UC-E03). |
| Số tiền đều giả lập | Không phát sinh tiền thật; không coi tổng thu là hoa hồng của nền tảng. Thanh toán và hoàn tiền do bộ giả lập thanh toán nội bộ xử lý; giai đoạn 1 chỉ kịch bản thành công, giai đoạn 2 bổ sung thất bại, chờ lâu, thành công muộn, hoàn lỗi (Database 8.4). |
| Hoàn tiền trừ số dư chờ khi hoàn xong (v5.3) | Đơn bị hủy sau thanh toán: Settlement chuyển chờ\_hoàn, phần tiền của đơn vẫn nằm trong số dư chờ nhưng bị khóa; chỉ khi Refund thành công mới trừ số dư chờ của ví Seller (Sᵢ) và ví nền tảng (Cᵢ). |
| Tách tài khoản theo vai trò | Customer, Seller, Administrator dùng tài khoản độc lập, không cộng gộp quyền. Cấm tài khoản theo nghĩa vụ UC-E03, không scope. |

## Giải nghĩa công thức & sổ tiền (FRD v5.x)

**Đọc phần này trước Database RBAC v5.0 mục 8.3, UC-G04 và UC-D05.** P-COM-01 chốt hoa hồng chung 5% = 500 bps theo [tham số đã chốt](parameters.md), áp dụng mọi Seller/ngành hàng. Checkout lưu tỷ lệ trên từng Order; Finance dùng snapshot, không tra Category hoặc tính lại đơn cũ.

### 01 | Bảng ký hiệu và đơn vị

| Ký hiệu / trường | Diễn giải nghiệp vụ | Đơn vị |
| :---- | :---- | :---- |
| G \= Order.tổng\_tiền | Tổng giá trị một đơn của một Seller; đã chụp giá/số lượng lúc checkout. | VND, số nguyên |
| r \= Order.tỷ\_lệ\_hoa\_hồng\_bps\_snapshot | Tỷ lệ chụp lúc checkout: 500 bps (5%) theo P-COM-01. | bps |
| 1 bps | 0,01%; 100 bps \= 1%; 500 bps \= 5%. | tỷ lệ |
| C \= hoa\_hồng\_dự\_kiến | Hoa hồng tính trên đơn; ghi vào ngăn chờ của ví nền tảng khi thanh toán, chốt khi đơn được xác nhận nhận hàng. | VND, số nguyên |
| S \= seller\_nhận\_dự\_kiến | Khoản Seller dự kiến được hưởng từ đơn; S \= G − C. Ghi vào số dư chờ khi thanh toán, chuyển sang khả dụng khi đơn đã\_xác\_nhận. | VND, số nguyên |
| T \= CheckoutSession.tổng\_tiền | Tổng tiền thanh toán chung một lần \= Σ G của N đơn trong cùng lần checkout. | VND, số nguyên |
| A \= Withdrawal.số\_tiền | Số nguyên dương ≤ available; ngưỡng chung 10.000 VND, ngoại lệ BANNED rút toàn bộ available dương dưới ngưỡng (UC-D05). | VND |
| floor(x) | Lấy phần nguyên thấp hơn hoặc bằng x; \+5.000 thực hiện làm tròn nửa lên ở phép chia /10.000. | phép toán |

### 02 | Công thức & ví dụ tính từng bước

**C \= floor((G × r \+ 5.000) / 10.000)**

**S \= G − C**

**Ví dụ 1:** đơn của cửa hàng A có G \= 200.000 VND; r \= 500 bps \= 5% → C \= 10.000 VND → S \= 190.000 VND. Các con số tính trên giá trị của riêng đơn đó, không phải tổng tiền thanh toán chung.

**Ví dụ 2 (kiểm tra làm tròn):** đơn có G \= 10.010 VND; r \= 500 bps \= 5% → C \= 501 VND, S \= 9.509 VND. Chỉ làm tròn một lần trên mỗi Order, không làm tròn từng sản phẩm.

### 03 | Các ngăn tiền trong sổ giả lập

Toàn bộ tiền khách đã trả nằm ở đúng **một** trong các ngăn dưới đây tại mỗi thời điểm. Tiền chuyển giữa các ngăn bằng bút toán ví (WalletTransaction, Database 3.5.3).

| Ngăn tiền | Vào ngăn khi | Ra khỏi ngăn khi | Được rút? |
| :---- | :---- | :---- | :---- |
| Chờ quyết toán (Seller: S; nền tảng: C) | Payment success — ghi cho từng Order (PAYMENT\_RECEIVED) | Order đã\_xác\_nhận (→ khả dụng / hoa hồng đã chốt) hoặc Refund của đơn thành công (→ đã hoàn, REFUND\_DEBIT) | Không |
| — trong đó: phần của đơn đã hủy đang chờ hoàn | Order hủy sau thanh toán: Settlement → chờ\_hoàn; tiền vẫn ở ngăn chờ nhưng bị khóa, không bao giờ sang khả dụng | Refund của đơn thành công (→ đã hoàn) | Không |
| Chờ hoàn toàn bộ phiên | Thanh toán thành công muộn khi phiên đã đóng (toàn bộ Payment; không qua ví) | Refund toàn phần thành công | Không |
| Đã hoàn khách | Refund success | — | — |
| Hoa hồng đã chốt (ví nền tảng) | Order đã\_xác\_nhận (C) | — (MVP không rút tiền nền tảng) | Không áp dụng |
| Khả dụng (ví Seller) | Order đã\_xác\_nhận (S) | Seller rút tiền (A) | **Có** |
| Đã rút (ví Seller) | Seller rút tiền (A) — hoàn tất ngay | — | — |

**Vì sao tiền chờ không được rút: Order chỉ có thể bị hủy và hoàn tiền khi chưa sang đã\_giao (Seller từ chối, quá hạn 7 ngày, khách hủy ở chờ\_xử\_lý, Seller từ chối hoặc hết hạn xử lý J4; Seller bị cấm sau thanh toán vẫn được quyền xử lý đơn đang dở). Đơn đã\_xác\_nhận là trạng thái kết thúc, không thể hủy hay hoàn. Vì tiền chỉ trở thành khả dụng khi đơn đã\_xác\_nhận, mọi khoản có khả năng phải hoàn cho khách vẫn đang nằm trong ngăn chờ do nền tảng giữ. Do đó không thể xảy ra tình huống Seller đã rút hết tiền rồi phát sinh hoàn tiền mà ví Seller không đủ để trả.**

**Ví dụ 3 (tình huống rút tiền rồi khách hủy; r \= 500 bps \= 5%):**

| Ngày | Sự kiện | Ví Seller (chờ / khả dụng / đã rút) | Ví nền tảng (chờ / đã chốt) | Chờ hoàn / đã hoàn khách |
| :---- | :---- | :---- | :---- | :---- |
| 1 | Khách thanh toán Đơn X \= 50.000 | 47.500 / 0 / 0 | 2.500 / 0 | 0 / 0 |
| 2 | Seller xác nhận đã giao Đơn X | 47.500 / 0 / 0 | 2.500 / 0 | 0 / 0 |
| 5 | Seller muốn rút tiền | Bị từ chối: khả dụng \= 0 (Đơn X chưa xác nhận) | — | — |
| 6 | (Giả định khách muốn hủy Đơn X) | Không thể hủy vì đơn đã\_giao; khách chỉ còn chọn “Đã nhận hàng” hoặc chờ J3 | — | — |
| 9 | J3 tự xác nhận Đơn X (7 ngày từ ngày 2\) | 0 / 47.500 / 0 | 0 / 2.500 | 0 / 0 |
| 10 | Seller rút 47.500 (thành công) | 0 / 0 / 47.500 | 0 / 2.500 | 0 / 0 |
| 11 | Khách thanh toán Đơn Y \= 50.000 cùng Seller | 47.500 / 0 / 47.500 | 2.500 / 2.500 | 0 / 0 |
| 12 | Khách hủy Đơn Y (còn chờ\_xử\_lý); Settlement Y → chờ\_hoàn | 47.500 (khóa, chờ hoàn) / 0 / 47.500 | 2.500 (chờ hoàn) / 2.500 | 50.000 (nằm trong ngăn chờ) / 0 |
| 12 | Refund Đơn Y thành công → REFUND\_DEBIT | 0 / 0 / 47.500 | 0 / 2.500 | 0 / 50.000 |

Kiểm tra bảo toàn ngày 12: tổng thu 100.000 \= 50.000 đã hoàn \+ 2.500 hoa hồng đã chốt \+ 47.500 Seller đã rút. Việc hoàn Đơn Y không chạm tới 47.500 Seller đã rút từ Đơn X.

**Bảo toàn số dư:** Tại cùng một thời điểm: tổng tiền đã thu (Payment success) \= hoàn đã thành công \+ chờ hoàn toàn bộ phiên (thanh toán muộn) \+ Σ số dư chờ quyết toán (mọi ví Seller và ví nền tảng, gồm cả phần của đơn đã hủy đang chờ hoàn) \+ hoa hồng đã chốt \+ Σ số dư khả dụng Seller \+ Σ đã rút. Với từng Seller: Σ S của các Order đã\_xác\_nhận \= khả dụng \+ đã rút; Σ S của Settlement chờ\_quyết\_toán và chờ\_hoàn \= số dư chờ (WD-11). Mỗi đồng tiền chỉ nằm ở một ngăn; không gộp số dư tại thời điểm với số phát sinh ở các kỳ khác nhau.

### 04 | Thẩm quyền tham số và những điểm còn mở

| Tham chiếu | Tình trạng / lưu ý |
| :---- | :---- |
| P-COM-01 | Đã chốt 500 bps = 5%, chung mọi Seller/ngành hàng; Order lưu snapshot. |
| N-06 | Chu kỳ job nền; không tự giả định lịch chạy. |
| P-xx / N-xx | [parameters.md](parameters.md) thay các giá trị cũ cùng chủ đề. P-01 vẫn tạm 15 phút chưa chốt; các mục mở khác giữ trạng thái riêng, N-09/N-11 không đổi. P-37 chốt ngày 19/11/2026: tìm tên sản phẩm và tên cửa hàng không phân biệt dấu tiếng Việt và hoa thường. |
| BRD / UI flow | Cần đồng bộ BRD, UI flow, wireframe và activity diagram với mô hình tách đơn (v5.0), mua ngay (v5.1), ví và rút tiền, bỏ báo cáo vi phạm (v5.2). |

## Ma trận truy vết tóm tắt

**Tra theo hành trình nghiệp vụ → UC → dữ liệu → quy tắc / kiểm thử.** Mã tham chiếu giữ theo bản gốc để phù hợp backlog và test case.

| Nghiệp vụ / điểm kiểm soát | Use case | Đối tượng dữ liệu | Tham chiếu |
| :---- | :---- | :---- | :---- |
| Đăng ký và kiểm soát truy cập | A01–A07, E01/E03/E06 | User / Customer / Seller / UserRole / AdminAuditLog | DB 3.1, mục 6, 8.1 |
| Chọn giỏ → tách thành N đơn theo cửa hàng | B03–B04 | Cart, CheckoutSession, Order, OrderItem, Reservation | DB 3.3, 3.6, 8.1 |
| Mua ngay từ trang sản phẩm → 1 đơn, không qua giỏ | B10, B04 | CheckoutSession (checkout\_source \= BUY\_NOW), Order, OrderItem, Reservation | DB 3.6.1, G-16 |
| Thanh toán một lần cho N đơn, ghi số dư chờ, chống thu trùng | B05–B06, G03 | CheckoutSession, Payment, Reservation, Order, Settlement, WalletTransaction | DB 3.4, 3.5, 8.2, 8.3 |
| Hủy / hoàn tiền theo từng đơn | B08, G02, E08 | Order, Refund, Settlement, WalletTransaction | DB 3.4.2, 3.5, 8.2, 8.3 |
| Xác nhận nhận đơn → quyết toán → số dư khả dụng | B09, G03, G04 | Order, Settlement, Wallet, WalletTransaction | DB 3.5, 8.3 |
| Seller rút tiền giả lập (tức thời) | D05 | Wallet, WalletTransaction, Withdrawal | DB 3.5, 8.3 (WD) |
| Admin báo cáo tổng quan / đối soát số dư | E02, E08 | Payment, Refund, Settlement, Wallet, Withdrawal | DB 8.3; P-13 |
| Thông báo và job định kỳ | G01, G03, E03 | Notification, CheckoutSession, Order, Payment, Withdrawal, User/Customer/Seller | [Database §8.2](database.md); J7 Identity và ca B1–B11 tại §9 |

**Nguyên tắc đối chiếu:** Các tham chiếu theo mã UC hoặc số mục; ưu tiên liên kết tới thực thể và acceptance criteria ngay trong use case. Không thay đổi ID gốc dù đã rút gọn tiêu đề hiển thị.

## 1. Giới thiệu

FRD gộp yêu cầu người dùng (use case) và yêu cầu chức năng hệ thống vào **một** tài liệu; chỉ dùng **UC-ID** làm định danh. Mỗi use case tự chứa: mô tả, luồng, business rule, edge case, acceptance criteria. Mọi giá trị còn để ngỏ (thời gian, giới hạn, định nghĩa) được ký hiệu **P-xx** và liệt kê ở 05-open-items-parameters.md; yêu cầu phi chức năng/kiến trúc ở 04-nfr-architecture.md.

## 2. Actor

| Actor | Mô tả | Ghi chú |
| :---- | :---- | :---- |
| Customer | Duyệt, tìm kiếm, mua hàng (đa-seller, tách đơn theo cửa hàng), theo dõi/hủy đơn, xác nhận nhận hàng, đánh giá | Chưa đăng nhập chỉ xem/tìm kiếm được |
| Seller | Tài khoản riêng; APPROVED/ACTIVE quản lý cửa hàng; BANNED chỉ nghĩa vụ đơn/tiền UC-E03. | PENDING/REJECTED không login; nghĩa vụ pending/hệ thống cũng giữ phiên giới hạn. |
| Administrator | Quản trị & giám sát toàn nền tảng | Tài khoản Administrator riêng do hệ thống cấp nội bộ (seed), không đăng ký công khai và không dùng chung với tài khoản Customer/Seller. |
| System | Các tác vụ nền: thông báo, hoàn tiền, quyết toán và ghi sổ ví, đối soát thanh toán, hết hạn giữ chỗ, tự hoàn tất đơn | Mô tả ở nhóm G |

## 3. Sơ đồ Use Case tổng quan

Sơ đồ use case giữ cấu trúc 7 nhóm A–G. Thay đổi v5.2 khi vẽ lại: **thêm** UC-D05 “Ví người bán và rút tiền” (actor Seller); **xóa** UC-C02 “Báo cáo sản phẩm/Seller vi phạm” và UC-E05 “Xử lý báo cáo vi phạm”; đổi tên UC-G04 thành “Quyết toán đơn và ghi có ví (giả lập)”. Các nhãn “nhóm Seller / đơn chung” đổi thành “đơn theo cửa hàng”.

## 4. Mô hình dữ liệu — ĐÃ CHUYỂN SANG DATABASE (v5.3; hiện hành: Database RBAC v5.0)

Mục 4 đã bị xóa khỏi FRD từ v5.3. Số mục được giữ chỗ để không đổi đánh số mục 5–9. Toàn bộ nội dung chuyển sang tài liệu Database RBAC v5.0 theo bảng dưới; khi gặp tham chiếu cũ “4.x” ở tài liệu khác, tra theo bảng này.

| Mục FRD cũ | Nội dung | Vị trí mới trong Database RBAC v5.0 |
| :---- | :---- | :---- |
| 4.1 | Customer | 3.1.1 User (danh tính chung); 3.1.2 Customer |
| 4.2 | Sổ địa chỉ, quy tắc địa chỉ mặc định | 3.1.4 CustomerAddress |
| 4.3 | Seller, trạng thái duyệt | 3.1.3 Seller; 8.2C |
| 4.4 | Tài khoản Admin | 3.1.1 User \+ vai trò ADMIN (3.1.5–3.1.9); không còn AdminAccount |
| 4.5 | Danh mục | 3.2.1 Category |
| 4.6 | Sản phẩm, media ảnh/video | 3.2.2 Product; 8.2E |
| 4.7 | Giỏ hàng | 3.3 Cart, CartItem |
| 4.8 | CheckoutSession, Order | 3.6.1 CheckoutSession; 3.6.2 Order |
| 4.9 | OrderItem | 3.6.3 OrderItem |
| 4.10 | Tồn kho, giữ hàng, biến động kho | 3.6.4 Inventory; 3.6.5 Reservation; 3.6.6 StockMovement |
| 4.11 | Payment, Refund | 3.4.1 Payment; 3.4.2 Refund; 8.4 bộ giả lập |
| 4.12 | Review | 3.2.3 Review |
| 4.13 | Report (đã loại bỏ v5.2) | Không có thực thể; xem 5.6 |
| 4.14 | Notification | 3.7.1 Notification |
| 4.15 | Nhật ký thao tác Admin | Mục 6 (AdminAuditLog) |
| 4.16 (A–J) | State machine | 8.2 (A–J) |
| 4.17.1–4.17.17 | Quy tắc toàn cục | 8.1 (G-01…G-17, cùng số thứ tự) |
| 4.18 | Dòng tiền, hoa hồng, quyết toán | 8.3 |
| 4.19 | Ví, bút toán, rút tiền | 3.5.2 Wallet; 3.5.3 WalletTransaction; 3.5.4 Withdrawal; 8.3 (WD-01…WD-11) |

## 5. Ma trận quyền theo actor

| Chức năng | Customer | Seller (phạm vi riêng) | Administrator |
| :---- | :---- | :---- | :---- |
| Đăng ký | ✓ (tài khoản Customer riêng, bằng email hoặc Google; không bổ sung vai trò Customer trên tài khoản Seller/Admin) | ✓ (tài khoản Seller riêng bằng email; chờ duyệt không đăng nhập; nếu bị từ chối phải gửi hồ sơ đăng ký mới) | – (cấp nội bộ) |
| Đăng nhập | Đúng kênh Customer; BANNED theo UC-E03. | APPROVED, đúng kênh Seller; BANNED theo UC-E03; PENDING/REJECTED không login. | Tài khoản Admin riêng. |
| Hồ sơ cá nhân, đổi mật khẩu | ✓ (họ tên, số điện thoại, ảnh đại diện lưu ở users.avatar\_object\_key, sổ địa chỉ) | ✓ (mô tả, ảnh đại diện dùng làm logo cửa hàng tại users.avatar\_object\_key; tên cửa hàng là họ tên tài khoản Seller, không đổi khi chờ duyệt/đã duyệt) | ✓ (họ tên, mật khẩu, ảnh đại diện của tài khoản Administrator riêng) |
| Xem catalog/tìm kiếm sản phẩm & Seller | ✓ | ✓ | ✓ |
| Giỏ, mua ngay, checkout, thanh toán mới | Chỉ ACTIVE; BANNED không dùng kể cả có AWAITING_PAYMENT. | – | – |
| Xem, hủy, xác nhận nhận hàng | Đúng chủ/state; BANNED chỉ đơn/phiên/Refund gắn nghĩa vụ hiện tại. | – | – |
| Đánh giá sản phẩm | ✓ | – | – |
| Quản lý sản phẩm & tồn kho | – | ✓ (của mình) | Chỉ gỡ/khôi phục (UC-E04) |
| Xử lý đơn cửa hàng: đóng gói → giao hoặc từ chối (M) | – | Đúng seller_id/state/hạn; BANNED xử lý đơn tồn gắn nghĩa vụ kể cả hiệu ứng tới muộn. | – |
| Thống kê kinh doanh | – | ✓ chỉ xem của mình | ✓ toàn hệ thống |
| Xem ví | – | Ví của mình; BANNED chỉ thông tin nghĩa vụ pending/available và biên nhận liên quan. | Mọi ví, chỉ xem |
| Rút tiền giả lập (M) | – | ✓ rút tiền từ số dư khả dụng của mình, hoàn tất ngay; Seller bị cấm mà đáp ứng ngoại lệ đăng nhập vẫn được rút. | – (không rút thay, không duyệt) |
| Xử lý hoàn tiền lỗi (M) | – | – | ✓ |
| Duyệt / từ chối Seller | – | – | ✓ |
| Cấm/gỡ cấm tài khoản UC-E03, không scope | – | – | ✓ |
| Xem người dùng | – | – | ✓ |
| Quản lý danh mục | – | – | ✓ |
| Xem đơn toàn hệ thống & can thiệp đơn lỗi (S) | – | – | ✓ |
| Nhận thông báo (email, in-app) | ✓ | ✓ | – (dùng dashboard) |

**Quyền dữ liệu:** Đúng tài khoản/vai trò/chủ sở hữu. BANNED chỉ nghĩa vụ hiện tại UC-E03 gồm Payment/Refund/pending/available; không lịch sử ngoài nghĩa vụ hoặc Order Seller khác dù chung phiên. AccessMode không thay ownership/state.

**Customer, Seller và Administrator dùng ba tài khoản độc lập, không cộng gộp quyền hay dùng chung phiên đăng nhập. Người dùng nhiều vai trò cần tài khoản riêng; quyền Admin không tự cấp quyền mua/bán. Mô hình một User nhiều vai trò của Database/API hiện hành cần sửa theo quyết định FRD Conflicts số 6\.**

## 6. Đặc tả chi tiết theo nhóm chức năng

Cách đọc: Mỗi use case trình bày mục tiêu, điều kiện, các bước người dùng/hệ thống thực hiện, tình huống thay thế và kết quả kiểm tra được. Các thuật ngữ dữ liệu, quy tắc trạng thái và ràng buộc triển khai được đặc tả tại mục 4\. M \= Must, S \= Should, C \= Could; P-xx là tham số chưa chốt.

### Nhóm A — Tài khoản & hồ sơ

#### UC-A01 | Đăng ký tài khoản khách hàng

*Vai trò: Khách hàng · Ưu tiên: M (đăng ký bằng Google: S)*

**Mục tiêu: Khách hàng tạo tài khoản Customer riêng để mua sắm và quản lý đơn hàng; không dùng tài khoản Seller hoặc Administrator.**

**Điều kiện:** Khách chưa có tài khoản với email hoặc số điện thoại đăng ký.

**LUỒNG CHÍNH**

1. Khách chọn “Đăng ký”, nhập email, họ tên, số điện thoại và mật khẩu; xác nhận lại mật khẩu.

2. Hệ thống kiểm tra thông tin bắt buộc, định dạng, độ mạnh mật khẩu và việc trùng thông tin.

3. Nếu hợp lệ, hệ thống tạo tài khoản khách hàng và thông báo đăng ký thành công (popup 1s).

4. Khách có thể đăng nhập ngay để sử dụng các chức năng mua sắm.

**LUỒNG THAY THẾ / NGOẠI LỆ**

* Đăng ký bằng Google (Should): khách cho phép sử dụng tài khoản Google; hệ thống lấy email và họ tên, tạo tài khoản. Khách phải bổ sung số điện thoại trong hồ sơ trước khi đặt hàng.

* Email hoặc số điện thoại đã được sử dụng: hệ thống chỉ rõ thông tin trùng và không tạo tài khoản mới.

* Người đã có tài khoản Seller/Administrator nhưng muốn mua hàng phải đăng ký tài khoản Customer độc lập bằng email khác; không bổ sung hồ sơ Customer vào tài khoản hiện có.

* Khách chọn phương thức Google cho email đã đăng ký bằng mật khẩu hoặc ngược lại: hệ thống hướng dẫn đăng nhập bằng phương thức đã dùng; không tự gộp tài khoản.

* Google không xác minh được email hoặc khách không đồng ý: hệ thống dừng đăng ký và quay lại màn hình ban đầu.

* Khách gửi yêu cầu nhiều lần do mất kết nối: hệ thống không tạo tài khoản trùng.

**Kết quả mong đợi: mỗi email chỉ gắn một tài khoản và mỗi tài khoản chỉ thuộc một vai trò Customer/Seller/Administrator; người có nhiều vai trò phải dùng các tài khoản riêng. Cần cập nhật mô hình User/RBAC tại Database/API; mật khẩu được bảo vệ.**

#### UC-A02 | Đăng ký trở thành người bán

*Vai trò: Người bán · Ưu tiên: M*

**Mục tiêu: Người đăng ký tạo tài khoản Seller độc lập và gửi hồ sơ xin quyền kinh doanh, không chuyển hoặc bổ sung vai trò Seller trên tài khoản Customer/Administrator.**

**Điều kiện: Tạo tài khoản Seller riêng hoặc gửi hồ sơ mới nếu trước đó bị từ chối; tên cửa hàng không trùng hồ sơ đang chờ/đã duyệt; thông tin đăng nhập của Seller không được dùng chung với tài khoản Customer/Administrator.**

**LUỒNG CHÍNH**

1. Người muốn bán hàng chọn “Đăng ký bán hàng”, đăng ký tài khoản Seller riêng bằng email, tên cửa hàng, số điện thoại và mật khẩu. Tên cửa hàng dùng users.full\_name, số điện thoại tài khoản Seller dùng làm số liên hệ cửa hàng.

2. Người đã có tài khoản Customer hoặc Administrator nhưng muốn bán hàng phải đăng ký tài khoản Seller riêng; không dùng tài khoản đang có để đăng ký vai trò Seller, kể cả cùng một người sở hữu.

3. Hệ thống kiểm tra thông tin và cho người dùng xác nhận gửi hồ sơ.

4. Hệ thống tiếp nhận hồ sơ ở trạng thái “Chờ duyệt”, gửi email xác nhận đã nhận hồ sơ và hiển thị thông báo.

5. Quản trị viên xem xét hồ sơ (UC-E06); hệ thống gửi kết quả duyệt hoặc từ chối qua email.

**LUỒNG THAY THẾ / NGOẠI LỆ**

* Email đã thuộc tài khoản Customer/Administrator hoặc Seller đang chờ duyệt/đã duyệt: không tạo tài khoản Seller trùng; yêu cầu email khác khi đăng ký mới. Ngoại lệ: Seller đã bị từ chối có thể nộp hồ sơ đăng ký mới sau bước xác minh quyền sở hữu email, không cần đăng nhập vào kênh Seller bị từ chối; hồ sơ cũ được lưu lịch sử.

* Tên cửa hàng trùng hồ sơ đang chờ duyệt hoặc đã duyệt, hoặc số điện thoại đã thuộc tài khoản khác: hệ thống chỉ rõ thông tin trùng và không nhận hồ sơ.

* Hồ sơ đang “Chờ duyệt”: không cấp quyền đăng nhập kênh Seller và không được thao tác bán. Kết quả xét duyệt được gửi qua email.

* Hồ sơ bị từ chối: hệ thống gửi lý do qua email, không cho đăng nhập tài khoản Seller. Muốn đăng ký lại phải gửi hồ sơ đăng ký mới qua luồng đăng ký bán hàng và xác minh email; không sửa/nộp lại hồ sơ cũ, giữ lịch sử hồ sơ bị từ chối.

* Hồ sơ chờ duyệt/đã duyệt không được nộp thêm hồ sơ. Hồ sơ bị từ chối chỉ có thể được thay thế bằng một lần đăng ký mới, không tái sử dụng hồ sơ đã bị từ chối.

**Kết quả mong đợi: tài khoản Seller riêng; chờ duyệt/bị từ chối không đăng nhập; chỉ Seller đã được duyệt và không bị cấm mới bán/hiển thị công khai. Muốn đăng ký lại sau từ chối phải nộp hồ sơ mới. Database/API cần bổ sung cơ chế nhiều lần nộp hồ sơ, xác minh email và loại bỏ luồng gán thêm vai trò Seller cho tài khoản có sẵn.**

#### UC-A03 | Đăng nhập

*Vai trò: Khách hàng, Người bán, Quản trị viên · Ưu tiên: M (Google: S)*

**Mục tiêu: Xác thực theo kênh Customer, Seller hoặc Administrator, chỉ cấp quyền cho tài khoản thuộc đúng kênh và đang đủ điều kiện.**

**Điều kiện:** Tài khoản đúng kênh; Seller APPROVED. Login/refresh đánh giá vòng đời và nghĩa vụ hiện tại theo UC-E03, kể cả nghĩa vụ đến muộn của DISABLED.

**LUỒNG CHÍNH**

1. Người dùng chọn kênh Customer, Seller hoặc Administrator rồi đăng nhập bằng thông tin của tài khoản tương ứng; đăng nhập Google hỗ trợ cho tài khoản Customer đã dùng Google.

2. Hệ thống xác minh thông tin, vai trò tài khoản, trạng thái duyệt/cấm và quyền truy cập; đúng mật khẩu không đồng nghĩa được đăng nhập vào kênh khác.

3. Nếu hợp lệ, hệ thống tạo phiên mới cho đúng tài khoản/kênh: lưu hash/thời hạn refresh và sessionId mới trên User cùng transaction. Mỗi User chỉ một phiên; login mới (kể cả Google Should) thay phiên trước, access token phiên trước bị từ chối ở lần kiểm tra quyền tiếp theo dù chưa hết hạn. Không có đăng nhập dùng chung Customer/Seller/Administrator.

4. BANNED nhận phiên giới hạn theo accessMode/allowlist UC-E03; Customer không được mua hoặc thanh toán mới.

**LUỒNG THAY THẾ / NGOẠI LỆ**

* Sai thông tin: thông báo chung, không tiết lộ tài khoản có tồn tại. Không khóa tạm tài khoản vì đăng nhập sai nhiều lần (P-18); vẫn giữ rate limit và biện pháp chống lạm dụng theo N-04, không nhầm với cấm bởi Admin.

* Seller PENDING/REJECTED không login; BANNED chỉ xử lý nghĩa vụ đơn/tiền của mình theo UC-E03, không kinh doanh mới.

* Nhập đúng thông tin của tài khoản nhưng không thuộc vai trò của kênh (ví dụ Customer vào Seller/Admin): thông báo chung “Thông tin đăng nhập không chính xác”, không hướng dẫn đăng ký bán hàng và không tiết lộ vai trò tài khoản.

* DISABLED chỉ bị từ chối với ACCOUNT_DISABLED sau kiểm tra nghĩa vụ muộn; nguồn cần xác minh lỗi trả 503. BANNED chỉ còn nghĩa vụ hệ thống dùng OBLIGATIONS_READ_ONLY; riêng Customer có Refund FAILED hết lượt cần thử lại dùng OPEN_ORDERS_ONLY với refund:retry:own trên đúng nghĩa vụ, kể cả FULL_CHECKOUT đã đóng. Không dùng giỏ, checkout, review hoặc lịch sử ngoài nghĩa vụ.

* Đăng nhập bằng Google khác phương thức đăng ký: hướng dẫn dùng phương thức đúng; không tự liên kết tài khoản.

**Kết quả mong đợi:** Đúng loại tài khoản, quyền hiện hành theo UC-E03; không dựa vào lịch sử đã từng thanh toán hoặc chỉ trạng thái terminal của Order.

**Vòng đời phiên theo [quyết định 0003](../decisions/0003-mot-phien-refresh-tren-user.md) — mục tiêu chưa triển khai:** refresh chỉ nhận cookie HttpOnly `refresh_token`, kiểm tra Origin theo quyết định 0001; không trả token nguyên văn qua JSON. Xác minh quyền/nghĩa vụ hiện hành rồi xoay hash và lưu dấu token cũ cùng transaction, giữ sessionId, gia hạn theo TTL cấu hình từ thời điểm thành công (P-06 chưa chốt). Giữ toàn bộ dấu chuỗi khi phiên còn hiệu lực. Dùng lại token đã xoay của đúng phiên hiện hành phải commit thu hồi phiên rồi trả 401 INVALID_REFRESH_TOKEN, không grace period; token lạ/hết hạn/thuộc phiên bị thay chỉ trả 401, không xóa phiên mới.

Login sai hoặc lỗi xác minh nghĩa vụ không thay phiên hiện có. Login/refresh/logout/thu hồi phải tuần tự hóa trên cùng User và kiểm tra lại hash/sessionId, version/trạng thái trước ghi; HTTP nghĩa vụ ngoài transaction giữ khóa. Hai login giữ lần ghi thành công sau cùng; hai refresh cùng token chỉ một lần xoay, lần còn lại là reuse thu hồi chuỗi. Request refresh/logout cũ không được ghi đè login mới. Các tab chia sẻ cookie là một phiên và cần phối hợp refresh, không phải nhiều phiên thiết bị độc lập.

#### UC-A04 | Đổi mật khẩu

*Vai trò: Khách hàng (local), Người bán, Quản trị viên · Ưu tiên: M*

**Mục tiêu:** Người dùng thay đổi mật khẩu để bảo vệ tài khoản.

**Điều kiện:** Đang đăng nhập bằng tài khoản có mật khẩu; không áp dụng tài khoản Google.

**LUỒNG CHÍNH**

1. Người dùng mở “Đổi mật khẩu”, nhập mật khẩu hiện tại và mật khẩu mới.

2. Hệ thống kiểm tra mật khẩu hiện tại, yêu cầu về độ mạnh và việc mật khẩu mới khác mật khẩu cũ.

3. Hệ thống đổi mật khẩu và thu hồi phiên hiện hành trên User cùng transaction; access/refresh cũ không dùng lại, người dùng phải đăng nhập lại.

**LUỒNG THAY THẾ / NGOẠI LỆ**

* Mật khẩu cũ không đúng hoặc mật khẩu mới không đạt yêu cầu: hiển thị lỗi ngay tại biểu mẫu, không thay đổi mật khẩu.

**Kết quả mong đợi:** Chỉ đổi khi xác thực đúng mật khẩu cũ; mật khẩu mới tuân thủ G-07 (Database RBAC v5.0 mục 8.1).

#### UC-A05 | Quên mật khẩu

*Vai trò: Khách hàng (local), Người bán, Quản trị viên · Ưu tiên: M*

**Mục tiêu:** Người dùng đặt lại mật khẩu khi không thể đăng nhập.

**Điều kiện:** Người dùng truy cập màn hình “Quên mật khẩu”.

**LUỒNG CHÍNH**

1. Người dùng nhập địa chỉ email và chọn “Gửi hướng dẫn”.

2. Hệ thống luôn hiển thị cùng một thông báo, dù email có tồn tại hay là tài khoản Google (G-05).

3. Nếu tài khoản đủ điều kiện (email tồn tại và tài khoản dùng mật khẩu, không phải Google), người dùng nhận liên kết đặt lại mật khẩu qua email.

4. Người dùng mở liên kết còn hiệu lực, đặt mật khẩu mới (đáp ứng các điều kiện) và nhận thông báo thành công.

5. Hệ thống thu hồi phiên hiện hành cùng transaction reset mật khẩu; không tạo phiên mới, người dùng phải đăng nhập lại.

**LUỒNG THAY THẾ / NGOẠI LỆ**

* Email không tồn tại hoặc là tài khoản Google: không gửi liên kết nhưng vẫn thông báo chung. Seller chờ duyệt/bị từ chối có thể đặt lại mật khẩu tài khoản LOCAL nhưng thao tác đó không cấp quyền đăng nhập Seller khi chưa được duyệt.

* Liên kết chỉ dùng một lần, hết hạn sau 30 phút kể từ khi tạo (P-05); đã dùng hoặc hết hạn thì yêu cầu gửi yêu cầu mới.

**Kết quả mong đợi:** Liên kết chỉ dùng một lần và không làm lộ thông tin tài khoản.

#### UC-A06 | Cập nhật hồ sơ

*Vai trò: mọi loại tài khoản (Customer, Seller, Administrator) · Ưu tiên: M*

**Mục tiêu: người dùng sửa hồ sơ của đúng tài khoản; ảnh đại diện của tài khoản Seller đồng thời là logo cửa hàng.**

**Điều kiện:** Đã đăng nhập vào tài khoản của mình.

**LUỒNG CHÍNH**

1. Người dùng mở “Hồ sơ”, xem các thông tin hiện có và chọn nội dung được phép cập nhật.

2. Người dùng sửa họ tên/số điện thoại theo quyền tài khoản; Customer quản lý địa chỉ; Seller sửa mô tả cửa hàng. Mỗi tài khoản có một trường ảnh users.avatar\_object\_key dùng làm ảnh đại diện; với Seller, cùng ảnh đó được hiển thị làm logo cửa hàng. Không tạo trường logo riêng.

3. Hệ thống kiểm tra các trường bắt buộc, sự trùng lặp và giới hạn thông tin.

4. Hệ thống lưu thay đổi hợp lệ và hiển thị dữ liệu mới trong hồ sơ.

**LUỒNG THAY THẾ / NGOẠI LỆ**

* Email không được sửa. Họ tên của tài khoản Seller đồng thời là tên cửa hàng và không được sửa khi hồ sơ đang chờ duyệt/đã duyệt (G-02); số điện thoại Seller là số liên hệ cửa hàng. Tài khoản Customer/Admin độc lập.

* Ảnh đại diện/logo không đúng định dạng hoặc vượt giới hạn (P-08), số điện thoại trùng hoặc địa chỉ thiếu: báo lỗi, giữ dữ liệu cũ. Mọi ảnh lưu ở users.avatar\_object\_key, không tạo seller.logo riêng.

* Khách xóa địa chỉ: địa chỉ bị xóa hẳn khỏi sổ địa chỉ; nếu đó là địa chỉ đang ưu tiên, hệ thống tự chọn lại theo quy tắc địa chỉ mặc định (Database RBAC v5.0 mục 3.1.4); khách vẫn được xóa địa chỉ cuối cùng nhưng sẽ phải thêm địa chỉ trước khi đặt hàng.

* Khách chọn tỉnh/thành khác: hệ thống yêu cầu chọn lại phường/xã/đặc khu phù hợp. P-26 dùng danh mục tĩnh có phiên bản nạp sẵn trong DB Identity, không gọi API địa giới ngoài; API chọn địa giới và validation dùng cùng bộ dữ liệu, nguồn/phiên bản phải ghi rõ khi chuẩn bị seed. Tối đa 3 địa chỉ hiện có (P-09), kể cả thêm đồng thời; địa chỉ xóa hẳn không còn tính vào giới hạn.

* Thay đổi địa chỉ sau khi đặt hàng không làm thay đổi thông tin nhận hàng của những đơn đã lập.

**Kết quả mong đợi: dữ liệu cập nhật đúng phạm vi tài khoản; avatar và logo của Seller dùng cùng một trường ảnh, Customer Google phải bổ sung số điện thoại trước khi đặt hàng.**

#### UC-A07 | Đăng xuất

*Vai trò: mọi loại tài khoản (Customer, Seller, Administrator) · Ưu tiên: M*

**Mục tiêu:** Người dùng kết thúc phiên đăng nhập trên thiết bị đang dùng.

**Điều kiện:** Người dùng chọn “Đăng xuất”; API giữ ANY_AUTH (session own), cần Bearer hợp lệ, cookie refresh và Origin trong allowlist.

**LUỒNG CHÍNH**

1. Hệ thống xác minh Bearer hợp lệ và Origin, lấy User/sessionId từ Bearer; refresh token chỉ đọc từ cookie `refresh_token`. Chỉ khi cookie hiện tại hoặc dấu đã xoay khớp cả chủ thể Bearer và phiên User hiện hành mới xóa ba trường phiên trên User. Không nhận userId/sessionId trong body/query để chọn tài khoản.

2. Màn hình quay về trạng thái chưa đăng nhập; các chức năng cần tài khoản yêu cầu đăng nhập lại.

**LUỒNG THAY THẾ / NGOẠI LỆ**

* Sau khi qua xác thực Bearer và Origin, cookie thiếu/không biết/không khớp vẫn trả 204 và xóa cookie đúng Path/thuộc tính, không thu hồi phiên khác. Cookie phiên cũ không xóa phiên mới.
* Thiếu/sai/hết hạn Bearer hoặc Bearer của phiên đã kết thúc/bị thay trả 401; sai Origin trả 403, không thay dữ liệu.

**Kết quả mong đợi:** Logout qua xác thực Bearer và Origin trả 204, clear cookie; chỉ phiên có cookie cùng chủ/phiên hiện hành được thu hồi và không dùng lại cả access/refresh. Các tab chung cookie cùng một phiên và đều cần đăng nhập lại. Không cam kết các thiết bị của cùng User có phiên độc lập: đăng nhập mới đã thay phiên trước. Quy tắc theo [quyết định 0003](../decisions/0003-mot-phien-refresh-tren-user.md), chưa phải bằng chứng code đã triển khai.

### Nhóm B — Customer: mua sắm (đa-seller, tách đơn theo cửa hàng)

#### UC-B01 | Tìm kiếm và xem sản phẩm

*Vai trò: Khách hàng (kể cả chưa đăng nhập) · Ưu tiên: M*

**Mục tiêu:** Khách tìm kiếm, lọc và xem thông tin sản phẩm phù hợp nhu cầu.

**Điều kiện:** Không bắt buộc đăng nhập.

**LUỒNG CHÍNH**

1. Khách mở trang sản phẩm hoặc nhập tên cần tìm.

2. Hệ thống hiển thị sản phẩm còn được phép bán, mặc định 20 sản phẩm/trang và mới nhất; tìm theo tên, lọc danh mục/khoảng giá, giữ các lựa chọn sắp xếp khác theo API (P-11). Thẻ sản phẩm hiển thị trung bình cộng đánh giá với 1 chữ số thập phân; tên người đánh giá che một phần (P-12).

3. Khách chọn sản phẩm để xem giá, hình ảnh/video, mô tả, trạng thái còn hàng và thông tin người bán; từ trang chi tiết có thể chọn số lượng rồi “Thêm vào giỏ hàng” (UC-B03) hoặc “Mua ngay” (UC-B10).

**LUỒNG THAY THẾ / NGOẠI LỆ**

* Không có kết quả: hệ thống hiển thị thông báo phù hợp.

* Sản phẩm hết hàng: vẫn có thể xuất hiện kèm nhãn “Hết hàng”; khách có thể đưa vào giỏ nhưng chưa được thanh toán; nút “Mua ngay” bị vô hiệu.

* Sản phẩm không có ảnh tĩnh: hiển thị ảnh minh họa mặc định.

* Sản phẩm vừa bị ẩn/gỡ trong lúc khách xem: khi thêm giỏ, hệ thống báo sản phẩm không còn bán.

**Kết quả mong đợi:** Danh sách không hiển thị sản phẩm của người bán chưa được duyệt hoặc bị cấm; tìm theo tên sản phẩm không phân biệt dấu tiếng Việt và hoa thường (P-37, chốt 19/11/2026). Chuẩn hóa cả từ khóa và tên để so khớp: bỏ dấu tiếng Việt, đổi đ/Đ thành d và chuyển về chữ thường; giữ nguyên tên lưu và hiển thị. Ví dụ “dien thoai”, “Điện Thoại” và “ĐIỆN THOẠI” cho cùng kết quả.

#### UC-B02 | Tìm kiếm và xem người bán

*Vai trò: Khách hàng · Ưu tiên: M*

**Mục tiêu:** Khách tra cứu người bán và xem những sản phẩm họ đang kinh doanh.

**Điều kiện:** Không yêu cầu khách phải đăng nhập.

**LUỒNG CHÍNH**

1. Khách tìm kiếm người bán theo tên cửa hàng hoặc chọn tên cửa hàng từ một sản phẩm. Tìm tên cửa hàng áp dụng cùng quy tắc P-37 như tên sản phẩm: không phân biệt dấu tiếng Việt và hoa thường; chuẩn hóa cả từ khóa và tên để so khớp, giữ nguyên tên lưu và hiển thị.

2. Hệ thống hiển thị trang người bán với tên cửa hàng, ảnh đại diện tài khoản Seller dùng làm logo, mô tả và các sản phẩm đang bán.

3. Khách chọn sản phẩm để tiếp tục xem hoặc mua.

**LUỒNG THAY THẾ / NGOẠI LỆ**

* Người bán chưa có sản phẩm: trang vẫn hiển thị nhưng danh sách sản phẩm trống.

* Hồ sơ chưa duyệt, bị từ chối hoặc tài khoản bị cấm: không hiển thị trang người bán công khai.

**Kết quả mong đợi:** Khách chỉ nhìn thấy sản phẩm của đúng người bán được chọn.

#### UC-B03 | Quản lý giỏ hàng

*Vai trò: Khách hàng (đã đăng nhập) · Ưu tiên: M*

**Mục tiêu:** Khách chọn và điều chỉnh các sản phẩm trước khi đặt hàng.

**Điều kiện:** Đã đăng nhập.

**LUỒNG CHÍNH**

1. Khách thêm hàng vào giỏ và điều chỉnh số lượng: mỗi dòng 1–99 đơn vị, tối đa 50 sản phẩm khác nhau/giỏ (P-10). Thêm lại cùng sản phẩm cộng vào dòng cũ, không vượt 99; checkout vẫn kiểm tra tồn khả dụng.

2. Hệ thống hiển thị giá, số lượng có thể mua và nhóm các dòng theo từng cửa hàng; mỗi nhóm sẽ trở thành một đơn riêng khi đặt hàng.

3. Khách chọn các sản phẩm cần mua và bấm “Thanh toán”.

4. Khi lần thanh toán chung thành công, hệ thống giảm đúng các sản phẩm đã mua trong giỏ, kể cả nếu sau checkout khách đã tăng số lượng.

**LUỒNG THAY THẾ / NGOẠI LỆ**

* Không đủ/hết hàng: không cho chọn để thanh toán; khách điều chỉnh lại số lượng.

* Sản phẩm ngừng bán: bị loại khi mở giỏ và có thông báo.

* Giá thay đổi: dùng giá tại lúc xác nhận đặt hàng.

* Thanh toán thất bại, chưa rõ hoặc hết hạn: không xóa các mặt hàng đã chọn.

* Đơn tạo qua “Mua ngay” (UC-B10): giỏ không bị đọc hay trừ, kể cả khi giỏ đang chứa cùng sản phẩm.

* BANNED không dùng giỏ/thu mới; phiên giới hạn theo nghĩa vụ UC-E03, không chỉ Order mở.

**Kết quả mong đợi:** Một giỏ nhiều cửa hàng được thanh toán một lần và tách thành các đơn riêng theo cửa hàng; giỏ không bị trừ lại khi nhận xác nhận thanh toán trùng.

#### UC-B04 | Đặt hàng và tách đơn theo cửa hàng

*Vai trò: Khách hàng · Ưu tiên: M*

**Mục tiêu:** Khách đặt hàng các sản phẩm thuộc một hoặc nhiều cửa hàng; hệ thống tách thành một đơn riêng cho mỗi cửa hàng.

**Điều kiện:** Đăng nhập, có số điện thoại, có địa chỉ nhận hàng và chọn tối thiểu một sản phẩm còn đủ hàng.

**LUỒNG CHÍNH**

1. Khách chọn “Mua hàng” từ giỏ (hoặc “Mua ngay” từ trang sản phẩm — UC-B10); hệ thống hiển thị sản phẩm chia theo từng cửa hàng, mỗi cửa hàng tương ứng một đơn dự kiến.

2. Hệ thống hiển thị tổng tiền từng đơn, tổng tiền cần thanh toán (tổng các đơn) và phí vận chuyển bằng 0\.

3. Khách chọn một địa chỉ nhận hàng (dùng chung cho tất cả các đơn) và xác nhận đặt hàng.

4. Hệ thống kiểm tra lại giá, người bán, khả năng bán hàng và tồn kho của tất cả sản phẩm cùng lúc.

5. Nếu tất cả hợp lệ, hệ thống giữ hàng, tạo một CheckoutSession (phiên thanh toán) và N Order — mỗi cửa hàng một Order; mỗi Order chỉ chứa sản phẩm của cửa hàng đó.

6. Hệ thống hiển thị danh sách các đơn vừa tạo, tổng tiền cần thanh toán và một nút “Thanh toán” duy nhất (UC-B05).

**LUỒNG THAY THẾ / NGOẠI LỆ**

* Thiếu địa chỉ/số điện thoại: yêu cầu hoàn thiện hồ sơ.

* Một mặt hàng hết hàng hoặc không hợp lệ: không tạo bất kỳ đơn nào (không tách một phần); thông báo mặt hàng cần sửa.

* Đã có phiên checkout chờ thanh toán: hướng dẫn thanh toán hoặc hủy phiên cũ trước.

* Gửi lại cùng yêu cầu: trả về cùng phiên thanh toán và các đơn đã tạo; thay nội dung nhưng dùng lại khóa xác nhận: từ chối xung đột (409).

* Hai khách tranh món cuối: chỉ một người giữ thành công.

**Kết quả mong đợi:** Một lần checkout tạo 1 CheckoutSession, N Order (N \= số cửa hàng) và một khoản thanh toán chung; mỗi Order có mã riêng, do đúng một Seller xử lý.

**Lưu ý nghiệp vụ:** Không cho thanh toán riêng từng đơn trong cùng một lần checkout; không hủy lẻ một đơn trước khi thanh toán (khách hủy cả phiên rồi đặt lại).

#### UC-B05 | Thanh toán

*Vai trò: Khách hàng, Hệ thống · Ưu tiên: M*

**Mục tiêu:** Thanh toán một lần cho tất cả các đơn được tách từ cùng một lần checkout.

**Điều kiện:** Phiên checkout còn hạn, chưa thanh toán, mọi sản phẩm hợp lệ và tài khoản khách được phép thanh toán.

**LUỒNG CHÍNH**

1. Khách mở phiên checkout (hoặc một đơn bất kỳ thuộc phiên) và chọn “Thanh toán”.

2. Hệ thống hiển thị danh sách các đơn, tổng tiền cần thanh toán cùng nhãn “Giả lập — không phát sinh tiền thật”.

3. Khách xác nhận; hệ thống gửi đúng một yêu cầu thanh toán chung tới bộ giả lập thanh toán nội bộ (Database RBAC v5.0 mục 8.4).

4. Khi có xác nhận thành công, hệ thống ghi nhận phiên đã thanh toán; tất cả các Order chuyển sang “Chờ xử lý” và được giao cho Seller tương ứng.

5. Từ đây mỗi đơn có tiến độ giao, hủy và hoàn tiền riêng.

**LUỒNG THAY THẾ / NGOẠI LỆ**

* BANNED không thanh toán mới; AWAITING_PAYMENT chỉ chờ J1/J2/J2b hoặc hủy hợp lệ; quyền theo UC-E03.

* Thanh toán bị từ chối rõ ràng (giai đoạn 2, kịch bản FAIL): tất cả các đơn của phiên đóng ở “Thanh toán thất bại”, giải phóng hàng đang giữ, giữ giỏ và hướng dẫn đặt hàng lại.

* Chưa nhận kết quả/timeout P-02 (giai đoạn 2, kịch bản TIMEOUT/LATE\_SUCCESS): hiển thị “Đang xác nhận thanh toán”, không cho thanh toán lại hoặc hủy phiên đến khi đối soát.

* Khách gửi lại hoặc mở hai tab: không tạo lần thu thứ hai; yêu cầu khác nội dung trả xung đột.

* Có hàng/Seller không còn hợp lệ ngay trước khi thu: dừng thanh toán và yêu cầu đặt hàng lại.

* Thanh toán thành công muộn sau khi phiên hết hạn/hủy: hoàn toàn bộ số tiền, không chuyển đơn nào sang xử lý.

**Kết quả mong đợi:** Mỗi CheckoutSession tối đa một Payment; kết quả thanh toán áp dụng đồng thời cho N đơn; hoàn tiền về sau tính theo từng đơn.

**Lưu ý nghiệp vụ:** Bộ giả lập thanh toán nội bộ chỉ mô phỏng, không phát sinh tiền thật; kết quả chưa rõ phải tiếp tục đối soát chứ không coi là thất bại. Giai đoạn 1 bộ giả lập luôn trả thành công nhưng luồng vẫn đi qua trạng thái chờ/thành công/thất bại như thiết kế đầy đủ; giai đoạn 2 bật thêm các kịch bản lỗi để kiểm thử các nhánh ngoại lệ.

#### UC-B06 | Xác nhận đơn sau thanh toán

*Vai trò: Hệ thống · Ưu tiên: M*

**Mục tiêu:** Ghi nhận thanh toán chung thành công, chuyển từng đơn cho đúng cửa hàng xử lý và ghi số dư chờ quyết toán.

**Điều kiện:** Hệ thống nhận được xác nhận thanh toán hợp lệ cho phiên checkout.

**LUỒNG CHÍNH**

1. Hệ thống kiểm tra lại tình trạng phiên, các đơn, sản phẩm và hàng đã giữ.

2. Hệ thống trừ số hàng đã mua đúng một lần, ghi nhận phiên đã thanh toán, chuyển từng Order sang “Chờ xử lý” và đặt hạn 7 ngày cho Seller của từng đơn.

3. Hệ thống tạo Settlement “chờ quyết toán” cho từng Order theo tổng tiền của đơn và ghi bút toán PAYMENT\_RECEIVED: Sᵢ vào số dư chờ của ví Seller, Cᵢ vào số dư chờ của ví nền tảng (Database RBAC v5.0 mục 8.3). Số dư chờ hiển thị cho Seller nhưng chưa được rút.

4. Nếu nguồn\_checkout \= giỏ\_hàng, hệ thống giảm phần đã mua trong giỏ (mua\_ngay: không động tới giỏ); gửi khách một thông báo thanh toán liệt kê các đơn theo cửa hàng và gửi mỗi Seller thông báo về đơn mới của mình.

**LUỒNG THAY THẾ / NGOẠI LỆ**

* Order Service, khi áp dụng Payment SUCCESS qua `payment.succeeded` hoặc đối soát dùng chung handler, xác minh điều kiện giao dịch qua Identity/Product ngoài transaction giữ khóa ([SAD §5.1](software-architecture.md), [Database §8.2B](database.md)). Chỉ invalid có bằng chứng tại thời điểm thu mới hủy/hoàn riêng Order đủ điều kiện PAID/PACKING; Payment giữ SUCCESS, đủ hiệu ứng checkout.paid/order.cancelled; Payment tạo Refund, Finance giữ pending/PENDING_REFUND đến Refund SUCCESS. Không ảnh hưởng Order Seller khác hoặc chuyển DELIVERED/CONFIRMED về CANCELLED.
* Seller ban sau capture không phải lý do hủy/hoàn, dù event tới chậm; không suy ra invalid tại lúc thu từ trạng thái Seller hiện tại. Thiếu bằng chứng hoặc Identity/Product lỗi → giữ công việc retry bền vững, không coi lỗi mạng là invalid. Replay không nhân đôi SOLD/RESTOCK/Refund/ledger. Không quét hủy mọi đơn Seller hoặc giao quyết định này cho J7/J2/J5; J4 và quyền xử lý đơn tồn giữ nguyên.

* Thông báo thanh toán thành công đến sau khi phiên đã hết hạn/hủy: hoàn toàn bộ tiền, không khôi phục đơn, không trừ kho, không tạo Settlement hay bút toán ví.

* Xác nhận thanh toán trùng hoặc xử lý đồng thời: không tạo thêm khoản thu, trừ kho, Settlement, bút toán ví hay thông báo.

**Kết quả mong đợi:** Lần checkout đã thanh toán một lần; mỗi Order được theo dõi riêng về hàng hóa và tiền; tiền của Seller nằm ở số dư chờ, chưa rút được ở bước này.

#### UC-B07 | Theo dõi trạng thái và lịch sử đơn

*Vai trò: Khách hàng · Ưu tiên: M*

**Mục tiêu:** Khách theo dõi từng đơn độc lập theo cửa hàng.

**Điều kiện:** Khách đã đăng nhập và mở “Đơn hàng của tôi”.

**LUỒNG CHÍNH**

1. Hệ thống liệt kê mỗi Order một dòng: tên cửa hàng, sản phẩm, tổng tiền đơn, trạng thái; các đơn cùng một phiên thanh toán được liên kết để khách biết chúng được trả cùng lần. Đơn và phiên định danh bằng ID, không có mã hiển thị riêng.

2. Khách mở một đơn để xem mặt hàng, địa chỉ nhận, trạng thái, lịch sử xử lý và khoản hoàn (nếu có).

3. Đơn ở trạng thái đã\_giao hiển thị “Đã giao — chờ khách xác nhận”; khách có thể chọn “Đã nhận hàng”.

4. Khách có thể chọn “Hủy đơn” nếu đơn đủ điều kiện và xem số tiền đang hoàn của đơn đó.

**LUỒNG THAY THẾ / NGOẠI LỆ**

* Phiên chưa thanh toán: các đơn hiển thị “Chờ thanh toán” với hạn chung; chỉ Customer ACTIVE có nút thanh toán chung. Customer BANNED chỉ xem nghĩa vụ, không thu mới; J1/J2/J2b và hủy hợp lệ vẫn tiếp tục. Payment pending: tạm khóa thanh toán/hủy.

* Các đơn cùng lần thanh toán ở các trạng thái khác nhau (ví dụ một đơn đã hủy, một đơn đã giao): hiển thị độc lập.

* Customer BANNED chỉ đơn/phiên và Payment/Refund gắn nghĩa vụ đúng chủ theo UC-E03, không toàn bộ lịch sử/mua sắm.

* Refund FAILED hết 3 lần retry tự động trong chu kỳ: Customer chủ Payment thấy “Thử lại hoàn tiền” theo UC-G02; trang đơn và trang phiên vẫn cho xem/retry FULL_CHECKOUT khi phiên đã đóng. Đọc lại trạng thái trước xác nhận, không tự retry khi mở link email.

* Không tìm thấy đơn hoặc không có quyền: không hiển thị dữ liệu của người khác.

**Kết quả mong đợi:** Mỗi cửa hàng là một đơn có trạng thái, hoàn tiền và xác nhận nhận hàng riêng; các đơn cùng lần checkout liên kết qua phiên thanh toán chung.

#### UC-B08 | Hủy đơn

*Vai trò: Khách hàng · Ưu tiên: M*

**Mục tiêu:** Khách hủy một đơn mà không ảnh hưởng các đơn khác được thanh toán cùng lần.

**Điều kiện:** Đơn được chọn đã thanh toán và chưa bắt đầu đóng gói (chờ\_xử\_lý), hoặc cả phiên checkout chưa thanh toán.

**LUỒNG CHÍNH**

1. Khách mở đơn cần hủy và chọn “Hủy đơn”.

2. Hệ thống kiểm tra trạng thái của đơn và hiển thị tác động, số tiền dự kiến hoàn nếu đã thanh toán.

3. Khách chọn CHANGED_MIND, ORDERED_BY_MISTAKE, FOUND_BETTER_PRICE, ADDRESS_CHANGE hoặc OTHER (P-21a); ghi chú tối đa 500 ký tự và bắt buộc không rỗng khi OTHER, rồi xác nhận hủy.

4. Nếu hợp lệ, hệ thống chuyển Order sang “Đã hủy”, hoàn lại hàng của đơn (nếu đã trừ kho), chuyển Settlement của đơn sang chờ\_hoàn (phần số dư chờ của Seller và nền tảng gắn với đơn bị khóa, chỉ bị trừ khi hoàn tiền thành công — UC-G02), tạo khoản hoàn bằng tổng tiền đơn và thông báo cho khách và Seller.

5. Các đơn khác cùng lần thanh toán vẫn giao, xác nhận nhận hàng và quyết toán bình thường.

**LUỒNG THAY THẾ / NGOẠI LỆ**

* Phiên chưa thanh toán và không có khoản đang xác minh: thao tác hủy áp dụng cho toàn bộ phiên — hệ thống báo trước sẽ hủy tất cả N đơn của lần checkout, nhả mọi lượng hàng đã giữ; không hoàn tiền vì chưa thu.

* Thanh toán đang chờ xác minh: không cho hủy cho tới khi biết kết quả.

* Đơn đã đóng gói, đã giao hoặc đã xác nhận: khách không thể hủy, hệ thống giải thích lý do.

* Khách nhấn hủy lặp: chỉ một lần hoàn kho, một lần chuyển Settlement sang chờ\_hoàn và một nghĩa vụ hoàn tiền cho đơn.

* Customer bị cấm nhưng còn đơn chưa hoàn tất: được hủy đúng đơn đang dở của mình khi đáp ứng điều kiện hủy thông thường.

**Kết quả mong đợi:** Hủy/hoàn tiền theo từng đơn trên một Payment chung; tiền hoàn lấy từ phần nền tảng đang tạm giữ của đơn, không trừ số dư khả dụng của Seller; tổng tiền hoàn không vượt khoản đã thanh toán và không thay đổi các đơn khác.

#### UC-B09 | Xác nhận đã nhận hàng

*Vai trò: Khách hàng, Hệ thống · Ưu tiên: M*

**Mục tiêu:** Xác nhận đã nhận hàng của một đơn sau khi người bán báo đã giao.

**Điều kiện:** Đơn đang ở trạng thái “Đã giao — chờ khách xác nhận”.

**LUỒNG CHÍNH**

1. Sau khi Seller bấm “Xác nhận đã giao”, hệ thống hiển thị “Đã giao — chờ khách xác nhận” và bắt đầu tính thời hạn 7 ngày riêng cho đơn đó.

2. Khách mở đơn và bấm “Đã nhận hàng”.

3. Hệ thống chuyển đơn sang “Đã xác nhận”, ghi nhận thời điểm hoàn tất.

4. Hệ thống quyết toán đơn (UC-G04): chốt hoa hồng và khoản người bán được nhận, chuyển khoản người bán được nhận từ số dư chờ sang số dư khả dụng của ví Seller. Hệ thống không tự động chi tiền; Seller chủ động rút (UC-D05).

5. Các đơn khác cùng lần thanh toán tiếp tục quy trình riêng, không chờ nhau.

**LUỒNG THAY THẾ / NGOẠI LỆ**

* Khách không phản hồi trong 7 ngày kể từ lúc Seller xác nhận đã giao: hệ thống tự chuyển đơn sang “Đã xác nhận” (J3), không dùng trạng thái “Đang giao”.

* Khách và hệ thống xác nhận đồng thời: chỉ ghi nhận một kết quả, một lần quyết toán và một bút toán SETTLEMENT\_RELEASED.

* Đơn bị hủy/đang hoàn tiền: không được xác nhận nhận hoặc chốt hoa hồng.

* Customer bị cấm nhưng còn đơn chưa hoàn tất: được xác nhận đã nhận hàng cho đúng đơn đang dở, không thao tác ngoài phạm vi đó.

**Kết quả mong đợi:** Sau xác nhận bởi khách hoặc tự động, chỉ đơn đó đạt “Đã xác nhận”, được quyết toán và tiền của Seller trở thành khả dụng; đơn đã xác nhận không thể bị hủy hay hoàn.

#### UC-B10 | Mua ngay từ trang sản phẩm

*Vai trò: Khách hàng · Ưu tiên: M*

**Mục tiêu:** Khách mua ngay một sản phẩm từ trang chi tiết mà không cần thêm vào giỏ rồi vào giỏ để thanh toán.

**Điều kiện:** Khách đã đăng nhập, có số điện thoại và địa chỉ nhận hàng; sản phẩm bán được và tồn kho khả dụng ≥ 1; không có CheckoutSession nào đang chờ thanh toán; tài khoản được phép thanh toán.

**LUỒNG CHÍNH**

1. Tại trang chi tiết sản phẩm (UC-B01), khách chọn số lượng và bấm “Mua ngay”.

2. Hệ thống kiểm tra số lượng (1–99 đơn vị, không vượt tồn khả dụng theo P-10) và mở trang Thanh toán chỉ với sản phẩm này, nằm dưới cửa hàng của sản phẩm (1 đơn dự kiến), kèm nhãn “Mua ngay — không qua giỏ hàng”.

3. Khách chọn địa chỉ nhận hàng (mặc định theo Database RBAC v5.0 mục 3.1.4) và bấm “Đặt hàng”.

4. Hệ thống thực hiện như UC-B04 bước 4–6: kiểm tra lại giá, người bán, tồn kho; giữ hàng; tạo 1 CheckoutSession (nguồn\_checkout \= mua\_ngay), 1 Order và 1 OrderItem; hiển thị nút “Thanh toán” (UC-B05).

5. Sau khi thanh toán thành công (UC-B06), đơn được xử lý như mọi đơn khác; giỏ hàng giữ nguyên.

**LUỒNG THAY THẾ / NGOẠI LỆ**

* Khách chưa đăng nhập: chuyển sang đăng nhập, xong quay lại đúng trang sản phẩm với số lượng đã chọn; khách bấm “Mua ngay” lại để tiếp tục (không tự đặt hàng).

* Sản phẩm hết hàng hoặc ngừng bán: nút “Mua ngay” bị vô hiệu; nếu trạng thái đổi trong lúc khách ở trang Thanh toán thì “Đặt hàng” báo lỗi và không tạo phiên.

* Số lượng vượt tồn kho khả dụng hoặc P-10: không cho tiếp tục, hiển thị “Chỉ còn X”.

* Đã có phiên checkout chờ thanh toán (từ giỏ hoặc mua ngay): hướng dẫn thanh toán hoặc hủy phiên cũ trước, như UC-B04.

* Thiếu số điện thoại/địa chỉ: yêu cầu hoàn thiện hồ sơ (UC-A06), rồi quay lại trang sản phẩm.

* Khách rời trang Thanh toán trước khi bấm “Đặt hàng”: chưa có phiên, đơn hay giữ hàng nào được tạo.

* Bấm “Đặt hàng” lặp: cùng khóa chống lặp trả về cùng phiên và đơn (G-09).

* BANNED không Mua ngay/đặt đơn mới; quyền nghĩa vụ theo UC-E03.

* Giỏ đang có cùng sản phẩm: không gộp, không trừ; giỏ không đổi.

**Kết quả mong đợi:** Một lần “Mua ngay” tạo tối đa 1 CheckoutSession (nguồn\_checkout \= mua\_ngay), 1 Order, 1 OrderItem và 1 Payment; mọi quy tắc thanh toán, hủy, hoàn, quyết toán áp dụng như đơn tạo từ giỏ; giỏ hàng không thay đổi.

**Lưu ý nghiệp vụ:** Không thêm sản phẩm khác vào phiên mua ngay; muốn mua nhiều sản phẩm thì dùng giỏ hàng. Đổi số lượng: quay lại trang sản phẩm.

### Nhóm C — Customer: sau mua hàng

#### UC-C01 | Đánh giá sản phẩm

*Vai trò: Khách hàng · Ưu tiên: S*

**Mục tiêu:** Khách đánh giá sản phẩm đã nhận từ người bán.

**Điều kiện:** Customer ACTIVE; sản phẩm thuộc một đơn đã\_xác\_nhận của khách và đơn không bị hủy. BANNED không được review, kể cả đã mua/nhận hàng; phiên nghĩa vụ không mở lịch sử ngoài allowlist UC-E03.

**LUỒNG CHÍNH**

1. Khách chọn sản phẩm trong lịch sử mua hàng thuộc đơn đã được xác nhận nhận.

2. Khách chọn số sao nguyên từ 1 đến 5; có thể nhập nội dung tối đa 1.000 ký tự và thêm tối đa 5 tệp, trong đó tối đa 1 video (P-08.3/P-08.5).

3. Hệ thống lưu đánh giá và cập nhật số lượng, điểm trung bình của sản phẩm.

4. Mỗi khách gửi một đánh giá cho mỗi sản phẩm; MVP không cho sửa hay gửi lại đánh giá đã đăng.

**LUỒNG THAY THẾ / NGOẠI LỆ**

* Chỉ chọn sao, không nhập nội dung/media: vẫn hợp lệ.

* Chưa xác nhận đã nhận hàng, đơn bị hủy hoặc không phải người mua: không cho đánh giá.

* Sao không nguyên hoặc ngoài 1–5: từ chối.

* Sản phẩm bị ẩn/gỡ: đánh giá được giữ nhưng không xuất hiện công khai đến khi sản phẩm được hiển thị lại.

**Kết quả mong đợi:** Chỉ đánh giá được hàng thuộc đơn đã\_xác\_nhận; một khách tối đa một đánh giá trên mỗi sản phẩm.

#### UC-C02 | Báo cáo sản phẩm/Seller vi phạm — ĐÃ LOẠI BỎ (v5.2)

Chức năng Customer gửi báo cáo vi phạm sản phẩm hoặc người bán bị loại khỏi phạm vi MVP. Không có nút “Báo cáo” trên trang sản phẩm/trang người bán, không có API và không lưu dữ liệu báo cáo (Database RBAC v5.0 mục 5.6). Mã UC-C02 được giữ chỗ, không dùng lại cho chức năng khác.

### Nhóm D — Seller (phạm vi của chính mình)

Chỉ Seller APPROVED/ACTIVE được dùng toàn bộ nhóm này. Seller BANNED chỉ có ngoại lệ xử lý đơn tồn tại UC-D02 và thông tin/rút tiền nghĩa vụ tại UC-D05 theo UC-E03, không quản lý sản phẩm/tồn kho hoặc thống kê chung. Mọi truy cập phải đúng seller_id của mình; hệ thống vẫn xử lý các hiệu ứng kho/tiền hợp lệ khi tài khoản bị cấm.

#### UC-D01 | Quản lý sản phẩm

*Vai trò: Người bán · Ưu tiên: M*

**Mục tiêu:** Người bán tạo và cập nhật sản phẩm thuộc gian hàng của mình.

**Điều kiện:** Người bán đã được duyệt và tài khoản đang hoạt động.

**LUỒNG CHÍNH**

1. Người bán mở “Sản phẩm của tôi”, chọn thêm mới hoặc một sản phẩm hiện có.

2. Người bán nhập tên, giá, mô tả tối đa 5.000 ký tự, một danh mục, tồn kho và 1–9 tệp, trong đó tối đa 2 video (P-08.3/P-08.5). Dung lượng, định dạng, thời lượng giữ trạng thái riêng theo P-08, không coi số lượng đã chốt là chốt các giới hạn đó.

3. Hệ thống kiểm tra các thông tin bắt buộc, giá và số lượng hợp lệ, định dạng/giới hạn tệp.

4. Tạo mới: sau validation, Product ghi HIDDEN cùng công việc provision bền vững; sau commit gọi `POST /internal/v1/inventory`. Order provision idempotent theo productId. Chỉ sau xác nhận Inventory và Seller/Product còn hợp lệ mới áp dụng trạng thái Seller yêu cầu. Cập nhật: kiểm tra ownership, trạng thái và cập nhật sản phẩm hiện có theo quyền hiện hành; không tạo Product mới.

**LUỒNG THAY THẾ / NGOẠI LỆ**

* Sản phẩm đã bị quản trị viên gỡ: người bán xem lý do nhưng không được sửa hoặc bật bán lại.

* Giá thay đổi: chỉ áp dụng cho lượt mua mới, không làm thay đổi giá trong các đơn trước đó.

* Người bán đặt tồn kho thấp hơn số hàng đang được giữ cho khách: hệ thống từ chối và cho biết số lượng tối thiểu có thể nhập.

* Validation/media lỗi hoặc tải lên bị hủy trước chấp nhận tạo: không lưu Product dở dang. Product hợp lệ HIDDEN chờ Inventory không thuộc trường hợp này. Timeout giữ HIDDEN, retry cùng productId và Idempotency-Key, không tạo Product khác hoặc rollback giả xuyên DB. Seller bị ban/Product REMOVED trong lúc chờ thì provision xong không tự bật SELLING. Dùng cơ chế hiện có tại [SAD §5.7/A.6](software-architecture.md), không thêm trạng thái, endpoint hay job provision.

* Sản phẩm bị ẩn trong khi có trong giỏ khách: giỏ được cập nhật khi khách mở lại.

**Kết quả mong đợi:** Người bán chỉ quản lý sản phẩm của mình; sản phẩm phải có ít nhất một ảnh hoặc video, còn video hiển thị được trên trình duyệt theo P-08.

#### UC-D02 | Chuẩn bị, xác nhận giao hoặc từ chối giao đơn

*Vai trò: Người bán · Ưu tiên: M (bao gồm từ chối giao hàng và xử lý quá hạn)*

**Mục tiêu:** Người bán xử lý các đơn của cửa hàng mình và bắt buộc chọn giao hoặc từ chối giao trong 7 ngày.

**Điều kiện:** Seller APPROVED có đơn đã thanh toán; BANNED xử lý đơn tồn gắn nghĩa vụ theo UC-E03 kể cả hiệu ứng tới muộn, không nhận đơn mới.

**LUỒNG CHÍNH**

1. Seller mở “Đơn cần xử lý”, chỉ xem đơn của cửa hàng mình. Seller BANNED chỉ thấy đơn tồn và thông tin tối thiểu gắn nghĩa vụ hiện tại, kể cả hiệu ứng đến muộn; không nhận đơn mới. Nghĩa vụ chỉ Payment/Refund/Finance không tạo quyền thao tác đơn ngoài state machine (UC-E03).

2. Người bán bắt đầu chuẩn bị hàng; hệ thống chuyển đơn sang “Đang đóng gói”.

3. Trong tối đa 7 ngày từ thời điểm thanh toán thành công, Seller phải chọn một trong hai hành động: “Xác nhận đã giao” hoặc “Từ chối giao hàng”.

4. Nếu Seller xác nhận đã giao, hệ thống chuyển đơn thẳng sang “Đã giao” và hiển thị cho khách “Chờ xác nhận”; không tồn tại bước “Đang giao”.

5. Nếu Seller từ chối, phải chọn DEFECTIVE, OUT_OF_STOCK_ACTUAL, CANNOT_DELIVER hoặc OTHER (P-21b) và luôn nhập ghi chú không rỗng, tối đa 500 ký tự; hệ thống hủy đơn, hoàn kho, chuyển Settlement sang chờ\_hoàn và khởi tạo hoàn đúng tổng đơn (pending chỉ giảm khi Refund SUCCESS).

**LUỒNG THAY THẾ / NGOẠI LỆ**

* Seller không xác nhận đã giao và cũng không từ chối trong 7 ngày kể từ khi thanh toán thành công: hệ thống tự hủy đơn và hoàn tiền (J4, Must).

* Seller từ chối khi hàng lỗi/hỏng/không đủ điều kiện: phải có lý do; không cần khách xác nhận để kích hoạt hủy/hoàn tiền.

* Đơn đã xác nhận “Đã giao”: Seller không được từ chối/hủy lại; chờ khách xác nhận hoặc J3 tự xác nhận sau 7 ngày kể từ lúc giao.

* Tác vụ quá hạn trùng với Seller bấm giao/từ chối: chỉ một kết quả được ghi nhận, không hoàn hai lần.

* Seller không thấy và không thao tác được đơn của cửa hàng khác, kể cả đơn được thanh toán cùng lần với đơn của mình.

**Kết quả mong đợi:** Từ chối giao hàng là chức năng Must; quá 7 ngày không trả lời cũng hủy/hoàn tiền bắt buộc. Mọi tác động chỉ nằm trong đơn của Seller đó.

#### UC-D03 | Cập nhật tồn kho

*Vai trò: Người bán, Hệ thống · Ưu tiên: M*

**Mục tiêu:** Người bán theo dõi và điều chỉnh số lượng hàng hiện có.

**Điều kiện:** Người bán đang hoạt động và chọn sản phẩm thuộc mình.

**LUỒNG CHÍNH**

1. Người bán mở “Tồn kho”, xem tồn thực, lượng đang giữ và tồn khả dụng; cảnh báo thấp khi tồn khả dụng ≤5 sản phẩm (P-16).

2. Khi nhập hàng hoặc kiểm kê, người bán nhập số lượng tồn kho mới.

3. Hệ thống kiểm tra số lượng hợp lệ và không thấp hơn lượng hàng đã giữ cho khách.

4. Hệ thống lưu số lượng mới và ghi nhận lịch sử điều chỉnh.

5. Khi thanh toán chung thành công, hệ thống trừ kho theo từng đơn; nếu một đơn hủy hợp lệ, chỉ hoàn lại hàng của đơn đó.

**LUỒNG THAY THẾ / NGOẠI LỆ**

* Nhập số âm, số lẻ hoặc nhỏ hơn lượng đang giữ: hệ thống từ chối.

* Có thay đổi kho từ thiết bị khác trong khi người bán đang sửa: hệ thống yêu cầu tải lại trước khi lưu.

**Kết quả mong đợi:** Tồn kho không âm, có lịch sử mọi biến động và không bị trừ/hoàn lặp cho cùng giao dịch.

#### UC-D04 | Xem kết quả kinh doanh và khoản được nhận

*Vai trò: Người bán · Ưu tiên: M*

**Mục tiêu:** Người bán theo dõi kết quả bán hàng và tình trạng tiền theo các đơn của mình.

**Điều kiện:** ACTIVE dùng thống kê; BANNED không thống kê chung, chỉ thông tin nghĩa vụ đơn/tiền gồm pending theo UC-E03.

**LUỒNG CHÍNH**

1. Người bán mở “Thống kê”, chọn khoảng thời gian.

2. Hệ thống tính số đơn mới đã được thanh toán, giá trị các đơn đã\_xác\_nhận, số đơn bị hủy và các sản phẩm bán chạy của cửa hàng.

3. Hệ thống hiển thị theo từng đơn: tổng tiền đơn, hoa hồng, khoản người bán được nhận và tình trạng tiền (đang chờ quyết toán / đã vào số dư khả dụng / đã hoàn khách).

4. Hệ thống hiển thị tóm tắt ví (số dư chờ, khả dụng, đã rút) và liên kết tới “Ví & rút tiền” (UC-D05).

**LUỒNG THAY THẾ / NGOẠI LỆ**

* Một lần thanh toán chung bao gồm đơn của nhiều Seller: chỉ tính Order.tổng\_tiền của Seller đang xem, không tính toàn bộ Payment như doanh số của Seller.

* Đơn bị hủy: không tính doanh số hoàn thành/hoa hồng, hiển thị tiền đã hoặc đang hoàn khách; không làm giảm số dư khả dụng.

* Kỳ không giao dịch hiển thị 0\.

**Kết quả mong đợi:** Σ khoản Seller được nhận của đơn đã quyết toán \= khả dụng \+ đã rút (WD-11). P-14 dùng GMT+7, tuần bắt đầu thứ Hai, quý theo lịch; P-15 xét bán chạy theo số lượng, chỉ sản phẩm trên 500 lượt bán (đúng 500 chưa đạt), không tự đặt thêm kỳ/trạng thái đơn được tính. Các ngưỡng không mở rộng phạm vi Reporting đã hoãn.

#### UC-D05 | Ví người bán và rút tiền (giả lập)

*Vai trò: Người bán · Ưu tiên: M · Mới ở v5.2*

**Mục tiêu:** Người bán xem số dư ví giả lập và rút ngay phần tiền đã được quyết toán, trong khi tiền của các đơn còn có thể bị hủy/hoàn vẫn được nền tảng giữ lại.

**Điều kiện:** Seller APPROVED, phiên hợp lệ UC-E03; xem ví theo allowlist; chỉ rút available đáp ứng công thức dưới đây, không pending.

**LUỒNG CHÍNH**

1. Seller mở “Ví & rút tiền”. Hệ thống hiển thị ba số dư kèm nhãn “Giả lập — không phát sinh tiền thật”: **Chờ quyết toán** (giải thích: tiền của đơn chưa được khách xác nhận nhận hàng, gồm cả phần của đơn đã hủy đang chờ hoàn cho khách; chưa thể rút), **Khả dụng** (có thể rút) và **Đã rút**.

2. Hệ thống hiển thị lịch sử biến động ví (bút toán) theo thời gian, mỗi dòng gắn đơn liên quan hoặc mã giao dịch rút; Seller mở số dư chờ để xem danh sách đơn đang chờ quyết toán và thời điểm dự kiến tự xác nhận (nếu đơn đã giao); phần của đơn đã hủy đang chờ hoàn được hiển thị tách riêng với nhãn “Chờ hoàn cho khách”.

3. Seller chọn “Rút tiền” và nhập số tiền A (mặc định đề xuất toàn bộ số dư khả dụng). Đích nhận cố định là “Ví ngoài giả lập”; không nhập thông tin ngân hàng.

4. `amountVnd` nguyên dương, ≤ available, và thỏa `(amountVnd >= 10000) || (accountStatus == BANNED && 0 < availableBalanceVnd < 10000 && amountVnd == availableBalanceVnd)`. Kiểm tra lại quyền/số dư trong transaction hiện hành; concurrency/idempotency và một atomic debit + ledger + Withdrawal giữ nguyên. Wallet giữ `minWithdrawalVnd=10000`, thêm `allowBelowMinimumFullWithdrawal: boolean`; ngoại lệ thỏa thì `canWithdraw=true`, `withdrawDisabledReason=null`, UI khóa amount bằng toàn bộ available. Rút một phần dưới ngưỡng trả 422 `WITHDRAWAL_BELOW_MINIMUM`; không thêm trạng thái Withdrawal.

5. Seller xác nhận; trong một transaction hệ thống trừ A khỏi số dư khả dụng, cộng A vào tổng đã rút, tạo Withdrawal và bút toán WITHDRAWAL\_COMPLETED (WD-04).

6. Hệ thống hiển thị ngay “Rút tiền thành công” với mã giao dịch, số tiền và số dư khả dụng còn lại, đồng thời gửi biên nhận (UC-G01). Không có bước chờ duyệt hay chờ xử lý.

**LUỒNG THAY THẾ / NGOẠI LỆ**

* Available=0: không rút; chỉ pending thì xem tiến độ, không rút pending.
* ACTIVE available<10.000: khóa rút dù có/không pending. BANNED available dương dưới ngưỡng: chỉ rút toàn bộ.
* Một phần dưới ngưỡng → 422 WITHDRAWAL_BELOW_MINIMUM; vượt available/không nguyên dương → lỗi, không Withdrawal, số dư không đổi.

* Hai tab/hai thiết bị rút cùng lúc: các lần rút được xử lý lần lượt; lần nào làm tổng vượt số dư khả dụng thì bị từ chối với thông báo “Số dư không đủ”.

* Bấm “Xác nhận” lặp hoặc mạng gián đoạn: cùng idempotency\_key trả lại đúng giao dịch đã thực hiện, không trừ ví lần hai; key cũ với số tiền khác → 409\.

* Lỗi hệ thống giữa chừng: transaction rollback, số dư không đổi; Seller thử lại.

* Khách hủy một đơn khác của Seller ngay sau khi Seller rút hết: khi hoàn tiền thành công, hệ thống trừ số dư chờ của đúng đơn bị hủy; số dư khả dụng và tổng đã rút không đổi (WD-06).

* BANNED không đóng băng available, được rút theo công thức trên. Pending/Settlement chưa apply vẫn giữ nghĩa vụ; rút hết và hết mọi nghĩa vụ thì J7 chuyển DISABLED.

* Phát hiện lệch giữa số dư và tổng bút toán: hệ thống chặn rút và đánh dấu cần xử lý thủ công cho Admin.

**Kết quả mong đợi: Seller chỉ rút tiền đã quyết toán thành khả dụng; Seller bị cấm mà được đăng nhập vẫn được rút khả dụng. Mỗi giao dịch rút thành công tức thời hoặc bị từ chối; tổng tiền đã rút không vượt số dư khả dụng từng thời điểm, hoàn tiền không làm ví âm.**

**Lưu ý nghiệp vụ:** MVP rút theo số tiền (không theo từng đơn), không cần Admin duyệt, không hủy hay hoàn tác giao dịch rút đã thực hiện, không giới hạn số lần rút trong ngày.

### Nhóm E — Administrator

#### UC-E01 | Quản lý người dùng

*Vai trò: Quản trị viên · Ưu tiên: M*

**Mục tiêu:** Quản trị viên tra cứu khách hàng, người bán và trạng thái tài khoản.

**Điều kiện:** Quản trị viên đã đăng nhập.

**LUỒNG CHÍNH**

1. Quản trị viên mở mục “Người dùng”.

2. Hệ thống liệt kê các tài khoản Customer và Seller độc lập, hỗ trợ tìm theo tên/email/SĐT/trạng thái. Một người có thể xuất hiện ở hai danh sách bằng hai tài khoản riêng, không phải một tài khoản có hai vai trò.

3. Admin chọn tài khoản để xem hồ sơ, số đơn, ví Seller, `accountStatus`, `accessMode`, lý do cấm, `bannedAt`, `disabledAt`, `restoreUntil` và `banImpact` theo UC-E03. Lịch sử cấm/gỡ cấm lưu trong AdminAuditLog nội bộ, không có màn xem nhật ký.

4. Từ hồ sơ, quản trị viên có thể mở chức năng duyệt người bán hoặc cấm/gỡ cấm tài khoản.

**LUỒNG THAY THẾ / NGOẠI LỆ**

* Danh sách không có kết quả: hệ thống hiển thị trạng thái trống.

* Tài khoản quản trị viên không xuất hiện trong danh sách có thể thao tác khóa.

**Kết quả mong đợi:** Quản trị viên chỉ xem chi tiết hồ sơ, không tạo/sửa trực tiếp hồ sơ của khách và người bán; các tác vụ quản trị có use case riêng.

#### UC-E02 | Báo cáo tổng quan

*Vai trò: Quản trị viên · Ưu tiên: M*

**Mục tiêu:** Quản trị viên giám sát tiền thu chung, các ngăn tiền đang giữ, hoa hồng và tiến độ xử lý từng đơn.

**Điều kiện:** Quản trị viên đã đăng nhập.

**LUỒNG CHÍNH**

1. Quản trị viên mở “Tổng quan” và chọn khoảng thời gian.

2. Hệ thống hiển thị tổng tiền thu từ các lần thanh toán, số đã hoàn và đang chờ hoàn, tổng số dư chờ quyết toán, hoa hồng đã chốt (ví nền tảng), tổng số dư khả dụng của các Seller và tổng đã rút.

3. Hệ thống hiển thị kết quả kiểm tra bảo toàn tiền (Database RBAC v5.0 mục 8.3) tại thời điểm xem; lệch thì cảnh báo.

4. Quản trị viên xem tiến độ các đơn; có thể nhóm các đơn theo phiên thanh toán chung để đối chiếu.

5. Hệ thống hiển thị hàng chờ xử lý: đơn có Seller quá 7 ngày chưa quyết định giao/từ chối, đơn khách quá 7 ngày chưa xác nhận, hoàn tiền lỗi và các phiên cần đối soát.

**LUỒNG THAY THẾ / NGOẠI LỆ**

* Không có dữ liệu: hiển thị 0\.

* Một Payment chung không được đếm lặp cho mỗi đơn/Seller; số hoàn tính theo kết quả thực tế của từng đơn; số đã rút tính theo các giao dịch Withdrawal.

* Theo dõi Seller BANNED và nghĩa vụ pending/available/Settlement; không đóng băng available vì ban. Nguồn lỗi không hiển thị zero giả.

**Kết quả mong đợi:** Phản ánh được thu một lần, hoàn theo đơn, hoa hồng theo đơn, rút tiền theo từng giao dịch; số liệu khớp bất biến tiền ở Database RBAC v5.0 mục 8.3 và WD-11.

#### UC-E03 | Cấm và gỡ cấm tài khoản

*Vai trò: Quản trị viên · Ưu tiên: M*

**Authority:** [Thẻ 15](coverage.md#the-15), quyết định 17/11/2026; thay thế policy cấm theo phạm vi và snapshot lịch sử. Đặc tả không chứng minh runtime đã triển khai.

**Điều kiện:** Admin có quyền `account:moderate`; không cấm ADMIN, chỉ cấm Seller `APPROVED`. Tài khoản có `accountType` cố định; không chọn scope, không tác động tài khoản khác của cùng người, không có khóa khẩn cấp vượt nghĩa vụ.

**LUỒNG CHÍNH**

1. Admin mở detail Customer/Seller. `banImpact = {evaluatedAt,resultingAccountStatus,obligations}` cảnh báo snapshot; `obligations = {hasOpenOrders,hasPendingPayment,hasUnresolvedRefund,hasPendingFinance,pendingBalanceVnd,availableBalanceVnd}`. Customer có hai số dư bằng 0 và hasPendingFinance=false. Nguồn lỗi không hiển thị zero giả, không cho xác nhận.
2. Popup hiển thị loại tài khoản, nghĩa vụ, pending/available Seller, trạng thái dự kiến và quyền còn lại. P-21d chọn Gian lận/lừa đảo; Kinh doanh hàng cấm; Kinh doanh hàng giả/vi phạm sở hữu trí tuệ; Khác. Lý do kinh doanh chỉ áp dụng Seller; Khác bắt buộc mô tả không rỗng sau trim. Gửi/lưu reason dạng nhãn hoặc “Nhãn: mô tả”, tổng 1–1.000 ký tự, không áp giới hạn 500 của ghi chú hủy đơn. Hai nút “Hủy” / “Xác nhận cấm”; BANNED chưa bắt đầu thời hạn, DISABLED giải thích 30 ngày từ chuyển trạng thái.
3. `POST /identity/admin/ban/{userId}` body `{reason}` đọc lại nghĩa vụ trước commit, trả trạng thái thực tế kể cả khác preview. Không có nghĩa vụ đã được tất cả nguồn xác minh → DISABLED, thu hồi phiên; còn nghĩa vụ → BANNED với allowlist dưới đây. Dependency lỗi → 503, không ghi ban một phần. Request gửi `scope` → 400, không alias.
4. Identity là owner duy nhất cập nhật cặp trạng thái, `authzVersion`, audit và Outbox trong cùng local transaction. `accountStatus` suy ra, không thêm cột: ACTIVE = users.ACTIVE + hồ sơ.ACTIVE; BANNED = users.ACTIVE + hồ sơ.BANNED; DISABLED = users.DISABLED + hồ sơ.BANNED. Seller.approval_status riêng, PENDING/REJECTED vẫn không login.
5. Đầu đợt cấm ghi `banned_at`; lần đầu DISABLED ghi `disabled_at`, `restore_until = disabled_at + 30 ngày` UTC. Ban/job lặp không đổi mốc hoặc phát duplicate. `disabled_reason` và `ban_reason` là lý do cùng đợt cấm, không phải hai lệnh khóa độc lập.
6. Admin xem `accountStatus`, mốc/deadline và chọn “Gỡ cấm”. `POST /identity/admin/unban/{userId}` body `{reason?: string <=1000}`: BANNED được gỡ bất kỳ lúc nào; DISABLED được gỡ khi `now <= restore_until`, quá hạn → 409 `ACCOUNT_RESTORE_WINDOW_EXPIRED`. UI khóa nút quá hạn kèm lý do. ACTIVE trả thành công idempotent. Unban hợp lệ không cần nguồn Order/Finance, vẫn kiểm tra tài khoản/Seller và local transaction.
7. Unban thành công đưa ACTIVE, xóa mốc hiệu lực và lý do hiện tại, giữ audit; đợt cấm mới tạo mốc mới. Không phục hồi đơn hủy, Product HIDDEN/REMOVED hoặc quyền Seller chưa duyệt. Quá hạn không xóa tài khoản, dữ liệu, tiền hoặc đổi đơn.

**Contract phản hồi và phiên:** Hai lệnh ban/unban trả 200 trong envelope `data`: `{userId,accountType,accountStatus,userStatus,customerStatus|null,sellerStatus|null,accessMode,bannedAt|null,disabledAt|null,restoreUntil|null,authzVersion}`; ban trả thêm `obligations`. Giữ lỗi 401/403/404; Seller chưa duyệt trả 409 `INVALID_STATE_TRANSITION`. Admin detail và auth/self/authorization-context cũng cung cấp trạng thái/mốc phù hợp, không chỉ response ban; login/refresh thu hồi hoặc cấp phiên theo quyền hiện hành. Ban lặp không tạo sự kiện hoặc đợt cấm mới.

**Nghĩa vụ hiện tại và quyền**

* Order chưa kết thúc: `AWAITING_PAYMENT`, `PAID`, `PACKING`, `DELIVERED`. `CONFIRMED`, `CANCELLED`, `PAYMENT_FAILED`, `EXPIRED` không tự giữ phiên; lịch sử từng trả tiền không cấp quyền.
* Payment PENDING, SUCCESS chưa apply xong vào Order, thu muộn chưa hoàn xong; Refund PENDING hoặc FAILED chưa giải quyết vẫn là nghĩa vụ, kể cả Order CANCELLED/phiên đã đóng hoặc FULL_CHECKOUT.
* Seller thêm Settlement PENDING/PENDING_REFUND, hiệu ứng tài chính chưa apply (kể cả giá trị 0), `pendingBalanceVnd > 0` hoặc `availableBalanceVnd > 0`. Không chỉ kiểm tra tổng tiền. Timeout, thiếu dữ liệu, 404 tài khoản/ví đáng lẽ tồn tại hoặc dữ liệu không nhất quán không chứng minh hết nghĩa vụ.
* BANNED Customer chỉ xem đơn/phiên và Payment/Refund gắn nghĩa vụ đúng chủ; hủy/xác nhận theo state machine. Refund FAILED hết lượt cho Customer chủ Payment mở chu kỳ qua refund:retry:own, kể cả FULL_CHECKOUT khi phiên đã đóng. Không giỏ, checkout, thu mới, review hoặc lịch sử ngoài nghĩa vụ; AWAITING_PAYMENT không thanh toán, J1/J2/J2b tiếp tục. Public browsing không mở quyền mua authenticated.
* BANNED Seller chỉ xử lý đơn tồn đúng cửa hàng và thông tin tiền/biên nhận cần giải quyết nghĩa vụ; không sản phẩm, tồn kho, nhận đơn mới hoặc thống kê chung. Chỉ pending: xem tiến độ, không rút pending; chỉ available: ví/rút và biên nhận liên quan.
* `accessMode`: ACTIVE → FULL; Customer có đơn hoặc Refund cần Customer retry → OPEN_ORDERS_ONLY; Seller có đơn và available → ORDERS_AND_WITHDRAWAL, chỉ đơn → OPEN_ORDERS_ONLY, chỉ available → WITHDRAWAL_ONLY; chỉ nghĩa vụ hệ thống/pending không có thao tác đơn/rút/retry → OBLIGATIONS_READ_ONLY; DISABLED → LOGIN_BLOCKED. Không là trạng thái cấm mới. Mỗi request kiểm tra allowlist/ownership/state; login/refresh dùng quyền hiện hành, DISABLED kiểm tra nghĩa vụ muộn trước ACCOUNT_DISABLED; ngoài allowlist trả ACCOUNT_BANNED.

**Đối soát và sự kiện**

* Identity lấy `GET /internal/v1/orders/accounts/{userId}/obligations?accountType=CUSTOMER|SELLER`: Order tổng hợp Order/CheckoutSession đúng chủ và xác minh Payment/Refund qua API, không join DB; kết quả `{hasOpenOrders,openOrderIds,hasPendingPayment,hasUnresolvedRefund,evaluatedAt,orderStateVersion}`. Seller thêm `GET /internal/v1/finance/sellers/{sellerId}/obligations` → `{hasPendingFinance,pendingBalanceVnd,availableBalanceVnd,evaluatedAt,financeStateVersion}`. Đây là contract đặc tả mới, không tuyên bố endpoint code có sẵn.
* J7 do Identity định kỳ đối soát cả BANNED/DISABLED: hết nghĩa vụ đã xác minh → DISABLED; nghĩa vụ đến muộn → BANNED giới hạn, không tự unban, không chuyển ACTIVE. Login/refresh cũng kiểm tra nghĩa vụ muộn trước từ chối DISABLED. Cùng đợt cấm giữ disabled_at/restore_until kể cả DISABLED→BANNED→DISABLED; không gia hạn. Không đặt chu kỳ/SLA mới.
* HTTP liên service ngoài transaction giữ khóa. Trước commit kiểm tra version/đợt cấm còn hiện hành để không ghi đè Admin unban. Nguồn lỗi giữ trạng thái, retry bền vững; request cần xác minh trả 503, không mở rộng quyền. Snapshot nhiều service không nguyên tử; hiệu ứng đến trễ phải được giữ bền vững, không mất tiền.
* Tái sử dụng `customer.banned/unbanned`, `seller.banned/unbanned`, `user.disabled/enabled`, envelope/version/dedupe; payload liên quan thêm `accountStatus,accessMode,bannedAt,disabledAt,restoreUntil`. `user.enabled` do nghĩa vụ muộn mang BANNED, không phải unban; chỉ lệnh unban phát *.unbanned. Không tự hủy Order hoặc đóng băng available; Payment/Refund/jobs tiếp tục. Notification theo User/accountType độc lập, phân biệt quyền giới hạn với khôi phục toàn bộ và nêu deadline khi có.

**Kết quả:** bảo toàn nghĩa vụ và tiền; xem [API Identity](api_specs.md), [Database §3.1, §8.2D](database.md) cho contract hiệu lực.

#### UC-E04 | Gỡ / khôi phục sản phẩm

*Vai trò: Quản trị viên · Ưu tiên: M*

**Mục tiêu:** Quản trị viên gỡ sản phẩm vi phạm hoặc khôi phục khi còn thời hạn.

**Điều kiện:** Quản trị viên đã đăng nhập và chọn một sản phẩm.

**LUỒNG CHÍNH**

1. Quản trị viên chọn “Gỡ sản phẩm”, chọn lý do P-21d: Sản phẩm bị cấm; Hàng giả/vi phạm sở hữu trí tuệ; Nội dung sai lệch/không phù hợp; Đăng trùng lặp/spam; Khác. Gửi reason dạng nhãn hoặc “Nhãn: mô tả”, tổng 1–1.000 ký tự; Khác phải có mô tả không rỗng sau trim, rồi xác nhận.

2. Hệ thống ngừng hiển thị sản phẩm cho khách, thông báo lý do cho người bán và lưu lịch sử xử lý.

3. Các giỏ chứa sản phẩm được cập nhật khi khách mở lại; các phiên checkout chưa thanh toán bị kiểm tra lại trước khi thu tiền và không được tiếp tục mua sản phẩm đã gỡ.

4. Nếu đủ điều kiện, quản trị viên chọn “Khôi phục”; hệ thống đưa sản phẩm về trạng thái “Tạm ẩn” để người bán tự bật bán lại.

**LUỒNG THAY THẾ / NGOẠI LỆ**

* Sản phẩm đã bị gỡ: thao tác gỡ thêm không tạo hiệu ứng mới.

* Quá 30 ngày kể từ khi gỡ: không cho khôi phục; lịch sử sản phẩm vẫn được lưu.

* Đơn đã thanh toán trước khi sản phẩm bị gỡ: không tự động thay đổi tiến độ đơn; lịch sử giữ thông tin sản phẩm.

**Kết quả mong đợi:** Việc gỡ có hiệu lực đối với lượt mua mới ngay lập tức; người bán không thể tự khôi phục sản phẩm bị gỡ. Việc phát hiện vi phạm do Admin chủ động rà soát (không có kênh báo cáo từ Customer từ v5.2).

#### UC-E05 | Xử lý báo cáo vi phạm — ĐÃ LOẠI BỎ (v5.2)

Do UC-C02 bị loại bỏ, Admin không còn danh sách “Báo cáo vi phạm” để xử lý. Mã UC-E05 được giữ chỗ, không dùng lại. Các hành động cưỡng chế vẫn thực hiện trực tiếp qua UC-E03 (cấm tài khoản) và UC-E04 (gỡ sản phẩm).

#### UC-E06 | Duyệt Seller mới đăng ký

*Vai trò: Quản trị viên · Ưu tiên: M*

**Mục tiêu:** Quản trị viên xét duyệt hồ sơ người bán mới.

**Điều kiện:** Có hồ sơ ở trạng thái “Chờ duyệt”.

**LUỒNG CHÍNH**

1. Quản trị viên vào danh sách “Người bán chờ duyệt”.

2. Hệ thống hiển thị hồ sơ đăng ký: tên cửa hàng, email, số điện thoại, mô tả và ngày nộp.

3. Quản trị viên chọn “Duyệt” hoặc “Từ chối”. Từ chối chọn lý do P-21d: Thông tin đăng ký không chính xác/không nhất quán; Thông tin gian hàng không phù hợp; Khác. Gửi reason dạng nhãn hoặc “Nhãn: mô tả”, tổng 1–1.000 ký tự; Khác bắt buộc mô tả không rỗng sau trim. Chỉ xét hồ sơ hiện có, không thêm KYC/giấy phép.

4. Hệ thống ghi nhận quyết định, gửi email; khi duyệt, kích hoạt quyền bán trên tài khoản Seller riêng và tạo ví giả lập của Seller với số dư ban đầu bằng 0\.

**LUỒNG THAY THẾ / NGOẠI LỆ**

* Hồ sơ đã được người khác xử lý: hệ thống thông báo đã xử lý, không nhận quyết định thứ hai.

* Hồ sơ bị từ chối: không cấp quyền đăng nhập hoặc bán; gửi lý do qua email. Muốn đăng ký lại phải gửi hồ sơ mới và xác minh email qua UC-A02; không nộp lại hồ sơ cũ.

* Chỉ Seller đã được duyệt mới có thể bị cấm; không tồn tại trường hợp duyệt hồ sơ Seller đang bị cấm khi hồ sơ chưa duyệt.

* Do các vai trò dùng tài khoản độc lập, Administrator không có hồ sơ Seller trên chính tài khoản Administrator; việc duyệt thực hiện bằng tài khoản Admin riêng (quyết định conflict số 11 được giải quyết theo số 6).

**Kết quả mong đợi: chỉ Seller được duyệt mới được bán và hiển thị công khai; Seller chờ duyệt/bị từ chối không đăng nhập, sau từ chối muốn đăng ký lại phải nộp hồ sơ mới.**

#### UC-E07 | Quản lý danh mục

*Vai trò: Quản trị viên · Ưu tiên: S*

**Mục tiêu:** Quản trị viên duy trì danh sách danh mục sản phẩm.

**Điều kiện:** Đã đăng nhập quản trị; tính năng thuộc mức Should.

**LUỒNG CHÍNH**

1. Quản trị viên vào “Danh mục sản phẩm”.

2. Hệ thống hiển thị các danh mục một cấp hiện có.

3. Quản trị viên thêm, đổi tên hoặc ẩn/hiện một danh mục.

4. Hệ thống kiểm tra tên không trùng và cập nhật danh mục.

**LUỒNG THAY THẾ / NGOẠI LỆ**

* Tên danh mục trùng: báo lỗi, không lưu.

* Danh mục bị ẩn: không cho chọn khi tạo/sửa sản phẩm mới nhưng sản phẩm đã gắn trước đó vẫn được hiển thị.

* Danh mục đã có sản phẩm: không cho xóa.

**Kết quả mong đợi:** Mỗi sản phẩm thuộc đúng một ngành hàng; bắt buộc seed 10 ngành hàng P-21e cùng ví dụ tại [parameters.md](parameters.md#danh-mục-ban-đầu--p-21e), kể cả chưa triển khai màn quản trị Should. categoryId là UUID, số 01–10 chỉ thứ tự; Category không có tỷ lệ hoa hồng riêng.

#### UC-E08 | Xem đơn và xử lý khoản tiền gặp sự cố

*Vai trò: Quản trị viên · Ưu tiên: S (mở rộng) / M (theo dõi khoản tiền gặp sự cố)*

**Mục tiêu:** Quản trị viên đọc tiến độ thanh toán/hoàn tiền; xem ví và lịch sử rút tiền Seller. Customer chủ Payment mới được mở chu kỳ hoàn mới theo UC-G02; Admin không xử lý thay khách.

**Điều kiện:** Có quyền Admin; phát sinh khoản hoàn lỗi/chờ lâu hoặc cần đối chiếu ví.

**LUỒNG CHÍNH**

1. Quản trị viên mở danh sách việc cần xử lý và lọc theo đơn, phiên thanh toán, mã giao dịch rút hoặc cửa hàng.

2. Hệ thống hiển thị Payment chung của lần checkout và các khoản hoàn/hoa hồng của từng đơn; với Seller: số dư ví, các bút toán và giao dịch rút (chỉ xem).

3. Quản trị viên xem trạng thái đã lưu, lịch sử tra cứu/thực hiện của Refund ORDER hoặc FULL_CHECKOUT; J5 sở hữu query/retry. Admin không gọi gateway, không có endpoint query/retry Refund dành Admin.

4. Admin chỉ ghi chú/gỡ cờ đối soát khoản thu của CheckoutSession, có audit; không đổi cờ chờ Customer hoặc nghĩa vụ Refund. Chỉ kết quả SUCCESS từ gateway mới dẫn tới refund.succeeded, Settlement REFUNDED và REFUND_DEBIT.

**LUỒNG THAY THẾ / NGOẠI LỆ**

* Một Payment có nhiều khoản hoàn của các đơn khác nhau: không cộng các khoản vượt tổng Payment; không hoàn trùng một đơn.

* BANNED tự rút available theo UC-D05, không đợi unban; Admin không rút thay.

* Không được tự nhập tay trạng thái đã hoàn, không sửa số dư ví, không tạo/hủy giao dịch rút hay bút toán thủ công.

* Lệch giữa số dư ví và tổng bút toán: chỉ đánh dấu và tra cứu; điều chỉnh sổ nằm ngoài phạm vi MVP.

**Kết quả mong đợi:** Admin theo dõi khoản tiền gặp sự cố nhưng không khởi động hoàn thay Customer. Rút tiền tức thời không có lệnh rút lỗi cần duyệt; không có thao tác đánh dấu Refund đã xử lý để bỏ nghĩa vụ.

### Nhóm F — Tính năng nâng cao

#### UC-F01 | Gợi ý sản phẩm liên quan

*Vai trò: Khách hàng · Ưu tiên: C*

**Mục tiêu:** Khách khám phá các mặt hàng phù hợp với sản phẩm đang xem.

**Điều kiện:** Khách đang xem chi tiết sản phẩm; tính năng thuộc mức Could.

**LUỒNG CHÍNH**

1. Khách mở trang sản phẩm.

2. Hệ thống gợi ý các mặt hàng liên quan dựa trên quy tắc đơn giản như cùng danh mục hoặc thường mua cùng nhau.

3. Khách chọn sản phẩm gợi ý để xem chi tiết.

**LUỒNG THAY THẾ / NGOẠI LỆ**

* Có ít sản phẩm đủ điều kiện: hiển thị ít gợi ý hơn hoặc ẩn khu vực gợi ý.

**Kết quả mong đợi:** Không gợi ý sản phẩm hiện tại, sản phẩm hết hàng hoặc đã ngừng bán; có thể gợi ý sản phẩm của người bán khác.

### Nhóm G — Tác vụ hệ thống

#### UC-G01 | Thông báo

*Vai trò: Hệ thống → Khách hàng, Người bán · Ưu tiên: M (in-app); email nghiệp vụ tùy chọn, triển khai sau theo [quyết định 25/11/2026](coverage.md#notification-channels). Không thay luồng email xác minh/đặt lại mật khẩu của Identity.*

**Mục tiêu:** Thông báo về lần thanh toán chung, các sự kiện riêng của từng đơn và các sự kiện ví/rút tiền của Seller.

**Điều kiện:** Có một sự kiện nghiệp vụ ảnh hưởng khách hoặc Seller.

**LUỒNG CHÍNH**

1. Khi thanh toán chung thành công, hệ thống gửi khách một thông báo gồm phiên thanh toán, tổng tiền và danh sách đơn theo cửa hàng; mỗi Seller nhận thông báo đơn mới của mình.

2. Khi một đơn được xác nhận đã giao, bị từ chối, bị hủy, đã hoàn hoặc đã được xác nhận nhận, hệ thống gửi thông báo gắn đơn và tên cửa hàng; với đơn đã xác nhận, Seller được báo số tiền vừa chuyển sang số dư khả dụng.

3. Khi Seller quá 7 ngày chưa xác nhận giao/từ chối hoặc khách quá 7 ngày chưa xác nhận, hệ thống thông báo kết quả tự động của đơn.

4. Khi Seller rút tiền thành công, hệ thống gửi biên nhận gồm mã giao dịch rút, số tiền và số dư khả dụng còn lại.

5. In-app là Must, email nghiệp vụ không bắt buộc và triển khai sau. Người nhận xem danh sách/số chưa đọc, đánh dấu đã đọc từng bản/tất cả và xóa thông báo của mình. Khi một chu kỳ Refund hết lượt vẫn FAILED, gửi Customer chủ Payment thông báo in-app REFUND_RETRY_REQUIRED gồm số tiền, đơn hoặc phiên FULL_CHECKOUT và link “Thử lại hoàn tiền”. Dedupe theo refundId + retryCycle + người nhận + kênh; mỗi chu kỳ mới hết lượt có thông báo riêng. Link chỉ mở trang đăng nhập/đọc trạng thái/xác nhận, không tự gọi retry.

**LUỒNG THAY THẾ / NGOẠI LỆ**

* Khi triển khai email, giữ P-19: mỗi email có lần gửi đầu và tối đa 3 retry, chờ 1/5/15 phút từ lần thất bại trước. Chỉ retry lỗi tạm thời (timeout, mất kết nối, lỗi nhà cung cấp, rate limit), tôn trọng Retry-After. Lỗi vĩnh viễn hoặc token/link đã hết hạn dừng ngay; hết lượt ghi FAILED, không đổi trạng thái nghiệp vụ hay làm mất in-app. Dùng cùng thông báo và khóa chống trùng.

* P-22: in-app giữ nội dung 30 ngày, chỉ hiển thị tối đa 50 gần nhất của người nhận. Không xóa email/cắt lịch retry/làm mất dedupe; replay không tạo lại thông báo đã xóa/hết hạn hiển thị.

* Thông báo hoàn tiền, số dư ví và rút tiền có nhãn “Giả lập — không phát sinh tiền thật”.

**Kết quả mong đợi:** Khách phân biệt một lần thanh toán với từng đơn giao/hủy/hoàn tiền; Seller chỉ nhận thông báo về đơn và ví của mình.

#### UC-G02 | Hoàn tiền

*Vai trò: Hệ thống; Customer chủ Payment mở lại chu kỳ khi hết lượt · Ưu tiên: M*

**Mục tiêu:** Hoàn tiền giả lập theo từng đơn bị hủy, hoặc hoàn toàn bộ khi lần thanh toán chung không còn hợp lệ.

**Điều kiện:** Payment chung đã success; có Order bị hủy hợp lệ (chưa đã\_giao), hoặc phiên checkout đã đóng và phát sinh khoản thu muộn.

**LUỒNG CHÍNH**

1. Hệ thống xác định đơn bị hủy và tổng tiền của đơn đó.

2. Hệ thống chuyển Settlement sang chờ\_hoàn (chưa đổi số dư ví; phần Sᵢ, Cᵢ của đơn bị khóa trong số dư chờ, không thể chuyển sang khả dụng) và ghi nhận khoản tiền phải hoàn của đơn; chỉ hoàn kho số lượng thuộc đơn, không ảnh hưởng đơn khác.

3. Hệ thống gửi yêu cầu hoàn tiền tới bộ giả lập thanh toán nội bộ theo số tiền của đơn trên Payment chung, giữ nguyên mã tham chiếu cho những lần đối soát hoặc thử lại.

4. Khi hoàn tiền thành công, trong cùng một transaction hệ thống chuyển Refund sang thành công, Settlement sang đã\_hoàn và ghi bút toán REFUND\_DEBIT: số dư chờ ví Seller −Sᵢ, số dư chờ ví nền tảng −Cᵢ; sau đó hiển thị “Đã hoàn” trên đơn và thông báo cho khách; các đơn khác tiếp tục xử lý.

**LUỒNG THAY THẾ / NGOẠI LỆ**

* Seller bắt buộc được phép từ chối giao hàng với lý do; khi từ chối hoặc không xác nhận giao/từ chối trong 7 ngày, hệ thống hủy đơn và hoàn tiền đúng đơn đó.

* Khách hủy hợp lệ, Seller từ chối, hết hạn J4 hoặc Order đã chứng minh invalid giao dịch khi áp dụng Payment SUCCESS theo UC-B06: hoàn đúng Order đủ điều kiện, Payment giữ SUCCESS, pending chỉ giảm khi Refund SUCCESS. Ban sau thu tiền hoặc lỗi nguồn không tự tạo hủy/Refund; thu muộn FULL_CHECKOUT vẫn theo nhánh riêng.

* Thanh toán được xác nhận thành công muộn khi phiên đã hết hạn/hủy: hoàn toàn bộ Payment; không đồng thời tạo hoàn theo đơn; không có bút toán ví.

* P-RF-01: mỗi chu kỳ có một lần thực hiện đầu và tối đa 3 retry sau lỗi xác định, chờ 1/5/15 phút từ lần lỗi trước. PENDING/timeout chỉ query, không tính retry hoặc suy FAILED theo thời gian. Hết lượt vẫn FAILED thì dừng phát lệnh tự động, giữ nghĩa vụ/tiền chờ, bật customerRetryRequired và gửi REFUND_RETRY_REQUIRED cho Customer chủ Payment, không chuyển Admin. Customer xác nhận expectedRetryCycle với Idempotency-Key để mở chu kỳ mới trên cùng Refund/số tiền/key gateway: tăng retryCycle, reset retryCount=0, xóa cờ chờ và lưu công việc bền vững bằng CAS/khóa trong local transaction. Worker query trước phát lại; vẫn áp SUCCESS muộn. Áp dụng ORDER và FULL_CHECKOUT kể cả phiên đóng; BANNED được refund:retry:own trên đúng nghĩa vụ qua OPEN_ORDERS_ONLY, người khác/Seller/Admin không được thao tác. Giữ lịch sử mọi chu kỳ; khoảng 21 phút chỉ ước tính, không là deadline hay căn cứ kết luận timeout thất bại.

* Nhiều callback/nhấn hủy trùng: mỗi đơn chỉ được hoàn tối đa một lần; tổng các khoản hoàn không vượt tiền đã thu.

* Không bao giờ trừ số dư khả dụng hoặc số tiền đã rút của Seller để hoàn tiền (WD-06).

**Kết quả mong đợi:** Một Payment chung có thể có nhiều khoản Refund khác nhau theo đơn, hoặc đúng một Refund toàn phần trong trường hợp hoàn cả lần checkout; ví Seller không bao giờ âm vì hoàn tiền.

**Lưu ý nghiệp vụ:** Hoàn tiền chỉ được coi là thành công khi bộ giả lập trả kết quả thành công cho Refund (giai đoạn 1 luôn thành công ngay). Cập nhật Refund, Settlement và bút toán ví của đơn tách khỏi việc gọi bộ giả lập để không gây lặp khi kết nối gián đoạn.

#### UC-G03 | Tự động xử lý đơn/giao dịch quá hạn và đối soát tài khoản bị cấm

*Vai trò: Hệ thống · Ưu tiên: M*

**Mục tiêu:** Xử lý quá hạn thanh toán, hai thời hạn 7 ngày độc lập của người bán và khách trên từng đơn, giao dịch tiền chưa rõ kết quả và vòng đời tài khoản bị cấm theo nghĩa vụ hiện tại.

**Điều kiện:** Tác vụ định kỳ theo N-06 được kích hoạt.

**LUỒNG CHÍNH**

1. J1: phiên checkout hết hạn giữ hàng mà chưa có khoản thanh toán đang chờ xác minh → đóng phiên và tất cả các đơn của phiên, giải phóng toàn bộ giữ chỗ.

2. J2/J2b: tra cứu Payment chưa rõ; nếu quá P-04 vẫn không có kết quả thì đóng phiên và các đơn, tiếp tục theo dõi để hoàn toàn bộ nếu tiền đến muộn.

3. J4 (Must): với mỗi Order, nếu quá 7 ngày kể từ thanh toán thành công mà Seller chưa xác nhận “Đã giao” hoặc “Từ chối giao hàng”, hệ thống tự hủy đơn và hoàn tiền đơn đó (Settlement chờ\_hoàn; số dư chờ bị trừ khi hoàn thành công — UC-G02).

4. J3 (Must): Order đã\_giao nhưng khách không chọn “Đã nhận hàng” trong 7 ngày kể từ lúc Seller xác nhận đã giao → tự chuyển sang “Đã xác nhận” và quyết toán đơn (UC-G04), tiền Seller chuyển sang khả dụng.

5. J5 query Refund chưa rõ, retry FAILED theo P-RF-01 và dừng phát lệnh sau 3 retry/chu kỳ để chờ Customer; vẫn query/áp kết quả muộn. Khi triển khai email nghiệp vụ, J6 gửi lại email theo P-19 độc lập lịch J5; không bắt buộc J6 email nghiệp vụ để hoàn thành in-app. Chu kỳ chạy job không phải khoảng chờ retry; luồng email bảo mật của Identity giữ nguyên.

6. J7 — Đối soát trạng thái tài khoản bị cấm, owner Identity: định kỳ xử lý BANNED↔DISABLED theo nghĩa vụ UC-E03; không tự unban/đụng ACTIVE. Retry an toàn, không reset deadline, kiểm tra version/đợt cấm trước commit; nguồn lỗi giữ trạng thái. Login/refresh/request dùng cùng policy, không cam kết SLA hay chu kỳ mới.

**LUỒNG THAY THẾ / NGOẠI LỆ**

* Seller bấm giao/từ chối ngay khi J4 chạy: chỉ một chuyển trạng thái thắng, không vừa giao vừa hủy.

* Khách xác nhận nhận đúng lúc J3 chạy: chỉ một lần ghi nhận, một lần quyết toán và một bút toán SETTLEMENT\_RELEASED.

* Thanh toán chờ kết quả không được coi là thất bại chỉ vì chưa phản hồi; đối soát trước khi nhả hàng.

**Kết quả mong đợi:** J4 hủy và hoàn tiền khi Seller im lặng quá 7 ngày là bắt buộc; J3 tự xác nhận nhận hàng sau 7 ngày kể từ “Đã giao”; mỗi đơn được tính hạn riêng; không có trạng thái “Đang giao”.

#### UC-G04 | Quyết toán đơn và ghi có ví (giả lập)

*Vai trò: Hệ thống · Ưu tiên: M · Đổi tên và phạm vi ở v5.2 (trước đây: Chốt hoa hồng và chi trả người bán)*

**Mục tiêu:** Chốt hoa hồng và khoản Seller được nhận khi một đơn được xác nhận nhận hàng, chuyển tiền của Seller từ số dư chờ sang số dư khả dụng.

**Điều kiện:** Order đã\_xác\_nhận bởi khách hoặc J3; Payment chung success và đơn không bị hủy/hoàn; Settlement đang chờ\_quyết\_toán.

**LUỒNG CHÍNH**

1. Khi đơn đạt “Đã xác nhận”, hệ thống tính hoa hồng và tiền người bán được hưởng trên tổng tiền của đơn theo tỷ lệ chụp lúc checkout.

2. Hệ thống chuyển Settlement sang đã\_quyết\_toán, ghi bút toán SETTLEMENT\_RELEASED: ví Seller chờ −Sᵢ, khả dụng \+Sᵢ; ví nền tảng chờ −Cᵢ, hoa hồng đã chốt \+Cᵢ — trong cùng transaction với chuyển trạng thái đơn.

3. Hệ thống gửi Seller thông báo tiền đã khả dụng; không phát lệnh chi tự động.

4. Seller có thể rút số dư khả dụng bất kỳ lúc nào theo UC-D05.

**LUỒNG THAY THẾ / NGOẠI LỆ**

* Đơn bị hủy/đang hoàn: không chốt hoa hồng, không ghi có khả dụng.

* BANNED vẫn quyết toán đơn đủ điều kiện; available không đóng băng; nghĩa vụ tới muộn mở lại BANNED giới hạn theo UC-E03.

* Sᵢ \= 0: chốt Settlement, không ghi bút toán cho ví Seller.

* Cùng một lần thanh toán có đơn đã\_xác\_nhận và đơn đã\_hủy: chỉ quyết toán đơn đã\_xác\_nhận, đơn hủy hoàn đúng số tiền.

* Sự kiện xác nhận bị xử lý lặp: khóa sự kiện (event\_key) của bút toán chặn ghi có lần hai.

**Kết quả mong đợi:** Hoa hồng ghi nhận một lần tại thời điểm xác nhận nhận hàng; Σ Sᵢ đã quyết toán của Seller \= khả dụng \+ đã rút.

## 7. Truy vết

Đăng ký/đăng nhập → A01–A07 · catalog/tìm kiếm → B01/B02 · giỏ → B03 · mua ngay → B10 · checkout tách đơn theo cửa hàng → B04 · thanh toán một lần và ghi số dư chờ → B05/B06 · theo dõi/hủy/xác nhận từng đơn → B07–B09 · đánh giá → C01 · Seller giao/từ chối bắt buộc → D02 · tồn kho → D03 · thống kê → D04 · ví và rút tiền → D05 · hoàn theo đơn → G02 · tự động 7 ngày → G03 · quyết toán và ghi có ví → G04 · Admin xử lý quyền/tiền → E03/E08.

Truy vết dữ liệu (theo Database RBAC v5.0): CheckoutSession (3.6.1) → Order/OrderItem (3.6.2–3.6.3) → Reservation (3.6.5) → Payment/Refund (3.4) → trạng thái và hạn 7 ngày (8.2) → Settlement (3.5.1, 8.3) → Wallet/WalletTransaction (3.5.2–3.5.3) → Withdrawal (3.5.4). Hai mã job J3/J4 bắt buộc.

Kiểm thử trọng yếu: 1 CheckoutSession/N Order/1 Payment; hoàn đúng đơn, không vượt tiền thu; giao/từ chối và xác nhận trong các mốc 7 ngày; ba tài khoản riêng cho ba vai trò; sai vai trò báo lỗi đăng nhập chung; Seller chờ duyệt/bị từ chối không đăng nhập, sau từ chối gửi hồ sơ mới; chỉ Seller đã duyệt mới được cấm. Quyền BANNED dựa nghĩa vụ Order/Payment/Refund/Finance hiện tại, không lịch sử thanh toán; J7 chuyển BANNED↔DISABLED không tự unban hoặc gia hạn; unban DISABLED trong 30 ngày từ lần đầu DISABLED của đợt cấm. Ảnh Seller dùng chung trường ảnh đại diện/logo; không rút pending, BANNED được rút toàn bộ available dương dưới 10.000, ACTIVE giữ ngưỡng cũ; hoàn tiền không làm ví âm, rút lặp không trừ hai lần. Đối chiếu đầy đủ B1–B11/P1–P2/O1–O3 tại §9.

## 8. Nhật ký quyết định cập nhật v5.0 – v5.5

Các hàng phiên bản trước là **lịch sử**; policy cấm và ngoại lệ rút đã được Thẻ 15 thay tại UC-E03/D05 ngày 17/11/2026, không dùng scope hoặc điều kiện chỉ có đơn/available làm policy hiện hành.

| Hạng mục | Quyết định | Áp dụng |
| :---- | :---- | :---- |
| Tách đơn theo cửa hàng | Một lần checkout có sản phẩm của nhiều cửa hàng tạo 1 CheckoutSession và N Order, mỗi Order thuộc một Seller và xử lý riêng; bỏ thực thể FulfillmentGroup | DB 3.1.2, 3.6; B03–B07 |
| Thanh toán | Giữ một lần thanh toán chung: 1 Payment gắn CheckoutSession; kết quả thanh toán áp dụng cho cả N Order | DB 3.4, 8.2A; B05–B06 |
| Hủy trước thanh toán | Chỉ hủy được cả phiên (tất cả N đơn); không hủy lẻ hay thanh toán riêng một đơn trước khi phiên được thanh toán | DB 3.6.1; B04, B08 |
| Hủy/hoàn sau thanh toán | Hủy và Refund theo Order (order\_id thay group\_id); thanh toán muộn → hoàn toàn bộ phiên | DB 3.4.2, 8.2B; B08, G02 |
| Hạn 7 ngày | Giữ nguyên hai mốc 7 ngày, áp dụng trên từng Order | DB 8.2B; D02, G03 |
| Quyết toán | Settlement gắn với Order | DB 8.3; B09, G04 |
| Trạng thái tổng hợp | Bỏ trạng thái tổng hợp cấp đơn chung (“hoàn tất một phần”); mỗi Order có vòng đời riêng | DB 3.6, 8.2 |
| Mua ngay (v5.1) | Thêm UC-B10: nút “Mua ngay” trên trang sản phẩm mở trang Thanh toán với đúng 1 sản phẩm, tạo 1 phiên \+ 1 đơn, không đọc/ghi giỏ; thêm trường CheckoutSession.nguồn\_checkout | DB 3.6.1, G-16; B01, B03, B04, B06, B10 |
| Ví giả lập (v5.2) | Thêm Wallet (một ví / Seller, một ví nền tảng) và WalletTransaction append-only; tiền chia ngăn chờ quyết toán / khả dụng / đã rút | DB 3.5; B06, B08, B09, G02, G04 |
| Tiền chờ vs khả dụng (v5.2) | Payment success → số dư chờ (không rút được); Order đã\_xác\_nhận → khả dụng. Refund chỉ lấy từ ngăn chờ của đúng đơn, không bao giờ trừ ví khả dụng; ví không âm | DB 8.3 (WD-02, WD-06, WD-07) |
| Rút tiền (v5.2) | Thêm UC-D05: Seller rút theo số tiền, 10.000 VND (P-WD-01) ≤ A ≤ số dư khả dụng; giao dịch giả lập hoàn tất ngay trong một transaction, không cần Admin duyệt, không có trạng thái chờ | DB 8.2J, 3.5.4; D05, G01 |
| Bỏ chi trả tự động (v5.2) | Không còn Payout theo từng Settlement khi đơn đã\_xác\_nhận; bỏ Payout/PayoutProvider và tham số P-PAYOUT-01/02. UC-G04 đổi tên “Quyết toán đơn và ghi có ví” | DB 8.3; B09, G04 |
| Bỏ báo cáo vi phạm (v5.2) | Loại UC-C02, UC-E05 và thực thể Report (mục 4.13 cũ); giữ chỗ mã, không tái sử dụng. Admin vẫn gỡ sản phẩm/cấm tài khoản chủ động | 2, 3, 5; DB 5.6; C02, E04, E05 |
| Xóa mục 4 (v5.3) | Mô hình dữ liệu, quy tắc toàn cục, state machine, dòng tiền và quy tắc ví chuyển sang Database v4.6; mục 4 chỉ còn bảng chuyển vị trí; tham chiếu 4.x đổi sang mục Database | Mục 4; toàn bộ FRD |
| Trạng thái Order (v5.3) | Giữ bộ trạng thái của FRD (chờ thanh toán, thanh toán thất bại, hết hạn, chờ xử lý, đang đóng gói, đã giao, đã xác nhận, đã hủy); Database bổ sung đủ các giá trị này | DB 3.6.2, 8.2B; B07, D02 |
| Giỏ hàng cần đăng nhập (v5.3) | Khách phải đăng nhập mới thêm vào giỏ; không có giỏ khách vãng lai; Database bỏ trường giỏ khách | DB 3.3; B03 |
| Mua ngay (v5.3) | Giữ UC-B10 theo FRD; Database bổ sung checkout\_source CART/BUY\_NOW và các trường liên quan | DB 3.6.1, G-16; B10 |
| Thời điểm trừ số dư chờ khi hoàn (v5.3) | Hủy đơn sau thanh toán chỉ chuyển Settlement sang chờ\_hoàn (không ghi bút toán, phần tiền bị khóa); khi Refund thành công mới ghi REFUND\_DEBIT trừ số dư chờ Seller (Sᵢ) và nền tảng (Cᵢ). Bỏ bút toán hủy\_chờ | DB 8.3; B06, B08, D02, E03, G02, G03 |
| Bộ giả lập thanh toán nội bộ (v5.3) | Thay “dịch vụ mô phỏng/mock provider” bằng bộ giả lập nội bộ sau interface PaymentGateway; giai đoạn 1 chỉ kịch bản thành công, giai đoạn 2 thêm FAIL, TIMEOUT, LATE\_SUCCESS, REFUND\_FAIL | DB 8.4; B05, E08, G02, G03 |
| Mã đơn, mã thanh toán, mã vận đơn (v5.4) | Đã chốt theo Quyết định \#15/C-06: dùng orderId/checkoutSessionId dạng UUID để định danh đơn và phiên thanh toán, không có orderCode/checkoutCode/trackingNumber; chỉ withdrawalCode được giữ. | DB 3.6.1, 3.6.2; B04, B06, B07, D05, E02, E08, G01 |
| Tài khoản tách theo vai trò (v5.5, thay thế v5.4) | Theo conflict \#6: Customer, Seller, Admin có tài khoản độc lập; Database v5.1 và API v1.1 đã sửa thiết kế accountType và RBAC một vai trò/User. Cần triển khai migration/FE/BE tương ứng. | Mục 2, 5; A01–A06, E01, E03, E06; cần cập nhật DB 3.1 và API Identity/RBAC. |
| Seller chờ duyệt/bị từ chối/bị cấm (điều chỉnh v5.5) | Chờ duyệt/bị từ chối không được đăng nhập, sau từ chối phải nộp hồ sơ mới; Seller đã duyệt bị cấm chỉ được đăng nhập khi còn đơn chưa hoàn tất/số dư khả dụng, chỉ xử lý đơn đang dở và rút khả dụng. | A02, A03, D02, D05, E03, E06; cần đồng bộ Database/API. |
| Tên cửa hàng và số điện thoại (v5.4) | Tên cửa hàng là họ tên của tài khoản, không đổi khi hồ sơ chờ duyệt/đã duyệt; số điện thoại của tài khoản là số liên hệ cửa hàng | DB 3.1.1, 3.1.3, G-02; A02, A06, B02, E06 |
| Cấm theo phạm vi và ngoại lệ (điều chỉnh v5.5) | Giữ scope USER/CUSTOMER/SELLER; Customer bị cấm chỉ được đăng nhập khi còn đơn chưa hoàn tất; Seller chỉ bị cấm sau duyệt và còn được xử lý đơn dở/rút khả dụng. USER bị cấm thì khóa toàn bộ tài khoản. | A03, B03–B10, D02, D05, E03/E06; cần cập nhật G-14, WD-09 và API. |
| Ảnh đại diện, địa chỉ, đánh giá, quên mật khẩu (điều chỉnh v5.5) | Dùng một trường users.avatar\_object\_key cho ảnh đại diện của mỗi tài khoản và logo Seller; xóa địa chỉ là xóa hẳn; không sửa đánh giá; quên mật khẩu hiển thị thông báo chung (C-09 đóng). | A05, A06, B02, C01; cần bổ sung users.avatar\_object\_key và API ảnh vào DB/API. |
| Lý do hủy (v5.4) | Hủy/từ chối đơn gồm mã lý do trong danh sách P-21 và ghi chú | DB 3.6.2; B08, D02 |
| Lịch sử khóa (v5.4) | Chỉ lưu trong nhật ký nội bộ; MVP không có màn xem lịch sử khóa | DB mục 6; E01 |
| Giữ nguyên từ v4.1, trừ các nội dung đã điều chỉnh ở v5.5 | Seller được từ chối giao; J3/J4 Must; tiền giả lập; Google cho Customer mức Should; media sản phẩm ảnh/video; review chỉ bắt buộc sao; miễn phí vận chuyển. Quy tắc cấm và đăng nhập đã thay đổi theo FRD Conflicts. | Các mục không thay đổi nghiệp vụ |

### Bảng ánh xạ thuật ngữ v4.1 → v5.x (dành cho backlog, API, DB)

| Bản cũ | Bản mới | Ghi chú |
| :---- | :---- | :---- |
| Order (đơn chung, nhiều Seller) — v4.1 | CheckoutSession | Gom N Order; mang trạng thái thanh toán và hạn thanh toán |
| FulfillmentGroup — v4.1 | Order | Có seller\_id; đơn vị xử lý độc lập |
| group\_id — v4.1 | order\_id | Trên Reservation, StockMovement, Refund, Settlement, Notification |
| FulfillmentGroup.tổng\_tiền\_nhóm — v4.1 | Order.tổng\_tiền |  |
| Order.tổng\_tiền — v4.1 | CheckoutSession.tổng\_tiền | \\= Payment.số\_tiền |
| Payment.order\_id — v4.1 | Payment.checkout\_session\_id | Tối đa 1 Payment / phiên |
| Order.trạng\_thái\_tổng\_hợp — v4.1 | (bỏ) | Không còn “hoàn tất một phần” |
| OrderItem.group\_id — v4.1 | (bỏ) | OrderItem thuộc trực tiếp Order |
| Payout, PayoutProvider — v5.1 | Withdrawal (giao dịch tức thời) | Bỏ lệnh chi bất đồng bộ; một lần rút có thể gồm tiền của nhiều đơn |
| “Còn phải trả Seller” — v5.1 | Số dư khả dụng (Wallet) | Tách thêm số dư chờ cho đơn chưa xác nhận |
| — | Wallet, WalletTransaction, Withdrawal | Thực thể mới v5.2 (Database 3.5) |
| Bút toán ghi\_chờ / quyết\_toán / hủy\_chờ / rút\_tiền — v5.2 | PAYMENT\_RECEIVED / SETTLEMENT\_RELEASED / REFUND\_DEBIT (khi Refund thành công) / WITHDRAWAL\_COMPLETED | v5.3: bỏ hủy\_chờ lúc hủy đơn |
| Report — v5.1 | (bỏ) | Cùng UC-C02, UC-E05 |
| Một User nhiều vai trò — v5.4 | Customer / Seller / Administrator dùng tài khoản riêng — v5.5 | Theo FRD Conflicts số 6; cần sửa Database 3.1 và API Identity/RBAC. |
| Tên người bán, SĐT liên hệ — v5.3 | Họ tên và số điện thoại của tài khoản | Không có trường riêng ở Seller |
| Mã đơn / mã thanh toán / mã vận đơn — v5.3 | orderId / checkoutSessionId; (bỏ) mã vận đơn | Không có mã hiển thị |

FRD v5.5 áp dụng Quyết định mới FRD Conflicts. Quyết định \#6 chốt tài khoản độc lập Customer/Seller/Admin. Quyết định \#15 chốt theo API/Database: dùng UUID orderId/checkoutSessionId; bỏ orderCode/checkoutCode/trackingNumber, giữ withdrawalCode (C-06 đóng). Database v5.1/API v1.1 đã được chỉnh; BRD/UI/Figma, OpenAPI và code cần đồng bộ.

## 9. Thông số bổ sung và kịch bản nghiệm thu

[parameters.md](parameters.md) là nguồn giá trị đã chốt Thẻ 16 và xác nhận người dùng 18/11/2026, bổ sung quyết định P-37 ngày 19/11/2026, thay phần để ngỏ/giá trị cũ cùng chủ đề. P-01 vẫn tạm 15 phút chưa chốt, tham số khác còn mở giữ trạng thái riêng; N-09/N-11 không đổi. P-37 yêu cầu tìm tên sản phẩm và tên cửa hàng không phân biệt dấu tiếng Việt và hoa thường. Hạn Seller 7 ngày tính từ Payment success; hạn khách 7 ngày từ Seller xác nhận đã giao.

| Thông số | Quy định | Trạng thái |
| :---- | :---- | :---- |
| S-DELIVERY-07 | 7 ngày kể từ Payment success: Seller phải xác nhận đã giao hoặc từ chối giao từng đơn; nếu không → hủy/hoàn đơn đó | Đã chốt |
| S-RECEIPT-07 | 7 ngày kể từ Seller xác nhận đã giao: khách không phản hồi → đơn tự đã\_xác\_nhận | Đã chốt |
| S-WALLET-01 (v5.2) | Tiền Seller chỉ chuyển sang khả dụng khi Order đã\_xác\_nhận; không có thời gian tạm giữ thêm sau xác nhận trong MVP vì không có đổi trả sau nhận hàng | Đã chốt trong v5.2 |
| P-WD-01 | Ngưỡng chung 10.000 VND; tối đa available; ngoại lệ BANNED rút toàn bộ available dương dưới ngưỡng UC-D05; không giới hạn số lần/ngày | Đã chốt Thẻ 15 |
| S-WD-INSTANT (v5.2) | Rút tiền giả lập hoàn tất ngay trong một transaction; không duyệt, không trạng thái chờ, không Payout | Đã chốt |
| P-COM-01 | Hoa hồng chung 500 bps = 5%, snapshot mỗi Order, không theo Category/Seller | Đã chốt, không chỉ để test |
| N-06 | Chu kỳ job nền | Chưa chốt; không suy diễn thêm |
| P-13 | Cách tổng hợp số thu chung, hoàn/hoa hồng theo đơn, số dư ví và rút tiền | Theo Database RBAC v5.0 mục 8.3 |
| S-REFUND-DEBIT (v5.3) | Số dư chờ chỉ bị trừ khi Refund của đơn thành công; trong lúc chờ hoàn, phần tiền bị khóa trong số dư chờ | Đã chốt |
| S-SIMULATOR (v5.3) | Bộ giả lập thanh toán nội bộ; giai đoạn 1: SUCCESS; giai đoạn 2: FAIL, TIMEOUT, LATE\_SUCCESS, REFUND\_FAIL | Đã chốt |

Kịch bản nghiệm thu tối thiểu — dùng để QA/UAT đối chiếu các quyết định đã thay đổi.

| Tình huống | Kết quả bắt buộc |
| :---- | :---- |
| Ba cửa hàng trong một giỏ | Một CheckoutSession, một Payment và ba Order; mỗi Order một seller\_id riêng |
| Hai sản phẩm của cùng một cửa hàng | Một Order chứa hai OrderItem |
| Một sản phẩm hết hàng tại checkout | Không tạo CheckoutSession/Order/Payment nào |
| Bấm đặt hàng hoặc thanh toán hai lần | Cùng một phiên và cùng N đơn, một lần thu; request trùng an toàn |
| Thanh toán thất bại hoặc hết hạn | Cả N đơn đóng, nhả toàn bộ hàng, giỏ giữ nguyên; không có bút toán ví |
| Khách hủy trước khi thanh toán | Hủy cả phiên và N đơn; không hoàn tiền |
| Một Seller xác nhận đã giao | Chỉ đơn đó sang đã\_giao, UI “Chờ khách xác nhận”; không có đang\_giao |
| Seller từ chối do hàng lỗi | Hủy đơn, hoàn kho, Settlement chờ\_hoàn; khi hoàn thành công mới trừ số dư chờ và hoàn đúng tổng tiền đơn |
| Đơn đã hủy nhưng Refund chưa thành công (giai đoạn 2: REFUND\_FAIL) | Pending chưa đổi/khóa chờ hoàn; J5 thử lại tối đa 3 lần sau lần đầu, hết lượt chờ Customer theo UC-G02, không Admin |
| Seller không giao/từ chối sau 7 ngày tính từ thanh toán | Tự hủy đơn đó, hoàn tiền; đơn khác cùng lần thanh toán không đổi |
| Khách không xác nhận sau 7 ngày kể từ đã\_giao | Đơn tự chuyển đã\_xác\_nhận; chỉ đơn đó quyết toán, tiền Seller sang khả dụng |
| Khách bấm xác nhận cùng lúc J3 | Một trạng thái cuối, một quyết toán, một bút toán SETTLEMENT\_RELEASED |
| Hai đơn cùng lần thanh toán bị hủy ở các thời điểm khác nhau | Hai Refund khác nhau trên một Payment; không hoàn trùng/không vượt tiền thu |
| Đơn A bị hủy, đơn B đã\_xác\_nhận | A hoàn tiền, B quyết toán và vào số dư khả dụng của Seller B |
| Seller B đăng nhập | Chỉ thấy đơn B và ví B, không thấy đơn A dù cùng lần thanh toán |
| Customer BANNED có/không Order mở | Đánh giá cả Payment/Refund; còn nghĩa vụ → giới hạn UC-E03; mọi nguồn xác minh hết → DISABLED. |
| Payment success đến muộn sau khi phiên hết hạn | Hoàn toàn bộ Payment; không tạo Refund theo đơn, không tạo Settlement/bút toán ví |
| Tỷ lệ hoa hồng thay đổi sau checkout | Mỗi đơn dùng tỷ lệ snapshot tại lúc tạo đơn |
| “Mua ngay” sản phẩm X số lượng 2, đặt hàng | Một CheckoutSession (nguồn\_checkout \= mua\_ngay), một Order, một OrderItem số lượng 2 |
| Giỏ có X ×3, “Mua ngay” X ×1 rồi thanh toán thành công | Giỏ vẫn còn X ×3 |
| “Mua ngay” khi đang có phiên chờ thanh toán | Không tạo phiên mới; hướng dẫn xử lý phiên cũ |
| Khách chưa đăng nhập bấm “Mua ngay” | Chuyển đăng nhập, quay lại trang sản phẩm; không tự tạo đơn |
| Rời trang Thanh toán (mua ngay) trước khi đặt hàng | Không có phiên/đơn/giữ hàng nào được tạo |
| Khách thanh toán đơn 50.000 (r = 500 bps) | Ví Seller chờ 47.500, khả dụng 0; ví nền tảng chờ 2.500 |
| Seller có khả dụng 0, chờ 47.500, bấm “Rút tiền” | Không cho rút; hiển thị lý do tiền đang chờ quyết toán |
| Đơn trên được xác nhận nhận hàng | Ví Seller chờ 0, khả dụng 47.500; hoa hồng đã chốt 2.500 |
| Seller rút 47.500 | Ngay khi xác nhận: khả dụng 0, đã rút 47.500; một Withdrawal và một bút toán WITHDRAWAL\_COMPLETED |
| Sau khi Seller rút hết, khách hủy một đơn khác (chờ\_xử\_lý) của cùng Seller | Hoàn tiền thành công thì trừ ngăn chờ của đúng đơn đó; khả dụng/đã rút của Seller không đổi; không số dư âm |
| Khách cố hủy đơn đã\_giao hoặc đã\_xác\_nhận | Bị từ chối; tiền đã khả dụng không bị thu hồi |
| ACTIVE rút 9.999 hoặc Seller rút vượt available | Từ chối, không Withdrawal; ngoại lệ BANNED dưới ngưỡng chỉ toàn bộ available. |
| Seller rút toàn bộ số dư đồng thời từ hai tab | Chỉ một lần thành công; lần còn lại báo không đủ số dư |
| Bấm “Rút” lặp cùng idempotency\_key | Trả lại cùng giao dịch; ví chỉ bị trừ một lần |
| APPROVED BANNED còn available | Phiên giới hạn, rút theo UC-D05 kể cả toàn bộ available dương dưới ngưỡng; không bán mới/rút pending. |
| Một người sử dụng cả Customer và Seller | Dùng hai tài khoản riêng với thông tin đăng nhập khác nhau, không gộp vai trò/phiên đăng nhập; Administrator (nếu có) cũng phải là tài khoản riêng. |
| Seller đăng nhập khi hồ sơ chờ duyệt hoặc bị từ chối | Cả hai trạng thái đều không đăng nhập được. Khi từ chối: thông báo lý do qua email; muốn đăng ký lại phải nộp hồ sơ đăng ký mới, không sửa/nộp lại hồ sơ cũ. |
| Tìm nút “Báo cáo” trên trang sản phẩm/người bán | Không tồn tại; không có API báo cáo vi phạm |
| Đối chiếu tiền | Tổng thu \= đã hoàn \+ chờ hoàn toàn bộ phiên \+ Σ số dư chờ (gồm phần đơn đang chờ hoàn) \+ hoa hồng đã chốt \+ Σ khả dụng \+ Σ đã rút |
| Hai tab mở chu kỳ hoàn từ expectedRetryCycle cũ | Một chu kỳ mới; cùng HTTP key trả kết quả cũ, key khác/snapshot cũ → REFUND_RETRY_NOT_SAFE; không Refund/key/số tiền mới |
| Hết lượt nhiều chu kỳ / SUCCESS muộn | Một REFUND_RETRY_REQUIRED mỗi refundId/retryCycle; chu kỳ mới lại hết lượt vẫn thông báo mới. Query không tính retry, SUCCESS chỉ hoàn/ghi bút toán một lần |
| Customer BANNED / người khác / FULL_CHECKOUT đóng | Chủ nghĩa vụ được đọc và retry khi đủ điều kiện; sai chủ 404, Seller/Admin 403. FULL_CHECKOUT có đường xem/retry không cần orderId |
| Biên tham số đầu vào | Địa chỉ thứ 4 bị từ chối; xóa rồi thêm được; dòng giỏ 100 hoặc dòng mới thứ 51 bị từ chối, thêm trùng không sinh dòng mới. Mua ngay tối đa 99 và kiểm tra tồn |
| Biên media/nội dung | Product 9 tệp/2 video, 5.000 ký tự hợp lệ; Review Should 5 tệp/1 video, 1.000 ký tự hợp lệ; vượt từng giới hạn bị từ chối |
| Cảnh báo tồn/hạn | Tồn khả dụng ≤5 cảnh báo thấp (P-16); thời gian xử lý còn ≤24 giờ trước hạn 7 ngày là sắp hết hạn (P-36); không tự đổi phạm vi dashboard/Reporting |

**Đối chiếu đặc tả, không phải UAT/runtime pass:** authority và tình trạng đồng bộ các bề mặt tại [coverage](coverage.md#the-15). Các ca sau là yêu cầu nghiệm thu bổ sung, không tuyên bố code/migration đã triển khai.

### Ca nghiệm thu Thẻ 15

| Ca | Đầu vào | Kết quả bắt buộc |
| :---- | :---- | :---- |
| B1 | Customer chỉ CONFIRMED, không Payment/Refund dở | DISABLED, thu hồi phiên, lập disabledAt/restoreUntil; lịch sử không cấp ngoại lệ. |
| B2 | Customer PAID/DELIVERED | BANNED đúng chủ, hủy PAID/xác nhận DELIVERED theo state; không giỏ/thu mới/review. |
| B3 | CANCELLED, Refund FAILED | BANNED xem nghĩa vụ; hết lượt retry → OPEN_ORDERS_ONLY với refund:retry:own đúng chủ Payment; Refund SUCCESS và mọi nguồn xong mới DISABLED. |
| B4 | Seller không đơn thao tác, pending>0 hoặc effect chưa apply | BANNED/OBLIGATIONS_READ_ONLY, không rút pending; available sau đó mới rút. |
| B5 | BANNED available=9.500 rút 9.500/5.000; ACTIVE rút 9.500 | BANNED toàn bộ 9.500 hợp lệ; hai yêu cầu còn lại WITHDRAWAL_BELOW_MINIMUM. |
| B6 | Rút hết, hết nghĩa vụ, J7 hai lần | DISABLED một lần; không reset deadline/duplicate event/sửa ledger cũ. |
| B7 | Unban đúng restoreUntil / sau 1 giây | 200 ACTIVE đúng hạn; sau hạn 409 ACCOUNT_RESTORE_WINDOW_EXPIRED; dữ liệu/tiền nguyên vẹn. |
| B8 | BANNED >30 ngày chưa từng DISABLED | Vẫn xử lý nghĩa vụ/unban bất kỳ lúc nào; cửa sổ chưa bắt đầu. |
| B9 | Order/Finance timeout, J7 đua Admin unban | Không đoán zero/disable hoặc ban một phần; job không ghi đè ACTIVE. |
| B10 | DISABLED rồi available tới muộn | BANNED giới hạn, không unban; giữ mốc cùng đợt, lại DISABLED không gia hạn. |
| B11 | PENDING/REJECTED hoặc ADMIN | Không ban/đổi approval/role; popup/API cùng điều kiện. |
| P1 | Muốn SELLING, provision timeout/replay | Một Product HIDDEN/cùng productId; Inventory xác nhận và còn hợp lệ mới SELLING. |
| P2 | REMOVED/Seller ban khi provision; validation/media lỗi trước tạo | Không tự publish; lỗi trước tạo không lưu Product dở. |
| O1 | Payment SUCCESS, một Order invalid, Seller khác hợp lệ | Giữ Payment SUCCESS; chỉ invalid PAID/PACKING hủy/hoàn; đủ checkout.paid/order.cancelled; pending chỉ giảm khi Refund SUCCESS. |
| O2 | Ban sau capture, event tới chậm | Không cancel/refund/suy invalid từ status hiện tại; giữ quyền xử lý/J4. |
| O3 | Identity/Product lỗi, replay | Retry bền vững, không coi lỗi mạng invalid; không lặp SOLD/RESTOCK/Refund/ledger. |

