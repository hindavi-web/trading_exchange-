package com.engine.model;

public class Enums {
    public enum OrderSide {
        BUY, SELL
    }

    public enum OrderType {
        MARKET, LIMIT, IOC, FOK,
        MARKET_IOC, MARKET_FOK, LIMIT_IOC, LIMIT_FOK,
        STOP_LOSS, STOP_BUY
    }

    public enum OrderStatus {
        NEW, REJECTED, PARTIALLY_FILLED, FILLED, CANCELLED
    }
}
