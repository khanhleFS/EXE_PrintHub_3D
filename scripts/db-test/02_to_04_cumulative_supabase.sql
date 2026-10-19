-- =========================================================================================
-- PRINTHUB 3D - CUMULATIVE MIGRATION 02_TO_04_CUMULATIVE_SUPABASE (PostgreSQL / Supabase)
-- Tiền điều kiện trên Supabase: ĐÃ CHẠY 01_expand.sql
-- File này gộp toàn bộ thay đổi cấu trúc dữ liệu và xử lý dữ liệu legacy cho Round 2:
--   - Migration 02: Bảng payment_attempts, cột scope trên payments, 
--                   phí ship/trạng thái theo nhóm trên checkout_orders, 
--                   price_version trên custom_orders.
--   - Migration 03: Mở rộng status lên VARCHAR(50) cho payments, orders, checkout_orders,
--                   inventory_reservations, payment_attempts.
--                   Tạo Unique partial index idempotency_key trên checkout_orders.
--   - Migration 04: Bảng idempotency_records với ràng buộc (user_id, action_key, idempotency_key).
--                   Cột custom_pricing_version và reward_points_awarded trên checkout_orders.
--                   Cột quote_version trên custom_orders.
--                   Các snapshot columns trên user_subscriptions (duration_days, applied_price,
--                   applied_required_points, applied_free_shipping, applied_shipping_discount_amount,
--                   activation_source) kèm xử lý legacy backfill an toàn.
-- LƯU Ý QUAN TRỌNG:
--   File này được chuẩn bị để admin đối soát và chạy thủ công trên Supabase console.
--   Agent KHÔNG tự động kết nối và chạy SQL trực tiếp lên database production Supabase.
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

    -- Xóa ràng buộc cũ chỉ cho phép 1 payment duy nhất trên 1 checkout_order (nếu có)
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
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING', -- PENDING, INITIALIZING, READY, SUCCESS, CANCELLED, EXPIRED, PENDING_VERIFY, RECONCILIATION_REQUIRED, DUPLICATE_PAID
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

-- =========================================================================================
-- PHẦN 3: MIGRATION 04 - IDEMPOTENCY RECORDS, VERSION TRACKING & SUBSCRIPTION SNAPSHOTS
-- =========================================================================================

-- 3.1 Bảng idempotency_records
CREATE TABLE IF NOT EXISTS idempotency_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    action_key VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(150) NOT NULL,
    request_hash VARCHAR(100),
    response_body TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'PROCESSING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_idempotency_records_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_idempotency_user_action_key UNIQUE (user_id, action_key, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_idempotency_lookup ON idempotency_records(user_id, action_key, idempotency_key);

-- 3.2 Bổ sung custom_pricing_version và reward_points_awarded trên checkout_orders
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'checkout_orders' AND column_name = 'custom_pricing_version') THEN
        ALTER TABLE checkout_orders ADD COLUMN custom_pricing_version BIGINT NOT NULL DEFAULT 1;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'checkout_orders' AND column_name = 'reward_points_awarded') THEN
        ALTER TABLE checkout_orders ADD COLUMN reward_points_awarded INT NOT NULL DEFAULT 0;
    END IF;
END $$;

-- 3.3 Bổ sung quote_version trên custom_orders
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'custom_orders' AND column_name = 'quote_version') THEN
        ALTER TABLE custom_orders ADD COLUMN quote_version BIGINT DEFAULT 1;
    END IF;
END $$;

