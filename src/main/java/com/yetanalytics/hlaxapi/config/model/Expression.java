package com.yetanalytics.hlaxapi.config.model;

public sealed interface Expression
        permits Criterion,
                LogicalExpression,
                LookupExpression,
                QueryExpression,
                Target,
                TriggerExpression,
                ValueExpression {
}
