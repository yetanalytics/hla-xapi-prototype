package com.yetanalytics.hlaxapi.config.model;

/** Reads a target value from the first cached object matching the query criteria. */
public final class QueryExpression implements Expression {

    public final String clazz;
    public final Target target;
    public final Expression criteria;

    public QueryExpression(String clazz, Target target, Expression criteria) {
        this.clazz = clazz;
        this.target = target;
        this.criteria = criteria;
    }

    @Override
    public String toString() {
        return String.format("Query(%s, %s, %s)", clazz, target, criteria);
    }
}
