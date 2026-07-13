package com.yetanalytics.hlaxapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.yetanalytics.hlaxapi.cache.CachedObject;
import com.yetanalytics.hlaxapi.config.model.ComparisonOperator;
import com.yetanalytics.hlaxapi.config.model.Criterion;
import com.yetanalytics.hlaxapi.config.model.Expression;
import com.yetanalytics.hlaxapi.config.model.LogicalExpression;
import com.yetanalytics.hlaxapi.config.model.LogicalOperator;
import com.yetanalytics.hlaxapi.config.model.ObjectLookup;
import com.yetanalytics.hlaxapi.config.model.StatementTrigger;
import com.yetanalytics.hlaxapi.config.model.Target;
import com.yetanalytics.hlaxapi.config.model.ThisExpression;
import com.yetanalytics.hlaxapi.config.model.ValueExpression;
import com.yetanalytics.hlaxapi.injection.InjectionContext;
import com.yetanalytics.hlaxapi.injection.InteractionInjectionContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TriggerProcessorCriteriaTest {

    private static final String STATEMENT = "{\"result\":{\"success\":true}}";

    @Test
    void processesTriggerWithoutCriteria() {
        TriggerProcessor processor = new TriggerProcessor(new CriteriaInjectionHandler(Map.of()));

        assertNotNull(processor.processTrigger(trigger(null), context()));
    }

    @Test
    void processesMatchingNestedCriteriaWithTargetAndThisExpressions() {
        Target score = new Target(List.of("Score"));
        ThisExpression level = new ThisExpression(new Target(List.of("Level")));
        LogicalExpression criteria = new LogicalExpression(
                LogicalOperator.AND,
                List.of(
                        new Criterion(score, ComparisonOperator.GTE, new ValueExpression(10)),
                        new LogicalExpression(
                                LogicalOperator.OR,
                                List.of(
                                        new Criterion(
                                                level,
                                                ComparisonOperator.EQ,
                                                new ValueExpression("advanced")),
                                        new Criterion(
                                                new Target(List.of("Fallback")),
                                                ComparisonOperator.EQ,
                                                new ValueExpression(true))))));
        CriteriaInjectionHandler handler = new CriteriaInjectionHandler(
                Map.of(List.of("Score"), 12, List.of("Level"), "advanced"));

        assertNotNull(new TriggerProcessor(handler).processTrigger(trigger(criteria), context()));
    }

    @Test
    void skipsNonMatchingTriggerBeforeResolvingLookups() {
        CriteriaInjectionHandler handler = new CriteriaInjectionHandler(Map.of(List.of("Score"), 5));
        StatementTrigger trigger = trigger(new Criterion(
                new Target(List.of("Score")),
                ComparisonOperator.GT,
                new ValueExpression(10)));
        trigger.lookups = Map.of("subject", new ObjectLookup());

        assertNull(new TriggerProcessor(handler).processTrigger(trigger, context()));
        assertEquals(0, handler.lookupResolutions.get());
    }

    private StatementTrigger trigger(Expression criteria) {
        StatementTrigger trigger = new StatementTrigger();
        trigger.type = StatementTrigger.Type.INTERACTION;
        trigger.clazz = "TestInteraction";
        trigger.criteria = criteria;
        trigger.statement = STATEMENT;
        return trigger;
    }

    private InteractionInjectionContext context() {
        return new InteractionInjectionContext("TestInteraction", Map.of());
    }

    private static final class CriteriaInjectionHandler extends InjectionHandler {

        private final Map<List<Object>, Object> values;
        private final AtomicInteger lookupResolutions = new AtomicInteger();

        private CriteriaInjectionHandler(Map<List<Object>, Object> values) {
            this.values = values;
        }

        @Override
        public Object handleThis(Target target, InjectionContext context) {
            return values.get(target.parts);
        }

        @Override
        public Optional<CachedObject> resolveLookup(ObjectLookup lookup, InjectionContext context) {
            lookupResolutions.incrementAndGet();
            return Optional.empty();
        }
    }
}
