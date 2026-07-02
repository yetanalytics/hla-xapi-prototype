package com.yetanalytics.hlaxapi.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yetanalytics.hlaxapi.FOMXML;
import com.yetanalytics.hlaxapi.HLADecoderRegistry;
import com.yetanalytics.hlaxapi.SimulationConfig;
import com.yetanalytics.hlaxapi.config.XapiConfig;
import com.yetanalytics.hlaxapi.config.model.ComparisonOperator;
import com.yetanalytics.hlaxapi.config.model.Criterion;
import com.yetanalytics.hlaxapi.config.model.StatementTrigger;
import com.yetanalytics.hlaxapi.config.model.Target;
import com.yetanalytics.hlaxapi.config.model.ValueExpression;
import hla.rti1516e.encoding.DataElement;
import hla.rti1516e.encoding.EncoderException;
import hla.rti1516e.encoding.EncoderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.portico.impl.hla1516e.types.encoding.HLA1516eEncoderFactory;

class ObjectCacheTest {

    private final EncoderFactory encoderFactory = new HLA1516eEncoderFactory();
    private final HLADecoderRegistry decoderRegistry = new HLADecoderRegistry(encoderFactory);
    private final FOMXML fomXml = new FOMXML(
            new SimulationConfig(null, null, null, null, "config/HlaFedereplFOM.xml"),
            decoderRegistry);
    private final FomCatalog catalog = new FomCatalog(fomXml);

    @Test
    void disabledWhenNoQueryInjectionsAndDoesNotOpenSqlite(@TempDir Path tempDir) {
        Path databasePath = tempDir.resolve("disabled.sqlite");

        try (ObjectCache cache = new ObjectCache(
                new XapiConfig(),
                catalog,
                fomXml,
                decoderRegistry,
                "jdbc:sqlite:" + databasePath)) {
            cache.discoverObject("object-1", "Rabbit One", "Rabbit");
            cache.reflectAttributeValue("object-1", "Rabbit", "Hunger", encoded(encoderFactory.createHLAinteger32BE(
                    75)));
            cache.removeObject("object-1");

            assertFalse(cache.isEnabled());
            assertTrue(cache.subscriptions().isEmpty());
            assertFalse(cache.findFirstValue("Rabbit", new Target(List.of("Hunger")), null).isPresent());
            assertFalse(Files.exists(databasePath));
        }
    }

    @Test
    void enabledWhenQueryInjectionsExistAndCanQueryReflectedValues(@TempDir Path tempDir) {
        Path databasePath = tempDir.resolve("enabled.sqlite");

        try (ObjectCache cache = new ObjectCache(
                configWithQuery(),
                catalog,
                fomXml,
                decoderRegistry,
                "jdbc:sqlite:" + databasePath)) {
            cache.discoverObject("object-1", "Rabbit One", "Rabbit");
            cache.reflectAttributeValue("object-1", "Rabbit", "EntityId", encoded(encoderFactory
                    .createHLAASCIIstring("rabbit-one")));
            cache.reflectAttributeValue("object-1", "Rabbit", "Hunger", encoded(encoderFactory.createHLAinteger32BE(
                    75)));

            Criterion criteria = new Criterion(
                    new Target(List.of("Hunger")),
                    ComparisonOperator.GT,
                    new ValueExpression(50));

            assertTrue(cache.isEnabled());
            assertEquals(Set.of("EntityId", "Hunger"), cache.subscriptions().get("Rabbit"));
            assertEquals(
                    "rabbit-one",
                    cache.findFirstValue("Rabbit", new Target(List.of("EntityId")), criteria).orElseThrow());
            assertTrue(Files.exists(databasePath));
        }
    }

    private XapiConfig configWithQuery() {
        StatementTrigger trigger = new StatementTrigger();
        trigger.statement = """
                {"actor":{"name":["query","Rabbit",["EntityId"],[["Hunger"],">",50]]}}
                """;

        XapiConfig config = new XapiConfig();
        config.statementTriggers = List.of(trigger);
        return config;
    }

    private byte[] encoded(DataElement element) {
        try {
            return element.toByteArray();
        } catch (EncoderException e) {
            throw new IllegalStateException("Could not encode test value", e);
        }
    }
}
