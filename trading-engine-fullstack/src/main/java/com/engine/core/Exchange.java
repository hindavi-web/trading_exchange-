package com.engine.core;

import com.engine.model.Stock;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Exchange {
    private final Map<String, Stock> stocks = new ConcurrentHashMap<>();

    public double totalRevenue = 0.0;
    public final double FEE_RATE = 0.001; // 0.1%

    public void registerStock(String ticker, double tickSize) {
        stocks.put(ticker, new Stock(ticker, tickSize));
    }

    public boolean isValidTicker(String ticker) {
        return stocks.containsKey(ticker);
    }

    public Stock getStock(String ticker) {
        return stocks.get(ticker);
    }

    public Collection<Stock> getAllStocks() {
        return stocks.values();
    }

    public synchronized void collectFee(double feeAmount) {
        totalRevenue += feeAmount;
    }
}
