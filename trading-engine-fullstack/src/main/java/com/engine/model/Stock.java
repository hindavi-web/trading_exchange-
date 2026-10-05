package com.engine.model;

import com.engine.core.CircuitBreaker;

public class Stock {
    public final String ticker;
    public final double tickSize;
    public volatile boolean tradingPaused;

    public double lastTradedPrice = 0.0;
    public double openPrice = 0.0;
    public double highPrice = 0.0;
    public double lowPrice = Double.MAX_VALUE;
    public int volume = 0;

    public final CircuitBreaker circuitBreaker;

    public Stock(String ticker, double tickSize) {
        this.ticker = ticker;
        this.tickSize = tickSize;
        this.tradingPaused = false;
        this.circuitBreaker = new CircuitBreaker(5.0);
    }

    public synchronized void updateMarketStats(double price, int qty) {
        if (openPrice == 0.0) openPrice = price;

        lastTradedPrice = price;
        highPrice = Math.max(highPrice, price);
        lowPrice = Math.min(lowPrice, price);
        volume += qty;
    }

    public void pauseTrading() {
        tradingPaused = true;
    }

    public void resumeTrading() {
        tradingPaused = false;
        circuitBreaker.reset();
    }
}
