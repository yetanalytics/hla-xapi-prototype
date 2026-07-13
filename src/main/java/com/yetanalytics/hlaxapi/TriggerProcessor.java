package com.yetanalytics.hlaxapi;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.yetanalytics.hlaxapi.cache.CachedObject;
import com.yetanalytics.hlaxapi.cache.ValueResolution;
import com.yetanalytics.hlaxapi.config.model.Expression;
import com.yetanalytics.hlaxapi.config.model.ObjectLookup;
import com.yetanalytics.hlaxapi.config.model.StatementTrigger;
import com.yetanalytics.hlaxapi.config.model.Target;
import com.yetanalytics.hlaxapi.config.model.ThisExpression;
import com.yetanalytics.hlaxapi.criteria.CriteriaEvaluator;
import com.yetanalytics.hlaxapi.injection.InjectionContext;
import com.yetanalytics.hlaxapi.injection.StatementInjectionParser;
import com.yetanalytics.hlaxapi.injection.StatementInjectionParser.InjectionOptions;
import com.yetanalytics.hlaxapi.injection.StatementInjectionParser.InlineInjection;
import com.yetanalytics.hlaxapi.injection.StatementInjectionParser.LookupInjection;
import com.yetanalytics.hlaxapi.injection.StatementInjectionParser.ParseResult;
import com.yetanalytics.hlaxapi.injection.StatementInjectionParser.QueryInjection;
import com.yetanalytics.hlaxapi.injection.StatementInjectionParser.StatementInjection;
import com.yetanalytics.hlaxapi.injection.StatementInjectionParser.ThisInjection;

@Component
public class TriggerProcessor {

    private static final Logger logger = LogManager.getLogger(TriggerProcessor.class);

    private final CriteriaEvaluator criteriaEvaluator = new CriteriaEvaluator();

    @Autowired
    private InjectionHandler injectionHandler;

    public TriggerProcessor() {
    }

    // For tests and non-Spring code, allow injection of a custom InjectionHandler
    public TriggerProcessor(InjectionHandler injectionHandler) {
        this.injectionHandler = injectionHandler;
    }

    public String processTrigger(StatementTrigger trigger, InjectionContext context) {
        if (trigger == null || trigger.statement == null) {
            return null;
        }
        ObjectMapper mapper = new ObjectMapper();
        try {
            if (!criteriaEvaluator.matches(
                    trigger.criteria,
                    expression -> resolveCriteriaExpression(expression, context))) {
                logger.trace("Skipping trigger {}.{} because criteria did not match", trigger.type, trigger.clazz);
                return null;
            }
            JsonNode stmtNode = mapper.readTree(trigger.statement);
            Map<String, CachedObject> lookupObjects = resolveLookups(trigger, context);

            JsonNode processed = processNode(stmtNode, context, mapper, lookupObjects);

            String output = mapper.writeValueAsString(processed);
            logger.trace("Processed statement output: {}", output);
            return output;
        } catch (RequiredInjectionException e) {
            logger.error("Could not process trigger {}.{}: {}", trigger.type, trigger.clazz, e.getMessage());
            return null;
        } catch (Exception e) {
            logger.debug("Could not process statement for trigger", e);
            return null;
        }
    }

    private Object resolveCriteriaExpression(Expression expression, InjectionContext context) {
        Target target = null;
        if (expression instanceof Target targetExpression) {
            target = targetExpression;
        } else if (expression instanceof ThisExpression thisExpression) {
            target = thisExpression.target;
        }
        if (target == null || context == null) {
            return null;
        }
        return injectionHandler.handleThis(target, context);
    }

    private Map<String, CachedObject> resolveLookups(StatementTrigger trigger, InjectionContext context) {
        Map<String, CachedObject> lookupObjects = new LinkedHashMap<>();
        if (trigger.lookups == null || trigger.lookups.isEmpty()) {
            return lookupObjects;
        }
        for (Map.Entry<String, ObjectLookup> entry : trigger.lookups.entrySet()) {
            injectionHandler.resolveLookup(entry.getValue(), context)
                    .ifPresent(object -> lookupObjects.put(entry.getKey(), object));
        }
        return lookupObjects;
    }

