package com.yetanalytics.hlaxapi.config.model;

/**
 * Represents a 'this' injection inside an expression tree. Holds a Target that
 * specifies the attribute path to extract from the current statement/context.
 */
public class ThisExpression implements Expression {
    public final Target target;

    public ThisExpression(Target target) {
        this.target = target;
    }

    @Override
    public String toString() {
        return "This(" + (target==null?"null":target.toString()) + ")";
    }
}
