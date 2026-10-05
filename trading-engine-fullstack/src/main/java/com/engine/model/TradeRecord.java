package com.engine.model;

import java.util.UUID;

public class TradeRecord {
    public final String tradeId;
    public final String buyOrderId, sellOrderId;
    public final String buyTraderId, sellTraderId;
    public final String ticker;
    public final double price;
    public final int quantity;
    public final double totalAmount;
    public final double fee;
    public final long timestamp;

    public TradeRecord(Order buy, Order sell, double price, int qty, String ticker, double feeRate) {
        this.tradeId = "TRD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.buyOrderId = buy.id;
        this.sellOrderId = sell.id;
        this.buyTraderId = buy.traderId;
        this.sellTraderId = sell.traderId;
        this.price = price;
        this.quantity = qty;
        this.totalAmount = price * qty;
        this.fee = this.totalAmount * feeRate;
        this.ticker = ticker;
        this.timestamp = System.currentTimeMillis();
    }

    @Override
    public String toString() {
        return String.format(
                "Trade[%s | %s | buyer=%s | seller=%s | $%.2f | qty=%d | fee=$%.2f]",
                tradeId, ticker, buyTraderId, sellTraderId, price, quantity, fee
        );
    }
}
