package com.yetanalytics.hlaxapi;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import com.yetanalytics.hlaxapi.config.ConfigConverter;
import com.yetanalytics.hlaxapi.config.model.Expression;
import com.yetanalytics.hlaxapi.config.model.InjectionType;
import com.yetanalytics.hlaxapi.config.model.ObjectLookup;
import com.yetanalytics.hlaxapi.config.model.StatementTrigger;
import com.yetanalytics.hlaxapi.config.model.Target;
import com.yetanalytics.hlaxapi.injection.InjectionContext;

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

    public String processTrigger(StatementTrigger trigger, InjectionContext context) {
        if (trigger == null || trigger.statement == null) {
            return null;
        }
        ObjectMapper mapper = new ObjectMapper();
        try {
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

    private static final Pattern INLINE_PLACEHOLDER = Pattern.compile("<<(.+?)>>", Pattern.DOTALL);

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
                // check if this array itself is an injection array
                if (node.size() > 0 && node.get(0).isTextual()) {
                    String k = node.get(0).asText();
                    if (InjectionType.fromString(k) != null) {
                        // handle injection array
                        return handleInjectionArray(node, context, mapper, false, lookupObjects);
                    }
                }
                // if not, process each element instead
                ArrayNode out = mapper.createArrayNode();
                for (JsonNode el : node) {
                    out.add(processNode(el, context, mapper, lookupObjects));
                }
                return out;
            }

            if (node.isTextual()) {
                // if it's stringy, we need to check for escaped/encoded injection array(s) and route them through the
                // handlers, then replace that text in the string with the result(s)
                String txt = node.asText();
                Matcher m = INLINE_PLACEHOLDER.matcher(txt);
                if (!m.find()) {
                    return node;
                }

                m.reset();
                StringBuffer sb = new StringBuffer();

                while (m.find()) {
                    String inner = m.group(1);
                    try {
                        JsonNode injNode = mapper.readTree(inner);
                        JsonNode repNode = null;
                        if (injNode.isArray() && injNode.size() > 0 && injNode.get(0).isTextual()
                                && InjectionType.fromString(injNode.get(0).asText()) != null) {
                            repNode = handleInjectionArray(injNode, context, mapper, true, lookupObjects);
                        }
                        if (repNode == null) {
                            m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
                        } else {
                            String replacementText = repNode.isValueNode()
                                    ? repNode.asText()
                                    : mapper.writeValueAsString(repNode);
                            m.appendReplacement(sb, Matcher.quoteReplacement(replacementText));
                        }
                    } catch (RequiredInjectionException e) {
                        throw e;
                    } catch (Exception e) {
                        m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
                    }
                }
                m.appendTail(sb);
                return TextNode.valueOf(sb.toString());
            }

            return node;
        } catch (RequiredInjectionException e) {
            throw e;
        } catch (Exception e) {
            logger.debug("Error processing node", e);
            return node;
        }
    }

    private JsonNode handleInjectionArray(JsonNode injArray, InjectionContext context, ObjectMapper mapper,
            Boolean embedded, Map<String, CachedObject> lookupObjects) {
        try {
            String keyword = injArray.get(0).asText();
            InjectionType iType = InjectionType.fromString(keyword);
            if (iType == InjectionType.THIS && injArray.size() >= 2) {
                Object rawTarget = mapper.convertValue(injArray.get(1), Object.class);
                Target t = ConfigConverter.toTarget(rawTarget);
                return renderResolution(
                        injectionHandler.handleThisResolution(t, context),
                        optionsFor(injArray, 2),
                        injectionDescription(iType, null, t),
                        embedded,
                        mapper);
            } else if (iType == InjectionType.QUERY && injArray.size() >= 4) {
                String clazz = injArray.get(1).asText();
                Object rawTarget = mapper.convertValue(injArray.get(2), Object.class);
                Target attr = ConfigConverter.toTarget(rawTarget);
                Object criteriaRaw = mapper.convertValue(injArray.get(3), Object.class);
                Expression criteriaExpr = ConfigConverter.toExpression(criteriaRaw);
                return renderResolution(
                        injectionHandler.handleQueryResolution(clazz, attr, criteriaExpr, context),
                        optionsFor(injArray, 4),
                        injectionDescription(iType, clazz, attr),
                        embedded,
                        mapper);
            } else if (iType == InjectionType.LOOKUP && injArray.size() >= 3) {
                String alias = injArray.get(1).asText();
                Object rawTarget = mapper.convertValue(injArray.get(2), Object.class);
                Target attr = ConfigConverter.toTarget(rawTarget);
                return renderResolution(
                        injectionHandler.handleLookupResolution(lookupObjects.get(alias), attr),
                        optionsFor(injArray, 3),
                        injectionDescription(iType, alias, attr),
                        embedded,
                        mapper);
            }
        } catch (RequiredInjectionException e) {
            throw e;
        } catch (Exception e) {
            logger.debug("Error handling injection array", e);
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
            throw new RequiredInjectionException(description + " failed: " + resolution.status());
        }
        Object replacement = resolution.value();
        if (replacement == null) {
            if (!options.nullable) {
                throw new RequiredInjectionException(description + " failed: unexpected null value");
            }
            return NullNode.instance;
        }
        return render(replacement, embedded, mapper);
    }

    private InjectionOptions optionsFor(JsonNode injArray, int index) {
        if (injArray.size() <= index || !injArray.get(index).isObject()) {
            return InjectionOptions.DEFAULT;
        }
        return new InjectionOptions(injArray.get(index).path("nullable").asBoolean(false));
    }

    private String injectionDescription(InjectionType type, String scope, Target target) {
        StringBuilder description = new StringBuilder(type.toString());
        if (scope != null && !scope.isBlank()) {
            description.append("(").append(scope).append(")");
        }
        description.append(" target ");
        description.append(target == null ? "<null>" : target.parts);
        return description.toString();
    }

    private record InjectionOptions(boolean nullable) {
        private static final InjectionOptions DEFAULT = new InjectionOptions(false);
    }

    private static class RequiredInjectionException extends RuntimeException {
        RequiredInjectionException(String message) {
            super(message);
        }
    }

}
