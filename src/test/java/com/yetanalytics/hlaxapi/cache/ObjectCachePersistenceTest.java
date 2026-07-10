package com.yetanalytics.hlaxapi.cache;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yetanalytics.hlaxapi.FOMXML;
import com.yetanalytics.hlaxapi.HLADecoderRegistry;
import com.yetanalytics.hlaxapi.SimulationConfig;
import com.yetanalytics.hlaxapi.config.XapiConfig;
import com.yetanalytics.hlaxapi.config.model.ComparisonOperator;
import com.yetanalytics.hlaxapi.config.model.Criterion;
import com.yetanalytics.hlaxapi.config.model.LogicalExpression;
import com.yetanalytics.hlaxapi.config.model.LogicalOperator;
import com.yetanalytics.hlaxapi.config.model.ObjectCacheConfig;
import com.yetanalytics.hlaxapi.config.model.Target;
import com.yetanalytics.hlaxapi.config.model.TrackedObject;
import com.yetanalytics.hlaxapi.config.model.ValueExpression;
import hla.rti1516e.encoding.DataElement;
import hla.rti1516e.encoding.EncoderException;
import hla.rti1516e.encoding.EncoderFactory;
import hla.rti1516e.encoding.HLAfixedRecord;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.portico.impl.hla1516e.types.encoding.HLA1516eEncoderFactory;

final class ObjectCachePersistenceTest {

    private final EncoderFactory encoderFactory = new HLA1516eEncoderFactory();
    private final HLADecoderRegistry decoderRegistry = new HLADecoderRegistry(encoderFactory);
    private final FOMXML fomXml = new FOMXML(
            new SimulationConfig(null, null, null, null, "config/HlaFedereplFOM.xml"),
            decoderRegistry);
    private final FomCatalog catalog = new FomCatalog(fomXml);

    @Test
    void initializesSchemaAndSeedsFomMetadata() throws SQLException {
        try (ObjectCache cache = newCache()) {
            assertEquals(1, scalarLong(cache, "PRAGMA user_version"));
            assertTrue(count(cache, "SELECT COUNT(*) FROM fom_object_class") > 0);
            assertTrue(count(cache, "SELECT COUNT(*) FROM fom_attribute WHERE path_key = 'Position.X'") > 0);
        }
    }

    @Test
    void upsertsLatestScalarObjectStateAndKeepsRawBytes() throws SQLException {
        byte[] firstHunger = encoded(encoderFactory.createHLAinteger32BE(75));
        byte[] secondHunger = encoded(encoderFactory.createHLAinteger32BE(40));

        try (ObjectCache cache = newCache()) {
            cache.discoverObject("object-1", "Rabbit One", "Rabbit");
            cache.reflectAttributeValue("object-1", "Rabbit", "Hunger", firstHunger);
            cache.reflectAttributeValue("object-1", "Rabbit", "Hunger", secondHunger);

            assertEquals(40, cache.findCurrentValue("object-1", "Hunger").orElseThrow().value());
            assertEquals(1, count(cache, """
                    SELECT COUNT(*)
                    FROM object_attribute_current c
                    JOIN fom_attribute a ON a.id = c.attribute_id
                    WHERE a.path_key = 'Hunger'
                    """));
            assertArrayEquals(secondHunger, rawBytes(cache, "Hunger"));
        }
    }

    @Test
    void flattensFixedRecordValuesToNestedCurrentRows() {
        byte[] position = position(12, 8);

        try (ObjectCache cache = newCache()) {
            cache.discoverObject("object-1", "Rabbit One", "Rabbit");
            cache.reflectAttributeValue("object-1", "Rabbit", "Position", position);

            assertEquals(12, cache.findCurrentValue("object-1", "Position.X").orElseThrow().value());
            assertEquals(8, cache.findCurrentValue("object-1", "Position.Y").orElseThrow().value());
            assertTrue(cache.findCurrentValue("object-1", "Position").orElseThrow().value() instanceof java.util.Map);
        }
    }

