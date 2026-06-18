package com.yetanalytics.hlaxapi.cache;

import java.io.File;
import java.io.IOException;
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
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * FOM-derived object metadata used by the SQLite cache.
 */
public final class FomCatalog {

    private static final Set<String> PRIMITIVE_TYPES = Set.of(
            "HLAASCIIchar",
            "HLAASCIIstring",
            "HLAboolean",
            "HLAbyte",
            "HLAfloat32BE",
            "HLAfloat32LE",
            "HLAfloat64BE",
            "HLAfloat64LE",
            "HLAinteger16BE",
            "HLAinteger16LE",
            "HLAinteger32BE",
            "HLAinteger32LE",
            "HLAinteger64BE",
            "HLAinteger64LE",
            "HLAoctet",
            "HLAoctetPairBE",
            "HLAoctetPairLE",
            "HLAopaqueData",
            "HLAunicodeChar",
            "HLAunicodeString");

    private final Map<String, ObjectClassDef> classesByName;
    private final Map<Integer, ObjectClassDef> classesById;
    private final Map<String, String> simpleRepresentations;
    private final Map<String, String> enumeratedRepresentations;
    private final Map<String, FixedRecordDef> fixedRecords;
    private final Map<String, ArrayDef> arrays;
    private final Map<Integer, FomAttribute> attributesById;

    private FomCatalog(
            Map<String, ObjectClassDef> classesByName,
            Map<String, String> simpleRepresentations,
            Map<String, String> enumeratedRepresentations,
            Map<String, FixedRecordDef> fixedRecords,
            Map<String, ArrayDef> arrays) {
        this.classesByName = Collections.unmodifiableMap(classesByName);
        this.simpleRepresentations = Collections.unmodifiableMap(simpleRepresentations);
        this.enumeratedRepresentations = Collections.unmodifiableMap(enumeratedRepresentations);
        this.fixedRecords = Collections.unmodifiableMap(fixedRecords);
        this.arrays = Collections.unmodifiableMap(arrays);

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

    public static FomCatalog fromFile(String fomPath) {
        Objects.requireNonNull(fomPath, "fomPath");
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            Document document = factory.newDocumentBuilder().parse(new File(fomPath));
            return fromDocument(document);
        } catch (IOException | ParserConfigurationException | SAXException e) {
            throw new IllegalArgumentException("Could not parse FOM XML: " + fomPath, e);
        }
    }

    static FomCatalog fromDocument(Document document) {
        Map<String, String> simpleTypes = parseRepresentations(document, "simpleData");
        Map<String, String> enumTypes = parseRepresentations(document, "enumeratedData");
        Map<String, FixedRecordDef> fixedRecords = parseFixedRecords(document);
        Map<String, ArrayDef> arrays = parseArrays(document);

        CatalogBuilder builder = new CatalogBuilder(simpleTypes, enumTypes, fixedRecords, arrays);
        Element objects = firstElement(document.getDocumentElement(), "objects");
        if (objects != null) {
            for (Element objectClass : childElements(objects, "objectClass")) {
                builder.parseObjectClass(objectClass, null, new ArrayList<>());
            }
        }
        return new FomCatalog(builder.classesByName, simpleTypes, enumTypes, fixedRecords, arrays);
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

    public Map<String, String> aliasesToPrimitiveTypes() {
        Map<String, String> aliases = new LinkedHashMap<>();
        aliases.putAll(simpleRepresentations);
        aliases.putAll(enumeratedRepresentations);
        return aliases;
    }

    public TypeKind typeKind(String typeName) {
        String localType = localName(typeName);
        if (PRIMITIVE_TYPES.contains(localType)) {
            return TypeKind.PRIMITIVE;
        }
        if (simpleRepresentations.containsKey(localType)) {
            return TypeKind.SIMPLE;
        }
        if (enumeratedRepresentations.containsKey(localType)) {
            return TypeKind.ENUMERATED;
        }
        if (fixedRecords.containsKey(localType)) {
            return TypeKind.FIXED_RECORD;
        }
        if (arrays.containsKey(localType)) {
            return TypeKind.ARRAY;
        }
        return TypeKind.UNKNOWN;
    }

    public Optional<FixedRecordDef> fixedRecord(String typeName) {
        return Optional.ofNullable(fixedRecords.get(localName(typeName)));
    }

    public Optional<ArrayDef> array(String typeName) {
        return Optional.ofNullable(arrays.get(localName(typeName)));
    }

    public String primitiveType(String typeName) {
        String localType = localName(typeName);
        if (PRIMITIVE_TYPES.contains(localType)) {
            return localType;
        }
        String simple = simpleRepresentations.get(localType);
        if (simple != null) {
            return primitiveType(simple);
        }
        String enumerated = enumeratedRepresentations.get(localType);
        if (enumerated != null) {
            return primitiveType(enumerated);
        }
        return null;
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

    private static Map<String, String> parseRepresentations(Document document, String elementName) {
        Map<String, String> representations = new LinkedHashMap<>();
        NodeList nodes = document.getElementsByTagName(elementName);
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            String name = text(element, "name");
            String representation = text(element, "representation");
            if (name != null && representation != null) {
                representations.put(name, representation);
            }
        }
        return representations;
    }

    private static Map<String, FixedRecordDef> parseFixedRecords(Document document) {
        Map<String, FixedRecordDef> records = new LinkedHashMap<>();
        NodeList nodes = document.getElementsByTagName("fixedRecordData");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            String name = text(element, "name");
            if (name == null) {
                continue;
            }
            List<FieldDef> fields = new ArrayList<>();
            for (Element field : childElements(element, "field")) {
                String fieldName = text(field, "name");
                String fieldType = text(field, "dataType");
                if (fieldName != null && fieldType != null) {
                    fields.add(new FieldDef(fieldName, fieldType));
                }
            }
            records.put(name, new FixedRecordDef(name, fields));
        }
        return records;
    }

