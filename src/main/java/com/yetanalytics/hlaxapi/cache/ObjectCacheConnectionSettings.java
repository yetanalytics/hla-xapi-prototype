package com.yetanalytics.hlaxapi.cache;

import com.yetanalytics.hlaxapi.config.XapiConfig;
import com.yetanalytics.hlaxapi.config.model.ObjectCacheBackend;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

record ObjectCacheConnectionSettings(
        ObjectCacheBackend backend,
        String jdbcUrl,
        String username,
        String password,
        String schema) {

    static final String DEFAULT_SQLITE_PATH = "hla-object-cache.sqlite";
    static final String DEFAULT_POSTGRESQL_SCHEMA = "hla_object_cache";
    private static final Pattern SQL_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    static ObjectCacheConnectionSettings from(XapiConfig config, Map<String, String> environment) {
        Objects.requireNonNull(environment, "environment");
        ObjectCacheBackend backend = config != null
                        && config.objectCacheConfig != null
                        && config.objectCacheConfig.backend != null
                ? config.objectCacheConfig.backend
                : ObjectCacheBackend.SQLITE;
        return switch (backend) {
            case SQLITE -> sqlite(environment);
            case POSTGRESQL -> postgresql(environment);
        };
    }

    static ObjectCacheConnectionSettings sqlite(String jdbcUrl) {
        return validated(ObjectCacheBackend.SQLITE, jdbcUrl, null, null, null);
    }

    static ObjectCacheConnectionSettings postgresql(
            String jdbcUrl,
            String username,
            String password,
            String schema) {
        return validated(ObjectCacheBackend.POSTGRESQL, jdbcUrl, username, password, schema);
    }

    static String defaultSqliteJdbcUrl(Map<String, String> environment) {
        String configured = trimmed(environment.get("HLA_OBJECT_CACHE_JDBC_URL"));
        if (configured != null) {
            return configured;
        }
        String path = trimmed(environment.get("HLA_OBJECT_CACHE_DB"));
        return "jdbc:sqlite:" + (path == null ? DEFAULT_SQLITE_PATH : path);
    }

    private static ObjectCacheConnectionSettings sqlite(Map<String, String> environment) {
        return validated(
                ObjectCacheBackend.SQLITE,
                defaultSqliteJdbcUrl(environment),
                null,
                null,
                null);
    }

    private static ObjectCacheConnectionSettings postgresql(Map<String, String> environment) {
        String jdbcUrl = trimmed(environment.get("HLA_OBJECT_CACHE_JDBC_URL"));
        if (jdbcUrl == null) {
            throw new IllegalArgumentException(
                    "HLA_OBJECT_CACHE_JDBC_URL is required for the PostgreSQL object cache backend");
        }
        return validated(
                ObjectCacheBackend.POSTGRESQL,
                jdbcUrl,
                trimmed(environment.get("HLA_OBJECT_CACHE_USERNAME")),
                trimmed(environment.get("HLA_OBJECT_CACHE_PASSWORD")),
                Objects.requireNonNullElse(
                        trimmed(environment.get("HLA_OBJECT_CACHE_SCHEMA")),
                        DEFAULT_POSTGRESQL_SCHEMA));
    }

    private static ObjectCacheConnectionSettings validated(
            ObjectCacheBackend backend,
            String jdbcUrl,
            String username,
            String password,
            String schema) {
        Objects.requireNonNull(backend, "backend");
        Objects.requireNonNull(jdbcUrl, "jdbcUrl");
        String requiredPrefix = switch (backend) {
            case SQLITE -> "jdbc:sqlite:";
            case POSTGRESQL -> "jdbc:postgresql:";
        };
        if (!jdbcUrl.startsWith(requiredPrefix)) {
            throw new IllegalArgumentException(
                    "Object cache JDBC URL must start with " + requiredPrefix + " for backend " + backend);
        }
        if ((username == null) != (password == null)) {
            throw new IllegalArgumentException(
                    "HLA_OBJECT_CACHE_USERNAME and HLA_OBJECT_CACHE_PASSWORD must be provided together");
        }
        if (backend == ObjectCacheBackend.POSTGRESQL
                && (schema == null || !SQL_IDENTIFIER.matcher(schema).matches())) {
            throw new IllegalArgumentException("Invalid PostgreSQL object cache schema: " + schema);
        }
        return new ObjectCacheConnectionSettings(backend, jdbcUrl, username, password, schema);
    }

    private static String trimmed(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
