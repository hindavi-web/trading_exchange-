package com.engine.core;

import com.engine.model.Order;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OrderLog {
    private final List<String> log = Collections.synchronizedList(new ArrayList<>());

    public void record(Order order, String note) {
        String entry = String.format("[LOG] %s | %s | %s", order.id, order.status, note);
        log.add(entry);
        System.out.println(entry);
    }

    public void recordSystemEvent(String note) {
        String entry = "[SYS] " + note;
        log.add(entry);
        System.out.println(entry);
    }

    public List<String> getRecent(int count) {
        synchronized (log) {
            int size = log.size();
            int from = Math.max(0, size - count);
            return new ArrayList<>(log.subList(from, size));
        }
    }
}
