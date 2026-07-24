package com.yetanalytics.hlaxapi.config.model;

/**
 * Binary comparison node (left op right). Left and right are Expressions.
 */
public final class Criterion implements Expression {
    public final Expression left; // Target or nested Expression
    public final ComparisonOperator operator;
    public final Expression right; // ValueExpression, Target, or nested Expression

    public Criterion(Expression left, ComparisonOperator operator, Expression right) {
        this.left = left;
        this.operator = operator;
        this.right = right;
    }

    @Override
    public String toString() {
        return String.format("Criterion{%s %s %s}", left, operator, right);
    }
}
