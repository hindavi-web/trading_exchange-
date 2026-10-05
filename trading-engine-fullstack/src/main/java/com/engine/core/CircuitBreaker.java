package com.engine.core;

import com.engine.model.Stock;

public class CircuitBreaker {
    public final double thresholdPercentage;
    public volatile boolean tripped;

    public CircuitBreaker(double thresholdPercentage) {
        this.thresholdPercentage = thresholdPercentage;
        this.tripped = false;
    }

    public void check(Stock stock, OrderLog log) {
        if (tripped || stock.openPrice == 0) return;

        double deviation = Math.abs(stock.lastTradedPrice - stock.openPrice) / stock.openPrice;

        if (deviation >= (thresholdPercentage / 100.0)) {
            stock.pauseTrading();
            tripped = true;
            log.recordSystemEvent(
                    "CIRCUIT BREAKER TRIPPED for "
                            + stock.ticker
                            + " at deviation "
                            + String.format("%.2f", deviation * 100)
                            + "%"
            );
        }
    }

    public void reset() {
        tripped = false;
    }
}
