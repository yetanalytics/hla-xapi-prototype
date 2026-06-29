package com.yetanalytics.hlaxapi;

import java.util.List;
import java.util.Map;

import javax.xml.xpath.XPathExpressionException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.yetanalytics.hlaxapi.FOMXML.PathCheckResult;
import com.yetanalytics.hlaxapi.config.model.Expression;
import com.yetanalytics.hlaxapi.config.model.Target;
import com.yetanalytics.hlaxapi.injection.InjectionContext;
import com.yetanalytics.hlaxapi.injection.InteractionInjectionContext;
import com.yetanalytics.hlaxapi.injection.ObjectInjectionContext;

import hla.rti1516e.encoding.ByteWrapper;
import hla.rti1516e.encoding.DataElement;
import hla.rti1516e.encoding.DecoderException;

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

        byte[] value = interrogateParameters(context.getHlaClass(), true, t.parts, context.getParameterMap());
        if (value == null)
            return null;

        PathCheckResult pcr = fomXml.checkInteractionParameterPath(context.getHlaClass(), t.parts);
        Object result;
        if (pcr.exists) {
            try {
                result = hlaDecoderRegistry.decode(pcr.primitiveType, value);
            } catch (DecoderException e) {
                logger.warn("Problem decoding value:", e);
                return null;
            }
        } else {
            // TODO: Properly log context of unfound target
            logger.warn("Target does not exist in FOM.", t);
            return null;
        }
        // placeholder: return a demo string showing the target and interaction context
        return result;
    }

    private byte[] interrogateParameters(String entityName, boolean isInteraction,
            List<Object> targetParts, Map<String, byte[]> paramMap) {
        if (targetParts == null || targetParts.isEmpty()) {
            return null;
        }

        Object firstPart = targetParts.get(0);
        if (!(firstPart instanceof String)) {
            throw new IllegalArgumentException("First target part must be a parameter name");
        }

        String parameterName = (String) firstPart;
        byte[] bytes = paramMap.get(parameterName);
        if (bytes == null || targetParts.size() == 1) {
            return bytes;
        }

        String currentType;
        try {
            currentType = fomXml.getParameterType(entityName, parameterName, isInteraction);
        } catch (XPathExpressionException e) {
            logger.warn("Unable to resolve parameter type for {}.{}", entityName, parameterName, e);
            return null;
        }

        if (currentType == null || currentType.isEmpty()) {
            return null;
        }

        return extractBytesForPath(currentType, targetParts.subList(1, targetParts.size()), bytes);
    }

    private byte[] extractBytesForPath(String currentType, List<Object> remainingPath, byte[] bytes) {
        if (remainingPath == null || remainingPath.isEmpty()) {
            return bytes;
        }

        Object part = remainingPath.get(0);
        if (part instanceof Integer) {
            int index = (Integer) part;
            if (index < 0) {
                throw new IllegalArgumentException("Array index must be 0 or greater");
            }
            String elementType;
            try {
                elementType = fomXml.getArrayElementType(currentType);
            } catch (XPathExpressionException e) {
                throw new IllegalStateException("Unable to resolve array element type for " + currentType, e);
            }
            if (elementType == null || elementType.isEmpty()) {
                return null;
            }
            byte[] elementBytes = extractArrayElementBytes(elementType, index, bytes);
            if (elementBytes == null) {
                return null;
            }
            return extractBytesForPath(elementType, remainingPath.subList(1, remainingPath.size()), elementBytes);
        } else if (part instanceof String) {
            String fieldName = (String) part;
            String fieldType;
            try {
                fieldType = fomXml.getFixedRecordFieldType(currentType, fieldName);
            } catch (XPathExpressionException e) {
                throw new IllegalStateException(
                        "Unable to resolve fixed record field type for " + currentType + "." + fieldName, e);
            }
            if (fieldType == null || fieldType.isEmpty()) {
                return null;
            }
            byte[] fieldBytes = extractFixedRecordFieldBytes(currentType, fieldName, bytes);
            if (fieldBytes == null) {
                return null;
            }
            return extractBytesForPath(fieldType, remainingPath.subList(1, remainingPath.size()), fieldBytes);
        } else {
            throw new IllegalArgumentException("Path parts must be String (field name) or Integer (array index)");
        }
    }

    private byte[] extractArrayElementBytes(String elementType, int index, byte[] bytes) {
        ByteWrapper wrapper = new ByteWrapper(bytes);
        int count = wrapper.getInt();
        if (index >= count) {
            return null;
        }

        for (int i = 0; i <= index; i++) {
            DataElement element = fomXml.createDataElementForType(elementType);
            try {
                element.decode(wrapper);
            } catch (DecoderException e) {
                throw new IllegalStateException("Failed to decode array element of type " + elementType, e);
            }
            if (i == index) {
                try {
                    return element.toByteArray();
                } catch (hla.rti1516e.encoding.EncoderException e) {
                    throw new IllegalStateException("Failed to extract bytes for array element of type " + elementType,
                            e);
                }
            }
        }

        return null;
    }

    private byte[] extractFixedRecordFieldBytes(String recordType, String fieldName, byte[] bytes) {
        List<FOMXML.FixedRecordField> fields;
        try {
            fields = fomXml.getFixedRecordFields(recordType);
        } catch (XPathExpressionException e) {
            throw new IllegalStateException("Unable to read fixed record fields for " + recordType, e);
        }
        ByteWrapper wrapper = new ByteWrapper(bytes);
        for (FOMXML.FixedRecordField field : fields) {
            DataElement element = fomXml.createDataElementForType(field.dataType);
            try {
                element.decode(wrapper);
            } catch (DecoderException e) {
                throw new IllegalStateException(
                        "Failed to decode fixed record field " + field.name + " for record " + recordType, e);
            }
            if (field.name.equals(fieldName)) {
                try {
                    return element.toByteArray();
                } catch (hla.rti1516e.encoding.EncoderException e) {
                    throw new IllegalStateException("Failed to extract bytes for fixed record field " + fieldName, e);
                }
            }
        }
        return null;
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

    // for test
    public void setFomXml(FOMXML fomXml) {
        this.fomXml = fomXml;
    }

    public void setHLADecoderRegistry(HLADecoderRegistry hdr) {
        this.hlaDecoderRegistry = hdr;
    }
}
