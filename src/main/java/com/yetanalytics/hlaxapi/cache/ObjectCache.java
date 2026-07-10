package com.yetanalytics.hlaxapi.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yetanalytics.hlaxapi.FOMXML;
import com.yetanalytics.hlaxapi.HLADecoderRegistry;
import com.yetanalytics.hlaxapi.config.XapiConfig;
import com.yetanalytics.hlaxapi.config.model.Expression;
import com.yetanalytics.hlaxapi.config.model.Target;
import com.yetanalytics.hlaxapi.config.model.TrackedObject;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class ObjectCache implements AutoCloseable {

    private static final int SCHEMA_VERSION = 1;

    private final FomCatalog catalog;
    private final Map<String, Set<String>> subscriptions;
    private final HlaValueFlattener valueFlattener;
    private final CacheQueryService queryService;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicLong sequence = new AtomicLong();
    private Connection connection;

    public ObjectCache(XapiConfig xapiConfig, FomCatalog catalog, FOMXML fomXml, HLADecoderRegistry decoderRegistry) {
        this(xapiConfig, catalog, fomXml, decoderRegistry, defaultJdbcUrl());
    }

    ObjectCache(
            XapiConfig xapiConfig,
            FomCatalog catalog,
            FOMXML fomXml,
            HLADecoderRegistry decoderRegistry,
            String jdbcUrl) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.subscriptions = collectSubscriptions(xapiConfig);
        this.valueFlattener = new HlaValueFlattener(fomXml, decoderRegistry);
        this.queryService = new CacheQueryService(this);
        if (!subscriptions.isEmpty()) {
            try {
                this.connection = DriverManager.getConnection(Objects.requireNonNull(jdbcUrl, "jdbcUrl"));
                initializeSchema();
                seedFomMetadata();
            } catch (SQLException e) {
                throw new IllegalStateException("Could not initialize HLA object cache", e);
            }
        }
    }

    public boolean isEnabled() {
        return connection != null;
    }

    public Map<String, Set<String>> subscriptions() {
        return subscriptions;
    }

    public FomCatalog catalog() {
        return catalog;
    }

    public CacheQueryService queryService() {
        return queryService;
    }

    public Optional<Object> findFirstValue(String clazz, Target attrTarget, Expression criteria) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        return queryService.findFirstValue(clazz, attrTarget, criteria);
    }

    public ValueResolution findFirstResolution(String clazz, Target attrTarget, Expression criteria) {
        if (!isEnabled()) {
            return ValueResolution.missingObject();
        }
        return queryService.findFirstResolution(clazz, attrTarget, criteria);
    }

    public Optional<CachedObject> findFirstObject(String clazz, Expression criteria) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        return queryService.findFirstObject(clazz, criteria);
    }

    public Optional<Object> findValue(CachedObject object, Target attrTarget) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        return queryService.findValue(object, attrTarget);
    }

    public ValueResolution findValueResolution(CachedObject object, Target attrTarget) {
        if (!isEnabled()) {
            return ValueResolution.missingObject();
        }
        return queryService.findValueResolution(object, attrTarget);
    }

    public synchronized void discoverObject(String objectHandle, String objectName, String className) {
        if (isEnabled()) {
            FomCatalog.ObjectClassDef clazz = requireClass(className);
            ensureObject(objectHandle, objectName, clazz.localName());
        }
    }

    public synchronized void reflectAttributeValue(
            String objectHandle,
            String className,
            String attributeName,
            byte[] bytes) {
        if (!isEnabled()) {
            return;
        }
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

    public synchronized void removeObject(String objectHandle) {
        if (!isEnabled()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE object_instance SET removed_at = ? WHERE object_handle = ?")) {
            statement.setString(1, Instant.now().toString());
            statement.setString(2, objectHandle);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not mark object removed: " + objectHandle, e);
        }
    }

    public synchronized Optional<CachedValue> findCurrentValue(long instanceId, String pathKey) {
        if (!isEnabled()) {
            return Optional.empty();
        }
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

    public synchronized Optional<CachedValue> findCurrentValue(String objectHandle, String pathKey) {
        if (!isEnabled()) {
            return Optional.empty();
        }
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

    public synchronized List<CachedObject> currentObjects(String className) {
        if (!isEnabled()) {
            return List.of();
        }
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
    public synchronized void close() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
            connection = null;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not close HLA object cache", e);
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
            statement.execute("PRAGMA foreign_keys = OFF");
            statement.execute("DROP TABLE IF EXISTS object_attribute_current");
            statement.execute("DROP TABLE IF EXISTS object_instance");
            statement.execute("DROP TABLE IF EXISTS fom_attribute");
            statement.execute("DROP TABLE IF EXISTS fom_object_class");
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
                    (instance_id, attribute_id, value_type, value_blob, value_json, raw_bytes, observed_at, sequence)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(instance_id, attribute_id) DO UPDATE SET
                    value_type = excluded.value_type,
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
            if (objectValue instanceof byte[] bytes) {
                statement.setBytes(4, bytes);
                statement.setNull(5, java.sql.Types.VARCHAR);
            } else {
                statement.setNull(4, java.sql.Types.BLOB);
                statement.setString(5, serializeJson(objectValue));
            }
            statement.setBytes(6, value.rawBytes());
            statement.setString(7, observedAt);
            statement.setLong(8, observedSequence);
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

    private String serializeJson(Object value) throws SQLException {
        try {
            return mapper.writeValueAsString(value);
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

    private Map<String, Set<String>> collectSubscriptions(XapiConfig xapiConfig) {
        Map<String, Set<String>> merged = new LinkedHashMap<>();
        QueryReferenceCollector.collect(xapiConfig.statementTriggers)
                .forEach((className, attributes) -> addAttributes(merged, className, attributes));
        addTrackedObjects(merged, xapiConfig);
        return copySubscriptions(merged);
    }

    private void addTrackedObjects(Map<String, Set<String>> merged, XapiConfig xapiConfig) {
        if (xapiConfig.objectCacheConfig == null || xapiConfig.objectCacheConfig.trackedObjects == null) {
            return;
        }
        for (TrackedObject trackedObject : xapiConfig.objectCacheConfig.trackedObjects) {
            if (trackedObject == null || trackedObject.clazz == null || trackedObject.clazz.isBlank()) {
                continue;
            }
            if ("*".equals(trackedObject.clazz.trim())) {
                if (trackedObject.allAttributes) {
                    catalog.objectClasses().forEach(clazz ->
                            addAttributes(merged, clazz.localName(), clazz.topLevelAttributeNames()));
                }
                continue;
            }
            if (trackedObject.allAttributes) {
                Optional<FomCatalog.ObjectClassDef> clazz = catalog.objectClass(trackedObject.clazz);
                if (clazz.isPresent()) {
                    FomCatalog.ObjectClassDef objectClass = clazz.orElseThrow();
                    addAttributes(merged, objectClass.localName(), objectClass.topLevelAttributeNames());
                } else {
                    addAttributes(merged, trackedObject.clazz, Set.of("*"));
                }
            } else {
                String className = catalog.objectClass(trackedObject.clazz)
                        .map(FomCatalog.ObjectClassDef::localName)
                        .orElse(trackedObject.clazz);
                addAttributes(merged, className, trackedObject.attributes);
            }
        }
    }

    private void addAttributes(Map<String, Set<String>> subscriptions, String className, Iterable<String> attributes) {
        if (className == null || className.isBlank() || attributes == null) {
            return;
        }
        Set<String> targetAttributes = subscriptions.computeIfAbsent(className, ignored -> new LinkedHashSet<>());
        for (String attribute : attributes) {
            if (attribute != null && !attribute.isBlank()) {
                targetAttributes.add(attribute);
            }
        }
        if (targetAttributes.isEmpty()) {
            subscriptions.remove(className);
        }
    }

    private Map<String, Set<String>> copySubscriptions(Map<String, Set<String>> subscriptions) {
        Map<String, Set<String>> copy = new LinkedHashMap<>();
        subscriptions.forEach((className, attributes) -> copy.put(className, Set.copyOf(attributes)));
        return Map.copyOf(copy);
    }
}
