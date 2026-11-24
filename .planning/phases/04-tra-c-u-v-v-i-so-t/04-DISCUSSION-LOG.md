# Phase 4 — Nhật ký thảo luận

**Ngày:** 2026-11-24  
**Phạm vi:** Tra cứu, ví và đối soát

Quyết định dùng khi lập kế hoạch nằm trong [CONTEXT.md](04-CONTEXT.md). Nhật ký này lưu các phương án đã trao đổi.

| Mục | Phương án đã trao đổi | Lựa chọn |
| --- | --- | --- |
| Danh sách Admin | Theo dấu hiệu xử lý hoặc theo tuổi chung của trạng thái PENDING | **1A**, theo dấu hiệu xử lý |
| Ngưỡng cảnh báo | 2 phút cho Payment chậm tạo link và 30 phút cho attempt Refund chưa rõ kết quả, hoặc ngưỡng người dùng chọn | Chốt **2 phút / 30 phút** |
| Điều kiện chặn rút | Dựa vào ví và ledger tại Finance hoặc bổ sung chặn từ kết quả đối soát liên service | **2A**, dựa vào ví và ledger tại Finance |
| Báo cáo đối soát | Ba trạng thái trong HTTP 200 hoặc giữ balanced khi đủ dữ liệu và trả 503 khi thiếu nguồn | **3A**, ba trạng thái và giữ balanced |
| Hiển thị Refund cho Admin | Giữ issueType và phân biệt chờ thử tự động, chờ khách và tạm dừng chờ đối soát | Người dùng duyệt, nhãn chờ đối soát được ưu tiên |

Người dùng xác nhận chốt thảo luận và ghi CONTEXT.md Phase 4. Các điểm cần đối chiếu kỹ thuật được ghi trong CONTEXT.md để research tiếp.
