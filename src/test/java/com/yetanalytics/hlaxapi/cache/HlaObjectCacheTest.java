package com.yetanalytics.hlaxapi.cache;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yetanalytics.hlaxapi.HLADecoderRegistry;
import com.yetanalytics.hlaxapi.config.model.ComparisonOperator;
import com.yetanalytics.hlaxapi.config.model.Criterion;
import com.yetanalytics.hlaxapi.config.model.LogicalExpression;
import com.yetanalytics.hlaxapi.config.model.LogicalOperator;
import com.yetanalytics.hlaxapi.config.model.Target;
import com.yetanalytics.hlaxapi.config.model.ValueExpression;
import hla.rti1516e.encoding.DataElement;
import hla.rti1516e.encoding.EncoderException;
import hla.rti1516e.encoding.EncoderFactory;
import hla.rti1516e.encoding.HLAfixedRecord;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.portico.impl.hla1516e.types.encoding.HLA1516eEncoderFactory;

class HlaObjectCacheTest {

    private final EncoderFactory encoderFactory = new HLA1516eEncoderFactory();
    private final FomCatalog catalog = FomCatalog.fromFile("config/fom.xml");
    private final HLADecoderRegistry decoderRegistry = new HLADecoderRegistry(encoderFactory);

    @Test
    void initializesSchemaAndSeedsFomMetadata() throws SQLException {
        try (HlaObjectCache cache = newCache()) {
            assertEquals(1, scalarLong(cache, "PRAGMA user_version"));
            assertTrue(count(cache, "SELECT COUNT(*) FROM fom_object_class") > 0);
            assertTrue(count(cache, "SELECT COUNT(*) FROM fom_attribute WHERE path_key = 'Position.Lat'") > 0);
        }
    }

    @Test
    void upsertsLatestScalarObjectStateAndKeepsRawBytes() throws SQLException {
        byte[] firstFuel = encoded(encoderFactory.createHLAinteger32BE(75));
        byte[] secondFuel = encoded(encoderFactory.createHLAinteger32BE(40));

        try (HlaObjectCache cache = newCache()) {
            cache.discoverObject("object-1", "Car One", "Car");
            cache.reflectAttributeValue("object-1", "Car", "FuelLevel", firstFuel);
            cache.reflectAttributeValue("object-1", "Car", "FuelLevel", secondFuel);

            assertEquals(40, cache.findCurrentValue("object-1", "FuelLevel").orElseThrow().value());
            assertEquals(1, count(cache, """
                    SELECT COUNT(*)
                    FROM object_attribute_current c
                    JOIN fom_attribute a ON a.id = c.attribute_id
                    WHERE a.path_key = 'FuelLevel'
                    """));
            assertArrayEquals(secondFuel, rawBytes(cache, "FuelLevel"));
        }
    }

    @Test
    void flattensFixedRecordValuesToNestedCurrentRows() {
        byte[] position = position(39.75d, -104.99d);

        try (HlaObjectCache cache = newCache()) {
            cache.discoverObject("object-1", "Car One", "Car");
            cache.reflectAttributeValue("object-1", "Car", "Position", position);

            assertEquals(39.75d, (Double) cache.findCurrentValue("object-1", "Position.Lat").orElseThrow().value());
            assertEquals(-104.99d, (Double) cache.findCurrentValue("object-1", "Position.Long").orElseThrow().value());
            assertTrue(cache.findCurrentValue("object-1", "Position").orElseThrow().value() instanceof java.util.Map);
        }
    }

    @Test
    void queryServiceEvaluatesCriteriaAndExcludesRemovedObjects() {
        try (HlaObjectCache cache = newCache()) {
            cache.discoverObject("object-1", "Car One", "Car");
            cache.reflectAttributeValue("object-1", "Car", "Name", encoded(encoderFactory.createHLAunicodeString(
                    "Car One")));
            cache.reflectAttributeValue("object-1", "Car", "FuelLevel", encoded(encoderFactory.createHLAinteger32BE(75)));
            cache.reflectAttributeValue("object-1", "Car", "Position", position(39.75d, -104.99d));

            cache.discoverObject("object-2", "Car Two", "Car");
            cache.reflectAttributeValue("object-2", "Car", "Name", encoded(encoderFactory.createHLAunicodeString(
                    "Car Two")));
            cache.reflectAttributeValue("object-2", "Car", "FuelLevel", encoded(encoderFactory.createHLAinteger32BE(20)));
            cache.reflectAttributeValue("object-2", "Car", "Position", position(40.0d, -105.0d));

            Criterion fuelCriteria = new Criterion(
                    new Target(List.of("FuelLevel")),
                    ComparisonOperator.GT,
                    new ValueExpression(50));
            Criterion latCriteria = new Criterion(
                    new Target(List.of("Position", "Lat")),
                    ComparisonOperator.LT,
                    new ValueExpression(40.0d));
            LogicalExpression criteria = new LogicalExpression(LogicalOperator.AND, List.of(fuelCriteria, latCriteria));

            assertEquals(
                    List.of("Car One"),
                    cache.queryService().findValues("Car", new Target(List.of("Name")), fuelCriteria));
            assertEquals(
                    List.of(-104.99d),
                    cache.queryService().findValues("Car", new Target(List.of("Position", "Long")), criteria));

            cache.removeObject("object-1");

            assertFalse(cache.queryService().findFirstValue("Car", new Target(List.of("FuelLevel")), criteria)
                    .isPresent());
        }
    }

    private HlaObjectCache newCache() {
        return new HlaObjectCache("jdbc:sqlite::memory:", catalog, decoderRegistry, encoderFactory);
    }

    private byte[] position(double lat, double lon) {
        HLAfixedRecord record = encoderFactory.createHLAfixedRecord();
        record.add(encoderFactory.createHLAfloat64BE(lat));
        record.add(encoderFactory.createHLAfloat64BE(lon));
        return encoded(record);
    }

    private byte[] encoded(DataElement element) {
        try {
            return element.toByteArray();
        } catch (EncoderException e) {
            throw new IllegalStateException("Could not encode test value", e);
        }
    }

    private long count(HlaObjectCache cache, String sql) throws SQLException {
        return scalarLong(cache, sql);
    }

    private long scalarLong(HlaObjectCache cache, String sql) throws SQLException {
        try (PreparedStatement statement = cache.connection().prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getLong(1) : 0L;
        }
    }

    private byte[] rawBytes(HlaObjectCache cache, String pathKey) throws SQLException {
        String sql = """
                SELECT c.raw_bytes
                FROM object_attribute_current c
                JOIN fom_attribute a ON a.id = c.attribute_id
                WHERE a.path_key = ?
                """;
        try (PreparedStatement statement = cache.connection().prepareStatement(sql)) {
            statement.setString(1, pathKey);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getBytes(1) : null;
            }
        }
    }
}
