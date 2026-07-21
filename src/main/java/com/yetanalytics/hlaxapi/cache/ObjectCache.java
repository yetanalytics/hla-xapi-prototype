package com.yetanalytics.hlaxapi.cache;

import com.yetanalytics.hlaxapi.FOMXML;
import com.yetanalytics.hlaxapi.HLADecoderRegistry;
import com.yetanalytics.hlaxapi.config.XapiConfig;
import com.yetanalytics.hlaxapi.config.model.Expression;
import com.yetanalytics.hlaxapi.config.model.Target;
import com.yetanalytics.hlaxapi.config.model.TrackedObject;
import java.sql.Connection;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class ObjectCache implements AutoCloseable {

    private final FomCatalog catalog;
    private final Map<String, Set<String>> subscriptions;
    private final HlaValueFlattener valueFlattener;
    private final CacheQueryService queryService;
    private final AtomicLong sequence = new AtomicLong();
    private ObjectCacheStore store;

    public ObjectCache(XapiConfig xapiConfig, FomCatalog catalog, FOMXML fomXml, HLADecoderRegistry decoderRegistry) {
        this(xapiConfig, catalog, fomXml, decoderRegistry, (ObjectCacheConnectionSettings) null);
    }

    ObjectCache(
            XapiConfig xapiConfig,
            FomCatalog catalog,
            FOMXML fomXml,
            HLADecoderRegistry decoderRegistry,
            String jdbcUrl) {
        this(
                xapiConfig,
                catalog,
                fomXml,
                decoderRegistry,
                ObjectCacheConnectionSettings.sqlite(jdbcUrl));
    }

    ObjectCache(
            XapiConfig xapiConfig,
            FomCatalog catalog,
            FOMXML fomXml,
            HLADecoderRegistry decoderRegistry,
            ObjectCacheConnectionSettings settings) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.subscriptions = collectSubscriptions(xapiConfig);
        this.valueFlattener = new HlaValueFlattener(fomXml, decoderRegistry);
        this.queryService = new CacheQueryService(this);
        if (!subscriptions.isEmpty()) {
            ObjectCacheConnectionSettings effectiveSettings = settings == null
                    ? ObjectCacheConnectionSettings.from(System.getenv())
                    : settings;
            this.store = ObjectCacheStoreFactory.open(effectiveSettings, catalog);
        }
    }

    public boolean isEnabled() {
        return store != null && store.isOpen();
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
            store.ensureObject(objectHandle, objectName, clazz);
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
        CachedObject object = store.ensureObject(objectHandle, null, clazz);
        FomCatalog.FomAttribute topAttribute = clazz.attribute(attributeName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No FOM attribute " + attributeName + " on object class " + className));
        List<DecodedAttributeValue> values = valueFlattener.flatten(attributeName, topAttribute.dataType(), bytes);
        String observedAt = Instant.now().toString();
        long observedSequence = sequence.incrementAndGet();
        store.replaceCurrentValues(
                object.id(),
                clazz,
                attributeName,
                values,
                observedAt,
                observedSequence);
    }

    public synchronized void removeObject(String objectHandle) {
        if (!isEnabled()) {
            return;
        }
        store.removeObject(objectHandle, Instant.now().toString());
    }

    public synchronized Optional<CachedValue> findCurrentValue(long instanceId, String pathKey) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        return store.findCurrentValue(instanceId, pathKey);
    }

    public synchronized Optional<CachedValue> findCurrentValue(String objectHandle, String pathKey) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        return store.findCurrentValue(objectHandle, pathKey);
    }

    public synchronized List<CachedObject> currentObjects(String className) {
        if (!isEnabled()) {
            return List.of();
        }
        FomCatalog.ObjectClassDef clazz = requireClass(className);
        return store.currentObjects(clazz);
    }

    Connection connection() {
        return store == null ? null : store.connection();
    }

    @Override
    public synchronized void close() {
        if (store == null) {
            return;
        }
        store.close();
        store = null;
    }

    private FomCatalog.ObjectClassDef requireClass(String className) {
        return catalog.objectClass(className)
                .orElseThrow(() -> new IllegalArgumentException("No FOM object class " + className));
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
