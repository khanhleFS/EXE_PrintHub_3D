-- Migration script for User lifecycle & OTP cleanup
-- 1. Backfill existing active users
UPDATE users 
SET status = 'ACTIVE', email_verified = true 
WHERE is_active = 1 OR is_active = true;

-- 2. Backfill existing inactive users to PENDING_VERIFICATION (if any)
UPDATE users 
SET status = 'PENDING_VERIFICATION', email_verified = false 
WHERE (is_active = 0 OR is_active = false) AND (status IS NULL);
