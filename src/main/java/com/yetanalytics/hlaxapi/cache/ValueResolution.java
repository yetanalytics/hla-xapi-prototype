package com.yetanalytics.hlaxapi.cache;

public final class ValueResolution {

    public enum Status {
        PRESENT,
        MISSING_OBJECT,
        MISSING_VALUE
    }

    private final Status status;
    private final Object value;

    private ValueResolution(Status status, Object value) {
        this.status = status;
        this.value = value;
    }

    public static ValueResolution present(Object value) {
        return new ValueResolution(Status.PRESENT, value);
    }

    public static ValueResolution missingObject() {
        return new ValueResolution(Status.MISSING_OBJECT, null);
    }

    public static ValueResolution missingValue() {
        return new ValueResolution(Status.MISSING_VALUE, null);
    }

    public Status status() {
        return status;
    }

    public Object value() {
        return value;
    }

    public boolean present() {
        return status == Status.PRESENT;
    }
}
