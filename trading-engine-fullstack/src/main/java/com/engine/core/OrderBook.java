package com.engine.core;

import com.engine.model.Enums.OrderSide;
import com.engine.model.Enums.OrderType;
import com.engine.model.Order;
import java.util.*;

public class OrderBook {
    public final TreeMap<Double, PriceLevel> bids = new TreeMap<>(Collections.reverseOrder());
    public final TreeMap<Double, PriceLevel> asks = new TreeMap<>();

    public synchronized void addOrder(Order order) {
        TreeMap<Double, PriceLevel> side = (order.side == OrderSide.BUY) ? bids : asks;
        side.putIfAbsent(order.price, new PriceLevel(order.price));
        side.get(order.price).addOrder(order);
        System.out.println("Added to book: " + order);
    }

    public synchronized boolean cancelOrder(String orderId) {
        for (PriceLevel pl : bids.values()) {
            if (pl.queue.remove(orderId)) {
                recalculateTotal(pl);
                cleanupEmptyLevels();
                return true;
            }
        }
        for (PriceLevel pl : asks.values()) {
            if (pl.queue.remove(orderId)) {
                recalculateTotal(pl);
                cleanupEmptyLevels();
                return true;
            }
        }
        return false;
    }

    private void recalculateTotal(PriceLevel pl) {
        int sum = 0;
        for (Order o : pl.queue.toList()) {
            sum += o.quantity;
        }
        pl.totalQuantity = sum;
    }

    private void cleanupEmptyLevels() {
        bids.entrySet().removeIf(e -> e.getValue().isEmpty() || e.getValue().totalQuantity <= 0);
        asks.entrySet().removeIf(e -> e.getValue().isEmpty() || e.getValue().totalQuantity <= 0);
    }

    public synchronized int availableQuantity(Order incoming) {
        TreeMap<Double, PriceLevel> opposite = (incoming.side == OrderSide.BUY) ? asks : bids;
        boolean isMarket = (incoming.type == OrderType.MARKET_FOK || incoming.type == OrderType.MARKET);
        int total = 0;

        for (Map.Entry<Double, PriceLevel> entry : opposite.entrySet()) {
            double lvlPrice = entry.getKey();
            if (!isMarket) {
                boolean ok = (incoming.side == OrderSide.BUY)
                        ? lvlPrice <= incoming.price
                        : lvlPrice >= incoming.price;
                if (!ok) break;
            }
            for (Order r : entry.getValue().queue.toList()) {
                if (!r.traderId.equals(incoming.traderId)) {
                    total += r.quantity;
                }
            }
            if (total >= incoming.quantity) return total;
        }
        return total;
    }

    public synchronized List<Map<String, Object>> getBidLevels(int limit) {
        List<Map<String, Object>> list = new ArrayList<>();
        int count = 0;
        for (Map.Entry<Double, PriceLevel> e : bids.entrySet()) {
            if (e.getValue().totalQuantity > 0) {
                Map<String, Object> m = new HashMap<>();
                m.put("price", e.getKey());
                m.put("quantity", e.getValue().totalQuantity);
                m.put("orderCount", e.getValue().queue.getSize());
                list.add(m);
                count++;
                if (count >= limit) break;
            }
        }
        return list;
    }

    public synchronized List<Map<String, Object>> getAskLevels(int limit) {
        List<Map<String, Object>> list = new ArrayList<>();
        int count = 0;
        for (Map.Entry<Double, PriceLevel> e : asks.entrySet()) {
            if (e.getValue().totalQuantity > 0) {
                Map<String, Object> m = new HashMap<>();
                m.put("price", e.getKey());
                m.put("quantity", e.getValue().totalQuantity);
                m.put("orderCount", e.getValue().queue.getSize());
                list.add(m);
                count++;
                if (count >= limit) break;
            }
        }
        return list;
    }

    public synchronized List<Order> getAllRestingOrders() {
        List<Order> list = new ArrayList<>();
        for (PriceLevel pl : bids.values()) {
            list.addAll(pl.queue.toList());
        }
        for (PriceLevel pl : asks.values()) {
            list.addAll(pl.queue.toList());
        }
        return list;
    }

    public void printBook() {
        System.out.println("\n===== ORDER BOOK =====");
        System.out.println("-- ASKS (lowest first) --");
        for (Map.Entry<Double, PriceLevel> e : asks.entrySet()) {
            System.out.printf("  $%.2f x %d%n", e.getKey(), e.getValue().totalQuantity);
        }
        System.out.println("-- BIDS (highest first) --");
        for (Map.Entry<Double, PriceLevel> e : bids.entrySet()) {
            System.out.printf("  $%.2f x %d%n", e.getKey(), e.getValue().totalQuantity);
        }
        System.out.println("======================");
    }
}
