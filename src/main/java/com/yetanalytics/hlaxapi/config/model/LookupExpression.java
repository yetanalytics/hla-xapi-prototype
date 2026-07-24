package com.yetanalytics.hlaxapi.config.model;

/** Reads a target value from a named trigger lookup. */
public final class LookupExpression implements Expression {

    public final String alias;
    public final Target target;

    public LookupExpression(String alias, Target target) {
        this.alias = alias;
        this.target = target;
    }

    @Override
    public String toString() {
        return String.format("Lookup(%s, %s)", alias, target);
    }
}
