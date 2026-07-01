package com.yetanalytics.hlaxapi.cache;

import com.yetanalytics.hlaxapi.FOMXML;
import com.yetanalytics.hlaxapi.HLADecoderRegistry;
import com.yetanalytics.hlaxapi.config.XapiConfig;
import com.yetanalytics.hlaxapi.config.model.Expression;
import com.yetanalytics.hlaxapi.config.model.Target;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class ObjectCache implements AutoCloseable {

    private final FomCatalog catalog;
    private final Map<String, Set<String>> subscriptions;
    private HlaObjectCache delegate;

    public ObjectCache(XapiConfig xapiConfig, FOMXML fomXml, HLADecoderRegistry decoderRegistry) {
        this(xapiConfig, fomXml, decoderRegistry, HlaObjectCache.defaultJdbcUrl());
    }

    ObjectCache(XapiConfig xapiConfig, FOMXML fomXml, HLADecoderRegistry decoderRegistry, String jdbcUrl) {
        this.catalog = FomCatalog.fromFomXml(fomXml);
        this.subscriptions = QueryReferenceCollector.collect(xapiConfig.statementTriggers);
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
}
