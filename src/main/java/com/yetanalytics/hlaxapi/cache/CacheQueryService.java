package com.yetanalytics.hlaxapi.cache;

import com.yetanalytics.hlaxapi.config.model.Expression;
import com.yetanalytics.hlaxapi.config.model.Target;
import com.yetanalytics.hlaxapi.criteria.CriteriaEvaluator;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class CacheQueryService {

    private final HlaObjectCache cache;
    private final CriteriaEvaluator criteriaEvaluator;

    public CacheQueryService(HlaObjectCache cache) {
        this.cache = Objects.requireNonNull(cache, "cache");
        this.criteriaEvaluator = new CriteriaEvaluator();
    }

    public Optional<Object> findFirstValue(String className, Target target, Expression criteria) {
        return findValues(className, target, criteria).stream().findFirst();
    }

    public Optional<CachedObject> findFirstObject(String className, Expression criteria) {
        for (CachedObject object : cache.currentObjects(className)) {
            if (matches(object, criteria)) {
                return Optional.of(object);
            }
        }
        return Optional.empty();
    }

    public ValueResolution findFirstResolution(String className, Target target, Expression criteria) {
        Optional<CachedObject> object = findFirstObject(className, criteria);
        if (object.isEmpty()) {
            return ValueResolution.missingObject();
        }
        return findValueResolution(object.orElseThrow(), target);
    }

    public Optional<Object> findValue(CachedObject object, Target target) {
        ValueResolution resolution = findValueResolution(object, target);
        if (!resolution.present() || resolution.value() == null) {
            return Optional.empty();
        }
        return Optional.of(resolution.value());
    }

    public ValueResolution findValueResolution(CachedObject object, Target target) {
        if (object == null) {
            return ValueResolution.missingObject();
        }
        return resolveTarget(object, target);
    }

    public List<Object> findValues(String className, Target target, Expression criteria) {
        String pathKey = FomCatalog.targetPath(target == null ? null : target.parts);
        if (pathKey == null) {
            return List.of();
        }

        List<Object> values = new ArrayList<>();
        for (CachedObject object : cache.currentObjects(className)) {
            if (matches(object, criteria)) {
                cache.findCurrentValue(object.id(), pathKey)
                        .map(CachedValue::value)
                        .ifPresent(values::add);
            }
        }
        return values;
    }

    public boolean matches(CachedObject object, Expression expression) {
        return criteriaEvaluator.matches(expression, leaf -> resolveCacheExpression(object, leaf));
    }

    private Object resolveCacheExpression(CachedObject object, Expression expression) {
        if (expression instanceof Target target) {
            ValueResolution resolution = resolveTarget(object, target);
            return resolution.present() ? resolution.value() : null;
        }
        return null;
    }

    private ValueResolution resolveTarget(CachedObject object, Target target) {
        String pathKey = FomCatalog.targetPath(target.parts);
        if (pathKey == null) {
            return ValueResolution.missingValue();
        }
        return cache.findCurrentValue(object.id(), pathKey)
                .map(value -> ValueResolution.present(value.value()))
                .orElseGet(ValueResolution::missingValue);
    }

}
