package com.yetanalytics.hlaxapi.config;

import com.yetanalytics.hlaxapi.config.model.Target;
import com.yetanalytics.hlaxapi.config.model.ComparisonOperator;
import com.yetanalytics.hlaxapi.config.model.Criterion;
import com.yetanalytics.hlaxapi.config.model.Expression;
import com.yetanalytics.hlaxapi.config.model.LogicalExpression;
import com.yetanalytics.hlaxapi.config.model.LogicalOperator;
import com.yetanalytics.hlaxapi.config.model.InjectionType;
import com.yetanalytics.hlaxapi.config.model.ValueExpression;
import com.yetanalytics.hlaxapi.config.model.TriggerExpression;

import java.util.ArrayList;
import java.util.List;

/**
 * Convert raw parsed criteria/targets into typed model objects.
 */
public class ConfigConverter {

    public static Target toTarget(Object raw) {
        if (raw == null) return null;
        if (raw instanceof List) {
            List<?> l = (List<?>) raw;
            List<Object> parts = new ArrayList<>();
            for (Object o : l) parts.add(o);
            return new Target(parts);
        }
        return null;
    }

    public static Object toCriterionOrValue(Object raw) {
        if (raw == null) return null;
        if (raw instanceof List) {
            List<?> l = (List<?>) raw;
            // If this is an injection-style array starting with "trigger", treat as TriggerExpression
            if (!l.isEmpty()
                    && l.get(0) instanceof String token
                    && InjectionType.fromString(token) == InjectionType.TRIGGER) {
                // expected form: ["trigger", targetArray]
                Target t = null;
                if (l.size() >= 2) t = toTarget(l.get(1));
                return new TriggerExpression(t);
            }
            // binary comparison (3 elements, middle is a string, and not a logical operator)
            if (l.size() == 3 && l.get(1) instanceof String && LogicalOperator.fromString((String) l.get(1)) == null) {
                Expression left = toExpression(l.get(0));
                String op = (String) l.get(1);
                Expression right = toExpression(l.get(2));
                ComparisonOperator cop = ComparisonOperator.fromString(op);
                return new Criterion(left, cop, right);
            }

            // logical expression like [expr, "or", expr, "and", expr]
            // normalize into operator and operands
            List<Expression> operands = new ArrayList<>();
            LogicalOperator operator = null;
            for (Object el : l) {
                if (el instanceof String) {
                    String s = ((String) el).toLowerCase();
                    LogicalOperator lo = LogicalOperator.fromString(s);
                    if (lo != null) {
                        operator = lo;
                        continue;
                    }
                }
                operands.add(toExpression(el));
            }
            if (operator != null && !operands.isEmpty()) {
                return new LogicalExpression(operator, operands);
            }

            // try target
            Target t = toTarget(raw);
            if (t != null) return t;
            // fallback: return raw
            return raw;
        }
        // primitive -> ValueExpression
        return new ValueExpression(raw);
    }

    public static Expression toExpression(Object raw) {
        Object o = toCriterionOrValue(raw);
        if (o instanceof Expression) return (Expression) o;
        // wrap primitives
        return new ValueExpression(o);
    }

    /**
     * Try to convert raw parsed structure into a Criterion (or return null)
     */
    public static Criterion toCriterion(Object raw) {
        Object out = toCriterionOrValue(raw);
        if (out instanceof Criterion) return (Criterion) out;
        return null;
    }
}
