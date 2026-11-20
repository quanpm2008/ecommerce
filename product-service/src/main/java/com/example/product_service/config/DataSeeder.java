package com.example.product_service.config;

import com.example.product_service.media.common.MediaType;
import com.example.product_service.product.dto.model.MediaItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    // ==========================================
    // 1. UUID CỐ ĐỊNH CHO SELLER & CUSTOMER
    // ==========================================
    public static final UUID SELLER_FASHION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111"); // Shop Thời Trang GenZ
    public static final UUID SELLER_TECH_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");    // Thế Giới Công Nghệ
    public static final UUID SELLER_HOME_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");    // Đồ Gia Dụng Thông Minh
    public static final UUID SELLER_BOOK_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");    // Nhà Sách Tri Thức

    public static final UUID CUSTOMER_1_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    public static final UUID CUSTOMER_2_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    public static final UUID CUSTOMER_3_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    public static final UUID CUSTOMER_4_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    // ==========================================
    // 2. UUID CỐ ĐỊNH CHO CATEGORY
    // ==========================================
    public static final UUID CATEGORY_FASHION_ID = UUID.fromString("c1111111-1111-1111-1111-111111111111");
    public static final UUID CATEGORY_ELECTRONICS_ID = UUID.fromString("c2222222-2222-2222-2222-222222222222");
    public static final UUID CATEGORY_COMPUTERS_ID = UUID.fromString("c3333333-3333-3333-3333-333333333333");
    public static final UUID CATEGORY_BOOKS_ID = UUID.fromString("c4444444-4444-4444-4444-444444444444");
    public static final UUID CATEGORY_HOME_ID = UUID.fromString("c5555555-1111-1111-1111-111111111111");
    public static final UUID CATEGORY_BEAUTY_ID = UUID.fromString("c6666666-1111-1111-1111-111111111111");
    public static final UUID CATEGORY_SPORTS_ID = UUID.fromString("c7777777-1111-1111-1111-111111111111");
    public static final UUID CATEGORY_HIDDEN_ID = UUID.fromString("c8888888-5555-5555-5555-555555555555");

    // ==========================================
    // 3. UUID CỐ ĐỊNH CHO PRODUCT
    // ==========================================
    public static final UUID PRODUCT_IPHONE_ID = UUID.fromString("d1111111-1111-1111-1111-111111111111");
    public static final UUID PRODUCT_SAMSUNG_ID = UUID.fromString("d1222222-1111-1111-1111-111111111111");
    public static final UUID PRODUCT_TSHIRT_ID = UUID.fromString("d2222222-2222-2222-2222-222222222222");
    public static final UUID PRODUCT_MOUSE_ID = UUID.fromString("d3333333-3333-3333-3333-333333333333");
    public static final UUID PRODUCT_HIDDEN_ID = UUID.fromString("d4444444-4444-4444-4444-444444444444");
    public static final UUID PRODUCT_REMOVED_ID = UUID.fromString("d5555555-5555-5555-5555-555555555555");
    public static final UUID PRODUCT_LAPTOP_ID = UUID.fromString("d6666666-1111-1111-1111-111111111111");
    public static final UUID PRODUCT_KEYBOARD_ID = UUID.fromString("d7777777-1111-1111-1111-111111111111");
    public static final UUID PRODUCT_JEANS_ID = UUID.fromString("d8888888-1111-1111-1111-111111111111");
    public static final UUID PRODUCT_SNEAKERS_ID = UUID.fromString("d9999999-1111-1111-1111-111111111111");
    public static final UUID PRODUCT_AIRFRYER_ID = UUID.fromString("daaaaaaa-1111-1111-1111-111111111111");
    public static final UUID PRODUCT_ROBOT_ID = UUID.fromString("dbbbbbbb-1111-1111-1111-111111111111");
    public static final UUID PRODUCT_BOOK_CLEANCODE_ID = UUID.fromString("dccccccc-1111-1111-1111-111111111111");
    public static final UUID PRODUCT_SUNSCREEN_ID = UUID.fromString("dddddddd-1111-1111-1111-111111111111");
    public static final UUID PRODUCT_EXPIRED_REMOVED_ID = UUID.fromString("deeeeeee-1111-1111-1111-111111111111");

    @Override
    @Transactional
    public void run(String... args) {
        seedCategories();
        seedProducts();
        seedReviews();
        logSeedInfo();
    }

    private void seedCategories() {
        log.info("Bắt đầu khởi tạo danh mục (Categories)...");
        String sql = "INSERT INTO categories (category_id, category_name, status, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?) ON CONFLICT (category_id) DO NOTHING";
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        insertCategory(sql, CATEGORY_FASHION_ID, "Thời trang & Phụ kiện", "VISIBLE", now);
        insertCategory(sql, CATEGORY_ELECTRONICS_ID, "Điện thoại & Thiết bị điện tử", "VISIBLE", now);
        insertCategory(sql, CATEGORY_COMPUTERS_ID, "Máy tính & Thiết bị IT", "VISIBLE", now);
        insertCategory(sql, CATEGORY_BOOKS_ID, "Sách & Văn phòng phẩm", "VISIBLE", now);
        insertCategory(sql, CATEGORY_HOME_ID, "Nhà cửa & Đời sống", "VISIBLE", now);
        insertCategory(sql, CATEGORY_BEAUTY_ID, "Sức khỏe & Sắc đẹp", "VISIBLE", now);
        insertCategory(sql, CATEGORY_SPORTS_ID, "Thể thao & Dã ngoại", "VISIBLE", now);
        insertCategory(sql, CATEGORY_HIDDEN_ID, "Danh mục thử nghiệm (Đang ẩn)", "HIDDEN", now);
    }

    private void insertCategory(String sql, UUID id, String name, String status, OffsetDateTime now) {
        jdbcTemplate.update(sql, id, name, status, now, now);
    }

    private void seedProducts() {
        log.info("Bắt đầu khởi tạo sản phẩm (Products)...");
        String sql = "INSERT INTO products (product_id, seller_id, category_id, product_name, price_vnd, description, status, media, removal_reason, removed_at, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?) ON CONFLICT (product_id) DO NOTHING";
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        // 1. Điện thoại cao cấp (SELLING)
        insertProduct(sql, PRODUCT_IPHONE_ID, SELLER_TECH_ID, CATEGORY_ELECTRONICS_ID,
                "iPhone 16 Pro Max 256GB Titan Tự Nhiên", 34990000L,
                "Thiết kế titan bền nhẹ mới, nút Điều Khiển Camera, quay phim 4K 120 fps Dolby Vision và chip A18 Pro mạnh mẽ vượt trội.",
                "SELLING",
                List.of(
                        MediaItem.builder().objectKey("https://images.unsplash.com/photo-1695048133142-1a20484d2569?w=800").mediaType(MediaType.IMAGE).sortOrder(1).isCover(true).build(),
                        MediaItem.builder().objectKey("https://images.unsplash.com/photo-1510557880182-3d4d3cba35a5?w=800").mediaType(MediaType.IMAGE).sortOrder(2).isCover(false).build()
                ), null, null, now);

        insertProduct(sql, PRODUCT_SAMSUNG_ID, SELLER_TECH_ID, CATEGORY_ELECTRONICS_ID,
                "Samsung Galaxy S24 Ultra 512GB Titanium Gray", 31990000L,
                "Khung viền titan sang trọng, camera 200MP zoom quang học 5x, tích hợp bút S-Pen và quyền năng Galaxy AI đỉnh cao.",
                "SELLING",
                List.of(
                        MediaItem.builder().objectKey("https://images.unsplash.com/photo-1610945265064-0e34e5519bbf?w=800").mediaType(MediaType.IMAGE).sortOrder(1).isCover(true).build()
                ), null, null, now);

        // 2. Laptop công việc (SELLING)
        insertProduct(sql, PRODUCT_LAPTOP_ID, SELLER_TECH_ID, CATEGORY_COMPUTERS_ID,
                "MacBook Air 13 inch M3 16GB/512GB Midnight", 32490000L,
                "Chip M3 thế hệ mới cực nhanh, thời lượng pin lên đến 18 giờ, màn hình Liquid Retina sắc nét với thiết kế siêu mỏng nhẹ.",
                "SELLING",
                List.of(
                        MediaItem.builder().objectKey("https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=800").mediaType(MediaType.IMAGE).sortOrder(1).isCover(true).build(),
                        MediaItem.builder().objectKey("https://images.unsplash.com/photo-1611186871348-b1ce696e52c9?w=800").mediaType(MediaType.IMAGE).sortOrder(2).isCover(false).build()
                ), null, null, now);

        // 3. Chuột không dây (SELLING)
        insertProduct(sql, PRODUCT_MOUSE_ID, SELLER_TECH_ID, CATEGORY_COMPUTERS_ID,
                "Chuột không dây Logitech MX Master 3S Graphite", 2190000L,
                "Chuột không dây công thái học đỉnh cao, cảm biến 8000 DPI theo dõi trên mọi bề mặt kể cả kính, cuộn MagSpeed điện từ siêu êm.",
                "SELLING",
                List.of(
                        MediaItem.builder().objectKey("https://images.unsplash.com/photo-1615663245857-ac93bb7c39e7?w=800").mediaType(MediaType.IMAGE).sortOrder(1).isCover(true).build()
                ), null, null, now);

        // 4. Bàn phím cơ (SELLING)
        insertProduct(sql, PRODUCT_KEYBOARD_ID, SELLER_TECH_ID, CATEGORY_COMPUTERS_ID,
                "Bàn phím cơ Keychron K2 Pro QMK/VIA Switch Red", 2350000L,
                "Bàn phím cơ layout 75%, kết nối Bluetooth 5.1 & Type-C, tương thích hoàn hảo Mac/Windows, hỗ trợ hot-swap tuỳ biến phím bấm.",
                "SELLING",
                List.of(
                        MediaItem.builder().objectKey("https://images.unsplash.com/photo-1587829741301-dc798b83add3?w=800").mediaType(MediaType.IMAGE).sortOrder(1).isCover(true).build()
                ), null, null, now);

        // 5. Áo thun nam (SELLING)
        insertProduct(sql, PRODUCT_TSHIRT_ID, SELLER_FASHION_ID, CATEGORY_FASHION_ID,
                "Áo thun nam Cotton Compact 250gsm Oversize", 199000L,
                "Chất liệu 100% Cotton Compact cao cấp không xù lông, thoáng mát, form rộng unisex trẻ trung, co giãn 4 chiều.",
                "SELLING",
                List.of(
                        MediaItem.builder().objectKey("https://images.unsplash.com/photo-1521572267360-ee0c2909d518?w=800").mediaType(MediaType.IMAGE).sortOrder(1).isCover(true).build(),
                        MediaItem.builder().objectKey("https://images.unsplash.com/photo-1583743814966-8936f5b7be1a?w=800").mediaType(MediaType.IMAGE).sortOrder(2).isCover(false).build()
                ), null, null, now);

        // 6. Quần Jeans nam (SELLING)
        insertProduct(sql, PRODUCT_JEANS_ID, SELLER_FASHION_ID, CATEGORY_FASHION_ID,
                "Quần Jeans nam Regular Fit Denim Indigo", 420000L,
                "Vải denim dệt chéo bền chắc, đường may kép tỉ mỉ, phom suông dễ phối đồ cả đi làm lẫn dạo phố.",
                "SELLING",
                List.of(
                        MediaItem.builder().objectKey("https://images.unsplash.com/photo-1542272604-780c96856592?w=800").mediaType(MediaType.IMAGE).sortOrder(1).isCover(true).build()
                ), null, null, now);

        // 7. Giày Sneaker Thể Thao (SELLING)
        insertProduct(sql, PRODUCT_SNEAKERS_ID, SELLER_FASHION_ID, CATEGORY_SPORTS_ID,
                "Giày Sneaker Chạy Bộ Đi Bộ Siêu Êm Air Cushion", 850000L,
                "Đế đệm khí nảy êm chân, vải dệt Flyknit thoáng khí kháng khuẩn, chống trơn trượt hiệu quả khi vận động mạnh.",
                "SELLING",
                List.of(
                        MediaItem.builder().objectKey("https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=800").mediaType(MediaType.IMAGE).sortOrder(1).isCover(true).build()
                ), null, null, now);

        // 8. Nồi chiên không dầu (SELLING)
        insertProduct(sql, PRODUCT_AIRFRYER_ID, SELLER_HOME_ID, CATEGORY_HOME_ID,
                "Nồi chiên không dầu Philips Essential Airfryer XL 6.2L", 2890000L,
                "Công nghệ Rapid Air giảm tới 90% lượng chất béo, bảng điều khiển cảm ứng với 7 chế độ cài đặt sẵn tiện dụng.",
                "SELLING",
                List.of(
                        MediaItem.builder().objectKey("https://images.unsplash.com/photo-1584992236310-6edddc08acff?w=800").mediaType(MediaType.IMAGE).sortOrder(1).isCover(true).build()
                ), null, null, now);

        // 9. Robot hút bụi (SELLING)
        insertProduct(sql, PRODUCT_ROBOT_ID, SELLER_HOME_ID, CATEGORY_HOME_ID,
                "Robot hút bụi lau nhà Ecovacs Deebot T20 Omni", 14990000L,
                "Lực hút 6000Pa cực mạnh, tự động giặt giẻ nước nóng và sấy khô bằng khí ấm, điều hướng laser TrueMapping 3D.",
                "SELLING",
                List.of(
                        MediaItem.builder().objectKey("https://images.unsplash.com/photo-1518770660439-4636190af475?w=800").mediaType(MediaType.IMAGE).sortOrder(1).isCover(true).build()
                ), null, null, now);

        // 10. Sách Clean Code (SELLING)
        insertProduct(sql, PRODUCT_BOOK_CLEANCODE_ID, SELLER_BOOK_ID, CATEGORY_BOOKS_ID,
                "Sách Clean Code - Mã Sạch Trong Phát Triển Phần Mềm", 285000L,
                "Cuốn cẩm nang kinh điển của tác giả Robert C. Martin giúp lập trình viên viết code tinh gọn, dễ bảo trì và mở rộng.",
                "SELLING",
                List.of(
                        MediaItem.builder().objectKey("https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=800").mediaType(MediaType.IMAGE).sortOrder(1).isCover(true).build()
                ), null, null, now);

        // 11. Kem chống nắng (SELLING)
        insertProduct(sql, PRODUCT_SUNSCREEN_ID, SELLER_FASHION_ID, CATEGORY_BEAUTY_ID,
                "Kem Chống Nắng Kiểm Soát Dầu La Roche-Posay SPF 50+ 50ml", 395000L,
                "Màng lọc quang phổ rộng bảo vệ da toàn diện khỏi tia UVA/UVB, công nghệ Airlicium kiềm dầu đến 8 giờ.",
                "SELLING",
                List.of(
                        MediaItem.builder().objectKey("https://images.unsplash.com/photo-1556228720-195a672e8a03?w=800").mediaType(MediaType.IMAGE).sortOrder(1).isCover(true).build()
                ), null, null, now);

        // 12. Áo hoodie (HIDDEN)
        insertProduct(sql, PRODUCT_HIDDEN_ID, SELLER_FASHION_ID, CATEGORY_FASHION_ID,
                "Áo hoodie nỉ bông mùa đông (Seller tạm ẩn kho)", 450000L,
                "Sản phẩm hết vụ hè nên tạm ẩn để chuẩn bị cập nhật bộ sưu tập mùa đông mới.",
                "HIDDEN",
                List.of(
                        MediaItem.builder().objectKey("https://images.unsplash.com/photo-1556905055-8f358a7a47b2?w=800").mediaType(MediaType.IMAGE).sortOrder(1).isCover(true).build()
                ), null, null, now);

        // 13. Tai nghe nhái (REMOVED < 30 ngày)
        insertProduct(sql, PRODUCT_REMOVED_ID, SELLER_TECH_ID, CATEGORY_COMPUTERS_ID,
                "Tai nghe Bluetooth nhái thương hiệu (Admin đã gỡ)", 99000L,
                "Sản phẩm vi phạm bản quyền thương hiệu phụ kiện.",
                "REMOVED",
                List.of(
                        MediaItem.builder().objectKey("https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=800").mediaType(MediaType.IMAGE).sortOrder(1).isCover(true).build()
                ), "Vi phạm bản quyền nhãn hiệu và quy chuẩn chất lượng của sàn.", now.minusDays(5), now);

        // 14. ĐT lậu (REMOVED > 30 ngày)
        insertProduct(sql, PRODUCT_EXPIRED_REMOVED_ID, SELLER_TECH_ID, CATEGORY_ELECTRONICS_ID,
                "Điện thoại lậu không rõ nguồn gốc (Đã gỡ quá 30 ngày)", 1200000L,
                "Hàng không có giấy tờ hải quan, bị tịch thu.",
                "REMOVED",
                List.of(
                        MediaItem.builder().objectKey("https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?w=800").mediaType(MediaType.IMAGE).sortOrder(1).isCover(true).build()
                ), "Hàng không rõ nguồn gốc xuất xứ, vi phạm nghiêm trọng chính sách.", now.minusDays(35), now);
    }

    private void insertProduct(String sql, UUID productId, UUID sellerId, UUID categoryId,
                               String productName, Long priceVnd, String description,
                               String status, List<MediaItem> media,
                               String removalReason, OffsetDateTime removedAt, OffsetDateTime now) {
        try {
            String mediaJson = objectMapper.writeValueAsString(media != null ? media : List.of());
            jdbcTemplate.update(sql, productId, sellerId, categoryId, productName, priceVnd, description, status,
                    mediaJson, removalReason, removedAt, now, now);
        } catch (Exception e) {
            log.error("Lỗi khi chèn sản phẩm {}: {}", productName, e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void seedReviews() {
        log.info("Bắt đầu khởi tạo đánh giá (Reviews)...");
        String sql = "INSERT INTO reviews (review_id, product_id, customer_id, order_item_id, rating, content, media_metadata, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?) ON CONFLICT (customer_id, product_id) DO NOTHING";
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        // Reviews iPhone
        insertReview(sql, PRODUCT_IPHONE_ID, CUSTOMER_1_ID, 5,
                "Máy rất đẹp, màu titan tự nhiên cầm đầm tay, camera quay phim 4K 120fps quá đỉnh!",
                List.of(MediaItem.builder().objectKey("https://images.unsplash.com/photo-1695048133142-1a20484d2569?w=800").mediaType(MediaType.IMAGE).sortOrder(1).isCover(false).build()), now);

        insertReview(sql, PRODUCT_IPHONE_ID, CUSTOMER_2_ID, 4,
                "Giao hàng nhanh, đóng gói cẩn thận 3 lớp bọc bong bóng, pin trâu dùng cả ngày thoải mái.",
                List.of(), now);

        insertReview(sql, PRODUCT_IPHONE_ID, CUSTOMER_3_ID, 5,
                "Nâng cấp từ iPhone 12 Pro lên cảm thấy mượt mà và màn hình Dynamic Island rất tiện lợi.",
                List.of(), now);

        insertReview(sql, PRODUCT_IPHONE_ID, CUSTOMER_4_ID, 3,
                "Sản phẩm tốt nhưng lúc chơi game Genshin Impact máy khá nóng, sạc 20W kèm theo hơi chậm.",
                List.of(), now);

        // Reviews Áo thun
        insertReview(sql, PRODUCT_TSHIRT_ID, CUSTOMER_1_ID, 5,
                "Vải dày dặn, mềm mát, form áo chuẩn không bị co rút hay phai màu sau khi giặt máy.",
                List.of(), now);

        insertReview(sql, PRODUCT_TSHIRT_ID, CUSTOMER_3_ID, 4,
                "Áo đẹp, chất vải sờ mịn, dáng hơi rộng chút so với bảng size nên ai thích vừa vặn nên lùi 1 size.",
                List.of(), now);

        insertReview(sql, PRODUCT_TSHIRT_ID, CUSTOMER_4_ID, 2,
                "Shop giao nhầm màu đen thành trắng, dù có đổi lại nhanh nhưng hơi mất công mang ra bưu cục gửi.",
                List.of(), now);

        // Reviews Chuột MX Master 3S
        insertReview(sql, PRODUCT_MOUSE_ID, CUSTOMER_2_ID, 5,
                "Cuộn MagSpeed siêu tốc cực sướng, bấm êm không tiếng ồn (silent click), dùng cả tuần chưa hết pin.",
                List.of(MediaItem.builder().objectKey("https://images.unsplash.com/photo-1615663245857-ac93bb7c39e7?w=800").mediaType(MediaType.IMAGE).sortOrder(1).isCover(false).build()), now);

        insertReview(sql, PRODUCT_MOUSE_ID, CUSTOMER_3_ID, 5,
                "Đáng từng xu, kết nối Flow chuyển qua lại giữa MacBook và PC mượt mà không độ trễ.",
                List.of(), now);

        // Reviews Sách Clean Code
        insertReview(sql, PRODUCT_BOOK_CLEANCODE_ID, CUSTOMER_1_ID, 5,
                "Sách gối đầu giường cho mọi dev, bìa cứng in đẹp, bản dịch tiếng Việt dễ hiểu và gần gũi.",
                List.of(), now);

        // Reviews Robot Deebot T20
        insertReview(sql, PRODUCT_ROBOT_ID, CUSTOMER_2_ID, 5,
                "Lau nhà bằng nước nóng sạch bóng, tự hút bụi vào túi chứa nên cả tháng mới phải dọn một lần.",
                List.of(), now);

        // Reviews Kem chống nắng
        insertReview(sql, PRODUCT_SUNSCREEN_ID, CUSTOMER_4_ID, 4,
                "Chất kem ráo nhanh, kiềm dầu tốt cả ngày đi làm, không bị vệt trắng bết rít.",
                List.of(), now);
    }

    private void insertReview(String sql, UUID productId, UUID customerId,
                              int rating, String content, List<MediaItem> media, OffsetDateTime now) {
        try {
            String mediaJson = objectMapper.writeValueAsString(media != null ? media : List.of());
            UUID reviewId = UUID.nameUUIDFromBytes(("review_" + productId + "_" + customerId).getBytes(StandardCharsets.UTF_8));
            UUID orderItemId = UUID.nameUUIDFromBytes(("order_item_" + productId + "_" + customerId).getBytes(StandardCharsets.UTF_8));
            jdbcTemplate.update(sql, reviewId, productId, customerId, orderItemId, rating, content, mediaJson, now, now);
        } catch (Exception e) {
            log.error("Lỗi khi chèn đánh giá cho sản phẩm {}: {}", productId, e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void logSeedInfo() {
        log.info("==========================================================================================");
        log.info("🎉 DỮ LIỆU SEED PRODUCT SERVICE ĐÃ SẴN SÀNG ĐỂ TEST TRÊN SWAGGER / POSTMAN:");
        log.info("👉 Swagger UI: http://localhost:8087/swagger-ui.html");
        log.info("------------------------------------------------------------------------------------------");
        log.info("🏢 SELLERS:");
        log.info("   • Seller Thời trang (Fashion):     {}", SELLER_FASHION_ID);
        log.info("   • Seller Công nghệ (Tech):          {}", SELLER_TECH_ID);
        log.info("   • Seller Gia dụng (Home):           {}", SELLER_HOME_ID);
        log.info("   • Seller Nhà sách (Book):           {}", SELLER_BOOK_ID);
        log.info("👤 CUSTOMERS:");
        log.info("   • Customer 1:                       {}", CUSTOMER_1_ID);
        log.info("   • Customer 2:                       {}", CUSTOMER_2_ID);
        log.info("   • Customer 3:                       {}", CUSTOMER_3_ID);
        log.info("   • Customer 4:                       {}", CUSTOMER_4_ID);
        log.info("📦 SẢN PHẨM TIÊU BIỂU:");
        log.info("   • iPhone 16 Pro Max (SELLING):      {}", PRODUCT_IPHONE_ID);
        log.info("   • Áo thun Compact (SELLING):        {}", PRODUCT_TSHIRT_ID);
        log.info("   • Chuột MX Master 3S (SELLING):     {}", PRODUCT_MOUSE_ID);
        log.info("   • MacBook Air M3 (SELLING):         {}", PRODUCT_LAPTOP_ID);
        log.info("   • Sách Clean Code (SELLING):        {}", PRODUCT_BOOK_CLEANCODE_ID);
        log.info("   • Robot Deebot T20 (SELLING):       {}", PRODUCT_ROBOT_ID);
        log.info("   • Áo Hoodie (HIDDEN - Seller ẩn):   {}", PRODUCT_HIDDEN_ID);
        log.info("   • Tai nghe nhái (REMOVED - Admin):  {}", PRODUCT_REMOVED_ID);
        log.info("   • ĐT Lậu (REMOVED > 30d hết hạn):   {}", PRODUCT_EXPIRED_REMOVED_ID);
        log.info("==========================================================================================");
    }
}