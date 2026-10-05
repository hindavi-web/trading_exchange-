package com.engine.core;

import com.engine.model.Enums.OrderSide;
import com.engine.model.Enums.OrderStatus;
import com.engine.model.Enums.OrderType;
import com.engine.model.Order;
import com.engine.model.Stock;
import com.engine.model.TradeRecord;
import com.engine.model.UserProfile;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class MatchingEngine {
    public final Exchange exchange;
    public final OrderBook orderBook;
    public final StopOrderBook stopOrderBook;
    public final TradeBook tradeBook;
    public final OrderLog orderLog;
    public final UserProfileManager um;

    // Track all orders by ID for quick API queries
    private final Map<String, Order> allOrders = new ConcurrentHashMap<>();
    private final List<EngineEventListener> listeners = new CopyOnWriteArrayList<>();

    public MatchingEngine(
            Exchange exchange,
            OrderBook ob,
            StopOrderBook sob,
            TradeBook tb,
            OrderLog ol,
            UserProfileManager um) {
        this.exchange = exchange;
        this.orderBook = ob;
        this.stopOrderBook = sob;
        this.tradeBook = tb;
        this.orderLog = ol;
        this.um = um;
    }

    public void addListener(EngineEventListener listener) {
        listeners.add(listener);
    }

    public Order getOrder(String id) {
        return allOrders.get(id);
    }

    public Collection<Order> getAllOrders() {
        return allOrders.values();
    }

    public List<Order> getOrdersByTrader(String traderId) {
        List<Order> list = new ArrayList<>();
        for (Order o : allOrders.values()) {
            if (o.traderId.equals(traderId)) {
                list.add(o);
            }
        }
        list.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));
        return list;
    }

    public synchronized boolean cancelOrder(String orderId) {
        Order o = allOrders.get(orderId);
        if (o == null) return false;

        if (o.status == OrderStatus.FILLED || o.status == OrderStatus.CANCELLED || o.status == OrderStatus.REJECTED) {
            return false;
        }

        boolean removed = orderBook.cancelOrder(orderId);
        if (!removed) {
            removed = stopOrderBook.cancelStopOrder(orderId);
        }

        if (removed || o.status == OrderStatus.NEW || o.status == OrderStatus.PARTIALLY_FILLED) {
            o.status = OrderStatus.CANCELLED;
            orderLog.record(o, "Order manually CANCELLED");
            notifyOrderUpdated(o);
            notifyBookChanged(o.ticker);
            return true;
        }
        return false;
    }

    public void process(Order order, Stock stock) {
        allOrders.put(order.id, order);

        if (stock.tradingPaused
                && order.type != OrderType.STOP_LOSS
                && order.type != OrderType.STOP_BUY) {
            order.status = OrderStatus.REJECTED;
            orderLog.record(order, "REJECTED: Circuit Breaker active. Trading is paused.");
            notifyOrderUpdated(order);
            return;
        }

        System.out.println("\nProcessing: " + order);

        switch (order.type) {
            case STOP_LOSS:
            case STOP_BUY:
                handleStop(order);
                break;
            case MARKET:
                handleMarketNormal(order, stock);
                break;
            case MARKET_IOC:
                handleMarketIOC(order, stock);
                break;
            case MARKET_FOK:
                handleMarketFOK(order, stock);
                break;
            case LIMIT:
                handleLimitNormal(order, stock);
                break;
            case LIMIT_IOC:
                handleLimitIOC(order, stock);
                break;
            case LIMIT_FOK:
                handleLimitFOK(order, stock);
                break;
            default:
                order.status = OrderStatus.REJECTED;
                orderLog.record(order, "Unknown order type");
                notifyOrderUpdated(order);
        }
    }

    private void handleStop(Order order) {
        stopOrderBook.addStopOrder(order);
        orderLog.record(order, "Stored in stop book at threshold: " + order.price);
        notifyOrderUpdated(order);
    }

    private void handleMarketNormal(Order order, Stock stock) {
        match(order, stock, true);
        if (order.quantity > 0) {
            orderLog.record(order, "Partial fill - remainder added to book");
        } else {
            orderLog.record(order, "Fully filled");
        }
        notifyOrderUpdated(order);
        notifyBookChanged(stock.ticker);
    }

    private void handleMarketIOC(Order order, Stock stock) {
        match(order, stock, false);
        if (order.quantity > 0) {
            order.status = OrderStatus.CANCELLED;
            orderLog.record(order, "IOC - unfilled remainder cancelled");
        } else {
            orderLog.record(order, "IOC - fully filled");
        }
        notifyOrderUpdated(order);
        notifyBookChanged(stock.ticker);
    }

    private void handleMarketFOK(Order order, Stock stock) {
        int available = orderBook.availableQuantity(order);
        if (available < order.quantity) {
            order.status = OrderStatus.CANCELLED;
            orderLog.record(order, "FOK failed - only " + available + " available, needed " + order.quantity);
            notifyOrderUpdated(order);
            return;
        }
        match(order, stock, false);
        orderLog.record(order, "FOK - fully filled");
        notifyOrderUpdated(order);
        notifyBookChanged(stock.ticker);
    }

    private void handleLimitNormal(Order order, Stock stock) {
        match(order, stock, true);
        if (order.quantity > 0) {
            orderLog.record(order, "Partial/no fill - remainder added to book");
        } else {
            orderLog.record(order, "Fully filled");
        }
        notifyOrderUpdated(order);
        notifyBookChanged(stock.ticker);
    }

    private void handleLimitIOC(Order order, Stock stock) {
        match(order, stock, false);
        if (order.quantity > 0) {
            order.status = OrderStatus.CANCELLED;
            orderLog.record(order, "Limit IOC - unfilled remainder cancelled");
        } else {
            orderLog.record(order, "Limit IOC - fully filled");
        }
        notifyOrderUpdated(order);
        notifyBookChanged(stock.ticker);
    }

    private void handleLimitFOK(Order order, Stock stock) {
        int available = orderBook.availableQuantity(order);
        if (available < order.quantity) {
            order.status = OrderStatus.CANCELLED;
            orderLog.record(order, "Limit FOK failed - only " + available + " available at limit, needed " + order.quantity);
            notifyOrderUpdated(order);
            return;
        }
        match(order, stock, false);
        orderLog.record(order, "Limit FOK - fully filled");
        notifyOrderUpdated(order);
        notifyBookChanged(stock.ticker);
    }

    private void match(Order incoming, Stock stock, boolean addRemainder) {
        TreeMap<Double, PriceLevel> opposite = (incoming.side == OrderSide.BUY) ? orderBook.asks : orderBook.bids;

        boolean isLimitType = (incoming.type == OrderType.LIMIT
                || incoming.type == OrderType.LIMIT_IOC
                || incoming.type == OrderType.LIMIT_FOK);

        boolean tradeExecuted = false;

        while (incoming.quantity > 0 && !opposite.isEmpty()) {
            Map.Entry<Double, PriceLevel> bestEntry = opposite.firstEntry();
            double bestPrice = bestEntry.getKey();
            PriceLevel level = bestEntry.getValue();

            if (isLimitType) {
                boolean priceOk = (incoming.side == OrderSide.BUY)
                        ? bestPrice <= incoming.price
                        : bestPrice >= incoming.price;
                if (!priceOk) break;
            }

            List<Order> skipped = new ArrayList<>();
            boolean matchedAtLevel = false;

            while (!level.isEmpty() && incoming.quantity > 0) {
                Order resting = level.peekOrder();

                // Self-trade prevention
                if (resting.traderId.equals(incoming.traderId)) {
                    skipped.add(level.popOrder());
                    continue;
                }

                int tradeQty = Math.min(incoming.quantity, resting.quantity);

                incoming.quantity -= tradeQty;
                resting.quantity -= tradeQty;
                level.reduceQuantity(tradeQty);

                incoming.status = (incoming.quantity == 0) ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED;
                resting.status = (resting.quantity == 0) ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED;

                Order buy = (incoming.side == OrderSide.BUY) ? incoming : resting;
                Order sell = (incoming.side == OrderSide.SELL) ? incoming : resting;

                TradeRecord trade = new TradeRecord(buy, sell, bestPrice, tradeQty, stock.ticker, exchange.FEE_RATE);
                tradeBook.recordTrade(trade);

                UserProfile buyerProfile = um.getUser(buy.traderId);
                UserProfile sellerProfile = um.getUser(sell.traderId);

                double tradeValue = bestPrice * tradeQty;
                double fee = tradeValue * exchange.FEE_RATE;

                exchange.collectFee(fee * 2);
                buyerProfile.settleBuy(stock.ticker, tradeQty, bestPrice, fee);
                sellerProfile.settleSell(stock.ticker, tradeQty, bestPrice, fee);
                stock.updateMarketStats(bestPrice, tradeQty);

                tradeExecuted = true;
                matchedAtLevel = true;

                notifyTradeExecuted(trade);
                notifyOrderUpdated(resting);

                if (resting.quantity == 0) {
                    level.popOrder();
                } else {
                    break;
                }
            }

            for (Order s : skipped) {
                level.addOrder(s);
            }

            if (level.isEmpty()) {
                opposite.remove(bestPrice);
            }

            if (!matchedAtLevel && !skipped.isEmpty()) {
                break;
            }
        }

        if (addRemainder && incoming.quantity > 0) {
            orderBook.addOrder(incoming);
        }

        if (tradeExecuted) {
            stock.circuitBreaker.check(stock, orderLog);
            if (stock.circuitBreaker.tripped) {
                notifyCircuitBreaker(stock);
            }
            if (!stock.tradingPaused) {
                checkStopTriggers(stock);
            }
        }
    }

    private void checkStopTriggers(Stock stock) {
        List<Order> triggered = stopOrderBook.getTriggeredOrders(stock.lastTradedPrice);

        for (Order stopOrder : triggered) {
            System.out.println("Stop triggered: " + stopOrder.id + " at price: " + stock.lastTradedPrice);
            Order marketOrder = new Order(
                    stopOrder.id + "_MKT",
                    stopOrder.side,
                    0,
                    stopOrder.quantity,
                    OrderType.MARKET,
                    stopOrder.traderId,
                    stopOrder.ticker
            );
            process(marketOrder, stock);
        }
    }

    private void notifyTradeExecuted(TradeRecord t) {
        for (EngineEventListener l : listeners) {
            try { l.onTradeExecuted(t); } catch (Exception ignored) {}
        }
    }

    private void notifyOrderUpdated(Order o) {
        for (EngineEventListener l : listeners) {
            try { l.onOrderUpdated(o); } catch (Exception ignored) {}
        }
    }

    private void notifyBookChanged(String ticker) {
        for (EngineEventListener l : listeners) {
            try { l.onOrderBookChanged(ticker); } catch (Exception ignored) {}
        }
    }

    private void notifyCircuitBreaker(Stock s) {
        for (EngineEventListener l : listeners) {
            try { l.onCircuitBreakerTripped(s); } catch (Exception ignored) {}
        }
    }
}