    private JsonNode processNode(
            JsonNode node,
            InjectionContext context,
            ObjectMapper mapper,
            Map<String, CachedObject> lookupObjects) {
        if (node == null || node.isNull())
            return node;

        try {
            if (node.isObject()) {
                // no injection possible, just process children
                ObjectNode out = mapper.createObjectNode();
                node.fieldNames().forEachRemaining(field -> {
                    JsonNode child = node.get(field);
                    JsonNode processedChild = processNode(child, context, mapper, lookupObjects);
                    out.set(field, processedChild);
                });
                return out;
            }

            if (node.isArray()) {
                ParseResult parsed = StatementInjectionParser.parse(node);
                if (parsed.recognized()) {
                    return parsed.valid()
                            ? handleInjection(parsed.injection(), context, mapper, false, lookupObjects)
                            : NullNode.instance;
                }
                ArrayNode out = mapper.createArrayNode();
                for (JsonNode el : node) {
                    out.add(processNode(el, context, mapper, lookupObjects));
                }
                return out;
            }

            if (node.isTextual()) {
                String txt = node.asText();
                List<InlineInjection> injections = StatementInjectionParser.findInline(txt);
                if (injections.isEmpty()) {
                    return node;
                }

                StringBuilder rendered = new StringBuilder();
                int cursor = 0;
                for (InlineInjection inline : injections) {
                    rendered.append(txt, cursor, inline.start());
                    try {
                        JsonNode repNode = null;
                        if (inline.result().valid()) {
                            repNode = handleInjection(
                                    inline.result().injection(),
                                    context,
                                    mapper,
                                    true,
                                    lookupObjects);
                        } else if (inline.result().recognized()) {
                            repNode = NullNode.instance;
                        }
                        if (repNode == null) {
                            rendered.append(inline.source());
                        } else {
                            String replacementText = repNode.isValueNode()
                                    ? repNode.asText()
                                    : mapper.writeValueAsString(repNode);
                            rendered.append(replacementText);
                        }
                    } catch (RequiredInjectionException e) {
                        throw e;
                    } catch (Exception e) {
                        rendered.append(inline.source());
                    }
                    cursor = inline.end();
                }
                rendered.append(txt, cursor, txt.length());
                return TextNode.valueOf(rendered.toString());
            }

            return node;
        } catch (RequiredInjectionException e) {
            throw e;
        } catch (Exception e) {
            logger.debug("Error processing node", e);
            return node;
        }
    }

    private JsonNode handleInjection(
            StatementInjection injection,
            InjectionContext context,
            ObjectMapper mapper,
            Boolean embedded,
            Map<String, CachedObject> lookupObjects) {
        try {
            if (injection instanceof ThisInjection thisInjection) {
                return renderResolution(
                        injectionHandler.handleThisResolution(thisInjection.target(), context),
                        thisInjection.options(),
                        injectionDescription(thisInjection, null),
                        embedded,
                        mapper);
            } else if (injection instanceof QueryInjection queryInjection) {
                return renderResolution(
                        injectionHandler.handleQueryResolution(
                                queryInjection.className(),
                                queryInjection.target(),
                                queryInjection.criteria(),
                                context),
                        queryInjection.options(),
                        injectionDescription(queryInjection, queryInjection.className()),
                        embedded,
                        mapper);
            } else if (injection instanceof LookupInjection lookupInjection) {
                return renderResolution(
                        injectionHandler.handleLookupResolution(
                                lookupObjects.get(lookupInjection.alias()),
                                lookupInjection.target()),
                        lookupInjection.options(),
                        injectionDescription(lookupInjection, lookupInjection.alias()),
                        embedded,
                        mapper);
            }
        } catch (RequiredInjectionException e) {
            throw e;
        } catch (Exception e) {
            logger.debug("Error handling statement injection", e);
        }
        return NullNode.instance;
    }

    /**
     * Embedded injections are always rendered as text within the containing string.
     * Whole-node injections preserve the replacement's JSON shape.
     *
     * @param replacement
     * @param embedded
     * @param mapper
     * @return actual string to put in result
     */
    private JsonNode render(Object replacement, Boolean embedded, ObjectMapper mapper) {
        if (Boolean.TRUE.equals(embedded)) {
            return TextNode.valueOf(replacement.toString());
        }
        return mapper.valueToTree(replacement);
    }

    private JsonNode renderResolution(
            ValueResolution resolution,
            InjectionOptions options,
            String description,
            Boolean embedded,
            ObjectMapper mapper) {
        if (!resolution.present()) {
            if (!options.required()) {
                return NullNode.instance;
            }
            throw new RequiredInjectionException(description + " failed: " + resolution.status());
        }
        Object replacement = resolution.value();
        if (replacement == null) {
            if (options.required() && !options.nullable()) {
                throw new RequiredInjectionException(description + " failed: unexpected null value");
            }
            return NullNode.instance;
        }
        return render(replacement, embedded, mapper);
    }

    private String injectionDescription(StatementInjection injection, String scope) {
        StringBuilder description = new StringBuilder(injection.type().toString());
        if (scope != null && !scope.isBlank()) {
            description.append("(").append(scope).append(")");
        }
        description.append(" target ");
        Target target = injection.target();
        description.append(target == null ? "<null>" : target.parts);
        return description.toString();
    }

    private static class RequiredInjectionException extends RuntimeException {
        RequiredInjectionException(String message) {
            super(message);
        }
    }

}
