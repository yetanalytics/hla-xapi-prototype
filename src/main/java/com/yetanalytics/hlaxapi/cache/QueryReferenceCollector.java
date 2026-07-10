package com.yetanalytics.hlaxapi.cache;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yetanalytics.hlaxapi.config.model.Criterion;
import com.yetanalytics.hlaxapi.config.model.Expression;
import com.yetanalytics.hlaxapi.config.model.LogicalExpression;
import com.yetanalytics.hlaxapi.config.model.StatementTrigger;
import com.yetanalytics.hlaxapi.config.model.Target;
import com.yetanalytics.hlaxapi.config.model.ValueExpression;
import com.yetanalytics.hlaxapi.injection.StatementInjectionParser;
import com.yetanalytics.hlaxapi.injection.StatementInjectionParser.InlineInjection;
import com.yetanalytics.hlaxapi.injection.StatementInjectionParser.LookupInjection;
import com.yetanalytics.hlaxapi.injection.StatementInjectionParser.ParseResult;
import com.yetanalytics.hlaxapi.injection.StatementInjectionParser.QueryInjection;
import com.yetanalytics.hlaxapi.injection.StatementInjectionParser.StatementInjection;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class QueryReferenceCollector {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private QueryReferenceCollector() {
    }

    public static Map<String, Set<String>> collect(List<StatementTrigger> triggers) {
        Map<String, Set<String>> references = new LinkedHashMap<>();
        if (triggers == null) {
            return references;
        }
        for (StatementTrigger trigger : triggers) {
            if (trigger == null || trigger.statement == null) {
                continue;
            }
            Map<String, String> lookupClasses = collectLookupDefinitions(trigger, references);
            try {
                collectFromNode(MAPPER.readTree(trigger.statement), references, lookupClasses);
            } catch (IOException ignored) {
                // Bad statement JSON is handled by TriggerProcessor at runtime.
            }
        }
        return copyReferences(references);
    }

    private static Map<String, String> collectLookupDefinitions(
            StatementTrigger trigger,
            Map<String, Set<String>> references) {
        Map<String, String> lookupClasses = new LinkedHashMap<>();
        if (trigger.lookups == null) {
            return lookupClasses;
        }
        trigger.lookups.forEach((alias, lookup) -> {
            if (lookup == null || lookup.clazz == null || lookup.clazz.isBlank()) {
                return;
            }
            lookupClasses.put(alias, lookup.clazz);
            collectCriteriaTargets(references, lookup.clazz, lookup.criteria);
        });
        return lookupClasses;
    }

    private static void collectFromNode(
            JsonNode node,
            Map<String, Set<String>> references,
            Map<String, String> lookupClasses) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            for (JsonNode child : node) {
                collectFromNode(child, references, lookupClasses);
            }
            return;
        }
        if (node.isArray()) {
            ParseResult parsed = StatementInjectionParser.parse(node);
            if (parsed.valid()) {
                collectInjection(parsed.injection(), references, lookupClasses);
                return;
            }
            for (JsonNode child : node) {
                collectFromNode(child, references, lookupClasses);
            }
            return;
        }
        if (node.isTextual()) {
            for (InlineInjection inline : StatementInjectionParser.findInline(node.asText())) {
                if (inline.result().valid()) {
                    collectInjection(inline.result().injection(), references, lookupClasses);
                }
            }
        }
    }

    private static void collectInjection(
            StatementInjection injection,
            Map<String, Set<String>> references,
            Map<String, String> lookupClasses) {
        if (injection instanceof QueryInjection queryInjection) {
            collectQuery(queryInjection, references);
        } else if (injection instanceof LookupInjection lookupInjection) {
            collectLookup(lookupInjection, references, lookupClasses);
        }
    }

    private static void collectQuery(QueryInjection query, Map<String, Set<String>> references) {
        String className = query.className();
        if (className == null || className.isBlank()) {
            return;
        }

        addTarget(references, className, query.target());
        collectCriteriaTargets(references, className, query.criteria());
    }

    private static void collectLookup(
            LookupInjection lookup,
            Map<String, Set<String>> references,
            Map<String, String> lookupClasses) {
        String className = lookupClasses.get(lookup.alias());
        if (className == null || className.isBlank()) {
            return;
        }

        addTarget(references, className, lookup.target());
    }

    private static void collectCriteriaTargets(
            Map<String, Set<String>> references,
            String className,
            Expression expression) {
        if (expression instanceof Target target) {
            addTarget(references, className, target);
        } else if (expression instanceof Criterion criterion) {
            collectCriteriaTargets(references, className, criterion.left);
            collectCriteriaTargets(references, className, criterion.right);
        } else if (expression instanceof LogicalExpression logicalExpression) {
            logicalExpression.operands.forEach(operand -> collectCriteriaTargets(references, className, operand));
        } else if (expression instanceof ValueExpression) {
            return;
        }
    }

    private static void addTarget(Map<String, Set<String>> references, String className, Target target) {
        String topLevelAttribute = target == null ? null : FomCatalog.topLevelTargetPart(target.parts);
        if (topLevelAttribute == null || topLevelAttribute.isBlank()) {
            return;
        }
        references.computeIfAbsent(className, ignored -> new LinkedHashSet<>()).add(topLevelAttribute);
    }

    private static Map<String, Set<String>> copyReferences(Map<String, Set<String>> references) {
        Map<String, Set<String>> copy = new LinkedHashMap<>();
        references.forEach((className, attributes) -> copy.put(className, Set.copyOf(attributes)));
        return Map.copyOf(copy);
    }
}
