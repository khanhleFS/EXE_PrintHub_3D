-- =========================================================================================
-- PRINTHUB 3D - SUPABASE SAMPLE DATA SCRIPT (CHUYÊN BIỆT THƯỚC IN 3D THẬT & REVIEWS CHẤT LƯỢNG)
-- Mục đích: Khởi tạo dữ liệu mẫu THỰC TẾ với dòng sản phẩm THƯỚC IN 3D CÁ NHÂN HÓA của PrintHub
-- Target User: khanhle0209c@gmail.com (id: 3f65fede-a289-4857-945a-df3f200d5e13)
-- Bao gồm:
--   0. ĐỒNG BỘ CẤU TRÚC DDL AN TOÀN TRÊN POSTGRES / SUPABASE:
--      - Gỡ bỏ hoàn toàn seller_id khỏi bảng reviews
--      - Đảm bảo các cột product_id, order_id, order_item_id, updated_at trên reviews
--      - Đảm bảo bảng review_images lưu link ảnh thành phẩm in 3D
--      - Đảm bảo average_rating & review_count trên bảng products
--   1. Đảm bảo User mẫu & Ví điểm thưởng PointWallet (250 điểm)
--   2. Gói hội viên Subscription Plans & kích hoạt UserSubscription (VIP)
--   3. 4 Danh mục Thước In 3D (categories)
--   4. 10 Sản phẩm THƯỚC IN 3D THỰC TẾ của PrintHub (giá từ 15.000đ - 49.000đ kèm ảnh đẹp)
--   5. 4 Đơn hàng thước in 3D: 2 COMPLETED (đã giao), 1 SHIPPING (đang giao), 1 PENDING (chờ duyệt)
--      (Đặc biệt: Đơn COMPLETED có 1 sản phẩm chưa đánh giá để test form tạo review trực tiếp trên UI)
--   6. 3 Khiếu nại Disputes thực tế về thước in 3D: UNDER_REVIEW, RESOLVED, REJECTED (kèm trao đổi)
--   7. 5 Đánh giá chất lượng sản phẩm in 3D (Reviews liên kết chính xác 1-1 với OrderItem đơn COMPLETED)
--   8. Tự động tính toán điểm sao trung bình average_rating & review_count cho tất cả sản phẩm
-- =========================================================================================

BEGIN;

-- =========================================================================================
-- 0. ĐỒNG BỘ CẤU TRÚC BẢNG (DDL MIGRATION AN TOÀN TRÊN SUPABASE)
-- =========================================================================================

-- Khắc phục triệt để lỗi 22P02: Chuyển các cột @Lob bị Hibernate gán oid sang kiểu TEXT chuẩn Postgres
DO $$
BEGIN
  -- 1. Bảng products: description
  IF EXISTS (
    SELECT 1 FROM information_schema.columns 
    WHERE table_name = 'products' AND column_name = 'description' AND data_type != 'text'
  ) THEN
    BEGIN
      ALTER TABLE products ALTER COLUMN description TYPE TEXT USING description::text;
    EXCEPTION WHEN OTHERS THEN
      ALTER TABLE products DROP COLUMN IF EXISTS description CASCADE;
      ALTER TABLE products ADD COLUMN description TEXT;
    END;
  END IF;

  -- 2. Bảng reviews: comment
  IF EXISTS (
    SELECT 1 FROM information_schema.columns 
    WHERE table_name = 'reviews' AND column_name = 'comment' AND data_type != 'text'
  ) THEN
    BEGIN
      ALTER TABLE reviews ALTER COLUMN comment TYPE TEXT USING comment::text;
    EXCEPTION WHEN OTHERS THEN
      ALTER TABLE reviews DROP COLUMN IF EXISTS comment CASCADE;
      ALTER TABLE reviews ADD COLUMN comment TEXT;
    END;
  END IF;

  -- 3. Bảng disputes: description & resolution_note
  IF EXISTS (
    SELECT 1 FROM information_schema.columns 
    WHERE table_name = 'disputes' AND column_name = 'description' AND data_type != 'text'
  ) THEN
    BEGIN
      ALTER TABLE disputes ALTER COLUMN description TYPE TEXT USING description::text;
    EXCEPTION WHEN OTHERS THEN
      ALTER TABLE disputes DROP COLUMN IF EXISTS description CASCADE;
      ALTER TABLE disputes ADD COLUMN description TEXT;
    END;
  END IF;

  IF EXISTS (
    SELECT 1 FROM information_schema.columns 
    WHERE table_name = 'disputes' AND column_name = 'resolution_note' AND data_type != 'text'
  ) THEN
    BEGIN
      ALTER TABLE disputes ALTER COLUMN resolution_note TYPE TEXT USING resolution_note::text;
    EXCEPTION WHEN OTHERS THEN
      ALTER TABLE disputes DROP COLUMN IF EXISTS resolution_note CASCADE;
      ALTER TABLE disputes ADD COLUMN resolution_note TEXT;
    END;
  END IF;

  -- 4. Bảng dispute_responses: content
  IF EXISTS (
    SELECT 1 FROM information_schema.columns 
    WHERE table_name = 'dispute_responses' AND column_name = 'content' AND data_type != 'text'
  ) THEN
    BEGIN
      ALTER TABLE dispute_responses ALTER COLUMN content TYPE TEXT USING content::text;
    EXCEPTION WHEN OTHERS THEN
      ALTER TABLE dispute_responses DROP COLUMN IF EXISTS content CASCADE;
      ALTER TABLE dispute_responses ADD COLUMN content TEXT;
    END;
  END IF;
END $$;

-- Tạo bảng reviews nếu chưa có (cấu trúc mới không chứa seller_id)
CREATE TABLE IF NOT EXISTS reviews (
    id BIGSERIAL PRIMARY KEY,
    reviewer_id UUID NOT NULL REFERENCES users(id),
    product_id UUID NOT NULL REFERENCES products(id),
    order_id UUID NOT NULL REFERENCES orders(id),
    order_item_id BIGINT REFERENCES order_items(id),
    rating SMALLINT NOT NULL CHECK (rating >= 1 AND rating <= 5),
    comment TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ
);

-- Gỡ bỏ hoàn toàn cột seller_id nếu bảng reviews cũ trên Supabase còn lưu
ALTER TABLE reviews DROP COLUMN IF EXISTS seller_id CASCADE;

-- Xóa ràng buộc UNIQUE cũ trên (order_id) của bảng reviews (trước đây @OneToOne Order, nay đổi thành @ManyToOne để 1 đơn hàng đánh giá được nhiều sản phẩm)
ALTER TABLE reviews DROP CONSTRAINT IF EXISTS uksbkc1fll14ly5y6yxxk2jwlef CASCADE;

