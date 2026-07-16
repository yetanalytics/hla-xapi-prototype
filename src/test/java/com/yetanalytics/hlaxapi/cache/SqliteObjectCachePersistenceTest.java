package com.yetanalytics.hlaxapi.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SqliteObjectCachePersistenceTest extends ObjectCachePersistenceTest {

    @TempDir
    private Path tempDir;

    @Override
    protected ObjectCache newCache(String name) {
        return new ObjectCache(
                enabledConfig(),
                catalog,
                fomXml,
                decoderRegistry,
                "jdbc:sqlite:" + tempDir.resolve(name + ".sqlite"));
    }

    @Test
    void configuresSqliteSchemaVersionAndForeignKeys() throws SQLException {
        try (ObjectCache cache = newCache()) {
            assertEquals(1, scalarLong(cache, "PRAGMA user_version"));
            assertEquals(1, scalarLong(cache, "PRAGMA foreign_keys"));
        }
    }
}
