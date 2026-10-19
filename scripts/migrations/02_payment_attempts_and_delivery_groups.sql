-- =========================================================================================
-- PRINTHUB 3D - MIGRATION 02_PAYMENT_ATTEMPTS_AND_DELIVERY_GROUPS (PostgreSQL / Supabase)
-- Mục đích:
--   1. Bổ sung bảng payment_attempts lưu lịch sử tạo mã PayOS (chống mất dấu khi retry / QR cũ)
--   2. Bổ sung cột scope cho bảng payments và chuyển unique constraint sang (checkout_order_id, scope)
--      nhằm hỗ trợ 2 đợt thanh toán cho chế độ Giao riêng (SEPARATE: MARKETPLACE và CUSTOM)
--   3. Bổ sung cột lưu phí ship và trạng thái theo nhóm trên checkout_orders
--   4. Bổ sung cột price_version và unit_price trên custom_orders bảo vệ dữ liệu giá lịch sử
-- Tiền điều kiện: 00_baseline_schema.sql và 01_expand.sql đã được áp dụng
-- =========================================================================================

-- 1. Bổ sung cột scope và cập nhật ràng buộc UNIQUE trên bảng payments
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

-- 2. Bổ sung bảng payment_attempts (lưu vết mọi lần sinh mã link PayOS)
CREATE TABLE IF NOT EXISTS payment_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id UUID NOT NULL,
    checkout_order_id UUID,
    order_id UUID,
    custom_order_id UUID,
    provider_code BIGINT NOT NULL,
    amount NUMERIC(18, 2) NOT NULL,
    scope VARCHAR(30) NOT NULL DEFAULT 'ALL', -- ALL, MARKETPLACE, CUSTOM
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING', -- PENDING, SUCCESS, CANCELLED, EXPIRED, RECONCILIATION_REQUIRED
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

-- 3. Bổ sung cột quản lý 2 nhóm giao hàng và phí ship cố định trên checkout_orders
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
        ALTER TABLE checkout_orders ADD COLUMN marketplace_status VARCHAR(30) DEFAULT 'PENDING';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'checkout_orders' AND column_name = 'custom_status') THEN
        ALTER TABLE checkout_orders ADD COLUMN custom_status VARCHAR(30) DEFAULT 'PENDING_QUOTE';
    END IF;
END $$;

-- 4. Bổ sung cột price_version và unit_price trên custom_orders bảo vệ dữ liệu lịch sử
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'custom_orders' AND column_name = 'price_version') THEN
        ALTER TABLE custom_orders ADD COLUMN price_version INT DEFAULT 1;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'custom_orders' AND column_name = 'unit_price') THEN
        ALTER TABLE custom_orders ADD COLUMN unit_price NUMERIC(18, 2);
    END IF;
END $$;