-- 3.4 Bổ sung snapshot columns trên user_subscriptions
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'user_subscriptions' AND column_name = 'duration_days') THEN
        ALTER TABLE user_subscriptions ADD COLUMN duration_days INT DEFAULT 30;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'user_subscriptions' AND column_name = 'applied_price') THEN
        ALTER TABLE user_subscriptions ADD COLUMN applied_price NUMERIC(18, 2);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'user_subscriptions' AND column_name = 'applied_required_points') THEN
        ALTER TABLE user_subscriptions ADD COLUMN applied_required_points INT;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'user_subscriptions' AND column_name = 'applied_free_shipping') THEN
        ALTER TABLE user_subscriptions ADD COLUMN applied_free_shipping BOOLEAN DEFAULT FALSE;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'user_subscriptions' AND column_name = 'applied_shipping_discount_amount') THEN
        ALTER TABLE user_subscriptions ADD COLUMN applied_shipping_discount_amount NUMERIC(18, 2) DEFAULT 0;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'user_subscriptions' AND column_name = 'activation_source') THEN
        ALTER TABLE user_subscriptions ADD COLUMN activation_source VARCHAR(50);
    END IF;
END $$;

-- 3.5 Bổ sung các cột cấu hình ưu đãi trên subscription_plans
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'subscription_plans' AND column_name = 'duration_days') THEN
        ALTER TABLE subscription_plans ADD COLUMN duration_days INT DEFAULT 30;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'subscription_plans' AND column_name = 'free_shipping') THEN
        ALTER TABLE subscription_plans ADD COLUMN free_shipping BOOLEAN DEFAULT FALSE;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'subscription_plans' AND column_name = 'shipping_discount_amount') THEN
        ALTER TABLE subscription_plans ADD COLUMN shipping_discount_amount NUMERIC(18, 2) DEFAULT 0;
    END IF;
END $$;

-- =========================================================================================
-- PHẦN 4: BACKFILL DỮ LIỆU LEGACY AN TOÀN
-- =========================================================================================

-- Backfill checkout_orders legacy
UPDATE checkout_orders
SET custom_pricing_version = COALESCE(summary_version, 1)
WHERE custom_pricing_version IS NULL;

-- Backfill an toàn reward_points_awarded từ các đơn con marketplace và custom_orders đã từng tích điểm thưởng trong quá khứ (P1.6 / Migration backfill)
UPDATE checkout_orders co
SET reward_points_awarded = COALESCE((
    SELECT COALESCE(SUM(o.reward_points_earned), 0)
    FROM orders o
    WHERE o.checkout_order_id = co.id AND o.status = 'COMPLETED'
), 0) + COALESCE((
    SELECT COALESCE(SUM(pt.amount), 0)
    FROM point_transactions pt
    JOIN custom_orders co_custom ON co_custom.checkout_order_id = co.id
    WHERE pt.point_wallet_id = co.user_id
      AND pt.type = 'EARN'
      AND pt.description ILIKE '%' || co_custom.id::text || '%'
), 0)
WHERE co.reward_points_awarded IS NULL OR co.reward_points_awarded = 0;

-- Backfill custom_orders legacy
UPDATE custom_orders
SET quote_version = 1
WHERE quote_version IS NULL;

-- Backfill subscription_plans legacy
UPDATE subscription_plans
SET duration_days = 30
WHERE duration_days IS NULL;

UPDATE subscription_plans
SET free_shipping = COALESCE(free_shipping, false),
    shipping_discount_amount = COALESCE(shipping_discount_amount, 0)
WHERE free_shipping IS NULL OR shipping_discount_amount IS NULL;

-- Backfill user_subscriptions legacy
UPDATE user_subscriptions us
SET duration_days = 30
WHERE duration_days IS NULL;

UPDATE user_subscriptions us
SET applied_price = sp.price,
    applied_required_points = sp.required_points,
    applied_free_shipping = COALESCE(sp.free_shipping, false),
    applied_shipping_discount_amount = COALESCE(sp.shipping_discount_amount, 0),
    activation_source = 'LEGACY_MIGRATION'
FROM subscription_plans sp
WHERE us.plan_id = sp.id AND (us.applied_price IS NULL OR us.activation_source IS NULL);

-- =========================================================================================
-- KẾT THÚC CUMULATIVE MIGRATION 02_TO_04
-- =========================================================================================
