# Apex Stock Order Matching Engine & Full-Stack Trading Platform

A high-performance stock order matching engine and full-stack trading exchange built in Java with real-time web trading frontend, REST/SSE APIs, and database persistence.

---

## 🚀 Key Features

- **Authentication & User Management**: Simple login & signup with persistent database storage.
- **High-Performance Matching Engine**: Price-time priority (FIFO) matching algorithm supporting `LIMIT`, `MARKET`, `IOC` (Immediate-Or-Cancel), `FOK` (Fill-Or-Kill), and `STOP_LOSS` orders.
- **Circuit Breaker System**: Automatic trading halt on extreme price movements with admin resume capability.
- **Real-Time Market Depth**: Live Level-2 Order Book ladder (Bids/Asks) and real-time trade tape.
- **Modern Clean UI**: Simplified, intuitive trading dashboard with watchlists, quick order pad, portfolio tracking, and order history.
- **Database Persistence**: MySQL schema and write-behind persistence with automatic fallback to high-speed in-memory mode.

---

## 📁 Project Structure

```
Stock-Order-Matching-Engine-main/
├── README.md
├── .gitignore
└── trading-engine-fullstack/
    ├── run.bat                 # 1-click run script for Windows
    ├── run.sh                  # 1-click run script for Linux/macOS
    ├── db/
    │   ├── schema.sql          # MySQL database schema & tables
    │   └── seed.sql            # Initial sample seed data
    ├── src/
    │   └── main/
    │       ├── java/com/engine/ # Backend Engine (Core, Model, DB, API)
    │       └── resources/static/# Web Frontend (HTML, CSS, JS)
    └── mobile-native/          # React Native mobile trading client
```

---

## ⚡ Quick Start

### 1. Run Backend & Frontend (Zero Configuration)
Requirements: **Java 21** or later.

#### Windows:
```cmd
cd trading-engine-fullstack
run.bat
```

#### Linux / macOS:
```bash
cd trading-engine-fullstack
chmod +x run.sh
./run.sh
```

### 2. Access the Application
Open your browser and visit:
👉 **http://localhost:8080**

---

## 🗄️ Database Setup (Optional)
If you have MySQL installed:
```bash
mysql -u root -p < trading-engine-fullstack/db/schema.sql
mysql -u root -p < trading-engine-fullstack/db/seed.sql
```
*(If MySQL is not running, the application automatically runs in high-performance in-memory mode).*

---

## 📡 REST API Reference

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/api/auth/register` | Register new user account |
| `POST` | `/api/auth/login` | Login user account |
| `GET` | `/api/stocks` | List stocks with live market statistics |
| `GET` | `/api/stocks/{ticker}/book` | Top bids and asks depth ladder |
| `GET` | `/api/stocks/{ticker}/trades` | Recent executed trades |
| `POST` | `/api/orders` | Place new order (`LIMIT`, `MARKET`, etc.) |
| `DELETE` | `/api/orders/{id}` | Cancel resting order |
| `GET` | `/api/orders?traderId={id}` | View trader order history |
| `GET` | `/api/portfolio?traderId={id}` | Trader stock holdings & P&L |
| `GET` | `/api/account?traderId={id}` | Cash balance and realized P&L |
| `POST` | `/api/account` | Deposit simulated funds |
| `GET` | `/api/stream` | Server-Sent Events (SSE) live updates |