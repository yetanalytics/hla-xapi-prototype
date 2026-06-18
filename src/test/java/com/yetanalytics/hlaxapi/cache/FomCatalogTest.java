package com.yetanalytics.hlaxapi.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class FomCatalogTest {

    @Test
    void flattensPrimitiveAliasesEnumsAndFixedRecords() {
        FomCatalog catalog = FomCatalog.fromFile("config/fom.xml");
        FomCatalog.ObjectClassDef car = catalog.objectClass("Car").orElseThrow();

        assertTrue(car.attribute("FuelLevel").orElseThrow().leaf());
        assertEquals("HLAinteger32BE", car.attribute("FuelLevel").orElseThrow().primitiveType());
        assertEquals("HLAinteger32BE", car.attribute("FuelType").orElseThrow().primitiveType());

        FomCatalog.FomAttribute position = car.attribute("Position").orElseThrow();
        assertFalse(position.leaf());
        assertEquals("PositionRec", position.dataType());
        assertEquals("HLAfloat64BE", car.attribute("Position.Lat").orElseThrow().primitiveType());
        assertEquals("HLAfloat64BE", car.attribute("Position.Long").orElseThrow().primitiveType());
    }

    @Test
    void includesInheritedObjectAttributesAndArrayMetadata() {
        FomCatalog catalog = FomCatalog.fromFile("src/test/resources/SISO-STD-025.3-2024.xml");

        FomCatalog.ObjectClassDef network = catalog.objectClass("Network").orElseThrow();
        assertEquals("HLAASCIIstring", network.attribute("Name").orElseThrow().primitiveType());
        assertEquals("HLAASCIIstring", network.attribute("ObjectID.Value").orElseThrow().primitiveType());
        assertFalse(network.attribute("RelatedObjects").orElseThrow().leaf());

        FomCatalog.ObjectClassDef networkLink = catalog.objectClass("NetworkLink").orElseThrow();
        assertEquals("HLAASCIIstring", networkLink.attribute("NetworkInterfaces[].Name").orElseThrow().primitiveType());
    }

    @Test
    void convertsTargetsToPathKeys() {
        assertEquals("Position.Lat", FomCatalog.targetPath(List.of("Position", "Lat")));
        assertEquals("TargetModifiers[0].Key", FomCatalog.targetPath(List.of("TargetModifiers", 0, "Key")));
        assertEquals("TargetModifiers[].Key", FomCatalog.wildcardArrayIndexes("TargetModifiers[12].Key"));
    }
}
