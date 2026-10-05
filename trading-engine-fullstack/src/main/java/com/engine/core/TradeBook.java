package com.engine.core;

import com.engine.model.TradeRecord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TradeBook {
    private final List<TradeRecord> tape = Collections.synchronizedList(new ArrayList<>());

    public void recordTrade(TradeRecord trade) {
        tape.add(trade);
    }

    public List<TradeRecord> getAll() {
        return new ArrayList<>(tape);
    }

    public List<TradeRecord> getRecent(int limit) {
        synchronized (tape) {
            int size = tape.size();
            int from = Math.max(0, size - limit);
            List<TradeRecord> sub = new ArrayList<>(tape.subList(from, size));
            Collections.reverse(sub);
            return sub;
        }
    }

    public void printFullTape() {
        System.out.println("\n===== FULL TRADE TAPE =====");
        for (TradeRecord t : getAll()) {
            System.out.println("  " + t);
        }
        System.out.println("============================");
    }
}
