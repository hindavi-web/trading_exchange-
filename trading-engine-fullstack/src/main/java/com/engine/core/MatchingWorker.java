package com.engine.core;

import com.engine.model.Order;

public class MatchingWorker implements Runnable {
    private final IncomingOrderQueue queue;
    private final MatchingEngine engine;
    private final Exchange exchange;

    private volatile boolean running = true;
    private volatile boolean processing = false;

    public MatchingWorker(IncomingOrderQueue queue, MatchingEngine engine, Exchange exchange) {
        this.queue = queue;
        this.engine = engine;
        this.exchange = exchange;
    }

    @Override
    public void run() {
        while (running) {
            try {
                Order order = queue.dequeue();
                processing = true;
                try {
                    System.out.println("[WORKER] Taking order " + order.id);
                    engine.process(order, exchange.getStock(order.ticker));
                    System.out.println("[WORKER] Finished order " + order.id);
                } finally {
                    processing = false;
                }
            } catch (InterruptedException e) {
                processing = false;
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public void stop() {
        running = false;
    }

    public boolean isProcessing() {
        return processing;
    }
}
