package com.yetanalytics.hlaxapi.cache;

public record DecodedAttributeValue(
        String pathKey,
        String dataType,
        String primitiveType,
        Object value,
        byte[] rawBytes,
        boolean leaf) {
}
