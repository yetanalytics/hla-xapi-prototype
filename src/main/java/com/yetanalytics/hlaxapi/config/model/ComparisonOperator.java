package com.yetanalytics.hlaxapi.config.model;

public enum ComparisonOperator {
    EQ("="), NEQ("!="), LT("<"), GT(">"), LTE("<="), GTE(">=");

    public final String symbol;

    ComparisonOperator(String symbol) {
        this.symbol = symbol;
    }

    @Override
    public String toString() { return symbol; }

    public static ComparisonOperator fromString(String s) {
        if (s == null) return null;
        switch (s.trim()) {
            case "=": return EQ;
            case "!=": return NEQ;
            case "<": return LT;
            case ">": return GT;
            case "<=": return LTE;
            case ">=": return GTE;
            default:
                // allow textual names
                try {
                    return ComparisonOperator.valueOf(s.trim().toUpperCase());
                } catch (IllegalArgumentException e) {
                    return null;
                }
        }
    }
}
