package com.yetanalytics.hlaxapi.config.model;

import java.util.List;

/**
 * Represents a parsed 'target' syntax: an ordered list of keys and array indexes
 */
public final class Target implements Expression {
    public final List<Object> parts;

    public Target(List<Object> parts) {
        this.parts = parts;
    }

    @Override
    public String toString() {
        return "Target{" + parts.toString() + "}";
    }
}
