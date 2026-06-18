package com.yetanalytics.hlaxapi.config.model;

public enum InjectionType {
    THIS("this"), QUERY("query");

    public final String token;

    InjectionType(String token) {
        this.token = token;
    }

    @Override
    public String toString() {
        return token;
    }

    public static InjectionType fromString(String s) {
        if (s == null) {
            return null;
        }
        switch (s.trim().toLowerCase()) {
            case "this":
                return THIS;
            case "query":
                return QUERY;
            default:
                return null;
        }
    }
}
