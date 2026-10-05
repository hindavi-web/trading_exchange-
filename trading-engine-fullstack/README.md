# Apex Matching Engine - Full-Stack Mobile Trading Platform

A complete full-stack stock order matching system combining an in-memory matching engine, MySQL persistence, real-time REST/SSE APIs, and a mobile trading frontend styled after Zerodha Kite and Robinhood.

---

## Architecture Overview

```
                                  +------------------------------------+
                                  |    Mobile Trading App (PWA) /      |
                                  |    React Native (iOS & Android)    |
                                  +------------------------------------+
                                             |              ^
                               (REST Orders) |              | (SSE Real-time Stream)
                                             v              |
                                  +------------------------------------+
                                  |       TradingHttpServer (8080)     |
                                  +------------------------------------+
                                             |
                                   (Pre-Trade Validation)
                                             v
                                  +------------------------------------+
                                  |     IncomingOrderQueue (FIFO)      |
                                  +------------------------------------+
                                             |
                                   (Single-Writer Dispatch)
                                             v
                                  +------------------------------------+
                                  |   MatchingWorker -> MatchingEngine |
                                  |   - Level-2 OrderBook (TreeMaps)   |
                                  |   - StopOrderBook                  |
                                  |   - Circuit Breaker                |
                                  +------------------------------------+
                                       |                     |
                      (Executions / Fills)      (Write-Behind Persistence)
                                       v                     v
                             +------------------+  +--------------------+
                             | TradeBook (Tape) |  |   DatabaseManager  |
                             +------------------+  |     (MySQL DDL)    |
                                                   +--------------------+
```

---

## 1. Quick Start

### Run the Backend & Mobile Web App (Zero Configuration)
Requirements: **Java 21** installed.

```bash
# Windows
.\run.bat

# Linux / macOS
chmod +x run.sh
./run.sh
```

Then open your browser on your desktop or mobile device:
👉 **`http://localhost:8080`**

---

## 2. MySQL Database Setup

1. Open your MySQL client (MySQL Workbench, phpMyAdmin, or command-line):
   ```bash
   mysql -u root -p < db/schema.sql
   mysql -u root -p < db/seed.sql
   ```
2. The schema creates the following tables:
   - `users`: Identity and trader status.
   - `accounts`: Cash balance, locked balance, realized P&L.
   - `stocks`: Tickers, tick sizes, circuit breaker flags, high/low/open stats.
   - `positions`: Trader share holdings, locked shares, weighted average cost basis.
   - `orders`: Order lifecycle (`NEW`, `PARTIALLY_FILLED`, `FILLED`, `CANCELLED`, `REJECTED`).
   - `trades`: Matched executions with buyer and seller IDs and exchange fees.
   - `exchange_revenue`: Cumulative exchange fee ledger.

*Note: If MySQL is not running on your machine, the backend will automatically operate in high-performance in-memory mode, so you can test all trading features right away.*

---

## 3. Mobile Frontend Features

1. **Watchlist & Markets:** Live prices, volume, and automatic **Circuit Breaker Halt** banners.
2. **Level 2 Market Depth (Order Book):** Dual-column ladder showing bids (green) vs asks (red) with real-time volume depth bars and spread display.
3. **Interactive Mini Chart:** Dynamic price trend line with gradient fill.
4. **Order Pad Sheet (Buy/Sell Modal):**
   - Toggle BUY / SELL.
   - Types: `LIMIT`, `MARKET`, `IOC`, `FOK`, `STOP_LOSS`, `STOP_BUY`.
   - Real-time fee estimator (0.1% exchange fee) and pre-trade balance/shares checks.
5. **Orders Tab:**
   - Open / Resting orders with 1-click **Cancel Order** button.
   - Executed trades history.
   - Rejected / Cancelled order logs.
6. **Portfolio & Wallet:**
   - Realized P&L and unrealized P&L per stock holding.
   - One-click Deposit funds simulation.
   - Trader Identity Switcher (`Alice T001`, `Bob T002`, `Carol T003`) to simulate multi-user trading instantly.

---

## 4. REST API Reference

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/stocks` | List all stocks with live market statistics |
| `GET` | `/api/stocks/{ticker}/book` | Top bids and asks depth ladder |
| `GET` | `/api/stocks/{ticker}/trades` | Recent executed trades tape |
| `POST` | `/api/orders` | Submit new order (`{ traderId, ticker, side, price, quantity, type }`) |
| `DELETE` | `/api/orders/{id}` | Cancel resting limit or stop order |
| `GET` | `/api/orders?traderId={id}` | Get orders for trader |
| `GET` | `/api/portfolio?traderId={id}`| Get stock holdings and unrealized P&L |
| `GET` | `/api/account?traderId={id}` | Get cash balance, realized P&L, exchange fees |
| `POST` | `/api/account` | Deposit cash (`{ traderId, amount }`) |
| `POST` | `/api/admin/resume` | Reset circuit breaker for halted stock |
| `POST` | `/api/admin/test-concurrency` | Run concurrent client stress test |
| `GET` | `/api/stream` | Server-Sent Events (SSE) live push stream |
