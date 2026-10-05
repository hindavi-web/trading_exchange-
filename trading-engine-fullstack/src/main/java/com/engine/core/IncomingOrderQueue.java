package com.engine.core;

import com.engine.model.Order;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class IncomingOrderQueue {
    private final BlockingQueue<Order> queue = new LinkedBlockingQueue<>();

    public void enqueue(Order order) {
        queue.offer(order);
    }

    public Order dequeue() throws InterruptedException {
        return queue.take();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public int size() {
        return queue.size();
    }
}
