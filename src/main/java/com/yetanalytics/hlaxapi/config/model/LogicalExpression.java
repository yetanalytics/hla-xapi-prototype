package com.yetanalytics.hlaxapi.config.model;

import java.util.List;

public class LogicalExpression implements Expression {
    public final com.yetanalytics.hlaxapi.config.model.LogicalOperator operator;
    public final List<Expression> operands;

    public LogicalExpression(com.yetanalytics.hlaxapi.config.model.LogicalOperator operator, List<Expression> operands) {
        this.operator = operator;
        this.operands = operands;
    }

    @Override
    public String toString() {
        return String.format("Logical(%s: %s)", operator, operands);
    }
}
