package com.yetanalytics.hlaxapi.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.yetanalytics.hlaxapi.config.XapiConfig;
import com.yetanalytics.hlaxapi.config.model.ObjectCacheBackend;
import com.yetanalytics.hlaxapi.config.model.ObjectCacheConfig;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ObjectCacheConnectionSettingsTest {

    @Test
    void defaultsToSqliteDatabasePath() {
        ObjectCacheConnectionSettings settings = ObjectCacheConnectionSettings.from(new XapiConfig(), Map.of());

        assertEquals(ObjectCacheBackend.SQLITE, settings.backend());
        assertEquals("jdbc:sqlite:hla-object-cache.sqlite", settings.jdbcUrl());
    }

    @Test
    void supportsSqlitePathAndJdbcUrlOverrides() {
        assertEquals(
                "jdbc:sqlite:/tmp/cache.sqlite",
                ObjectCacheConnectionSettings.from(
                        new XapiConfig(),
                        Map.of("HLA_OBJECT_CACHE_DB", "/tmp/cache.sqlite"))
                        .jdbcUrl());
        assertEquals(
                "jdbc:sqlite::memory:",
                ObjectCacheConnectionSettings.from(
                        new XapiConfig(),
                        Map.of("HLA_OBJECT_CACHE_JDBC_URL", "jdbc:sqlite::memory:"))
                        .jdbcUrl());
    }

    @Test
    void resolvesPostgresqlConnectionSettings() {
        XapiConfig config = config(ObjectCacheBackend.POSTGRESQL);
        Map<String, String> environment = Map.of(
                "HLA_OBJECT_CACHE_JDBC_URL", "jdbc:postgresql://localhost/cache",
                "HLA_OBJECT_CACHE_USERNAME", "cache_user",
                "HLA_OBJECT_CACHE_PASSWORD", "secret",
                "HLA_OBJECT_CACHE_SCHEMA", "adapter_cache");

        ObjectCacheConnectionSettings settings = ObjectCacheConnectionSettings.from(config, environment);

        assertEquals(ObjectCacheBackend.POSTGRESQL, settings.backend());
        assertEquals("jdbc:postgresql://localhost/cache", settings.jdbcUrl());
        assertEquals("cache_user", settings.username());
        assertEquals("secret", settings.password());
        assertEquals("adapter_cache", settings.schema());
    }

    @Test
    void postgresqlRequiresJdbcUrl() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ObjectCacheConnectionSettings.from(config(ObjectCacheBackend.POSTGRESQL), Map.of()));
    }

    @Test
    void rejectsBackendUrlMismatch() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ObjectCacheConnectionSettings.from(
                        config(ObjectCacheBackend.POSTGRESQL),
                        Map.of("HLA_OBJECT_CACHE_JDBC_URL", "jdbc:sqlite:cache.sqlite")));
    }

    @Test
    void rejectsIncompleteCredentialPair() {
        Map<String, String> environment = new HashMap<>();
        environment.put("HLA_OBJECT_CACHE_JDBC_URL", "jdbc:postgresql://localhost/cache");
        environment.put("HLA_OBJECT_CACHE_USERNAME", "cache_user");

        assertThrows(
                IllegalArgumentException.class,
                () -> ObjectCacheConnectionSettings.from(config(ObjectCacheBackend.POSTGRESQL), environment));
    }

    @Test
    void rejectsUnsafePostgresqlSchema() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ObjectCacheConnectionSettings.from(
                        config(ObjectCacheBackend.POSTGRESQL),
                        Map.of(
                                "HLA_OBJECT_CACHE_JDBC_URL", "jdbc:postgresql://localhost/cache",
                                "HLA_OBJECT_CACHE_SCHEMA", "cache;drop schema public")));
    }

    private XapiConfig config(ObjectCacheBackend backend) {
        ObjectCacheConfig objectCacheConfig = new ObjectCacheConfig();
        objectCacheConfig.backend = backend;
        XapiConfig config = new XapiConfig();
        config.objectCacheConfig = objectCacheConfig;
        return config;
    }
}
