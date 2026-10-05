package com.engine.db;

import com.engine.core.EngineEventListener;
import com.engine.model.Order;
import com.engine.model.Stock;
import com.engine.model.TradeRecord;
import com.engine.model.UserProfile;
import com.engine.model.Position;

import java.sql.*;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DatabaseManager implements EngineEventListener {
    private String jdbcUrl = "jdbc:mysql://localhost:3306/trading_engine?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private String user = "root";
    private String password = "";
    private boolean connected = false;

    // Asynchronous worker pool for non-blocking database writes
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "DB-Writer-Thread");
        t.setDaemon(true);
        return t;
    });

    public DatabaseManager() {
        initConnection();
    }

    public DatabaseManager(String jdbcUrl, String user, String password) {
        this.jdbcUrl = jdbcUrl;
        this.user = user;
        this.password = password;
        initConnection();
    }

    private void initConnection() {
        try {
            // Check for MySQL driver
            Class.forName("com.mysql.cj.jdbc.Driver");
            try (Connection conn = DriverManager.getConnection(jdbcUrl, user, password)) {
                connected = true;
                System.out.println("[DatabaseManager] Successfully connected to MySQL at: " + jdbcUrl);
                initTables(conn);
            }
        } catch (ClassNotFoundException e) {
            System.out.println("[DatabaseManager] MySQL JDBC Driver not found in classpath. Operating with in-memory persistence.");
            connected = false;
        } catch (SQLException e) {
            System.out.println("[DatabaseManager] Could not connect to MySQL (" + e.getMessage() + "). Operating with in-memory persistence.");
            System.out.println("[DatabaseManager] Tip: Ensure MySQL is running on localhost:3306 and database 'trading_engine' exists.");
            connected = false;
        }
    }

    private void initTables(Connection conn) {
        String createUsers = """
            CREATE TABLE IF NOT EXISTS users (
                id VARCHAR(36) PRIMARY KEY,
                username VARCHAR(50) UNIQUE NOT NULL,
                email VARCHAR(100) UNIQUE NOT NULL,
                full_name VARCHAR(100) NOT NULL,
                password_hash VARCHAR(255) NOT NULL,
                status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
            ) ENGINE=InnoDB;
        """;
        String createAccounts = """
            CREATE TABLE IF NOT EXISTS accounts (
                id VARCHAR(36) PRIMARY KEY,
                user_id VARCHAR(36) NOT NULL,
                currency VARCHAR(10) NOT NULL DEFAULT 'USD',
                available_balance DECIMAL(18, 4) NOT NULL DEFAULT 0.0000,
                locked_balance DECIMAL(18, 4) NOT NULL DEFAULT 0.0000,
                realized_pnl DECIMAL(18, 4) NOT NULL DEFAULT 0.0000,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX idx_accounts_user_id (user_id)
            ) ENGINE=InnoDB;
        """;
        String createOrders = """
            CREATE TABLE IF NOT EXISTS orders (
                id VARCHAR(36) PRIMARY KEY,
                user_id VARCHAR(36) NOT NULL,
                ticker VARCHAR(10) NOT NULL,
                side VARCHAR(10) NOT NULL,
                order_type VARCHAR(20) NOT NULL,
                price DECIMAL(18, 4) NOT NULL DEFAULT 0.0000,
                initial_quantity INT NOT NULL,
                remaining_quantity INT NOT NULL,
                filled_quantity INT NOT NULL DEFAULT 0,
                status VARCHAR(20) NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
            ) ENGINE=InnoDB;
        """;
        String createTrades = """
            CREATE TABLE IF NOT EXISTS trades (
                trade_id VARCHAR(36) PRIMARY KEY,
                ticker VARCHAR(10) NOT NULL,
                buy_order_id VARCHAR(36) NOT NULL,
                sell_order_id VARCHAR(36) NOT NULL,
                buyer_id VARCHAR(36) NOT NULL,
                seller_id VARCHAR(36) NOT NULL,
                price DECIMAL(18, 4) NOT NULL,
                quantity INT NOT NULL,
                total_amount DECIMAL(18, 4) NOT NULL,
                buyer_fee DECIMAL(18, 4) NOT NULL,
                seller_fee DECIMAL(18, 4) NOT NULL,
                executed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            ) ENGINE=InnoDB;
        """;
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(createUsers);
            stmt.execute(createAccounts);
            stmt.execute(createOrders);
            stmt.execute(createTrades);
        } catch (SQLException ex) {
            System.err.println("[DatabaseManager] Table init error: " + ex.getMessage());
        }
    }

    public boolean isConnected() {
        return connected;
    }

    public void persistOrder(Order order) {
        if (!connected) return;
        dbExecutor.submit(() -> {
            String sql = """
                INSERT INTO orders (id, user_id, ticker, side, order_type, price, initial_quantity, remaining_quantity, filled_quantity, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    remaining_quantity = VALUES(remaining_quantity),
                    filled_quantity = VALUES(filled_quantity),
                    status = VALUES(status);
            """;
            try (Connection conn = DriverManager.getConnection(jdbcUrl, user, password);
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, order.id);
                ps.setString(2, order.traderId);
                ps.setString(3, order.ticker);
                ps.setString(4, order.side.name());
                ps.setString(5, order.type.name());
                ps.setDouble(6, order.price);
                ps.setInt(7, order.initialQuantity);
                ps.setInt(8, order.quantity);
                ps.setInt(9, order.getFilledQuantity());
                ps.setString(10, order.status.name());
                ps.executeUpdate();
            } catch (SQLException e) {
                System.err.println("[DatabaseManager] Error persisting order " + order.id + ": " + e.getMessage());
            }
        });
    }

    public void persistTrade(TradeRecord trade) {
        if (!connected) return;
        dbExecutor.submit(() -> {
            String sql = """
                INSERT INTO trades (trade_id, ticker, buy_order_id, sell_order_id, buyer_id, seller_id, price, quantity, total_amount, buyer_fee, seller_fee)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
            try (Connection conn = DriverManager.getConnection(jdbcUrl, user, password);
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, trade.tradeId);
                ps.setString(2, trade.ticker);
                ps.setString(3, trade.buyOrderId);
                ps.setString(4, trade.sellOrderId);
                ps.setString(5, trade.buyTraderId);
                ps.setString(6, trade.sellTraderId);
                ps.setDouble(7, trade.price);
                ps.setInt(8, trade.quantity);
                ps.setDouble(9, trade.totalAmount);
                ps.setDouble(10, trade.fee);
                ps.setDouble(11, trade.fee);
                ps.executeUpdate();
            } catch (SQLException e) {
                System.err.println("[DatabaseManager] Error persisting trade " + trade.tradeId + ": " + e.getMessage());
            }
        });
    }

    public void syncUserProfile(UserProfile profile) {
        if (!connected) return;
        dbExecutor.submit(() -> {
            String updateAcc = "UPDATE accounts SET available_balance = ?, realized_pnl = ? WHERE user_id = ?";
            try (Connection conn = DriverManager.getConnection(jdbcUrl, user, password);
                 PreparedStatement ps = conn.prepareStatement(updateAcc)) {
                ps.setDouble(1, profile.balance);
                ps.setDouble(2, profile.realizedPnL);
                ps.setString(3, profile.traderId);
                ps.executeUpdate();

                // Sync positions
                String upsertPos = """
                    INSERT INTO positions (id, user_id, ticker, shares, avg_cost)
                    VALUES (?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE shares = VALUES(shares), avg_cost = VALUES(avg_cost)
                """;
                try (PreparedStatement posPs = conn.prepareStatement(upsertPos)) {
                    for (Map.Entry<String, Position> e : profile.getPortfolio().entrySet()) {
                        posPs.setString(1, "POS-" + profile.traderId + "-" + e.getKey());
                        posPs.setString(2, profile.traderId);
                        posPs.setString(3, e.getKey());
                        posPs.setInt(4, e.getValue().shares);
                        posPs.setDouble(5, e.getValue().avgCost);
                        posPs.addBatch();
                    }
                    posPs.executeBatch();
                }
            } catch (SQLException e) {
                System.err.println("[DatabaseManager] Error syncing user " + profile.traderId + ": " + e.getMessage());
            }
        });
    }

    public void saveNewUser(String id, String username, String email, String fullName, String passwordHash, double initialBalance) {
        if (!connected) return;
        dbExecutor.submit(() -> {
            String insertUser = """
                INSERT INTO users (id, username, email, full_name, password_hash, status)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE')
                ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), password_hash = VALUES(password_hash);
            """;
            String insertAccount = """
                INSERT INTO accounts (id, user_id, currency, available_balance, locked_balance, realized_pnl)
                VALUES (?, ?, 'USD', ?, 0.0000, 0.0000)
                ON DUPLICATE KEY UPDATE available_balance = VALUES(available_balance);
            """;
            try (Connection conn = DriverManager.getConnection(jdbcUrl, user, password);
                 PreparedStatement psUser = conn.prepareStatement(insertUser);
                 PreparedStatement psAcc = conn.prepareStatement(insertAccount)) {
                
                psUser.setString(1, id);
                psUser.setString(2, username);
                psUser.setString(3, email);
                psUser.setString(4, fullName);
                psUser.setString(5, passwordHash);
                psUser.executeUpdate();

                psAcc.setString(1, "ACC-" + id);
                psAcc.setString(2, id);
                psAcc.setDouble(3, initialBalance);
                psAcc.executeUpdate();

                System.out.println("[DatabaseManager] Persisted user & account for " + username + " (" + id + ")");
            } catch (SQLException e) {
                System.err.println("[DatabaseManager] Error persisting new user " + username + ": " + e.getMessage());
            }
        });
    }

    // EngineEventListener implementation
    @Override
    public void onTradeExecuted(TradeRecord trade) {
        persistTrade(trade);
    }

    @Override
    public void onOrderUpdated(Order order) {
        persistOrder(order);
    }

    @Override
    public void onOrderBookChanged(String ticker) {}

    @Override
    public void onCircuitBreakerTripped(Stock stock) {}
}
