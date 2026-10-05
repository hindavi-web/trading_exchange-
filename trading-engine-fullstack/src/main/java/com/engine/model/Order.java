package com.engine.model;

import com.engine.model.Enums.OrderSide;
import com.engine.model.Enums.OrderStatus;
import com.engine.model.Enums.OrderType;

public class Order {
    public final String id;
    public final long timestamp;
    public final OrderSide side;
    public final OrderType type;
    public final String traderId;
    public final String ticker;
    public final double price;
    public final int initialQuantity;

    public int quantity; // remaining unfilled quantity
    public OrderStatus status;

    public Order(String id, OrderSide side, double price, int quantity,
                 OrderType type, String traderId, String ticker) {
        this.id = id;
        this.timestamp = System.currentTimeMillis();
        this.side = side;
        this.price = price;
        this.initialQuantity = quantity;
        this.quantity = quantity;
        this.type = type;
        this.traderId = traderId;
        this.ticker = ticker;
        this.status = OrderStatus.NEW;
    }

    public int getFilledQuantity() {
        return initialQuantity - quantity;
    }

    @Override
    public String toString() {
        return String.format(
                "Order[%s | %s | %s | $%.2f | rem=%d/%d | %s | %s]",
                id, side, type, price, quantity, initialQuantity, status, traderId
        );
    }
}
