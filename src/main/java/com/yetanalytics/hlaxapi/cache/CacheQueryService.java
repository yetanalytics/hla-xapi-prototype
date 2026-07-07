package com.yetanalytics.hlaxapi.cache;

import com.yetanalytics.hlaxapi.config.model.ComparisonOperator;
import com.yetanalytics.hlaxapi.config.model.Criterion;
import com.yetanalytics.hlaxapi.config.model.Expression;
import com.yetanalytics.hlaxapi.config.model.LogicalExpression;
import com.yetanalytics.hlaxapi.config.model.LogicalOperator;
import com.yetanalytics.hlaxapi.config.model.Target;
import com.yetanalytics.hlaxapi.config.model.ThisExpression;
import com.yetanalytics.hlaxapi.config.model.ValueExpression;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class CacheQueryService {

    private final HlaObjectCache cache;

    public CacheQueryService(HlaObjectCache cache) {
        this.cache = Objects.requireNonNull(cache, "cache");
    }

    public Optional<Object> findFirstValue(String className, Target target, Expression criteria) {
        return findValues(className, target, criteria).stream().findFirst();
    }

    public Optional<CachedObject> findFirstObject(String className, Expression criteria) {
        for (CachedObject object : cache.currentObjects(className)) {
            if (matches(object, criteria)) {
                return Optional.of(object);
            }
        }
        return Optional.empty();
    }

    public ValueResolution findFirstResolution(String className, Target target, Expression criteria) {
        Optional<CachedObject> object = findFirstObject(className, criteria);
        if (object.isEmpty()) {
            return ValueResolution.missingObject();
        }
        return findValueResolution(object.orElseThrow(), target);
    }

    public Optional<Object> findValue(CachedObject object, Target target) {
        ValueResolution resolution = findValueResolution(object, target);
        if (!resolution.present() || resolution.value() == null) {
            return Optional.empty();
        }
        return Optional.of(resolution.value());
    }

    public ValueResolution findValueResolution(CachedObject object, Target target) {
        if (object == null) {
            return ValueResolution.missingObject();
        }
        return resolveTarget(object, target);
    }

    public List<Object> findValues(String className, Target target, Expression criteria) {
        String pathKey = FomCatalog.targetPath(target == null ? null : target.parts);
        if (pathKey == null) {
            return List.of();
        }

        List<Object> values = new ArrayList<>();
        for (CachedObject object : cache.currentObjects(className)) {
            if (matches(object, criteria)) {
                cache.findCurrentValue(object.id(), pathKey)
                        .map(CachedValue::value)
                        .ifPresent(values::add);
            }
        }
        return values;
    }

    public boolean matches(CachedObject object, Expression expression) {
        if (expression == null) {
            return true;
        }
        if (expression instanceof Criterion criterion) {
            return evaluateCriterion(object, criterion);
        }
        if (expression instanceof LogicalExpression logical) {
            if (logical.operator == LogicalOperator.OR) {
                return logical.operands.stream().anyMatch(operand -> matches(object, operand));
            }
            return logical.operands.stream().allMatch(operand -> matches(object, operand));
        }
        Object value = resolveExpression(object, expression);
        return Boolean.TRUE.equals(value);
    }

    private boolean evaluateCriterion(CachedObject object, Criterion criterion) {
        Object left = resolveExpression(object, criterion.left);
        Object right = resolveExpression(object, criterion.right);
        ComparisonOperator operator = criterion.operator;
        if (operator == null) {
            return false;
        }
        return compare(left, right, operator);
    }

    private Object resolveExpression(CachedObject object, Expression expression) {
        if (expression == null) {
            return null;
        }
        if (expression instanceof Target target) {
            ValueResolution resolution = resolveTarget(object, target);
            return resolution.present() ? resolution.value() : null;
        }
        if (expression instanceof ValueExpression valueExpression) {
            return valueExpression.value;
        }
        if (expression instanceof ThisExpression) {
            return null;
        }
        if (expression instanceof Criterion criterion) {
            return evaluateCriterion(object, criterion);
        }
        if (expression instanceof LogicalExpression logical) {
            return matches(object, logical);
        }
        return null;
    }

    private ValueResolution resolveTarget(CachedObject object, Target target) {
        String pathKey = FomCatalog.targetPath(target.parts);
        if (pathKey == null) {
            return ValueResolution.missingValue();
        }
        return cache.findCurrentValue(object.id(), pathKey)
                .map(value -> ValueResolution.present(value.value()))
                .orElseGet(ValueResolution::missingValue);
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
