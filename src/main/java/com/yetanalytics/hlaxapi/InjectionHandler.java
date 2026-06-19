package com.yetanalytics.hlaxapi;

import org.springframework.stereotype.Component;

import com.yetanalytics.hlaxapi.config.model.Target;
import com.yetanalytics.hlaxapi.injection.InjectionContext;
import com.yetanalytics.hlaxapi.injection.InteractionInjectionContext;
import com.yetanalytics.hlaxapi.injection.ObjectInjectionContext;
import com.yetanalytics.hlaxapi.config.model.Expression;

/**
 * Stubs for injection handlers. In the real app these will implement logic to
 * resolve injection syntaxes like ["this", [target]] or ["query", ...].
 */
@Component
public class InjectionHandler {

    public String handleThis(Target t, InjectionContext context) {
        if (context instanceof InteractionInjectionContext) {
            return handleThis(t, (InteractionInjectionContext) context);
        } else if (context instanceof ObjectInjectionContext) {
            return handleThis(t, (ObjectInjectionContext) context);
        } else {
            throw new IllegalArgumentException("Unsupported InjectionContext type: " + context.getClass().getName());
        }
    }

    public String handleThis(Target t, InteractionInjectionContext context) {
        t.printParts();
        context.getParameterMap().forEach((handle, value) -> {
            System.out.println("Parameter Handle: " + handle + ", Value: " + new String(value));
        });
        // placeholder: return a demo string showing the target and interaction context
        return "[THIS(interaction):" + t.toString() + ":CONTEXT:" + context.getHlaClass() + "]";
    }

    public String handleThis(Target t, ObjectInjectionContext context) {
        // placeholder: return a demo string showing the target and interaction context
        return "[THIS(object):" + t.toString() + ":CONTEXT:" + context.getHlaClass() + "]";
    }

    public String handleQuery(String clazz, Target attrTarget, Expression criteria, InjectionContext context) {
        // placeholder: return a demo string showing the criteria expression
        // TODO: Query methods for both types like interation has
        return "[QUERY:" + clazz
                + ":" + (attrTarget == null ? "null" : attrTarget.toString())
                + ":" + (criteria == null ? "null" : criteria.toString())
                + "]";
    }
}
