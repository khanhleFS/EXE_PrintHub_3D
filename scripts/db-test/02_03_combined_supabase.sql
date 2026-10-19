-- =========================================================================================
-- PRINTHUB 3D - MIGRATION 02_03_COMBINED_SUPABASE (PostgreSQL / Supabase)
-- Tiền điều kiện trên Supabase: ĐÃ CHẠY 01_expand.sql
-- File này gộp toàn bộ thay đổi của:
--   - Migration 02: Bảng payment_attempts, mở rộng cột scope trên payments, 
--                   cột phí ship/trạng thái theo nhóm trên checkout_orders, 
--                   cột price_version trên custom_orders.
--   - Migration 03: Mở rộng độ dài cột status lên VARCHAR(50) cho payments, orders,
--                   checkout_orders, inventory_reservations, payment_attempts.
--                   Tạo Unique partial index idempotency_key trên checkout_orders.
-- =========================================================================================

-- =========================================================================================
-- PHẦN 1: MIGRATION 02 - PAYMENT ATTEMPTS & DELIVERY GROUPS
-- =========================================================================================

-- 1.1 Bổ sung cột scope và cập nhật ràng buộc UNIQUE trên bảng payments
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'payments' AND column_name = 'scope') THEN
        ALTER TABLE payments ADD COLUMN scope VARCHAR(30) NOT NULL DEFAULT 'ALL';
    END IF;

    -- Xóa ràng buộc cũ chỉ cho phép 1 payment duy nhất trên 1 checkout_order
    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'payments_checkout_order_id_key') THEN
        ALTER TABLE payments DROP CONSTRAINT payments_checkout_order_id_key;
    END IF;

    -- Thêm ràng buộc mới: Mỗi checkout_order chỉ có tối đa 1 payment cho mỗi scope (ALL, MARKETPLACE, CUSTOM)
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_payments_checkout_scope') THEN
        ALTER TABLE payments ADD CONSTRAINT uq_payments_checkout_scope UNIQUE (checkout_order_id, scope);
    END IF;
END $$;

-- 1.2 Bổ sung bảng payment_attempts (lưu vết mọi lần sinh mã link PayOS)
CREATE TABLE IF NOT EXISTS payment_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id UUID NOT NULL,
    checkout_order_id UUID,
    order_id UUID,
    custom_order_id UUID,
    provider_code BIGINT NOT NULL,
    amount NUMERIC(18, 2) NOT NULL,
    scope VARCHAR(30) NOT NULL DEFAULT 'ALL', -- ALL, MARKETPLACE, CUSTOM
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING', -- PENDING, SUCCESS, CANCELLED, EXPIRED, RECONCILIATION_REQUIRED, DUPLICATE_PAID
    checkout_url VARCHAR(1000),
    qr_code TEXT,
    price_version BIGINT DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    paid_at TIMESTAMPTZ,
    CONSTRAINT fk_payment_attempts_payment FOREIGN KEY (payment_id) REFERENCES payments(id) ON DELETE CASCADE,
    CONSTRAINT fk_payment_attempts_checkout FOREIGN KEY (checkout_order_id) REFERENCES checkout_orders(id) ON DELETE CASCADE,
    CONSTRAINT uq_payment_attempts_provider_code UNIQUE (provider_code)
);

CREATE INDEX IF NOT EXISTS idx_payment_attempts_provider_code ON payment_attempts(provider_code);
CREATE INDEX IF NOT EXISTS idx_payment_attempts_checkout ON payment_attempts(checkout_order_id, scope);

-- 1.3 Bổ sung cột quản lý 2 nhóm giao hàng và phí ship trên checkout_orders
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'checkout_orders' AND column_name = 'marketplace_shipping_fee') THEN
        ALTER TABLE checkout_orders ADD COLUMN marketplace_shipping_fee NUMERIC(18, 2) DEFAULT 0;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'checkout_orders' AND column_name = 'custom_shipping_fee') THEN
        ALTER TABLE checkout_orders ADD COLUMN custom_shipping_fee NUMERIC(18, 2) DEFAULT 0;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'checkout_orders' AND column_name = 'shipping_fee_assigned_scope') THEN
        ALTER TABLE checkout_orders ADD COLUMN shipping_fee_assigned_scope VARCHAR(30) DEFAULT 'MARKETPLACE';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'checkout_orders' AND column_name = 'marketplace_status') THEN
        ALTER TABLE checkout_orders ADD COLUMN marketplace_status VARCHAR(50) DEFAULT 'PENDING';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'checkout_orders' AND column_name = 'custom_status') THEN
        ALTER TABLE checkout_orders ADD COLUMN custom_status VARCHAR(50) DEFAULT 'PENDING_QUOTE';
    END IF;
END $$;

-- 1.4 Bổ sung cột price_version và unit_price trên custom_orders
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'custom_orders' AND column_name = 'price_version') THEN
        ALTER TABLE custom_orders ADD COLUMN price_version INT DEFAULT 1;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'custom_orders' AND column_name = 'unit_price') THEN
        ALTER TABLE custom_orders ADD COLUMN unit_price NUMERIC(18, 2);
    END IF;
END $$;

-- =========================================================================================
-- PHẦN 2: MIGRATION 03 - MỞ RỘNG STATUS LÊN VARCHAR(50) VÀ IDEMPOTENCY PARTIAL UNIQUE INDEX
-- =========================================================================================

-- 2.1 Mở rộng status bảng payments
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'payments' AND column_name = 'status') THEN
        ALTER TABLE payments ALTER COLUMN status TYPE VARCHAR(50);
    END IF;
END $$;

-- 2.2 Mở rộng status trên checkout_orders, orders, inventory_reservations, payment_attempts
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

-- 2.3 Bổ sung ràng buộc Unique partial index cho idempotency_key trên checkout_orders
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'uq_checkout_orders_user_idempotency') THEN
        CREATE UNIQUE INDEX uq_checkout_orders_user_idempotency 
        ON checkout_orders(user_id, idempotency_key) 
        WHERE idempotency_key IS NOT NULL;
    END IF;
END $$;
