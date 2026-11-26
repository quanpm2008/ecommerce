DO $$
BEGIN
    IF to_regclass('categories') IS NOT NULL THEN
        INSERT INTO categories (category_id, category_name, status, created_at, updated_at)
        VALUES
            ('c1111111-1111-1111-1111-111111111111', 'Thời trang', 'VISIBLE', now(), now()),
            ('c2222222-2222-2222-2222-222222222222', 'Điện thoại & Phụ kiện', 'VISIBLE', now(), now()),
            ('c3333333-3333-3333-3333-333333333333', 'Máy tính & Thiết bị điện tử', 'VISIBLE', now(), now()),
            ('c5555555-1111-1111-1111-111111111111', 'Nhà cửa & Đời sống', 'VISIBLE', now(), now()),
            ('c6666666-1111-1111-1111-111111111111', 'Sắc đẹp & Chăm sóc cá nhân', 'VISIBLE', now(), now()),
            ('c9999999-1111-1111-1111-111111111111', 'Sức khỏe', 'VISIBLE', now(), now()),
            ('caaaaaaa-1111-1111-1111-111111111111', 'Mẹ & Bé', 'VISIBLE', now(), now()),
            ('cbbbbbbb-1111-1111-1111-111111111111', 'Thực phẩm & Đồ uống', 'VISIBLE', now(), now()),
            ('c4444444-4444-4444-4444-444444444444', 'Sách & Văn phòng phẩm', 'VISIBLE', now(), now()),
            ('c7777777-1111-1111-1111-111111111111', 'Thể thao & Ngoài trời', 'VISIBLE', now(), now())
        ON CONFLICT DO NOTHING;
    END IF;
END
$$;
