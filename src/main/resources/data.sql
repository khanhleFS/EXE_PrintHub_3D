-- Insert test data for User entity with all 3 roles
-- Password: Password123 (BCrypt hashed)

-- Admin User
INSERT INTO users (id, role, username, email, password, full_name, phone, address, is_active, created_at, updated_at)
VALUES (NEWID(), 'ADMIN', 'admin_user', 'admin@printhub.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcg7b3XeKeUxWdeS86E3l0CvWmK', 'Admin User', '0901234567', '123 Admin Street', 1, GETDATE(), GETDATE());

-- Maker User
INSERT INTO users (id, role, username, email, password, full_name, phone, address, is_active, created_at, updated_at)
VALUES (NEWID(), 'MAKER', 'maker_user', 'maker@printhub.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcg7b3XeKeUxWdeS86E3l0CvWmK', 'Maker User', '0912345678', '456 Maker Avenue', 1, GETDATE(), GETDATE());

-- Regular User
INSERT INTO users (id, role, username, email, password, full_name, phone, address, is_active, created_at, updated_at)
VALUES (NEWID(), 'USER', 'normal_user', 'user@printhub.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcg7b3XeKeUxWdeS86E3l0CvWmK', 'Normal User', '0923456789', '789 User Road', 1, GETDATE(), GETDATE());