DO $$
DECLARE
  r RECORD;
BEGIN
  -- Quét và gỡ bỏ mọi UNIQUE constraint cũ trên cột order_id trong bảng reviews nếu có
  FOR r IN (
    SELECT c.conname 
    FROM pg_constraint c
    JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY(c.conkey)
    WHERE c.conrelid = 'reviews'::regclass 
      AND c.contype = 'u'
      AND a.attname = 'order_id'
  ) LOOP
    EXECUTE 'ALTER TABLE reviews DROP CONSTRAINT IF EXISTS ' || quote_ident(r.conname) || ' CASCADE';
  END LOOP;
END $$;

-- Đảm bảo các cột mới tồn tại nếu bảng reviews đã tạo trước đó
ALTER TABLE reviews ADD COLUMN IF NOT EXISTS product_id UUID REFERENCES products(id);
ALTER TABLE reviews ADD COLUMN IF NOT EXISTS order_id UUID REFERENCES orders(id);
ALTER TABLE reviews ADD COLUMN IF NOT EXISTS order_item_id BIGINT REFERENCES order_items(id);
ALTER TABLE reviews ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ;

-- Bảng lưu link ảnh chụp thành phẩm in 3D thực tế của review
CREATE TABLE IF NOT EXISTS review_images (
    review_id BIGINT NOT NULL REFERENCES reviews(id) ON DELETE CASCADE,
    image_url VARCHAR(500) NOT NULL
);

-- Đảm bảo bảng products có 2 cột lưu điểm sao trung bình và số lượt đánh giá
ALTER TABLE products ADD COLUMN IF NOT EXISTS average_rating DOUBLE PRECISION DEFAULT 0.0;
ALTER TABLE products ADD COLUMN IF NOT EXISTS review_count INTEGER DEFAULT 0;

-- Đảm bảo cột order_id trong bảng payments cho phép NULL (để hỗ trợ thanh toán CheckoutOrder và CustomOrder)
ALTER TABLE payments ALTER COLUMN order_id DROP NOT NULL;


-- =========================================================================================
-- 0.1. DỌN DẸP DỮ LIỆU CŨ THEO ĐÚNG THỨ TỰ RÀNG BUỘC KHÓA NGOẠI (TRÁNH LỖI KHI CHẠY LẠI NHIỀU LẦN)
-- =========================================================================================
DELETE FROM review_images WHERE review_id IN (1, 2, 3, 4, 5);
DELETE FROM reviews WHERE id IN (1, 2, 3, 4, 5);
DELETE FROM dispute_responses WHERE dispute_id IN (
  'd0000001-0001-0001-0001-000000000001',
  'd0000001-0001-0001-0001-000000000002',
  'd0000001-0001-0001-0001-000000000003'
);
DELETE FROM disputes WHERE id IN (
  'd0000001-0001-0001-0001-000000000001',
  'd0000001-0001-0001-0001-000000000002',
  'd0000001-0001-0001-0001-000000000003'
);
DELETE FROM payments WHERE order_id IN (
  'e0000001-0001-0001-0001-000000000001',
  'e0000001-0001-0001-0001-000000000002',
  'e0000001-0001-0001-0001-000000000003',
  'e0000001-0001-0001-0001-000000000004'
);
DELETE FROM shipping_infos WHERE order_id IN (
  'e0000001-0001-0001-0001-000000000001',
  'e0000001-0001-0001-0001-000000000002',
  'e0000001-0001-0001-0001-000000000003',
  'e0000001-0001-0001-0001-000000000004'
);
DELETE FROM order_items WHERE order_id IN (
  'e0000001-0001-0001-0001-000000000001',
  'e0000001-0001-0001-0001-000000000002',
  'e0000001-0001-0001-0001-000000000003',
  'e0000001-0001-0001-0001-000000000004'
);
DELETE FROM orders WHERE id IN (
  'e0000001-0001-0001-0001-000000000001',
  'e0000001-0001-0001-0001-000000000002',
  'e0000001-0001-0001-0001-000000000003',
  'e0000001-0001-0001-0001-000000000004'
);


-- =========================================================================================
-- 1. ĐẢM BẢO USER MẪU VÀ VÍ ĐIỂM THƯỞNG POINT WALLET
-- =========================================================================================
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM users WHERE id = '3f65fede-a289-4857-945a-df3f200d5e13') THEN
    IF EXISTS (SELECT 1 FROM users WHERE email = 'khanhle0209c@gmail.com') THEN
      UPDATE users 
      SET id = '3f65fede-a289-4857-945a-df3f200d5e13' 
      WHERE email = 'khanhle0209c@gmail.com';
    ELSE
      INSERT INTO users (id, role, username, email, password, full_name, phone, address, is_active, created_at, updated_at)
      VALUES (
        '3f65fede-a289-4857-945a-df3f200d5e13',
        'USER',
        'khanhle0209c',
        'khanhle0209c@gmail.com',
        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcg7b3XeKeUxWdeS86E3l0CvWmK',
        'Lê Quốc Khánh',
        '0786954657',
        'Khu Công Nghệ Cao, P. Long Thạnh Mỹ, TP. Thủ Đức, TP. Hồ Chí Minh',
        TRUE,
        NOW(),
        NOW()
      );
    END IF;
  END IF;
END $$;

INSERT INTO point_wallets (user_id, balance, updated_at)
VALUES ('3f65fede-a289-4857-945a-df3f200d5e13', 250, NOW())
ON CONFLICT (user_id) DO UPDATE 
SET balance = 250, updated_at = NOW();


-- =========================================================================================
-- 2. TẠO CÁC GÓI SUBSCRIPTION VÀ KÍCH HOẠT CHO USER
-- =========================================================================================
INSERT INTO subscription_plans (id, type, name, price, benefits, required_points, is_active, created_at, updated_at)
VALUES 
  ('11111111-1111-1111-1111-111111111101', 'CUSTOMER', 'Gói Sinh Viên Maker (Student Pro)', 49000.00, 
   'Giảm 5% mọi đơn in thước 3D & custom; Miễn phí tải 10+ file thiết kế thước độc quyền hàng tháng; Lưu trữ file CAD 50GB; Ưu tiên lên máy in.', 100, TRUE, NOW(), NOW()),
  ('11111111-1111-1111-1111-111111111102', 'CUSTOMER_VIP', 'Gói Đồ Án VIP (Ultra Creator)', 129000.00, 
   'Giảm 15% tổng hóa đơn in & gia công; Miễn phí vận chuyển toàn quốc cho đơn từ 100k; Miễn phí khắc tên & mã số sinh viên laser; In hỏa tốc lấy ngay 24h.', 300, TRUE, NOW(), NOW())