    @Test
    void queryServiceEvaluatesCriteriaAndExcludesRemovedObjects() {
        try (ObjectCache cache = newCache()) {
            cache.discoverObject("object-1", "Rabbit One", "Rabbit");
            cache.reflectAttributeValue("object-1", "Rabbit", "EntityId", encoded(encoderFactory.createHLAASCIIstring(
                    "rabbit-one")));
            cache.reflectAttributeValue("object-1", "Rabbit", "Hunger", encoded(encoderFactory.createHLAinteger32BE(75)));
            cache.reflectAttributeValue("object-1", "Rabbit", "Position", position(12, 8));

            cache.discoverObject("object-2", "Rabbit Two", "Rabbit");
            cache.reflectAttributeValue("object-2", "Rabbit", "EntityId", encoded(encoderFactory.createHLAASCIIstring(
                    "rabbit-two")));
            cache.reflectAttributeValue("object-2", "Rabbit", "Hunger", encoded(encoderFactory.createHLAinteger32BE(20)));
            cache.reflectAttributeValue("object-2", "Rabbit", "Position", position(20, 5));

            Criterion hungerCriteria = new Criterion(
                    new Target(List.of("Hunger")),
                    ComparisonOperator.GT,
                    new ValueExpression(50));
            Criterion xCriteria = new Criterion(
                    new Target(List.of("Position", "X")),
                    ComparisonOperator.LT,
                    new ValueExpression(15));
            LogicalExpression criteria = new LogicalExpression(LogicalOperator.AND, List.of(hungerCriteria, xCriteria));

            assertEquals(
                    List.of("rabbit-one"),
                    cache.queryService().findValues("Rabbit", new Target(List.of("EntityId")), hungerCriteria));
            assertEquals(
                    List.of(8),
                    cache.queryService().findValues("Rabbit", new Target(List.of("Position", "Y")), criteria));
            CachedObject matched = cache.queryService().findFirstObject("Rabbit", criteria).orElseThrow();
            assertEquals("object-1", matched.objectHandle());
            assertEquals(
                    8,
                    cache.queryService().findValue(matched, new Target(List.of("Position", "Y"))).orElseThrow());

            cache.removeObject("object-1");

            assertFalse(cache.queryService().findFirstValue("Rabbit", new Target(List.of("Hunger")), criteria)
                    .isPresent());
        }
    }

    @Test
    void queryServiceDistinguishesPresentNullFromMissingValue() {
        try (ObjectCache cache = newCache()) {
            cache.discoverObject("object-1", "Rabbit One", "Rabbit");
            cache.reflectAttributeValue("object-1", "Rabbit", "Hunger", new byte[] { 1 });
            CachedObject matched = cache.queryService().findFirstObject("Rabbit", null).orElseThrow();

            ValueResolution presentNull = cache.queryService().findValueResolution(
                    matched,
                    new Target(List.of("Hunger")));
            ValueResolution missingValue = cache.queryService().findValueResolution(
                    matched,
                    new Target(List.of("Position", "X")));

            assertEquals(ValueResolution.Status.PRESENT, presentNull.status());
            assertNull(presentNull.value());
            assertEquals(ValueResolution.Status.MISSING_VALUE, missingValue.status());
        }
    }

    @Test
    void persistentCacheStartsFreshOnInitialization(@TempDir Path tempDir) throws SQLException {
        String jdbcUrl = "jdbc:sqlite:" + tempDir.resolve("cache.sqlite");

        try (ObjectCache cache = newCache(jdbcUrl)) {
            cache.discoverObject("object-1", "Rabbit One", "Rabbit");
            cache.reflectAttributeValue("object-1", "Rabbit", "Hunger",
                    encoded(encoderFactory.createHLAinteger32BE(75)));

            assertEquals(1, count(cache, "SELECT COUNT(*) FROM object_instance"));
            assertEquals(1, count(cache, "SELECT COUNT(*) FROM object_attribute_current"));
        }

        try (ObjectCache cache = newCache(jdbcUrl)) {
            assertEquals(0, count(cache, "SELECT COUNT(*) FROM object_instance"));
            assertEquals(0, count(cache, "SELECT COUNT(*) FROM object_attribute_current"));
            assertTrue(count(cache, "SELECT COUNT(*) FROM fom_object_class") > 0);
        }
    }

    private ObjectCache newCache() {
        return newCache("jdbc:sqlite::memory:");
    }

    private ObjectCache newCache(String jdbcUrl) {
        return new ObjectCache(enabledConfig(), catalog, fomXml, decoderRegistry, jdbcUrl);
    }

    private XapiConfig enabledConfig() {
        TrackedObject trackedObject = new TrackedObject();
        trackedObject.clazz = "Rabbit";
        trackedObject.allAttributes = true;
        ObjectCacheConfig objectCacheConfig = new ObjectCacheConfig();
        objectCacheConfig.trackedObjects = List.of(trackedObject);
        XapiConfig config = new XapiConfig();
        config.objectCacheConfig = objectCacheConfig;
        return config;
    }

    private byte[] position(int x, int y) {
        HLAfixedRecord record = encoderFactory.createHLAfixedRecord();
        record.add(encoderFactory.createHLAinteger32BE(x));
        record.add(encoderFactory.createHLAinteger32BE(y));
        return encoded(record);
    }

    private byte[] encoded(DataElement element) {
        try {
            return element.toByteArray();
        } catch (EncoderException e) {
            throw new IllegalStateException("Could not encode test value", e);
        }
    }

    private long count(ObjectCache cache, String sql) throws SQLException {
        return scalarLong(cache, sql);
    }

    private long scalarLong(ObjectCache cache, String sql) throws SQLException {
        try (PreparedStatement statement = cache.connection().prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getLong(1) : 0L;
        }
    }

    private byte[] rawBytes(ObjectCache cache, String pathKey) throws SQLException {
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
