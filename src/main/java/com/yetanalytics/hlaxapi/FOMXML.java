package com.yetanalytics.hlaxapi;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;

import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.util.List;

@Component
public class FOMXML {

    private static final Logger logger = LogManager.getLogger(App.class);

    private Document doc;
    private XPath xPath;
    private HLADecoderRegistry decoderRegistry;

    //automatically injected by spring
    public FOMXML(SimulationConfig simConfig, HLADecoderRegistry decoderRegistry) {
        File xmlFile = new File(simConfig.getFom());
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);

        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            setDoc(builder.parse(xmlFile));
        } catch (SAXException | IOException | ParserConfigurationException e) {
            logger.error("Could not parse FOM XML.", e);
        }

        setxPath(XPathFactory.newInstance().newXPath());
        setDecoderRegistry(decoderRegistry);
    }

    private boolean isPrim(String type) {
        try {
            decoderRegistry.decoderFor(type);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Result holder for path checks.
     */
    public static class PathCheckResult {
        public final boolean exists;
        /**
         * The resolved primitive type (e.g. HLAinteger32LE) if available, otherwise null.
         */
        public final String primitiveType;
        /**
         * The raw type name found at the end of the provided path (may be a custom type).
         */
        public final String resolvedType;

        public PathCheckResult(boolean exists, String primitiveType, String resolvedType) {
            this.exists = exists;
            this.primitiveType = primitiveType;
            this.resolvedType = resolvedType;
        }

        public String toString() {
            return String.format(
                    "PathCheckResult{exists=%s, primitiveType=%s, resolvedType=%s}",
                    exists,
                    primitiveType,
                    resolvedType);
        }
    }

    public PathCheckResult checkInteractionParameterPath(String interactionName, List<Object> pathParts){
        try {
            return checkParameterPath(interactionName, true, pathParts);
        } catch (XPathExpressionException e) {
            logger.error("Error checking interaction parameter path", e);
            return new PathCheckResult(false, null, null);
        }
    }

    public PathCheckResult checkInteractionParameterPath(String interactionName, String param){
        return checkInteractionParameterPath(interactionName, List.of(param));
    }

    public PathCheckResult checkObjectParameterPath(String objectName, List<Object> pathParts) {
        try {
            return checkParameterPath(objectName, false, pathParts);
        } catch (XPathExpressionException e) {
            logger.error("Error checking object parameter path", e);
            return new PathCheckResult(false, null, null);
        }
    }

    public PathCheckResult checkObjectParameterPath(String objectName, String param){
        return checkObjectParameterPath(objectName, List.of(param));
    }



    private final String findInteractionByNameExp =
            "//interactionClass[name[text()='%s']]/parameter[name[text()='%s']]/dataType";
    private final String findObjectByNameExp =
            "//objectClass[name[text()='%s']]/attribute[name[text()='%s']]/dataType";
    private final String fixedRecordDataTypeExp =
            "//fixedRecordData[name[text()='%s']]/field[name[text()='%s']]/dataType";
    private final String arrayDataTypeExp = "//arrayData[name[text()='%s']]/dataType";

    /**
     * Given an interaction name and a path (where the first element is the parameter
     * name and subsequent elements are field names or integer array indices), resolve
     * whether that path exists and what primitive representation (if any) is at the
     * root of the resolved path.
     *
     */
    private PathCheckResult checkParameterPath(String entityName, boolean isInteraction, List<Object> pathParts)
            throws XPathExpressionException {
        if (entityName == null || entityName.isEmpty())
            throw new IllegalArgumentException("entity name is required");

        if (pathParts == null || pathParts.isEmpty())
            throw new IllegalArgumentException("First element of pathParts must be the parameter name (String)");

        Object first = pathParts.get(0);
        if (!(first instanceof String)) {
            throw new IllegalArgumentException("First element of pathParts must be the parameter name (String)");
        }

        // find parameter dataType for the interaction/object
        String paramExp = String.format(isInteraction ? findInteractionByNameExp : findObjectByNameExp,
            entityName, (String) first);
        String currentTypeName = (String) xPath.compile(paramExp).evaluate(doc, XPathConstants.STRING);

        if (currentTypeName == null || currentTypeName.isEmpty()) {
            return new PathCheckResult(false, null, null);
        }

        // Walk the remaining parts
        for (int i = 1; i < pathParts.size(); i++) {
            Object part = pathParts.get(i);
            String foundType = null;

            if (part instanceof Integer) {
                int idx = (Integer) part;
                if (idx < 0) {
                    throw new IllegalArgumentException("Array index must be 0 or greater");
                }
                // resolve array element dataType for currentTypeName
                String exp = String.format(arrayDataTypeExp, currentTypeName);
                foundType = (String) xPath.compile(exp).evaluate(doc, XPathConstants.STRING);
            } else if (part instanceof String) {
                String fieldName = (String) part;
                // try fixedRecord field
                String fixedRecordExp = String.format(fixedRecordDataTypeExp, currentTypeName, fieldName);
                foundType = (String) xPath.compile(fixedRecordExp).evaluate(doc, XPathConstants.STRING);
            } else {
                throw new IllegalArgumentException("Path parts must be String (field name) or Integer (array index)");
            }

            if (foundType == null || foundType.isEmpty()) {
                return new PathCheckResult(false, null, null);
            }

            currentTypeName = foundType;
        }

        // currentTypeName is now the type at the end of the path. It may be
        // primitive, simpleData, enumeratedData, or another custom type.
        // If it's a primitive, return it. Otherwise try to resolve to a primitive via getRawType.
        if (isPrim(currentTypeName)) {
            return new PathCheckResult(true, currentTypeName, currentTypeName);
        }

        String raw = getRawType(currentTypeName);
        if (raw != null && !raw.isEmpty() && isPrim(raw)) {
            return new PathCheckResult(true, raw, currentTypeName);
        }

        // Not resolved to a primitive
        return new PathCheckResult(true, null, currentTypeName);
    }

    private final String checkSimpleDataTypeExp = "//simpleData[name[text()='%s']]/representation";
    private final String checkEnumDataTypeExp = "//enumeratedData[name[text()='%s']]/representation";

    /**
     * Given what is presumed to be a custom data type name, check if it's a simpleData or enumeratedData and return
     * the primitive representation if so.
     *
     */
    private String getRawType(String dataTypeName) throws XPathExpressionException{

        String simpleExp = String.format(checkSimpleDataTypeExp, dataTypeName);
        String simpleType = (String) xPath.compile(simpleExp).evaluate(doc, XPathConstants.STRING);
        if (!simpleType.isEmpty())
            return simpleType;

        String enumExp = String.format(checkEnumDataTypeExp, dataTypeName);
        String enumType = (String) xPath.compile(enumExp).evaluate(doc, XPathConstants.STRING);
        if (!enumType.isEmpty())
            return enumType;

        return null;
    }

    public Document getDoc() {
        return doc;
    }

    public void setDoc(Document doc) {
        this.doc = doc;
    }

    public XPath getxPath() {
        return xPath;
    }

    public void setxPath(XPath xPath) {
        this.xPath = xPath;
    }

    public HLADecoderRegistry getDecoderRegistry() {
        return decoderRegistry;
    }

    public void setDecoderRegistry(HLADecoderRegistry decoderRegistry) {
        this.decoderRegistry = decoderRegistry;
    }

}