ON CONFLICT (id) DO UPDATE 
SET name = EXCLUDED.name, price = EXCLUDED.price, benefits = EXCLUDED.benefits, is_active = TRUE, updated_at = NOW();

-- Kích hoạt gói VIP cho user
INSERT INTO user_subscriptions (id, user_id, plan_id, start_date, end_date, is_active, created_at, updated_at)
VALUES (
  '22222222-2222-2222-2222-222222222201',
  '3f65fede-a289-4857-945a-df3f200d5e13',
  '11111111-1111-1111-1111-111111111102',
  NOW() - INTERVAL '5 days',
  NOW() + INTERVAL '25 days',
  TRUE,
  NOW() - INTERVAL '5 days',
  NOW()
)
ON CONFLICT (id) DO UPDATE 
SET is_active = TRUE, end_date = NOW() + INTERVAL '25 days', updated_at = NOW();


-- =========================================================================================
-- 3. DANH MỤC THƯỚC IN 3D (CATEGORIES)
-- =========================================================================================
INSERT INTO categories (category_id, category_name, description)
VALUES 
  (1, 'Thước Học Tập & Đồ Án', 'Dòng thước kỹ thuật chính xác cao, thước thẳng, thước đo đồ án cho sinh viên khối ngành kỹ thuật & kiến trúc'),
  (2, 'Thước Trendy Gen Z (TikTok Viral)', 'Các mẫu thước in 3D tạo hình độc lạ: kiếm Katana, gai khủng long, đuôi thú cưng viral giới trẻ'),
  (3, 'Thước Tiện Ích Đa Năng', 'Thước kẹp sách bookmark, thước móc khóa treo balo mini bỏ túi không sợ quên khi đến trường'),
  (4, 'Thước Cơ Khí & Khuôn Vẽ Stencil', 'Thước kẹp mini Vernier Caliper, khuôn vẽ stencil hình học và mạch điện chuyên dụng')
ON CONFLICT (category_id) DO UPDATE 
SET category_name = EXCLUDED.category_name, description = EXCLUDED.description;

-- Cập nhật sequence an toàn cho categories
DO $$
DECLARE
  seq_name TEXT;
  max_id BIGINT;
BEGIN
  seq_name := pg_get_serial_sequence('categories', 'category_id');
  IF seq_name IS NOT NULL THEN
    SELECT COALESCE(MAX(category_id), 1) INTO max_id FROM categories;
    PERFORM setval(seq_name, max_id);
  END IF;
END $$;


-- =========================================================================================
-- 4. 10 SẢN PHẨM THƯỚC IN 3D THẬT (GIÁ TỪ 15.000đ - 49.000đ)
-- =========================================================================================
INSERT INTO products (id, seller_id, category_id, title, description, price, stock, type, status, average_rating, review_count, created_at, updated_at)
VALUES 
  -- Thước 1: 15.000đ
  ('c0000001-0000-0000-0000-000000000001', '3f65fede-a289-4857-945a-df3f200d5e13', 3,
   'Thước Móc Khóa Balo Sinh Viên 10cm In 3D (PLA Dạ Quang)',
   'Thước thẳng mini 10cm tích hợp khoen móc khóa treo balo học sinh sinh viên. In 3D bằng nhựa PLA Pro dạ quang phát sáng trong đêm, tiện lợi đo nhanh tài liệu không sợ quên ở nhà.',
   15000.00, 150, 'PHYSICAL', 'ACTIVE', 5.0, 1, NOW() - INTERVAL '20 days', NOW()),

  -- Thước 2: 18.000đ
  ('c0000001-0000-0000-0000-000000000002', '3f65fede-a289-4857-945a-df3f200d5e13', 3,
   'Thước Bookmark Kẹp Đánh Dấu Sách 15cm Siêu Mỏng',
   'Độ dày siêu mỏng chỉ 0.8mm dẻo dai làm thẻ kẹp trang sách giáo trình. Vạch chia 15cm in nổi hai màu tương phản cao, dễ đọc số và không làm hằn cấn trang sách.',
   18000.00, 120, 'PHYSICAL', 'ACTIVE', 4.0, 1, NOW() - INTERVAL '19 days', NOW()),

  -- Thước 3: 22.000đ
  ('c0000001-0000-0000-0000-000000000003', '3f65fede-a289-4857-945a-df3f200d5e13', 1,
   'Thước Kỹ Thuật Bỏ Túi 15cm Khắc Tên Sinh Viên (Pocket Edition)',
   'Kích thước nhỏ gọn 15cm x 2.5cm chuẩn bỏ vừa mọi loại bóp viết. Hỗ trợ khắc tên riêng và mã số sinh viên theo yêu cầu, chất liệu PLA+ chống cong vênh.',
   22000.00, 100, 'PHYSICAL', 'ACTIVE', 0.0, 0, NOW() - INTERVAL '18 days', NOW()),

  -- Thước 4: 28.000đ
  ('c0000001-0000-0000-0000-000000000004', '3f65fede-a289-4857-945a-df3f200d5e13', 2,
   'Thước Đuôi Mèo & Dấu Chân Thú Cưng 18cm (Cute Cat Paw)',
   'Tạo hình dễ thương với hoạ tiết dấu chân mèo và đuôi thú cưng. Cạnh thước có rãnh uốn lượn sóng tạo nét gạch chân highlight tài liệu cực xinh xắn.',
   28000.00, 85, 'PHYSICAL', 'ACTIVE', 0.0, 0, NOW() - INTERVAL '17 days', NOW()),

  -- Thước 5: 32.000đ
  ('c0000001-0000-0000-0000-000000000005', '3f65fede-a289-4857-945a-df3f200d5e13', 2,
   'Thước Gai Khủng Long Dino Spine 20cm (Trendy Gen Z)',
   'Lấy cảm hứng từ gai sống lưng khủng long bạo chúa, phong cách cá tính viral TikTok. Nhựa PLA hoàn thiện nhám mờ chống trơn trượt khi kẻ bút bi mực nước.',
   32000.00, 70, 'PHYSICAL', 'ACTIVE', 0.0, 0, NOW() - INTERVAL '16 days', NOW()),

  -- Thước 6: 35.000đ (Best-seller)
  ('c0000001-0000-0000-0000-000000000006', '3f65fede-a289-4857-945a-df3f200d5e13', 1,
   'Thước Kỹ Thuật PLA Pro 20cm (Khắc Tên & MSSV Chống Bay Số)',
   'Sản phẩm best-seller của PrintHub! Độ chia nhỏ nhất 0.5mm, rãnh số dập chìm hai màu laser chống bay số, viền vát cạnh công thái học không làm lem mực bút vẽ kỹ thuật.',
   35000.00, 200, 'PHYSICAL', 'ACTIVE', 5.0, 1, NOW() - INTERVAL '15 days', NOW()),

  -- Thước 7: 39.000đ
  ('c0000001-0000-0000-0000-000000000007', '3f65fede-a289-4857-945a-df3f200d5e13', 4,
   'Thước Vẽ Kỹ Thuật Stencil Đa Năng (Geometry & Sơ Đồ Khối)',
   'Tích hợp các khuôn vẽ hình học: vòng tròn R2-R25, tam giác đều, elip, góc đo 180 độ và ký hiệu mạch điện tử. Công cụ hỗ trợ vẽ sơ đồ đồ án nhanh gấp 3 lần.',
   39000.00, 90, 'PHYSICAL', 'ACTIVE', 0.0, 0, NOW() - INTERVAL '14 days', NOW()),

  -- Thước 8: 42.000đ
  ('c0000001-0000-0000-0000-000000000008', '3f65fede-a289-4857-945a-df3f200d5e13', 1,
   'Thước Parabol / Đồ Thị Toán Học & Hàm Số Bậc Hai',
   'Biên dạng Parabol cong tiêu chuẩn chính xác tuyệt đối, hỗ trợ học sinh sinh viên khối A vẽ đồ thị hàm số và xác định đỉnh cực trị nhanh chóng trong phòng thi.',
   42000.00, 65, 'PHYSICAL', 'ACTIVE', 0.0, 0, NOW() - INTERVAL '13 days', NOW()),

  -- Thước 9: 46.000đ
  ('c0000001-0000-0000-0000-000000000009', '3f65fede-a289-4857-945a-df3f200d5e13', 2,
   'Thước Kiếm Katana Pixel Sword 8-Bit 22cm (Gamer Edition)',
   'Tạo hình thanh kiếm Samurai Pixel Art phong cách game 8-bit cực ngầu. Vừa làm thước đo 22cm vạch chia sắc nét, vừa làm vật trang trí độc đáo trên bàn học.',
   46000.00, 60, 'PHYSICAL', 'ACTIVE', 5.0, 1, NOW() - INTERVAL '12 days', NOW()),

  -- Thước 10: 49.000đ
  ('c0000001-0000-0000-0000-000000000010', '3f65fede-a289-4857-945a-df3f200d5e13', 4,
   'Thước Kẹp Mini Vernier Caliper In 3D (Độ Chính Xác 0.1mm)',
   'Thước kẹp cơ khí in 3D với cơ cấu trượt êm mượt, tích hợp ngàm đo đường kính trong, đường kính ngoài và cữ đo độ sâu chi tiết in 3D cho dân kỹ thuật.',
   49000.00, 50, 'PHYSICAL', 'ACTIVE', 5.0, 1, NOW() - INTERVAL '11 days', NOW())
