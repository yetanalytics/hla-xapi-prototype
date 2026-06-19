package com.yetanalytics.hlaxapi;

import org.springframework.stereotype.Component;

import com.yetanalytics.hlaxapi.config.model.Target;
import com.yetanalytics.hlaxapi.config.model.Expression;

/**
 * Stubs for injection handlers. In the real app these will implement logic to
 * resolve injection syntaxes like ["this", [target]] or ["query", ...].
 */
@Component
public class InjectionHandler {

    public String handleThis(Target t) {
        // placeholder: return a demo string showing the target
        return "[THIS:" + t.toString() + "]";
    }

    public String handleQuery(String clazz, Target attrTarget, Expression criteria) {
        // placeholder: return a demo string showing the criteria expression
        return "[QUERY:" + clazz
                + ":" + (attrTarget == null ? "null" : attrTarget.toString())
                + ":" + (criteria == null ? "null" : criteria.toString())
                + "]";
    }
}
