-- =========================================================================================
-- PRINTHUB 3D - MIGRATION 01_EXPAND (PostgreSQL / Supabase)
-- Mục đích:
--   1. Chuyển orders.seller_id và products.seller_id sang NULLABLE (Bỏ maker/seller)
--   2. Bổ sung bảng inventory_reservations (Giữ kho với Lazy Expiry & Grace period 20m)
--   3. Bổ sung bảng idempotency_records (Chống lặp request bền vững trên DB)
--   4. Bổ sung các cột versioning & auditing trên checkout_orders, orders, custom_orders
-- Tiền điều kiện: Schema baseline hiện tại đã tồn tại
-- Phương án phục hồi: Xem scripts/db-test/rollback_01_expand.sql
-- =========================================================================================

-- 1. Bỏ ràng buộc NOT NULL của seller_id
ALTER TABLE IF EXISTS orders ALTER COLUMN seller_id DROP NOT NULL;
ALTER TABLE IF EXISTS products ALTER COLUMN seller_id DROP NOT NULL;

-- 2. Bổ sung bảng inventory_reservations phục vụ giữ kho an toàn (Lazy Expiration)
CREATE TABLE IF NOT EXISTS inventory_reservations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL,
    checkout_order_id UUID,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    status VARCHAR(30) NOT NULL DEFAULT 'RESERVED', -- RESERVED, COMMITTED, RELEASED, EXPIRED
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_inv_res_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT fk_inv_res_checkout FOREIGN KEY (checkout_order_id) REFERENCES checkout_orders(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_inv_res_product_status ON inventory_reservations(product_id, status, expires_at);
CREATE INDEX IF NOT EXISTS idx_inv_res_checkout ON inventory_reservations(checkout_order_id);

-- 3. Bổ sung bảng idempotency_records chống request lặp
CREATE TABLE IF NOT EXISTS idempotency_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    action_key VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(150) NOT NULL,
    request_hash VARCHAR(100),
    response_body TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'PROCESSING', -- PROCESSING, COMPLETED, FAILED
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_idempotency_user_action_key UNIQUE (user_id, action_key, idempotency_key),
    CONSTRAINT fk_idempotency_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_idempotency_lookup ON idempotency_records(user_id, action_key, idempotency_key);

-- 4. Bổ sung cột quản lý phiên bản và đối soát trên checkout_orders
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'checkout_orders' AND column_name = 'payment_expires_at') THEN
        ALTER TABLE checkout_orders ADD COLUMN payment_expires_at TIMESTAMPTZ;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'checkout_orders' AND column_name = 'reservation_expires_at') THEN
        ALTER TABLE checkout_orders ADD COLUMN reservation_expires_at TIMESTAMPTZ;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'checkout_orders' AND column_name = 'idempotency_key') THEN
        ALTER TABLE checkout_orders ADD COLUMN idempotency_key VARCHAR(150);
    END IF;
END $$;