    private static Map<String, ArrayDef> parseArrays(Document document) {
        Map<String, ArrayDef> arrayDefs = new LinkedHashMap<>();
        NodeList nodes = document.getElementsByTagName("arrayData");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            String name = text(element, "name");
            String dataType = text(element, "dataType");
            if (name != null && dataType != null) {
                arrayDefs.put(name, new ArrayDef(name, dataType));
            }
        }
        return arrayDefs;
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

    public enum TypeKind {
        PRIMITIVE,
        SIMPLE,
        ENUMERATED,
        FIXED_RECORD,
        ARRAY,
        UNKNOWN
    }

    public record FieldDef(String name, String dataType) {
    }

    public record FixedRecordDef(String name, List<FieldDef> fields) {
        public FixedRecordDef {
            fields = List.copyOf(fields);
        }
    }

    public record ArrayDef(String name, String elementType) {
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

        private final Map<String, String> simpleRepresentations;
        private final Map<String, String> enumeratedRepresentations;
        private final Map<String, FixedRecordDef> fixedRecords;
        private final Map<String, ArrayDef> arrays;
        private final Map<String, ObjectClassDef> classesByName = new LinkedHashMap<>();
        private int nextClassId = 1;
        private int nextAttributeId = 1;

        private CatalogBuilder(
                Map<String, String> simpleRepresentations,
                Map<String, String> enumeratedRepresentations,
                Map<String, FixedRecordDef> fixedRecords,
                Map<String, ArrayDef> arrays) {
            this.simpleRepresentations = simpleRepresentations;
            this.enumeratedRepresentations = enumeratedRepresentations;
            this.fixedRecords = fixedRecords;
            this.arrays = arrays;
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
            FixedRecordDef fixedRecord = fixedRecords.get(localName(dataType));
            ArrayDef array = arrays.get(localName(dataType));
            boolean leaf = primitive != null || fixedRecord == null && array == null;

            attributes.add(new FomAttribute(
                    nextAttributeId++,
                    classId,
                    attributeName,
                    pathKey,
                    dataType,
                    primitive,
                    leaf));

            if (fixedRecord != null) {
                for (FieldDef field : fixedRecord.fields()) {
                    flattenAttribute(
                            classId,
                            attributeName,
                            pathKey + "." + field.name(),
                            field.dataType(),
                            attributes);
                }
            } else if (array != null) {
                flattenAttribute(
                        classId,
                        attributeName,
                        pathKey + "[]",
                        array.elementType(),
                        attributes);
            }
        }

        private String primitiveType(String dataType) {
            String localType = localName(dataType);
            if (PRIMITIVE_TYPES.contains(localType)) {
                return localType;
            }
            String simple = simpleRepresentations.get(localType);
            if (simple != null) {
                return primitiveType(simple);
            }
            String enumerated = enumeratedRepresentations.get(localType);
            if (enumerated != null) {
                return primitiveType(enumerated);
            }
            return null;
        }

        private record AttributeSource(String name, String dataType) {
        }
    }
}
