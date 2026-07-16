package com.yetanalytics.hlaxapi.config.model;

import java.util.List;

public class ObjectCacheConfig {
    public ObjectCacheBackend backend = ObjectCacheBackend.SQLITE;
    public List<TrackedObject> trackedObjects;
}
