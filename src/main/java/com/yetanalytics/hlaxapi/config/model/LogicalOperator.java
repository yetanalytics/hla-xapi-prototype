package com.yetanalytics.hlaxapi.config.model;

public enum LogicalOperator {
    AND("and"), OR("or");

    public final String token;

    LogicalOperator(String token) { this.token = token; }

    @Override
    public String toString() { return token; }

    public static LogicalOperator fromString(String s) {
        if (s == null) return null;
        switch (s.trim().toLowerCase()) {
            case "and": return AND;
            case "or": return OR;
            default: return null;
        }
    }
}
