package com.engine.model;

public class Position {
    public int shares = 0;
    public double avgCost = 0.0;

    public Position() {}

    public Position(int shares, double avgCost) {
        this.shares = shares;
        this.avgCost = avgCost;
    }

    @Override
    public String toString() {
        return String.format("%d shares @ $%.2f", shares, avgCost);
    }
}
