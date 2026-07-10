package com.yetanalytics.hlaxapi.criteria;

import com.yetanalytics.hlaxapi.config.model.Expression;

/** Resolves context-dependent leaf expressions to runtime values. */
@FunctionalInterface
public interface ExpressionResolver {

    Object resolve(Expression expression);
}
