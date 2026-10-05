package com.engine.core;

import com.engine.model.Order;
import com.engine.model.Stock;
import com.engine.model.TradeRecord;

public interface EngineEventListener {
    void onTradeExecuted(TradeRecord trade);
    void onOrderUpdated(Order order);
    void onOrderBookChanged(String ticker);
    void onCircuitBreakerTripped(Stock stock);
}
