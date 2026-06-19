package com.yetanalytics.hlaxapi.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.yetanalytics.hlaxapi.config.model.InjectionType;
import com.yetanalytics.hlaxapi.config.model.LogicalOperator;
import com.yetanalytics.hlaxapi.config.model.LrsConfig;
import com.yetanalytics.hlaxapi.config.model.StatementTrigger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight parser for the config format described in config-ideas.md.
 * It loads the JSON file and performs higher-level parsing into Java objects.
 */
public class ConfigParser {

    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonNode root;

    public ConfigParser(String path) throws IOException {
        File f = Paths.get(path).toFile();
        this.root = mapper.readTree(f);
    }

    public static ConfigParser fromEnvOrDefault() throws IOException {
        String path = System.getenv().getOrDefault("XAPI_CONFIG", "config/xapi-config.json");
        return ConfigParser.fromFile(path);
    }

    public static ConfigParser fromFile(String path) throws IOException {
        return new ConfigParser(path);
    }

    public XapiConfig parse() {
        XapiConfig cfg = new XapiConfig();

        // statementTriggers
        JsonNode st = root.get("statementTriggers");
        if (st != null && st.isArray()) {
            List<StatementTrigger> triggers = new ArrayList<>();
            for (JsonNode tnode : st) {
                StatementTrigger stt = new StatementTrigger();
                stt.type = StatementTrigger.Type.fromString(tnode.path("type").asText(null));
                // map "class" json prop to clazz
                stt.clazz = tnode.path("class").asText(null);
                Object rawCrit = parseCriteriaNode(tnode.get("criteria"));
                stt.criteria = ConfigConverter.toCriterion(rawCrit);
                if (tnode.has("statement")) {
                    try {
                        stt.statement = mapper.writeValueAsString(tnode.get("statement"));
                    } catch (JsonProcessingException e) {
                        stt.statement = null;
                    }
                }
                triggers.add(stt);
            }
            cfg.statementTriggers = triggers;
        }

        // lrs
        if (root.has("lrs")) {
            cfg.lrsConfig = mapper.convertValue(root.get("lrs"), LrsConfig.class);
        }

        return cfg;
    }

    private Object parseCriteriaNode(JsonNode node) {
        // criteria syntax: [targetSyntax, operator, value]
        if (node == null || node.isNull()) return null;

        if (node.isArray()) {
            ArrayNode an = (ArrayNode) node;
            // Could be nested criteria: e.g. [criteria, "or", criteria]
            if (an.size() == 3 && !isLogicalOperator(an.get(1))) {
                Object target = parseTargetSyntax(an.get(0));
                String op = an.get(1).asText();
                Object val = parseValueNode(an.get(2));
                return List.of(target, op, val);
            }
            // Otherwise treat as raw array/compound expression
            List<Object> out = new ArrayList<>();
            for (JsonNode el : an) {
                if (el.isTextual()
                        && (LogicalOperator.fromString(el.asText().toLowerCase()) != null)) {
                    out.add(el.asText().toLowerCase());
                } else {
                    out.add(parseCriteriaNode(el));
                }
            }
            return out;
        }

        // fallback to primitive
        if (node.isTextual()) return node.asText();
        if (node.isNumber()) return node.numberValue();
        if (node.isBoolean()) return node.booleanValue();
        return mapper.convertValue(node, Object.class);
    }

    private boolean isLogicalOperator(JsonNode n) {
        if (!n.isTextual()) return false;
        String s = n.asText().toLowerCase();
        return LogicalOperator.fromString(s) != null;
    }

    private Object parseTargetSyntax(JsonNode node) {
        // target syntax is an array of strings and ints
        if (node == null || !node.isArray()) return null;
        List<Object> parts = new ArrayList<>();
        for (JsonNode el : node) {
            if (el.isTextual()) parts.add(el.asText());
            else if (el.isInt()) parts.add(el.asInt());
            else if (el.isLong()) parts.add(el.longValue());
            else parts.add(mapper.convertValue(el, Object.class));
        }
        return parts;
    }

    private Object parseValueNode(JsonNode node) {
        // value can be an injection syntax, a primitive, or another criteria
        if (node == null || node.isNull()) return null;
        if (node.isArray()) {
            ArrayNode an = (ArrayNode) node;
            if (an.size() > 0 && an.get(0).isTextual()) {
                String keyword = an.get(0).asText();
                if (InjectionType.fromString(keyword) != null) {
                    // return the raw array as parsed JSON to be interpreted later
                    return mapper.convertValue(node, List.class);
                }
            }
            // otherwise fallback
            return mapper.convertValue(node, List.class);
        }
        if (node.isTextual()) return node.asText();
        if (node.isNumber()) return node.numberValue();
        if (node.isBoolean()) return node.booleanValue();
        return mapper.convertValue(node, Object.class);
    }
}
