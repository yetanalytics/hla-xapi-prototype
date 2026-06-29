package com.yetanalytics.hlaxapi.cache;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yetanalytics.hlaxapi.config.ConfigConverter;
import com.yetanalytics.hlaxapi.config.model.Criterion;
import com.yetanalytics.hlaxapi.config.model.Expression;
import com.yetanalytics.hlaxapi.config.model.LogicalExpression;
import com.yetanalytics.hlaxapi.config.model.StatementTrigger;
import com.yetanalytics.hlaxapi.config.model.Target;
import com.yetanalytics.hlaxapi.config.model.ValueExpression;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class QueryReferenceCollector {

    private static final Pattern INLINE_PLACEHOLDER = Pattern.compile("<<(.+?)>>", Pattern.DOTALL);
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
            try {
                collectFromNode(MAPPER.readTree(trigger.statement), references);
            } catch (IOException ignored) {
                // Bad statement JSON is handled by TriggerProcessor at runtime.
            }
        }
        return copyReferences(references);
    }

    private static void collectFromNode(JsonNode node, Map<String, Set<String>> references) throws IOException {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            for (JsonNode child : node) {
                collectFromNode(child, references);
            }
            return;
        }
        if (node.isArray()) {
            if (isQueryInjection(node)) {
                collectQuery(node, references);
                return;
            }
            for (JsonNode child : node) {
                collectFromNode(child, references);
            }
            return;
        }
        if (node.isTextual()) {
            Matcher matcher = INLINE_PLACEHOLDER.matcher(node.asText());
            while (matcher.find()) {
                JsonNode inner = MAPPER.readTree(matcher.group(1));
                if (isQueryInjection(inner)) {
                    collectQuery(inner, references);
                }
            }
        }
    }

    private static boolean isQueryInjection(JsonNode node) {
        return node != null
                && node.isArray()
                && node.size() >= 4
                && node.get(0).isTextual()
                && "query".equalsIgnoreCase(node.get(0).asText());
    }

    private static void collectQuery(JsonNode queryNode, Map<String, Set<String>> references) {
        String className = queryNode.get(1).asText(null);
        if (className == null || className.isBlank()) {
            return;
        }

        Target target = ConfigConverter.toTarget(MAPPER.convertValue(queryNode.get(2), Object.class));
        addTarget(references, className, target);

        Object criteriaRaw = MAPPER.convertValue(queryNode.get(3), Object.class);
        collectCriteriaTargets(references, className, ConfigConverter.toExpression(criteriaRaw));
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
