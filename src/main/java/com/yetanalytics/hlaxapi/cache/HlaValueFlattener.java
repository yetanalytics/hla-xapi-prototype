package com.yetanalytics.hlaxapi.cache;

import com.yetanalytics.hlaxapi.HLADecoderRegistry;
import hla.rti1516e.encoding.DataElement;
import hla.rti1516e.encoding.DecoderException;
import hla.rti1516e.encoding.EncoderException;
import hla.rti1516e.encoding.EncoderFactory;
import hla.rti1516e.encoding.HLAfixedRecord;
import hla.rti1516e.encoding.HLAvariableArray;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class HlaValueFlattener {

    private final FomCatalog catalog;
    private final HLADecoderRegistry decoderRegistry;
    private final EncoderFactory encoderFactory;

    public HlaValueFlattener(
            FomCatalog catalog,
            HLADecoderRegistry decoderRegistry,
            EncoderFactory encoderFactory) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.decoderRegistry = Objects.requireNonNull(decoderRegistry, "decoderRegistry");
        this.encoderFactory = Objects.requireNonNull(encoderFactory, "encoderFactory");
    }

    public List<DecodedAttributeValue> flatten(String attributeName, String dataType, byte[] bytes) {
        Objects.requireNonNull(attributeName, "attributeName");
        Objects.requireNonNull(dataType, "dataType");
        Objects.requireNonNull(bytes, "bytes");
        try {
            DataElement element = createElement(dataType);
            element.decode(bytes);
            List<DecodedAttributeValue> values = new ArrayList<>();
            Object value = extractValue(attributeName, dataType, element, values);
            String primitiveType = catalog.primitiveType(dataType);
            if (primitiveType != null) {
                return values;
            }
            values.add(0, new DecodedAttributeValue(
                    attributeName,
                    dataType,
                    primitiveType,
                    value,
                    bytes,
                    primitiveType != null));
            return values;
        } catch (DecoderException | EncoderException | IllegalArgumentException e) {
            return List.of(new DecodedAttributeValue(attributeName, dataType, null, null, bytes, false));
        }
    }

    private Object extractValue(
            String pathKey,
            String dataType,
            DataElement element,
            List<DecodedAttributeValue> values) throws DecoderException, EncoderException {
        String primitiveType = catalog.primitiveType(dataType);
        if (primitiveType != null) {
            Object value = decoderRegistry.decode(primitiveType, element.toByteArray());
            values.add(new DecodedAttributeValue(
                    pathKey,
                    dataType,
                    primitiveType,
                    value,
                    element.toByteArray(),
                    true));
            return value;
        }

        FomCatalog.FixedRecordDef fixedRecord = catalog.fixedRecord(dataType).orElse(null);
        if (fixedRecord != null) {
            HLAfixedRecord record = (HLAfixedRecord) element;
            Map<String, Object> fields = new LinkedHashMap<>();
            for (int i = 0; i < fixedRecord.fields().size(); i++) {
                FomCatalog.FieldDef field = fixedRecord.fields().get(i);
                String fieldPath = pathKey + "." + field.name();
                fields.put(field.name(), extractValue(fieldPath, field.dataType(), record.get(i), values));
            }
            return fields;
        }

        FomCatalog.ArrayDef array = catalog.array(dataType).orElse(null);
        if (array != null && element instanceof HLAvariableArray<?> variableArray) {
            List<Object> arrayValues = new ArrayList<>();
            int index = 0;
            for (DataElement child : variableArray) {
                String childPath = pathKey + "[" + index + "]";
                arrayValues.add(extractValue(childPath, array.elementType(), child, values));
                index++;
            }
            return arrayValues;
        }

        return null;
    }

    private DataElement createElement(String dataType) {
        String primitiveType = catalog.primitiveType(dataType);
        if (primitiveType != null) {
            return createPrimitiveElement(primitiveType);
        }

        FomCatalog.FixedRecordDef fixedRecord = catalog.fixedRecord(dataType).orElse(null);
        if (fixedRecord != null) {
            HLAfixedRecord record = encoderFactory.createHLAfixedRecord();
            for (FomCatalog.FieldDef field : fixedRecord.fields()) {
                record.add(createElement(field.dataType()));
            }
            return record;
        }

        FomCatalog.ArrayDef array = catalog.array(dataType).orElse(null);
        if (array != null) {
            return encoderFactory.createHLAvariableArray(index -> createElement(array.elementType()));
        }

        throw new IllegalArgumentException("Unsupported FOM data type " + dataType);
    }

    private DataElement createPrimitiveElement(String primitiveType) {
        return switch (primitiveType) {
            case "HLAASCIIchar" -> encoderFactory.createHLAASCIIchar();
            case "HLAASCIIstring" -> encoderFactory.createHLAASCIIstring();
            case "HLAboolean" -> encoderFactory.createHLAboolean();
            case "HLAbyte" -> encoderFactory.createHLAbyte();
            case "HLAfloat32BE" -> encoderFactory.createHLAfloat32BE();
            case "HLAfloat32LE" -> encoderFactory.createHLAfloat32LE();
            case "HLAfloat64BE" -> encoderFactory.createHLAfloat64BE();
            case "HLAfloat64LE" -> encoderFactory.createHLAfloat64LE();
            case "HLAinteger16BE" -> encoderFactory.createHLAinteger16BE();
            case "HLAinteger16LE" -> encoderFactory.createHLAinteger16LE();
            case "HLAinteger32BE" -> encoderFactory.createHLAinteger32BE();
            case "HLAinteger32LE" -> encoderFactory.createHLAinteger32LE();
            case "HLAinteger64BE" -> encoderFactory.createHLAinteger64BE();
            case "HLAinteger64LE" -> encoderFactory.createHLAinteger64LE();
            case "HLAoctet" -> encoderFactory.createHLAoctet();
            case "HLAoctetPairBE" -> encoderFactory.createHLAoctetPairBE();
            case "HLAoctetPairLE" -> encoderFactory.createHLAoctetPairLE();
            case "HLAopaqueData" -> encoderFactory.createHLAopaqueData();
            case "HLAunicodeChar" -> encoderFactory.createHLAunicodeChar();
            case "HLAunicodeString" -> encoderFactory.createHLAunicodeString();
            default -> throw new IllegalArgumentException("Unsupported HLA primitive type " + primitiveType);
        };
    }
}