ON CONFLICT (id) DO UPDATE 
SET title = EXCLUDED.title, price = EXCLUDED.price, description = EXCLUDED.description, stock = EXCLUDED.stock, category_id = EXCLUDED.category_id, updated_at = NOW();

-- Hình ảnh minh họa chất lượng cao cho 10 mẫu thước
DELETE FROM product_images WHERE product_id IN (
  'c0000001-0000-0000-0000-000000000001', 'c0000001-0000-0000-0000-000000000002',
  'c0000001-0000-0000-0000-000000000003', 'c0000001-0000-0000-0000-000000000004',
  'c0000001-0000-0000-0000-000000000005', 'c0000001-0000-0000-0000-000000000006',
  'c0000001-0000-0000-0000-000000000007', 'c0000001-0000-0000-0000-000000000008',
  'c0000001-0000-0000-0000-000000000009', 'c0000001-0000-0000-0000-000000000010'
);

INSERT INTO product_images (product_id, image_url, is_primary)
VALUES 
  ('c0000001-0000-0000-0000-000000000001', 'https://images.unsplash.com/photo-1596495577886-d920f1fb7238?auto=format&fit=crop&w=600&q=80', TRUE),
  ('c0000001-0000-0000-0000-000000000002', 'https://images.unsplash.com/photo-1589739900266-43b2843f4c12?auto=format&fit=crop&w=600&q=80', TRUE),
  ('c0000001-0000-0000-0000-000000000003', 'https://images.unsplash.com/photo-1596495577886-d920f1fb7238?auto=format&fit=crop&w=600&q=80', TRUE),
  ('c0000001-0000-0000-0000-000000000004', 'https://images.unsplash.com/photo-1586953208448-b95a79798f07?auto=format&fit=crop&w=600&q=80', TRUE),
  ('c0000001-0000-0000-0000-000000000005', 'https://images.unsplash.com/photo-1546776310-eef45dd6d63c?auto=format&fit=crop&w=600&q=80', TRUE),
  ('c0000001-0000-0000-0000-000000000006', 'https://images.unsplash.com/photo-1596495577886-d920f1fb7238?auto=format&fit=crop&w=600&q=80', TRUE),
  ('c0000001-0000-0000-0000-000000000007', 'https://images.unsplash.com/photo-1589739900266-43b2843f4c12?auto=format&fit=crop&w=600&q=80', TRUE),
  ('c0000001-0000-0000-0000-000000000008', 'https://images.unsplash.com/photo-1596495577886-d920f1fb7238?auto=format&fit=crop&w=600&q=80', TRUE),
  ('c0000001-0000-0000-0000-000000000009', 'https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?auto=format&fit=crop&w=600&q=80', TRUE),
  ('c0000001-0000-0000-0000-000000000010', 'https://images.unsplash.com/photo-1581092160607-ee22621dd758?auto=format&fit=crop&w=600&q=80', TRUE);


-- =========================================================================================
-- 5. 4 ĐƠN HÀNG THƯỚC IN 3D CỦA USER Ở CÁC TRẠNG THÁI KHÁC NHAU
-- =========================================================================================

-- -----------------------------------------------------------------------------------------
-- ĐƠN HÀNG 1: Trạng thái COMPLETED (82.000đ - Đã giao thành công 12 ngày trước)
-- Gồm:
--   Item 101: Thước Kỹ Thuật 20cm (35k) -> ĐÃ ĐÁNH GIÁ (Review 1, 5★)
--   Item 102: Thước Móc Khóa Balo 10cm (15k) -> ĐÃ ĐÁNH GIÁ (Review 2, 5★)
--   Item 103: Thước Gai Khủng Long 20cm (32k) -> CHƯA ĐÁNH GIÁ (Hiện nút ⭐ Đánh giá sản phẩm trên UI để test)
-- -----------------------------------------------------------------------------------------
INSERT INTO orders (id, buyer_id, seller_id, total_amount, commission_fee, status, reward_processed, reward_points_earned, created_at, updated_at)
VALUES (
  'e0000001-0001-0001-0001-000000000001',
  '3f65fede-a289-4857-945a-df3f200d5e13',
  '3f65fede-a289-4857-945a-df3f200d5e13',
  82000.00,
  4100.00,
  'COMPLETED',
  TRUE,
  82,
  NOW() - INTERVAL '15 days',
  NOW() - INTERVAL '12 days'
)
ON CONFLICT (id) DO UPDATE 
SET status = 'COMPLETED', total_amount = 82000.00, updated_at = NOW();

