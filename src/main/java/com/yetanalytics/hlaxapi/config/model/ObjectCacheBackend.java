package com.yetanalytics.hlaxapi.config.model;

import java.util.Locale;

public enum ObjectCacheBackend {
    SQLITE,
    POSTGRESQL;

    public static ObjectCacheBackend fromString(String value) {
        if (value == null || value.isBlank()) {
            return SQLITE;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "sqlite" -> SQLITE;
            case "postgresql" -> POSTGRESQL;
            default -> throw new IllegalArgumentException("Unsupported object cache backend: " + value);
        };
    }
}
