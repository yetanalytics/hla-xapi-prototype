package com.yetanalytics.hlaxapi;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.yetanalytics.hlaxapi.cache.CachedObject;
import com.yetanalytics.hlaxapi.cache.ValueResolution;
import com.yetanalytics.hlaxapi.config.model.ObjectLookup;
import com.yetanalytics.hlaxapi.config.model.StatementTrigger;
import com.yetanalytics.hlaxapi.config.model.Target;
import com.yetanalytics.hlaxapi.exception.InjectionDatatypeMismatchException;
import com.yetanalytics.hlaxapi.injection.InjectionContext;
import com.yetanalytics.hlaxapi.injection.StatementInjectionParser;
import com.yetanalytics.hlaxapi.injection.StatementInjectionParser.InjectionOptions;
import com.yetanalytics.hlaxapi.injection.StatementInjectionParser.InlineInjection;
import com.yetanalytics.hlaxapi.injection.StatementInjectionParser.LookupInjection;
import com.yetanalytics.hlaxapi.injection.StatementInjectionParser.ParseResult;
import com.yetanalytics.hlaxapi.injection.StatementInjectionParser.QueryInjection;
import com.yetanalytics.hlaxapi.injection.StatementInjectionParser.StatementInjection;
import com.yetanalytics.hlaxapi.injection.StatementInjectionParser.TriggerInjection;

@Component
public class TriggerProcessor {

    private static final Logger logger = LogManager.getLogger(TriggerProcessor.class);

    @Autowired
    private InjectionHandler injectionHandler;

    public TriggerProcessor() {
    }

    // For tests and non-Spring code, allow injection of a custom InjectionHandler
    public TriggerProcessor(InjectionHandler injectionHandler) {
        this.injectionHandler = injectionHandler;
    }

    public record TriggerProcessingResult(String statement, boolean success, Throwable error){}

    public TriggerProcessingResult processTrigger(StatementTrigger trigger, InjectionContext context) {
        if (trigger == null || trigger.statement == null) {
            return null;
        }
        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode stmtNode = mapper.readTree(trigger.statement);
            Map<String, CachedObject> lookupObjects = resolveLookups(trigger, context);

            JsonNode processed = processNode(stmtNode, context, mapper, lookupObjects, List.of());

            String output = mapper.writeValueAsString(processed);
            logger.trace("Processed statement output: {}", output);
            return new TriggerProcessingResult(output, true, null);
        } catch (Exception e) {
            logger.error("Could not process trigger %s.%s: %s", trigger.type, trigger.clazz, e.getMessage(), e);
            return new TriggerProcessingResult(null, false, e);
        }
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
            Map<String, CachedObject> lookupObjects,
            List<Object> statementPath) throws JsonProcessingException {
        if (node == null || node.isNull())
            return node;

        if (node.isObject()) {
            // no injection possible, just process children
            ObjectNode out = mapper.createObjectNode();
            Iterator<String> fields = node.fieldNames();
            while(fields.hasNext()){
                String field = fields.next();
                JsonNode child = node.get(field);
                JsonNode processedChild = processNode(
                        child,
                        context,
                        mapper,
                        lookupObjects,
                        appendPath(statementPath, field));
                out.set(field, processedChild);
            }
            return out;
        }

        if (node.isArray()) {
            ParseResult parsed = StatementInjectionParser.parse(node);
            if (parsed.recognized()) {
                return parsed.valid()
                        ? handleInjection(parsed.injection(), context, mapper, false, lookupObjects, statementPath)
                        : NullNode.instance;
            }
            ArrayNode out = mapper.createArrayNode();
            for (int index = 0; index < node.size(); index++) {
                out.add(processNode(
                        node.get(index),
                        context,
                        mapper,
                        lookupObjects,
                        appendPath(statementPath, index)));
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
                JsonNode repNode = null;
                if (inline.result().valid()) {
                    repNode = handleInjection(
                            inline.result().injection(),
                            context,
                            mapper,
                            true,
                            lookupObjects,
                            statementPath);
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
                cursor = inline.end();
            }
            rendered.append(txt, cursor, txt.length());
            return TextNode.valueOf(rendered.toString());
        }

        return node;
    }

    private JsonNode handleInjection(
            StatementInjection injection,
            InjectionContext context,
            ObjectMapper mapper,
            Boolean embedded,
            Map<String, CachedObject> lookupObjects,
            List<Object> statementPath) {
        List<Object> previousPath = context.getStatementPath();
        context.setStatementPath(statementPath);
        try {
            if (injection instanceof TriggerInjection triggerInjection) {
                return renderResolution(
                        injectionHandler.handleTrigger(triggerInjection.target(), context),
                        triggerInjection.options(),
                        injectionDescription(triggerInjection, null),
                        embedded,
                        mapper);
            } else if (injection instanceof QueryInjection queryInjection) {
                return renderResolution(
                        injectionHandler.handleQuery(
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
                        injectionHandler.handleLookup(
                                lookupObjects.get(lookupInjection.alias()),
                                lookupInjection.target(),
                                context),
                        lookupInjection.options(),
                        injectionDescription(lookupInjection, lookupInjection.alias()),
                        embedded,
                        mapper);
            }
        } catch (RequiredInjectionException | InjectionDatatypeMismatchException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error handling statement injection", e);
        } finally {
            context.setStatementPath(previousPath);
        }
        return NullNode.instance;
    }

    private List<Object> appendPath(List<Object> statementPath, Object part) {
        java.util.ArrayList<Object> childPath = new java.util.ArrayList<>(statementPath);
        childPath.add(part);
        return List.copyOf(childPath);
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
