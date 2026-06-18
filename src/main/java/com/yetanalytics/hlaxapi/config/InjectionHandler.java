package com.yetanalytics.hlaxapi.config;

import com.yetanalytics.hlaxapi.config.model.Expression;
import com.yetanalytics.hlaxapi.config.model.Target;

/**
 * Stubs for injection handlers. In the real app these will implement logic to
 * resolve injection syntaxes like ["this", [target]] or ["query", ...].
 */
public class InjectionHandler {

    public static String handleThis(Target t) {
        // placeholder: return a demo string showing the target
        return "[THIS:" + t.toString() + "]";
    }

    public static String handleQuery(String clazz, Target attrTarget, Expression criteria) {
        // placeholder: return a demo string showing the criteria expression
        return "[QUERY:"
                + clazz
                + ":"
                + (attrTarget == null ? "null" : attrTarget.toString())
                + ":"
                + (criteria == null ? "null" : criteria.toString())
                + "]";
    }
}
