-- =============================================================================
-- MySQL Schema for In-Memory Stock Trading Engine Persistence
-- =============================================================================

CREATE DATABASE IF NOT EXISTS trading_engine CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE trading_engine;

-- 1. Users Table (Trader Profiles & Credentials)
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(36) PRIMARY KEY,                         -- e.g., 'T001', 'T002'
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,                -- SHA-256 hashed password
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',       -- ACTIVE, SUSPENDED, FROZEN
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- 2. Accounts Table (Cash Balances & Realized P&L)
CREATE TABLE IF NOT EXISTS accounts (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'USD',
    available_balance DECIMAL(18, 4) NOT NULL DEFAULT 0.0000,   -- Available spendable cash
    locked_balance DECIMAL(18, 4) NOT NULL DEFAULT 0.0000,      -- Cash locked for resting BUY orders
    realized_pnl DECIMAL(18, 4) NOT NULL DEFAULT 0.0000,        -- Realized profit & loss
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_accounts_user_id (user_id)
) ENGINE=InnoDB;

-- 3. Stocks Table (Instruments & Circuit Breakers)
CREATE TABLE IF NOT EXISTS stocks (
    ticker VARCHAR(10) PRIMARY KEY,                     -- e.g. 'AAPL'
    company_name VARCHAR(100) NOT NULL,
    tick_size DECIMAL(10, 4) NOT NULL DEFAULT 0.0100,
    lot_size INT NOT NULL DEFAULT 1,
    trading_paused BOOLEAN NOT NULL DEFAULT FALSE,
    circuit_breaker_threshold DECIMAL(5, 2) NOT NULL DEFAULT 5.00,  -- 5.00%
    circuit_breaker_tripped BOOLEAN NOT NULL DEFAULT FALSE,
    last_traded_price DECIMAL(18, 4) NOT NULL DEFAULT 0.0000,
    open_price DECIMAL(18, 4) NOT NULL DEFAULT 0.0000,
    high_price DECIMAL(18, 4) NOT NULL DEFAULT 0.0000,
    low_price DECIMAL(18, 4) NOT NULL DEFAULT 0.0000,
    total_volume BIGINT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- 4. Positions Table (Stock Holdings per Trader)
CREATE TABLE IF NOT EXISTS positions (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    ticker VARCHAR(10) NOT NULL,
    shares INT NOT NULL DEFAULT 0,                      -- Available shares
    locked_shares INT NOT NULL DEFAULT 0,               -- Shares locked in resting SELL orders
    avg_cost DECIMAL(18, 4) NOT NULL DEFAULT 0.0000,    -- Weighted average cost basis
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_user_ticker (user_id, ticker),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (ticker) REFERENCES stocks(ticker),
    INDEX idx_positions_user (user_id)
) ENGINE=InnoDB;

-- 5. Orders Table (Submitted, Resting, Filled, and Cancelled Orders)
CREATE TABLE IF NOT EXISTS orders (
    id VARCHAR(36) PRIMARY KEY,                         -- Order ID (e.g., 'O001', 'M1234')
    user_id VARCHAR(36) NOT NULL,
    ticker VARCHAR(10) NOT NULL,
    side VARCHAR(10) NOT NULL,                          -- 'BUY', 'SELL'
    order_type VARCHAR(20) NOT NULL,                    -- 'MARKET', 'LIMIT', 'IOC', 'FOK', 'STOP_LOSS', etc.
    price DECIMAL(18, 4) NOT NULL DEFAULT 0.0000,       -- Limit / Trigger price
    initial_quantity INT NOT NULL,                      -- Originally submitted quantity
    remaining_quantity INT NOT NULL,                    -- Unfilled quantity resting
    filled_quantity INT NOT NULL DEFAULT 0,             -- Quantity executed
    status VARCHAR(20) NOT NULL,                        -- 'NEW', 'PARTIALLY_FILLED', 'FILLED', 'CANCELLED', 'REJECTED'
    rejection_reason VARCHAR(255) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (ticker) REFERENCES stocks(ticker),
    INDEX idx_orders_user_status (user_id, status),
    INDEX idx_orders_ticker_status (ticker, status),
    INDEX idx_orders_created (created_at DESC)
) ENGINE=InnoDB;

-- 6. Trades Table (Executed Matches / Trade Tape)
CREATE TABLE IF NOT EXISTS trades (
    trade_id VARCHAR(36) PRIMARY KEY,
    ticker VARCHAR(10) NOT NULL,
    buy_order_id VARCHAR(36) NOT NULL,
    sell_order_id VARCHAR(36) NOT NULL,
    buyer_id VARCHAR(36) NOT NULL,
    seller_id VARCHAR(36) NOT NULL,
    price DECIMAL(18, 4) NOT NULL,
    quantity INT NOT NULL,
    total_amount DECIMAL(18, 4) NOT NULL,               -- price * quantity
    buyer_fee DECIMAL(18, 4) NOT NULL,                  -- fee collected from buyer
    seller_fee DECIMAL(18, 4) NOT NULL,                 -- fee collected from seller
    executed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (ticker) REFERENCES stocks(ticker),
    FOREIGN KEY (buy_order_id) REFERENCES orders(id),
    FOREIGN KEY (sell_order_id) REFERENCES orders(id),
    FOREIGN KEY (buyer_id) REFERENCES users(id),
    FOREIGN KEY (seller_id) REFERENCES users(id),
    INDEX idx_trades_ticker_time (ticker, executed_at DESC),
    INDEX idx_trades_buyer (buyer_id),
    INDEX idx_trades_seller (seller_id)
) ENGINE=InnoDB;

-- 7. Exchange Revenue Table (Trading Fee Ledger)
CREATE TABLE IF NOT EXISTS exchange_revenue (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trade_id VARCHAR(36) NOT NULL,
    fee_collected DECIMAL(18, 4) NOT NULL,
    fee_rate DECIMAL(6, 4) NOT NULL DEFAULT 0.0010,     -- 0.1% (0.001)
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (trade_id) REFERENCES trades(trade_id),
    INDEX idx_revenue_time (created_at DESC)
) ENGINE=InnoDB;
