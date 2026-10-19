-- =========================================================================================
-- PRINTHUB 3D - ROLLBACK 01_EXPAND (PostgreSQL / Supabase)
-- Mục đích: Hoàn tác các thay đổi từ 01_expand.sql nếu cần khôi phục
-- CẢNH BÁO: Thao tác này sẽ xóa bảng inventory_reservations và idempotency_records
-- =========================================================================================

-- 1. Xóa các bảng mới tạo
DROP TABLE IF EXISTS inventory_reservations CASCADE;
DROP TABLE IF EXISTS idempotency_records CASCADE;

-- 2. Xóa các cột bổ sung trên checkout_orders
ALTER TABLE IF EXISTS checkout_orders DROP COLUMN IF EXISTS payment_expires_at;
ALTER TABLE IF EXISTS checkout_orders DROP COLUMN IF EXISTS reservation_expires_at;
ALTER TABLE IF EXISTS checkout_orders DROP COLUMN IF EXISTS idempotency_key;

-- 3. Khôi phục ràng buộc seller_id (chỉ khi không có dữ liệu null)
-- ALTER TABLE orders ALTER COLUMN seller_id SET NOT NULL;
-- ALTER TABLE products ALTER COLUMN seller_id SET NOT NULL;
