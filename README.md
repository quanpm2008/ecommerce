# Development

Chuẩn bị `.env` từ `.env.example` và điền mật khẩu DB nếu chưa có `.env`.
Compose đọc kết nối Supabase từ `DB_JDBC_URL`, `DB_USERNAME`, `DB_PASSWORD`;
không commit `.env`.

Build và chạy toàn bộ service lần đầu:

```bash
bash up.sh
```

Sau khi sửa code trong `src`, chỉ cần khởi động lại service đã sửa để Maven biên dịch source mới, ví dụ:

```bash
docker compose -p ecommerce restart product-service
```

Nếu sửa `pom.xml`, cần build lại image của service đó.

## Database

Mặc định cả 7 service nghiệp vụ dùng Supabase qua SSL từ `.env`. Gateway và
Discovery không dùng database. Mỗi service giữ schema riêng; Flyway tiếp tục
kiểm tra và áp dụng migration khi khởi động.

Product dùng Hibernate `validate` và không tự seed dữ liệu demo cũ. Chỉ bật
`PRODUCT_SEED_ENABLED=true` khi chạy Product trên database dùng một lần.

PostgreSQL local chỉ chạy khi bật profile `local-db` hoặc gọi đích danh service
`database`; `bash up.sh` mặc định không khởi động nó. Không xóa volume local khi
đổi sang Supabase.

Xem [DEVELOPMENT.md](DEVELOPMENT.md#database) để cấu hình datasource khi chạy
Docker hoặc IDE/Maven.
