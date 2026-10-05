package com.engine.core;

import com.engine.model.Enums.OrderType;
import com.engine.model.Order;
import java.util.*;

public class StopOrderBook {
    public final TreeMap<Double, List<Order>> stopLoss = new TreeMap<>(Collections.reverseOrder());
    public final TreeMap<Double, List<Order>> stopBuy = new TreeMap<>();

    public synchronized void addStopOrder(Order order) {
        if (order.type == OrderType.STOP_LOSS) {
            stopLoss.computeIfAbsent(order.price, k -> new ArrayList<>()).add(order);
        } else {
            stopBuy.computeIfAbsent(order.price, k -> new ArrayList<>()).add(order);
        }
        System.out.println("Stop order stored: " + order);
    }

    public synchronized boolean cancelStopOrder(String orderId) {
        for (List<Order> list : stopLoss.values()) {
            if (list.removeIf(o -> o.id.equals(orderId))) return true;
        }
        for (List<Order> list : stopBuy.values()) {
            if (list.removeIf(o -> o.id.equals(orderId))) return true;
        }
        return false;
    }

    public synchronized List<Order> getTriggeredOrders(double marketPrice) {
        List<Order> triggered = new ArrayList<>();

        for (Map.Entry<Double, List<Order>> e : stopLoss.entrySet()) {
            if (marketPrice <= e.getKey()) {
                triggered.addAll(e.getValue());
            }
        }

        for (Map.Entry<Double, List<Order>> e : stopBuy.entrySet()) {
            if (marketPrice >= e.getKey()) {
                triggered.addAll(e.getValue());
            }
        }

        stopLoss.entrySet().removeIf(e -> triggered.containsAll(e.getValue()));
        stopBuy.entrySet().removeIf(e -> triggered.containsAll(e.getValue()));

        return triggered;
    }

    public synchronized List<Order> getAllStopOrders() {
        List<Order> all = new ArrayList<>();
        for (List<Order> list : stopLoss.values()) all.addAll(list);
        for (List<Order> list : stopBuy.values()) all.addAll(list);
        return all;
    }
}