INSERT INTO order_items (id, order_id, product_id, quantity, unit_price, color, engraving_text)
VALUES 
  (101, 'e0000001-0001-0001-0001-000000000001', 'c0000001-0000-0000-0000-000000000006', 1, 35000.00, 'Xanh Lá (Emerald)', 'FPTU - KHANH LE K17'),
  (102, 'e0000001-0001-0001-0001-000000000001', 'c0000001-0000-0000-0000-000000000001', 1, 15000.00, 'Trắng Dạ Quang', 'MINI 10CM'),
  (103, 'e0000001-0001-0001-0001-000000000001', 'c0000001-0000-0000-0000-000000000005', 1, 32000.00, 'Đen Nhám (Matte Black)', 'DINO T-REX')
ON CONFLICT (id) DO UPDATE 
SET product_id = EXCLUDED.product_id, quantity = EXCLUDED.quantity, unit_price = EXCLUDED.unit_price, color = EXCLUDED.color, engraving_text = EXCLUDED.engraving_text;

INSERT INTO shipping_infos (order_id, recipient_name, phone, address, province, tracking_number, shipped_at)
VALUES (
  'e0000001-0001-0001-0001-000000000001',
  'Lê Quốc Khánh',
  '0786954657',
  'Khu Công Nghệ Cao, P. Long Thạnh Mỹ, TP. Thủ Đức',
  'TP. Hồ Chí Minh',
  'VNPOST77281903VN',
  NOW() - INTERVAL '14 days'
)
ON CONFLICT (order_id) DO UPDATE 
SET recipient_name = EXCLUDED.recipient_name, phone = EXCLUDED.phone, address = EXCLUDED.address, tracking_number = EXCLUDED.tracking_number;

INSERT INTO payments (id, order_id, amount, gateway, status, transaction_id, paid_at, created_at, updated_at)
VALUES (
  'fa000001-0001-0001-0001-000000000001',
  'e0000001-0001-0001-0001-000000000001',
  82000.00,
  'PAYOS',
  'SUCCESS',
  'PAYOS-TXN-88492015',
  NOW() - INTERVAL '15 days',
  NOW() - INTERVAL '15 days',
  NOW() - INTERVAL '15 days'
)
ON CONFLICT (id) DO UPDATE 
SET status = 'SUCCESS', amount = EXCLUDED.amount, updated_at = NOW();

-- -----------------------------------------------------------------------------------------
-- ĐƠN HÀNG 2: Trạng thái SHIPPING (46.000đ - Đang giao hàng qua GHN, đã thanh toán PayOS)
-- Gồm: 1 Thước Kiếm Katana Pixel Sword 8-Bit (46k)
-- -----------------------------------------------------------------------------------------
INSERT INTO orders (id, buyer_id, seller_id, total_amount, commission_fee, status, reward_processed, reward_points_earned, created_at, updated_at)
VALUES (
  'e0000001-0001-0001-0001-000000000002',
  '3f65fede-a289-4857-945a-df3f200d5e13',
  '3f65fede-a289-4857-945a-df3f200d5e13',
  46000.00,
  2300.00,
  'SHIPPING',
  FALSE,
  46,
  NOW() - INTERVAL '3 days',
  NOW() - INTERVAL '1 days'
)
ON CONFLICT (id) DO UPDATE 
SET status = 'SHIPPING', total_amount = 46000.00, updated_at = NOW();

INSERT INTO order_items (id, order_id, product_id, quantity, unit_price, color, engraving_text)
VALUES 
  (104, 'e0000001-0001-0001-0001-000000000002', 'c0000001-0000-0000-0000-000000000009', 1, 46000.00, 'Đen Nhám (Industrial)', '[ KATANA PIXEL ]')
ON CONFLICT (id) DO UPDATE 
SET product_id = EXCLUDED.product_id, quantity = EXCLUDED.quantity, unit_price = EXCLUDED.unit_price, color = EXCLUDED.color, engraving_text = EXCLUDED.engraving_text;

INSERT INTO shipping_infos (order_id, recipient_name, phone, address, province, tracking_number, shipped_at)
VALUES (
  'e0000001-0001-0001-0001-000000000002',
  'Lê Quốc Khánh',
  '0786954657',
  'Phòng 402, Ký túc xá Đại học FPT, Đường D1, Long Thạnh Mỹ',
  'TP. Hồ Chí Minh',
  'GHN88291039VN',
  NOW() - INTERVAL '2 days'
)
ON CONFLICT (order_id) DO UPDATE 
SET recipient_name = EXCLUDED.recipient_name, phone = EXCLUDED.phone, address = EXCLUDED.address, tracking_number = EXCLUDED.tracking_number;

INSERT INTO payments (id, order_id, amount, gateway, status, transaction_id, paid_at, created_at, updated_at)
VALUES (
  'fa000001-0001-0001-0001-000000000002',
  'e0000001-0001-0001-0001-000000000002',
  46000.00,
  'PAYOS',
  'SUCCESS',
  'PAYOS-TXN-99381023',
  NOW() - INTERVAL '3 days',
  NOW() - INTERVAL '3 days',
  NOW() - INTERVAL '3 days'
)
ON CONFLICT (id) DO UPDATE 
SET status = 'SUCCESS', amount = EXCLUDED.amount, updated_at = NOW();

-- -----------------------------------------------------------------------------------------
-- ĐƠN HÀNG 3: Trạng thái PENDING (50.000đ - Chờ duyệt & xác nhận giao hàng COD)
-- Gồm: 1 Thước Đuôi Mèo (28k) + 1 Thước Kỹ Thuật Bỏ Túi (22k) = 50k
-- -----------------------------------------------------------------------------------------
INSERT INTO orders (id, buyer_id, seller_id, total_amount, commission_fee, status, reward_processed, reward_points_earned, created_at, updated_at)
VALUES (
  'e0000001-0001-0001-0001-000000000003',
  '3f65fede-a289-4857-945a-df3f200d5e13',
  '3f65fede-a289-4857-945a-df3f200d5e13',
  50000.00,
  2500.00,
  'PENDING',
  FALSE,
  0,
  NOW() - INTERVAL '3 hours',
  NOW() - INTERVAL '3 hours'
)
ON CONFLICT (id) DO UPDATE 
SET status = 'PENDING', total_amount = 50000.00, updated_at = NOW();

