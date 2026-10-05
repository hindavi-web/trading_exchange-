-- =============================================================================
-- MySQL Initial Seed Data for In-Memory Stock Trading Engine
-- =============================================================================

USE trading_engine;

-- 1. Insert Predefined Users with Passwords (default: 'password123')
-- SHA-256 of 'password123' = ef92b778bafe771e89245b89ecbc08a44a4e166c06659911881f383d4473e94f
INSERT INTO users (id, username, email, full_name, password_hash, status) VALUES
('T001', 'alice', 'alice@trading.local', 'Alice Smith', 'ef92b778bafe771e89245b89ecbc08a44a4e166c06659911881f383d4473e94f', 'ACTIVE'),
('T002', 'bob', 'bob@trading.local', 'Bob Jones', 'ef92b778bafe771e89245b89ecbc08a44a4e166c06659911881f383d4473e94f', 'ACTIVE'),
('T003', 'carol', 'carol@trading.local', 'Carol White', 'ef92b778bafe771e89245b89ecbc08a44a4e166c06659911881f383d4473e94f', 'ACTIVE')
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), password_hash = VALUES(password_hash);

-- 2. Insert Accounts with $100,000 Initial Balance
INSERT INTO accounts (id, user_id, currency, available_balance, locked_balance, realized_pnl) VALUES
('ACC-T001', 'T001', 'USD', 100000.0000, 0.0000, 0.0000),
('ACC-T002', 'T002', 'USD', 100000.0000, 0.0000, 0.0000),
('ACC-T003', 'T003', 'USD', 100000.0000, 0.0000, 0.0000)
ON DUPLICATE KEY UPDATE available_balance = VALUES(available_balance);

-- 3. Register Predefined Stocks
INSERT INTO stocks (ticker, company_name, tick_size, lot_size, trading_paused, circuit_breaker_threshold, circuit_breaker_tripped, last_traded_price, open_price, high_price, low_price, total_volume, is_active) VALUES
('AAPL', 'Apple Inc.', 0.0100, 1, FALSE, 5.00, FALSE, 100.0000, 100.0000, 100.0000, 100.0000, 0, TRUE),
('MSFT', 'Microsoft Corp.', 0.0100, 1, FALSE, 5.00, FALSE, 400.0000, 400.0000, 400.0000, 400.0000, 0, TRUE),
('TSLA', 'Tesla Inc.', 0.0500, 1, FALSE, 5.00, FALSE, 250.0000, 250.0000, 250.0000, 250.0000, 0, TRUE)
ON DUPLICATE KEY UPDATE company_name = VALUES(company_name);

-- 4. Initial Portfolio Positions
-- Bob: 500 AAPL @ $95.00
-- Carol: 500 AAPL @ $95.00
INSERT INTO positions (id, user_id, ticker, shares, locked_shares, avg_cost) VALUES
('POS-T002-AAPL', 'T002', 'AAPL', 500, 0, 95.0000),
('POS-T003-AAPL', 'T003', 'AAPL', 500, 0, 95.0000)
ON DUPLICATE KEY UPDATE shares = VALUES(shares), avg_cost = VALUES(avg_cost);
