-- =========================================================================================
-- PRINTHUB 3D - MIGRATION 03_FIX_STATUS_LENGTH_AND_CONSTRAINTS (PostgreSQL / Supabase)
-- Mục đích:
--   1. Mở rộng độ dài cột status lên VARCHAR(50) cho payments, checkout_orders,
--      inventory_reservations, payment_attempts để chứa các trạng thái dài như RECONCILIATION_REQUIRED (23 ký tự)
--   2. Bổ sung UNIQUE partial index cho (user_id, idempotency_key) trên checkout_orders
--      để ngăn chặn triệt để tình trạng tạo 2 đơn trùng lặp khi có race condition / retry song song
-- Tiền điều kiện: 00_baseline_schema.sql, 01_expand.sql và 02_payment_attempts_and_delivery_groups.sql đã chạy
-- =========================================================================================

-- 1. Mở rộng độ dài cột status trên bảng payments (R04)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'payments' AND column_name = 'status') THEN
        ALTER TABLE payments ALTER COLUMN status TYPE VARCHAR(50);
    END IF;
END $$;

-- 2. Mở rộng độ dài cột status trên các bảng liên quan (checkout_orders, inventory_reservations, payment_attempts)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'checkout_orders' AND column_name = 'status') THEN
        ALTER TABLE checkout_orders ALTER COLUMN status TYPE VARCHAR(50);
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'checkout_orders' AND column_name = 'marketplace_status') THEN
        ALTER TABLE checkout_orders ALTER COLUMN marketplace_status TYPE VARCHAR(50);
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'checkout_orders' AND column_name = 'custom_status') THEN
        ALTER TABLE checkout_orders ALTER COLUMN custom_status TYPE VARCHAR(50);
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'orders' AND column_name = 'status') THEN
        ALTER TABLE orders ALTER COLUMN status TYPE VARCHAR(50);
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'inventory_reservations' AND column_name = 'status') THEN
        ALTER TABLE inventory_reservations ALTER COLUMN status TYPE VARCHAR(50);
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'payment_attempts' AND column_name = 'status') THEN
        ALTER TABLE payment_attempts ALTER COLUMN status TYPE VARCHAR(50);
    END IF;
END $$;

-- 3. Bổ sung ràng buộc Unique partial index cho idempotency_key trên checkout_orders (R09)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'uq_checkout_orders_user_idempotency') THEN
        CREATE UNIQUE INDEX uq_checkout_orders_user_idempotency 
        ON checkout_orders(user_id, idempotency_key) 
        WHERE idempotency_key IS NOT NULL;
    END IF;
END $$;
