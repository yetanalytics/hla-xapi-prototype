package com.yetanalytics.hlaxapi.config.model;

import java.util.List;

/**
 * Represents a parsed 'target' syntax: an ordered list of keys and array indexes
 */
public class Target implements Expression {
    public final List<Object> parts;

    public Target(List<Object> parts) {
        this.parts = parts;
    }

    public void printParts() {
        System.out.print("Target parts: ");
        for (Object part : parts) {
            System.out.print(part + " ");
        }
        System.out.println();
    }

    @Override
    public String toString() {
        return "Target{" + parts.toString() + "}";
    }
}
