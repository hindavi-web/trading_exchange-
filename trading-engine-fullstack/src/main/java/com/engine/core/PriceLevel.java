package com.engine.core;

import com.engine.model.Order;

public class PriceLevel {
    public final double price;
    public final RingBuffer queue;
    public int totalQuantity;

    public PriceLevel(double price) {
        this.price = price;
        this.queue = new RingBuffer(64);
        this.totalQuantity = 0;
    }

    public void addOrder(Order o) {
        queue.enqueue(o);
        totalQuantity += o.quantity;
    }

    public Order peekOrder() {
        return queue.peek();
    }

    public Order popOrder() {
        Order o = queue.dequeue();
        if (o != null) {
            totalQuantity -= o.quantity;
        }
        return o;
    }

    public void reduceQuantity(int n) {
        totalQuantity -= n;
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }
}
