package com.yetanalytics.hlaxapi.criteria;

import com.yetanalytics.hlaxapi.config.model.ComparisonOperator;
import com.yetanalytics.hlaxapi.config.model.Criterion;
import com.yetanalytics.hlaxapi.config.model.Expression;
import com.yetanalytics.hlaxapi.config.model.LogicalExpression;
import com.yetanalytics.hlaxapi.config.model.LogicalOperator;
import com.yetanalytics.hlaxapi.config.model.ValueExpression;
import java.util.Objects;

/** Evaluates criteria expressions independently of their runtime value source. */
public final class CriteriaEvaluator {

    public boolean matches(Expression expression, ExpressionResolver resolver) {
        Objects.requireNonNull(resolver, "resolver");
        if (expression == null) {
            return true;
        }
        if (expression instanceof Criterion criterion) {
            return evaluateCriterion(criterion, resolver);
        }
        if (expression instanceof LogicalExpression logical) {
            if (logical.operator == LogicalOperator.OR) {
                return logical.operands.stream().anyMatch(operand -> matches(operand, resolver));
            }
            return logical.operands.stream().allMatch(operand -> matches(operand, resolver));
        }
        Object value = resolveExpression(expression, resolver);
        return Boolean.TRUE.equals(value);
    }

    private boolean evaluateCriterion(Criterion criterion, ExpressionResolver resolver) {
        Object left = resolveExpression(criterion.left, resolver);
        Object right = resolveExpression(criterion.right, resolver);
        ComparisonOperator operator = criterion.operator;
        if (operator == null) {
            return false;
        }
        return compare(left, right, operator);
    }

    private Object resolveExpression(Expression expression, ExpressionResolver resolver) {
        if (expression == null) {
            return null;
        }
        if (expression instanceof ValueExpression valueExpression) {
            return valueExpression.value;
        }
        if (expression instanceof Criterion criterion) {
            return evaluateCriterion(criterion, resolver);
        }
        if (expression instanceof LogicalExpression logical) {
            return matches(logical, resolver);
        }
        return resolver.resolve(expression);
    }

    private boolean compare(Object left, Object right, ComparisonOperator operator) {
        if (operator == ComparisonOperator.EQ) {
            return valuesEqual(left, right);
        }
        if (operator == ComparisonOperator.NEQ) {
            return !valuesEqual(left, right);
        }
        if (left == null || right == null) {
            return false;
        }
        int result = compareOrder(left, right);
        return switch (operator) {
            case LT -> result < 0;
            case GT -> result > 0;
            case LTE -> result <= 0;
            case GTE -> result >= 0;
            default -> false;
        };
    }

    private boolean valuesEqual(Object left, Object right) {
        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
            return Double.compare(leftNumber.doubleValue(), rightNumber.doubleValue()) == 0;
        }
        return Objects.equals(left, right);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private int compareOrder(Object left, Object right) {
        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
            return Double.compare(leftNumber.doubleValue(), rightNumber.doubleValue());
        }
        if (left instanceof Comparable comparable && left.getClass().isInstance(right)) {
            return comparable.compareTo(right);
        }
        return String.valueOf(left).compareTo(String.valueOf(right));
    }
}
