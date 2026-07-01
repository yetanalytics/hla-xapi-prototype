package com.yetanalytics.hlaxapi.cache;

import com.yetanalytics.hlaxapi.FOMXML;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.xml.xpath.XPathExpressionException;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * FOM-derived object metadata used by the SQLite cache.
 */
@Component
public final class FomCatalog {

    private final Map<String, ObjectClassDef> classesByName;
    private final Map<Integer, ObjectClassDef> classesById;
    private final Map<Integer, FomAttribute> attributesById;

    public FomCatalog(FOMXML fomXml) {
        Objects.requireNonNull(fomXml, "fomXml");
        CatalogBuilder builder = new CatalogBuilder(fomXml);
        Document document = fomXml.getDoc();
        Element objects = firstElement(document.getDocumentElement(), "objects");
        if (objects != null) {
            for (Element objectClass : childElements(objects, "objectClass")) {
                builder.parseObjectClass(objectClass, null, new ArrayList<>());
            }
        }
        this.classesByName = builder.classesByName;

        Map<Integer, ObjectClassDef> byId = new LinkedHashMap<>();
        Map<Integer, FomAttribute> attrsById = new LinkedHashMap<>();
        for (ObjectClassDef clazz : classesByName.values()) {
            byId.put(clazz.id(), clazz);
            for (FomAttribute attribute : clazz.attributes()) {
                attrsById.put(attribute.id(), attribute);
            }
        }
        this.classesById = Collections.unmodifiableMap(byId);
        this.attributesById = Collections.unmodifiableMap(attrsById);
    }

    public Collection<ObjectClassDef> objectClasses() {
        return classesByName.values();
    }

    public Optional<ObjectClassDef> objectClass(String name) {
        return Optional.ofNullable(classesByName.get(localName(name)));
    }

    public Optional<ObjectClassDef> objectClass(int id) {
        return Optional.ofNullable(classesById.get(id));
    }

    public Optional<FomAttribute> attribute(int id) {
        return Optional.ofNullable(attributesById.get(id));
    }

    public Optional<FomAttribute> attribute(String className, String attributeName, String pathKey) {
        return objectClass(className).flatMap(clazz -> clazz.attribute(attributeName, pathKey));
    }

    public Optional<FomAttribute> attribute(String className, String pathKey) {
        return objectClass(className).flatMap(clazz -> clazz.attribute(pathKey));
    }

    public static String targetPath(List<Object> targetParts) {
        if (targetParts == null || targetParts.isEmpty()) {
            return null;
        }
        StringBuilder path = new StringBuilder();
        for (Object part : targetParts) {
            if (part instanceof Number number) {
                path.append('[').append(number.intValue()).append(']');
            } else if (part != null) {
                if (path.length() > 0) {
                    path.append('.');
                }
                path.append(part);
            }
        }
        return path.toString();
    }

    public static String topLevelTargetPart(List<Object> targetParts) {
        if (targetParts == null || targetParts.isEmpty() || !(targetParts.get(0) instanceof String part)) {
            return null;
        }
        return part;
    }

    public static String wildcardArrayIndexes(String pathKey) {
        if (pathKey == null) {
            return null;
        }
        return pathKey.replaceAll("\\[[0-9]+\\]", "[]");
    }

    static String localName(String hlaName) {
        if (hlaName == null) {
            return null;
        }
        String trimmed = hlaName.trim();
        int index = trimmed.lastIndexOf('.');
        return index >= 0 ? trimmed.substring(index + 1) : trimmed;
    }

    private static String text(Element parent, String tagName) {
        Element element = firstElement(parent, tagName);
        if (element == null) {
            return null;
        }
        String text = element.getTextContent();
        return text == null || text.isBlank() ? null : text.trim();
    }

    private static Element firstElement(Element parent, String tagName) {
        for (Element element : childElements(parent, tagName)) {
            return element;
        }
        return null;
    }

