package com.engine.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class UserProfile {
    public final String traderId;
    public final String name;

    public double balance;
    public double lockedBalance = 0.0;
    public double realizedPnL = 0.0;

    private final Map<String, Position> portfolio = new HashMap<>();

    public UserProfile(String traderId, String name, double balance) {
        this.traderId = traderId;
        this.name = name;
        this.balance = balance;
    }

    public synchronized void deposit(double amount) {
        if (amount > 0) {
            balance += amount;
        }
    }

    public synchronized void settleBuy(String ticker, int qty, double price, double fee) {
        double totalCost = (qty * price) + fee;
        balance -= totalCost;

        Position p = portfolio.computeIfAbsent(ticker, k -> new Position());
        double cumulativeCost = (p.shares * p.avgCost) + (qty * price);
        p.shares += qty;
        p.avgCost = cumulativeCost / p.shares;
    }

    public synchronized void settleSell(String ticker, int qty, double price, double fee) {
        double totalRevenue = (qty * price) - fee;
        balance += totalRevenue;

        Position p = portfolio.get(ticker);
        if (p != null) {
            p.shares -= qty;
            double pnl = ((price - p.avgCost) * qty) - fee;
            realizedPnL += pnl;

            if (p.shares <= 0) {
                portfolio.remove(ticker);
            }
        }
    }

    public synchronized void addShares(String ticker, int qty, double initialCost) {
        Position p = portfolio.computeIfAbsent(ticker, k -> new Position());
        p.shares += qty;
        p.avgCost = initialCost;
    }

    public synchronized int getShares(String ticker) {
        Position p = portfolio.get(ticker);
        return p != null ? p.shares : 0;
    }

    public synchronized Map<String, Position> getPortfolio() {
        return Collections.unmodifiableMap(new HashMap<>(portfolio));
    }

    @Override
    public synchronized String toString() {
        return String.format(
                "User[%s | %s | Bal: $%.2f | P&L: $%.2f | Portfolio: %s]",
                traderId, name, balance, realizedPnL, portfolio
        );
    }
}
