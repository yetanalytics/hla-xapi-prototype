package com.yetanalytics.hlaxapi.config;

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
public class InjectionHandler {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static volatile CacheQueryService queryService;

    public static void setQueryService(CacheQueryService cacheQueryService) {
        queryService = cacheQueryService;
    }

    public static String handleThis(Target t) {
        return null;
    }

    public static String handleQuery(String clazz, Target attrTarget, Expression criteria) {
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
