package com.yetanalytics.hlaxapi;

import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.yetanalytics.hlaxapi.config.model.Target;
import com.yetanalytics.hlaxapi.injection.InjectionContext;
import com.yetanalytics.hlaxapi.injection.InteractionInjectionContext;
import com.yetanalytics.hlaxapi.injection.ObjectInjectionContext;

import hla.rti1516e.encoding.DecoderException;

import com.yetanalytics.hlaxapi.FOMXML.PathCheckResult;
import com.yetanalytics.hlaxapi.config.model.Expression;

/**
 * Stubs for injection handlers. In the real app these will implement logic to
 * resolve injection syntaxes like ["this", [target]] or ["query", ...].
 */
@Component
public class InjectionHandler {

    private static final Logger logger = LogManager.getLogger(InjectionHandler.class);

    @Autowired
    private FOMXML fomXml;

    @Autowired
    private HLADecoderRegistry hlaDecoderRegistry;

    public Object handleThis(Target t, InjectionContext context) {
        if (context instanceof InteractionInjectionContext) {
            return handleThis(t, (InteractionInjectionContext) context);
        } else if (context instanceof ObjectInjectionContext) {
            return handleThis(t, (ObjectInjectionContext) context);
        } else {
            throw new IllegalArgumentException("Unsupported InjectionContext type: " + context.getClass().getName());
        }
    }

    public Object handleThis(Target t, InteractionInjectionContext context) {

        byte[] value = interrogateParameters(t.parts, context.getParameterMap());
        if (value == null) return null;

        PathCheckResult pcr = fomXml.checkInteractionParameterPath(context.getHlaClass(), t.parts);
        Object result;
        if(pcr.exists) {
            try {
                result = hlaDecoderRegistry.decode(pcr.primitiveType, value);
            } catch (DecoderException e) {
                logger.warn("Problem decoding value:", e);
                return null;
            }
        } else {
            //TODO: Properly log context of unfound target
            logger.warn("Target does not exist in FOM.", t);
            return null;
        }
        // placeholder: return a demo string showing the target and interaction context
        return result;
    }

    private byte[] interrogateParameters(List<Object> targetParts, Map<String, byte[]> paramMap) {
        if (targetParts.size() == 0) return null;

        Object thisPart  = targetParts.getFirst();

        //TODO: handle recursive case for nested. This is just a stand-in that works for one-level ops

        return paramMap.entrySet().stream()
            .filter(entry -> entry.getKey().equals(thisPart))
            .map(entry -> entry.getValue())
            .findAny()
            .orElse(null);

    }

    public Object handleThis(Target t, ObjectInjectionContext context) {
        // placeholder: return a demo string showing the target and interaction context
        return "[THIS(object):" + t.toString() + ":CONTEXT:" + context.getHlaClass() + "]";
    }

    public Object handleQuery(String clazz, Target attrTarget, Expression criteria, InjectionContext context) {
        // placeholder: return a demo string showing the criteria expression
        // TODO: Query methods for both types like interation has
        return "[QUERY:" + clazz
                + ":" + (attrTarget == null ? "null" : attrTarget.toString())
                + ":" + (criteria == null ? "null" : criteria.toString())
                + "]";
    }

    //for test
    public void setFomXml(FOMXML fomXml) {
        this.fomXml = fomXml;
    }
    public void setHLADecoderRegistry(HLADecoderRegistry hdr) {
        this.hlaDecoderRegistry = hdr;
    }
}
