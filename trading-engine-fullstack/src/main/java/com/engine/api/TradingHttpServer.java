package com.engine.api;

import com.engine.core.*;
import com.engine.db.AuthManager;
import com.engine.db.DatabaseManager;
import com.engine.model.*;
import com.engine.model.Enums.OrderSide;
import com.engine.model.Enums.OrderType;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Executors;

public class TradingHttpServer implements EngineEventListener {
    private final int port;
    private final MatchingEngine engine;
    private final Exchange exchange;
    private final IncomingOrderQueue queue;
    private final UserProfileManager um;
    private final DatabaseManager db;
    private final AuthManager auth;
    private final SseBroadcaster broadcaster;
    private HttpServer server;

    public TradingHttpServer(
            int port,
            MatchingEngine engine,
            Exchange exchange,
            IncomingOrderQueue queue,
            UserProfileManager um,
            DatabaseManager db,
            AuthManager auth) {
        this.port = port;
        this.engine = engine;
        this.exchange = exchange;
        this.queue = queue;
        this.um = um;
        this.db = db;
        this.auth = (auth != null) ? auth : new AuthManager();
        this.broadcaster = new SseBroadcaster();

        // Listen for engine events
        this.engine.addListener(this);
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        // API routes
        server.createContext("/api/auth", new AuthHandler());
        server.createContext("/api/stocks", new StocksHandler());
        server.createContext("/api/orders", new OrdersHandler());
        server.createContext("/api/portfolio", new PortfolioHandler());
        server.createContext("/api/account", new AccountHandler());
        server.createContext("/api/admin", new AdminHandler());
        server.createContext("/api/stream", exchange -> broadcaster.register(exchange));

        // Static files (Mobile PWA)
        server.createContext("/", new StaticFileHandler());

        server.start();
        System.out.println("========================================================");
        System.out.println(" Trading Engine & Mobile App Server Live on Port " + port);
        System.out.println(" Mobile App URL: http://localhost:" + port);
        System.out.println("========================================================");
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    // --- SSE Event Dispatching ---
    @Override
    public void onTradeExecuted(TradeRecord t) {
        broadcaster.broadcast("trade", formatTradeJson(t));
        // Also broadcast updated stats
        Stock s = exchange.getStock(t.ticker);
        if (s != null) {
            broadcaster.broadcast("stock_update", formatStockJson(s));
        }
    }

    @Override
    public void onOrderUpdated(Order o) {
        broadcaster.broadcast("order_update", formatOrderJson(o));
    }

    @Override
    public void onOrderBookChanged(String ticker) {
        Stock s = exchange.getStock(ticker);
        if (s != null) {
            broadcaster.broadcast("book_update", formatOrderBookJson(s));
        }
    }

    @Override
    public void onCircuitBreakerTripped(Stock s) {
        broadcaster.broadcast("circuit_breaker", "{\"ticker\":\"" + s.ticker + "\",\"paused\":true}");
    }

    // --- Handlers ---
    private class AuthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            addCorsHeaders(ex);
            String method = ex.getRequestMethod();
            String path = ex.getRequestURI().getPath();

            if ("OPTIONS".equalsIgnoreCase(method)) {
                ex.sendResponseHeaders(204, -1);
                return;
            }

            // POST /api/auth/register
            if (path.endsWith("/register") && "POST".equalsIgnoreCase(method)) {
                String body = readBody(ex);
                Map<String, String> map = parseJsonSimple(body);
                String username = map.get("username");
                String email = map.get("email");
                String fullName = map.get("fullName");
                String password = map.get("password");

                try {
                    AuthManager.UserCredential cred = auth.register(username, email, fullName, password, um, db);
                    UserProfile profile = um.getUser(cred.id);
                    double balance = profile != null ? profile.balance : 100000.0;
                    String json = String.format(Locale.US,
                            "{\"success\":true,\"user\":{\"id\":\"%s\",\"username\":\"%s\",\"email\":\"%s\",\"fullName\":\"%s\",\"balance\":%.2f}}",
                            cred.id, cred.username, cred.email, cred.fullName, balance
                    );
                    sendJson(ex, 200, json);
                } catch (IllegalArgumentException e) {
                    sendJson(ex, 400, "{\"success\":false,\"message\":\"" + e.getMessage() + "\"}");
                }
                return;
            }

            // POST /api/auth/login
            if (path.endsWith("/login") && "POST".equalsIgnoreCase(method)) {
                String body = readBody(ex);
                Map<String, String> map = parseJsonSimple(body);
                String username = map.get("username");
                String password = map.get("password");

                AuthManager.UserCredential cred = auth.login(username, password);
                if (cred != null) {
                    UserProfile profile = um.getUser(cred.id);
                    double balance = profile != null ? profile.balance : 100000.0;
                    String json = String.format(Locale.US,
                            "{\"success\":true,\"user\":{\"id\":\"%s\",\"username\":\"%s\",\"email\":\"%s\",\"fullName\":\"%s\",\"balance\":%.2f}}",
                            cred.id, cred.username, cred.email, cred.fullName, balance
                    );
                    sendJson(ex, 200, json);
                } else {
                    sendJson(ex, 401, "{\"success\":false,\"message\":\"Invalid username or password.\"}");
                }
                return;
            }

            // GET /api/auth/users
            if (path.endsWith("/users") && "GET".equalsIgnoreCase(method)) {
                StringBuilder sb = new StringBuilder("[");
                int i = 0;
                Collection<AuthManager.UserCredential> all = auth.getAllUsers();
                for (AuthManager.UserCredential u : all) {
                    sb.append(String.format("{\"id\":\"%s\",\"username\":\"%s\",\"fullName\":\"%s\"}", u.id, u.username, u.fullName));
                    if (i < all.size() - 1) sb.append(",");
                    i++;
                }
                sb.append("]");
                sendJson(ex, 200, sb.toString());
                return;
            }

            sendJson(ex, 404, "{\"error\":\"Unknown auth endpoint\"}");
        }
    }

    private class StocksHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            addCorsHeaders(ex);
            String method = ex.getRequestMethod();
            String path = ex.getRequestURI().getPath();

            if ("OPTIONS".equalsIgnoreCase(method)) {
                ex.sendResponseHeaders(204, -1);
                return;
            }

            // Route: GET /api/stocks/{ticker}/book
            if (path.matches("^/api/stocks/[A-Za-z0-9_-]+/book$")) {
                String[] parts = path.split("/");
                String ticker = parts[3].toUpperCase();
                Stock s = exchange.getStock(ticker);
                if (s == null) {
                    sendJson(ex, 404, "{\"error\":\"Unknown ticker: " + ticker + "\"}");
                } else {
                    sendJson(ex, 200, formatOrderBookJson(s));
                }
                return;
            }

            // Route: GET /api/stocks/{ticker}/trades
            if (path.matches("^/api/stocks/[A-Za-z0-9_-]+/trades$")) {
                List<TradeRecord> recent = engine.tradeBook.getRecent(30);
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < recent.size(); i++) {
                    sb.append(formatTradeJson(recent.get(i)));
                    if (i < recent.size() - 1) sb.append(",");
                }
                sb.append("]");
                sendJson(ex, 200, sb.toString());
                return;
            }

            // Route: GET /api/stocks
            StringBuilder sb = new StringBuilder("[");
            int i = 0;
            Collection<Stock> all = exchange.getAllStocks();
            for (Stock s : all) {
                sb.append(formatStockJson(s));
                if (i < all.size() - 1) sb.append(",");
                i++;
            }
            sb.append("]");
            sendJson(ex, 200, sb.toString());
        }
    }

    private class OrdersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            addCorsHeaders(ex);
            String method = ex.getRequestMethod();
            String path = ex.getRequestURI().getPath();

            if ("OPTIONS".equalsIgnoreCase(method)) {
                ex.sendResponseHeaders(204, -1);
                return;
            }

            // DELETE /api/orders/{id}
            if ("DELETE".equalsIgnoreCase(method)) {
                String[] parts = path.split("/");
                if (parts.length >= 4) {
                    String orderId = parts[3];
                    boolean success = engine.cancelOrder(orderId);
                    if (success) {
                        sendJson(ex, 200, "{\"success\":true,\"message\":\"Order " + orderId + " cancelled.\"}");
                    } else {
                        sendJson(ex, 400, "{\"success\":false,\"message\":\"Could not cancel order (not found or already filled/cancelled).\"}");
                    }
                    return;
                }
            }

            // POST /api/orders
            if ("POST".equalsIgnoreCase(method)) {
                String body = readBody(ex);
                Map<String, String> map = parseJsonSimple(body);

                try {
                    String traderId = map.getOrDefault("traderId", "").trim();
                    String ticker = map.getOrDefault("ticker", "").trim().toUpperCase();
                    OrderSide side = OrderSide.valueOf(map.getOrDefault("side", "BUY").trim().toUpperCase());
                    double price = Double.parseDouble(map.getOrDefault("price", "0"));
                    int quantity = Integer.parseInt(map.getOrDefault("quantity", "0"));
                    OrderType type = OrderType.valueOf(map.getOrDefault("type", "LIMIT").trim().toUpperCase());

                    String orderId = "ORD-" + System.currentTimeMillis() % 100000;
                    Order newOrder = new Order(orderId, side, price, quantity, type, traderId, ticker);

                    String validation = OrderValidator.validate(newOrder, um, exchange);
                    if ("VALID".equals(validation)) {
                        queue.enqueue(newOrder);
                        sendJson(ex, 200, "{\"success\":true,\"orderId\":\"" + orderId + "\",\"status\":\"ACCEPTED\"}");
                    } else {
                        sendJson(ex, 400, "{\"success\":false,\"message\":\"" + validation + "\"}");
                    }
                } catch (Exception e) {
                    sendJson(ex, 400, "{\"success\":false,\"message\":\"Invalid payload: " + e.getMessage() + "\"}");
                }
                return;
            }

            // GET /api/orders?traderId=...
            if ("GET".equalsIgnoreCase(method)) {
                Map<String, String> query = parseQuery(ex.getRequestURI().getQuery());
                String traderId = query.get("traderId");

                List<Order> orders = (traderId != null && !traderId.isEmpty())
                        ? engine.getOrdersByTrader(traderId)
                        : new ArrayList<>(engine.getAllOrders());

                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < orders.size(); i++) {
                    sb.append(formatOrderJson(orders.get(i)));
                    if (i < orders.size() - 1) sb.append(",");
                }
                sb.append("]");
                sendJson(ex, 200, sb.toString());
            }
        }
    }

    private class PortfolioHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            addCorsHeaders(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
                ex.sendResponseHeaders(204, -1);
                return;
            }

            Map<String, String> query = parseQuery(ex.getRequestURI().getQuery());
            String traderId = query.getOrDefault("traderId", "T001");
            UserProfile user = um.getUser(traderId);

            if (user == null) {
                sendJson(ex, 404, "{\"error\":\"User not found\"}");
                return;
            }

            StringBuilder sb = new StringBuilder("{");
            sb.append("\"traderId\":\"").append(user.traderId).append("\",");
            sb.append("\"name\":\"").append(user.name).append("\",");
            sb.append("\"positions\":[");

            Map<String, Position> portfolio = user.getPortfolio();
            int i = 0;
            for (Map.Entry<String, Position> e : portfolio.entrySet()) {
                String ticker = e.getKey();
                Position p = e.getValue();
                Stock s = exchange.getStock(ticker);
                double currentPrice = (s != null && s.lastTradedPrice > 0) ? s.lastTradedPrice : p.avgCost;
                double unrealizedPnL = (currentPrice - p.avgCost) * p.shares;

                sb.append(String.format(Locale.US,
                        "{\"ticker\":\"%s\",\"shares\":%d,\"avgCost\":%.2f,\"currentPrice\":%.2f,\"unrealizedPnL\":%.2f}",
                        ticker, p.shares, p.avgCost, currentPrice, unrealizedPnL
                ));
                if (i < portfolio.size() - 1) sb.append(",");
                i++;
            }
            sb.append("]}");
            sendJson(ex, 200, sb.toString());
        }
    }

    private class AccountHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            addCorsHeaders(ex);
            String method = ex.getRequestMethod();

            if ("OPTIONS".equalsIgnoreCase(method)) {
                ex.sendResponseHeaders(204, -1);
                return;
            }

            if ("POST".equalsIgnoreCase(method)) {
                // Deposit funds
                String body = readBody(ex);
                Map<String, String> map = parseJsonSimple(body);
                String traderId = map.getOrDefault("traderId", "T001");
                double amount = Double.parseDouble(map.getOrDefault("amount", "0"));

                UserProfile u = um.getUser(traderId);
                if (u != null && amount > 0) {
                    u.deposit(amount);
                    db.syncUserProfile(u);
                    sendJson(ex, 200, "{\"success\":true,\"balance\":" + u.balance + "}");
                } else {
                    sendJson(ex, 400, "{\"success\":false,\"message\":\"Invalid user or deposit amount\"}");
                }
                return;
            }

            Map<String, String> query = parseQuery(ex.getRequestURI().getQuery());
            String traderId = query.getOrDefault("traderId", "T001");
            UserProfile u = um.getUser(traderId);

            if (u == null) {
                sendJson(ex, 404, "{\"error\":\"User not found\"}");
                return;
            }

            String json = String.format(Locale.US,
                    "{\"traderId\":\"%s\",\"name\":\"%s\",\"balance\":%.2f,\"realizedPnL\":%.2f,\"exchangeRevenue\":%.2f}",
                    u.traderId, u.name, u.balance, u.realizedPnL, exchange.totalRevenue
            );
            sendJson(ex, 200, json);
        }
    }

    private class AdminHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            addCorsHeaders(ex);
            String path = ex.getRequestURI().getPath();

            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
                ex.sendResponseHeaders(204, -1);
                return;
            }

            if (path.endsWith("/resume")) {
                Stock s = exchange.getStock("AAPL");
                if (s != null) {
                    s.resumeTrading();
                    broadcaster.broadcast("circuit_breaker", "{\"ticker\":\"AAPL\",\"paused\":false}");
                    broadcaster.broadcast("stock_update", formatStockJson(s));
                }
                sendJson(ex, 200, "{\"success\":true,\"message\":\"Trading resumed for AAPL.\"}");
                return;
            }

            if (path.endsWith("/test-concurrency")) {
                new Thread(() -> {
                    System.out.println("[ADMIN] Starting concurrent test clients...");
                    enqueueSimulatedOrder("SIM-1", "T001", OrderSide.BUY, 99.00, 5);
                    enqueueSimulatedOrder("SIM-2", "T001", OrderSide.BUY, 98.00, 5);
                    enqueueSimulatedOrder("SIM-3", "T001", OrderSide.BUY, 97.00, 5);
                }).start();
                sendJson(ex, 200, "{\"success\":true,\"message\":\"Concurrent orders enqueued.\"}");
                return;
            }

            sendJson(ex, 404, "{\"error\":\"Unknown admin endpoint\"}");
        }
    }

    private void enqueueSimulatedOrder(String id, String traderId, OrderSide side, double price, int qty) {
        Order o = new Order(id, side, price, qty, OrderType.LIMIT, traderId, "AAPL");
        if ("VALID".equals(OrderValidator.validate(o, um, exchange))) {
            queue.enqueue(o);
        }
    }

    // Static Web App Server
    private class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            String path = ex.getRequestURI().getPath();
            if (path.equals("/") || path.isEmpty()) {
                path = "/index.html";
            }

            // Look in current working directory static files
            Path filePath = Paths.get("src/main/resources/static", path);
            if (!Files.exists(filePath)) {
                filePath = Paths.get("trading-engine-fullstack/src/main/resources/static", path);
            }

            if (Files.exists(filePath) && !Files.isDirectory(filePath)) {
                String contentType = "text/html";
                if (path.endsWith(".css")) contentType = "text/css";
                else if (path.endsWith(".js")) contentType = "application/javascript";
                else if (path.endsWith(".json")) contentType = "application/json";
                else if (path.endsWith(".svg")) contentType = "image/svg+xml";

                byte[] data = Files.readAllBytes(filePath);
                ex.getResponseHeaders().set("Content-Type", contentType);
                ex.sendResponseHeaders(200, data.length);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(data);
                }
            } else {
                ex.sendResponseHeaders(404, -1);
            }
        }
    }

    // --- Helpers & JSON formatters ---
    private String formatStockJson(Stock s) {
        return String.format(Locale.US,
                "{\"ticker\":\"%s\",\"tickSize\":%.2f,\"tradingPaused\":%b,\"lastTradedPrice\":%.2f," +
                "\"openPrice\":%.2f,\"highPrice\":%.2f,\"lowPrice\":%.2f,\"volume\":%d}",
                s.ticker, s.tickSize, s.tradingPaused, s.lastTradedPrice,
                s.openPrice, s.highPrice, (s.lowPrice == Double.MAX_VALUE ? 0.0 : s.lowPrice), s.volume
        );
    }

    private String formatOrderBookJson(Stock s) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"ticker\":\"").append(s.ticker).append("\",");
        sb.append("\"tradingPaused\":").append(s.tradingPaused).append(",");
        sb.append("\"lastPrice\":").append(s.lastTradedPrice).append(",");

        // Bids
        sb.append("\"bids\":[");
        List<Map<String, Object>> bids = engine.orderBook.getBidLevels(8);
        for (int i = 0; i < bids.size(); i++) {
            Map<String, Object> m = bids.get(i);
            sb.append(String.format(Locale.US, "{\"price\":%.2f,\"quantity\":%d,\"count\":%d}",
                    m.get("price"), m.get("quantity"), m.get("orderCount")));
            if (i < bids.size() - 1) sb.append(",");
        }
        sb.append("],");

        // Asks
        sb.append("\"asks\":[");
        List<Map<String, Object>> asks = engine.orderBook.getAskLevels(8);
        for (int i = 0; i < asks.size(); i++) {
            Map<String, Object> m = asks.get(i);
            sb.append(String.format(Locale.US, "{\"price\":%.2f,\"quantity\":%d,\"count\":%d}",
                    m.get("price"), m.get("quantity"), m.get("orderCount")));
            if (i < asks.size() - 1) sb.append(",");
        }
        sb.append("]}");
        return sb.toString();
    }

    private String formatTradeJson(TradeRecord t) {
        return String.format(Locale.US,
                "{\"tradeId\":\"%s\",\"ticker\":\"%s\",\"price\":%.2f,\"quantity\":%d,\"totalAmount\":%.2f," +
                "\"fee\":%.2f,\"buyerId\":\"%s\",\"sellerId\":\"%s\",\"timestamp\":%d}",
                t.tradeId, t.ticker, t.price, t.quantity, t.totalAmount, t.fee, t.buyTraderId, t.sellTraderId, t.timestamp
        );
    }

    private String formatOrderJson(Order o) {
        return String.format(Locale.US,
                "{\"id\":\"%s\",\"traderId\":\"%s\",\"ticker\":\"%s\",\"side\":\"%s\",\"type\":\"%s\"," +
                "\"price\":%.2f,\"initialQuantity\":%d,\"remainingQuantity\":%d,\"filledQuantity\":%d," +
                "\"status\":\"%s\",\"timestamp\":%d}",
                o.id, o.traderId, o.ticker, o.side.name(), o.type.name(),
                o.price, o.initialQuantity, o.quantity, o.getFilledQuantity(), o.status.name(), o.timestamp
        );
    }

    private void addCorsHeaders(HttpExchange ex) {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    private void sendJson(HttpExchange ex, int code, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String readBody(HttpExchange ex) throws IOException {
        try (InputStream is = ex.getRequestBody();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[1024];
            int n;
            while ((n = is.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            return baos.toString(StandardCharsets.UTF_8);
        }
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isEmpty()) return map;
        for (String param : query.split("&")) {
            String[] pair = param.split("=");
            if (pair.length > 1) {
                map.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
            } else if (pair.length == 1) {
                map.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8), "");
            }
        }
        return map;
    }

    private Map<String, String> parseJsonSimple(String body) {
        Map<String, String> map = new HashMap<>();
        if (body == null) return map;
        String trimmed = body.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
            String[] tokens = trimmed.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
            for (String token : tokens) {
                String[] kv = token.split(":(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                if (kv.length == 2) {
                    String k = kv[0].trim().replace("\"", "");
                    String v = kv[1].trim().replace("\"", "");
                    map.put(k, v);
                }
            }
        }
        return map;
    }
}
