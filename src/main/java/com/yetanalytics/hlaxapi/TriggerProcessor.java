package com.yetanalytics.hlaxapi;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    //For tests and non-Spring code, allow injection of a custom InjectionHandler
    public TriggerProcessor(InjectionHandler injectionHandler) {
        this.injectionHandler = injectionHandler;
    }

    public String processTrigger(StatementTrigger trigger, InjectionContext context) {
        if (trigger == null || trigger.statement == null) {
            return null;
        }

        ObjectMapper mapper = new ObjectMapper();
        String output = trigger.statement;
        try {

            JsonNode stmtNode = mapper.readTree(trigger.statement);
            // walk node to find array nodes that look like injection: ["this", ...] or
            // ["query", ...]
            List<JsonNode> injections = new ArrayList<>();
            findInjectionArrays(stmtNode, injections);
            for (JsonNode inj : injections) {
                if (!inj.isArray() || inj.size() == 0)
                    continue;
                String keyword = inj.get(0).asText();
                InjectionType iType = InjectionType.fromString(keyword);
                String replacement = null;
                if (iType == InjectionType.THIS && inj.size() >= 2) {

                    Object rawTarget = mapper.convertValue(inj.get(1), Object.class);
                    Target t = ConfigConverter.toTarget(rawTarget);
                    replacement = injectionHandler.handleThis(t, context);
                } else if (iType == InjectionType.QUERY && inj.size() >= 4) {

                    String clazz = inj.get(1).asText();
                    Object rawTarget = mapper.convertValue(inj.get(2), Object.class);
                    Target attr = ConfigConverter.toTarget(rawTarget);
                    Object criteriaRaw = mapper.convertValue(inj.get(3), Object.class);
                    // convert raw criteria into typed Expression tree
                    Expression criteriaExpr = ConfigConverter
                            .toExpression(criteriaRaw);
                    replacement = injectionHandler.handleQuery(clazz, attr, criteriaExpr, context);
                }

                if (replacement != null) {
                    // replace the first occurrence of the JSON text of inj in output
                    String injText = mapper.writeValueAsString(inj);
                    output = output.replace(injText, replacement);
                }
            }

            logger.info("Processed statement output: {}", output);

            return output;
        } catch (Exception e) {
            logger.debug("Could not process statement for trigger", e);
            return null;
        }
    }

    private void findInjectionArrays(JsonNode node, List<JsonNode> out) {
        if (node == null)
            return;
        if (node.isArray()) {
            if (node.size() > 0 && node.get(0).isTextual()) {
                String k = node.get(0).asText();
                // check if this looks like an injection array
                if (InjectionType.fromString(k) != null) {
                    out.add(node);
                    return;
                }
            }
            for (JsonNode el : node)
                findInjectionArrays(el, out);
            return;
        }
        if (node.isObject()) {
            node.forEach(n -> findInjectionArrays(n, out));
        }
    }

}
