package com.yetanalytics.hlaxapi;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.databind.node.NullNode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.yetanalytics.hlaxapi.config.ConfigConverter;
import com.yetanalytics.hlaxapi.config.model.StatementTrigger;
import com.yetanalytics.hlaxapi.config.model.Target;
import com.yetanalytics.hlaxapi.injection.InjectionContext;
import com.yetanalytics.hlaxapi.config.model.Expression;
import com.yetanalytics.hlaxapi.config.model.InjectionType;

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

            JsonNode processed = processNode(stmtNode, context, mapper);

            String output = mapper.writeValueAsString(processed);
            logger.info("Processed statement output: {}", output);
            return output;
        } catch (Exception e) {
            logger.debug("Could not process statement for trigger", e);
            return null;
        }
    }

    private static final Pattern INLINE_PLACEHOLDER = Pattern.compile("<<(.+?)>>", Pattern.DOTALL);

    private JsonNode processNode(JsonNode node, InjectionContext context, ObjectMapper mapper) {
        if (node == null || node.isNull())
            return node;

        try {
            if (node.isObject()) {
                ObjectNode out = mapper.createObjectNode();
                node.fieldNames().forEachRemaining(field -> {
                    JsonNode child = node.get(field);
                    JsonNode processedChild = processNode(child, context, mapper);
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
                        return handleInjectionArray(node, context, mapper, false);
                    }
                }
                ArrayNode out = mapper.createArrayNode();
                for (JsonNode el : node) {
                    out.add(processNode(el, context, mapper));
                }
                return out;
            }

            if (node.isTextual()) {
                String txt = node.asText();
                Matcher m = INLINE_PLACEHOLDER.matcher(txt);
                if (!m.find())
                    return node;

                // If the entire text equals a single placeholder, and replacement is a full
                // JSON node,
                // return that node instead of a text node.
                m.reset();
                StringBuffer sb = new StringBuffer();
                // track whether whole-string equals placeholder (not used currently)
                List<JsonNode> replacements = new ArrayList<>();
                while (m.find()) {
                    String inner = m.group(1);
                    try {
                        JsonNode injNode = mapper.readTree(inner);
                        JsonNode repNode = null;
                        if (injNode.isArray() && injNode.size() > 0 && injNode.get(0).isTextual()
                                && InjectionType.fromString(injNode.get(0).asText()) != null) {
                            repNode = handleInjectionArray(injNode, context, mapper, true);
                        }
                        if (repNode == null) {
                            // parsing succeeded but not an injection array; leave placeholder alone
                            m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
                        } else {
                            replacements.add(repNode);
                            String replacementSerialized;
                            if (repNode.isValueNode()) {
                                replacementSerialized = repNode.asText();
                            } else {
                                replacementSerialized = mapper.writeValueAsString(repNode);
                            }
                            m.appendReplacement(sb, Matcher.quoteReplacement(replacementSerialized));
                        }
                    } catch (Exception e) {
                        // inner JSON invalid: leave placeholder text unchanged
                        m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
                    }
                }
                m.appendTail(sb);
                String replaced = sb.toString();

                // if the original text was exactly a single placeholder and replacement was a
                // non-text node,
                // return that node directly
                if (replacements.size() == 1 && txt.trim()
                        .equals("<<" + mapper.writeValueAsString(replacements.get(0)) + "==>REPLACEMARKER>>")) {
                    // This is a fallback path unlikely to be hit; return text node instead
                    return TextNode.valueOf(replaced);
                }

                return TextNode.valueOf(replaced);
            }

            return node;
        } catch (Exception e) {
            logger.debug("Error processing node", e);
            return node;
        }
    }

    private JsonNode handleInjectionArray(JsonNode injArray, InjectionContext context, ObjectMapper mapper,
            Boolean embedded) {
        try {
            String keyword = injArray.get(0).asText();
            InjectionType iType = InjectionType.fromString(keyword);
            if (iType == InjectionType.THIS && injArray.size() >= 2) {
                Object rawTarget = mapper.convertValue(injArray.get(1), Object.class);
                Target t = ConfigConverter.toTarget(rawTarget);
                Object replacement = injectionHandler.handleThis(t, context);
                if (replacement == null)
                    return NullNode.instance;
                String replacementString = render(replacement, embedded);
                try {
                    return mapper.readTree(replacementString);
                } catch (Exception e) {
                    return TextNode.valueOf(replacementString);
                }
            } else if (iType == InjectionType.QUERY && injArray.size() >= 4) {
                String clazz = injArray.get(1).asText();
                Object rawTarget = mapper.convertValue(injArray.get(2), Object.class);
                Target attr = ConfigConverter.toTarget(rawTarget);
                Object criteriaRaw = mapper.convertValue(injArray.get(3), Object.class);
                Expression criteriaExpr = ConfigConverter.toExpression(criteriaRaw);
                Object replacement = injectionHandler.handleQuery(clazz, attr, criteriaExpr, context);
                if (replacement == null)
                    return NullNode.instance;
                String replacementString = render(replacement, embedded);
                try {
                    return mapper.readTree(replacementString);
                } catch (Exception e) {
                    return TextNode.valueOf(replacementString);
                }
            }
        } catch (Exception e) {
            logger.debug("Error handling injection array", e);
        }
        return NullNode.instance;
    }

    private String render(Object replacement, Boolean embedded) {
        // TODO: Expand for special type handling.
        String formatString = (replacement instanceof String && embedded) ? "\"%s\"" : "%s";
        return String.format(formatString, replacement.toString());
    }

    // Legacy parsing method removed; processNode handles both array injections and
    // inline placeholders.

}