INSERT INTO order_items (id, order_id, product_id, quantity, unit_price, color, engraving_text)
VALUES 
  (105, 'e0000001-0001-0001-0001-000000000003', 'c0000001-0000-0000-0000-000000000004', 1, 28000.00, 'Tím Cyberpunk', 'CAT PAW'),
  (106, 'e0000001-0001-0001-0001-000000000003', 'c0000001-0000-0000-0000-000000000003', 1, 22000.00, 'Cam Neon (Bright Orange)', 'POCKET 15CM')
ON CONFLICT (id) DO UPDATE 
SET product_id = EXCLUDED.product_id, quantity = EXCLUDED.quantity, unit_price = EXCLUDED.unit_price, color = EXCLUDED.color, engraving_text = EXCLUDED.engraving_text;

INSERT INTO shipping_infos (order_id, recipient_name, phone, address, province, tracking_number, shipped_at)
VALUES (
  'e0000001-0001-0001-0001-000000000003',
  'Lê Quốc Khánh',
  '0786954657',
  '12 Đường Nam Kỳ Khởi Nghĩa, P. Bến Nghé, Quận 1',
  'TP. Hồ Chí Minh',
  NULL,
  NULL
)
ON CONFLICT (order_id) DO UPDATE 
SET recipient_name = EXCLUDED.recipient_name, phone = EXCLUDED.phone, address = EXCLUDED.address;

INSERT INTO payments (id, order_id, amount, gateway, status, transaction_id, paid_at, created_at, updated_at)
VALUES (
  'fa000001-0001-0001-0001-000000000003',
  'e0000001-0001-0001-0001-000000000003',
  50000.00,
  'COD',
  'PENDING',
  NULL,
  NULL,
  NOW() - INTERVAL '3 hours',
  NOW() - INTERVAL '3 hours'
)
ON CONFLICT (id) DO UPDATE 
SET status = 'PENDING', amount = EXCLUDED.amount, updated_at = NOW();

-- -----------------------------------------------------------------------------------------
-- ĐƠN HÀNG 4: Trạng thái COMPLETED (113.000đ - Đã giao thành công 25 ngày trước)
-- Gồm:
--   Item 107: Thước Kiếm Katana (46k) -> ĐÃ ĐÁNH GIÁ (Review 3, 5★)
--   Item 108: Thước Bookmark Kẹp Sách (18k) -> ĐÃ ĐÁNH GIÁ (Review 4, 4★)
--   Item 109: Thước Kẹp Mini Vernier Caliper (49k) -> ĐÃ ĐÁNH GIÁ (Review 5, 5★)
-- -----------------------------------------------------------------------------------------
INSERT INTO orders (id, buyer_id, seller_id, total_amount, commission_fee, status, reward_processed, reward_points_earned, created_at, updated_at)
VALUES (
  'e0000001-0001-0001-0001-000000000004',
  '3f65fede-a289-4857-945a-df3f200d5e13',
  '3f65fede-a289-4857-945a-df3f200d5e13',
  113000.00,
  5650.00,
  'COMPLETED',
  TRUE,
  113,
  NOW() - INTERVAL '28 days',
  NOW() - INTERVAL '25 days'
)
ON CONFLICT (id) DO UPDATE 
SET status = 'COMPLETED', total_amount = 113000.00, updated_at = NOW();

INSERT INTO order_items (id, order_id, product_id, quantity, unit_price, color, engraving_text)
VALUES 
  (107, 'e0000001-0001-0001-0001-000000000004', 'c0000001-0000-0000-0000-000000000009', 1, 46000.00, 'Đen Samurai', 'KATANA 22CM'),
  (108, 'e0000001-0001-0001-0001-000000000004', 'c0000001-0000-0000-0000-000000000002', 1, 18000.00, 'Xanh Dương Pastel', 'BOOKMARK 15CM'),
  (109, 'e0000001-0001-0001-0001-000000000004', 'c0000001-0000-0000-0000-000000000010', 1, 49000.00, 'Xám Bạc (Silver)', 'VERNIER 0.1MM')
ON CONFLICT (id) DO UPDATE 
SET product_id = EXCLUDED.product_id, quantity = EXCLUDED.quantity, unit_price = EXCLUDED.unit_price, color = EXCLUDED.color, engraving_text = EXCLUDED.engraving_text;

INSERT INTO shipping_infos (order_id, recipient_name, phone, address, province, tracking_number, shipped_at)
VALUES (
  'e0000001-0001-0001-0001-000000000004',
  'Lê Quốc Khánh',
  '0786954657',
  'Khu Công Nghệ Cao, P. Long Thạnh Mỹ, TP. Thủ Đức',
  'TP. Hồ Chí Minh',
  'VNPOST99821455VN',
  NOW() - INTERVAL '27 days'
)
ON CONFLICT (order_id) DO UPDATE 
SET recipient_name = EXCLUDED.recipient_name, phone = EXCLUDED.phone, address = EXCLUDED.address, tracking_number = EXCLUDED.tracking_number;

INSERT INTO payments (id, order_id, amount, gateway, status, transaction_id, paid_at, created_at, updated_at)
VALUES (
  'fa000001-0001-0001-0001-000000000004',
  'e0000001-0001-0001-0001-000000000004',
  113000.00,
  'PAYOS',
  'SUCCESS',
  'PAYOS-TXN-77381920',
  NOW() - INTERVAL '28 days',
  NOW() - INTERVAL '28 days',
  NOW() - INTERVAL '28 days'
)
ON CONFLICT (id) DO UPDATE 
SET status = 'SUCCESS', amount = EXCLUDED.amount, updated_at = NOW();

-- Cập nhật sequence an toàn cho order_items tránh xung đột ID tự sinh
DO $$
DECLARE
  seq_name TEXT;
  max_id BIGINT;
BEGIN
  seq_name := pg_get_serial_sequence('order_items', 'id');
  IF seq_name IS NOT NULL THEN
    SELECT COALESCE(MAX(id), 1) INTO max_id FROM order_items;
    PERFORM setval(seq_name, max_id);
  END IF;
END $$;


-- =========================================================================================
-- 6. 3 KHIẾU NẠI (DISPUTES) THỰC TẾ VỀ THƯỚC IN 3D
-- =========================================================================================

