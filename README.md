
## Database dùng chung cho dev

Compose chạy một PostgreSQL 16 với database `ecommerce`, username/password đều là
`ecommerce`. Các service dùng chung datasource `jdbc:postgresql://database:5432/ecommerce`
và chờ database healthy trước khi khởi động (discovery-service không cần database).
JPA hiện vẫn bị comment trong các `pom.xml`; cấu hình này cung cấp thông tin kết nối,
chưa bật tầng persistence hay tạo bảng.

Chỉ chạy database để phát triển service trên máy:

```bash
docker compose -p ecommerce up -d database
```

Kết nối từ máy host bằng `jdbc:postgresql://localhost:5432/ecommerce` với tài khoản trên.
Database chỉ mở cổng trên localhost; tài khoản này chỉ dành cho dev.

Dữ liệu lưu trong volume `postgres-data`, được giữ lại khi chạy `docker compose down`.
Lệnh `docker compose down -v` sẽ xóa cả dữ liệu database.
