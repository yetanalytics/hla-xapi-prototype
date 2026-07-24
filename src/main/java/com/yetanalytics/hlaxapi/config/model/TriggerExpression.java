package com.yetanalytics.hlaxapi.config.model;

/**
 * Represents a trigger injection inside an expression tree. Holds a Target that
 * specifies the attribute path to extract from the current statement/context.
 */
public final class TriggerExpression implements Expression {
    public final Target target;

    public TriggerExpression(Target target) {
        this.target = target;
    }

    @Override
    public String toString() {
        return "Trigger(" + (target==null?"null":target.toString()) + ")";
    }
}