-- Khiếu nại 1: Trạng thái UNDER_REVIEW (Đơn SHIPPING)
INSERT INTO disputes (id, order_id, filed_by, description, evidence_url, status, resolution_note, created_at, updated_at)
VALUES (
  'd0000001-0001-0001-0001-000000000001',
  'e0000001-0001-0001-0001-000000000002',
  '3f65fede-a289-4857-945a-df3f200d5e13',
  'Cây Thước Kiếm Katana Pixel Sword 22cm khi shipper GHN giao tới bị nứt gãy phần chuôi kiếm và mẻ mất 2 vạch chia milimet ở mũi thước. Kiện hàng có dấu hiệu bị vật nặng đè ép trong thùng vận chuyển. Tôi đã đồng kiểm và chụp ảnh bao bì.',
  'https://images.unsplash.com/photo-1584438784894-089d6a62b8fa?auto=format&fit=crop&w=800&q=80',
  'UNDER_REVIEW',
  'CSKH PrintHub đã tiếp nhận hình ảnh đối soát và đang phối hợp xử lý bồi hoàn cùng bưu cục GHN. Xưởng in 3D đang cho chạy lệnh in lại một cây Thước Kiếm Katana mới nguyên vẹn để bàn giao hỏa tốc cho bạn.',
  NOW() - INTERVAL '1 days',
  NOW() - INTERVAL '6 hours'
)
ON CONFLICT (id) DO UPDATE 
SET status = 'UNDER_REVIEW', description = EXCLUDED.description, resolution_note = EXCLUDED.resolution_note, updated_at = NOW();

INSERT INTO dispute_responses (dispute_id, responded_by, content, evidence_url, created_at)
VALUES 
  ('d0000001-0001-0001-0001-000000000001', '3f65fede-a289-4857-945a-df3f200d5e13', 
   'Chào Admin, mình gửi ảnh chụp cây thước kiếm Katana lúc khui hộp bị gãy chuôi rồi nhé. Shop xem giúp mình với vì mình cần dùng kịp nộp đồ án cuối tuần này.', 
   'https://images.unsplash.com/photo-1584438784894-089d6a62b8fa?auto=format&fit=crop&w=800&q=80', NOW() - INTERVAL '1 days'),
  ('d0000001-0001-0001-0001-000000000001', '3f65fede-a289-4857-945a-df3f200d5e13', 
   'Dạ chào bạn Khánh, PrintHub rất xin lỗi vì sơ sót của đơn vị vận chuyển GHN! Xưởng in đã ưu tiên in lại cây kiếm Katana mới với mật độ infill 100% gia cường chắc chắn hơn và sẽ gửi hỏa tốc cho bạn trong ngày hôm nay.', 
   NULL, NOW() - INTERVAL '6 hours');

-- Khiếu nại 2: Trạng thái RESOLVED (Đơn COMPLETED 4)
INSERT INTO disputes (id, order_id, filed_by, description, evidence_url, status, resolution_note, created_at, updated_at)
VALUES (
  'd0000001-0001-0001-0001-000000000002',
  'e0000001-0001-0001-0001-000000000004',
  '3f65fede-a289-4857-945a-df3f200d5e13',
  'Thước Kỹ Thuật PLA Pro 20cm bị in nhầm nội dung khắc chữ: tôi ghi chú khắc "FPTU - KHANH LE K17" nhưng khi nhận thước lại khắc thành "FPTU - KHANH LE K16". Ngoài ra vạch số 10cm bị lem vệt mực.',
  'https://images.unsplash.com/photo-1589739900266-43b2843f4c12?auto=format&fit=crop&w=800&q=80',
  'RESOLVED',
  'Kỹ thuật viên PrintHub xác nhận lỗi nhầm file cấu hình khắc laser G-code. Shop đã in lại hoàn toàn miễn phí cây thước mới đúng chuẩn K17 gửi tận tay khách hàng, đồng thời tặng kèm mã voucher giảm 20% cho đơn đặt in tiếp theo.',
  NOW() - INTERVAL '26 days',
  NOW() - INTERVAL '24 days'
)
ON CONFLICT (id) DO UPDATE 
SET status = 'RESOLVED', description = EXCLUDED.description, resolution_note = EXCLUDED.resolution_note, updated_at = NOW();

INSERT INTO dispute_responses (dispute_id, responded_by, content, evidence_url, created_at)
VALUES 
  ('d0000001-0001-0001-0001-000000000002', '3f65fede-a289-4857-945a-df3f200d5e13', 
   'Cây thước 20cm bị in nhầm K16 rồi shop ơi, mình học khóa K17 cơ.', 
   NULL, NOW() - INTERVAL '26 days'),
  ('d0000001-0001-0001-0001-000000000002', '3f65fede-a289-4857-945a-df3f200d5e13', 
   'PrintHub chân thành xin lỗi bạn Khánh ạ. Bên mình đã làm lại cây thước mới đúng tên và mã K17, đồng thời gửi mã bưu tá Viettel Post 77281903VN đến địa chỉ của bạn rồi nhé. Cảm ơn bạn!', 
   NULL, NOW() - INTERVAL '24 days');

-- Khiếu nại 3: Trạng thái REJECTED (Đơn PENDING 3)
INSERT INTO disputes (id, order_id, filed_by, description, evidence_url, status, resolution_note, created_at, updated_at)
VALUES (
  'd0000001-0001-0001-0001-000000000003',
  'e0000001-0001-0001-0001-000000000003',
  '3f65fede-a289-4857-945a-df3f200d5e13',
  'Tôi muốn yêu cầu hủy đơn hàng Đuôi Mèo và hoàn cọc vì muốn đổi ý sang màu Vàng Hoàng Gia sau khi xưởng in đã nạp cuộn nhựa và bắt đầu in lớp đế (first layer).',
  NULL,
  'REJECTED',
  'Theo Quy định dịch vụ in thước 3D cá nhân hóa tại Điều 4.2 của PrintHub: Đơn sau khi đã chuyển sang trạng thái bắt đầu gia công nạp vật liệu in sẽ không hỗ trợ hủy ngang hoặc đổi sợi nhựa đã cắt định hình. Khiếu nại không đủ điều kiện giải quyết.',
  NOW() - INTERVAL '2 hours',
  NOW() - INTERVAL '30 minutes'
)
ON CONFLICT (id) DO UPDATE 
SET status = 'REJECTED', description = EXCLUDED.description, resolution_note = EXCLUDED.resolution_note, updated_at = NOW();

