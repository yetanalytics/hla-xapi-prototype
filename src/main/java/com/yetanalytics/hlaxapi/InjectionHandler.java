package com.yetanalytics.hlaxapi;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yetanalytics.hlaxapi.cache.CacheQueryService;
import com.yetanalytics.hlaxapi.config.model.Expression;
import com.yetanalytics.hlaxapi.config.model.Target;
import java.util.Optional;

/**
 * Stubs for injection handlers. In the real app these will implement logic to
 * resolve injection syntaxes like ["this", [target]] or ["query", ...].
 */
@Component
public class InjectionHandler {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private volatile CacheQueryService queryService;

    public void setQueryService(CacheQueryService cacheQueryService) {
        this.queryService = cacheQueryService;
    }

    public String handleThis(Target t) {
        return null;
    }

    public String handleQuery(String clazz, Target attrTarget, Expression criteria) {
        CacheQueryService service = queryService;
        if (service == null) {
            return null;
        }
        Optional<Object> value = service.findFirstValue(clazz, attrTarget, criteria);
        return value.map(InjectionHandler::toJson).orElse("null");
    }

    private static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "null";
        }
    }
}
