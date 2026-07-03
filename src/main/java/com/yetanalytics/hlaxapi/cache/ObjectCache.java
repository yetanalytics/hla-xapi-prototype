package com.yetanalytics.hlaxapi.cache;

import com.yetanalytics.hlaxapi.FOMXML;
import com.yetanalytics.hlaxapi.HLADecoderRegistry;
import com.yetanalytics.hlaxapi.config.XapiConfig;
import com.yetanalytics.hlaxapi.config.model.Expression;
import com.yetanalytics.hlaxapi.config.model.Target;
import com.yetanalytics.hlaxapi.config.model.TrackedObject;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class ObjectCache implements AutoCloseable {

    private final FomCatalog catalog;
    private final Map<String, Set<String>> subscriptions;
    private HlaObjectCache delegate;

    public ObjectCache(XapiConfig xapiConfig, FomCatalog catalog, FOMXML fomXml, HLADecoderRegistry decoderRegistry) {
        this(xapiConfig, catalog, fomXml, decoderRegistry, HlaObjectCache.defaultJdbcUrl());
    }

    ObjectCache(
            XapiConfig xapiConfig,
            FomCatalog catalog,
            FOMXML fomXml,
            HLADecoderRegistry decoderRegistry,
            String jdbcUrl) {
        this.catalog = catalog;
        this.subscriptions = collectSubscriptions(xapiConfig);
        if (!subscriptions.isEmpty()) {
            this.delegate = new HlaObjectCache(jdbcUrl, catalog, fomXml, decoderRegistry);
        }
    }

    public boolean isEnabled() {
        return delegate != null;
    }

    public Map<String, Set<String>> subscriptions() {
        return subscriptions;
    }

    public FomCatalog catalog() {
        return catalog;
    }

    public Optional<Object> findFirstValue(String clazz, Target attrTarget, Expression criteria) {
        if (delegate == null) {
            return Optional.empty();
        }
        return delegate.queryService().findFirstValue(clazz, attrTarget, criteria);
    }

    public Optional<CachedObject> findFirstObject(String clazz, Expression criteria) {
        if (delegate == null) {
            return Optional.empty();
        }
        return delegate.queryService().findFirstObject(clazz, criteria);
    }

    public Optional<Object> findValue(CachedObject object, Target attrTarget) {
        if (delegate == null) {
            return Optional.empty();
        }
        return delegate.queryService().findValue(object, attrTarget);
    }

    public void discoverObject(String objectHandle, String objectName, String className) {
        if (delegate != null) {
            delegate.discoverObject(objectHandle, objectName, className);
        }
    }

    public void reflectAttributeValue(String objectHandle, String className, String attributeName, byte[] bytes) {
        if (delegate != null) {
            delegate.reflectAttributeValue(objectHandle, className, attributeName, bytes);
        }
    }

    public void removeObject(String objectHandle) {
        if (delegate != null) {
            delegate.removeObject(objectHandle);
        }
    }

    @Override
    public synchronized void close() {
        if (delegate != null) {
            delegate.close();
            delegate = null;
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
