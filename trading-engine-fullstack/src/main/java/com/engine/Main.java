package com.engine;

import com.engine.api.TradingHttpServer;
import com.engine.core.*;
import com.engine.db.AuthManager;
import com.engine.db.DatabaseManager;
import com.engine.model.Enums.OrderSide;
import com.engine.model.Enums.OrderType;
import com.engine.model.Order;
import com.engine.model.Stock;
import com.engine.model.UserProfile;

public class Main {
    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println(" Starting Stock Order Matching Engine & Full-Stack");
        System.out.println("=================================================");

        // 1. Setup Exchange & Registered Stocks
        Exchange exchange = new Exchange();
        exchange.registerStock("AAPL", 0.01);
        exchange.registerStock("MSFT", 0.01);
        exchange.registerStock("TSLA", 0.05);

        // 2. Setup Predefined Users & Portfolios
        UserProfileManager um = new UserProfileManager();
        um.addUser(new UserProfile("T001", "Alice", 100000.0));
        um.addUser(new UserProfile("T002", "Bob", 100000.0));
        um.addUser(new UserProfile("T003", "Carol", 100000.0));

        um.getUser("T002").addShares("AAPL", 500, 95.00);
        um.getUser("T003").addShares("AAPL", 500, 95.00);

        // 3. Initialize Engine Components
        OrderBook orderBook = new OrderBook();
        StopOrderBook stopOrderBook = new StopOrderBook();
        TradeBook tradeBook = new TradeBook();
        OrderLog orderLog = new OrderLog();
        IncomingOrderQueue queue = new IncomingOrderQueue();

        MatchingEngine engine = new MatchingEngine(
                exchange,
                orderBook,
                stopOrderBook,
                tradeBook,
                orderLog,
                um
        );

        // 4. Initialize Database Layer
        DatabaseManager db = new DatabaseManager();
        engine.addListener(db);

        // 5. Start Single Matching Worker Thread
        MatchingWorker worker = new MatchingWorker(queue, engine, exchange);
        Thread matchingThread = new Thread(worker, "MatchingEngine-Worker");
        matchingThread.setDaemon(true);
        matchingThread.start();

        // 6. Preload Initial Resting Orders (from original test setup)
        Stock aapl = exchange.getStock("AAPL");
        Order[] initialOrders = {
                new Order("O001", OrderSide.SELL, 100.00, 50, OrderType.LIMIT, "T002", "AAPL"),
                new Order("O002", OrderSide.SELL, 101.00, 30, OrderType.LIMIT, "T003", "AAPL"),
                new Order("O003", OrderSide.SELL, 102.00, 20, OrderType.LIMIT, "T002", "AAPL"),
                new Order("O004", OrderSide.SELL, 99.00, 20, OrderType.STOP_LOSS, "T002", "AAPL"),
                new Order("O005", OrderSide.BUY, 100.00, 80, OrderType.LIMIT, "T001", "AAPL")
        };

        System.out.println("\n[ENGINE] Pre-loading initial market orders...");
        for (Order o : initialOrders) {
            if ("VALID".equals(OrderValidator.validate(o, um, exchange))) {
                engine.process(o, aapl);
            }
        }
        System.out.println("[ENGINE] Initial book established successfully.\n");

        // 7. Start REST & Mobile Web App Server on Port 8080
        int port = 8080;
        try {
            AuthManager auth = new AuthManager();
            TradingHttpServer server = new TradingHttpServer(port, engine, exchange, queue, um, db, auth);
            server.start();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\nShutting down Trading Engine...");
                worker.stop();
                server.stop();
            }));

        } catch (Exception e) {
            System.err.println("Fatal: Could not start Trading HTTP Server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
