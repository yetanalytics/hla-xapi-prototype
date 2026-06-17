package com.yetanalytics.hlaxapi.config.model;

public class ValueExpression implements Expression {
    public final Object value;

    public ValueExpression(Object value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return String.format("Value(%s)", value);
    }
}
