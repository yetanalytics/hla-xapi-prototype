package com.yetanalytics.hlaxapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.yetanalytics.hlaxapi.cache.ValueResolution;
import com.yetanalytics.hlaxapi.config.model.ComparisonOperator;
import com.yetanalytics.hlaxapi.config.model.Criterion;
import com.yetanalytics.hlaxapi.config.model.Expression;
import com.yetanalytics.hlaxapi.config.model.LogicalExpression;
import com.yetanalytics.hlaxapi.config.model.LogicalOperator;
import com.yetanalytics.hlaxapi.config.model.QueryExpression;
import com.yetanalytics.hlaxapi.config.model.Target;
import com.yetanalytics.hlaxapi.config.model.TriggerExpression;
import com.yetanalytics.hlaxapi.config.model.ValueExpression;
import com.yetanalytics.hlaxapi.injection.InjectionContext;
import com.yetanalytics.hlaxapi.injection.InteractionInjectionContext;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InjectionHandlerTest {

    @Test
    void rewritesTriggerExpressionsInsideCriteriaAndQueryFilters() throws Exception {
        List<String> resolvedTargets = new ArrayList<>();
        InjectionHandler handler = new InjectionHandler() {
            @Override
            public ValueResolution handleTrigger(Target target, InjectionContext context) {
                String name = String.valueOf(target.parts.get(0));
                resolvedTargets.add(name);
                return ValueResolution.present("resolved:" + name);
            }
        };

        TriggerExpression criterionTrigger = new TriggerExpression(target("Score"));
        Criterion criterion = new Criterion(
                criterionTrigger,
                ComparisonOperator.GT,
                new ValueExpression(10));
        Target queryTarget = target("EntityId");
        Target filterTarget = target("OwnerId");
        TriggerExpression filterTrigger = new TriggerExpression(target("DesiredOwnerId"));
        Criterion queryFilter = new Criterion(filterTarget, ComparisonOperator.EQ, filterTrigger);
        QueryExpression query = new QueryExpression("Entity", queryTarget, queryFilter);
        LogicalExpression expression = new LogicalExpression(LogicalOperator.AND, List.of(criterion, query));

        Expression resolved = resolveTriggerExpressions(handler, expression, context());

        LogicalExpression resolvedLogical = assertInstanceOf(LogicalExpression.class, resolved);
        Criterion resolvedCriterion = assertInstanceOf(Criterion.class, resolvedLogical.operands.get(0));
        ValueExpression resolvedCriterionValue = assertInstanceOf(ValueExpression.class, resolvedCriterion.left);
        QueryExpression resolvedQuery = assertInstanceOf(QueryExpression.class, resolvedLogical.operands.get(1));
        Criterion resolvedFilter = assertInstanceOf(Criterion.class, resolvedQuery.criteria);
        ValueExpression resolvedFilterValue = assertInstanceOf(ValueExpression.class, resolvedFilter.right);

        assertEquals("resolved:Score", resolvedCriterionValue.value);
        assertEquals("resolved:DesiredOwnerId", resolvedFilterValue.value);
        assertEquals(List.of("Score", "DesiredOwnerId"), resolvedTargets);
        assertSame(queryTarget, resolvedQuery.target);
        assertSame(filterTarget, resolvedFilter.left);

        assertNotSame(expression, resolvedLogical);
        assertNotSame(criterion, resolvedCriterion);
        assertNotSame(query, resolvedQuery);
        assertNotSame(queryFilter, resolvedFilter);
        assertSame(criterionTrigger, criterion.left);
        assertSame(filterTrigger, queryFilter.right);
        assertSame(expression, resolveTriggerExpressions(handler, expression, null));
    }

    private static Expression resolveTriggerExpressions(
            InjectionHandler handler,
            Expression expression,
            InjectionContext context) throws Exception {
        Method method = InjectionHandler.class.getDeclaredMethod(
                "resolveTriggerExpressions",
                Expression.class,
                InjectionContext.class);
        method.setAccessible(true);
        return (Expression) method.invoke(handler, expression, context);
    }

    private static Target target(String name) {
        return new Target(List.of(name));
    }

    private static InteractionInjectionContext context() {
        return new InteractionInjectionContext("TestInteraction", Map.of());
    }
}
