package com.yetanalytics.hlaxapi.config;

import com.yetanalytics.hlaxapi.config.model.Criterion;
import com.yetanalytics.hlaxapi.config.model.Expression;
import com.yetanalytics.hlaxapi.config.model.LogicalExpression;
import com.yetanalytics.hlaxapi.config.model.LookupExpression;
import com.yetanalytics.hlaxapi.config.model.ObjectLookup;
import com.yetanalytics.hlaxapi.config.model.QueryExpression;
import com.yetanalytics.hlaxapi.config.model.Target;
import com.yetanalytics.hlaxapi.config.model.TriggerExpression;
import com.yetanalytics.hlaxapi.config.model.ValueExpression;
import java.util.Map;

/** Enforces the value sources permitted by each criteria evaluation context. */
public final class CriteriaExpressionValidator {

    private enum Context {
        TRIGGER,
        CACHE_FILTER
    }

    private CriteriaExpressionValidator() {
    }

    public static void validateTrigger(Expression criteria, Map<String, ObjectLookup> lookupDefinitions) {
        Map<String, ObjectLookup> definitions = lookupDefinitions == null ? Map.of() : lookupDefinitions;
        visit(criteria, Context.TRIGGER, definitions, "criteria");
    }

    public static void validateCacheFilter(Expression criteria) {
        visit(criteria, Context.CACHE_FILTER, Map.of(), "criteria");
    }

    private static void visit(
            Expression expression,
            Context context,
            Map<String, ObjectLookup> definitions,
            String location) {
        if (expression == null || expression instanceof ValueExpression) {
            return;
        }
        if (expression instanceof Criterion criterion) {
            visit(criterion.left, context, definitions, location + ".left");
            visit(criterion.right, context, definitions, location + ".right");
            return;
        }
        if (expression instanceof LogicalExpression logical) {
            for (int index = 0; index < logical.operands.size(); index++) {
                visit(logical.operands.get(index), context, definitions, location + "[" + index + "]");
            }
            return;
        }
        if (expression instanceof TriggerExpression) {
            return;
        }
        if (context == Context.CACHE_FILTER && expression instanceof Target) {
            return;
        }
        if (context == Context.TRIGGER && expression instanceof QueryExpression query) {
            visit(query.criteria, Context.CACHE_FILTER, definitions, location + ".queryFilter");
            return;
        }
        if (context == Context.TRIGGER && expression instanceof LookupExpression lookup) {
            ObjectLookup definition = definitions.get(lookup.alias);
            if (definition == null) {
                throw new IllegalArgumentException(
                        location + " references unknown lookup alias '" + lookup.alias + "'");
            }
            if (definition.clazz == null || definition.clazz.isBlank()) {
                throw new IllegalArgumentException(
                        location + " references lookup alias '" + lookup.alias + "' without a class");
            }
            return;
        }
        if (context == Context.TRIGGER && expression instanceof Target target) {
            throw new IllegalArgumentException(
                    location + " contains bare target " + target.parts
                            + "; use [\"trigger\", [...]] for incoming event values");
        }
        throw new IllegalArgumentException(
                location + " contains unsupported " + expression.getClass().getSimpleName());
    }
}
