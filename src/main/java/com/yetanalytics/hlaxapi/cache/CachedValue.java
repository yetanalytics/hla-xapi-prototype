package com.yetanalytics.hlaxapi.cache;

import java.util.Arrays;
import java.util.Objects;

public final class CachedValue {

    private final String valueType;
    private final Object value;
    private final byte[] rawBytes;

    public CachedValue(String valueType, Object value, byte[] rawBytes) {
        this.valueType = Objects.requireNonNull(valueType, "valueType");
        this.value = value;
        this.rawBytes = rawBytes == null ? null : Arrays.copyOf(rawBytes, rawBytes.length);
    }

    public String valueType() {
        return valueType;
    }

    public Object value() {
        return value;
    }

    public byte[] rawBytes() {
        return rawBytes == null ? null : Arrays.copyOf(rawBytes, rawBytes.length);
    }

    public static String valueType(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Boolean) {
            return "bool";
        }
        if (value instanceof Number) {
            return "num";
        }
        if (value instanceof String || value instanceof Character) {
            return "text";
        }
        if (value instanceof byte[]) {
            return "blob";
        }
        return "json";
    }
}