    private static List<Element> childElements(Element parent, String tagName) {
        if (parent == null) {
            return List.of();
        }
        List<Element> elements = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element element && element.getTagName().equals(tagName)) {
                elements.add(element);
            }
        }
        return elements;
    }

    public record ObjectClassDef(
            int id,
            String hlaName,
            String localName,
            String parentName,
            List<FomAttribute> attributes) {

        public ObjectClassDef {
            attributes = List.copyOf(attributes);
        }

        public List<FomAttribute> leafAttributes() {
            return attributes.stream().filter(FomAttribute::leaf).toList();
        }

        public List<String> topLevelAttributeNames() {
            Set<String> names = new LinkedHashSet<>();
            for (FomAttribute attribute : attributes) {
                names.add(attribute.attributeName());
            }
            return List.copyOf(names);
        }

        public Optional<FomAttribute> attribute(String pathKey) {
            String localPath = pathKey == null ? null : pathKey.trim();
            String wildcardPath = wildcardArrayIndexes(localPath);
            for (FomAttribute attribute : attributes) {
                if (attribute.pathKey().equals(localPath) || attribute.pathKey().equals(wildcardPath)) {
                    return Optional.of(attribute);
                }
            }
            return Optional.empty();
        }

        public Optional<FomAttribute> attribute(String attributeName, String pathKey) {
            return attribute(pathKey).filter(attribute -> attribute.attributeName().equals(attributeName));
        }
    }

    public record FomAttribute(
            int id,
            int classId,
            String attributeName,
            String pathKey,
            String dataType,
            String primitiveType,
            boolean leaf) {
    }

    private static final class CatalogBuilder {

        private final FOMXML fomXml;
        private final Map<String, ObjectClassDef> classesByName = new LinkedHashMap<>();
        private int nextClassId = 1;
        private int nextAttributeId = 1;

        private CatalogBuilder(FOMXML fomXml) {
            this.fomXml = fomXml;
        }

        private void parseObjectClass(Element element, String parentName, List<AttributeSource> inheritedAttributes) {
            String className = text(element, "name");
            if (className == null) {
                return;
            }

            List<AttributeSource> allAttributes = new ArrayList<>(inheritedAttributes);
            for (Element attribute : childElements(element, "attribute")) {
                String attributeName = text(attribute, "name");
                String dataType = text(attribute, "dataType");
                if (attributeName != null && dataType != null) {
                    allAttributes.add(new AttributeSource(attributeName, dataType));
                }
            }

            int classId = nextClassId++;
            List<FomAttribute> flattened = new ArrayList<>();
            for (AttributeSource attribute : allAttributes) {
                flattenAttribute(classId, attribute.name(), attribute.name(), attribute.dataType(), flattened);
            }

            ObjectClassDef classDef =
                    new ObjectClassDef(classId, className, localName(className), parentName, flattened);
            classesByName.put(classDef.localName(), classDef);

            for (Element childClass : childElements(element, "objectClass")) {
                parseObjectClass(childClass, classDef.localName(), allAttributes);
            }
        }

        private void flattenAttribute(
                int classId,
                String attributeName,
                String pathKey,
                String dataType,
                List<FomAttribute> attributes) {
            String primitive = primitiveType(dataType);
            List<FOMXML.FixedRecordField> fields = fixedRecordFields(dataType);
            String arrayElementType = arrayElementType(dataType);
            boolean leaf = primitive != null || fields.isEmpty() && arrayElementType == null;

            attributes.add(new FomAttribute(
                    nextAttributeId++,
                    classId,
                    attributeName,
                    pathKey,
                    dataType,
                    primitive,
                    leaf));

            if (!fields.isEmpty()) {
                for (FOMXML.FixedRecordField field : fields) {
                    flattenAttribute(
                            classId,
                            attributeName,
                            pathKey + "." + field.name,
                            field.dataType,
                            attributes);
                }
            } else if (arrayElementType != null) {
                flattenAttribute(classId, attributeName, pathKey + "[]", arrayElementType, attributes);
            }
        }

        private String primitiveType(String dataType) {
            try {
                return fomXml.resolvePrimitiveType(dataType);
            } catch (XPathExpressionException e) {
                throw new IllegalArgumentException("Could not resolve primitive type for " + dataType, e);
            }
        }

        private List<FOMXML.FixedRecordField> fixedRecordFields(String dataType) {
            try {
                if (!fomXml.isFixedRecordType(dataType)) {
                    return List.of();
                }
                return fomXml.getFixedRecordFields(dataType);
            } catch (XPathExpressionException e) {
                throw new IllegalArgumentException("Could not resolve fixed record fields for " + dataType, e);
            }
        }

        private String arrayElementType(String dataType) {
            try {
                if (!fomXml.isArrayType(dataType)) {
                    return null;
                }
                String elementType = fomXml.getArrayElementType(dataType);
                return elementType == null || elementType.isBlank() ? null : elementType;
            } catch (XPathExpressionException e) {
                throw new IllegalArgumentException("Could not resolve array element type for " + dataType, e);
            }
        }

        private record AttributeSource(String name, String dataType) {
        }
    }
}
