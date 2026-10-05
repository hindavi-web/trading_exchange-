package com.engine.core;

import com.engine.model.Enums.OrderSide;
import com.engine.model.Enums.OrderType;
import com.engine.model.Order;
import com.engine.model.Stock;
import com.engine.model.UserProfile;

public class OrderValidator {
    private static final double EPSILON = 1e-9;

    public static String validate(Order order, UserProfileManager um, Exchange ex) {
        if (order == null) {
            return "Order is null.";
        }

        if (!um.userExists(order.traderId)) {
            return "Invalid trader: " + order.traderId;
        }

        if (!ex.isValidTicker(order.ticker)) {
            return "Invalid ticker: " + order.ticker;
        }

        Stock stock = ex.getStock(order.ticker);
        if (stock.tradingPaused && order.type != OrderType.STOP_LOSS && order.type != OrderType.STOP_BUY) {
            return "Trading paused for: " + order.ticker;
        }

        if (order.quantity <= 0) {
            return "Quantity must be > 0.";
        }

        boolean needsPrice = (order.type == OrderType.LIMIT
                || order.type == OrderType.LIMIT_IOC
                || order.type == OrderType.LIMIT_FOK
                || order.type == OrderType.STOP_LOSS
                || order.type == OrderType.STOP_BUY);

        if (needsPrice) {
            if (order.price <= 0) {
                return "Price must be > 0.";
            }

            double rem = order.price % stock.tickSize;
            if (rem > EPSILON && Math.abs(rem - stock.tickSize) > EPSILON) {
                return "Price violates tick size: " + stock.tickSize;
            }
        }

        UserProfile user = um.getUser(order.traderId);

        if (order.side == OrderSide.BUY && needsPrice) {
            double required = (order.price * order.quantity) * (1 + ex.FEE_RATE);
            if (user.balance < required) {
                return String.format("Insufficient balance. Need: $%.2f, Have: $%.2f", required, user.balance);
            }
        }

        if (order.side == OrderSide.SELL) {
            int shares = user.getShares(order.ticker);
            if (shares < order.quantity) {
                return String.format("Insufficient shares. Need: %d, Have: %d", order.quantity, shares);
            }
        }

        return "VALID";
    }
}
