package com.yetanalytics.hlaxapi.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yetanalytics.hlaxapi.HLADecoderRegistry;
import hla.rti1516e.encoding.EncoderFactory;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class HlaObjectCache implements AutoCloseable {

    private static final int SCHEMA_VERSION = 1;

    private final Connection connection;
    private final FomCatalog catalog;
    private final HlaValueFlattener valueFlattener;
    private final CacheQueryService queryService;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicLong sequence = new AtomicLong();

    public HlaObjectCache(
            String jdbcUrl,
            FomCatalog catalog,
            HLADecoderRegistry decoderRegistry,
            EncoderFactory encoderFactory) {
        try {
            this.connection = DriverManager.getConnection(Objects.requireNonNull(jdbcUrl, "jdbcUrl"));
            this.catalog = Objects.requireNonNull(catalog, "catalog");
            this.valueFlattener = new HlaValueFlattener(catalog, decoderRegistry, encoderFactory);
            this.queryService = new CacheQueryService(this);
            initializeSchema();
            seedFomMetadata();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not initialize HLA object cache", e);
        }
    }

    public static String defaultJdbcUrl() {
        String configured = System.getenv("HLA_OBJECT_CACHE_JDBC_URL");
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        String path = System.getenv().getOrDefault("HLA_OBJECT_CACHE_DB", "hla-object-cache.sqlite");
        return "jdbc:sqlite:" + path;
    }

    public FomCatalog catalog() {
        return catalog;
    }

    public CacheQueryService queryService() {
        return queryService;
    }

    public void discoverObject(String objectHandle, String objectName, String className) {
        FomCatalog.ObjectClassDef clazz = requireClass(className);
        ensureObject(objectHandle, objectName, clazz.localName());
    }

    public void removeObject(String objectHandle) {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE object_instance SET removed_at = ? WHERE object_handle = ?")) {
            statement.setString(1, Instant.now().toString());
            statement.setString(2, objectHandle);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not mark object removed: " + objectHandle, e);
        }
    }

    public void reflectAttributeValue(
            String objectHandle,
            String className,
            String attributeName,
            byte[] bytes) {
        FomCatalog.ObjectClassDef clazz = requireClass(className);
        CachedObject object = ensureObject(objectHandle, null, clazz.localName());
        FomCatalog.FomAttribute topAttribute = clazz.attribute(attributeName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No FOM attribute " + attributeName + " on object class " + className));
        List<DecodedAttributeValue> values = valueFlattener.flatten(attributeName, topAttribute.dataType(), bytes);
        String observedAt = Instant.now().toString();
        long observedSequence = sequence.incrementAndGet();
        for (DecodedAttributeValue value : values) {
            attributeIdForPath(clazz, value.pathKey()).ifPresent(attributeId -> upsertCurrentValue(
                    object.id(),
                    attributeId,
                    value,
                    observedAt,
                    observedSequence));
        }
    }

    public Optional<CachedValue> findCurrentValue(long instanceId, String pathKey) {
        String normalizedPath = FomCatalog.wildcardArrayIndexes(pathKey);
        String sql = """
                SELECT c.value_type, c.value_json, c.value_blob, c.raw_bytes
                FROM object_attribute_current c
                JOIN fom_attribute a ON a.id = c.attribute_id
                WHERE c.instance_id = ? AND (a.path_key = ? OR a.path_key = ?)
                ORDER BY CASE WHEN a.path_key = ? THEN 0 ELSE 1 END
                LIMIT 1
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, instanceId);
            statement.setString(2, pathKey);
            statement.setString(3, normalizedPath);
            statement.setString(4, pathKey);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(readCachedValue(resultSet));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read current cached value", e);
        }
    }

    public Optional<CachedValue> findCurrentValue(String objectHandle, String pathKey) {
        String sql = """
                SELECT id
                FROM object_instance
                WHERE object_handle = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, objectHandle);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return findCurrentValue(resultSet.getLong("id"), pathKey);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read object handle: " + objectHandle, e);
        }
    }

    public List<CachedObject> currentObjects(String className) {
        FomCatalog.ObjectClassDef clazz = requireClass(className);
        String sql = """
                SELECT id, object_handle, object_name
                FROM object_instance
                WHERE class_id = ? AND removed_at IS NULL
                ORDER BY id
                """;
        List<CachedObject> objects = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, clazz.id());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    objects.add(new CachedObject(
                            resultSet.getLong("id"),
                            resultSet.getString("object_handle"),
                            resultSet.getString("object_name"),
                            clazz.localName()));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not list cached objects for class " + className, e);
        }
        return objects;
    }

    Connection connection() {
        return connection;
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not close HLA object cache", e);
        }
    }

    private FomCatalog.ObjectClassDef requireClass(String className) {
        return catalog.objectClass(className)
                .orElseThrow(() -> new IllegalArgumentException("No FOM object class " + className));
    }

    private CachedObject ensureObject(String objectHandle, String objectName, String className) {
        FomCatalog.ObjectClassDef clazz = requireClass(className);
        String sql = """
                INSERT INTO object_instance (object_handle, object_name, class_id, discovered_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(object_handle) DO UPDATE SET
                    object_name = COALESCE(excluded.object_name, object_instance.object_name),
                    class_id = excluded.class_id,
                    removed_at = NULL
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, Objects.requireNonNull(objectHandle, "objectHandle"));
            statement.setString(2, objectName);
            statement.setInt(3, clazz.id());
            statement.setString(4, Instant.now().toString());
            statement.executeUpdate();
            return loadObject(objectHandle, clazz.localName());
        } catch (SQLException e) {
            throw new IllegalStateException("Could not upsert object instance " + objectHandle, e);
        }
    }

    private CachedObject loadObject(String objectHandle, String className) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, object_name FROM object_instance WHERE object_handle = ?")) {
            statement.setString(1, objectHandle);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("Object instance was not inserted: " + objectHandle);
                }
                return new CachedObject(
                        resultSet.getLong("id"),
                        objectHandle,
                        resultSet.getString("object_name"),
                        className);
            }
        }
    }

    private void initializeSchema() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS fom_object_class (
                        id INTEGER PRIMARY KEY,
                        hla_name TEXT NOT NULL,
                        local_name TEXT NOT NULL UNIQUE,
                        parent_name TEXT
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS fom_attribute (
                        id INTEGER PRIMARY KEY,
                        class_id INTEGER NOT NULL REFERENCES fom_object_class(id),
                        attribute_name TEXT NOT NULL,
                        path_key TEXT NOT NULL,
                        data_type TEXT NOT NULL,
                        primitive_type TEXT,
                        is_leaf INTEGER NOT NULL,
                        UNIQUE(class_id, path_key)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS object_instance (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        object_handle TEXT NOT NULL UNIQUE,
                        object_name TEXT,
                        class_id INTEGER NOT NULL REFERENCES fom_object_class(id),
                        discovered_at TEXT NOT NULL,
                        removed_at TEXT
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS object_attribute_current (
                        instance_id INTEGER NOT NULL REFERENCES object_instance(id),
                        attribute_id INTEGER NOT NULL REFERENCES fom_attribute(id),
                        value_type TEXT NOT NULL,
                        value_text TEXT,
                        value_num REAL,
                        value_bool INTEGER,
                        value_blob BLOB,
                        value_json TEXT,
                        raw_bytes BLOB,
                        observed_at TEXT NOT NULL,
                        sequence INTEGER NOT NULL,
                        PRIMARY KEY(instance_id, attribute_id)
                    )
                    """);
            statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_object_handle ON object_instance(object_handle)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_fom_attribute_class_path "
                    + "ON fom_attribute(class_id, path_key)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_current_text "
                    + "ON object_attribute_current(attribute_id, value_text)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_current_num "
                    + "ON object_attribute_current(attribute_id, value_num)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_current_bool "
                    + "ON object_attribute_current(attribute_id, value_bool)");
            statement.execute("PRAGMA user_version = " + SCHEMA_VERSION);
        }
    }

    private void seedFomMetadata() throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            insertClasses();
            insertAttributes();
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    private void insertClasses() throws SQLException {
        String sql = """
                INSERT OR IGNORE INTO fom_object_class (id, hla_name, local_name, parent_name)
                VALUES (?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (FomCatalog.ObjectClassDef clazz : catalog.objectClasses()) {
                statement.setInt(1, clazz.id());
                statement.setString(2, clazz.hlaName());
                statement.setString(3, clazz.localName());
                statement.setString(4, clazz.parentName());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertAttributes() throws SQLException {
        String sql = """
                INSERT OR IGNORE INTO fom_attribute
                    (id, class_id, attribute_name, path_key, data_type, primitive_type, is_leaf)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (FomCatalog.ObjectClassDef clazz : catalog.objectClasses()) {
                for (FomCatalog.FomAttribute attribute : clazz.attributes()) {
                    statement.setInt(1, attribute.id());
                    statement.setInt(2, attribute.classId());
                    statement.setString(3, attribute.attributeName());
                    statement.setString(4, attribute.pathKey());
                    statement.setString(5, attribute.dataType());
                    statement.setString(6, attribute.primitiveType());
                    statement.setInt(7, attribute.leaf() ? 1 : 0);
                    statement.addBatch();
                }
            }
            statement.executeBatch();
        }
    }

    private void upsertCurrentValue(
            long instanceId,
            int attributeId,
            DecodedAttributeValue value,
            String observedAt,
            long observedSequence) {
        String sql = """
                INSERT INTO object_attribute_current
                    (instance_id, attribute_id, value_type, value_text, value_num, value_bool, value_blob,
                     value_json, raw_bytes, observed_at, sequence)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(instance_id, attribute_id) DO UPDATE SET
                    value_type = excluded.value_type,
                    value_text = excluded.value_text,
                    value_num = excluded.value_num,
                    value_bool = excluded.value_bool,
                    value_blob = excluded.value_blob,
                    value_json = excluded.value_json,
                    raw_bytes = excluded.raw_bytes,
                    observed_at = excluded.observed_at,
                    sequence = excluded.sequence
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            Object objectValue = value.value();
            String valueType = CachedValue.valueType(objectValue);
            statement.setLong(1, instanceId);
            statement.setInt(2, attributeId);
            statement.setString(3, valueType);
            bindValueColumns(statement, 4, objectValue);
            statement.setBytes(9, value.rawBytes());
            statement.setString(10, observedAt);
            statement.setLong(11, observedSequence);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not upsert current object attribute", e);
        }
    }

    private Optional<Integer> attributeIdForPath(FomCatalog.ObjectClassDef clazz, String pathKey) {
        Optional<Integer> existingId = attributeIdFromDatabase(clazz.id(), pathKey);
        if (existingId.isPresent()) {
            return existingId;
        }

        String wildcardPath = FomCatalog.wildcardArrayIndexes(pathKey);
        FomCatalog.FomAttribute template = clazz.attribute(wildcardPath).orElse(null);
        if (template == null || wildcardPath.equals(pathKey)) {
            return Optional.empty();
        }

        String sql = """
                INSERT OR IGNORE INTO fom_attribute
                    (class_id, attribute_name, path_key, data_type, primitive_type, is_leaf)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, clazz.id());
            statement.setString(2, template.attributeName());
            statement.setString(3, pathKey);
            statement.setString(4, template.dataType());
            statement.setString(5, template.primitiveType());
            statement.setInt(6, template.leaf() ? 1 : 0);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not insert dynamic FOM attribute path " + pathKey, e);
        }
        return attributeIdFromDatabase(clazz.id(), pathKey);
    }

    private Optional<Integer> attributeIdFromDatabase(int classId, String pathKey) {
        String sql = "SELECT id FROM fom_attribute WHERE class_id = ? AND path_key = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, classId);
            statement.setString(2, pathKey);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(resultSet.getInt("id"));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read FOM attribute path " + pathKey, e);
        }
        return Optional.empty();
    }

    private void bindValueColumns(PreparedStatement statement, int startIndex, Object value) throws SQLException {
        setNullableString(statement, startIndex, value instanceof Character ? value.toString() : value);
        setNullableNumber(statement, startIndex + 1, value);
        setNullableBoolean(statement, startIndex + 2, value);
        setNullableBlob(statement, startIndex + 3, value);
        setNullableJson(statement, startIndex + 4, value);
    }

    private void setNullableString(PreparedStatement statement, int index, Object value) throws SQLException {
        if (value instanceof String stringValue) {
            statement.setString(index, stringValue);
        } else {
            statement.setNull(index, Types.VARCHAR);
        }
    }

    private void setNullableNumber(PreparedStatement statement, int index, Object value) throws SQLException {
        if (value instanceof Number number) {
            statement.setDouble(index, number.doubleValue());
        } else {
            statement.setNull(index, Types.DOUBLE);
        }
    }

    private void setNullableBoolean(PreparedStatement statement, int index, Object value) throws SQLException {
        if (value instanceof Boolean booleanValue) {
            statement.setInt(index, booleanValue ? 1 : 0);
        } else {
            statement.setNull(index, Types.INTEGER);
        }
    }

    private void setNullableBlob(PreparedStatement statement, int index, Object value) throws SQLException {
        if (value instanceof byte[] bytes) {
            statement.setBytes(index, bytes);
        } else {
            statement.setNull(index, Types.BLOB);
        }
    }

    private void setNullableJson(PreparedStatement statement, int index, Object value) throws SQLException {
        if (value instanceof byte[]) {
            statement.setNull(index, Types.VARCHAR);
            return;
        }
        try {
            statement.setString(index, mapper.writeValueAsString(value));
        } catch (JsonProcessingException e) {
            throw new SQLException("Could not serialize cached value as JSON", e);
        }
    }

    private CachedValue readCachedValue(ResultSet resultSet) throws SQLException {
        String valueType = resultSet.getString("value_type");
        byte[] rawBytes = resultSet.getBytes("raw_bytes");
        if ("blob".equals(valueType)) {
            return new CachedValue(valueType, resultSet.getBytes("value_blob"), rawBytes);
        }
        String valueJson = resultSet.getString("value_json");
        if (valueJson == null) {
            return new CachedValue(valueType, null, rawBytes);
        }
        try {
            return new CachedValue(valueType, mapper.readValue(valueJson, Object.class), rawBytes);
        } catch (JsonProcessingException e) {
            throw new SQLException("Could not deserialize cached value JSON", e);
        }
    }
}
