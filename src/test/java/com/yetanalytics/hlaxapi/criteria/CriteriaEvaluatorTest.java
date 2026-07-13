package com.yetanalytics.hlaxapi.criteria;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yetanalytics.hlaxapi.config.model.ComparisonOperator;
import com.yetanalytics.hlaxapi.config.model.Criterion;
import com.yetanalytics.hlaxapi.config.model.Expression;
import com.yetanalytics.hlaxapi.config.model.LogicalExpression;
import com.yetanalytics.hlaxapi.config.model.LogicalOperator;
import com.yetanalytics.hlaxapi.config.model.Target;
import com.yetanalytics.hlaxapi.config.model.TriggerExpression;
import com.yetanalytics.hlaxapi.config.model.ValueExpression;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CriteriaEvaluatorTest {

    private final CriteriaEvaluator evaluator = new CriteriaEvaluator();

    @Test
    void matchesNullCriteriaAndBooleanLiterals() {
        ExpressionResolver resolver = expression -> null;

        assertTrue(evaluator.matches(null, resolver));
        assertTrue(evaluator.matches(new ValueExpression(true), resolver));
        assertFalse(evaluator.matches(new ValueExpression(false), resolver));
        assertFalse(evaluator.matches(new ValueExpression("true"), resolver));
    }

    @ParameterizedTest
    @MethodSource("comparisonCases")
    void evaluatesComparisonOperators(Object left, ComparisonOperator operator, Object right, boolean expected) {
        Criterion criterion = new Criterion(new ValueExpression(left), operator, new ValueExpression(right));

        if (expected) {
            assertTrue(evaluator.matches(criterion, expression -> null));
        } else {
            assertFalse(evaluator.matches(criterion, expression -> null));
        }
    }

    static Stream<Arguments> comparisonCases() {
        return Stream.of(
                Arguments.of(5, ComparisonOperator.EQ, 5L, true),
                Arguments.of(5, ComparisonOperator.EQ, 6L, false),
                Arguments.of("rabbit", ComparisonOperator.NEQ, "fox", true),
                Arguments.of("rabbit", ComparisonOperator.NEQ, "rabbit", false),
                Arguments.of(1, ComparisonOperator.LT, 2L, true),
                Arguments.of(2, ComparisonOperator.LT, 1L, false),
                Arguments.of(2.5, ComparisonOperator.GT, 2, true),
                Arguments.of(1, ComparisonOperator.GT, 2, false),
                Arguments.of("a", ComparisonOperator.LTE, "b", true),
                Arguments.of("b", ComparisonOperator.LTE, "a", false),
                Arguments.of("b", ComparisonOperator.GTE, "b", true),
                Arguments.of("a", ComparisonOperator.GTE, "b", false),
                Arguments.of(10, ComparisonOperator.LT, "2", true),
                Arguments.of(null, ComparisonOperator.EQ, null, true),
                Arguments.of(null, ComparisonOperator.NEQ, "value", true),
                Arguments.of(null, ComparisonOperator.LT, 1, false));
    }

    @Test
    void resolvesContextDependentLeafExpressions() {
        Target target = new Target(List.of("EntityId"));
        TriggerExpression triggerExpression = new TriggerExpression(new Target(List.of("PredatorId")));
        Criterion criterion = new Criterion(target, ComparisonOperator.EQ, triggerExpression);

        assertTrue(evaluator.matches(criterion, expression -> {
            if (expression == target || expression == triggerExpression) {
                return "rabbit-one";
            }
            return null;
        }));
    }

    @Test
    void evaluatesNestedLogicalExpressions() {
        Target hunger = new Target(List.of("Hunger"));
        Target xPosition = new Target(List.of("Position", "X"));
        Map<Target, Object> values = Map.of(hunger, 75, xPosition, 12);
        Criterion hungry = new Criterion(hunger, ComparisonOperator.GT, new ValueExpression(50));
        Criterion nearby = new Criterion(xPosition, ComparisonOperator.LT, new ValueExpression(15));
        Criterion missing = new Criterion(xPosition, ComparisonOperator.GT, new ValueExpression(20));
        LogicalExpression nested = new LogicalExpression(
                LogicalOperator.AND,
                List.of(hungry, new LogicalExpression(LogicalOperator.OR, List.of(nearby, missing))));

        assertTrue(evaluator.matches(nested, values::get));
    }

    @Test
    void shortCircuitsLogicalExpressions() {
        Target unresolved = new Target(List.of("Unresolved"));
        AtomicInteger resolutions = new AtomicInteger();
        ExpressionResolver resolver = expression -> {
            resolutions.incrementAndGet();
            return true;
        };

        LogicalExpression orExpression = new LogicalExpression(
                LogicalOperator.OR,
                List.of(new ValueExpression(true), unresolved));
        LogicalExpression andExpression = new LogicalExpression(
                LogicalOperator.AND,
                List.of(new ValueExpression(false), unresolved));

        assertTrue(evaluator.matches(orExpression, resolver));
        assertFalse(evaluator.matches(andExpression, resolver));
        assertEquals(0, resolutions.get());
    }

    @Test
    void rejectsCriterionWithoutComparisonOperator() {
        Criterion criterion = new Criterion(new ValueExpression(1), null, new ValueExpression(1));

        assertFalse(evaluator.matches(criterion, expression -> null));
    }
}