INSERT INTO dispute_responses (dispute_id, responded_by, content, evidence_url, created_at)
VALUES 
  ('d0000001-0001-0001-0001-000000000003', '3f65fede-a289-4857-945a-df3f200d5e13', 
   'Mình muốn hủy cây thước đuôi mèo màu tím này để đặt lại màu vàng được không shop?', 
   NULL, NOW() - INTERVAL '2 hours'),
  ('d0000001-0001-0001-0001-000000000003', '3f65fede-a289-4857-945a-df3f200d5e13', 
   'Dạ chào bạn Khánh, máy in 3D đã nạp cuộn nhựa và chạy lớp đế cho thước đuôi mèo rồi nên theo quy định xưởng không thể hủy ngang ạ. Bạn có thể đặt thêm mẫu màu vàng với voucher giảm 10% shop gửi tặng nhé!', 
   NULL, NOW() - INTERVAL '30 minutes');


-- =========================================================================================
-- 7. 5 ĐÁNH GIÁ CHẤT LƯỢNG SẢN PHẨM IN 3D (REVIEWS & REVIEW_IMAGES)
-- (Tuân thủ mô hình Verified Purchase: gắn trực tiếp 1-1 với OrderItem của đơn COMPLETED)
-- =========================================================================================
INSERT INTO reviews (id, reviewer_id, product_id, order_id, order_item_id, rating, comment, created_at, updated_at)
VALUES 
  -- Đánh giá 1: Thước Kỹ Thuật PLA Pro 20cm (Gắn trực tiếp với OrderItem 101 của Đơn COMPLETED 1)
  (1, '3f65fede-a289-4857-945a-df3f200d5e13', 'c0000001-0000-0000-0000-000000000006', 
   'e0000001-0001-0001-0001-000000000001', 101, 5, 
   'Thước in 3D bằng nhựa PLA Pro cầm rất đầm tay, vạch chia 0.5mm dập chìm sắc nét không lo bị bay màu mực như thước nhựa chợ. Chữ khắc laser FPTU - KHANH LE K17 chuẩn nét, góc vát cạnh chống lem mực rất ưng ý. Sẽ tiếp tục ủng hộ xưởng in!', 
   NOW() - INTERVAL '11 days', NULL),

  -- Đánh giá 2: Thước Móc Khóa Balo Dạ Quang 10cm (Gắn trực tiếp với OrderItem 102 của Đơn COMPLETED 1)
  (2, '3f65fede-a289-4857-945a-df3f200d5e13', 'c0000001-0000-0000-0000-000000000001', 
   'e0000001-0001-0001-0001-000000000001', 102, 5, 
   'Móc khóa thước 10cm nhỏ gọn, treo balo đi học rất tiện lợi, ban đêm phát sáng dạ quang xanh ngọc nhìn cực ngầu! Vạch chia chuẩn xác để đo nhanh khổ giấy và linh kiện đồ án.', 
   NOW() - INTERVAL '11 days', NULL),

  -- Đánh giá 3: Thước Kiếm Katana Pixel 22cm (Gắn trực tiếp với OrderItem 107 của Đơn COMPLETED 4)
  (3, '3f65fede-a289-4857-945a-df3f200d5e13', 'c0000001-0000-0000-0000-000000000009', 
   'e0000001-0001-0001-0001-000000000004', 107, 5, 
   'Cầm cây kiếm Katana pixel infill 100% siêu chắc chắn và ngầu! Thiết kế pixel art sắc sảo, vạch chia 22cm kẻ rất chuẩn, đem lên giảng đường ai cũng hỏi mua ở đâu.', 
   NOW() - INTERVAL '24 days', NULL),

  -- Đánh giá 4: Thước Bookmark Kẹp Sách 15cm (Gắn trực tiếp với OrderItem 108 của Đơn COMPLETED 4)
  (4, '3f65fede-a289-4857-945a-df3f200d5e13', 'c0000001-0000-0000-0000-000000000002', 
   'e0000001-0001-0001-0001-000000000004', 108, 4, 
   'Độ dày 0.8mm dẻo dai kẹp sách rất thích, không làm cấn hay gãy trang sách giáo trình. Màu in tương phản rõ nét. Đóng gói hộp carton cẩn thận 10/10.', 
   NOW() - INTERVAL '23 days', NULL),

  -- Đánh giá 5: Thước Kẹp Mini Vernier Caliper (Gắn trực tiếp với OrderItem 109 của Đơn COMPLETED 4)
  (5, '3f65fede-a289-4857-945a-df3f200d5e13', 'c0000001-0000-0000-0000-000000000010', 
   'e0000001-0001-0001-0001-000000000004', 109, 5, 
   'Cơ cấu trượt của thước kẹp mini cực kỳ êm ái, đo cữ sâu và đường kính trong ngoài chi tiết in 3D rất tiện lợi cho dân kỹ thuật. Rất đáng tiền!', 
   NOW() - INTERVAL '22 days', NULL)
ON CONFLICT (id) DO UPDATE 
SET rating = EXCLUDED.rating, comment = EXCLUDED.comment, product_id = EXCLUDED.product_id, updated_at = NOW();

-- Cập nhật sequence an toàn cho reviews tránh xung đột ID tự sinh
DO $$
DECLARE
  seq_name TEXT;
  max_id BIGINT;
BEGIN
  seq_name := pg_get_serial_sequence('reviews', 'id');
  IF seq_name IS NOT NULL THEN
    SELECT COALESCE(MAX(id), 1) INTO max_id FROM reviews;
    PERFORM setval(seq_name, max_id);
  END IF;
END $$;

-- HÌNH ẢNH THÀNH PHẨM IN 3D THỰC TẾ CHO ĐÁNH GIÁ (review_images)
INSERT INTO review_images (review_id, image_url)
VALUES 
  (1, 'https://images.unsplash.com/photo-1596495577886-d920f1fb7238?auto=format&fit=crop&w=800&q=80'),
  (1, 'https://images.unsplash.com/photo-1589739900266-43b2843f4c12?auto=format&fit=crop&w=800&q=80'),
  (2, 'https://images.unsplash.com/photo-1546776310-eef45dd6d63c?auto=format&fit=crop&w=800&q=80'),
  (3, 'https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?auto=format&fit=crop&w=800&q=80'),
  (4, 'https://images.unsplash.com/photo-1586953208448-b95a79798f07?auto=format&fit=crop&w=800&q=80'),
  (5, 'https://images.unsplash.com/photo-1581092160607-ee22621dd758?auto=format&fit=crop&w=800&q=80');


-- =========================================================================================
-- 8. TỰ ĐỘNG CẬP NHẬT ĐIỂM SAO TRUNG BÌNH & SỐ LƯỢT ĐÁNH GIÁ VÀO BẢNG PRODUCTS
-- =========================================================================================
UPDATE products p
SET 
  average_rating = COALESCE((SELECT ROUND(AVG(rating)::numeric, 1)::double precision FROM reviews r WHERE r.product_id = p.id), 0.0),
  review_count = (SELECT COUNT(*)::integer FROM reviews r WHERE r.product_id = p.id);

COMMIT;
